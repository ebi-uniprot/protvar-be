package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataAccessException;
import uk.ac.ebi.protvar.model.response.UploadSummary;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.exception.QueryTooBroadException;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.GenomicVariant;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Isoform;
import uk.ac.ebi.protvar.repo.MappingRepo;
import uk.ac.ebi.protvar.repo.UploadVariantRepo;
import uk.ac.ebi.protvar.repo.UploadVariantRepo.Passing;
import uk.ac.ebi.protvar.repo.UploadVariantRepo.Row;
import uk.ac.ebi.protvar.repo.UploadVariantRepo.Upload;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.protvar.types.Codon;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filter/sort over a user upload. On the first filtered or sorted request the upload is materialised
 * into protvar_runtime.upload_variant (genomic rows tagged with their input line number); filter + sort +
 * paginate then run in SQL over passing lines, and the page's lines go through the usual InputMapper.
 * Small uploads build within the request; larger ones build in the background while the response says
 * "preparing" with a progress percentage.
 */
@Service
@RequiredArgsConstructor
public class UploadFilterService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadFilterService.class);

    private static final int CHUNK = 2000;          // lines per preprocess + insert batch
    private static final int SYNC_MAX = 5000;       // build within the request up to this many lines
    private static final int STALE_MINUTES = 10;    // a BUILDING row without heartbeat for this long is taken over
    private static final int KEEP_DAYS = 7;         // drop builds unused for this long (rebuilt on demand)

    private final UploadVariantRepo uploadVariantRepo;
    private final UploadCacheService uploadCacheService;
    private final InputMapper inputMapper;
    private final MappingRepo mappingRepo;
    private final AsyncTaskExecutor uploadFilterExecutor; // bean resolved by name (ExecutorConfig)

    @Value("${uniprot.release}")
    private String release;

    /** A page of passing input lines plus the rows that passed; or, while building, the progress. */
    public record Result(Page<VariantInput> page, Set<Passing> passing, Integer preparing) {
        static Result preparing(int pct) {
            return new Result(Page.empty(), Set.of(), pct);
        }
    }

    public static boolean applies(MappingRequest request) {
        return request.getResultId() != null && !request.getResultId().isBlank()
                && UploadVariantRepo.filtersOrSorts(request);
    }

    public Result get(MappingRequest request, InputBuild build) {
        String uploadId = request.getResultId();
        List<String> lines = uploadCacheService.getInput(uploadId);
        if (lines == null || lines.isEmpty()) return new Result(Page.empty(), Set.of(), null);

        String assembly = assembly(request.getAssembly());
        Upload u = ensureBuild(uploadId, assembly, lines, build).orElseThrow();
        if (UploadVariantRepo.BUILDING.equals(u.status())) {
            return Result.preparing(progress(u));
        }
        if (!UploadVariantRepo.READY.equals(u.status())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not prepare this upload for filtering; please retry.");
        }

        uploadVariantRepo.touch(u.key());
        PageRequest pageable = PageRequest.of(request.getPage() - 1, request.getPageSize());
        long total;
        List<Integer> pageLines;
        Set<Passing> passing;
        try {
            total = uploadVariantRepo.countLines(u.key(), request);
            if (total == 0) return new Result(Page.empty(pageable), Set.of(), null);
            pageLines = uploadVariantRepo.pageLines(u.key(), request, pageable.getOffset(), pageable.getPageSize());
            passing = new HashSet<>(uploadVariantRepo.passingRows(u.key(), request, pageLines));
        } catch (DataAccessException e) {
            if (!isStatementCancelled(e)) throw e;
            LOGGER.info("Upload filter query for {} exceeded {}s", uploadId, UploadVariantRepo.FILTER_TIMEOUT_SECONDS);
            throw new QueryTooBroadException(FILTER_TIMEOUT_MESSAGE);
        }
        List<VariantInput> inputs = pageLines.stream().map(idx -> parse(lines, idx)).toList();
        return new Result(new PageImpl<>(inputs, pageable, total), passing, null);
    }

    /**
     * Every passing line of a prepared upload, in result order, as chunks of lines with their passing rows
     * (for a full download; the download controller has checked the upload is prepared).
     */
    public Stream<Result> chunks(MappingRequest request, int chunkSize) {
        String uploadId = request.getResultId();
        List<String> lines = uploadCacheService.getInput(uploadId);
        if (lines == null || lines.isEmpty()) return Stream.empty();

        String assembly = request.getAssembly() == null ? "AUTO" : request.getAssembly();
        Upload u = uploadVariantRepo.find(uploadId, assembly, buildRelease(), STALE_MINUTES)
                .filter(x -> UploadVariantRepo.READY.equals(x.status()))
                .orElseThrow(() -> new IllegalStateException("Upload " + uploadId + " is not prepared for filtering"));
        uploadVariantRepo.touch(u.key());
        return Stream.iterate(0L, offset -> offset + chunkSize)
                .map(offset -> uploadVariantRepo.pageLines(u.key(), request, offset, chunkSize))
                .takeWhile(idx -> !idx.isEmpty())
                .map(idx -> new Result(new PageImpl<>(idx.stream().map(i -> parse(lines, i)).toList()),
                        new HashSet<>(uploadVariantRepo.passingRows(u.key(), request, idx)), null));
    }

    /**
     * Starts preparing an upload when it is submitted (in the background if large), so its summary and first
     * filter don't wait. Best effort: a busy executor just leaves it to the first request.
     */
    public void prepare(String uploadId, String assembly, InputBuild build) {
        List<String> lines = uploadCacheService.getInput(uploadId);
        if (lines == null || lines.isEmpty()) return;
        try {
            ensureBuild(uploadId, assembly(assembly), lines, build);
        } catch (ResponseStatusException e) {
            LOGGER.info("Upload {} not prepared on submit: {}", uploadId, e.getReason());
        }
    }

    /**
     * The upload's preparation: its progress while building, or once prepared its mapped/not mapped lines
     * and notes by code. Empty when there is no build (not started, or failed).
     */
    public Optional<UploadSummary> summary(String uploadId, String assembly) {
        return uploadVariantRepo.find(uploadId, assembly(assembly), buildRelease(), STALE_MINUTES)
                .flatMap(u -> switch (u.status()) {
                    case UploadVariantRepo.READY -> Optional.of(uploadVariantRepo.summary(u.key()));
                    case UploadVariantRepo.BUILDING -> Optional.of(UploadSummary.preparing(progress(u)));
                    default -> Optional.empty();
                });
    }

    private static int progress(Upload u) {
        int n = u.inputCount() == null || u.inputCount() == 0 ? 1 : u.inputCount();
        return Math.min(99, (int) (100L * u.linesDone() / n));
    }

    /**
     * The upload's build, claiming and running it when there is none (or it failed, or went stale). Small
     * uploads build here; large ones in the background, so the returned build may still be BUILDING.
     */
    private Optional<Upload> ensureBuild(String uploadId, String assembly, List<String> lines, InputBuild build) {
        Optional<Upload> upload = uploadVariantRepo.find(uploadId, assembly, buildRelease(), STALE_MINUTES);
        if (upload.isPresent() && !needsBuild(upload.get())) return upload;
        Optional<Integer> key = uploadVariantRepo.claim(uploadId, assembly, buildRelease(), lines.size(), STALE_MINUTES);
        if (key.isPresent()) {
            if (lines.size() <= SYNC_MAX) {
                build(key.get(), lines, assembly, build);
            } else {
                try {
                    uploadFilterExecutor.execute(() -> build(key.get(), lines, assembly, build));
                } catch (RejectedExecutionException e) {
                    uploadVariantRepo.fail(key.get());
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Too many uploads being prepared for filtering; please retry shortly.");
                }
            }
        }
        return uploadVariantRepo.find(uploadId, assembly, buildRelease(), STALE_MINUTES);
    }

    private static String assembly(String assembly) {
        return assembly == null ? "AUTO" : assembly;
    }

    /** The build's release key: builds copied from coding_variant are kept apart from old-format ones. */
    private String buildRelease() {
        return uploadVariantRepo.fromCodingVariant() ? release + UploadVariantRepo.CV_SUFFIX : release;
    }

    static final String FILTER_TIMEOUT_MESSAGE = "These filters took too long on this upload. "
            + "Please try again in a moment, or use fewer filters.";

    /** Postgres reports a statement timeout (and a cancel) as SQLState 57014. */
    private static boolean isStatementCancelled(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "57014".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    private static boolean needsBuild(Upload u) {
        return UploadVariantRepo.FAILED.equals(u.status())
                || (UploadVariantRepo.BUILDING.equals(u.status()) && u.stale());
    }

    /**
     * Keeps only the derived variants that passed: per derived genomic variant, the genes (one per alt)
     * whose isoforms include a passing row's accession; variants left without genes are dropped.
     */
    public void trim(List<VariantInput> inputs, Set<Passing> passing) {
        Set<String> keys = passing.stream()
                .map(p -> passKey(p.inputIdx(), p.chromosome(), p.position(), p.alt(), p.accession()))
                .collect(Collectors.toSet());
        for (VariantInput input : inputs) {
            input.getDerivedGenomicVariants().removeIf(gv -> {
                gv.getGenes().removeIf(gene -> gene.getIsoforms() == null || gene.getIsoforms().stream()
                        .noneMatch(iso -> keys.contains(passKey(input.getOriginalIndex(), gv.getChromosome(),
                                gv.getPosition(), gene.getAltAllele(), iso.getAccession()))));
                return gv.getGenes().isEmpty();
            });
        }
    }

    private static String passKey(int idx, String chr, Integer pos, String alt, String acc) {
        return idx + "|" + chr + "|" + pos + "|" + (alt == null ? "" : alt.toUpperCase()) + "|" + acc;
    }

    private static VariantInput parse(List<String> lines, int idx) {
        VariantInput input = VariantParser.parse(lines.get(idx));
        input.setOriginalIndex(idx);
        return input;
    }

    // ---------------------------------------------------------------- build

    void build(int key, List<String> lines, String assembly, InputBuild build) {
        long start = System.currentTimeMillis();
        try {
            int rows = 0;
            for (int from = 0; from < lines.size(); from += CHUNK) {
                int to = Math.min(from + CHUNK, lines.size());
                List<VariantInput> chunk = new ArrayList<>(to - from);
                for (int i = from; i < to; i++) chunk.add(parse(lines, i));
                inputMapper.preprocess(chunk, assembly, build);
                if (uploadVariantRepo.fromCodingVariant()) {
                    rows += uploadVariantRepo.insertFromCodingVariant(key, toRows(chunk, false));
                } else {
                    List<Row> out = toRows(chunk, true);
                    uploadVariantRepo.insert(key, out);
                    rows += out.size();
                }
                // after toRows: parsing, preprocessing and resolving alt bases have all added their notes
                uploadVariantRepo.insertLines(key, noteCodes(chunk));
                uploadVariantRepo.progress(key, to);
            }
            uploadVariantRepo.finish(key, rows);
            LOGGER.info("Upload filter build {}: {} lines → {} rows in {} ms",
                    key, lines.size(), rows, System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOGGER.error("Upload filter build {} failed", key, e);
            uploadVariantRepo.fail(key);
        }
    }

    /**
     * One row per derived genomic variant × alt × gene, on the gene's lead accession (canonical, else the
     * first isoform in natural accession order — the isoform the result list leads with). Without
     * {@code leads}, one row per derived variant × alt only (no protein columns): coding_variant supplies
     * the leads.
     */
    private List<Row> toRows(List<VariantInput> chunk, boolean leads) {
        Set<List<Object>> chrPos = new HashSet<>();
        for (VariantInput input : chunk) {
            if (!input.isValid()) continue;
            if (input instanceof GenomicInput g && input.getDerivedGenomicVariants().isEmpty()) {
                input.getDerivedGenomicVariants().add(g.toGenomicVariant());
            }
            for (GenomicVariant gv : input.getDerivedGenomicVariants()) {
                if (gv.getChromosome() != null && gv.getPosition() != null) {
                    chrPos.add(List.of(gv.getChromosome(), gv.getPosition()));
                }
            }
        }
        if (chrPos.isEmpty()) return List.of();

        String[] chrs = chrPos.stream().map(l -> (String) l.get(0)).toArray(String[]::new);
        Integer[] poss = chrPos.stream().map(l -> (Integer) l.get(1)).toArray(Integer[]::new);
        Map<String, List<GenomeToProteinMapping>> byPos = mappingRepo.getMappingsByChrPos(chrs, poss).stream()
                .collect(Collectors.groupingBy(GenomeToProteinMapping::getVariantKeyGenomic));

        Set<Row> rows = new LinkedHashSet<>();
        for (VariantInput input : chunk) {
            if (!input.isValid()) continue;
            for (GenomicVariant gv : input.getDerivedGenomicVariants()) {
                List<GenomeToProteinMapping> mappings = byPos.get(gv.getVariantKey());
                if (mappings == null || mappings.isEmpty()) continue; // unmapped: no protein to filter on
                String ref = mappings.get(0).getBaseNucleotide();
                if (!isBase(ref)) continue;
                Set<String> alts = inputMapper.resolveAltBases(input, gv, ref);
                if (!leads) {
                    for (String alt : alts) {
                        if (isBase(alt)) rows.add(new Row(input.getOriginalIndex(), gv.getChromosome(),
                                gv.getPosition(), ref.toUpperCase(), alt.toUpperCase(), null, 0, null, null));
                    }
                    continue;
                }

                Collection<List<GenomeToProteinMapping>> genes = mappings.stream()
                        .filter(m -> m.getAccession() != null)
                        .collect(Collectors.groupingBy(m -> Objects.toString(m.getEnsg(), ""))).values();
                for (List<GenomeToProteinMapping> gene : genes) {
                    GenomeToProteinMapping lead = gene.stream().min(LEAD_ORDER).orElseThrow();
                    for (String alt : alts) {
                        if (!isBase(alt)) continue;
                        AminoAcid mt = Codon.altAA(lead.getCodon(), lead.getCodonPosition(), alt, lead.isReverseStrand());
                        rows.add(new Row(input.getOriginalIndex(), gv.getChromosome(), gv.getPosition(),
                                ref.toUpperCase(), alt.toUpperCase(), lead.getAccession(), lead.getIsoformPosition(),
                                lead.getAa(), mt == null ? null : mt.getOneLetter()));
                    }
                }
            }
        }
        return new ArrayList<>(rows);
    }

    /** Each line's distinct note codes (line number → codes), for lines that have any. */
    static Map<Integer, List<String>> noteCodes(List<VariantInput> chunk) {
        Map<Integer, List<String>> codes = new LinkedHashMap<>();
        for (VariantInput input : chunk) {
            List<String> c = input.getMessages().stream().map(Message::getCode).filter(Objects::nonNull)
                    .distinct().toList();
            if (!c.isEmpty()) codes.put(input.getOriginalIndex(), c);
        }
        return codes;
    }

    private static boolean isBase(String b) {
        return b != null && b.length() == 1 && "ACGTacgt".contains(b);
    }

    // same lead rule as Isoform.compareTo and the coding_variant table: canonical, then -N (reviewed) isoforms, then rest
    static final Comparator<GenomeToProteinMapping> LEAD_ORDER =
            Comparator.comparing((GenomeToProteinMapping m) -> !m.isCanonical())
                    .thenComparing(m -> !Isoform.isReviewedIsoform(m.getAccession()))
                    .thenComparing(GenomeToProteinMapping::getAccession, Isoform::compareAccession);

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanUp() {
        try {
            int removed = uploadVariantRepo.deleteExpired(KEEP_DAYS, buildRelease());
            if (removed > 0) LOGGER.info("Upload filter clean-up: removed {} builds", removed);
        } catch (Exception e) {
            LOGGER.warn("Upload filter clean-up failed: {}", e.getMessage());
        }
    }
}
