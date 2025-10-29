package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.PopEveClass;

@Component
public class PopEveClassConverter implements Converter<String, PopEveClass> {
    @Override
    public PopEveClass convert(String source) {
        return PopEveClass.parse(source);
    }
}
