package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.CuratedCategory;

/**
 * Enables case-insensitive binding of CuratedCategory from request parameters.
 * Only used for non-JSON inputs like @RequestParam, @PathVariable, etc.
 * For JSON bodies (@RequestBody), see the @JsonCreator in the enum.
 */
@Component // no need to manually register in config
public class CuratedCategoryConverter implements Converter<String, CuratedCategory> {
    @Override
    public CuratedCategory convert(String source) {
        return CuratedCategory.parse(source);
    }
}