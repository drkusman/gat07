package ng.gat2027.grassroot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${gat.remember-me-key}")
    private String rememberMeKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MemberUserDetailsService uds) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/events", "/events/*/video", "/news", "/podcast", "/register", "/register-group", "/login", "/forgot-password", "/reset-password", "/verify-code", "/error", "/css/**", "/js/**", "/img/**", "/docs/**", "/favicon.ico",
                                 "/api/locations/**", "/api/referral/**", "/api/events/**", "/api/institutions", "/actuator/health").permitAll()
                .requestMatchers("/admin/locations/**", "/admin/settings/**", "/admin/announcements/**", "/admin/institutions/**", "/admin/organization/**", "/admin/roles/**", "/admin/support-groups/**", "/api/admin/import",
                                 "/admin/members/suspended", "/admin/members/*/role", "/admin/members/*/position", "/admin/members/*/status",
                                 "/admin/promotions/**", "/admin/events/*/approve", "/admin/videos/*/approve").hasRole("ADMIN")
                .requestMatchers("/admin/members/*/appointment-letter").hasAnyRole("ADMIN", "COORDINATOR", "LGA_COORDINATOR")
                .requestMatchers("/admin/members/*/password").hasAnyRole("ADMIN", "COORDINATOR", "LGA_COORDINATOR", "WARD_COORDINATOR")
                .requestMatchers("/admin/events/**").hasAnyRole("ADMIN", "COORDINATOR", "ZONAL_COORDINATOR", "MEDIA_COORDINATOR", "NATIONAL_PUBLICITY_SECRETARY")
                .requestMatchers("/admin/videos/**").hasAnyRole("ADMIN", "MEDIA_COORDINATOR", "NATIONAL_PUBLICITY_SECRETARY")
                .requestMatchers("/admin/**", "/api/admin/**").hasAnyRole("ADMIN", "COORDINATOR", "ZONAL_COORDINATOR", "GRAND_PATRON",
                                 "LGA_COORDINATOR", "WARD_COORDINATOR", "POLLING_UNIT_COORDINATOR", "MEDIA_COORDINATOR", "NATIONAL_PUBLICITY_SECRETARY")
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")
                .usernameParameter("phone")
                .passwordParameter("password")
                .successHandler(new RoleAwareSuccessHandler())
                .failureUrl("/login?error")
                .permitAll())
            // Stateless (hash-based) remember-me: no DB-backed token to rotate, so no series/token race
            // under concurrent requests - see MemberService.setStatus/resetPasswordWithToken for how
            // suspension and password resets still invalidate a remembered login without it.
            .rememberMe(r -> r
                .key(rememberMeKey)
                .tokenValiditySeconds(30 * 24 * 3600)
                .alwaysRemember(true)
                .userDetailsService(uds))
            .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/").deleteCookies("JSESSIONID", "remember-me"))
            .userDetailsService(uds);
        return http.build();
    }
}
