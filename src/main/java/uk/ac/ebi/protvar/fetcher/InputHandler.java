package uk.ac.ebi.protvar.fetcher;

import org.springframework.data.domain.Page;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.model.MappingRequest;

import java.util.List;
import java.util.stream.Stream;

public interface InputHandler {


    // For paginated UI/API
    Page<VariantInput> pagedInput(MappingRequest request);


    // Stream chunks for download
    Stream<List<VariantInput>> streamChunkedInput(MappingRequest request); // for full input
}
