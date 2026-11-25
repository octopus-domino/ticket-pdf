package ru.ticket.recognition;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.ticket.clientapi.*;

class ClientApiSecurityTest {
  @Test void scopesNeverGrantControlPlaneOrCrossProtocolAccess() {
    assertTrue(ClientApiKeyService.permits(Set.of("read"), "GET", "/api/templates"));
    assertFalse(ClientApiKeyService.permits(Set.of("read"), "POST", "/api/templates"));
    assertTrue(ClientApiKeyService.permits(Set.of("generate"), "POST", "/api/generate/sync"));
    assertTrue(ClientApiKeyService.permits(Set.of("generate"),"POST","/api/recognition"));
    assertFalse(ClientApiKeyService.permits(Set.of("read"),"POST","/api/recognition"));
    for (String path : new String[]{"/api/client-keys", "/api/auth/register", "/api/connectors", "/api/templates/../client-keys", "/api/templates/%2e%2e", "/actuator/metrics"})
      assertFalse(ClientApiKeyService.permits(Set.of("read", "generate", "data:write"), "GET", path), path);
  }
  @Test void encryptedCredentialsRoundTripAndRejectTamperingOrWrongMasterKey() {
    var cipher = new SecretCipher("test-master-secret-01234567890123456789");
    String encrypted = cipher.encrypt("private-client-api-key");
    assertFalse(encrypted.contains("private-client-api-key"));
    assertEquals("private-client-api-key", cipher.decrypt(encrypted));
    assertThrows(IllegalStateException.class, () -> new SecretCipher("wrong-key").decrypt(encrypted));
    assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted.substring(0, encrypted.length() - 5) + "AAAAA"));
    assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"));
  }
}
