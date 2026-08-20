package com.greedy.festa.global.security;

import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
@Getter
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration validity;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${app.jwt.admin-secret}") String secret,
            @Value("${app.jwt.admin-token-validity}") Duration validity,
            Clock clock) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.validity = validity;
        this.clock = clock;
    }

    public String issue(String username) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    public String parseUsername(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (ExpiredJwtException e) {
            throw new FestaException(CommonErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new FestaException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
