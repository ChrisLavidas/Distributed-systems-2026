package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import com.funGames.app.util.PlayerProfile;

/** Slides in from top, shows achievement badge, auto-dismisses. */
public class AchievementToastView extends View {

    private final Paint bgP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emoP  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float slideY = -1f;
    private String emoji="🏆", title="Achievement!", desc="";
    private ValueAnimator inAnim, outAnim;

    public AchievementToastView(Context c) { super(c); init(); }
    public AchievementToastView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        rimP.setStyle(Paint.Style.STROKE); rimP.setStrokeWidth(2f); rimP.setColor(0xFFFFD84A);
        txtP.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        txtP.setColor(0xFFFFFFFF); txtP.setShadowLayer(6f,0,0,0xFF000000);
        subP.setColor(0xFFB4B9D4); subP.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        emoP.setTextAlign(Paint.Align.CENTER);
        setVisibility(GONE);
    }

    public void show(PlayerProfile.Achievement ach) {
        emoji = ach.emoji; title = "UNLOCKED: " + ach.title; desc = ach.desc;
        if (inAnim != null) inAnim.cancel();
        if (outAnim!= null) outAnim.cancel();
        setVisibility(VISIBLE); slideY = -getHeight() == 0 ? -200f : -getHeight();
        inAnim = ValueAnimator.ofFloat(slideY, 0f);
        inAnim.setDuration(480); inAnim.setInterpolator(new OvershootInterpolator(1.3f));
        inAnim.addUpdateListener(v -> { slideY=(float)v.getAnimatedValue(); invalidate(); });
        outAnim = ValueAnimator.ofFloat(0f, -getHeight()==0?-200f:-getHeight());
        outAnim.setStartDelay(3200); outAnim.setDuration(350);
        outAnim.addUpdateListener(v -> { slideY=(float)v.getAnimatedValue(); invalidate(); });
        outAnim.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator a){ setVisibility(GONE); }
        });
        inAnim.start(); outAnim.start();
    }

    @Override protected void onDraw(Canvas c) {
        int W=getWidth(), H=getHeight();
        c.save(); c.translate(0, slideY);
        bgP.setShader(new LinearGradient(0,0,W,0,
                new int[]{0xFF1A1060,0xFF2A1880,0xFF1A1060},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(W*.03f,4,W*.97f,H-4),14,14,bgP);
        c.drawRoundRect(new RectF(W*.03f,4,W*.97f,H-4),14,14,rimP);
        emoP.setTextSize(H*.5f); c.drawText(emoji, W*.1f, H*.68f, emoP);
        txtP.setTextSize(H*.28f); c.drawText(title, W*.18f, H*.42f, txtP);
        subP.setTextSize(H*.22f); c.drawText(desc, W*.18f, H*.72f, subP);
        c.restore();
    }
}
