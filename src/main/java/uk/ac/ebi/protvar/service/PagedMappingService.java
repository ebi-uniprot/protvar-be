package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputCache;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.processor.BuildProcessor;
import uk.ac.ebi.protvar.input.processor.InputProcessor;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.EnsemblIDValidator;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.util.*;

@Service
@AllArgsConstructor
public class PagedMappingService {
    private ProtVarDataRepo protVarDataRepo;
    private MappingFetcher mappingFetcher;
    private InputCache inputCache;

    private BuildProcessor buildProcessor;

    public PagedMappingResponse getInputResult(String id, int pageNo, int pageSize, String assembly) {
        String originalInput = inputCache.getInput(id);
        if (originalInput == null)
            return null;

        List<String> originalInputList = Arrays.asList(originalInput.split("\\R|,"));
        int totalElements = originalInputList.size();
        int totalPages = totalElements / pageSize + ((totalElements % pageSize == 0) ? 0 : 1);

        PagedMappingResponse response = new PagedMappingResponse();
        response.setId(id);
        response.setPage(pageNo);
        response.setPageSize(pageSize);
        response.setAssembly(assembly);
        response.setTotalItems(totalElements);
        response.setTotalPages(totalPages);
        response.setLast(pageNo == totalPages);

        /** Accepted values: The assembly can be one of the following:
         *  - null, empty, anything else (treated as null or not provided)
         *  - AUTO, 37, or 38
         *
         *  Rules:
         *  - Use auto-detection only if explicitly specified as AUTO.
         *  - Perform build conversion if 37 is specified.
         *  - Default to 38 in all other cases, including when auto-detection is inconclusive.
         *
         *  Steps:
         *  1. Check the user-specified assembly.
         *  2. If AUTO, check if the input ID has a cached detected build;
         *      if yes, use it, otherwise, detect, cache, and use the detected build.
         *  3. If 37, convert to 38.
         *  4. If 38, no conversion is needed.
         *  5. In all other cases, no conversion is required. Defaults to 38.
         *
         *  Note:
         *  - BuildProcessor always converts hgvsGs37 inputs, if any.
         *  - The same input ID may be requested with a different assembly parameter in another
         *    request, so always verify the submitted assembly.
         */
        List<String> inputs = getPage(originalInputList, pageNo, pageSize);
        InputBuild inputBuild = buildProcessor.determinedBuild(id, originalInputList, assembly);
        InputParams params = InputParams.builder()
                .id(id)
                .inputs(InputProcessor.parse(inputs))
                .assembly(assembly)
                .inputBuild(inputBuild)
                .summarise(true)
                .build();

        MappingResponse mappingContent = mappingFetcher.getMapping(params);
        if (inputBuild != null && inputBuild.getMessage() != null) {
            mappingContent.getMessages().add(inputBuild.getMessage());
        }
        response.setContent(mappingContent);

        // Post-fetch: add input summary to response
        InputSummary inputSummary = inputCache.getInputSummary(id);
        String summary;
        if (inputSummary == null) { // probably still calculating...
            // use tmp/basic summary
            summary = String.format("%d user input%s ", originalInputList.size(), FetcherUtils.pluralise(originalInputList.size()));
        } else {
            summary = inputSummary.toString();
        }
        mappingContent.getMessages().add(0, new Message(Message.MessageType.INFO, summary));

        long ttl = inputCache.expires(id);
        response.setTtl(ttl);

        return response;
    }

    public static List getPage(List sourceList, int pageNo, int pageSize) {
        if(pageSize <= 0 || pageNo <= 0) {
            return Collections.emptyList();
        }
        int fromIndex = (pageNo - 1) * pageSize;
        if(sourceList == null || sourceList.size() <= fromIndex) {
            return Collections.emptyList();
        }
        // toIndex exclusive
        return sourceList.subList(fromIndex, Math.min(fromIndex + pageSize, sourceList.size()));
    }


    public PagedMappingResponse getMappingByAccession(String accession, int pageNo, int pageSize) {
        // Create a Pageable instance
        Pageable pageable = PageRequest.of(pageNo-1, pageSize);
        // Retrieve a page of chr-pos for accession
        Page<UserInput> page = protVarDataRepo.getGenInputsByAccession(accession, pageable);
        // Get content for page object
        List<UserInput> inputs = page.getContent();
        InputParams params = InputParams.builder()
                .inputs(inputs)
                .build(); // default values for annotations will be false
        // for function, population and structure

        MappingResponse content = mappingFetcher.getGenMappings(params);

        PagedMappingResponse response = newPagedMappingResponse(pageNo, page);
        response.setContent(content);
        return response;
    }


    public PagedMappingResponse getMappingByEnsemblID(String id, int pageNo, int pageSize) {
        // Create a Pageable instance
        Pageable pageable = PageRequest.of(pageNo-1, pageSize);

        if (!EnsemblIDValidator.isValidEnsemblID(id)) {
            PagedMappingResponse response = newPagedMappingResponse(pageNo, Page.empty());
            MappingResponse content = new MappingResponse(List.of());
            content.getMessages().add(new Message(Message.MessageType.ERROR, "Invalid Ensembl ID"));
            response.setContent(content);
            return response;
        }

        // Retrieve a page of chr-pos for accession
        Page<UserInput> page = protVarDataRepo.getGenInputsByEnsemblID(id, pageable);
        // Get content for page object
        List<UserInput> inputs = page.getContent();
        InputParams params = InputParams.builder()
                .inputs(inputs)
                .build(); // default values for annotations will be false
        // for function, population and structure

        MappingResponse content = mappingFetcher.getGenMappings(params);

        PagedMappingResponse response = newPagedMappingResponse(pageNo, page);
        response.setContent(content);
        return response;
    }

    private PagedMappingResponse newPagedMappingResponse(int pageNo, Page<?> page) {
        PagedMappingResponse response = new PagedMappingResponse();
        response.setPage(pageNo);//(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalItems(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLast(page.isLast());
        return response;
    }
}
