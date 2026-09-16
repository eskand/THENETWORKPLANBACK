package com.thenetworkplan.networkplan.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Authentication is deliberately OFF for this iteration (agreed scope): the
 * whole API is open so the Dispatch screen can be driven end to end.
 *
 * <p>THIS IS NOT A PRODUCTION CONFIGURATION. The audit's third decisive finding
 * was that the prototype had no security boundary at all; the shape below is the
 * one seam where that gets fixed. Replace {@code permitAll} with an OAuth2
 * resource server and read the tenant from the token in
 * {@code TenantFilter} — nothing else in the code base has to change.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Component
    @ConfigurationProperties(prefix = "netplus.cors")
    @Getter
    @Setter
    public static class CorsProperties {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    /**
     * {@code @Qualifier} is required, not cosmetic: Spring MVC's
     * {@code mvcHandlerMappingIntrospector} also implements
     * {@link CorsConfigurationSource}, so the type alone matches two beans and the
     * context fails to start. Naming the bean pins the injection to the one below.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Total-Count"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
