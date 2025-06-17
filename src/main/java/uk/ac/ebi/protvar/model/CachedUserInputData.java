package uk.ac.ebi.protvar.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;

import java.util.List;

@Data
@AllArgsConstructor
public class CachedUserInputData {
    private List<String> inputs;
    //private Map<String, UserInput> inputMap;
    private InputBuild build;
    private InputSummary summary;
}
