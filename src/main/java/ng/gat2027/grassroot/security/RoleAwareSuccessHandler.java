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
        java.util.Set<String> staffRoles = java.util.Set.of("ROLE_ADMIN", "ROLE_COORDINATOR", "ROLE_ZONAL_COORDINATOR", "ROLE_GRAND_PATRON",
            "ROLE_LGA_COORDINATOR", "ROLE_WARD_COORDINATOR", "ROLE_POLLING_UNIT_COORDINATOR", "ROLE_MEDIA_COORDINATOR", "ROLE_NATIONAL_PUBLICITY_SECRETARY");
        boolean staff = authentication.getAuthorities().stream().anyMatch(a -> staffRoles.contains(a.getAuthority()));
        setDefaultTargetUrl(staff ? "/admin" : "/dashboard");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
