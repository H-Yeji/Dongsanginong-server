package org.samtuap.inong.domain.seller.securities;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.samtuap.inong.domain.seller.jwt.domain.SecretKeyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final SecretKeyFactory secretKeyFactory;

    @Value("${jwt.secret_key}")
    private String secretKey;

    @Value("${jwt.token.access_expiration_time}")
    private Long accessExpirationTime;

    @Value("${jwt.token.refresh_expiration_time}")
    private Long refreshExpirationTime;

    public String createToken(Long sellerId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);

        Date now = new Date();

        return Jwts.builder()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationTime))
                .subject(Long.toString(sellerId))
                .claims(claims)
                .signWith(secretKeyFactory.createSecretKey())
                .compact();
    }

    public String createRefreshToken(Long sellerId, String role) {
        Date now = new Date();
        String refreshToken = Jwts.builder()
                .subject(String.valueOf(sellerId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpirationTime))
                .signWith(secretKeyFactory.createSecretKey())
                .compact();

        return refreshToken;
    }
}