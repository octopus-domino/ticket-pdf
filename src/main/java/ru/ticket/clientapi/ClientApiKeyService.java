package ru.ticket.clientapi;


import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.auth.repository.UserRepository;


@Service
public class ClientApiKeyService {
  private final ClientApiKeyRepository keys;
  private final SecretCipher cipher;
  private final TykClient gateway;
  private final UserRepository users;

  public ClientApiKeyService(ClientApiKeyRepository keys, SecretCipher cipher, TykClient gateway, UserRepository users) {
    this.keys = keys; this.cipher = cipher; this.gateway = gateway; this.users = users;
  }

  public record View(String id, String name, String prefix, List<String> scopes, Instant createdAt, Instant expiresAt, boolean revoked, int monthlyRequests, Instant lastUsedAt) {}

  public record Issued(View key, String token) {}

  public List<View> list(String userId) { return keys.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList(); }

  public synchronized Issued issue(String userId, String name, List<String> scopes, int days) {
    if (!gateway.enabled()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Client API gateway is not configured");
    }
    if (name == null || name.isBlank() || name.length() > 100 || days < 1 || days > 365 || scopes == null || scopes.isEmpty()
        || !Set.of("read", "generate", "data:write").containsAll(scopes)) throw ApiException.badRequest("Invalid API key settings");
    if (keys.findByUserIdOrderByCreatedAtDesc(userId).stream().filter(k -> !k.revoked && k.expiresAt.isAfter(Instant.now())).count() >= 10)
      throw ApiException.conflict("Maximum 10 active client API keys");
    byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
    String token = "codoc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    var key = new ClientApiKey(); key.userId = userId; key.name = name.trim(); key.tokenHash = SecretCipher.hash(token);
    key.prefix = token.substring(0, 14); key.encryptedToken = cipher.encrypt(token); key.scopes = String.join(",", new TreeSet<>(scopes));
    key.expiresAt = Instant.now().plus(days, java.time.temporal.ChronoUnit.DAYS);
    gateway.create(token, userId, key.expiresAt);
    try { keys.saveAndFlush(key); }
    catch (RuntimeException error) { try { gateway.revoke(token); } catch (RuntimeException ignored) { } throw error; }
    return new Issued(view(key), token);
  }

  public boolean revoke(String userId, String id) {
    var key = keys.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("API key"));
    key.revoked = true; keys.saveAndFlush(key); // Commit local rejection even if gateway is down.
    try { gateway.revoke(cipher.decrypt(key.encryptedToken)); return true; }
    catch (RuntimeException error) { return false; }
  }
  @Transactional

  public String authenticate(String token, String method, String path) {
    if (token == null || !token.matches("codoc_[A-Za-z0-9_-]{43}")) {
      throw unauthorized();
    }
    var key = keys.lockByHash(SecretCipher.hash(token)).orElseThrow(ClientApiKeyService::unauthorized);
    Instant now = Instant.now();
    if (key.revoked || !key.expiresAt.isAfter(now) || !users.existsById(key.userId)) {
      throw unauthorized();
    }
    if (!permits(Set.of(key.scopes.split(",")), method, path)) {
      throw ApiException.forbidden("API key scope does not permit this operation");
    }
    long minute = now.getEpochSecond() / 60; String month = YearMonth.now(ZoneOffset.UTC).toString();
    if (key.minuteWindow != minute) { key.minuteWindow = minute; key.minuteCount = 0; }
    if (!month.equals(key.monthWindow)) { key.monthWindow = month; key.monthCount = 0; }
    if (key.minuteCount >= 60 || key.monthCount >= 10000) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "API key quota exceeded");
    }
    key.minuteCount++; key.monthCount++; key.lastUsedAt = now;
    return key.userId;
  }

  public static boolean permits(Set<String> scopes, String method, String path) {
    if (path.contains("%") || path.contains(";") || path.contains("..")) {
      return false;
    }
    boolean data = path.matches("/api/(templates|schemas|data-models)(/[a-zA-Z0-9_-]+)?");
    boolean jobs = path.matches("/api/generate/[a-zA-Z0-9_-]+(/result)?");
    if (method.equals("GET") && (data || jobs)) {
      return scopes.contains("read");
    }
    if(method.equals("GET")&&path.equals("/api/recognition/capabilities"))return scopes.contains("read");
    if(method.equals("POST")&&path.equals("/api/recognition"))return scopes.contains("generate");
    if (method.equals("POST") && Set.of("/api/generate", "/api/generate/sync", "/api/generate/preview").contains(path)) {
      return scopes.contains("generate");
    }
    if (method.equals("DELETE") && jobs && !path.endsWith("/result")) {
      return scopes.contains("generate");
    }
    return data && Set.of("POST", "PUT", "DELETE").contains(method) && scopes.contains("data:write");
  }

  private static ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired API key"); }

  private View view(ClientApiKey key) { return new View(key.id, key.name, key.prefix, List.of(key.scopes.split(",")), key.createdAt, key.expiresAt, key.revoked, key.monthCount, key.lastUsedAt); }
}
