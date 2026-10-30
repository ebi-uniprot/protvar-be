package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Objects;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    public enum Type {
        INFO, // (?)
        WARN, // (!) for e.g. ref base or ref aa mismatch - > show a warning in icon tooltip
        ERROR // (X)
    }
    Type type;
    String text;
    String code; // catalogue code (MessageCode), e.g. PV-103; null for uncatalogued text

    public Message(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    @Override
    public String toString() {
        if (type != null && text != null) {
            return type + ":" + (code != null ? code + " " : "") + text;
        } else return Objects.requireNonNullElse(text, "");
    }
}
