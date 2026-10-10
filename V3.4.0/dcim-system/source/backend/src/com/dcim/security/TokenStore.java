package com.dcim.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

public interface TokenStore {
    void save(String refreshToken, String username, Duration ttl);

    Optional<String> findUsername(String refreshToken);

    /**
     * 原子地取出并作废 refresh token（一次性使用）。
     * 并发重放同一 refresh token 时只有一个请求能拿到用户名，其余返回 empty。
     */
    Optional<String> consume(String refreshToken);

    /**
     * 该 refresh token 是否在宽限期内刚被轮换过（多个浏览器标签页同时续期时，
     * 只有一个请求真正换发，其余请求凭浏览器里已更新的 Cookie 重试即可，不应被踢下线）。
     */
    boolean recentlyRotated(String refreshToken);

    void remove(String refreshToken);

    void removeAll(String username);

    /** 存储层只保存 refresh token 的 SHA-256 摘要，Redis 数据泄露也无法直接拿来续期。 */
    static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
