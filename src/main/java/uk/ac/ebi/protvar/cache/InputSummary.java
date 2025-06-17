package uk.ac.ebi.protvar.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputSummary {
    int totalCount;
    EnumMap<Type, Integer> inputCounts = new EnumMap<>(Type.class);


    @Override
    public String toString() {
        String pluralSuffix = FetcherUtils.pluralise(totalCount);
        List<Map.Entry<Type, Integer>> nonZeroInputs = inputCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .toList();

        if (nonZeroInputs.size() == 1) {
            // Only one type of input
            Type onlyInputType = nonZeroInputs.get(0).getKey();
            return String.format("%d %s input%s", totalCount, onlyInputType.getName(), pluralSuffix);
        } else {
            // Multiple input types
            String breakdown = nonZeroInputs.stream()
                    .map(entry -> String.format("%d %s", entry.getValue(), entry.getKey().getName()))
                    .collect(Collectors.joining(", "));

            return String.format("%d user input%s (%s)", totalCount, pluralSuffix, breakdown);
        }
    }
}
