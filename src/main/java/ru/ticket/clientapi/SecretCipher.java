package ru.ticket.clientapi;


import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class SecretCipher {
  private final SecretKeySpec key;

  public SecretCipher(@Value("${app.secrets.encryption-key:${jwt.secret}}") String secret) {
    key = new SecretKeySpec(HexFormat.of().parseHex(hash("codoc-connector-v1:" + secret)), "AES");
  }

  public static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
  }

  public String encrypt(String value) {
    try {
      byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(encrypted);
    } catch (GeneralSecurityException error) { throw new IllegalStateException("Secret encryption failed", error); }
  }

  public String decrypt(String value) {
    try {
      String[] parts = value.split("\\.", 2);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
      return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException error) { throw new IllegalStateException("Stored secret cannot be decrypted; check the server encryption key", error); }
  }
}
