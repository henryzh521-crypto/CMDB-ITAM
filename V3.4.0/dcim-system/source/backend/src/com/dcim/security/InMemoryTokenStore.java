package com.dcim.security;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTokenStore implements TokenStore {
    private final Map<String, InMemoryTokenStore.Entry> tokens = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> userIndex = new ConcurrentHashMap<>();
    /** 刚被轮换的 token → (用户名, 宽限到期时刻) */
    private final Map<String, Entry> rotated = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(String refreshToken, String username, Duration ttl) {
        this.evictExpired();
        long expireAt = System.currentTimeMillis() + ttl.toMillis();
        this.tokens.put(refreshToken, new InMemoryTokenStore.Entry(username, expireAt));
        this.userIndex.computeIfAbsent(username, k -> new ConcurrentHashMap<>()).put(refreshToken, expireAt);
    }

    @Override
    public synchronized Optional<String> findUsername(String refreshToken) {
        this.evictExpired();
        InMemoryTokenStore.Entry e = this.tokens.get(refreshToken);
        return e == null ? Optional.empty() : Optional.of(e.username());
    }

    @Override
    public synchronized Optional<String> consume(String refreshToken) {
        this.evictExpired();
        InMemoryTokenStore.Entry e = this.tokens.remove(refreshToken);
        if (e == null) {
            return Optional.empty();
        }
        Map<String, Long> idx = this.userIndex.get(e.username());
        if (idx != null) {
            idx.remove(refreshToken);
        }
        this.rotated.put(refreshToken, new Entry(e.username(), System.currentTimeMillis() + 30_000L));
        return Optional.of(e.username());
    }

    @Override
    public synchronized boolean recentlyRotated(String refreshToken) {
        Entry e = this.rotated.get(refreshToken);
        return e != null && !e.expired();
    }

    @Override
    public synchronized void remove(String refreshToken) {
        this.evictExpired();
        InMemoryTokenStore.Entry e = this.tokens.remove(refreshToken);
        if (e != null) {
            Map<String, Long> idx = this.userIndex.get(e.username());
            if (idx != null) {
                idx.remove(refreshToken);
            }
        }
    }

    @Override
    public synchronized void removeAll(String username) {
        this.rotated.values().removeIf(e -> e.username().equals(username));
        Map<String, Long> idx = this.userIndex.remove(username);
        if (idx != null) {
            idx.keySet().forEach(this.tokens::remove);
        }
    }

    private void evictExpired() {
        this.rotated.values().removeIf(Entry::expired);
        Iterator<Map.Entry<String, InMemoryTokenStore.Entry>> it = this.tokens.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, InMemoryTokenStore.Entry> en = it.next();
            if (en.getValue().expired()) {
                it.remove();
                Map<String, Long> idx = this.userIndex.get(en.getValue().username());
                if (idx != null) {
                    idx.remove(en.getKey());
                }
            }
        }
    }

    private static record Entry(String username, long expireAtMillis) {
        boolean expired() {
            return System.currentTimeMillis() > this.expireAtMillis;
        }
    }
}
