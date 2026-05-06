package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import com.funGames.app.util.BetHistory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Drawn bet history panel — last 10 bets as animated rows.
 * Shows: game name | bet | result | net (colour coded).
 */
public class BetHistoryView extends View {

    private final Paint bgP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hdrP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint winP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint loseP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float revealFrac = 0f;
    private ValueAnimator revealAnim;

    public BetHistoryView(Context ctx) { super(ctx); init(); }
    public BetHistoryView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        bgP.setColor(0xFF080A18);
        rowP.setStyle(Paint.Style.FILL);
        txtP.setColor(0xFFCCCCDD);
        txtP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        hdrP.setColor(0xFFFFD84A);
        hdrP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        hdrP.setTextAlign(Paint.Align.CENTER);
        winP.setColor(0xFF34D399);
        winP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        loseP.setColor(0xFFF87171);
        loseP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        rimP.setStyle(Paint.Style.STROKE);
        rimP.setColor(0xFF1E2455);
        rimP.setStrokeWidth(1.5f);
        barBg.setColor(0xFF111328);
        barFg.setColor(0xFF34D399);
        reveal();
    }

    public void refresh() { reveal(); }

    private void reveal() {
        if (revealAnim != null) revealAnim.cancel();
        revealFrac = 0f;
        revealAnim = ValueAnimator.ofFloat(0f, 1f);
        revealAnim.setDuration(600);
        revealAnim.addUpdateListener(v -> { revealFrac = (float)v.getAnimatedValue(); invalidate(); });
        revealAnim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        RectF bg = new RectF(0, 0, W, H);
        c.drawRoundRect(bg, 14, 14, bgP);
        c.drawRoundRect(bg, 14, 14, rimP);

        List<BetHistory.Entry> entries = BetHistory.get().getAll();
        if (entries.isEmpty()) {
            hdrP.setTextSize(W * .04f);
            c.drawText("No bets yet", W / 2f, H / 2f, hdrP);
            return;
        }

        float pad   = W * .04f;
        float rowH  = (H - pad * 2f) / (entries.size() + 1.5f);
        float ts    = rowH * .38f;
        float y     = pad + rowH;

        // Header
        hdrP.setTextSize(ts * .85f);
        c.drawText("─  BET HISTORY  ─", W / 2f, y - rowH * .2f, hdrP);

        // Win-rate bar
        float wr = (float) BetHistory.get().getWinRate() / 100f;
        float barY = y - rowH * .05f;
        float barW = W - pad * 2f;
        c.drawRoundRect(new RectF(pad, barY, pad + barW, barY + rowH * .18f), 4, 4, barBg);
        c.drawRoundRect(new RectF(pad, barY, pad + barW * wr * revealFrac, barY + rowH * .18f), 4, 4, barFg);
        Paint wrTxt = new Paint(winP);
        wrTxt.setTextSize(ts * .65f);
        wrTxt.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.format(Locale.US, "Win rate %.0f%%", BetHistory.get().getWinRate()),
                W - pad, barY - 2f, wrTxt);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);

        for (int i = entries.size() - 1; i >= 0; i--) {
            BetHistory.Entry e = entries.get(i);

            // Row bg alternate
            rowP.setColor((i % 2 == 0) ? 0xFF0C0E20 : 0xFF0F1128);
            c.drawRect(pad * .5f, y, W - pad * .5f, y + rowH * .82f, rowP);

            float revY = y + rowH * .82f * revealFrac;

            txtP.setTextSize(ts);
            // Game name (left)
            String name = e.game.length() > 10 ? e.game.substring(0, 10) + "…" : e.game;
            c.drawText(name, pad, revY, txtP);

            // Bet amount
            String betStr = String.format(Locale.US, "%.1f", e.bet);
            c.drawText(betStr, W * .42f, revY, txtP);

            // Net result (right, coloured)
            Paint np = e.isWin() ? winP : loseP;
            np.setTextSize(ts);
            np.setTextAlign(Paint.Align.RIGHT);
            String netStr = (e.net() >= 0 ? "+" : "") +
                    String.format(Locale.US, "%.1f", e.net());
            c.drawText(netStr, W - pad, revY, np);
            np.setTextAlign(Paint.Align.LEFT);

            // Time
            Paint tp2 = new Paint(txtP);
            tp2.setColor(0xFF555577); tp2.setTextSize(ts * .65f);
            tp2.setTextAlign(Paint.Align.RIGHT);
            c.drawText(sdf.format(new Date(e.time)), W - pad, revY + ts * .7f, tp2);

            y += rowH;
        }
    }
}
