package com.school.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter; // our custom token-checking filter

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Disable CSRF — not needed because we use tokens, not cookies
            .csrf(csrf -> csrf.disable())

            // 2. Disable the browser's built-in Basic Auth popup
            .httpBasic(basic -> basic.disable())

            // 3. No server sessions — every request is independent and carries its token
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 4. Let Spring Security permit all routes — our JwtFilter handles
            //    the actual access control (check before the controller runs)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )

            // 5. Add our JWT filter to run BEFORE Spring's own auth processing
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
