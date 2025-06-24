package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.constants.PageUtils;
import uk.ac.ebi.protvar.fetcher.UserInputHandler;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.processor.UserInputParser;
import uk.ac.ebi.protvar.mapper.ProteinInputMapper;
import uk.ac.ebi.protvar.mapper.UserInputMapper;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.UserInputRequest;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MappingService {
    private final UserInputService userInputService;
    private final UserInputCacheService userInputCacheService;
    private final UserInputHandler userInputHandler; // get the input
    private final UserInputMapper userInputMapper;   // do the mapping logic
    private final ProteinInputMapper proteinInputMapper;

    // Any of the supported variant input format (e.g. genomic, protein, coding or variant id)
    public MappingResponse get(String singleVariant, String assembly) {
        InputParams params = InputParams.builder()
                .inputs(UserInputParser.parse(List.of(singleVariant)))
                .assembly(assembly)
                .build();
        return userInputMapper.getMapping(params);
    }

    public PagedMappingResponse get(MappingRequest request) {
        Page<UserInput> page = userInputHandler.getPage(request);
        //mappingRepo.getGenInputsByAccession(accession, pageable);

        List<UserInput> inputs = page.getContent();
        InputParams params = InputParams.builder()
                .inputs(inputs)
                .build(); // default values for annotations will be false
        // for function, population and structure

        MappingResponse content = userInputMapper.getMapping(params);
        // proteinInputMapping.getMappings(accession, params);

        PagedMappingResponse response = PagedMappingResponse.builder()
                                            .page(request.getPage()) //(page.getNumber())
                                            .pageSize(page.getSize())
                                            .totalItems(page.getTotalElements())
                                            .totalPages(page.getTotalPages())
                                            .last(page.isLast())
                                            .build();
        response.setContent(content);

        if (response != null) {
            response.setId(request.getInput());
        }
        return response;
    }


     // InputCache
    // id::
    // key-value
    // gen - chr pos ref alt
    // prot - ...
    //
    /*
    WITH user_input (chr, pos, ref, alt, input) AS (
        VALUES :inputList
    )
    SELECT * FROM %s -- mapping table
    JOIN user_input ON chromosome = user_input.chr
      AND position = user_input.pos
      AND ref = user_input.ref
      AND alt = user_input.alt

     */
    /* MIXED INPUTS */
    // Input ID will have mixed inputs
    public PagedMappingResponse getInputIdMapping(String inputId, int pageNo, int pageSize, String assembly) {
        List<String> inputList = userInputCacheService.getInputs(inputId);
        if (inputList == null || inputList.isEmpty())
            return null;

        // ensure that the build is detected and cached
        userInputService.detectBuild(UserInputRequest.builder()
                .inputId(inputId)
                .assembly(assembly)
                .build());

        InputBuild inputBuild = userInputCacheService.getBuild(inputId);

        List<String> inputs = PageUtils.getPage(inputList, pageNo, pageSize);

        InputParams params = InputParams.builder()
                .id(inputId)
                .inputs(UserInputParser.parse(inputs))
                .assembly(assembly)
                .inputBuild(inputBuild)
                .summarise(true) // is this needed?
                .build();

        int totalItems = inputList.size();
        int totalPages = totalItems / pageSize + ((totalItems % pageSize == 0) ? 0 : 1);
        PagedMappingResponse response = PagedMappingResponse.builder()
                                            .page(pageNo)
                                            .pageSize(pageSize)
                                            .totalItems(totalItems)
                                            .totalPages(totalPages)
                                            .last(pageNo == totalPages)
                                            .build();
        response.setId(inputId);
        response.setAssembly(assembly);

        MappingResponse mappingResponse = userInputMapper.getMapping(params);
        if (mappingResponse != null) {
            if (mappingResponse.getMessages() == null)
                mappingResponse.setMessages(new ArrayList<>());

            // Add build message
            if (inputBuild != null && inputBuild.getMessage() != null)
                mappingResponse.getMessages().add(inputBuild.getMessage());

            // Add input summary
            InputSummary inputSummary = userInputCacheService.getSummary(inputId);
            String summary = inputSummary != null
                    ? inputSummary.toString()
                    // probably still generating.. use basic summary
                    : String.format("%d user input%s ", inputList.size(), FetcherUtils.pluralise(inputList.size()));

            mappingResponse.getMessages().add(0, new Message(Message.MessageType.INFO, summary));
        }
        response.setContent(mappingResponse);
        return response;
    }
}
