package uk.ac.ebi.protvar.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.model.DownloadRequest;

import javax.mail.internet.MimeMessage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class Email {
    private static final String FROM = "protvar@ebi.ac.uk";
    private static final String DEVELOPER = "prabhat@ebi.ac.uk";
    private static JavaMailSender emailSender;

    private static final Logger LOGGER = LoggerFactory.getLogger(Email.class);

    @Autowired
    public void setEmailSender(JavaMailSender emailSender) {
        Email.emailSender = emailSender;
    }

    private static void sendSimpleMessage(String to, String cc, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            if (Commons.notNullNotEmpty(cc))
                message.setBcc(cc);
            message.setFrom(FROM);
            message.setSubject(subject);
            message.setText(body);

            emailSender.send(message);
        } catch (Exception e) {
            LOGGER.error("sendSimpleMessage exception", e);
        }
    }

    private static void sendMimeMessage(String to, String cc, String subject, String body, Path file) {
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            if (Commons.notNullNotEmpty(cc))
                helper.setBcc(cc);
            helper.setFrom(FROM);
            helper.setFrom(FROM);
            helper.setSubject(subject);
            helper.setText(body);

            if (file != null) {
                helper.addAttachment(file.getFileName().toString(), file.toFile());
            }
            emailSender.send(message);
        } catch (Exception e) {
            LOGGER.error("sendMimeMessage exception", e);
        }
    }

    public static void notifyUser(DownloadRequest request) {
        if (Commons.notNullNotEmpty(request.getEmail())) {
            sendSimpleMessage(request.getEmail(), null,
                    String.format("ProtVar results: %s", request.getJobName()),
                    notifyEmailBody(request.getUrl()));
        }
    }

    public static void notifyUserErr(DownloadRequest request, List<String> inputs) {
        if (Commons.notNullNotEmpty(request.getEmail())) {
            String subject = String.format("ProtVar job: %s failed", request.getJobName());
            String body = notifyErrEmailBody();
            sendSimpleMessage(request.getEmail(), DEVELOPER, subject,
                    String.format("%s\n\n%s", body, first10Inputs(inputs)));
            //sendMimeMessage(request.getEmail(), DEVELOPER, subject, body, request.getFileInput());
        }
    }

    public static void notifyDevErr(DownloadRequest request, List<String> inputs, Throwable t) {
        LOGGER.error("notifyDevErr", t);
        String subject = String.format("Download job failed: %s", request.getJobName());
        String body = "unknown error";
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            body = String.format("%s\n\n%s", t.getMessage(), sw);
        }
        sendSimpleMessage(DEVELOPER, null, subject,
                String.format("%s\n\n%s\n\n%s", body, request, first10Inputs(inputs)));
        //sendMimeMessage(DEVELOPER, null, subject, body, request.getFileInput());
    }

    private static String first10Inputs(List<String> inputs) {
        if (inputs != null) {
            String first10 = inputs.stream().limit(10).collect(Collectors.joining("\n"));
            if (inputs.size() > 10) {
                first10 += "\n...";
            }
            return first10;
        }
        return "";
    }

    private static String notifyEmailBody(String url) {
        return String.format("""
                Dear user,
                                            
                Your results from ProtVar have been processed, please use the following link to download the file.
                %s
                            
                For descriptions of each column and what value it contains please see below link
                https://www.ebi.ac.uk/ProtVar/help#download-file
                            
                For any additional queries please feel free to contact us.
                            
                Thank you for using ProtVar.
                ProtVar Team
                protvar@ebi.ac.uk
                """, url);
    }

    private static String notifyErrEmailBody() {
        return """
                Dear user,
                                
                Our apologies but we have been unable to retrieve results for your query.
                Please check the ProtVar website again for information relating to any potential issues: https://www.ebi.ac.uk/ProtVar/
                Please also check the information page for frequently asked questions and potential upload problems here: https://www.ebi.ac.uk/ProtVar/about
                Please re-submit your query. If the problem persists then please contact us with your inputs.
                                
                For any additional queries please feel free to contact us.
                                
                Kind regards,
                ProtVar Team
                protvar@ebi.ac.uk
                """;
    }
}
