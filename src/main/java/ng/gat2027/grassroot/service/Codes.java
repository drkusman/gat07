package ng.gat2027.grassroot.service;

import java.security.SecureRandom;
import java.util.Base64;

/** Member / referral codes and phone normalisation. */
public final class Codes {
    private Codes() {}
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no ambiguous 0/O 1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String make(String prefix, int len) {
        StringBuilder sb = new StringBuilder(prefix).append('-');
        for (int i = 0; i < len; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /** High-entropy, URL-safe token for one-time links (password reset, etc.) - not meant to be typed by a human. */
    public static String secureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String memberCode(long n) { return String.format("GAT-%010d", n); }

    /** 0803..., +234803..., 234803... -> 0803... */
    public static String normalizePhone(String p) {
        if (p == null) return "";
        String s = p.replaceAll("[\\s\\-()]", "");
        if (s.startsWith("+234")) s = "0" + s.substring(4);
        else if (s.startsWith("234") && s.length() == 13) s = "0" + s.substring(3);
        return s;
    }

    public static boolean validPhone(String normalized) { return normalized != null && normalized.matches("^0[789][01]\\d{8}$"); }

    /** 0803... -> 234803... (no leading +), the format the WhatsApp Cloud API expects for "to" numbers. */
    public static String nigeriaE164(String normalizedPhone) {
        return normalizedPhone != null && normalizedPhone.startsWith("0") ? "234" + normalizedPhone.substring(1) : normalizedPhone;
    }

    public static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
