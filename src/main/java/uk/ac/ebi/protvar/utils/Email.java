package uk.ac.ebi.protvar.utils;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.exception.UnexpectedUseCaseException;

import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public class Email {
  private static final String FROM = "protvar@ebi.ac.uk";
  private static final String DEVELOPER_GROUP = "protvar@ebi.ac.uk";
  private static JavaMailSender emailSender;
  private static String successBody = "";
  private static String errorBody = "";

  @Autowired
  public void setEmailSender(JavaMailSender emailSender) {
    Email.emailSender = emailSender;
  }

  public static void send(String email, String jobName, Path outputFile) {
    send(email, null, "ProtVar results: " + jobName, getSuccessBody(), jobName + "-ProtVar.zip", outputFile);
  }

  public static void sendErr(String email, String jobName, List<String> inputs) {
    var body = getErrorBody() + "\n\n" + String.join("\n", inputs);
    send(email, DEVELOPER_GROUP, failedSub(jobName), body);
  }

  public static void sendErr(String email, String jobName, Path outputFile) {
    send(email, DEVELOPER_GROUP, failedSub(jobName), getErrorBody(), outputFile.getFileName().toString(), outputFile);
  }

  public static void reportException(String subject, String sMessage, Throwable t) {
    var sub = "ProtVar " + Optional.ofNullable(subject).orElse("unexpected error");
    var body = Optional.ofNullable(sMessage).orElse("") + System.lineSeparator() +
      Optional.ofNullable(t).map(ExceptionUtils::getStackTrace).orElse("");
    send(DEVELOPER_GROUP, null, sub, body);
  }

  private static String failedSub(String jobName) {
    return "ProtVar job: " + jobName + " failed";
  }

  private static void send(String to, String cc, String subject, String body, String attachmentName, Path outputFile) {
    try {
      MimeMessage message = emailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);

      helper.setTo(to);
      helper.setFrom(FROM);
      helper.setSubject(subject);
      helper.setText(body);
      if (Commons.notNullNotEmpty(cc))
        helper.setCc(cc);

      FileSystemResource file = new FileSystemResource(outputFile);
      helper.addAttachment(attachmentName, file);
      emailSender.send(message);
    } catch (Exception e) {
      throw new UnexpectedUseCaseException("Not able to send email", e);
    }
  }

  private static void send(String to, String cc, String subject, String body) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(to);
      message.setFrom(FROM);
      message.setSubject(subject);
      message.setText(body);
      if (Commons.notNullNotEmpty(cc))
        message.setCc(cc);
      emailSender.send(message);
    } catch (Exception e) {
      throw new UnexpectedUseCaseException("Not able to send email", e);
    }
  }

  private static String getSuccessBody() {
    if (successBody.isEmpty()) {
      var stream = Email.class.getClassLoader().getResourceAsStream("successEmailBody.txt");
      assert stream != null;
      try {
        successBody = new String(stream.readAllBytes());
        stream.close();
      } catch (IOException e) {
        successBody = "Please find your attached results";
      }
    }
    return successBody;
  }

  private static String getErrorBody() {
    if (errorBody.isEmpty()) {
      var stream = Email.class.getClassLoader().getResourceAsStream("errorEmailBody.txt");
      assert stream != null;
      try {
        errorBody = new String(stream.readAllBytes());
        stream.close();
      } catch (IOException e) {
        errorBody = "Your request failed. Contact us with your inputs";
      }
    }
    return errorBody;
  }
}
