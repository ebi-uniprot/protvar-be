package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.StabilityChange;

/**
 * Enables case-insensitive binding of StabilityChange from request parameters.
 * Only used for non-JSON inputs like @RequestParam, @PathVariable, etc.
 * For JSON bodies (@RequestBody), see the @JsonCreator in the enum.
 */
@Component // no need to manually register in config
public class StabilityChangeConverter implements Converter<String, StabilityChange> {
    @Override
    public StabilityChange convert(String source) {
        return StabilityChange.parse(source);
    }
}