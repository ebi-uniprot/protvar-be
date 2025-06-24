package uk.ac.ebi.protvar.fetcher;

import org.springframework.data.domain.Page;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.MappingRequest;

import java.util.List;
import java.util.stream.Stream;

public interface InputHandler {

    // todo: change to take an InputParam object? with inputId (type=INPUT_ID), page, pageSize

    // For paginated UI/API
    // TODO DownloadParam should be made UserInputParam(orRequest)
    // UserInputRequest<----<extends>-----DownloadRequest
    // pre-requisite: DownloadParam must have inputId (isInputId() == true)
    Page<UserInput> pagedInput(MappingRequest request);


    // Stream chunks for download
    Stream<List<UserInput>> streamChunkedInput(MappingRequest request); // for full input
}
