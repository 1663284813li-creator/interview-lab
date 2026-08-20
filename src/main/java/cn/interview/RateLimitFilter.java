package cn.interview;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    // Atomic fixed-window limiter; deliberately not described as a sliding-window implementation.
    private static final DefaultRedisScript<Long> SCRIPT=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",Long.class);
    public RateLimitFilter(StringRedisTemplate redis) { this.redis=redis; }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) { return !r.getRequestURI().startsWith("/api/"); }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c) throws ServletException,IOException {
        String identity=r.getHeader("Authorization");
        if (identity==null || identity.length()>300) identity=r.getRemoteAddr();
        try {
            Long count=redis.execute(SCRIPT,List.of("rate:{"+AuthFilter.hash(identity)+"}"));
            if (count!=null && count>60) { s.setHeader("Retry-After","60"); s.sendError(429); return; }
        } catch(Exception e) { s.sendError(503,"限流服务暂不可用"); return; }
        c.doFilter(r,s);
    }
}
