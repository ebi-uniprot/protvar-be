package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import static uk.ac.ebi.protvar.config.PagedMapping.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class PagedMappingService {
    private MappingService mappingService;

    private ProtVarDataRepo protVarDataRepo;
    private MappingFetcher mappingFetcher;


    public PagedMappingResponse newInput(String id, String requestBody, String assembly) {
        // return first page result
        return getInputResult(id, requestBody, DEFAULT_PAGE, DEFAULT_PAGE_SIZE, assembly);
    }

    public PagedMappingResponse getInputResult(String id, String input, int pageNo, int pageSize, String assembly) {

        PagedMappingResponse response = new PagedMappingResponse();
        response.setId(id);
        response.setPage(pageNo);
        response.setPageSize(pageSize);

        List<String> inputs = Arrays.asList(input.split("\\R|,"));
        int totalElements = inputs.size();
        int totalPages = totalElements / pageSize + ((totalElements % pageSize == 0) ? 0 : 1);

        response.setTotalItems(totalElements);
        response.setTotalPages(totalPages);
        response.setLast(pageNo == totalPages);

        List<String> results = getPage(inputs, pageNo, pageSize);

        MappingResponse mappingContent = mappingService.getMapping(results, false, false, false, assembly);
        response.setContent(mappingContent);

        return response;
    }

    public List getPage(List sourceList, int pageNo, int pageSize) {
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
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        // Retrieve a page of chr-pos for accession
        Page<UserInput> page = protVarDataRepo.getGenInputsByAccession(accession, pageable);
        // Get content for page object
        List<UserInput> inputs = page.getContent();

        List<OptionBuilder.OPTIONS> options = OptionBuilder.build(false, false, false);
        MappingResponse content = mappingFetcher.getMappings(inputs, options, "38");

        PagedMappingResponse response = new PagedMappingResponse();
        response.setContent(content);
        response.setPage(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalItems(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLast(page.isLast());
        return response;
    }

}
