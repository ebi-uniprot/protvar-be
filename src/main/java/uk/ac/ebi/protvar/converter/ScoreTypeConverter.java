package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.model.score.ScoreType;

@Component
public class ScoreTypeConverter implements Converter<String, ScoreType> {
    @Override
    public ScoreType convert(String type) {
        try {
            return ScoreType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            //return null; // ambiguous; null could mean "no input" or "invalid input"
            // throw exception, let validation handle it and return 400 if needed
            throw new IllegalArgumentException("Invalid input type: " + type);
        }
    }
}
