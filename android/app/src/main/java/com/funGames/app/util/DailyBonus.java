package com.funGames.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Tracks whether the daily bonus has been claimed today (SharedPreferences). */
public class DailyBonus {
    private static final String PREF  = "casino_prefs";
    private static final String KEY   = "last_bonus_day";
    public  static final double AMOUNT = 100.0;

    public static boolean isAvailable(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long last = p.getLong(KEY, 0L);
        long now  = System.currentTimeMillis();
        // Available if last claim was > 20 hours ago
        return (now - last) > 20L * 3600 * 1000;
    }

    public static void claim(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
           .edit().putLong(KEY, System.currentTimeMillis()).apply();
    }
}
