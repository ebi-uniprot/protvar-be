package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import uk.ac.ebi.protvar.types.InputType;

public class StringToInputTypeConverter implements Converter<String, InputType> {
    @Override
    public InputType convert(String type) {
        try {
            return InputType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
