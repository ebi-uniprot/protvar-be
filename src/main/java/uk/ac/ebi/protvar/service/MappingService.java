package uk.ac.ebi.protvar.service;

import com.google.common.collect.Iterables;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.Identifier;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.InputRequest;
import uk.ac.ebi.protvar.repo.GenomicVariantRepo;
import uk.ac.ebi.protvar.repo.MappingRepo;
import uk.ac.ebi.protvar.types.IdentifierType;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class MappingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MappingService.class);

    private final InputService inputService;
    private final UploadCacheService uploadCacheService;
    private final MappingRepo mappingRepo;
    private final GenomicVariantRepo genomicVariantRepo;
    private final InputMapper inputMapper;

    /**
     * Fetches a single page of {@link VariantInput} for the request, dispatching
     * to the appropriate source based on what the caller provided:
     *
     * <pre>
     *   Request shape         Source                        Counting
     *   ─────────────────     ────────────────────────      ──────────────────────
     *   q=…                   inline VariantParser          exact (always 1)
     *   resultId=…            UploadCacheService            exact (cached input size)
     *   ids[]=…               MappingRepo                   exact
     *   (none — filter-only)  GenomicVariantRepo            best-effort:
     *                                                       • exact when result count
     *                                                         is small
     *                                                       • capped (totalCap) when
     *                                                         > COUNT_CAP rows
     *                                                       • totalItems = -1 when
     *                                                         the bounded COUNT
     *                                                         exceeds its query
     *                                                         timeout (sparse multi-
     *                                                         filter intersections)
     * </pre>
     *
     * Only the filter-only path can return totalItems = -1 or a non-null
     * totalCap; identifier / variant / uploaded-result paths always return
     * an exact totalItems.
     */
    public Page<VariantInput> getInputs(MappingRequest request) {
        if (isResultId(request)) {
            return cachedUploadPage(request);
        }
        if (hasIds(request)) {
            Pageable pageable = PageRequest.of(request.getPage() - 1, request.getPageSize());
            return mappingRepo.getGenomicVariantsForInput(request, pageable);
        }
        if (hasQ(request)) {
            return new PageImpl<>(
                    VariantParser.parse(List.of(request.getQ())),
                    PageRequest.of(0, 1),
                    1
            );
        }
        // filter-only browse — routed through GenomicVariantRepo for query optimisation
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getPageSize());
        return genomicVariantRepo.get(request, pageable);
    }

    /**
     * Returns a row count for the request without fetching the full page.
     * Used by the download controller to enforce a hard cap before queueing.
     *
     * <p>Return values:
     * <ul>
     *   <li>{@code 1} for {@code q} (always a single variant)</li>
     *   <li>cached upload size for {@code resultId} — exact, O(1)</li>
     *   <li>exact count from {@link MappingRepo} for {@code ids[]}</li>
     *   <li>For filter-only: exact when ≤ {@link GenomicVariantRepo#COUNT_CAP},
     *       {@code COUNT_CAP+1} when the bounded COUNT short-circuits (i.e.
     *       result set exceeds the cap), {@code -1} when COUNT timed out</li>
     * </ul>
     */
    public long countInputs(MappingRequest request) {
        if (hasQ(request)) {
            return 1L;
        }
        if (isResultId(request)) {
            List<String> cached = uploadCacheService.getInput(request.getResultId());
            return cached == null ? 0L : cached.size();
        }
        if (hasIds(request)) {
            return mappingRepo.getGenomicVariantsForInput(request, PageRequest.of(0, 1)).getTotalElements();
        }
        // filter-only
        return genomicVariantRepo.get(request, PageRequest.of(0, 1)).getTotalElements();
    }

    /**
     * Streams {@link VariantInput}s in chunks for full-data downloads. Same
     * dispatch as {@link #getInputs} but yields the entire result set in
     * fixed-size chunks rather than one page.
     */
    public Stream<List<VariantInput>> streamChunkedInputs(MappingRequest request, int chunkSize) {
        if (isResultId(request)) {
            return cachedUploadStream(request, chunkSize);
        }
        if (hasIds(request)) {
            return Stream.iterate(0, page -> page + 1)
                    .map(page -> mappingRepo.getGenomicVariantsForInput(request, PageRequest.of(page, chunkSize)).getContent())
                    .takeWhile(batch -> !batch.isEmpty());
        }
        if (hasQ(request)) {
            return Stream.of(VariantParser.parse(List.of(request.getQ())));
        }
        // filter-only
        return Stream.iterate(0, page -> page + 1)
                .map(page -> genomicVariantRepo.get(request, PageRequest.of(page, chunkSize)).getContent())
                .takeWhile(batch -> !batch.isEmpty());
    }

    private Page<VariantInput> cachedUploadPage(MappingRequest request) {
        String cacheKey = request.getResultId();
        List<String> fullList = uploadCacheService.getInput(cacheKey);
        if (fullList == null || fullList.isEmpty()) return Page.empty();

        int page = request.getPage() - 1;
        int pageSize = request.getPageSize();
        int total = fullList.size();
        int fromIndex = page * pageSize;
        if (fromIndex >= total) return Page.empty();

        List<String> subList = fullList.subList(fromIndex, Math.min(fromIndex + pageSize, total));
        List<VariantInput> parsed = VariantParser.parse(subList);
        Pageable pageable = PageRequest.of(page, pageSize);
        return new PageImpl<>(parsed, pageable, total);
    }

    private Stream<List<VariantInput>> cachedUploadStream(MappingRequest request, int chunkSize) {
        String cacheKey = request.getResultId();
        List<String> fullInput = uploadCacheService.getInput(cacheKey);
        if (fullInput == null || fullInput.isEmpty()) return Stream.empty();

        if (fullInput.size() > 1_000_000) {
            LOGGER.warn("Cached input size is very large: {}", fullInput.size());
        }
        return StreamSupport.stream(
                Iterables.partition(fullInput, chunkSize).spliterator(), false
        ).map(VariantParser::parse);
    }

    /**
     * Dispatches a MappingRequest and assembles the PagedMappingResponse for
     * the API. Wraps {@link #getInputs} with build detection (for resultId)
     * and mapping/annotation enrichment.
     */
    public PagedMappingResponse get(MappingRequest request) {
        Page<VariantInput> page = getInputs(request);
        boolean filterOnly = isFilterOnly(request);
        boolean multiFormat = isMultiFormat(request);

        List<VariantInput> inputs = page.getContent();

        InputBuild build = null;
        if (isResultId(request)) {
            inputService.detectBuild(InputRequest.builder()
                    .inputId(request.getResultId())
                    .assembly(request.getAssembly())
                    .build());
            build = uploadCacheService.getBuild(request.getResultId());
        }

        MappingResponse mapping = inputMapper.getMapping(inputs, request.getAssembly(), build, multiFormat);

        if (mapping != null && posRangeIgnored(request)) {
            mapping.getMessages().add(new Message(Message.MessageType.WARN,
                    "startPos/endPos position range filter applies to single UniProt accession browse only and has been ignored."));
        }

        if (mapping != null && build != null) {
            List<Message> messages = Optional.ofNullable(mapping.getMessages()).orElseGet(ArrayList::new);
            mapping.setMessages(messages);

            if (build.getMessage() != null) {
                messages.add(build.getMessage());
            }

            InputSummary inputSummary = uploadCacheService.getSummary(request.getResultId());
            String summaryText = (inputSummary != null)
                    ? inputSummary.toString()
                    : String.format("%d user input%s", inputs.size(), FetcherUtils.pluralise(inputs.size()));

            messages.add(0, new Message(Message.MessageType.INFO, summaryText));
        }

        return PagedMappingResponse.builder()
                    .page(request.getPage())
                    .pageSize(page.getSize())
                    .totalItems(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .totalCap(filterOnly ? (long) GenomicVariantRepo.COUNT_CAP : null)
                    .last(page.isLast())
                    .assembly(request.getAssembly())
                    .content(mapping)
                    .build();
    }

    private static boolean isResultId(MappingRequest request) {
        return request.getResultId() != null && !request.getResultId().isBlank();
    }

    private static boolean hasIds(MappingRequest request) {
        return request.getIds() != null && !request.getIds().isEmpty();
    }

    private static boolean hasQ(MappingRequest request) {
        return request.getQ() != null && !request.getQ().isBlank();
    }

    private static boolean isFilterOnly(MappingRequest request) {
        return !isResultId(request) && !hasIds(request) && !hasQ(request);
    }

    private static boolean isMultiFormat(MappingRequest request) {
        return isResultId(request) || hasQ(request);
    }

    /** True when posFrom/posTo is set but the request is NOT a single-UniProt browse. */
    private boolean posRangeIgnored(MappingRequest request) {
        if (request.getStartPos() == null && request.getEndPos() == null) return false;
        List<Identifier> ids = request.getIds();
        if (ids == null || ids.size() != 1) return true;
        Identifier id = ids.get(0);
        return id.type() != IdentifierType.UNIPROT;
    }
}
