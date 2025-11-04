package uk.ac.ebi.protvar.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.model.SearchTerm;

import java.util.List;

/**
 * Enables Spring to convert a JSON string in form-data (application/x-www-form-urlencoded)
 * into a List<SearchTerm> when binding to @ModelAttribute or form submissions.
 */
@Component
@RequiredArgsConstructor
public class SearchTermListConverter implements Converter<String, List<SearchTerm>> {

    private final ObjectMapper objectMapper;

    @Override
    public List<SearchTerm> convert(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(source, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid JSON format for searchTerms. Expected JSON array of {value, type}. Got: " + source, e
            );
        }
    }
}