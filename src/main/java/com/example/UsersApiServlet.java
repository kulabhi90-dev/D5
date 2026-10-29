package com.example;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// 1. This annotation registers the literal URL endpoint route path
@WebServlet("/UsersApiServlet")
public class UsersApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // A simple container object to format our JSON output structures cleanly
    private static class UserRecord {
        String fullName;
        String email;

        UserRecord(String fullName, String email) {
            this.fullName = fullName;
            this.email = email;
        }
    }

    // 2. This method handles all incoming HTTP GET requests sent to the endpoint
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Configure standard REST API headers
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // Inject relaxed CORS headers so D6 can securely read this stream from a browser
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        List<UserRecord> users = new ArrayList<>();

        // 3. Query the database using your application's connection management class
        String query = "SELECT full_name, email FROM users";

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(new UserRecord(
                    rs.getString("full_name"),
                    rs.getString("email")
                ));
            }

        } catch (SQLException e) {
            // Log errors to catalina.out and throw a 500 error block back to the consumer
            getServletContext().log("API Database Engine Crash", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database connection failed.\"}");
            return;
        }

        // 4. Manually format the list into a clean JSON text stream array string
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < users.size(); i++) {
            UserRecord u = users.get(i);
            json.append(String.format("{\"fullName\":\"%s\",\"email\":\"%s\"}", u.fullName, u.email));
            if (i < users.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        // Send the completed payload out to the caller
        PrintWriter out = response.getWriter();
        out.print(json.toString());
        out.flush();
    }
}
