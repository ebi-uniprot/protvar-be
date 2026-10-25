package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.cache.UniprotAccessionCache;
import uk.ac.ebi.protvar.model.Identifier;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.repo.MappingRepo;
import uk.ac.ebi.protvar.service.MappingService;
import uk.ac.ebi.protvar.types.IdentifierType;
import uk.ac.ebi.protvar.utils.InputTypeResolver;
import uk.ac.ebi.protvar.utils.MappingRequestValidator;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.ac.ebi.protvar.constants.PageUtils.*;

@Tag(name = "Mapping")
@RestController
@RequestMapping("/mapping")
@CrossOrigin
@RequiredArgsConstructor
public class MappingController {

    private final MappingService mappingService;
    private final MappingRepo mappingRepo;
    private final UniprotAccessionCache uniprotAccessionCache;

    private static final String BROWSE_SUMMARY = "Browse and filter variants";
    private static final String BROWSE_DESC = "Browse by identifier (ids: UniProt, gene, PDB, Ensembl, RefSeq), or filter all variants. "
            + "Filters (e.g. known, ptm, cadd, am) and sort apply to browse only, not to q or resultId.";

    /**
     * Direct variant query: GET /mapping?q=19-1010539-G-C[&assembly=GRCh38]
     */
    @Operation(summary = "Map a single variant")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedMappingResponse> queryVariant(
            @Parameter(description = "Variant query in any supported format.", example = "19-1010539-G-C")
            @RequestParam String q,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        MappingRequest request = MappingRequest.builder()
                .q(q)
                .assembly(assembly)
                .page(1)
                .pageSize(1)
                .build();
        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }

    /**
     * Plain-text accession lists for linking to ProtVar.
     * <ul>
     *   <li>{@code all} — every canonical accession in the current release's
     *       {@code uniprot_entry} table (the intended input to the mapping
     *       import). Served from the in-memory cache loaded at startup.</li>
     *   <li>{@code mapped} — canonical accessions with at least one row in the
     *       mapping table (filtered via {@code is_canonical = true} so the
     *       set matches the canonical-only {@code uniprot_entry} list).
     *       {@code @Cacheable} on the repo method; the first request after a
     *       deploy pays the full-scan cost, subsequent requests are Redis-
     *       fast.</li>
     *   <li>{@code notmapped} (alias {@code unmapped}) — {@code all − mapped}: canonicals in the release
     *       that have no mapping. Reconstructs the {@code notMappedUniprot.txt}
     *       artifact the import writes. Computed at request time over the two
     *       cached lists (cheap set diff).</li>
     * </ul>
     */
    @Operation(summary = "Reviewed (Swiss-Prot) accessions: all, mapped or not mapped",
            description = "Canonical Swiss-Prot accessions in this release, one per line. "
                    + "mapped = at least one position mapped to GRCh38; notmapped = none. Counts match the release statistics.")
    @GetMapping(value = "/accessions/{kind}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> accessions(
            @Parameter(description = "all, mapped or notmapped", example = "mapped")
            @PathVariable("kind") String kind) {
        Collection<String> list = switch (kind.toLowerCase()) {
            case "all" -> uniprotAccessionCache.getCanonicalAccessions();
            case "mapped" -> mappingRepo.getMappedAccessions();
            case "notmapped", "unmapped" -> { // unmapped: original name, kept for existing links
                Set<String> mapped = new HashSet<>(mappingRepo.getMappedAccessions());
                yield uniprotAccessionCache.getCanonicalAccessions().stream()
                        .filter(acc -> !mapped.contains(acc))
                        .collect(Collectors.toList());
            }
            default -> null;
        };
        if (list == null) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("kind must be one of: all, mapped, notmapped");
        }
        return ResponseEntity.ok(String.join("\n", list));
    }

    /**
     * Uploaded result lookup: GET /mapping/{resultId}
     */
    @Operation(summary = "Get mappings for a submitted input")
    @GetMapping(value = "/{resultId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedMappingResponse> queryResult(
            @Parameter(description = "inputId from /input.", example = "148095764c8e06da401a29408e3d19ec")
            @PathVariable("resultId") String resultId,
            @Parameter(description = MappingRequest.PAGE_DESC, example = PAGE)
            @RequestParam(value = "page", defaultValue = PAGE, required = false) Integer page,
            @Parameter(description = MappingRequest.PAGE_SIZE_DESC, example = PAGE_SIZE)
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) Integer pageSize,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {

        if (page < 1) page = DEFAULT_PAGE;
        if (pageSize < PAGE_SIZE_MIN || pageSize > PAGE_SIZE_MAX) pageSize = DEFAULT_PAGE_SIZE;

        MappingRequest request = MappingRequest.builder()
                .resultId(resultId)
                .page(page)
                .pageSize(pageSize)
                .assembly(assembly)
                .build();
        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }

    @Operation(summary = BROWSE_SUMMARY, description = BROWSE_DESC)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingJson(@Valid @RequestBody MappingRequest request) {
        return handleRequest(request);
    }

    @Operation(summary = BROWSE_SUMMARY, description = BROWSE_DESC)
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingForm(@Valid @RequestBody @ModelAttribute MappingRequest request) {
        return handleRequest(request);
    }

    /**
     * Central dispatch for POST /mapping requests.
     * Routing priority: resultId → ids[] → q → filter-only.
     * Identifier types with null type are auto-detected; ambiguous values fall back to GENE.
     */
    public ResponseEntity<?> handleRequest(MappingRequest request) {
        Optional<String> validationError = MappingRequestValidator.validate(request);
        if (validationError.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError.get()));
        }

        // resultId and q route directly — no pre-processing needed
        if (request.getResultId() != null || request.getQ() != null) {
            return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
        }

        // ids[]: auto-detect missing types
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            List<Identifier> resolvedIds = request.getIds().stream()
                    .map(id -> {
                        if (id.type() != null) return id;
                        IdentifierType detected = InputTypeResolver.resolveIdentifier(id.value());
                        return new Identifier(detected != null ? detected : IdentifierType.GENE, id.value());
                    })
                    .collect(Collectors.toList());
            request.setIds(resolvedIds);
        }

        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }
}
