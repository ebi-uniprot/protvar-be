package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import uk.ac.ebi.protvar.model.ResultType;

public class StringToResultTypeConverter implements Converter<String, ResultType> {
    @Override
    public ResultType convert(String type) {
        try {
            return ResultType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
