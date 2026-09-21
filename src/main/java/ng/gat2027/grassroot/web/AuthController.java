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
import ng.gat2027.grassroot.service.SupportGroupService;
import ng.gat2027.grassroot.web.forms.RegisterForm;
import ng.gat2027.grassroot.web.forms.SupportGroupForm;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class AuthController {
    private final MemberService memberService; private final MemberRepository members; private final SettingsService settings; private final CurrentUser currentUser;
    private final SupportGroupService supportGroups;
    private final HttpSessionSecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();

    public AuthController(MemberService memberService, MemberRepository members, SettingsService settings, CurrentUser currentUser, SupportGroupService supportGroups) {
        this.memberService = memberService; this.members = members; this.settings = settings; this.currentUser = currentUser; this.supportGroups = supportGroups;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String next, @RequestParam(required = false) String error, @RequestParam(required = false) String logout,
                         @RequestParam(required = false) String reset, Model model) {
        var u = currentUser.get();
        if (u.isPresent()) return "redirect:" + (next != null && next.startsWith("/") ? next : u.get().isStaff() ? "/admin" : "/dashboard");
        model.addAttribute("next", next);
        model.addAttribute("error", error != null);
        model.addAttribute("reset", reset != null);
        return "login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String phone, HttpServletRequest request, Model model) {
        String resetBaseUrl = ServletUriComponentsBuilder.fromContextPath(request).path("/reset-password").toUriString();
        var result = memberService.requestPasswordReset(phone, resetBaseUrl);
        model.addAttribute("sent", true);
        model.addAttribute("maskedEmail", result.maskedEmail());
        model.addAttribute("devModeLink", result.devModeLink());
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", memberService.isResetTokenValid(token));
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token, @RequestParam String password, @RequestParam String password2, Model model) {
        model.addAttribute("token", token);
        if (!password.equals(password2)) {
            model.addAttribute("valid", true);
            model.addAttribute("error", "Passwords do not match");
            return "reset-password";
        }
        try {
            memberService.resetPasswordWithToken(token, password);
            return "redirect:/login?reset=1";
        } catch (MemberService.MemberException e) {
            model.addAttribute("valid", memberService.isResetTokenValid(token));
            model.addAttribute("error", e.getMessage());
            return "reset-password";
        }
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

    @GetMapping("/register-group")
    public String registerGroupForm(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new SupportGroupForm());
        return "register-group";
    }

    @PostMapping("/register-group")
    public String registerGroup(@Valid @ModelAttribute("form") SupportGroupForm form, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("error", binding.getAllErrors().get(0).getDefaultMessage());
            return "register-group";
        }
        try {
            supportGroups.register(form);
            model.addAttribute("sent", true);
            model.addAttribute("form", new SupportGroupForm());
        } catch (SupportGroupService.SupportGroupException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "register-group";
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
