package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.fetcher.SearchInputHandler;
import uk.ac.ebi.protvar.fetcher.CachedInputHandler;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.InputRequest;
import uk.ac.ebi.protvar.model.SearchTerm;
import uk.ac.ebi.protvar.types.SearchType;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MappingService {
    private final InputService inputService;
    private final InputCacheService inputCacheService;
    private final CachedInputHandler cachedInputHandler; // get the input
    private final SearchInputHandler searchInputHandler;
    private final InputMapper inputMapper;   // do the mapping logic


    /**
     * Fetch variants based on the request type.
     * Handles: single variant, input_id, identifiers, and filter-only queries.
     */
    public Page<VariantInput> fetch(MappingRequest request) {
        // Single variant - parse directly
        if (request.isSingleVariant()) {
            String variantStr = request.getSearchTerms().get(0).getValue();
            List<VariantInput> parsed = VariantParser.parse(List.of(variantStr));
            return new PageImpl<>(parsed, PageRequest.of(0, 1), 1);
        }

        // Input ID - retrieve from cache
        if (request.isInputIdQuery()) {
            return cachedInputHandler.pagedInput(request);
        }

        // Identifiers and/or filters - query repository
        return searchInputHandler.pagedInput(request);
    }

    public PagedMappingResponse get(MappingRequest request) {
        // Fetch variants (handles all cases: variant, input_id, identifiers, filters)
        Page<VariantInput> page = fetch(request);

        // Determine build (only for input_id)
        InputBuild build = null;
        if (request.isInputIdQuery()) {
            String inputId = request.getSearchTerms().get(0).getValue();
            // ensure that the build is detected and cached
            inputService.detectBuild(InputRequest.builder()
                    .inputId(inputId)
                    .assembly(request.getAssembly())
                    .build());
            build = inputCacheService.getBuild(inputId);
        }

        // Map variants
        boolean multiFormat = request.isSingleVariant() || request.isInputIdQuery();
        MappingResponse mapping = inputMapper.getMapping(
                page.getContent(),
                request.getAssembly(),
                build,
                multiFormat);

        // Add contextual messages
        addContextualMessages(mapping, request, build, page.getContent().size());

        // Build response
        return PagedMappingResponse.builder()
                .request(request)  // Include full request with all filters
                // Pagination metadata - actual results (may differ from request)
                .page(page.getNumber() + 1)  // Convert from 0-based to 1-based
                .pageSize(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                // Mapping result
                .mapping(mapping)
                .build();
    }

    private void addContextualMessages(
            MappingResponse mapping,
            MappingRequest request,
            InputBuild build,
            int variantCount) {

        if (mapping == null) {
            return;
        }

        List<Message> messages = Optional.ofNullable(mapping.getMessages())
                .orElseGet(ArrayList::new);
        mapping.setMessages(messages);

        // Add appropriate context message
        if (request.isInputIdQuery()) {
            String inputId = request.getSearchTerms().get(0).getValue();
            String summary = buildInputIdSummary(inputId, variantCount);
            messages.add(0, new Message(Message.MessageType.INFO, summary));

            if (build != null && build.getMessage() != null) {
                messages.add(build.getMessage());
            }
        } else if (request.isAdvancedSearch()) {
            String scopeMessage = buildSearchScopeMessage(
                    request.getIdentifierTerms(),
                    request
            );
            messages.add(0, new Message(Message.MessageType.INFO, scopeMessage));
        }
    }

    /**
     * Build summary message for input ID queries
     */
    private String buildInputIdSummary(String inputId, int variantCount) {
        InputSummary summary = inputCacheService.getSummary(inputId);

        if (summary != null) {
            return summary.toString();
        }

        // Fallback if summary not yet available
        return String.format("%d user input%s",
                variantCount,
                FetcherUtils.pluralise(variantCount));
    }

    /**
     * Build search scope message for identifier/filter queries
     */
    private String buildSearchScopeMessage(List<SearchTerm> identifiers, MappingRequest request) {
        int filterCount = countActiveFilters(request);

        if (identifiers.isEmpty()) {
            // Filter-only query
            if (filterCount == 0) {
                return "Showing all variants";
            }
            return String.format("Database-wide search with %d filter%s applied",
                    filterCount,
                    FetcherUtils.pluralise(filterCount));
        }

        if (identifiers.size() == 1) {
            // Single identifier
            SearchTerm term = identifiers.get(0);
            String base = String.format("Variants for %s: %s",
                    formatTermType(term.getType()),
                    term.getValue());

            if (filterCount > 0) {
                base += String.format(" (%d filter%s applied)",
                        filterCount,
                        FetcherUtils.pluralise(filterCount));
            }
            return base;
        }

        // Multiple identifiers
        String base = String.format("Variants across %d identifier%s",
                identifiers.size(),
                FetcherUtils.pluralise(identifiers.size()));

        if (filterCount > 0) {
            base += String.format(" with %d filter%s applied",
                    filterCount,
                    FetcherUtils.pluralise(filterCount));
        }

        return base;
    }

    /**
     * Count active filters in the request
     */
    private int countActiveFilters(MappingRequest request) {
        int count = 0;

        if (request.getKnown() != null) count++;
        if (request.getPtm() != null) count++;
        if (request.getMutagenesis() != null) count++;
        if (request.getConservationMin() != null) count++;
        if (request.getConservationMax() != null) count++;
        if (request.getFunctionalDomain() != null) count++;
        if (request.getDiseaseAssociation() != null) count++;
        if (request.getAlleleFreq() != null && !request.getAlleleFreq().isEmpty()) count++;
        if (request.getExperimentalModel() != null) count++;
        if (request.getInteract() != null) count++;
        if (request.getPocket() != null) count++;
        if (request.getStability() != null && !request.getStability().isEmpty()) count++;
        if (request.getCadd() != null && !request.getCadd().isEmpty()) count++;
        if (request.getAm() != null && !request.getAm().isEmpty()) count++;
        if (request.getPopeve() != null && !request.getPopeve().isEmpty()) count++;
        if (request.getEsm1bMin() != null) count++;
        if (request.getEsm1bMax() != null) count++;

        return count;
    }

    /**
     * Format search term type for display
     */
    private String formatTermType(SearchType type) {
        return switch (type) {
            case UNIPROT -> "UniProt accession";
            case GENE -> "gene";
            case ENSEMBL -> "Ensembl ID";
            case PDB -> "PDB structure";
            case REFSEQ -> "RefSeq ID";
            default -> type.getValue();
        };
    }
}
