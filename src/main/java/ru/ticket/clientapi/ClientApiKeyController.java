package ru.ticket.clientapi;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;

@RestController @RequestMapping("/api/client-keys")
public class ClientApiKeyController {
  private final ClientApiKeyService service;
  private final TykClient gateway;
  private final String publicUrl;
  public ClientApiKeyController(ClientApiKeyService service, TykClient gateway, @Value("${app.client-api.public-url:/client-api}") String publicUrl) { this.service=service; this.gateway=gateway; this.publicUrl=publicUrl; }
  public record Request(@NotBlank @Size(max=100) String name, @NotEmpty @Size(max=3) List<String> scopes, @Min(1) @Max(365) int days) {}
  @GetMapping public Map<String, Object> list(@AuthenticationPrincipal UserIdPrincipal me) { return Map.of("enabled", gateway.enabled(), "baseUrl", publicUrl, "keys", service.list(me.userId())); }
  @PostMapping public ClientApiKeyService.Issued create(@AuthenticationPrincipal UserIdPrincipal me, @Valid @RequestBody Request request) {
    return service.issue(me.userId(), request.name(), request.scopes(), request.days());
  }
  @DeleteMapping("/{id}") public Map<String, Boolean> revoke(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id) { return Map.of("revoked", true, "gatewaySynchronized", service.revoke(me.userId(), id)); }
}
