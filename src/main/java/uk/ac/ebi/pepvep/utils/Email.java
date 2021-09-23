package uk.ac.ebi.pepvep.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import uk.ac.ebi.pepvep.exception.UnexpectedUseCaseException;

import javax.mail.internet.MimeMessage;
import java.io.File;

@Component
public class Email {
  private static final String FROM = "pepvep@ebi.ac.uk";
  private static final String DEVELOPER_GROUP = "pepvep@ebi.ac.uk";
  private static JavaMailSender emailSender;

  @Autowired
  public void setEmailSender(JavaMailSender emailSender) {
    Email.emailSender = emailSender;
  }

  public static void send(String email, String jobName, File outputFile) {
    try {
      MimeMessage message = emailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);

      helper.setTo(email);
      helper.setFrom(FROM);
      helper.setSubject("PepVEP result for job - " + jobName);
      helper.setText("Attached is your result");

      FileSystemResource file = new FileSystemResource(outputFile);
      helper.addAttachment("results.csv", file);
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
}
