package ng.gat2027.grassroot.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

/** After login: honour a saved request (or ?next=), otherwise send staff to /admin and members to /dashboard. */
public class RoleAwareSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
        String next = request.getParameter("next");
        if (next != null && next.startsWith("/") && !next.startsWith("//")) {
            getRedirectStrategy().sendRedirect(request, response, next);
            return;
        }
        boolean staff = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_COORDINATOR"));
        setDefaultTargetUrl(staff ? "/admin" : "/dashboard");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
