package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.CaddCategory;

@Component // no need to manually register in config
public class CaddCategoryConverter implements Converter<String, CaddCategory> {
    @Override
    public CaddCategory convert(String source) {
        if (source == null) return null;

        try {
            return CaddCategory.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid CADD category: " + source);
        }
    }
}