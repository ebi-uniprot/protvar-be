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
}
