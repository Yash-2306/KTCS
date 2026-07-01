package com.school.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter; // our custom token-checking filter

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Enable CORS and configure it to accept requests from Netlify
            .cors(Customizer.withDefaults())
            
            // 2. Disable CSRF — not needed because we use tokens, not cookies
            .csrf(csrf -> csrf.disable())

            // 3. Disable the browser's built-in Basic Auth popup
            .httpBasic(basic -> basic.disable())

            // 4. No server sessions — every request is independent and carries its token
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 5. Let Spring Security permit all routes — our JwtFilter handles
            //    the actual access control (check before the controller runs)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )

            // 6. Add our JWT filter to run BEFORE Spring's own auth processing
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS Configuration — Tells the browser that our frontend (Netlify)
     * is allowed to talk to this backend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allows requests from any origin (including your Netlify site)
        configuration.setAllowedOrigins(List.of("*"));
        
        // Allows standard HTTP methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Allows standard headers (like Authorization token)
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
