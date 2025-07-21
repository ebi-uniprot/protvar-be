package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.AmClass;

@Component // no need to manually register in config
public class AmClassConverter implements Converter<String, AmClass> {
    @Override
    public AmClass convert(String source) {
        return AmClass.parse(source);
    }
}
