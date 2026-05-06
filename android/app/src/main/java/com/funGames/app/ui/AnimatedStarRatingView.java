package com.funGames.app.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Tap-to-rate star row with animated fill.
 * Stars bounce in one-by-one and glow gold when selected.
 */
public class AnimatedStarRatingView extends View {

    public interface RatingListener { void onRatingChanged(int stars); }

    private static final int MAX = 5;
    private final float[] starScale = {1f,1f,1f,1f,1f};
    private final float[] starAlpha = {0f,0f,0f,0f,0f};
    private final Paint  filledP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint  emptyP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int   selected = 0;
    private RatingListener listener;

    public AnimatedStarRatingView(Context c) { super(c); init(); }
    public AnimatedStarRatingView(Context c, AttributeSet a) { super(c,a); init(); }

    private void init() {
        filledP.setColor(0xFFFFD84A);
        filledP.setTextAlign(Paint.Align.CENTER);
        filledP.setTypeface(Typeface.DEFAULT_BOLD);
        filledP.setShadowLayer(14f,0,0,0xAAFFD84A);
        emptyP.setColor(0xFF444466);
        emptyP.setTextAlign(Paint.Align.CENTER);
        emptyP.setTypeface(Typeface.DEFAULT_BOLD);
        setClickable(true);
        // Entrance animation
        for (int i=0;i<MAX;i++) {
            final int idx=i;
            ValueAnimator a = ValueAnimator.ofFloat(0f,1f);
            a.setDuration(300); a.setStartDelay(80L*i);
            a.setInterpolator(new OvershootInterpolator(2f));
            a.addUpdateListener(v->{ starAlpha[idx]=(float)v.getAnimatedValue(); invalidate(); });
            a.start();
        }
    }

    public void setListener(RatingListener l) { listener=l; }
    public int  getRating() { return selected; }

    @Override protected void onDraw(Canvas c) {
        int W=getWidth(), H=getHeight();
        float size = Math.min(W/(MAX+1f), H*.8f);
        float gap  = W/(float)MAX;
        filledP.setTextSize(size);
        emptyP.setTextSize(size);
        for (int i=0;i<MAX;i++) {
            float x = gap*i + gap/2f;
            float y = H/2f + size*.38f;
            c.save();
            c.scale(starScale[i], starScale[i], x, H/2f);
            Paint p = (i<selected) ? filledP : emptyP;
            p.setAlpha((int)(starAlpha[i]*255));
            c.drawText(i<selected?"★":"☆", x, y, p);
            c.restore();
        }
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction()!=MotionEvent.ACTION_UP) return true;
        float gap = getWidth()/(float)MAX;
        int tapped = (int)(ev.getX()/gap)+1;
        if (tapped<1) tapped=1;
        if (tapped>MAX) tapped=MAX;
        selected=tapped;
        // Bounce each filled star
        for (int i=0;i<tapped;i++) {
            final int idx=i;
            ObjectAnimator bounce = ObjectAnimator.ofFloat(null,"dummy",1f,1.4f,1f);
            bounce.setDuration(250); bounce.setStartDelay(40L*i);
            bounce.addUpdateListener(v->{ starScale[idx]=(float)v.getAnimatedValue(); invalidate(); });
            bounce.start();
        }
        if (listener!=null) listener.onRatingChanged(selected);
        invalidate();
        return true;
    }
}
