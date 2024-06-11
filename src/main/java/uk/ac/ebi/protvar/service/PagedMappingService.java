package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class PagedMappingService {

    public final static int DEFAULT_PAGE = 1;
    public final static int DEFAULT_PAGE_SIZE = 25;
    private MappingService mappingService;

    public PagedMappingResponse newInput(String id, String requestBody) {
        // return first page result
        return getInputResult(id, requestBody, DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
    }

    public PagedMappingResponse getInputResult(String id, String input, int pageNo, int pageSize) {

        PagedMappingResponse response = new PagedMappingResponse();
        response.setResultId(id);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);

        List<String> inputs = Arrays.asList(input.split("\\R|,"));
        int totalElements = inputs.size();
        int totalPages = totalElements / pageSize + ((totalElements % pageSize == 0) ? 0 : 1);

        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);
        response.setLast(pageNo == totalPages);

        List<String> results = getPage(inputs, pageNo, pageSize);

        MappingResponse mappingContent = mappingService.getMapping(results, false, false, false, "AUTO");
        response.setContent(mappingContent);

        return response;
    }

    public List getPage(List sourceList, int page, int pageSize) {
        if(pageSize <= 0 || page <= 0) {
            return Collections.emptyList();
        }
        int fromIndex = (page - 1) * pageSize;
        if(sourceList == null || sourceList.size() <= fromIndex) {
            return Collections.emptyList();
        }
        // toIndex exclusive
        return sourceList.subList(fromIndex, Math.min(fromIndex + pageSize, sourceList.size()));
    }

}
