package uk.ac.ebi.protvar;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.ac.ebi.protvar.converter.StringToResultTypeConverter;
import uk.ac.ebi.protvar.converter.StringToScoreNameConverter;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToScoreNameConverter());
        registry.addConverter(new StringToResultTypeConverter());
    }
}
