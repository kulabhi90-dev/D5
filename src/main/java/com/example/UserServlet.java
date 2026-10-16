package com.example;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@WebServlet("/submit")
public class UserServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(UserServlet.class.getName());
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    
    // FIX 1: Added gender column and placeholder (?) to the SQL insert statement
    private static final String INSERT_SQL =
            "INSERT INTO dbo.users (full_name, email_address, city, gender) VALUES (?, ?, ?, ?)";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendRedirect(req.getContextPath() + "/");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String fullName = trim(req.getParameter("fullName"));
        String email = trim(req.getParameter("emailAddress"));
        String city = trim(req.getParameter("city"));
        
        // FIX 2: Changed duplicate String city declaration to String gender
        String gender = trim(req.getParameter("Gender"));

        // FIX 3: Passed gender into the validate method
        String error = validate(fullName, email, city, gender);
        if (error != null) {
            flash(req, "error", error);
        } else {
            try (Connection con = Db.getConnection();
                 PreparedStatement ps = con.prepareStatement(INSERT_SQL)) {
                
                // FIX 4: Updated parameter indexes sequentially (1 to 4)
                ps.setString(1, fullName);
                ps.setString(2, email);
                ps.setString(3, city);
                ps.setString(4, gender);
                
                ps.executeUpdate();
                flash(req, "success", "Thank you, " + fullName + ". Your details were saved.");
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Could not save user details", e);
                flash(req, "error", "Sorry, we could not save your details right now. Please try again.");
            }
        }
        resp.sendRedirect(req.getContextPath() + "/");
    }

    // FIX 5: Updated method signature and validation rules to handle the gender variable
    private static String validate(String name, String email, String city, String gender) {
        if (name.isEmpty() || email.isEmpty() || city.isEmpty() || gender.isEmpty()) return "All fields are required.";
        if (name.length() > 10 || city.length() > 10 || email.length() > 150 || gender.length() > 150) return "One of the values is too long.";
        if (!EMAIL.matcher(email).matches()) return "Please enter a valid email address.";
        return null;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static void flash(HttpServletRequest req, String type, String message) {
        HttpSession session = req.getSession();
        session.setAttribute("flashType", type);
        session.setAttribute("flashMessage", message);
    }
}
