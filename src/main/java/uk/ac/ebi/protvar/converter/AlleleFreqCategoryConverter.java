package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.AlleleFreqCategory;

@Component
public class AlleleFreqCategoryConverter implements Converter<String, AlleleFreqCategory> {
    @Override
    public AlleleFreqCategory convert(String source) {
        return AlleleFreqCategory.parse(source);
    }
}