package com.dcim.security;

import com.dcim.common.BusinessException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    private static final int MIN_CLASSES = 3;
    private static final String COMPLEXITY_MSG = "密码需至少12位，且包含大写/小写/数字/符号中的3类";
    private static final String RESOURCE = "/password-denylist.txt";
    private static final Set<String> COMMON = new HashSet<>();
    private static final Set<String> KEYWORDS = new HashSet<>();

    private PasswordPolicy() {
    }

    public static void validate(String password, String username) {
        validate(password, username, null);
    }

    public static void validate(String password, String username, String oldPassword) {
        if (password != null && !password.isEmpty()) {
            if (password.length() < 12) {
                throw BusinessException.badRequest("密码需至少12位，且包含大写/小写/数字/符号中的3类");
            } else if (password.length() > 64) {
                throw BusinessException.badRequest("密码最长 64 位");
            } else if (classCount(password) < 3) {
                throw BusinessException.badRequest("密码需至少12位，且包含大写/小写/数字/符号中的3类");
            } else {
                String lower = password.toLowerCase(Locale.ROOT);
                if (COMMON.contains(lower) || containsKeyword(lower)) {
                    throw BusinessException.badRequest("密码过于常见或包含易猜解词（如项目名、常见词），请更换");
                } else if (username != null && !username.isBlank() && lower.contains(username.toLowerCase(Locale.ROOT))) {
                    throw BusinessException.badRequest("密码不能包含用户名");
                } else if (oldPassword != null && password.equals(oldPassword)) {
                    throw BusinessException.badRequest("新密码不能与原密码相同");
                }
            }
        } else {
            throw BusinessException.badRequest("密码不能为空");
        }
    }

    static int classCount(String password) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else {
                symbol = true;
            }
        }

        int n = 0;
        if (upper) {
            n++;
        }

        if (lower) {
            n++;
        }

        if (digit) {
            n++;
        }

        if (symbol) {
            n++;
        }

        return n;
    }

    private static boolean containsKeyword(String lowerPassword) {
        for (String word : KEYWORDS) {
            if (lowerPassword.contains(word)) {
                return true;
            }
        }

        return false;
    }

    private static void load() {
        try {
            try (InputStream in = PasswordPolicy.class.getResourceAsStream("/password-denylist.txt")) {
                if (in == null) {
                    throw new IllegalStateException("缺少口令黑名单资源 /password-denylist.txt");
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

                String line;
                while ((line = reader.readLine()) != null) {
                    String entry = line.trim();
                    if (!entry.isEmpty() && !entry.startsWith("#")) {
                        if (entry.startsWith("~")) {
                            String word = entry.substring(1).toLowerCase(Locale.ROOT);
                            if (!word.isEmpty()) {
                                KEYWORDS.add(word);
                            }
                        } else {
                            String common = entry.toLowerCase(Locale.ROOT);
                            if (!common.isEmpty()) {
                                COMMON.add(common);
                            }
                        }
                    }
                }
            }
        } catch (IOException var7) {
            throw new IllegalStateException("读取口令黑名单失败 /password-denylist.txt", var7);
        }
    }

    static Set<String> commonPasswords() {
        return Set.copyOf(COMMON);
    }

    static Set<String> keywords() {
        return Set.copyOf(KEYWORDS);
    }

    static {
        load();
    }
}
