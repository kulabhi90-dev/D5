package com.example;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// Correct Jakarta EE 10 namespaces to clear the compilation error
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

@WebServlet("/UsersApiServlet")
public class UsersApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // Data Transfer Object matching your front-end drop-down requirements
    private static class UserRecord {
        String fullName;
        String email;

        UserRecord(String fullName, String email) {
            this.fullName = fullName;
            this.email = email;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // 1. Set REST API headers
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        // 2. Allow cross-origin requests from your front-end deployment domain
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        List<UserRecord> users = new ArrayList<>();

        // 3. Database query using your existing connection manager
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
            getServletContext().log("API Database Engine Failure", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database connection failed.\"}");
            return;
        }

        // 4. Output the compiled collection object payload as pure JSON text
        Gson gson = new Gson();
        String jsonOutput = gson.toJson(users);

        PrintWriter out = response.getWriter();
        out.print(jsonOutput);
        out.flush();
    }
}
