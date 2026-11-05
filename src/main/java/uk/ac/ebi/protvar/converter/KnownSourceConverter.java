package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.KnownSource;

/**
 * Enables case-insensitive binding of KnownSource from request parameters.
 * Only used for non-JSON inputs like @RequestParam, @PathVariable, etc.
 * For JSON bodies (@RequestBody), see the @JsonCreator in the enum.
 */
@Component // no need to manually register in config
public class KnownSourceConverter implements Converter<String, KnownSource> {
    @Override
    public KnownSource convert(String source) {
        return KnownSource.parse(source);
    }
}