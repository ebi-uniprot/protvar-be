package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Message {
    public enum MessageType {
        INFO,
        WARN,
        ERROR
    }
    MessageType type;
    String text;
}
