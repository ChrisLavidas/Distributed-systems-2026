package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Horizontal scrolling live activity ticker.
 * Shows real bet events from the server broadcast.
 */
public class LiveTickerView extends View {

    private final Paint bgP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sepP = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Ring buffer of recent events (max 20)
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private static final int MAX_EVENTS = 20;

    private String  displayText = "";
    private float   scrollX     = 0f;
    private float   textWidth   = 0f;
    private ValueAnimator scrollAnim;
    private boolean hasRealEvents = false;

    public LiveTickerView(Context c) { super(c); init(); }
    public LiveTickerView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        bgP.setColor(0xEE080600);
        txtP.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        txtP.setAntiAlias(true);
        dotP.setColor(0xFF34D399);
        sepP.setColor(0xFF2A2000);
        sepP.setStyle(Paint.Style.STROKE);
        sepP.setStrokeWidth(1f);
        buildDisplayText();
    }

    /** Called when a real bet event arrives from the server */
    public void addBetEvent(String playerId, String gameName, double bet, double result, boolean jackpot) {
        hasRealEvents = true;
        double net = result - bet;
        String event;
        if (jackpot) {
            event = String.format(Locale.US,
                    "★ %s hit JACKPOT +%.0f FUN on %s", playerId, result, gameName);
        } else if (net > 0) {
            event = String.format(Locale.US,
                    "▲ %s won +%.2f FUN on %s", playerId, net, gameName);
        } else {
            event = String.format(Locale.US,
                    "▼ %s lost %.2f FUN on %s", playerId, Math.abs(net), gameName);
        }

        synchronized (events) {
            if (events.size() >= MAX_EVENTS) events.pollFirst();
            events.addLast(event);
        }
        buildDisplayText();
        restartScroll();
    }

    private void buildDisplayText() {
        StringBuilder sb = new StringBuilder();
        synchronized (events) {
            if (events.isEmpty()) {
                // Show placeholder until real events arrive
                sb.append("  LIVE  •  Connecting to game server...  •  Waiting for activity...  •  ");
            } else {
                for (String e : events) {
                    sb.append("  •  ").append(e);
                }
                sb.append("  •  ");
            }
        }
        displayText = sb.toString();
    }

    private void restartScroll() {
        if (getWidth() == 0) return;
        if (scrollAnim != null) scrollAnim.cancel();

        txtP.setTextSize(getHeight() * 0.46f);
        textWidth = txtP.measureText(displayText);
        if (textWidth <= 0) return;

        // Speed: ~80dp/sec for normal, ~60dp/sec for long text
        float density = getResources().getDisplayMetrics().density;
        float speed   = hasRealEvents ? 70f * density : 50f * density;
        long  dur     = (long) (textWidth / speed * 1000f);

        scrollAnim = ValueAnimator.ofFloat(0f, textWidth);
        scrollAnim.setDuration(Math.max(dur, 6000L));
        scrollAnim.setRepeatCount(ValueAnimator.INFINITE);
        scrollAnim.setRepeatMode(ValueAnimator.RESTART);
        scrollAnim.addUpdateListener(v -> {
            scrollX = (float) v.getAnimatedValue();
            invalidate();
        });
        scrollAnim.start();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        restartScroll();
    }

    @Override protected void onDraw(Canvas canvas) {
        int W = getWidth(), H = getHeight();
        canvas.drawRect(0, 0, W, H, bgP);

        // Top border
        canvas.drawLine(0, 0, W, 0, sepP);

        float sz = H * 0.46f;
        txtP.setTextSize(sz);

        // LIVE dot (pulsing green)
        long now = System.currentTimeMillis();
        int alpha = (int) (160 + 95 * Math.abs(Math.sin(now / 700.0)));
        dotP.setAlpha(alpha);
        canvas.drawCircle(H * 0.38f, H * 0.5f, H * 0.16f, dotP);

        // LIVE label
        txtP.setColor(0xFF34D399);
        txtP.setTextSize(H * 0.36f);
        txtP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("LIVE", H * 0.62f, H * 0.67f, txtP);

        // Vertical separator
        canvas.drawLine(H * 1.5f, H * 0.15f, H * 1.5f, H * 0.85f, sepP);

        // Scrolling events text
        txtP.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        txtP.setTextSize(sz);

        float startX = H * 1.7f - scrollX;
        float textY  = H * 0.68f;

        // Color: gold for jackpots/wins, muted for losses
        if (hasRealEvents) {
            txtP.setColor(0xFFD4AF37);
        } else {
            txtP.setColor(0xFF5A4820);
        }

        // Draw twice for seamless loop
        canvas.drawText(displayText, startX, textY, txtP);
        canvas.drawText(displayText, startX + textWidth, textY, txtP);

        // Edge fade left
        Paint fadeL = new Paint();
        fadeL.setShader(new LinearGradient(H * 1.5f, 0, H * 2.5f, 0,
                0xEE080600, 0x00080600, Shader.TileMode.CLAMP));
        canvas.drawRect(H * 1.5f, 0, H * 2.5f, H, fadeL);

        // Edge fade right
        Paint fadeR = new Paint();
        fadeR.setShader(new LinearGradient(W - H * 1.5f, 0, W, 0,
                0x00080600, 0xEE080600, Shader.TileMode.CLAMP));
        canvas.drawRect(W - H * 1.5f, 0, W, H, fadeR);

        postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (scrollAnim != null) scrollAnim.cancel();
    }
}
