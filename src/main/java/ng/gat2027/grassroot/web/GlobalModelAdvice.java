package ng.gat2027.grassroot.web;

import jakarta.servlet.http.HttpServletRequest;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.security.CurrentUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Model attributes available to every template: the logged-in member, the current path and today's date. */
@ControllerAdvice
public class GlobalModelAdvice {
    private final CurrentUser currentUser;

    public GlobalModelAdvice(CurrentUser currentUser) { this.currentUser = currentUser; }

    @ModelAttribute("currentUser")
    public Member currentUser() { return currentUser.get().orElse(null); }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest req) { return req.getRequestURI(); }

    @ModelAttribute("todayLabel")
    public String todayLabel() { return LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")); }
}
