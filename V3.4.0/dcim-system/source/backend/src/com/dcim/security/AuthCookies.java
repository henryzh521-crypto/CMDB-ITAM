package com.dcim.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {
    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";
    private final ClientIp clientIpResolver;

    public AuthCookies(ClientIp clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(this.clientIsHttps(request));
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = this.clientIsHttps(request);
        this.addClearedCookie(response, "access_token", secure);
        this.addClearedCookie(response, "refresh_token", secure);
    }

    public String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        } else {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    String v = cookie.getValue();
                    if (v != null && !v.isBlank()) {
                        return v;
                    }
                }
            }

            return null;
        }
    }

    private boolean clientIsHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        } else if (!this.clientIpResolver.isTrustedPeer(request)) {
            return false;
        } else {
            String proto = request.getHeader("X-Forwarded-Proto");
            if (proto != null && !proto.isBlank()) {
                int comma = proto.indexOf(44);
                String first = (comma >= 0 ? proto.substring(0, comma) : proto).trim();
                return "https".equalsIgnoreCase(first);
            } else {
                return false;
            }
        }
    }

    private void addClearedCookie(HttpServletResponse response, String name, boolean secure) {
        Cookie cookie = new Cookie(name, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }
}
