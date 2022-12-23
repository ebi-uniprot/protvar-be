package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class AssemblyMappingResponse {
    String from;
    String to;
    List<AssemblyMapping> mappings;
}