package com.vagent.eval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * X-Eval-Token 校验（P0 最小实现）。
 *
 * <p>说明：P0 方案允许 target 侧仅存 token 的 hash（不存明文），并用常量时间比较避免时序侧信道。</p>
 */
public class EvalTokenVerifier {

    private final EvalApiProperties props;

    public EvalTokenVerifier(EvalApiProperties props) {
        this.props = props;
    }

    public boolean isEnabled() {
        return props != null && props.isEnabled();
    }

    public boolean verifyOrFalse(String token) {
        if (!isEnabled()) {
            return false;
        }
        String t = token != null ? token.trim() : "";
        if (t.isEmpty()) {
            return false;
        }
        List<String> allowed = parseHashes(props.getTokenHash());
        if (allowed.isEmpty()) {
            return false;
        }
        String got = sha256HexLower(t);
        for (String h : allowed) {
            if (constantTimeEqualsAsciiLowerHex(got, h)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> parseHashes(String csv) {
        String raw = csv != null ? csv : "";
        String[] parts = raw.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String v = p != null ? p.trim() : "";
            if (!v.isEmpty()) {
                out.add(v.toLowerCase());
            }
        }
        return out;
    }

    private static String sha256HexLower(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return toHexLower(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = alphabet[v >>> 4];
            hex[i * 2 + 1] = alphabet[v & 0x0F];
        }
        return new String(hex);
    }

    private static boolean constantTimeEqualsAsciiLowerHex(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= (a.charAt(i) ^ b.charAt(i));
        }
        return r == 0;
    }
}

