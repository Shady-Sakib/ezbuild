package ezbuild.controller.admin;

import ezbuild.model.Role;
import ezbuild.service.BusinessException;
import ezbuild.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumSet;
import java.util.List;

/** Member management: ADMIN role only. */
@Controller
@RequestMapping("/admin/members")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMemberController {

    private final UserService userService;

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("members", userService.search(q, PageRequest.of(Math.max(0, page), 30,
                Sort.by(Sort.Direction.DESC, "joinedAt"))));
        model.addAttribute("q", q);
        model.addAttribute("roles", Role.values());
        return "admin/members";
    }

    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable String id, @RequestParam boolean suspended, Authentication authentication,
                          RedirectAttributes ra) {
        try {
            userService.setSuspended(id, suspended, authentication.getName());
            ra.addFlashAttribute("success", suspended ? "Member suspended." : "Member restored.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/members";
    }

    @PostMapping("/{id}/roles")
    public String roles(@PathVariable String id, @RequestParam(required = false) List<Role> roles,
                        Authentication authentication, RedirectAttributes ra) {
        userService.setRoles(id, roles == null ? EnumSet.of(Role.ROLE_USER) : EnumSet.copyOf(roles), authentication.getName());
        ra.addFlashAttribute("success", "Roles updated.");
        return "redirect:/admin/members";
    }
}
