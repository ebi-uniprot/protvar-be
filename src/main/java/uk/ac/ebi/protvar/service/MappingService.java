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
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.InputRequest;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.types.InputType;
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

    private Page<VariantInput> singleInputPage(String input) {
        return new PageImpl<>(
                InputParser.parse(List.of(input)),
                PageRequest.of(0, 1), // page index 0, size 1
                1 // total elements
        );
    }

    public PagedMappingResponse get(MappingRequest request) {
        Page<VariantInput> page = switch (request.getType()) {
            case SINGLE_VARIANT -> singleInputPage(request.getInput());
            case INPUT_ID -> cachedInputHandler.pagedInput(request);
            case UNIPROT, ENSEMBL, GENE, PDB, REFSEQ -> searchInputHandler.pagedInput(request);
        };

        List<VariantInput> inputs = page.getContent();

        InputBuild build = null;
        if (request.getType() == InputType.INPUT_ID) {
            // ensure that the build is detected and cached
            inputService.detectBuild(InputRequest.builder()
                    .inputId(request.getInput())
                    .assembly(request.getAssembly())
                    .build());
            build = inputCacheService.getBuild(request.getInput());
        }

        boolean multiFormat = request.getType() == InputType.SINGLE_VARIANT || request.getType() == InputType.INPUT_ID;
        MappingResponse mapping = inputMapper.getMapping(inputs, request.getAssembly(), build, multiFormat);

        if (mapping !=null && request.getType() == InputType.INPUT_ID) {
            List<Message> messages = Optional.ofNullable(mapping.getMessages()).orElseGet(ArrayList::new);
            mapping.setMessages(messages);

            // Add build message if available
            if (build != null && build.getMessage() != null) {
                messages.add(build.getMessage());
            }

            // Add input summary as the first message
            InputSummary inputSummary = inputCacheService.getSummary(request.getInput());
            String summaryText = (inputSummary != null)
                    ? inputSummary.toString()
                    // probably still generating.. use basic summary
                    : String.format("%d user input%s", inputs.size(), FetcherUtils.pluralise(inputs.size()));

            messages.add(0, new Message(Message.MessageType.INFO, summaryText));
        }

        return PagedMappingResponse.builder()
                    //paging
                    .page(request.getPage()) //use request (1-base) page here, not page.getNumber() which is 0-based.
                    .pageSize(page.getSize())
                    .totalItems(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .last(page.isLast())
                    //input
                    .input(request.getInput())
                    .type(request.getType())
                    //assembly, if provided, is relevant only for genomic input
                    .assembly(request.getAssembly())
                    //mapping response
                    .content(mapping)
                    .build();
    }
}
