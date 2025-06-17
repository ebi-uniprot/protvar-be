package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import uk.ac.ebi.protvar.types.IdentifierType;


public class StringToIdentifierTypeConverter implements Converter<String, IdentifierType> {
    @Override
    public IdentifierType convert(String type) {
        try {
            return IdentifierType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
