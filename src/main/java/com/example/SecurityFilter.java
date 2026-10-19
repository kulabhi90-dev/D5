package com.example;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebFilter("/api/*")
public class SecurityFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Extract Token from Header
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Missing or invalid token.\"}");
            return;
        }
        String token = authHeader.substring(7);

        // 2. Identify the sender from the token string
        String senderUsername = getUsernameFromToken(token); 

        // 3. DYNAMIC CHECK: Query Azure SQL to see if this user is in the allowed list
        if (senderUsername == null || !isUserAllowed(senderUsername)) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Access Denied. You are not on the authorized sender list.\"}");
            return; 
        }

        // Success: Forward the request to your servlet
        httpRequest.setAttribute("currentSender", senderUsername);
        chain.doFilter(request, response);
    }

    // Connects to Azure SQL to check if the sender exists in the AllowedSenders table
    private boolean isUserAllowed(String username) {
        String query = "SELECT COUNT(*) AS user_count FROM AllowedSenders WHERE LOWER(Username) = LOWER(?)";
        
        try (Connection conn = Db.getConnection(); // Uses your existing Db.java
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_count") > 0; // Returns true if user is found in the table
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false; // Safely deny access if a database error happens
    }

    // Temporary method mapping tokens to test users
    private String getUsernameFromToken(String token) {
        if ("token-for-abhi-123".equals(token)) {
            return "abhi";
        } else if ("token-for-john-456".equals(token)) {
            return "john";
        }
        return null; 
    }

    @Override
    public void destroy() {}
}
