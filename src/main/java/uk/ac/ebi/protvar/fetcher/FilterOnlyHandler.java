package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.repo.GenomicVariantRepo;

import java.util.List;
import java.util.stream.Stream;

/**
 * Handles filter-only browse — no identifier provided, just primary filter(s).
 * Routes through GenomicVariantRepo (4-strategy optimiser) so the bounded
 * leading-table approach is used instead of the full-table-scan path in
 * MappingRepo.
 */
@Service
@AllArgsConstructor
public class FilterOnlyHandler implements InputHandler {
    private static final int FULL_INPUT_CHUNK_SIZE = 6000;
    private GenomicVariantRepo genomicVariantRepo;

    public Page<VariantInput> pagedInput(MappingRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getPageSize());
        return genomicVariantRepo.get(request, pageable);
    }

    public Stream<List<VariantInput>> streamChunkedInput(MappingRequest request) {
        return Stream.iterate(0, page -> page + 1)
                .map(page ->
                        genomicVariantRepo.get(request, PageRequest.of(page, FULL_INPUT_CHUNK_SIZE))
                                .getContent()
                )
                .takeWhile(batch -> !batch.isEmpty());
    }
}
