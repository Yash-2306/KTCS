package com.school.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebConfig — Registers our custom interceptors (middleware) with Spring.
 *
 * Think of interceptors as "checkpoints" that run before/after every request.
 * Here we attach the RateLimitInterceptor only to the /api/leads endpoint
 * (the Contact form) so it doesn't affect other parts of the app.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only apply rate limiting to the Contact form/leads endpoint
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/leads", "/api/leads/**");
    }
}
