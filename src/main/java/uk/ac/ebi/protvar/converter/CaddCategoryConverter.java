package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.CaddCategory;

/**
 * Enables case-insensitive binding of CaddCategory from request parameters.
 * Only used for non-JSON inputs like @RequestParam, @PathVariable, etc.
 * For JSON bodies (@RequestBody), see the @JsonCreator in the enum.
 */
@Component // no need to manually register in config
public class CaddCategoryConverter implements Converter<String, CaddCategory> {
    @Override
    public CaddCategory convert(String source) {
        return CaddCategory.parse(source);
    }
}