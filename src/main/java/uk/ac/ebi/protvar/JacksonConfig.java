package uk.ac.ebi.protvar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import uk.ac.ebi.uniprot.domain.entry.comment.Comment;
import uk.ac.ebi.uniprot.mapper.CommentDeserializer;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Register custom polymorphic deserializer
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Comment.class, new CommentDeserializer());
        objectMapper.registerModule(module);

        return objectMapper;
    }
}