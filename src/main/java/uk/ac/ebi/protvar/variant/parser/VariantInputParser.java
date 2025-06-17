package uk.ac.ebi.protvar.variant.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.variant.VariantInput;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class VariantInputParser {
    public List<VariantInput> parseInputs(List<String> rawInputs) {
        if (rawInputs == null || rawInputs.isEmpty()) {
            return List.of(); // Or throw exception if required
        }

        return IntStream.range(0, rawInputs.size())
                .mapToObj(i -> VariantInput.parse(i, rawInputs.get(i)))
                .toList();
    }
}
