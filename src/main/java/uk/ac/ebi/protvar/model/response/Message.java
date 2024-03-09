package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Message {
    public enum MessageType {
        INFO, // (?)
        WARN, // (!) for e.g. ref base or ref aa mismatch - > show a warning in icon tooltip
        ERROR // (X)
    }
    MessageType type;
    String text;
}
