package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
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
 *  GET    /api/users                 -> list all users
 *  GET    /api/users?city=Mumbai     -> list users matching the filters (see below)
 *  GET    /api/users/{id}            -> one user
 *  POST   /api/users                 -> create a user          (JSON body, all 4 fields)
 *  PUT    /api/users/{id}            -> replace a user         (JSON body, all 4 fields)
 *  PATCH  /api/users/{id}            -> update some fields     (JSON body, any of the 4 fields)
 *  DELETE /api/users/{id}            -> delete one user
 *  DELETE /api/users?city=Mumbai     -> delete every user matching the filters
 *
 *  Filters (query string, exact match, not case-sensitive, can be combined with AND):
 *     name, email, city, gender      e.g.  ?city=Mumbai   or   ?city=Mumbai&gender=Male
 *
 *  JSON fields: fullName, emailAddress, city, gender
 */
@WebServlet("/api/users/*")
public class UsersApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(UsersApiServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String COLS = "id, full_name, email_address, city, gender, created_at";

    /** JSON field name -> database column (also the whitelist for PATCH). */
    private static final Map<String, String> COLUMN = new LinkedHashMap<>();
    private static final Map<String, Integer> MAX_LEN = new LinkedHashMap<>();
    static {
        COLUMN.put("fullName", "full_name");
        COLUMN.put("emailAddress", "email_address");
        COLUMN.put("city", "city");
        COLUMN.put("gender", "gender");
        MAX_LEN.put("fullName", 100);
        MAX_LEN.put("emailAddress", 150);
        MAX_LEN.put("city", 100);
        MAX_LEN.put("gender", 50);
    }

    /** Query-string filter -> database column. */
    private static final Map<String, String> FILTERS = new LinkedHashMap<>();
    static {
        FILTERS.put("name", "full_name");
        FILTERS.put("email", "email_address");
        FILTERS.put("city", "city");
        FILTERS.put("gender", "gender");
    }

    /** HttpServlet has no doPatch, so route PATCH ourselves. */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    // ------------------------------------------------------------------ GET
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        boolean one = path != null && path.length() > 1;

        List<String> values = new ArrayList<>();
        String sql;
        if (one) sql = "SELECT " + COLS + " FROM dbo.users WHERE id = ?";
        else sql = "SELECT " + COLS + " FROM dbo.users" + buildFilter(req, values) + " ORDER BY id";

        try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (one) {
                Integer id = parseId(path);
                if (id == null) { send(resp, 400, err("id must be a number")); return; }
                ps.setInt(1, id);
            } else {
                int i = 1;
                for (String v : values) ps.setString(i++, v);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(row(rs));
            }
            if (one) {
                if (rows.isEmpty()) send(resp, 404, err("User not found"));
                else send(resp, 200, rows.get(0));
            } else {
                send(resp, 200, rows);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "GET failed", e);
            send(resp, 500, err("Database error"));
        }
    }

    // ----------------------------------------------------------------- POST
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject body = readBody(req, resp);
        if (body == null) return;

        String error = validateAll(body);
        if (error != null) { send(resp, 400, err(error)); return; }

        String sql = "INSERT INTO dbo.users (full_name, email_address, city, gender) VALUES (?, ?, ?, ?)";
        try (Connection con = Db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, str(body, "fullName"));
            ps.setString(2, str(body, "emailAddress"));
            ps.setString(3, str(body, "city"));
            ps.setString(4, str(body, "gender"));
            ps.executeUpdate();

            Map<String, Object> out = new LinkedHashMap<>();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) out.put("id", keys.getInt(1));
            }
            for (String k : COLUMN.keySet()) out.put(k, str(body, k));
            send(resp, 201, out);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "POST failed", e);
            send(resp, 500, err("Database error"));
        }
    }

    // ------------------------------------------------------------------ PUT
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer id = requireId(req, resp);
        if (id == null) return;

        JsonObject body = readBody(req, resp);
        if (body == null) return;

        String error = validateAll(body);
        if (error != null) { send(resp, 400, err(error)); return; }

        String sql = "UPDATE dbo.users SET full_name = ?, email_address = ?, city = ?, gender = ? WHERE id = ?";
        try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, str(body, "fullName"));
            ps.setString(2, str(body, "emailAddress"));
            ps.setString(3, str(body, "city"));
            ps.setString(4, str(body, "gender"));
            ps.setInt(5, id);
            if (ps.executeUpdate() == 0) { send(resp, 404, err("User not found")); return; }
            send(resp, 200, fetchOne(con, id));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "PUT failed", e);
            send(resp, 500, err("Database error"));
        }
    }

    // ---------------------------------------------------------------- PATCH
    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer id = requireId(req, resp);
        if (id == null) return;

        JsonObject body = readBody(req, resp);
        if (body == null) return;

        List<String> sets = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, String> e : COLUMN.entrySet()) {
            String key = e.getKey();
            if (!body.has(key)) continue;
            String value = str(body, key);
            String error = validateField(key, value);
            if (error != null) { send(resp, 400, err(error)); return; }
            sets.add(e.getValue() + " = ?");     // column names come from our own whitelist
            values.add(value);
        }
        if (sets.isEmpty()) {
            send(resp, 400, err("Send at least one of: fullName, emailAddress, city, gender."));
            return;
        }

        String sql = "UPDATE dbo.users SET " + String.join(", ", sets) + " WHERE id = ?";
        try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (String v : values) ps.setString(i++, v);
            ps.setInt(i, id);
            if (ps.executeUpdate() == 0) { send(resp, 404, err("User not found")); return; }
            send(resp, 200, fetchOne(con, id));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "PATCH failed", e);
            send(resp, 500, err("Database error"));
        }
    }

    // --------------------------------------------------------------- DELETE
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        boolean byId = path != null && path.length() > 1;

        try (Connection con = Db.getConnection()) {
            if (byId) {
                Integer id = parseId(path);
                if (id == null) { send(resp, 400, err("id must be a number")); return; }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM dbo.users WHERE id = ?")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() == 0) send(resp, 404, err("User not found"));
                    else send(resp, 200, Map.of("deleted", id));
                }
                return;
            }

            List<String> values = new ArrayList<>();
            String where = buildFilter(req, values);
            if (where.isEmpty()) {
                // Never allow "delete everything" by accident.
                send(resp, 400, err("Provide an id (DELETE /api/users/5) or at least one filter: "
                        + "name, email, city, gender (e.g. DELETE /api/users?city=Mumbai)."));
                return;
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM dbo.users" + where)) {
                int i = 1;
                for (String v : values) ps.setString(i++, v);
                int n = ps.executeUpdate();
                Map<String, Object> filters = new LinkedHashMap<>();
                for (String k : FILTERS.keySet()) {
                    String v = clean(req.getParameter(k));
                    if (!v.isEmpty()) filters.put(k, v);
                }
                if (n == 0) {
                    Map<String, Object> m = err("No users matched");
                    m.put("filters", filters);
                    send(resp, 404, m);
                } else {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("deletedCount", n);
                    out.put("filters", filters);
                    send(resp, 200, out);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "DELETE failed", e);
            send(resp, 500, err("Database error"));
        }
    }

    // -------------------------------------------------------------- helpers
    private static Map<String, Object> fetchOne(Connection con, int id) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("SELECT " + COLS + " FROM dbo.users WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row(rs) : Map.of("id", id);
            }
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

    /** Returns the id from /api/users/{id}, or sends 400 and returns null. */
    private static Integer requireId(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null || path.length() <= 1) {
            send(resp, 400, err("Provide an id, e.g. /api/users/5"));
            return null;
        }
        Integer id = parseId(path);
        if (id == null) send(resp, 400, err("id must be a number"));
        return id;
    }

    private static Integer parseId(String path) {
        try {
            return Integer.valueOf(path.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonObject readBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        try {
            return JsonParser.parseReader(req.getReader()).getAsJsonObject();
        } catch (Exception e) {
            send(resp, 400, err("Body must be valid JSON"));
            return null;
        }
    }

    /** Builds " WHERE a = ? AND b = ?" from the query string and fills `values`. "" if no filter given. */
    private static String buildFilter(HttpServletRequest req, List<String> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : FILTERS.entrySet()) {
            String v = clean(req.getParameter(e.getKey()));
            if (!v.isEmpty()) {
                parts.add(e.getValue() + " = ?");   // column names come from our own whitelist
                values.add(v);
            }
        }
        return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString().trim() : "";
    }

    private static String validateAll(JsonObject body) {
        for (String key : COLUMN.keySet()) {
            if (str(body, key).isEmpty())
                return "fullName, emailAddress, city and gender are all required.";
        }
        for (String key : COLUMN.keySet()) {
            String error = validateField(key, str(body, key));
            if (error != null) return error;
        }
        return null;
    }

    private static String validateField(String key, String value) {
        if (value.isEmpty()) return key + " cannot be empty.";
        if (value.length() > MAX_LEN.get(key)) return key + " is too long (max " + MAX_LEN.get(key) + ").";
        if (key.equals("emailAddress") && !EMAIL.matcher(value).matches()) return "emailAddress is not valid.";
        return null;
    }

    private static Map<String, Object> err(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    private static void send(HttpServletResponse resp, int status, Object payload) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(GSON.toJson(payload));
    }
}
