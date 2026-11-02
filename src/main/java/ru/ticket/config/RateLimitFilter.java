package ru.ticket.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.ticket.auth.security.UserIdPrincipal;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Простой token-bucket rate limiter для дорогих эндпоинтов.
 *
 * Покрывает:
 *   /api/export/*   — рендер SVG/PDF/PDF-A, дорогой по CPU/памяти
 *   /api/generate   — батч-генерация, ещё дороже
 *
 * Ключ bucket'а: (routePrefix, userId | IP). Порог — из properties,
 * default 20/min для export и 10/min для generate (sync + async вместе).
 *
 * Cleanup: раз в 10 минут удаляются bucket'ы, не использованные час.
 * Иначе ConcurrentHashMap растёт под IP-сканерами.
 *
 * Сознательно НЕ полагается на @Scheduled, чтобы работать даже если
 * @EnableScheduling отключён в каком-то профиле.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long IDLE_EVICTION_NANOS = TimeUnit.HOURS.toNanos(1);

    private final boolean enabled;
    private final int exportPerMinute;
    private final int generatePerMinute;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleaner;

    public RateLimitFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.export-per-minute:20}") int exportPerMinute,
            @Value("${app.rate-limit.generate-per-minute:10}") int generatePerMinute) {
        this.enabled = enabled;
        this.exportPerMinute = Math.max(1, exportPerMinute);
        this.generatePerMinute = Math.max(1, generatePerMinute);
    }

    @PostConstruct
    void startCleaner() {
        if (!enabled) return;
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::cleanup, 10, 10, TimeUnit.MINUTES);
    }

    @PreDestroy
    void stopCleaner() {
        if (cleaner != null) cleaner.shutdownNow();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (!enabled) {
            chain.doFilter(req, res);
            return;
        }

        String route = routePrefixFor(req.getRequestURI());
        if (route == null) {
            chain.doFilter(req, res);
            return;
        }

        int limit = "export".equals(route) ? exportPerMinute : generatePerMinute;
        String bucketKey = route + "|" + clientKey(req);

        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(limit));

        if (!bucket.tryConsume()) {
            log.warn("rate_limit.exceeded key={} path={}", bucketKey, req.getRequestURI());
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"Too Many Requests\"}");
            return;
        }

        chain.doFilter(req, res);
    }

    private String routePrefixFor(String path) {
        if (path.startsWith("/api/export/")) return "export";
        if (path.startsWith("/api/generate")) return "generate";
        return null;
    }

    private String clientKey(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserIdPrincipal p) {
            return "u:" + p.userId();
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return "ip:" + xff.split(",")[0].trim();
        }
        return "ip:" + req.getRemoteAddr();
    }

    private void cleanup() {
        try {
            long cutoff = System.nanoTime() - IDLE_EVICTION_NANOS;
            int removed = 0;
            for (Map.Entry<String, TokenBucket> e : buckets.entrySet()) {
                if (e.getValue().lastUsedNanos < cutoff) {
                    buckets.remove(e.getKey(), e.getValue());
                    removed++;
                }
            }
            if (removed > 0) {
                log.debug("rate_limit.cleanup removed={} size={}", removed, buckets.size());
            }
        } catch (Exception e) {
            log.warn("rate_limit.cleanup failed", e);
        }
    }

    static final class TokenBucket {
        final int capacity;
        final double refillPerNano;
        double tokens;
        long lastRefillNanos;
        volatile long lastUsedNanos;

        TokenBucket(int capacity) {
            this.capacity = capacity;
            this.refillPerNano = capacity / 60_000_000_000.0; // capacity per minute
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
            this.lastUsedNanos = this.lastRefillNanos;
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                double refill = elapsed * refillPerNano;
                if (refill > 0) {
                    tokens = Math.min(capacity, tokens + refill);
                    lastRefillNanos = now;
                }
            }
            lastUsedNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}