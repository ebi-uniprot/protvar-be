package uk.ac.ebi.protvar.model.response;

import lombok.*;

import java.util.Objects;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    public enum MessageType {
        INFO, // (?)
        WARN, // (!) for e.g. ref base or ref aa mismatch - > show a warning in icon tooltip
        ERROR // (X)
    }
    MessageType type;
    String text;

    @Override
    public String toString() {
        if (type != null && text != null) {
            return type + ":" + text;
        } else return Objects.requireNonNullElse(text, "");
    }
}
