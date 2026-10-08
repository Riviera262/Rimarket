package RiMarket.com.example.demo.module.identity.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${application.base-url}")
    private String baseUrl;

    @Async("mailTaskExecutor")
    public void sendVerificationEmail(String toEmail, String token){
       try {
           MimeMessage message = mailSender.createMimeMessage();

           //True = multipart, "UTF-8" = encoding
           MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

           helper.setFrom(senderEmail);
           helper.setTo(toEmail);
           helper.setSubject("Welcome to RiMarket - Verify your email");
           String verificationUrl = baseUrl + "/api/auth/verify?token=" + token;

           String htmlContent = """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Welcome to RiMarket!</h2>
            
                <p>
                    Thank you for registering your account.
                </p>
            
                <p>
                    Please click the button below to verify your email address.
                </p>
            
                <p>
                    <a href="%s"
                       style="
                            background:#0d6efd;
                            color:white;
                            padding:12px 20px;
                            text-decoration:none;
                            border-radius:5px;
                            display:inline-block;">
                        Verify My Account
                    </a>
                </p>
            
                <p>
                    If the button doesn't work, copy and paste this URL into your browser:
                </p>
            
                <p>%s</p>
            
                <hr>
            
                <p>
                    If you didn't create this account, you can safely ignore this email.
                </p>
            
                <p>
                    Regards,<br>
                    RiMarket Team
                </p>
            
            </body>
            </html>
            """.formatted(verificationUrl, verificationUrl);
           helper.setText(htmlContent, true); //True tell Spring this is HTML, not plain text
           mailSender.send(message);
           log.info("Verification email successfully sent to {}", toEmail);
       } catch (MessagingException | MailException e){
           log.error("Failed to send email to {}", toEmail, e);
           // We don't throw an AppException here because this is on a background thread.
           // If it fails, we log it, but the user still gets their 201 Created response.
       }
    }


}
