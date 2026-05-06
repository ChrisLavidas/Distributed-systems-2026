package com.funGames.app.util;

import android.animation.ValueAnimator;
import android.graphics.Color;

/**
 * Manages dynamic color theming based on game risk level.
 *
 * LOW    → green palette  (#34D399 accent)
 * MEDIUM → gold palette   (#FFD84A accent)
 * HIGH   → red palette    (#F87171 accent)
 *
 * Colors animate smoothly between themes.
 * Listeners are notified every frame so views can update themselves.
 */
public class ThemeManager {

    public interface ThemeListener {
        void onColorUpdate(int accent, int accentSoft, int bgTint);
    }

    // Theme definitions [accent, accentSoft, bgTint]
    private static final int[] THEME_LOW    = {0xFF34D399, 0x2234D399, 0x0A34D399};
    private static final int[] THEME_MEDIUM = {0xFFFFD84A, 0x22FFD84A, 0x0AFFD84A};
    private static final int[] THEME_HIGH   = {0xFFF87171, 0x22F87171, 0x0AF87171};

    private static ThemeManager INSTANCE;
    private int[] currentTheme = THEME_MEDIUM;
    private int[] targetTheme  = THEME_MEDIUM;
    private ThemeListener listener;
    private ValueAnimator anim;
    private float frac = 1f;

    public static ThemeManager get() {
        if (INSTANCE == null) INSTANCE = new ThemeManager();
        return INSTANCE;
    }

    public void setListener(ThemeListener l) { this.listener = l; }

    public void applyTheme(String riskLevel) {
        int[] next = THEME_MEDIUM;
        if (riskLevel != null) {
            switch (riskLevel.toLowerCase()) {
                case "low":  next = THEME_LOW;  break;
                case "high": next = THEME_HIGH; break;
            }
        }
        if (next == targetTheme) return;

        final int[] from = currentTheme;
        targetTheme = next;
        final int[] to = next;

        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(600);
        anim.addUpdateListener(v -> {
            frac = (float) v.getAnimatedValue();
            int[] interpolated = new int[3];
            for (int i = 0; i < 3; i++) {
                interpolated[i] = blendColors(from[i], to[i], frac);
            }
            currentTheme = interpolated;
            if (listener != null) {
                listener.onColorUpdate(interpolated[0], interpolated[1], interpolated[2]);
            }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                currentTheme = to;
            }
        });
        anim.start();
    }

    public int getAccent()     { return currentTheme[0]; }
    public int getAccentSoft() { return currentTheme[1]; }
    public int getBgTint()     { return currentTheme[2]; }

    private static int blendColors(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int a = (int)(Color.alpha(c1)*inv + Color.alpha(c2)*ratio);
        int r = (int)(Color.red  (c1)*inv + Color.red  (c2)*ratio);
        int g = (int)(Color.green(c1)*inv + Color.green(c2)*ratio);
        int b = (int)(Color.blue (c1)*inv + Color.blue (c2)*ratio);
        return Color.argb(a,r,g,b);
    }
}
