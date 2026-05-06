package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animated bar chart for manager stats.
 * Call setData(rawText) with the text from the server.
 * Parses lines like:  "game1: +1000 FUN"  or  "player1: -50 FUN"
 */
public class StatsChartView extends View {

    public static class Bar { String label; double value; }

    private final List<Bar> bars = new ArrayList<>();
    private float animFrac = 0f;
    private ValueAnimator anim;

    private final Paint winP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint losP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lblP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeroP = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StatsChartView(Context c) { super(c); init(); }
    public StatsChartView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        bgP.setColor(0xFF08091A);
        axisP.setColor(0xFF1E2455); axisP.setStyle(Paint.Style.STROKE); axisP.setStrokeWidth(1.5f);
        zeroP.setColor(0xFF33D399); zeroP.setStyle(Paint.Style.STROKE); zeroP.setStrokeWidth(2f);
        lblP.setColor(0xFF9999BB); lblP.setTextAlign(Paint.Align.CENTER);
        lblP.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        valP.setTextAlign(Paint.Align.CENTER);
        valP.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
    }

    /** Parse raw server output and animate bars. */
    public void setData(String raw) {
        bars.clear();
        if (raw == null || raw.trim().isEmpty()) { invalidate(); return; }
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("Total")) continue;
            // expect format: "Label: +1234.56 FUN" or "Label: -50 FUN"
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String lbl = line.substring(0, colon).trim();
            String rest = line.substring(colon+1).trim();
            // extract number
            rest = rest.replace("FUN","").replace(",",".").trim();
            try {
                Bar b = new Bar();
                b.label = lbl.length() > 12 ? lbl.substring(0,12) : lbl;
                b.value = Double.parseDouble(rest);
                bars.add(b);
            } catch (NumberFormatException ignored) {}
        }
        if (anim != null) anim.cancel();
        animFrac = 0f;
        anim = ValueAnimator.ofFloat(0f,1f); anim.setDuration(900);
        anim.addUpdateListener(v -> { animFrac=(float)v.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDraw(Canvas c) {
        int W=getWidth(), H=getHeight();
        if (W==0||H==0) return;
        c.drawRoundRect(new RectF(0,0,W,H), 14,14, bgP);

        if (bars.isEmpty()) {
            lblP.setTextSize(W*.04f); c.drawText("No data", W/2f, H/2f, lblP); return;
        }

        float pad=W*.04f, chartT=H*.1f, chartB=H*.82f, chartH=chartB-chartT;
        float midY = chartT + chartH/2f;

        // axes
        c.drawLine(pad,chartT,pad,chartB,axisP);
        c.drawLine(pad,chartB,W-pad,chartB,axisP);
        c.drawLine(pad,midY,W-pad,midY,zeroP);  // zero line

        // find max abs value
        double maxAbs = 0.01;
        for (Bar b : bars) maxAbs = Math.max(maxAbs, Math.abs(b.value));

        float barW = (W - pad*2f) / bars.size() - 6f;
        float ts = Math.min(barW*.28f, 22f);
        lblP.setTextSize(ts); valP.setTextSize(ts*.9f);

        for (int i=0; i<bars.size(); i++) {
            Bar b = bars.get(i);
            float bh = (float)(Math.abs(b.value)/maxAbs) * chartH/2f * animFrac;
            float bx = pad + i*(barW+6f) + 3f;
            float by;
            RectF r;
            if (b.value >= 0) {
                by = midY - bh;
                winP.setShader(new LinearGradient(bx,by,bx,midY,
                        new int[]{0xFF56E0A8,0xFF34D399},null,Shader.TileMode.CLAMP));
                r = new RectF(bx,by,bx+barW,midY);
                c.drawRoundRect(r,6,6,winP);
                // value label above bar
                valP.setColor(0xFF34D399);
                c.drawText("+"+String.format(Locale.US,"%.0f",b.value), bx+barW/2f, by-4f, valP);
            } else {
                by = midY;
                losP.setShader(new LinearGradient(bx,by,bx,by+bh,
                        new int[]{0xFFF87171,0xFFCC3333},null,Shader.TileMode.CLAMP));
                r = new RectF(bx,by,bx+barW,by+bh);
                c.drawRoundRect(r,6,6,losP);
                valP.setColor(0xFFF87171);
                c.drawText(String.format(Locale.US,"%.0f",b.value), bx+barW/2f, by+bh+ts+2f, valP);
            }
            // name label
            lblP.setColor(0xFF9999BB);
            c.drawText(b.label, bx+barW/2f, chartB+ts+4f, lblP);
        }
    }
}
