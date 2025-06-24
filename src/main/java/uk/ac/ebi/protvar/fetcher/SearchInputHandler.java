package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.repo.MappingRepo;

import java.util.List;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class SearchInputHandler implements InputHandler {
    // returns genomic-level variants (chr pos ref alt x3)
    // for the largest protein, ~30k AA, that would be at most ~90k genomic variants
    // (depending on filters), so about 18 chunks if chunk size is 5000
    private static final int SEARCH_INPUT_CHUNK_SIZE = 6000;
    private MappingRepo mappingRepo;


    // For UI/API and paged download
    public Page<UserInput> pagedInput(MappingRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getPageSize()); // page (0-based), size
        return mappingRepo.getGenomicVariantsForInput(request, pageable);
    }

    // For (full?) download
    public Stream<List<UserInput>> streamChunkedInput(MappingRequest request) {
        return Stream.iterate(0, page -> page + 1)
                .map(page ->
                        mappingRepo.getGenomicVariantsForInput(request, PageRequest.of(page, SEARCH_INPUT_CHUNK_SIZE))
                                .getContent()
                )
                .takeWhile(batch -> !batch.isEmpty());
    }
}
