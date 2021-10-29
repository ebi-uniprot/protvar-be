package uk.ac.ebi.protvar.utils;

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

@Component
public class Email {
  private static final String FROM = "protvar@ebi.ac.uk";
  private static final String DEVELOPER_GROUP = "protvar@ebi.ac.uk";
  private static JavaMailSender emailSender;
  private static String successBody = "";

  @Autowired
  public void setEmailSender(JavaMailSender emailSender) {
    Email.emailSender = emailSender;
  }

  public static void send(String email, String jobName, Path outputFile) {
    try {
      MimeMessage message = emailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);

      helper.setTo(email);
      helper.setFrom(FROM);
      helper.setSubject("ProtVar results: " + jobName);
      helper.setText(getSuccessBody());

      FileSystemResource file = new FileSystemResource(outputFile);
      helper.addAttachment(jobName + "-ProtVar.zip", file);
      emailSender.send(message);
    } catch (Exception e) {
      throw new UnexpectedUseCaseException("Not able to send email", e);
    }
  }

  public static void reportException(String sMessage) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(DEVELOPER_GROUP);
      message.setFrom(FROM);
      message.setSubject("Unexpected error ");
      message.setText(sMessage);
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
}
