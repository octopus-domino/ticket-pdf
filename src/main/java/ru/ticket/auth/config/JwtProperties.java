package ru.ticket.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenTtlMs = 900_000L;
    private long refreshTokenTtlMs = 604_800_000L;
    private String issuer = "ticket-editor";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getAccessTokenTtlMs() { return accessTokenTtlMs; }
    public void setAccessTokenTtlMs(long v) { this.accessTokenTtlMs = v; }
    public long getRefreshTokenTtlMs() { return refreshTokenTtlMs; }
    public void setRefreshTokenTtlMs(long v) { this.refreshTokenTtlMs = v; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}