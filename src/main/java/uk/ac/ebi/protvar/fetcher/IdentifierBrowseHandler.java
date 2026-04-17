package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.repo.MappingRepo;

import java.util.List;
import java.util.stream.Stream;

/**
 * Handles browse queries for biological identifiers (UniProt, Gene, PDB, Ensembl, RefSeq).
 * Queries the mapping database and returns genomic-level variant results.
 */
@Service
@AllArgsConstructor
public class IdentifierBrowseHandler implements InputHandler {
    private static final int FULL_INPUT_CHUNK_SIZE = 6000;
    private MappingRepo mappingRepo;

    public Page<VariantInput> pagedInput(MappingRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getPageSize());
        return mappingRepo.getGenomicVariantsForInput(request, pageable);
    }

    public Stream<List<VariantInput>> streamChunkedInput(MappingRequest request) {
        return Stream.iterate(0, page -> page + 1)
                .map(page ->
                        mappingRepo.getGenomicVariantsForInput(request, PageRequest.of(page, FULL_INPUT_CHUNK_SIZE))
                                .getContent()
                )
                .takeWhile(batch -> !batch.isEmpty());
    }
}
