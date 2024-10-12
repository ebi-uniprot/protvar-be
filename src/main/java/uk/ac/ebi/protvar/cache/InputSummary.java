package uk.ac.ebi.protvar.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputSummary implements Serializable {
    int totalCount;
    EnumMap<Type, Integer> inputCounts = new EnumMap<>(Type.class);


    @Override
    public String toString() {
        String s = FetcherUtils.pluralise(totalCount);

        // Check if only one input type has a non-zero count
        boolean singleInputType = inputCounts.values().stream()
                .filter(count -> count > 0)
                .count() == 1;

        if (singleInputType) {
            Type singleInputTypeKey = inputCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .findFirst()
                    .map(Map.Entry::getKey)
                    .orElse(Type.INVALID);
            return String.format("%d %s input%s", totalCount, singleInputTypeKey.getName(), s);
        } else {
            // Filter out types with zero counts
            String summary = inputCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(entry -> String.format("%d %s", entry.getValue(), entry.getKey().getName()))
                    .collect(Collectors.joining(", "));

            return String.format("%d user input%s (%s)", totalCount, s, summary);
        }
    }
}
