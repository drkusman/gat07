package ng.gat2027.grassroot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;

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
    public PersistentTokenRepository tokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource); // table persistent_logins is created by Flyway
        return repo;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MemberUserDetailsService uds, PersistentTokenRepository tokens) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/events", "/events/*/video", "/register", "/login", "/error", "/css/**", "/js/**", "/img/**", "/docs/**", "/favicon.ico",
                                 "/api/locations/**", "/api/referral/**", "/api/events/**", "/actuator/health").permitAll()
                .requestMatchers("/admin/locations/**", "/admin/settings/**", "/api/admin/import", "/admin/members/*/role",
                                 "/admin/members/*/status", "/admin/members/*/password").hasRole("ADMIN")
                .requestMatchers("/admin/promotions/**").hasAnyRole("ADMIN", "ZONAL_COORDINATOR")
                .requestMatchers("/admin/events/**").hasAnyRole("ADMIN", "COORDINATOR", "ZONAL_COORDINATOR")
                .requestMatchers("/admin/**", "/api/admin/**").hasAnyRole("ADMIN", "COORDINATOR", "ZONAL_COORDINATOR", "GRAND_PATRON",
                                 "LGA_COORDINATOR", "WARD_COORDINATOR", "POLLING_UNIT_COORDINATOR")
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")
                .usernameParameter("phone")
                .passwordParameter("password")
                .successHandler(new RoleAwareSuccessHandler())
                .failureUrl("/login?error")
                .permitAll())
            .rememberMe(r -> r
                .key(rememberMeKey)
                .tokenRepository(tokens)
                .tokenValiditySeconds(30 * 24 * 3600)
                .alwaysRemember(true)
                .userDetailsService(uds))
            .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/").deleteCookies("JSESSIONID", "remember-me"))
            .userDetailsService(uds);
        return http.build();
    }
}
