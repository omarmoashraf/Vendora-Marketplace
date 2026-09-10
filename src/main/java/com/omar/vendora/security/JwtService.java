package com.omar.vendora.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder B64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secretBytes;
    private final long ttlSeconds;

    public JwtService(
        ObjectMapper objectMapper,
        @Value("${security.jwt.secret}") String secret,
        @Value("${security.jwt.access-token-ttl-seconds:900}") long ttlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String generateToken(UUID userId, String email, List<String> roles) {
        long now = Instant.now().getEpochSecond();
        long exp = now + ttlSeconds;

        try {
            Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
            );
            String headerJson = objectMapper.writeValueAsString(header);
            String headerB64 = B64_URL_ENCODER.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> payload = Map.of(
                "sub", userId.toString(),
                "email", email,
                "roles", roles,
                "iat", now,
                "exp", exp
            );
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadB64 = B64_URL_ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            String contentToSign = headerB64 + "." + payloadB64;
            String signatureB64 = sign(contentToSign);

            return contentToSign + "." + signatureB64;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate JWT token", ex);
        }
    }

    public boolean validateToken(String token) {
        if (token == null) {
            return false;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        try {
            String contentToSign = parts[0] + "." + parts[1];
            String expectedSignature = sign(contentToSign);

            if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                return false;
            }

            byte[] payloadBytes = B64_URL_DECODER.decode(parts[1]);
            JsonNode payloadNode = objectMapper.readTree(payloadBytes);

            if (!payloadNode.has("exp")) {
                return false;
            }

            long exp = payloadNode.get("exp").asLong();
            return Instant.now().getEpochSecond() <= exp;
        } catch (Exception ex) {
            return false;
        }
    }

    public String extractSubject(String token) {
        JsonNode payloadNode = parsePayload(token);
        return payloadNode.has("sub") ? payloadNode.get("sub").asText() : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extractClaims(String token) {
        try {
            JsonNode payloadNode = parsePayload(token);
            return objectMapper.convertValue(payloadNode, Map.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to extract claims from JWT token", ex);
        }
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    private JsonNode parsePayload(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed JWT token");
        }
        try {
            byte[] payloadBytes = B64_URL_DECODER.decode(parts[1]);
            return objectMapper.readTree(payloadBytes);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse JWT payload", ex);
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA256));
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return B64_URL_ENCODER.encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Failed to calculate HMAC signature", ex);
        }
    }
}
