package ezbuild.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("support_tickets")
@Getter
@Setter
public class SupportTicket {

    @Id
    private String id;

    @Indexed
    private String userId;

    @NotBlank(message = "Name is required.")
    @Size(max = 60)
    private String name;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @Size(max = 20)
    private String orderCode;

    @NotBlank(message = "Subject is required.")
    @Size(max = 100)
    private String subject;

    @NotBlank(message = "Message is required.")
    @Size(max = 2000)
    private String message;

    private String reply;

    @Indexed
    private TicketStatus status = TicketStatus.OPEN;

    @CreatedDate
    private Instant createdAt;
}
