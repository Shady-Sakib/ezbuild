package ezbuild.controller;

import ezbuild.dto.PasswordForm;
import ezbuild.model.Address;
import ezbuild.model.Profile;
import ezbuild.model.User;
import ezbuild.service.BusinessException;
import ezbuild.service.SupportService;
import ezbuild.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final SupportService supportService;
    private final CurrentUser currentUser;

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        User me = currentUser.require(authentication);
        if (!model.containsAttribute("profile")) model.addAttribute("profile", me.getProfile());
        if (!model.containsAttribute("passwordForm")) model.addAttribute("passwordForm", new PasswordForm());
        if (!model.containsAttribute("address")) model.addAttribute("address", new Address());
        model.addAttribute("tickets", supportService.mine(me));
        return "account";
    }

    @PostMapping("/account/profile")
    public String profile(@Valid @ModelAttribute("profile") Profile profile, BindingResult result,
                          Authentication authentication, RedirectAttributes ra) {
        User me = currentUser.require(authentication);
        if (result.hasErrors()) {
            ra.addFlashAttribute("org.springframework.validation.BindingResult.profile", result);
            ra.addFlashAttribute("profile", profile);
            return "redirect:/account";
        }
        userService.updateProfile(me, profile);
        ra.addFlashAttribute("success", "Profile updated.");
        return "redirect:/account";
    }

    @PostMapping("/account/password")
    public String password(@Valid @ModelAttribute("passwordForm") PasswordForm form, BindingResult result,
                           Authentication authentication, RedirectAttributes ra) {
        User me = currentUser.require(authentication);
        if (result.hasErrors()) {
            ra.addFlashAttribute("org.springframework.validation.BindingResult.passwordForm", result);
            ra.addFlashAttribute("passwordForm", form);
            return "redirect:/account#security";
        }
        try {
            userService.changePassword(me, form.getCurrentPassword(), form.getNewPassword());
            ra.addFlashAttribute("success", "Password changed.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account#security";
    }

    @PostMapping("/account/addresses")
    public String addAddress(@Valid @ModelAttribute("address") Address address, BindingResult result,
                             Authentication authentication, RedirectAttributes ra) {
        User me = currentUser.require(authentication);
        if (result.hasErrors()) {
            ra.addFlashAttribute("org.springframework.validation.BindingResult.address", result);
            ra.addFlashAttribute("address", address);
            return "redirect:/account#addresses";
        }
        userService.saveAddress(me, address);
        ra.addFlashAttribute("success", "Address saved.");
        return "redirect:/account#addresses";
    }

    @PostMapping("/account/addresses/{index}/delete")
    public String deleteAddress(@PathVariable int index, Authentication authentication, RedirectAttributes ra) {
        userService.removeAddress(currentUser.require(authentication), index);
        ra.addFlashAttribute("success", "Address removed.");
        return "redirect:/account#addresses";
    }
}
