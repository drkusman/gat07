package ng.gat2027.grassroot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Allows the public Next.js frontend (a different origin) to read the public /api/** endpoints. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${gat.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET");
    }
}
