package ezbuild.controller;

import ezbuild.dto.PasswordForm;
import ezbuild.service.BusinessException;
import ezbuild.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService resetService;

    @GetMapping("/forgot-password")
    public String forgot() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String request(@RequestParam String email, RedirectAttributes ra) {
        resetService.request(email);
        ra.addFlashAttribute("success", "If that email has an account, a reset link is on its way. It works for 30 minutes.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", resetService.valid(token));
        model.addAttribute("form", new PasswordForm());
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam String token, @Valid @ModelAttribute("form") PasswordForm form, BindingResult result,
                        Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("token", token);
            model.addAttribute("valid", true);
            return "reset-password";
        }
        try {
            resetService.reset(token, form.getNewPassword());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/forgot-password";
        }
        ra.addFlashAttribute("success", "Password changed. Sign in with your new password.");
        return "redirect:/sign-in";
    }
}
