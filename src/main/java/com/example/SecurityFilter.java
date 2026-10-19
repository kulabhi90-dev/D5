package com.example;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects every /api/* URL. The caller must send:  Authorization: Bearer <API_TOKEN>
 * API_TOKEN is an App Service application setting (never hard-code it in GitHub).
 */
@WebFilter("/api/*")
public class SecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String expected = System.getenv("API_TOKEN");
        String header = req.getHeader("Authorization");

        if (expected == null || expected.isBlank()) {
            reject(resp, 500, "API_TOKEN is not configured on the server.");
            return;
        }
        if (header == null || !header.startsWith("Bearer ")) {
            reject(resp, 401, "Missing or invalid token.");
            return;
        }
        String token = header.substring(7).trim();
        boolean ok = MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                expected.trim().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            reject(resp, 403, "Access denied.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
