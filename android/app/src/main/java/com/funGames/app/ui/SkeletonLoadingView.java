package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/** Skeleton shimmer loader — shown while search is in flight. */
public class SkeletonLoadingView extends View {

    private final Paint bgP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardP= new Paint(Paint.ANTI_ALIAS_FLAG);

    private float shimmerX = 0f;
    private ValueAnimator anim;

    public SkeletonLoadingView(Context c) { super(c); init(); }
    public SkeletonLoadingView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        bgP.setColor(0xFF080A18);
        cardP.setColor(0xFF111328);
        anim = ValueAnimator.ofFloat(-0.3f, 1.3f);
        anim.setDuration(1200);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(v -> { shimmerX = (float)v.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        c.drawColor(0x00000000);

        float pad  = W * .04f;
        float cardH= H * .22f;
        float gap  = H * .03f;

        for (int i = 0; i < 4; i++) {
            float top = pad + i * (cardH + gap);
            RectF r = new RectF(pad, top, W - pad, top + cardH);
            c.drawRoundRect(r, 14, 14, cardP);

            // shimmer sweep
            float cx = shimmerX * W;
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setShader(new LinearGradient(cx - W * .18f, 0, cx + W * .18f, 0,
                    new int[]{0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 14, 14, sh);

            // skeleton lines inside card
            float lx = pad * 2f, ly = top + cardH * .28f;
            Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
            lp.setColor(0xFF1A1D38);
            c.drawRoundRect(new RectF(lx + 60, ly, lx + W * .55f, ly + cardH * .14f), 4, 4, lp);
            c.drawRoundRect(new RectF(lx + 60, ly + cardH * .22f, lx + W * .35f, ly + cardH * .34f), 4, 4, lp);
            c.drawRoundRect(new RectF(lx, ly, lx + 52, ly + 52), 8, 8, lp);
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow(); if (anim != null) anim.cancel();
    }
}
