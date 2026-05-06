package com.funGames.app.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.widget.EditText;

/**
 * Utility class that shakes an EditText and shows an inline
 * error message below it.
 *
 * Usage:
 *   ShakeErrorView.shake(etBet, "Bet must be positive");
 */
public class ShakeErrorView extends View {

    private final Paint bgP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String msg = "";
    private float alpha2 = 0f;

    public ShakeErrorView(Context c) { super(c); init(); }
    public ShakeErrorView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        bgP.setColor(0x22F87171);
        txtP.setColor(0xFFF87171);
        txtP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        txtP.setTextSize(36f);
        setVisibility(GONE);
    }

    public void showError(String message) {
        msg = message; alpha2 = 1f;
        setVisibility(VISIBLE);
        invalidate();
        postDelayed(() -> {
            android.animation.ValueAnimator fade =
                    android.animation.ValueAnimator.ofFloat(1f, 0f);
            fade.setDuration(1500);
            fade.setStartDelay(1000);
            fade.addUpdateListener(v -> { alpha2=(float)v.getAnimatedValue(); invalidate(); });
            fade.addListener(new android.animation.AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(android.animation.Animator a){
                    setVisibility(GONE);
                }
            });
            fade.start();
        }, 100);
    }

    @Override protected void onDraw(Canvas c) {
        int W=getWidth(), H=getHeight();
        bgP.setAlpha((int)(alpha2*80));
        c.drawRoundRect(new RectF(0,0,W,H), 8,8, bgP);
        txtP.setAlpha((int)(alpha2*255));
        c.drawText(msg, W*.03f, H*.72f, txtP);
    }

    /** Static convenience — shake the field and show error below it. */
    public static void shake(EditText field, String errorMsg) {
        // Red border flash
        field.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFF87171));
        // Shake animation
        ObjectAnimator shake = ObjectAnimator.ofFloat(field, "translationX",
                0f, -18f, 18f, -14f, 14f, -8f, 8f, 0f);
        shake.setInterpolator(new CycleInterpolator(1f));
        shake.setDuration(500);
        shake.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator a){
                // Reset border after shake
                field.setBackgroundTintList(null);
            }
        });
        shake.start();
    }
}
