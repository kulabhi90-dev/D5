package com.example;

import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DELETE /api/users/{id} - used by D6 "Reject". Protected by SecurityFilter (/api/*), so a valid bearer token is required.
 */
@WebServlet("/api/users/*")
public class UsersDeleteServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(UsersDeleteServlet.class.getName());
    private static final Gson GSON = new Gson();

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        int id;
        try {
            id = Integer.parseInt(path == null ? "" : path.substring(1));
        } catch (NumberFormatException e) {
            send(resp, 400, Map.of("error", "id must be a number"));
            return;
        }
        try (Connection con = Db.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM dbo.users WHERE id = ?")) {
            ps.setInt(1, id);
            int n = ps.executeUpdate();
            if (n == 0) send(resp, 404, Map.of("error", "User not found"));
            else send(resp, 200, Map.of("deleted", id));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Delete failed", e);
            send(resp, 500, Map.of("error", "Server error"));
        }
    }

    private static void send(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(GSON.toJson(body));
    }
}
