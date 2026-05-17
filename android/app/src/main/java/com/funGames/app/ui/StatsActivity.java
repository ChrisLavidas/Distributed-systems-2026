package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.funGames.app.R;
import com.funGames.app.util.BetHistory;
import com.funGames.app.util.PlayerProfile;
import com.funGames.app.util.Session;
import java.util.List;
import java.util.Locale;

public class StatsActivity extends AppCompatActivity {

    public static void start(Context ctx) {
        ctx.startActivity(new Intent(ctx, StatsActivity.class));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);

        // Gold animated background
        CasinoBackgroundView bg = new CasinoBackgroundView(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        // Stats canvas
        StatsCanvasView canvas = new StatsCanvasView(this);
        root.addView(canvas, new FrameLayout.LayoutParams(-1, -1));

        // Back button
        TextView back = new TextView(this);
        back.setText("← BACK");
        back.setTextColor(0xFFFFD700);
        back.setTextSize(14f);
        back.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        back.setPadding(48, 0, 48, 0);
        back.setGravity(android.view.Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, 140);
        lp.topMargin = 80; lp.leftMargin = 0;
        root.addView(back, lp);
        back.setOnClickListener(v -> finish());

        setContentView(root);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static class StatsCanvasView extends View {

        private final Paint overlayP = new Paint();
        private final Paint titleP   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelP   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valueP   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cardP    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cardBrdP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint goldP    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barWinP  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barLosP  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint axisP    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint xpBgP    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint xpFgP    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint divP     = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float animFrac = 0f;
        private ValueAnimator anim;

        public StatsCanvasView(Context c) { super(c); init(); }
        public StatsCanvasView(Context c, AttributeSet a) { super(c, a); init(); }

        private void init() {
            overlayP.setColor(0xCC050300);

            titleP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            titleP.setColor(0xFFFFD700);
            titleP.setTextAlign(Paint.Align.CENTER);
            titleP.setShadowLayer(12f, 0, 0, 0x88FFD700);

            labelP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            labelP.setColor(0xFF8A7040);
            labelP.setLetterSpacing(0.12f);

            valueP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            valueP.setColor(0xFFFFF8E7);

            goldP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            goldP.setColor(0xFFFFD700);

            cardP.setColor(0xEE100D00);
            cardBrdP.setStyle(Paint.Style.STROKE);
            cardBrdP.setStrokeWidth(1.5f);
            cardBrdP.setColor(0xFF2A2000);

            barWinP.setColor(0xFF34D399);
            barLosP.setColor(0xFFF87171);

            axisP.setColor(0xFF2A2000);
            axisP.setStyle(Paint.Style.STROKE);
            axisP.setStrokeWidth(1.5f);

            xpBgP.setColor(0xFF1A1400);
            xpFgP.setColor(0xFFFFD700);

            divP.setColor(0xFF2A2000);
            divP.setStyle(Paint.Style.STROKE);
            divP.setStrokeWidth(1f);

            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1000);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(v -> { animFrac = (float) v.getAnimatedValue(); invalidate(); });
            anim.start();
        }

        @Override protected void onDraw(Canvas c) {
            int W = getWidth(), H = getHeight();
            if (W == 0 || H == 0) return;

            // Dark overlay
            c.drawRect(0, 0, W, H, overlayP);

            float pad = W * 0.05f;
            float y = H * 0.12f;

            // Title
            titleP.setTextSize(W * 0.062f);
            c.drawText("MY STATS", W / 2f, y, titleP);
            y += H * 0.015f;

            // Gold divider line
            c.drawLine(pad * 3, y, W - pad * 3, y, divP);
            y += H * 0.025f;

            // Player info
            PlayerProfile pp = null;
            try { pp = PlayerProfile.get(getContext()); } catch (Exception ignored) {}

            String playerId = "";
            try { playerId = Session.get().getPlayerId(); } catch (Exception ignored) {}

            if (pp != null) {
                // Level card
                drawCard(c, pad, y, W - pad, y + H * 0.09f);
                labelP.setTextSize(W * 0.03f);
                c.drawText("PLAYER", pad * 1.6f, y + H * 0.03f, labelP);
                valueP.setTextSize(W * 0.042f);
                c.drawText(playerId, pad * 1.6f, y + H * 0.065f, valueP);

                // Level badge right side
                goldP.setTextAlign(Paint.Align.RIGHT);
                goldP.setTextSize(W * 0.038f);
                c.drawText("Level " + pp.getLevel() + "  •  " + pp.getLevelTitle(),
                        W - pad * 1.6f, y + H * 0.065f, goldP);
                goldP.setTextAlign(Paint.Align.LEFT);
                y += H * 0.1f;

                // XP Bar
                float bx = pad, bw = W - pad * 2f, bh = H * 0.018f;
                c.drawRoundRect(new RectF(bx, y, bx + bw, y + bh), bh / 2, bh / 2, xpBgP);
                float progress = pp.getLevelProgress() * animFrac;
                if (progress > 0) {
                    xpFgP.setShader(new LinearGradient(bx, 0, bx + bw, 0,
                            new int[]{0xFFFFD700, 0xFFB8860B}, null, Shader.TileMode.CLAMP));
                    c.drawRoundRect(new RectF(bx, y, bx + bw * progress, y + bh), bh / 2, bh / 2, xpFgP);
                }
                labelP.setTextSize(W * 0.028f);
                labelP.setTextAlign(Paint.Align.RIGHT);
                c.drawText(pp.getXP() + " XP", W - pad, y - H * 0.006f, labelP);
                labelP.setTextAlign(Paint.Align.LEFT);
                y += H * 0.032f;

                // 4 stat cards
                float cardW = (W - pad * 3f) / 2f;
                float cardH = H * 0.085f;

                // Row 1
                drawStatCard(c, pad, y, cardW, cardH, "TOTAL BETS",
                        String.valueOf(pp.getTotalBets()), 0xFFFFF8E7);
                drawStatCard(c, pad * 2f + cardW, y, cardW, cardH, "WIN RATE",
                        String.format(Locale.US, "%.1f%%", pp.getWinRate()), 0xFF34D399);
                y += cardH + pad * 0.8f;

                // Row 2
                String netStr = String.format(Locale.US, "%+.2f FUN",
                        pp.getTotalWon() - pp.getTotalLost());
                int netColor = (pp.getTotalWon() >= pp.getTotalLost()) ? 0xFF34D399 : 0xFFF87171;
                drawStatCard(c, pad, y, cardW, cardH, "NET P/L", netStr, netColor);
                drawStatCard(c, pad * 2f + cardW, y, cardW, cardH, "BEST WIN",
                        String.format(Locale.US, "+%.2f FUN", pp.getBestWin()), 0xFFFFD700);
                y += cardH + pad;
            }

            // Bar chart (last bets)
            List<BetHistory.Entry> hist = BetHistory.get().getAll();
            if (!hist.isEmpty()) {
                // Section title
                labelP.setTextSize(W * 0.028f);
                labelP.setTextAlign(Paint.Align.LEFT);
                c.drawText("LAST " + hist.size() + " BETS", pad, y, labelP);
                c.drawLine(pad, y + H * 0.005f, W - pad, y + H * 0.005f, divP);
                y += H * 0.022f;

                float chartH = H * 0.15f;
                float barW2  = (W - pad * 2f) / hist.size() - 6f;
                float maxAbs = 0.01f;
                for (BetHistory.Entry e : hist)
                    maxAbs = Math.max(maxAbs, (float) Math.abs(e.net()));

                float baseline = y + chartH / 2f;

                // Axis
                c.drawLine(pad, baseline, W - pad, baseline, axisP);

                for (int i = 0; i < hist.size(); i++) {
                    BetHistory.Entry e = hist.get(i);
                    float net = (float) e.net();
                    float bh2 = Math.abs(net) / maxAbs * (chartH / 2f) * animFrac;
                    float bx  = pad + i * (barW2 + 6f);
                    RectF rect;
                    if (net >= 0) {
                        rect = new RectF(bx, baseline - bh2, bx + barW2, baseline);
                        c.drawRoundRect(rect, 3, 3, barWinP);
                    } else {
                        rect = new RectF(bx, baseline, bx + barW2, baseline + bh2);
                        c.drawRoundRect(rect, 3, 3, barLosP);
                    }
                }
                y += chartH + H * 0.015f;

                // Legend
                barWinP.setAlpha(180);
                c.drawRect(pad, y, pad + W * 0.025f, y + H * 0.012f, barWinP);
                barWinP.setAlpha(200);
                labelP.setTextSize(W * 0.026f);
                c.drawText(" WIN", pad + W * 0.03f, y + H * 0.01f, labelP);
                barLosP.setAlpha(180);
                c.drawRect(pad + W * 0.2f, y, pad + W * 0.225f, y + H * 0.012f, barLosP);
                barLosP.setAlpha(200);
                c.drawText(" LOSS", pad + W * 0.23f, y + H * 0.01f, labelP);
            }
        }

        private void drawCard(Canvas c, float l, float t, float r, float b) {
            c.drawRoundRect(new RectF(l, t, r, b), 12, 12, cardP);
            c.drawRoundRect(new RectF(l, t, r, b), 12, 12, cardBrdP);
        }

        private void drawStatCard(Canvas c, float x, float y, float w, float h,
                                  String lbl, String val, int valColor) {
            drawCard(c, x, y, x + w, y + h);
            labelP.setTextSize(h * 0.22f);
            labelP.setTextAlign(Paint.Align.LEFT);
            c.drawText(lbl, x + w * 0.1f, y + h * 0.38f, labelP);
            valueP.setColor(valColor);
            valueP.setTextSize(h * 0.32f);
            c.drawText(val, x + w * 0.1f, y + h * 0.78f, valueP);
            valueP.setColor(0xFFFFF8E7); // reset
        }
    }
}
