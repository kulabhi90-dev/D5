package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 *  GET  /api/users        -> list all users
 *  GET  /api/users/{id}   -> one user
 *  POST /api/users        -> create a user (JSON body)
 */
@WebServlet("/api/users/*")
public class UsersApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(UsersApiServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String COLS = "id, full_name, email_address, city, gender, created_at";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        boolean one = path != null && path.length() > 1;

        String sql = "SELECT " + COLS + " FROM dbo.users" + (one ? " WHERE id = ?" : " ORDER BY id");
        try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (one) {
                try {
                    ps.setInt(1, Integer.parseInt(path.substring(1)));
                } catch (NumberFormatException e) {
                    send(resp, 400, Map.of("error", "id must be a number"));
                    return;
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(row(rs));
            }
            if (one) {
                if (rows.isEmpty()) send(resp, 404, Map.of("error", "User not found"));
                else send(resp, 200, rows.get(0));
            } else {
                send(resp, 200, rows);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "GET failed", e);
            send(resp, 500, Map.of("error", "Database error"));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        JsonObject body;
        try {
            body = JsonParser.parseReader(req.getReader()).getAsJsonObject();
        } catch (Exception e) {
            send(resp, 400, Map.of("error", "Body must be valid JSON"));
            return;
        }

        String fullName = str(body, "fullName");
        String email = str(body, "emailAddress");
        String city = str(body, "city");
        String gender = str(body, "gender");

        String error = validate(fullName, email, city, gender);
        if (error != null) {
            send(resp, 400, Map.of("error", error));
            return;
        }

        String sql = "INSERT INTO dbo.users (full_name, email_address, city, gender) VALUES (?, ?, ?, ?)";
        try (Connection con = Db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, city);
            ps.setString(4, gender);
            ps.executeUpdate();

            Map<String, Object> out = new LinkedHashMap<>();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) out.put("id", keys.getInt(1));
            }
            out.put("fullName", fullName);
            out.put("emailAddress", email);
            out.put("city", city);
            out.put("gender", gender);
            send(resp, 201, out);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "POST failed", e);
            send(resp, 500, Map.of("error", "Database error"));
        }
    }

    private static Map<String, Object> row(ResultSet rs) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("fullName", rs.getString("full_name"));
        m.put("emailAddress", rs.getString("email_address"));
        m.put("city", rs.getString("city"));
        m.put("gender", rs.getString("gender"));
        m.put("createdAt", rs.getString("created_at"));
        return m;
    }

    private static String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString().trim() : "";
    }

    private static String validate(String name, String email, String city, String gender) {
        if (name.isEmpty() || email.isEmpty() || city.isEmpty() || gender.isEmpty())
            return "fullName, emailAddress, city and gender are all required.";
        if (name.length() > 100) return "fullName is too long (max 100).";
        if (city.length() > 100) return "city is too long (max 100).";
        if (email.length() > 150) return "emailAddress is too long (max 150).";
        if (gender.length() > 50) return "gender is too long (max 50).";
        if (!EMAIL.matcher(email).matches()) return "emailAddress is not valid.";
        return null;
    }

    private static void send(HttpServletResponse resp, int status, Object payload) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(GSON.toJson(payload));
    }
}
