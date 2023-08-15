package uk.co.hexillium.rhul.compsoc.mail;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

public class Mail {

    Address fromAddress;
    Session session;


    static Logger logger = LogManager.getLogger(Mail.class);

    public Mail(MailConfiguration configuration){
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", true);
        properties.put("mail.smtp.starttls.enable", "true");

        properties.put("mail.smtp.host", configuration.getHostUrl());
        properties.put("mail.smtp.port", String.valueOf(configuration.getPort()));
        properties.put("mail.smtp.ssl.trust", configuration.getHostUrl());


        session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(configuration.getUsername(), configuration.getPassword());
            }
        });
        try {
            fromAddress = new InternetAddress(configuration.getFromAddress());

            logger.info("Started mail agent with host as {}, port as {}, username as {} and password as {}",
                    configuration.getHostUrl(), configuration.getPort(), configuration.getUsername(),
                    configuration.getPassword().substring(0,3) + "*".repeat(configuration.getPassword().length() - 2));
        } catch (AddressException ex){
            logger.error("Failed to resolve from address.  Mail will not be sent", ex);
        }
    }

    public void sendMessage(Address toAddress, String title, String content) throws MessagingException {
        if (fromAddress == null){
            logger.warn("Mail not active due to invalid from address.  See startup stacktrace for more information.  Mail not sent.");
            return;
        }
        try {
            Message message = new MimeMessage(session);
            message.setFrom(fromAddress);
            message.setRecipients(
                    Message.RecipientType.TO, new Address[]{toAddress});
            message.setSubject(title);

            MimeBodyPart mimeBodyPart = new MimeBodyPart();
            mimeBodyPart.setContent(content, "text/html; charset=utf-8");

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(mimeBodyPart);

            message.setContent(multipart);
            logger.info("About to send a message to " + toAddress.toString() + ".");
            Transport.send(message);
        } catch (MessagingException ex){
            logger.error("Failed to send message", ex);
            throw ex;
        }
    }

    public static final Properties defaultMailProperties = new Properties();
    static {
        defaultMailProperties.put("host.url", "mail.example.com");
        defaultMailProperties.put("host.port", "587");
        defaultMailProperties.put("host.mailbox.username", "compsocbot@example.com");
        defaultMailProperties.put("host.mailbox.password", "this would be a terrible password");
        defaultMailProperties.put("mail.from.email", "compsocbot@example.com");
    }

    public static class MailConfiguration{
        private String url;
        private short port;

        private String username;
        private String password;

        private String fromAddress;

        public MailConfiguration() {}

        public MailConfiguration(String url, short port, String username, String password, String fromAddress) {
            this.url = url;
            this.port = port;
            this.username = username;
            this.password = password;
            this.fromAddress = fromAddress;
        }

        public String getHostUrl() {
            return url;
        }

        public MailConfiguration setHostUrl(String url) {
            this.url = url;
            return this;
        }

        public short getPort() {
            return port;
        }

        public MailConfiguration setPort(short port) {
            this.port = port;
            return this;
        }

        public String getUsername() {
            return username;
        }

        public MailConfiguration setUsername(String username) {
            this.username = username;
            return this;
        }

        public String getPassword() {
            return password;
        }

        public MailConfiguration setPassword(String password) {
            this.password = password;
            return this;
        }

        public String getFromAddress() {
            return fromAddress;
        }

        public MailConfiguration setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
            return this;
        }
    }
}
