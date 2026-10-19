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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Protects every /api/* URL.
 * The caller sends:  Authorization: Bearer <their personal token>
 * The token is hashed (SHA-256) and looked up in dbo.AllowedSenders.TokenHash.
 * If no row matches, the request is rejected. Only people listed in the table get in.
 */
@WebFilter("/api/*")
public class SecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            reject(resp, 401, "Missing or invalid token.");
            return;
        }

        String sender = findSender(header.substring(7).trim());
        if (sender == null) {
            reject(resp, 403, "Access denied. You are not on the authorized sender list.");
            return;
        }

        req.setAttribute("currentSender", sender);
        chain.doFilter(request, response);
    }

    /** Returns the Username if the token belongs to someone in AllowedSenders, else null. */
    private String findSender(String token) {
        String sql = "SELECT Username FROM dbo.AllowedSenders WHERE TokenHash = ?";
        try (Connection con = Db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, sha256Hex(token));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Username");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // deny on any error
    }

    private static String sha256Hex(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void reject(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
