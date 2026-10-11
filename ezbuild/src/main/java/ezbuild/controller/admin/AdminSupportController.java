package ezbuild.controller.admin;

import ezbuild.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/support")
@PreAuthorize("hasAuthority('ORDER_MANAGE')")
@RequiredArgsConstructor
public class AdminSupportController {

    private final SupportService supportService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tickets", supportService.all());
        return "admin/support";
    }

    @PostMapping("/{id}/reply")
    public String reply(@PathVariable String id, @RequestParam String reply,
                        @RequestParam(defaultValue = "false") boolean close, RedirectAttributes ra) {
        if (reply == null || reply.isBlank()) {
            ra.addFlashAttribute("error", "Write a reply first.");
        } else {
            supportService.reply(id, reply.trim(), close);
            ra.addFlashAttribute("success", "Reply sent.");
        }
        return "redirect:/admin/support";
    }
}
