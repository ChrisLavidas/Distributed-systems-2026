package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/**
 * Full-screen animated casino background.
 *
 * Layers (back to front):
 * 1. Deep radial vignette  — rich midnight-black-to-deep-navy
 * 2. Animated bokeh orbs   — 16 soft glowing circles drifting slowly
 * 3. Diagonal gold-thread grid — like casino felt texture
 * 4. Floating card suits   — ♠♥♦♣ slowly rotating, very faint
 * 5. Ambient glow pulse    — slow gold halo at screen centre
 * 6. Chip scatter          — 8 semi-transparent chips orbiting edges
 *
 * All animation is driven by a single ValueAnimator (0→1 repeating).
 */
public class CasinoBackgroundView extends View {

    /* ── bokeh ───────────────────────────────────────────────────── */
    private static final int BOKEH = 16;
    protected final float[] bx  = new float[BOKEH];
    protected final float[] by  = new float[BOKEH];
    protected final float[] br  = new float[BOKEH];    // radius factor 0-1
    protected final float[] bsp = new float[BOKEH];    // speed
    protected final float[] bph = new float[BOKEH];    // phase
    private final int[]   bcol= new int[BOKEH];

    /* ── suits ───────────────────────────────────────────────────── */
    private static final String[] SUITS = {"♠","♥","♦","♣","★"};
    private static final int  SUIT_COUNT = 12;
    protected final float[] sx  = new float[SUIT_COUNT];
    protected final float[] sy  = new float[SUIT_COUNT];
    protected final float[] ssc = new float[SUIT_COUNT]; // scale
    protected final float[] srt = new float[SUIT_COUNT]; // rotation base
    protected final float[] ssp = new float[SUIT_COUNT]; // rot speed
    protected final int[]   sidx= new int[SUIT_COUNT];

    /* ── chips ───────────────────────────────────────────────────── */
    private static final int CHIPS = 10;
    protected final float[] chipAngle = new float[CHIPS];
    protected final float[] chipR     = new float[CHIPS]; // orbit radius factor
    protected final float[] chipSp    = new float[CHIPS]; // orbit speed
    private final int[]   chipColor = new int[CHIPS];

    /* ── paints ──────────────────────────────────────────────────── */
    private final Paint vigP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suitP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipRP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowP  = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected float tick = 0f;   // 0..1 repeating
    private ValueAnimator anim;

    public CasinoBackgroundView(Context ctx) { super(ctx); init(); }
    public CasinoBackgroundView(Context ctx, AttributeSet a) { super(ctx,a); init(); }

