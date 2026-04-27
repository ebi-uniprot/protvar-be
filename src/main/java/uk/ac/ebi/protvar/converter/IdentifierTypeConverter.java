package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.types.IdentifierType;

@Component
public class IdentifierTypeConverter implements Converter<String, IdentifierType> {
    @Override
    public IdentifierType convert(String type) {
        try {
            return IdentifierType.fromString(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid identifier type: " + type);
        }
    }
}
