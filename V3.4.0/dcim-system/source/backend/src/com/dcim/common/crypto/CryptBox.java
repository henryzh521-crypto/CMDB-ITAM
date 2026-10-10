package com.dcim.common.crypto;

import com.dcim.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 敏感字段加解密（AES-256-GCM，随机 12 字节 IV，密文 = base64(IV || 密文 || TAG)）。
 *
 * <p>v3.3 新增密钥轮换：{@code dcim.crypto.old-keys}（环境变量 DCIM_CRYPTO_KEY_OLD，逗号分隔）。
 * 加密永远用当前密钥；解密先试当前密钥、再依次试旧密钥。启动时 {@link CryptoKeyRotationRunner}
 * 会把仍由旧密钥加密的数据重新加密为当前密钥，完成后即可移除旧密钥。
 */
@Component
public class CryptBox {
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final List<SecretKeySpec> oldKeys;
    private final SecureRandom random = new SecureRandom();

    public CryptBox(@Value("${dcim.crypto.key}") String base64Key, @Value("${dcim.crypto.old-keys:}") String oldKeys) {
        this.key = parse(base64Key, "dcim.crypto.key");
        List<SecretKeySpec> olds = new ArrayList<>();
        if (oldKeys != null) {
            for (String k : oldKeys.split(",")) {
                if (!k.isBlank()) {
                    olds.add(parse(k, "dcim.crypto.old-keys"));
                }
            }
        }
        this.oldKeys = List.copyOf(olds);
    }

    private static SecretKeySpec parse(String base64Key, String name) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " 不是合法 base64");
        }
        if (raw.length != 32) {
            throw new IllegalStateException(name + " 解码后须为 32 字节（AES-256），当前 " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    public boolean hasOldKeys() {
        return !this.oldKeys.isEmpty();
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            this.random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + enc.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(enc, 0, out, IV_LEN, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BusinessException(500, "凭据加密失败");
        }
    }

    public String decrypt(String encoded) {
        byte[] all;
        try {
            all = Base64.getDecoder().decode(encoded);
        } catch (Exception e) {
            throw new BusinessException(500, "凭据解密失败（密文格式错误）");
        }
        String v = tryDecrypt(this.key, all);
        if (v != null) {
            return v;
        }
        for (SecretKeySpec k : this.oldKeys) {
            v = tryDecrypt(k, all);
            if (v != null) {
                return v;
            }
        }
        throw new BusinessException(500, "凭据解密失败（密钥是否更换过？轮换时请把旧密钥配置到 DCIM_CRYPTO_KEY_OLD）");
    }

    /** 是否能用「当前」密钥解密（用于判断是否需要重加密） */
    public boolean isCurrent(String encoded) {
        try {
            return tryDecrypt(this.key, Base64.getDecoder().decode(encoded)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String tryDecrypt(SecretKeySpec k, byte[] all) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            return new String(cipher.doFinal(all, IV_LEN, all.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
