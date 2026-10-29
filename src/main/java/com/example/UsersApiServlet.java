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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
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
 *  Filters (query string, case-insensitive exact match, combined with AND):
 *     id, name (or fullName), email (or emailAddress), city, gender, createdDate (yyyy-MM-dd, UTC)
 *     e.g.  ?city=mumbai   or   ?city=MUMBAI&gender=male   or   ?createdDate=2026-10-19
 *     id accepts several values:  ?id=11,12   or   ?id=11&id=12
 *     Unknown filter names are rejected with 400 so a typo can never widen a delete.
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

    /** Text filters: query-string name -> database column (compared case-insensitively). */
    private static final Map<String, String> TEXT_FILTERS = new LinkedHashMap<>();
    private static final List<String> ALLOWED_FILTERS = new ArrayList<>();
    static {
        TEXT_FILTERS.put("name", "full_name");
        TEXT_FILTERS.put("fullName", "full_name");
        TEXT_FILTERS.put("email", "email_address");
        TEXT_FILTERS.put("emailAddress", "email_address");
        TEXT_FILTERS.put("city", "city");
        TEXT_FILTERS.put("gender", "gender");
        ALLOWED_FILTERS.addAll(TEXT_FILTERS.keySet());
        ALLOWED_FILTERS.add("id");
        ALLOWED_FILTERS.add("createdDate");
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

        Filter f = null;
        if (!one) {
            f = buildFilter(req);
            if (f.error != null) { send(resp, 400, err(f.error)); return; }
        }
        String sql = one
                ? "SELECT " + COLS + " FROM dbo.users WHERE id = ?"
                : "SELECT " + COLS + " FROM dbo.users" + f.where + " ORDER BY id";

        try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (one) {
                Integer id = parseId(path);
                if (id == null) { send(resp, 400, err("id must be a number")); return; }
                ps.setInt(1, id);
            } else {
                bind(ps, f.values);
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
            if (out.get("id") instanceof Integer newId) {
                D6Notifier.send(newId, str(body, "fullName"), str(body, "emailAddress"),
                        str(body, "city"), str(body, "gender"));
            }
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

        Filter f = null;
        if (!byId) {
            f = buildFilter(req);
            if (f.error != null) { send(resp, 400, err(f.error)); return; }
            if (f.where.isEmpty()) {
                // Never allow "delete everything" by accident.
                send(resp, 400, err("Provide an id (DELETE /api/users/5) or at least one filter: "
                        + "id, name, email, city, gender, createdDate (e.g. DELETE /api/users?city=mumbai)."));
                return;
            }
        }

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

            try (PreparedStatement ps = con.prepareStatement("DELETE FROM dbo.users" + f.where)) {
                bind(ps, f.values);
                int n = ps.executeUpdate();
                if (n == 0) {
                    Map<String, Object> m = err("No users matched");
                    m.put("filters", f.applied);
                    send(resp, 404, m);
                } else {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("deletedCount", n);
                    out.put("filters", f.applied);
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

    /** The result of reading the query string: a WHERE clause, its values, or an error message. */
    private static final class Filter {
        String where = "";
        String error;
        final List<Object> values = new ArrayList<>();
        final Map<String, Object> applied = new LinkedHashMap<>();
    }

    /**
     * Reads ?id=, ?name=, ?email=, ?city=, ?gender=, ?createdDate= from the query string.
     * Text filters ignore upper/lower case (LOWER on both sides, whatever the database collation is).
     * Any unknown parameter is rejected so a typo can never turn a narrow delete into a wide one.
     */
    private static Filter buildFilter(HttpServletRequest req) {
        Filter f = new Filter();

        for (String param : req.getParameterMap().keySet()) {
            if (!ALLOWED_FILTERS.contains(param)) {
                f.error = "Unknown filter '" + param + "'. Allowed: " + String.join(", ", ALLOWED_FILTERS) + ".";
                return f;
            }
        }

        List<String> parts = new ArrayList<>();

        for (Map.Entry<String, String> e : TEXT_FILTERS.entrySet()) {
            String v = clean(req.getParameter(e.getKey()));
            if (v.isEmpty()) continue;
            parts.add("LOWER(" + e.getValue() + ") = LOWER(?)");   // column names come from our own whitelist
            f.values.add(v);
            f.applied.put(e.getKey(), v);
        }

        // id accepts one or many:  ?id=11   ?id=11,12   ?id=11&id=12
        String[] rawIds = req.getParameterValues("id");
        if (rawIds != null) {
            List<Integer> ids = new ArrayList<>();
            for (String raw : rawIds) {
                for (String piece : raw.split(",")) {
                    String p = piece.trim();
                    if (p.isEmpty()) continue;
                    try {
                        ids.add(Integer.valueOf(p));
                    } catch (NumberFormatException ex) {
                        f.error = "id must be a number, or a list like 11,12";
                        return f;
                    }
                }
            }
            if (ids.size() > 100) {
                f.error = "Too many ids (max 100 at once).";
                return f;
            }
            if (!ids.isEmpty()) {
                parts.add("id IN (" + String.join(", ", Collections.nCopies(ids.size(), "?")) + ")");
                f.values.addAll(ids);
                f.applied.put("id", ids);
            }
        }

        String date = clean(req.getParameter("createdDate"));
        if (!date.isEmpty()) {
            try {
                f.values.add(java.sql.Date.valueOf(LocalDate.parse(date)));
            } catch (DateTimeParseException ex) {
                f.error = "createdDate must look like 2026-10-19";
                return f;
            }
            parts.add("CONVERT(date, created_at) = ?");
            f.applied.put("createdDate", date);
        }

        if (!parts.isEmpty()) f.where = " WHERE " + String.join(" AND ", parts);
        return f;
    }

    private static void bind(PreparedStatement ps, List<Object> values) throws Exception {
        int i = 1;
        for (Object v : values) ps.setObject(i++, v);
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
