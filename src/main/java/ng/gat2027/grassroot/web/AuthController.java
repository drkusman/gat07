package ng.gat2027.grassroot.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.repo.MemberRepository;
import ng.gat2027.grassroot.security.CurrentUser;
import ng.gat2027.grassroot.security.MemberPrincipal;
import ng.gat2027.grassroot.service.LocationService;
import ng.gat2027.grassroot.service.MemberService;
import ng.gat2027.grassroot.service.SettingsService;
import ng.gat2027.grassroot.web.forms.RegisterForm;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    private final MemberService memberService; private final MemberRepository members; private final SettingsService settings; private final CurrentUser currentUser;
    private final HttpSessionSecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();

    public AuthController(MemberService memberService, MemberRepository members, SettingsService settings, CurrentUser currentUser) {
        this.memberService = memberService; this.members = members; this.settings = settings; this.currentUser = currentUser;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String next, @RequestParam(required = false) String error, @RequestParam(required = false) String logout, Model model) {
        var u = currentUser.get();
        if (u.isPresent()) return "redirect:" + (next != null && next.startsWith("/") ? next : u.get().isStaff() ? "/admin" : "/dashboard");
        model.addAttribute("next", next);
        model.addAttribute("error", error != null);
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(@RequestParam(required = false) String ref, Model model) {
        RegisterForm f = new RegisterForm();
        if (ref != null && !ref.isBlank()) {
            f.setReferralCode(ref.trim().toUpperCase());
            members.findByReferralCodeIgnoreCase(ref.trim()).filter(Member::isActive)
                .ifPresentOrElse(r -> model.addAttribute("refBanner", "You were invited by " + r.getDisplayName() + " (" + r.getMemberCode() + "). Their referral code has been applied."),
                                 () -> model.addAttribute("refError", "The referral code in your link was not recognised. You can still register."));
        }
        model.addAttribute("form", f);
        model.addAttribute("ageRules", settings.ageRules());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult binding, Model model, HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("ageRules", settings.ageRules());
        if (binding.hasErrors()) {
            model.addAttribute("error", binding.getAllErrors().get(0).getDefaultMessage());
            return "register";
        }
        try {
            Member m = memberService.register(form);
            loginAs(m, request, response);
            model.addAttribute("member", m);
            return "register-success";
        } catch (MemberService.MemberException | LocationService.LocationException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    /** Log the freshly registered member in without a second form. */
    private void loginAs(Member m, HttpServletRequest request, HttpServletResponse response) {
        MemberPrincipal p = new MemberPrincipal(m);
        var auth = UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, request, response);
    }
}
