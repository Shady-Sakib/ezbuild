package ezbuild.order;

import ezbuild.config.AppProperties;
import ezbuild.model.Order;
import ezbuild.repository.OrderRepository;
import ezbuild.service.MailService;
import ezbuild.util.Fmt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Side effects (mail) that must run after the DB transaction commits. */
@Component
@RequiredArgsConstructor
public class OrderMailListener {

    private final OrderRepository orderRepository;
    private final MailService mailService;
    private final AppProperties props;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPlaced(OrderEvents.Placed event) {
        orderRepository.findById(event.orderId()).ifPresent(o -> mailService.send(o.getEmail(),
                "EZBuild order " + o.getCode() + " received",
                "Hi " + o.getUsername() + ",\n\nThanks for your order " + o.getCode() + " (" + Fmt.taka(o.getTotal()) + ").\n"
                        + "Payment: " + o.getPaymentMethod().getLabel() + "\nStatus: " + o.getStatus().getLabel()
                        + "\n\nTrack it here: " + props.baseUrl() + "/orders/" + o.getCode() + "\n\n- EZBuild"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaid(OrderEvents.Paid event) {
        orderRepository.findById(event.orderId()).ifPresent(o -> mailService.send(o.getEmail(),
                "Payment received for " + o.getCode(),
                "We received " + Fmt.taka(o.getTotal()) + " for order " + o.getCode() + ". We'll start packing your parts.\n\n"
                        + props.baseUrl() + "/orders/" + o.getCode() + "\n\n- EZBuild"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatus(OrderEvents.StatusChanged event) {
        Order o = orderRepository.findById(event.orderId()).orElse(null);
        if (o == null) return;
        mailService.send(o.getEmail(), "Order " + o.getCode() + ": " + o.getStatus().getLabel(),
                "Your order " + o.getCode() + " is now: " + o.getStatus().getLabel() + ".\n\n" + props.baseUrl()
                        + "/orders/" + o.getCode() + "\n\n- EZBuild");
    }
}
