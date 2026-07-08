package com.school.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtFilter — The security guard that runs on EVERY incoming request.
 *
 * ── How it works (for interviews) ───────────────────────────────────────────
 * 1. Request comes in (e.g. GET /api/attendance/students)
 * 2. This filter runs BEFORE the controller
 * 3. It reads the "Authorization: Bearer <token>" header
 * 4. It asks JwtUtil: "is this token valid?"
 * 5. If YES → store the user's info in the request and continue
 * 6. If NO  → stop the request, send back a 401 Unauthorized error
 *
 * Public routes (no token needed): /api/auth/login, /api/leads
 * Everything else requires a valid token.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    // These routes don't need a token — they're open to everyone
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/auth/login",  // login itself — can't require auth to log in!
            "/api/leads",       // contact form — public website visitors use this
            "/api/visitors"     // visitor analytics — fired from public pages with no token
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path   = request.getRequestURI();
        String method = request.getMethod();

        // Always let browser CORS preflight requests through
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // Always let public routes through
        boolean isPublic = PUBLIC_ROUTES.stream().anyMatch(path::startsWith);
        if (isPublic) {
            chain.doFilter(request, response);
            return;
        }

        // All other routes: check for Authorization header
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            reject(response, "You must be logged in to access this page.");
            return;
        }

        String token = authHeader.substring(7); // strip "Bearer " prefix

        if (!jwtUtil.isValid(token)) {
            reject(response, "Your session has expired. Please log in again.");
            return;
        }

        // Token is valid — extract user details and attach to request
        // Controllers can read these with:  request.getAttribute("username") etc.
        Claims claims = jwtUtil.parseToken(token);
        request.setAttribute("username", claims.getSubject());
        request.setAttribute("role",     claims.get("role", String.class));
        request.setAttribute("userId",   claims.get("userId", Long.class));

        // Let the request continue to the controller
        chain.doFilter(request, response);
    }

    /** Sends a 401 JSON error back to the browser */
    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
