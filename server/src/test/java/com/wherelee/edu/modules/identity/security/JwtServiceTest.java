package com.wherelee.edu.modules.identity.security;

import com.wherelee.edu.modules.identity.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void tokenCarriesAccountTokenVersion() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60, "btry", "btry-web"));
        User user = new User(7L, 0L, "student", "hash", "学生", "STUDENT", "ACTIVE", false, 3);

        String token = service.createToken(user);
        JwtService.TokenPayload payload = service.parse(token);

        assertThat(payload.userId()).isEqualTo(7L);
        assertThat(payload.tokenVersion()).isEqualTo(3);
    }

    @Test
    void tokenWithWrongIssuerIsRejected() {
        JwtService issuerA = new JwtService(new JwtProperties(SECRET, 60, "other-system", "btry-web"));
        User user = new User(7L, 0L, "student", "hash", "学生", "STUDENT", "ACTIVE", false, 3);
        String token = issuerA.createToken(user);

        JwtService verifier = new JwtService(new JwtProperties(SECRET, 60, "btry", "btry-web"));
        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithWrongAudienceIsRejected() {
        JwtService otherAudience = new JwtService(new JwtProperties(SECRET, 60, "btry", "other-web"));
        User user = new User(7L, 0L, "student", "hash", "学生", "STUDENT", "ACTIVE", false, 3);
        String token = otherAudience.createToken(user);

        JwtService verifier = new JwtService(new JwtProperties(SECRET, 60, "btry", "btry-web"));
        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }
}
