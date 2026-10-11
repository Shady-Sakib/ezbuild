package ezbuild.service;

import ezbuild.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Sends mail when MAIL_HOST is set; otherwise just logs it. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Async
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) return;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailHost == null || mailHost.isBlank() || sender == null) {
            log.info("[mail not configured] to={} subject={}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mailFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
        } catch (Exception e) {
            log.warn("Mail to {} failed: {}", to, e.getMessage());
        }
    }
}
