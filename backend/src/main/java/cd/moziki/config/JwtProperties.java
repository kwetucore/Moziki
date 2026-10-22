package cd.moziki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moziki.jwt")
public record JwtProperties(String secret, long ttlMinutes) {
}
