package com.dcim.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private final SecretKey key;
    private final long accessTtlMinutes;

    public JwtService(@Value("${dcim.jwt.secret}") String secret, @Value("${dcim.jwt.access-ttl-minutes}") long accessTtlMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("dcim.jwt.secret 长度必须 >= 32 字节，当前 " + bytes.length + " 字节");
        } else {
            this.key = Keys.hmacShaKeyFor(bytes);
            this.accessTtlMinutes = accessTtlMinutes;
        }
    }

    public String createAccessToken(String username, String name, List<String> roles, boolean isAdmin) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(username)
            .claim("name", name)
            .claim("roles", roles)
            .claim("isAdmin", isAdmin)
            // 毫秒级签发时间：与 users.token_valid_after 比较，实现登出/改密后旧令牌立即失效
            .claim("iat_ms", now)
            .id(java.util.UUID.randomUUID().toString())
            .issuedAt(new Date(now))
            .expiration(new Date(now + this.accessTtlMinutes * 60000L))
            .signWith(this.key)
            .compact();
    }

    /** 令牌签发时刻（毫秒）；旧令牌无 iat_ms 时退化为秒级 iat。 */
    public static long issuedAtMillis(Claims claims) {
        Object ms = claims.get("iat_ms");
        if (ms instanceof Number n) {
            return n.longValue();
        }
        Date iat = claims.getIssuedAt();
        return iat == null ? 0L : iat.getTime();
    }

    public Claims parse(String token) {
        return (Claims)Jwts.parser().verifyWith(this.key).build().parseSignedClaims(token).getPayload();
    }
}
