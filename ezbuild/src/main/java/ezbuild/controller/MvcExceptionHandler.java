package ezbuild.controller;

import ezbuild.service.BusinessException;
import ezbuild.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Page errors: friendly 404, and rule violations bounce back to the previous page as a flash message. */
@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class MvcExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException e, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("message", e.getMessage());
        return "error";
    }

    @ExceptionHandler(BusinessException.class)
    public String business(BusinessException e, HttpServletRequest request, RedirectAttributes ra) {
        ra.addFlashAttribute("error", e.getMessage());
        String referer = request.getHeader("Referer");
        String back = referer != null && referer.startsWith(request.getScheme() + "://" + request.getServerName())
                ? referer : "/";
        return "redirect:" + back;
    }
}
