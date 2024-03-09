package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.processor.InputProcessor;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;

import java.util.List;

@Service
@AllArgsConstructor
public class MappingService {
    private ProtVarDataRepo protVarDataRepo;
    private MappingFetcher mappingFetcher;

    private InputProcessor inputProcessor;
    public MappingResponse getMapping(List<String> inputs, boolean function, boolean variation, boolean structure, String assemblyVersion) {
        if (inputs == null || inputs.isEmpty())
            return new MappingResponse();

        List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, variation, structure);
        // Step 1a - parse input strings into UserInput objects - of specific type and format
        List<UserInput> userInputs = inputProcessor.parse(inputs);
        return mappingFetcher.getMappings(userInputs, options, assemblyVersion);
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
        response.setPageNo(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLast(page.isLast());
        return response;
    }

}
