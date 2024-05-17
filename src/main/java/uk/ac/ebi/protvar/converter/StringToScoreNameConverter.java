package uk.ac.ebi.protvar.converter;

import org.springframework.core.convert.converter.Converter;
import uk.ac.ebi.protvar.model.score.Score;

public class StringToScoreNameConverter implements Converter<String, Score.Name> {
    @Override
    public Score.Name convert(String source) {
        try {
            return Score.Name.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
