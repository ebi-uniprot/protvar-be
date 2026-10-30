package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.protvar.input.MessageCode;
import uk.ac.ebi.protvar.model.response.Message;

import java.util.Arrays;
import java.util.List;

/**
 * The message catalogue (MessageCode): every note ProtVar can attach to an input or a result, by code.
 * Used for filter labels and the help page.
 */
@Tag(name = "Messages")
@RestController
@CrossOrigin
@RequestMapping("/messages")
public class MessageController {

    /** One catalogue entry; text may hold format placeholders (%s), filled where the message is raised. */
    public record Entry(String code, Message.Type type, String label, String text) {}

    private static final List<Entry> CATALOGUE = Arrays.stream(MessageCode.values())
            .map(c -> new Entry(c.getCode(), c.getType(), c.getLabel(), c.getText())).toList();

    @Operation(summary = "Message catalogue", description = "Every message code with its severity, label and text.")
    @GetMapping
    public List<Entry> messages() {
        return CATALOGUE;
    }
}
