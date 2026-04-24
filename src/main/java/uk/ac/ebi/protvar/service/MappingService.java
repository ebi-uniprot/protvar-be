package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.fetcher.IdentifierBrowseHandler;
import uk.ac.ebi.protvar.fetcher.ResultCacheHandler;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.Identifier;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.InputRequest;
import uk.ac.ebi.protvar.types.IdentifierType;
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
    private final ResultCacheHandler resultCacheHandler;
    private final IdentifierBrowseHandler identifierBrowseHandler;
    private final InputMapper inputMapper;

    public PagedMappingResponse get(MappingRequest request) {
        Page<VariantInput> page;
        boolean multiFormat;

        if (request.getResultId() != null && !request.getResultId().isBlank()) {
            // Uploaded result: retrieve from Redis cache
            page = resultCacheHandler.pagedInput(request);
            multiFormat = true;
        } else if (request.getIds() != null && !request.getIds().isEmpty()) {
            // Identifier browse: UniProt, Gene, PDB, Ensembl, RefSeq
            page = identifierBrowseHandler.pagedInput(request);
            multiFormat = false;
        } else if (request.getQ() != null && !request.getQ().isBlank()) {
            // Direct variant query
            page = new PageImpl<>(
                    VariantParser.parse(List.of(request.getQ())),
                    PageRequest.of(0, 1),
                    1
            );
            multiFormat = true;
        } else {
            // Filter-only browse (no identifier constraint)
            page = identifierBrowseHandler.pagedInput(request);
            multiFormat = false;
        }

        List<VariantInput> inputs = page.getContent();

        InputBuild build = null;
        if (request.getResultId() != null && !request.getResultId().isBlank()) {
            inputService.detectBuild(InputRequest.builder()
                    .inputId(request.getResultId())
                    .assembly(request.getAssembly())
                    .build());
            build = inputCacheService.getBuild(request.getResultId());
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

            InputSummary inputSummary = inputCacheService.getSummary(request.getResultId());
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
                    .last(page.isLast())
                    .assembly(request.getAssembly())
                    .content(mapping)
                    .build();
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
