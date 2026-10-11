package ezbuild.repository;

import ezbuild.model.SupportTicket;
import ezbuild.model.TicketStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SupportTicketRepository extends MongoRepository<SupportTicket, String> {

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(String userId);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    long countByStatus(TicketStatus status);
}
