package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.widget.AppCompatTextView;

import java.util.Locale;

/**
 * Balance TextView that animates the number counting from old → new value.
 * Drop-in replacement for plain TextView; call animateTo(newVal).
 */
public class AnimatedBalanceTextView extends AppCompatTextView {

    private double current = 0.0;
    private ValueAnimator anim;

    public AnimatedBalanceTextView(Context c) { super(c); }
    public AnimatedBalanceTextView(Context c, AttributeSet a) { super(c, a); }

    public void animateTo(double target) {
        if (anim != null) anim.cancel();
        // If the value is too large for float animation, just set it immediately
        if (Double.isInfinite(target) || Double.isNaN(target)
                || target > 1_000_000_000.0 || target < -1_000_000_000.0) {
            setValueImmediate(target);
            return;
        }
        double from = current;
        anim = ValueAnimator.ofFloat((float) from, (float) target);
        anim.setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(v -> {
            current = (float) v.getAnimatedValue();
            setText(String.format(Locale.US, "%.2f", current));
        });
        anim.start();
    }

    public void setValueImmediate(double val) {
        if (anim != null) anim.cancel();
        current = val;
        setText(String.format(Locale.US, "%.2f", val));
    }
}
