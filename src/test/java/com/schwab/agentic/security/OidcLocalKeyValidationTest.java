package com.schwab.agentic.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * LOCAL SIMULATION ONLY. Tokens are signed with a throwaway RSA key generated in-process and verified with the
 * matching public key, using the production validator composition. This proves signature, expiry, issuer and
 * audience enforcement in the Spring/Nimbus stack. It does NOT prove discovery, JWKS retrieval over TLS,
 * key rotation, issuer outage handling or real provider claim shapes; those need a live test identity provider.
 */
class OidcLocalKeyValidationTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "agentic-api";

    private static KeyPair signingKey;
    private static KeyPair otherKey;
    private static NimbusJwtDecoder decoder;

    @BeforeAll
    static void createKeysAndDecoder() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();
        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) signingKey.getPublic()).build();
        decoder.setJwtValidator(SecurityConfiguration.productionTokenValidator(ISSUER, AUDIENCE));
    }

    @Test
    void acceptsCorrectlySignedUnexpiredTokenWithIssuerAndAudience() throws Exception {
        assertDoesNotThrow(() -> decoder.decode(token(signingKey, ISSUER, AUDIENCE, Instant.now().plusSeconds(300))));
    }

    @Test
    void rejectsTokenSignedByAnUnknownKey() throws Exception {
        String token = token(otherKey, ISSUER, AUDIENCE, Instant.now().plusSeconds(300));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = token(signingKey, ISSUER, AUDIENCE, Instant.now().minusSeconds(3600));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        String token = token(signingKey, "https://evil.example.test", AUDIENCE, Instant.now().plusSeconds(300));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsTokenForAnotherAudience() throws Exception {
        String token = token(signingKey, ISSUER, "another-api", Instant.now().plusSeconds(300));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String[] parts = token(signingKey, ISSUER, AUDIENCE, Instant.now().plusSeconds(300)).split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"" + ISSUER + "\",\"aud\":[\"" + AUDIENCE + "\"],\"scope\":\"agentic.operator\","
                        + "\"exp\":" + (Instant.now().getEpochSecond() + 300) + ",\"sub\":\"attacker\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];
        assertThrows(JwtException.class, () -> decoder.decode(tampered));
    }

    private static String token(KeyPair key, String issuer, String audience, Instant expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("local-test-user")
                .claim("scope", "agentic.operator")
                .issueTime(Date.from(Instant.now().minusSeconds(3700)))
                .expirationTime(Date.from(expiry))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }
}
