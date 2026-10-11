package ezbuild.controller;

import ezbuild.assistant.AssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Full-page chat. The same JS module powers the floating widget on every page. */
@Controller
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;
    private final CurrentUser currentUser;

    @GetMapping("/assistant")
    public String assistant(@org.springframework.web.bind.annotation.RequestParam(required = false) String q,
                            Authentication authentication, Model model) {
        model.addAttribute("prefill", q == null ? "" : q.length() > 500 ? q.substring(0, 500) : q);
        model.addAttribute("sessions", assistantService.sessions(currentUser.require(authentication)));
        model.addAttribute("aiMode", assistantService.mode());
        return "assistant";
    }
}
