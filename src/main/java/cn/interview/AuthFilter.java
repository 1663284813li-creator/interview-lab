package cn.interview;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
    private final JdbcTemplate db;
    public AuthFilter(JdbcTemplate db) { this.db = db; }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        return !r.getRequestURI().startsWith("/api/") || r.getRequestURI().equals("/api/auth/register");
    }
    @Override protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain) throws ServletException, IOException {
        String auth = r.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) { s.sendError(401); return; }
        var ids = db.query("SELECT id FROM account WHERE token_hash=?", (rs, n) -> rs.getObject(1, UUID.class), hash(auth.substring(7)));
        if (ids.isEmpty()) { s.sendError(401); return; }
        r.setAttribute("owner", ids.getFirst());
        s.setHeader("Cache-Control", "no-store");
        chain.doFilter(r, s);
    }
    static UUID owner(HttpServletRequest r) { return (UUID) r.getAttribute("owner"); }
}
