package com.funGames.app.util;

import java.util.regex.Pattern;

/** Shared input validation — same rules as the backend DummyPlayer. */
public final class Validators {

    /** Player ID: exactly 2 uppercase letters followed by 4 digits (e.g. AN1234). */
    private static final Pattern PLAYER_ID = Pattern.compile("[A-Z]{2}[0-9]{4}");

    public static boolean isValidPlayerId(String id) {
        return id != null && PLAYER_ID.matcher(id).matches();
    }

    /** Manager ID: same format as playerId, e.g. AK1234. Matches backend rule. */
    public static boolean isValidManagerId(String id) {
        return id != null && PLAYER_ID.matcher(id).matches();
    }

    /** Parse a user-entered decimal allowing either '.' or ',' as separator. */
    public static Double parseDecimal(String s) {
        if (s == null) return null;
        String trimmed = s.trim().replace(',', '.');
        if (trimmed.isEmpty()) return null;
        try { return Double.parseDouble(trimmed); }
        catch (NumberFormatException e) { return null; }
    }

    private Validators() {}
}
