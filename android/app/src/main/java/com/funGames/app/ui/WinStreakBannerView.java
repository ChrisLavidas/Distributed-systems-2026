package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Animated "🔥 3 wins in a row!" banner that slides in from the top.
 * Call show(streak) to trigger. Auto-hides after 2.5s.
 */
public class WinStreakBannerView extends View {

    private final Paint bgP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimP  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float slideY = -1f;   // -1 = hidden above screen
    private String msg   = "";
    private ValueAnimator slideIn, slideOut, autoHide;

    public WinStreakBannerView(Context c) { super(c); init(); }
    public WinStreakBannerView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        rimP.setStyle(Paint.Style.STROKE);
        rimP.setStrokeWidth(2f);
        rimP.setColor(0xFFFFD84A);
        txtP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        txtP.setTextAlign(Paint.Align.CENTER);
        txtP.setColor(0xFFFFFFFF);
        txtP.setShadowLayer(8f, 0, 0, 0xFF000000);
        setVisibility(GONE);
    }

    public void show(int streak) {
        if (streak < 2) return;
        String[] emojis = {"","","🔥","🔥🔥","⚡","⚡🔥","💥","🚀","👑","🎯","🏆"};
        String e = streak < emojis.length ? emojis[streak] : "🏆";
        msg = e + "  " + streak + " WINS IN A ROW!  " + e;

        if (slideIn  != null) slideIn.cancel();
        if (slideOut != null) slideOut.cancel();
        if (autoHide != null) autoHide.cancel();

        setVisibility(VISIBLE);
        slideY = -getHeight();

        slideIn = ValueAnimator.ofFloat(-getHeight() == 0 ? -200f : -getHeight(), 0f);
        slideIn.setDuration(500);
        slideIn.setInterpolator(new OvershootInterpolator(1.4f));
        slideIn.addUpdateListener(v -> { slideY = (float)v.getAnimatedValue(); invalidate(); });

        autoHide = ValueAnimator.ofFloat(0f, 1f);
        autoHide.setStartDelay(2500);
        autoHide.setDuration(400);
        autoHide.addUpdateListener(v -> {
            slideY = -(float)v.getAnimatedValue() * getHeight();
            invalidate();
        });
        autoHide.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                setVisibility(GONE);
            }
        });

        slideIn.start();
        autoHide.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        c.save();
        c.translate(0, slideY);

        bgP.setShader(new LinearGradient(0, 0, W, 0,
                new int[]{0xFFFF8C00, 0xFFFFD84A, 0xFFFF8C00},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(W*.04f, 4, W*.96f, H - 4), 12, 12, bgP);
        c.drawRoundRect(new RectF(W*.04f, 4, W*.96f, H - 4), 12, 12, rimP);

        txtP.setTextSize(H * .44f);
        c.drawText(msg, W / 2f, H * .65f, txtP);

        c.restore();
    }
}
