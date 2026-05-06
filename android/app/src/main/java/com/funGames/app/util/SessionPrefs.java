package com.funGames.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists login credentials to SharedPreferences.
 * Remembers: playerId/managerId, host, port, role, balance.
 * Called from LoginActivity (save) and SplashActivity (restore).
 */
public class SessionPrefs {
    private static final String PREF    = "session_prefs";
    private static final String KEY_ID  = "user_id";
    private static final String KEY_HOST= "host";
    private static final String KEY_PORT= "port";
    private static final String KEY_ROLE= "role";       // "PLAYER" or "MANAGER"
    private static final String KEY_BAL = "balance";
    private static final String KEY_API = "claude_api_key";

    public static void save(Context ctx, String userId, String role,
                            String host, int port, double balance) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_ID,   userId)
                .putString(KEY_HOST, host)
                .putInt   (KEY_PORT, port)
                .putString(KEY_ROLE, role)
                .putLong  (KEY_BAL,  Double.doubleToLongBits(balance))
                .apply();
    }

    public static boolean hasSaved(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String id = p.getString(KEY_ID, null);
        return id != null && !id.isEmpty();
    }

    public static String getUserId  (Context ctx){ return ctx.getSharedPreferences(PREF,0).getString(KEY_ID,""); }
    public static String getHost    (Context ctx){ return ctx.getSharedPreferences(PREF,0).getString(KEY_HOST,"10.0.2.2"); }
    public static int    getPort    (Context ctx){ return ctx.getSharedPreferences(PREF,0).getInt(KEY_PORT,5000); }
    public static String getRole    (Context ctx){ return ctx.getSharedPreferences(PREF,0).getString(KEY_ROLE,"PLAYER"); }
    public static double getBalance (Context ctx){ return Double.longBitsToDouble(ctx.getSharedPreferences(PREF,0).getLong(KEY_BAL,0L)); }

    public static void saveApiKey(Context ctx, String key) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_API, key).apply();
    }
    public static String getApiKey(Context ctx) {
        return ctx.getSharedPreferences(PREF, 0).getString(KEY_API, "");
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
