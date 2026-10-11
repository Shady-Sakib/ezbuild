package ezbuild.controller;

import ezbuild.model.SupportTicket;
import ezbuild.model.User;
import ezbuild.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;
    private final CurrentUser currentUser;

    @GetMapping("/support")
    public String form(Authentication authentication, Model model) {
        if (!model.containsAttribute("ticket")) {
            SupportTicket t = new SupportTicket();
            User me = currentUser.from(authentication);
            if (me != null) {
                t.setName(me.displayLabel());
                t.setEmail(me.getEmail());
            }
            model.addAttribute("ticket", t);
        }
        return "support";
    }

    @PostMapping("/support")
    public String submit(@Valid @ModelAttribute("ticket") SupportTicket ticket, BindingResult result,
                         Authentication authentication, RedirectAttributes ra) {
        if (result.hasErrors()) return "support";
        supportService.open(ticket, currentUser.from(authentication));
        ra.addFlashAttribute("success", "Thanks! Our team will reply by email, usually within a day.");
        return "redirect:/support";
    }
}
