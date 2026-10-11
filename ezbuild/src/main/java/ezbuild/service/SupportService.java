package ezbuild.service;

import ezbuild.model.SupportTicket;
import ezbuild.model.TicketStatus;
import ezbuild.model.User;
import ezbuild.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository repository;
    private final MailService mailService;

    public SupportTicket open(SupportTicket form, User user) {
        form.setId(null);
        form.setUserId(user == null ? null : user.getId());
        form.setStatus(TicketStatus.OPEN);
        form.setReply(null);
        form.setCreatedAt(Instant.now());
        return repository.save(form);
    }

    public List<SupportTicket> mine(User user) {
        return repository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public List<SupportTicket> all() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public void reply(String id, String reply, boolean close) {
        SupportTicket t = repository.findById(id).orElseThrow(() -> new NotFoundException("Ticket not found."));
        t.setReply(reply);
        t.setStatus(close ? TicketStatus.CLOSED : TicketStatus.ANSWERED);
        repository.save(t);
        mailService.send(t.getEmail(), "Re: " + t.getSubject(), reply + "\n\n- EZBuild support");
    }

    public long openCount() {
        return repository.countByStatus(TicketStatus.OPEN);
    }
}
