package uk.ac.ebi.protvar.converter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import uk.ac.ebi.protvar.types.*;

/**
 * Centralized converter configuration for all enum types.
 * These converters enable Spring to bind enum values from request parameters
 * (e.g., @RequestParam, @PathVariable, form data).
 *
 * For JSON request bodies (@RequestBody), Jackson uses the @JsonCreator
 * methods defined in each enum class.
 */
@Configuration
public class ConverterConfiguration {

    @Bean
    public Converter<String, CaddCategory> caddCategoryConverter() {
        return new Converter<String, CaddCategory>() {
            @Override
            public CaddCategory convert(String source) {
                return CaddCategory.parse(source);
            }
        };
    }

    @Bean
    public Converter<String, StabilityChange> stabilityChangeConverter() {
        return new Converter<String, StabilityChange>() {
            @Override
            public StabilityChange convert(String source) {
                return StabilityChange.parse(source);
            }
        };
    }

    @Bean
    public Converter<String, AlleleFreqCategory> alleleFreqCategoryConverter() {
        return new Converter<String, AlleleFreqCategory>() {
            @Override
            public AlleleFreqCategory convert(String source) {
                return AlleleFreqCategory.parse(source);
            }
        };
    }

    @Bean
    public Converter<String, AmClass> amClassConverter() {
        return new Converter<String, AmClass>() {
            @Override
            public AmClass convert(String source) {
                return AmClass.parse(source);
            }
        };
    }

    @Bean
    public Converter<String, PopEveClass> popEveClassConverter() {
        return new Converter<String, PopEveClass>() {
            @Override
            public PopEveClass convert(String source) {
                return PopEveClass.parse(source);
            }
        };
    }

    @Bean
    public Converter<String, SearchType> searchTypeConverter() {
        return new Converter<String, SearchType>() {
            @Override
            public SearchType convert(String source) {
                return SearchType.parse(source);
            }
        };
    }
}