    private void init() {
        int[] bokehColors = {
                0x18FFD84A, 0x14D4AF37, 0x12B07A0E,
                0x108B5CF6, 0x0C6D28D9, 0x0EFF8C00,
                0x0834D399, 0x06F87171, 0x14FFD84A,
                0x108B5CF6, 0x12D4AF37, 0x0CFF8C00,
                0x0C34D399, 0x08FFD84A, 0x14B07A0E, 0x0CF87171
        };
        for (int i = 0; i < BOKEH; i++) {
            bx[i]  = (float) Math.random();
            by[i]  = (float) Math.random();
            br[i]  = .04f + (float)(Math.random() * .12f);
            bsp[i] = .08f + (float)(Math.random() * .14f);
            bph[i] = (float)(Math.random() * Math.PI * 2);
            bcol[i]= bokehColors[i % bokehColors.length];
        }

        int[] suitColors = {0x12D4AF37, 0x10FFD84A, 0x0EB07A0E,
                            0x0C8B5CF6, 0x0AFFD84A, 0x08D4AF37};
        for (int i = 0; i < SUIT_COUNT; i++) {
            sx[i]  = .05f + (float)(Math.random() * .9f);
            sy[i]  = .05f + (float)(Math.random() * .9f);
            ssc[i] = .6f  + (float)(Math.random() * 1.4f);
            srt[i] = (float)(Math.random() * 360f);
            ssp[i] = (float)(Math.random() > .5 ? 1 : -1) * (.3f + (float)(Math.random() * .7f));
            sidx[i]= i % SUITS.length;
        }

        int[] chipCols = {0xFFD4AF37,0xFFB07A0E,0xFF8B5CF6,0xFF34D399,
                          0xFFF87171,0xFFFFD84A,0xFF6D28D9,0xFF059669,
                          0xFFFF8C00,0xFF22D3EE};
        for (int i = 0; i < CHIPS; i++) {
            chipAngle[i] = (float)(Math.random() * 360);
            chipR[i]     = .35f + (float)(Math.random() * .45f);
            chipSp[i]    = (float)(Math.random() > .5 ? 1 : -1) * (.5f + (float)(Math.random() * 1f));
            chipColor[i] = chipCols[i % chipCols.length];
        }

        gridP.setStyle(Paint.Style.STROKE);
        gridP.setStrokeWidth(1f);

        suitP.setTextAlign(Paint.Align.CENTER);
        suitP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        chipP.setStyle(Paint.Style.FILL);
        chipRP.setStyle(Paint.Style.STROKE);
        chipRP.setStrokeWidth(2f);

        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(12000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.RESTART);
        anim.addUpdateListener(v -> { tick = (float) v.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int W = getWidth(), H = getHeight();
        if (W == 0 || H == 0) return;

        // ── 1. base gradient ─────────────────────────────────────────
        Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setShader(new RadialGradient(W/2f, H*.35f, Math.max(W,H)*.8f,
                new int[]{0xFF0E1138, 0xFF07091A, 0xFF030408},
                new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,W,H,basePaint);

        // ── 2. bokeh ─────────────────────────────────────────────────
        for (int i = 0; i < BOKEH; i++) {
            float phase = (float)(bph[i] + tick * bsp[i] * Math.PI * 2);
            float cx = (bx[i] + (float)(Math.sin(phase)*.06f)) * W;
            float cy = (by[i] + (float)(Math.cos(phase*.7f)*.05f)) * H;
            float r  = br[i] * Math.min(W,H);
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setShader(new RadialGradient(cx, cy, r, bcol[i], 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, bp);
        }

        // ── 3. diagonal grid (felt texture) ──────────────────────────
        int gridStep = Math.max(W,H) / 22;
        gridP.setColor(0x08D4AF37);
        for (int i = -H; i < W+H; i += gridStep) {
            canvas.drawLine(i, 0, i + H, H, gridP);
            canvas.drawLine(i, H, i + H, 0, gridP);
        }
        // second grid at 90°, lighter
        gridP.setColor(0x05D4AF37);
        for (int x = 0; x < W; x += gridStep) canvas.drawLine(x, 0, x, H, gridP);
        for (int y = 0; y < H; y += gridStep) canvas.drawLine(0, y, W, y, gridP);

        // ── 4. floating suits ─────────────────────────────────────────
        for (int i = 0; i < SUIT_COUNT; i++) {
            float rot = srt[i] + tick * ssp[i] * 360f;
            float cx  = sx[i] * W;
            float cy  = sy[i] * H;
            float size= ssc[i] * Math.min(W,H) * .055f;
            suitP.setTextSize(size);
            suitP.setColor(0x11D4AF37);
            canvas.save();
            canvas.rotate(rot, cx, cy);
            canvas.drawText(SUITS[sidx[i]], cx, cy + size*.35f, suitP);
            canvas.restore();
        }

        // ── 5. centre glow pulse ──────────────────────────────────────
        float pulse = .5f + .5f * (float)Math.sin(tick * Math.PI * 2 * .8f);
        Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        gp.setShader(new RadialGradient(W/2f, H/2f, Math.min(W,H)*.55f,
                new int[]{Color.argb((int)(14*pulse), 0xFF, 0xD8, 0x4A), 0x00000000},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, H, gp);

        // ── 6. orbiting chips ────────────────────────────────────────
        float orbitCX = W/2f, orbitCY = H/2f;
        for (int i = 0; i < CHIPS; i++) {
            float a  = (float)Math.toRadians(chipAngle[i] + tick * chipSp[i] * 360f);
            float r  = chipR[i] * Math.min(W,H) * .44f;
            float cx = orbitCX + (float)(Math.cos(a)*r);
            float cy = orbitCY + (float)(Math.sin(a)*r);
            float cr = Math.min(W,H) * .025f;

            // chip body
            chipP.setColor(chipColor[i] & 0x00FFFFFF | 0x18000000);
            canvas.drawCircle(cx, cy, cr, chipP);

            // chip rings
            chipRP.setColor(chipColor[i] & 0x00FFFFFF | 0x22000000);
            canvas.drawCircle(cx, cy, cr, chipRP);
            chipRP.setColor(chipColor[i] & 0x00FFFFFF | 0x15000000);
            canvas.drawCircle(cx, cy, cr*.7f, chipRP);
        }

        // ── 7. top vignette ───────────────────────────────────────────
        Paint topFade = new Paint();
        topFade.setShader(new LinearGradient(0, 0, 0, H*.25f,
                0x55000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, H*.25f, topFade);

        Paint botFade = new Paint();
        botFade.setShader(new LinearGradient(0, H*.75f, 0, H,
                0x00000000, 0x44000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, H*.75f, W, H, botFade);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (anim != null && !anim.isRunning()) anim.start();
    }
}
