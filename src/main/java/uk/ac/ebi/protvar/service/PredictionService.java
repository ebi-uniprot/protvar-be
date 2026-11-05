package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.response.PredictionInfo;
import uk.ac.ebi.protvar.model.score.*;
import uk.ac.ebi.protvar.repo.FoldxRepo;
import uk.ac.ebi.protvar.repo.InteractionRepo;
import uk.ac.ebi.protvar.repo.PocketRepo;
import uk.ac.ebi.protvar.repo.ScoreRepo;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.*;

/**
 * Everything predicted at a protein position, from the same repos the download uses: the score tables in one
 * transaction (ScoreRepo), then pockets, interfaces and FoldX. Only the requested keys are queried.
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    /** The prediction keys: the response's field names and the values of ?include=. */
    public static final List<String> KEYS =
            List.of("conserv", "pocket", "interface", "alphamissense", "popeve", "esm", "eve", "foldx", "m3d");

    private static final Map<String, ScoreType> SCORE_KEYS = Map.of(
            "conserv", ScoreType.CONSERV, "alphamissense", ScoreType.AM, "popeve", ScoreType.POPEVE,
            "esm", ScoreType.ESM, "eve", ScoreType.EVE, "m3d", ScoreType.M3D);

    private final ScoreRepo scoreRepo;
    private final PocketRepo pocketRepo;
    private final InteractionRepo interactionRepo;
    private final FoldxRepo foldxRepo;

    /**
     * @param mt      one-letter variant amino acid, or null for all
     * @param include keys to return (subset of {@link #KEYS}); empty = all
     */
    public PredictionInfo get(String acc, int pos, String mt, Set<String> include) {
        Set<String> keys = include == null || include.isEmpty() ? new HashSet<>(KEYS) : include;
        String key = VariantKey.protein(acc, pos);

        Set<ScoreType> types = EnumSet.noneOf(ScoreType.class);
        SCORE_KEYS.forEach((k, t) -> { if (keys.contains(k)) types.add(t); });
        List<Score> scores = types.isEmpty() ? List.of()
                : scoreRepo.getScores(new String[]{acc}, new Integer[]{pos}, types).stream()
                    .filter(s -> mt == null || s.getMt() == null || mt.equalsIgnoreCase(s.getMt()))  // conserv has no mt
                    .toList();

        return PredictionInfo.builder()
                .accession(acc).position(pos).variantAA(mt)
                .conserv(of(scores, ConservScore.class).stream().findFirst().orElse(null))
                .alphamissense(of(scores, AmScore.class))
                .popeve(of(scores, PopEveScore.class))
                .esm(of(scores, EsmScore.class))
                .eve(of(scores, EveScore.class))
                .m3d(of(scores, M3DPred.class))
                .pocket(keys.contains("pocket") ? pocketRepo.getPockets(acc, pos).getOrDefault(key, List.of()) : null)
                .interfaces(keys.contains("interface")
                        ? interactionRepo.getInteractions(acc, pos).getOrDefault(key, List.of()) : null)
                .foldx(keys.contains("foldx") ? foldxRepo.getFoldxs(acc, pos, mt).values().stream()
                        .flatMap(List::stream).sorted(Comparator.comparing(Foldx::getMutatedType)).toList() : null)
                .build();
    }

    private static <T extends Score> List<T> of(List<Score> scores, Class<T> type) {
        return scores.stream().filter(type::isInstance).map(type::cast)
                .sorted(Comparator.comparing(s -> Objects.toString(s.getMt(), ""))).toList();
    }
}
