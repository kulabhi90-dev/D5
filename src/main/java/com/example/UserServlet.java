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
    private static final String INSERT_SQL =
            "INSERT INTO dbo.users (full_name, email_address, city) VALUES (?, ?, ?)";

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
        String city = trim(req.getParameter("Gender"));

        String error = validate(fullName, email, city);
        if (error != null) {
            flash(req, "error", error);
        } else {
            try (Connection con = Db.getConnection();
                 PreparedStatement ps = con.prepareStatement(INSERT_SQL)) {
                ps.setString(1, fullName);
                ps.setString(2, email);
                ps.setString(3, city);
                ps.setString(3, Gender);
                ps.executeUpdate();
                flash(req, "success", "Thank you, " + fullName + ". Your details were saved.");
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Could not save user details", e);
                flash(req, "error", "Sorry, we could not save your details right now. Please try again.");
            }
        }
        // Post/Redirect/Get: refreshing the page will not insert a duplicate row
        resp.sendRedirect(req.getContextPath() + "/");
    }

    private static String validate(String name, String email, String city) {
        if (name.isEmpty() || email.isEmpty() || city.isEmpty() || Gender.isEmpty()) return "All fields are required.";
        if (name.length() > 10 || city.length() > 10 || email.length() > 150 || Gender.length() >150) return "One of the values is too long.";
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
