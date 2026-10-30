package com.quantumlock.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Secret key for signing and validating HMAC-SHA256 tokens.
     */
    private String secret;

    /**
     * Expiration duration in milliseconds (default: 1 hour).
     */
    private long expirationMs = 3600000;
}
