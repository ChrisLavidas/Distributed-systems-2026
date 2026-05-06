package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/**
 * Tiny connection status indicator: green pulsing dot + "LIVE Xms"
 * or red dot + "OFFLINE". Updated from GamePlayActivity / MainActivity.
 */
public class ConnectionStatusView extends View {

    public enum Status { UNKNOWN, ONLINE, OFFLINE, CONNECTING }

    private Status status  = Status.UNKNOWN;
    private long   latency = 0;
    private float  pulse   = 0f;
    private ValueAnimator pulseAnim;

    private final Paint dotP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ConnectionStatusView(Context c) { super(c); init(); }
    public ConnectionStatusView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        txtP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        txtP.setTextAlign(Paint.Align.LEFT);
        glowP.setStyle(Paint.Style.STROKE);

        pulseAnim = ValueAnimator.ofFloat(0f, 1f);
        pulseAnim.setDuration(1400);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.addUpdateListener(v -> { pulse = (float)v.getAnimatedValue(); invalidate(); });
        pulseAnim.start();
    }

    public void setOnline(long latencyMs) {
        status = Status.ONLINE; latency = latencyMs; invalidate();
    }
    public void setOffline()     { status = Status.OFFLINE;    invalidate(); }
    public void setConnecting()  { status = Status.CONNECTING; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        float r = Math.min(W, H) * .2f;
        float cx = r * 1.4f, cy = H / 2f;

        int col;
        String label;
        switch (status) {
            case ONLINE:     col = 0xFF34D399; label = "LIVE  " + latency + "ms"; break;
            case OFFLINE:    col = 0xFFF87171; label = "OFFLINE"; break;
            case CONNECTING: col = 0xFFFFD84A; label = "CONNECTING…"; break;
            default:         col = 0xFF555577; label = ""; break;
        }

        // Glow ring
        if (status == Status.ONLINE) {
            glowP.setColor(col & 0x00FFFFFF | (int)((1f - pulse) * 80) << 24);
            glowP.setStrokeWidth(r * .8f);
            c.drawCircle(cx, cy, r * (1f + pulse * .8f), glowP);
        }
        // Dot
        dotP.setColor(col);
        c.drawCircle(cx, cy, r, dotP);

        // Label
        txtP.setColor(col);
        txtP.setTextSize(H * .45f);
        c.drawText(label, cx + r * 2f, cy + txtP.getTextSize() * .35f, txtP);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow(); if (pulseAnim != null) pulseAnim.cancel();
    }
}
