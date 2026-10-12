package ezbuild.controller;

import ezbuild.config.AppProperties;
import ezbuild.dto.SignUpForm;
import ezbuild.service.BusinessException;
import ezbuild.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final CurrentUser currentUser;
    private final AppProperties props;

    /** A demo account shown as a one-click button on the sign-in page (seeded accounts only). */
    public record DemoLogin(String label, String role, String username, String password) { }

    @GetMapping("/sign-in")
    public String signIn(@RequestParam(required = false) String error, @RequestParam(required = false) String logout,
                         Authentication authentication, Model model) {
        if (currentUser.from(authentication) != null) return "redirect:/";
        model.addAttribute("loggedOut", logout != null);
        model.addAttribute("demoLogins", props.demoLogins() && props.seed() ? List.of(
                new DemoLogin("Admin", "admin", "admin", props.seedAdminPassword()),
                new DemoLogin("Staff", "staff", "staff", "Staff@123"),
                new DemoLogin("Customer", "user", "rahim", "User@1234")) : List.of());
        if (error != null) {
            model.addAttribute("error", switch (error) {
                case "locked" -> "Too many wrong attempts. Your account is locked for 15 minutes.";
                case "suspended" -> "This account has been suspended. Contact support.";
                default -> "Wrong username or password.";
            });
        }
        return "sign-in";
    }

    @GetMapping("/sign-up")
    public String signUpForm(Authentication authentication, Model model) {
        if (currentUser.from(authentication) != null) return "redirect:/";
        model.addAttribute("form", new SignUpForm());
        return "sign-up";
    }

    @PostMapping("/sign-up")
    public String signUp(@Valid @ModelAttribute("form") SignUpForm form, BindingResult result, RedirectAttributes ra) {
        if (!result.hasFieldErrors("username") && userService.usernameTaken(form.getUsername())) {
            result.rejectValue("username", "taken", "That username is taken.");
        }
        if (!result.hasFieldErrors("email") && userService.emailTaken(form.getEmail())) {
            result.rejectValue("email", "taken", "An account with that email already exists.");
        }
        if (result.hasErrors()) return "sign-up";
        try {
            userService.register(form);
        } catch (BusinessException e) {
            result.reject("register", e.getMessage());
            return "sign-up";
        }
        ra.addFlashAttribute("success", "Account created. Sign in to start building.");
        return "redirect:/sign-in";
    }
}
