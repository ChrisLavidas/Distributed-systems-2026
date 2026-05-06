package com.funGames.app.ui.games;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import com.funGames.app.util.SoundManager;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Casino-grade Lucky Wheel — 16 segments, authentic feel.
 *
 * Segments (like a real casino wheel):
 *   LOSE x6, 1.5x x3, 2x x2, 3x x2, 5x x1, 0.5x x1, JACKPOT x1
 *
 * Key fix: the VISUAL segment the pointer lands on determines the result label.
 * The server decides win/lose; we pick a matching segment to land on.
 */
public class LuckyWheelView extends View {

    /* ── 16-segment wheel ─────────────────────────────────────────── */
    private static final String[] LABELS = {
        "LOSE", "1.5x", "LOSE", "2x",
        "LOSE", "3x",   "LOSE", "1.5x",
        "JACKPOT","LOSE","2x",  "LOSE",
        "5x",   "LOSE", "3x",  "0.5x"
    };
    // multiplier for each segment (0 = lose, >1 = win, <1 but >0 = partial)
    private static final float[] MULTS = {
        0f,   1.5f, 0f,   2f,
        0f,   3f,   0f,   1.5f,
        20f,  0f,   2f,   0f,
        5f,   0f,   3f,   0.5f
    };

    // Colours: alternating dark/vibrant for visual clarity
    private static final int[] COLORS_INNER = {
        0xFF5C1A1A, 0xFF1A4A2A, 0xFF5C1A1A, 0xFF1A3A5C,
        0xFF5C1A1A, 0xFF3A1A5C, 0xFF5C1A1A, 0xFF1A4A2A,
        0xFF6B3A00, 0xFF5C1A1A, 0xFF1A3A5C, 0xFF5C1A1A,
        0xFF1A4A1A, 0xFF5C1A1A, 0xFF3A1A5C, 0xFF5C3A00
    };
    private static final int[] COLORS_EDGE = {
        0xFFF87171, 0xFF34D399, 0xFFF87171, 0xFF60A5FA,
        0xFFF87171, 0xFF8B5CF6, 0xFFF87171, 0xFF34D399,
        0xFFFFD84A, 0xFFF87171, 0xFF60A5FA, 0xFFF87171,
        0xFF4ADE80, 0xFFF87171, 0xFF8B5CF6, 0xFFFF8C00
    };

    // Segment groups by outcome type
    private static final int[] JACKPOT_SEG = {8};
    private static final int[] WIN_HIGH    = {12};          // 5x
    private static final int[] WIN_MED     = {5, 14};       // 3x
    private static final int[] WIN_LOW     = {1, 7, 3, 10}; // 1.5x, 2x
    private static final int[] PARTIAL_SEG = {15};          // 0.5x (partial return)
    private static final int[] LOSE_SEG    = {0, 2, 4, 6, 9, 11, 13}; // 7 lose segments

    private static final int N = LABELS.length;
    private static final float SWEEP = 360f / N;

    /* ── paints ───────────────────────────────────────────────────── */
    private final Paint rimOutP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimInP    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sepP      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centreP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centreRP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textP     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinP      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinShadP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starP     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resultP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowRingP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hlP       = new Paint(Paint.ANTI_ALIAS_FLAG); // segment highlight

    /* ── state ────────────────────────────────────────────────────── */
    private float angle = 0f;
    private int   landedSeg = -1; // which segment the pointer is on
    private ValueAnimator spinAnim, resultAnim, glowAnim, particleAnim;
    private float glowAlpha = 0f;

    /* ── particles ────────────────────────────────────────────────── */
    private static final int PARTICLES = 24;
    private final float[] pAngle  = new float[PARTICLES];
    private final float[] pRadius = new float[PARTICLES];
    private final int[]   pColor  = new int[PARTICLES];
    private float particleProgress = 0f;
    private boolean showParticles = false;

    /* ── result overlay ───────────────────────────────────────────── */
    private String resText  = "";
    private int    resColor = 0xFFFFFFFF;
    private float  resAlpha = 0f;

    public LuckyWheelView(Context ctx) {
        super(ctx);
        rimOutP.setStyle(Paint.Style.STROKE); rimOutP.setColor(0xFFD4AF37); rimOutP.setStrokeWidth(16f);
        rimOutP.setShadowLayer(24f, 0, 0, 0xAAD4AF37);
        rimInP.setStyle(Paint.Style.STROKE);  rimInP.setStrokeWidth(5f);
        sepP.setStyle(Paint.Style.STROKE);    sepP.setColor(0x99000000); sepP.setStrokeWidth(1.5f);
        centreP.setShadowLayer(16f, 0, 0, 0x99FFD84A);
        centreRP.setStyle(Paint.Style.STROKE); centreRP.setStrokeWidth(3f); centreRP.setColor(0xFFD4AF37);
        textP.setColor(Color.WHITE); textP.setTextAlign(Paint.Align.CENTER);
        textP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textP.setShadowLayer(4f, 1, 1, 0x88000000);
        pinP.setColor(0xFFFFD84A); pinP.setShadowLayer(14f, 0, 4, 0xAA000000);
        pinShadP.setColor(0x44000000);
        starP.setColor(0xFFD4AF37); starP.setTextAlign(Paint.Align.CENTER);
        starP.setTypeface(Typeface.DEFAULT_BOLD);
        resultP.setTextAlign(Paint.Align.CENTER);
        resultP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        resultP.setShadowLayer(22f, 0, 0, 0xFF000000);
        glowRingP.setStyle(Paint.Style.STROKE); glowRingP.setStrokeWidth(14f);
        hlP.setStyle(Paint.Style.FILL);

        int[] pcols = {0xFFFFD84A, 0xFF34D399, 0xFFFF8C00, 0xFF8B5CF6, 0xFFFFFFFF, 0xFFF87171, 0xFF60A5FA, 0xFF4ADE80};
        for (int i = 0; i < PARTICLES; i++) {
            pAngle[i]  = (float)(Math.random() * 360);
            pRadius[i] = .12f + (float)(Math.random() * .55f);
            pColor[i]  = pcols[i % pcols.length];
        }
    }

    /* ── drawing ──────────────────────────────────────────────────── */
    @Override
    protected void onDraw(Canvas canvas) {
        int W = getWidth(), H = getHeight();
        float cx = W / 2f, cy = H / 2f;
        float r  = Math.min(cx, cy) * .84f;
        canvas.drawColor(0xFF05060F);

        drawStarDecorations(canvas, cx, cy, r);
        drawWheel(canvas, cx, cy, r);
        drawLandedHighlight(canvas, cx, cy, r);
        drawGlowRing(canvas, cx, cy, r);
        drawPin(canvas, cx, cy, r);
        drawParticles(canvas, cx, cy, r);
        drawResultOverlay(canvas, W, H, cy);
    }

    private void drawStarDecorations(Canvas c, float cx, float cy, float r) {
        starP.setTextSize(r * .07f);
        float starR = r * 1.13f;
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 + angle * .08f);
            float sx = (float)(cx + Math.cos(a) * starR);
            float sy = (float)(cy + Math.sin(a) * starR) + starP.getTextSize() * .35f;
            starP.setAlpha(i % 2 == 0 ? 180 : 80);
            c.drawText(i % 2 == 0 ? "★" : "◆", sx, sy, starP);
        }
    }

    private void drawWheel(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r + rimOutP.getStrokeWidth() / 2f, rimOutP);

        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        c.save();
        c.rotate(angle, cx, cy);

        for (int i = 0; i < N; i++) {
            float start = i * SWEEP - 90f;

            // Radial gradient fill
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setShader(new RadialGradient(cx, cy, r,
                new int[]{COLORS_INNER[i], COLORS_EDGE[i]},
                new float[]{.3f, 1f}, Shader.TileMode.CLAMP));
            c.drawArc(oval, start, SWEEP, true, sp);
            c.drawArc(oval, start, SWEEP, true, sepP);
        }

        // Inner gloss ring
        rimInP.setColor(0x22FFFFFF);
        c.drawCircle(cx, cy, r * .72f, rimInP);
        rimInP.setColor(0x11FFFFFF);
        c.drawCircle(cx, cy, r * .55f, rimInP);

        // Labels
        for (int i = 0; i < N; i++) {
            float start = i * SWEEP - 90f;
            float mid   = (float)Math.toRadians(start + SWEEP / 2f);
            float tx    = (float)(cx + Math.cos(mid) * r * .70f);
            float ty    = (float)(cy + Math.sin(mid) * r * .70f);

            // Smaller text for 16 segments
            textP.setTextSize(r * .095f);
            // Lose = red tint, win = white
            boolean isLose = MULTS[i] == 0f;
            boolean isJackpot = MULTS[i] > 10f;
            textP.setColor(isJackpot ? 0xFFFFD84A : isLose ? 0xFFFF8080 : 0xFFFFFFFF);
            c.drawText(LABELS[i], tx, ty, textP);
        }

        // Centre hub with gradient
        Paint hubP = new Paint(Paint.ANTI_ALIAS_FLAG);
        hubP.setShader(new RadialGradient(cx, cy, r * .15f,
            new int[]{0xFFFFE066, 0xFFB07A0E}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * .15f, hubP);
        c.drawCircle(cx, cy, r * .15f, centreRP);
        centreP.setColor(0xFF050810);
        c.drawCircle(cx, cy, r * .07f, centreP);

        c.restore();
    }

    private void drawLandedHighlight(Canvas c, float cx, float cy, float r) {
        if (landedSeg < 0 || resAlpha <= 0f) return;
        // Highlight drawn in screen coords (outside rotate block), so add angle to segment position
        float startAngle = angle + landedSeg * SWEEP - 90f;
        RectF oval = new RectF(cx - r * .96f, cy - r * .96f, cx + r * .96f, cy + r * .96f);
        hlP.setColor(COLORS_EDGE[landedSeg]);
        hlP.setAlpha((int)(resAlpha * 90));
        hlP.setShadowLayer(24f, 0, 0, COLORS_EDGE[landedSeg]);
        c.drawArc(oval, startAngle, SWEEP, true, hlP);
    }

    private void drawGlowRing(Canvas c, float cx, float cy, float r) {
        if (glowAlpha <= 0f) return;
        glowRingP.setColor(landedSeg >= 0 && MULTS[landedSeg] > 0 ? 0xFFFFD84A : 0xFFF87171);
        glowRingP.setAlpha((int)(glowAlpha * 220));
        glowRingP.setShadowLayer(32f, 0, 0, glowRingP.getColor());
        c.drawCircle(cx, cy, r + rimOutP.getStrokeWidth() / 2f + 5f, glowRingP);
    }

    private void drawPin(Canvas c, float cx, float cy, float r) {
        float tipY = cy - r - rimOutP.getStrokeWidth() / 2f + 6f;
        float s    = r * .075f;
        // Shadow
        Path sh = new Path();
        sh.moveTo(cx + 2, tipY + 3); sh.lineTo(cx - s + 2, tipY - s * 2.8f + 3); sh.lineTo(cx + s + 2, tipY - s * 2.8f + 3);
        sh.close(); c.drawPath(sh, pinShadP);
        // Pin
        Path p = new Path();
        p.moveTo(cx, tipY); p.lineTo(cx - s, tipY - s * 2.8f); p.lineTo(cx + s, tipY - s * 2.8f);
        p.close(); c.drawPath(p, pinP);
        // Shine dot
        Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        shine.setColor(0xAAFFFFFF);
        c.drawCircle(cx - s * .25f, tipY - s * 1.4f, s * .25f, shine);
    }

    private void drawParticles(Canvas c, float cx, float cy, float r) {
        if (!showParticles || particleProgress <= 0f) return;
        for (int i = 0; i < PARTICLES; i++) {
            float pr = r * (.35f + pRadius[i]) * particleProgress;
            float px = (float)(cx + Math.cos(Math.toRadians(pAngle[i])) * pr);
            float py = (float)(cy + Math.sin(Math.toRadians(pAngle[i])) * pr);
            float ps = r * .04f * (1f - particleProgress * .5f);
            Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            pp.setColor(pColor[i]);
            pp.setAlpha((int)((1f - particleProgress) * 230));
            c.drawCircle(px, py, ps, pp);
        }
    }

    private void drawResultOverlay(Canvas c, int W, int H, float cy) {
        if (resAlpha <= 0f || resText.isEmpty()) return;
        resultP.setColor(resColor);
        resultP.setAlpha((int)(resAlpha * 255));
        resultP.setTextSize(H * .062f);
        // Draw with background pill for readability
        float tw = resultP.measureText(resText);
        float tx = W / 2f;
        float ty = cy * .25f;
        Paint bgP = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgP.setColor(0xCC050810);
        bgP.setAlpha((int)(resAlpha * 200));
        float pad = H * .018f;
        c.drawRoundRect(new RectF(tx - tw/2 - pad, ty - H*.05f, tx + tw/2 + pad, ty + pad), 16, 16, bgP);
        c.drawText(resText, tx, ty, resultP);
    }

    /* ── spin API ─────────────────────────────────────────────────── */
    public void startSpin() {
        landedSeg = -1;
        showParticles = false; resText = ""; resAlpha = 0f; glowAlpha = 0f;
        if (spinAnim    != null) spinAnim.cancel();
        if (resultAnim  != null) resultAnim.cancel();
        if (glowAnim    != null) glowAnim.cancel();
        if (particleAnim!= null) particleAnim.cancel();

        spinAnim = ValueAnimator.ofFloat(angle, angle + 3600f);
        spinAnim.setDuration(4500);
        spinAnim.setRepeatCount(ValueAnimator.INFINITE);
        spinAnim.setRepeatMode(ValueAnimator.RESTART);
        spinAnim.addUpdateListener(v -> {
            float newAngle = (float)v.getAnimatedValue() % 360f;
            if ((int)(newAngle / SWEEP) != (int)(angle / SWEEP)) {
                SoundManager.get().playWheelTick();
            }
            angle = newAngle;
            invalidate();
        });
        spinAnim.start();
    }

    /**
     * Stop the wheel.
     * The server's win/lose result picks which SEGMENT group to land on.
     * The multiplier is then READ FROM THE VISUAL SEGMENT — 
     * so the result displayed always matches what the player sees.
     *
     * The onDone callback receives the actual multiplier via the segment.
     * But since GamePlayActivity already has the server result, we just
     * make the visual consistent with the server result.
     */
    public void stopSpin(boolean win, boolean jackpot, Runnable onDone) {
        if (spinAnim != null) spinAnim.cancel();

        // Pick target segment based on server result
        int targetSeg;
        if (jackpot) {
            targetSeg = JACKPOT_SEG[0];
        } else if (win) {
            // Pick from win segments weighted by excitement
            // 50% chance high win (3x/5x), 50% lower win (1.5x/2x)
            double r = Math.random();
            if (r < 0.15)      targetSeg = WIN_HIGH[(int)(Math.random() * WIN_HIGH.length)];
            else if (r < 0.45) targetSeg = WIN_MED[(int)(Math.random() * WIN_MED.length)];
            else               targetSeg = WIN_LOW[(int)(Math.random() * WIN_LOW.length)];
        } else {
            // Partial return (0.5x) or full lose
            if (Math.random() < 0.12) {
                targetSeg = PARTIAL_SEG[0]; // 12% of losses show 0.5x (partial)
            } else {
                targetSeg = LOSE_SEG[(int)(Math.random() * LOSE_SEG.length)];
            }
        }

        landedSeg = targetSeg;

        // Calculate the angle needed to land this segment under the pin (top).
        // The pin is fixed at screen top. For segment i to appear there,
        // the wheel angle must be: (-i*SWEEP - SWEEP/2) % 360
        float targetAngle = ((-targetSeg * SWEEP - SWEEP / 2f) % 360f + 360f) % 360f;
        float extra       = 5 * 360f;
        float needed      = (targetAngle - angle % 360f + 360f) % 360f;
        float finalAngle  = angle + extra + needed;

        spinAnim = ValueAnimator.ofFloat(angle, finalAngle);
        spinAnim.setDuration(4000);
        spinAnim.setInterpolator(new DecelerateInterpolator(3f));
        spinAnim.addUpdateListener(v -> {
            float newAngle = (float)v.getAnimatedValue() % 360f;
            if ((int)(newAngle / SWEEP) != (int)(angle / SWEEP)) {
                SoundManager.get().playWheelTick();
            }
            angle = newAngle;
            invalidate();
        });
        spinAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                afterLand(win, jackpot, onDone);
            }
        });
        spinAnim.start();
    }

    private void afterLand(boolean win, boolean jackpot, Runnable onDone) {
        glowAnim = ValueAnimator.ofFloat(0f, 1f, 0.6f, 1f, 0f);
        glowAnim.setDuration(jackpot ? 2000 : 1400);
        if (jackpot || win) glowAnim.setRepeatCount(jackpot ? ValueAnimator.INFINITE : 3);
        glowAnim.addUpdateListener(v -> { glowAlpha = (float)v.getAnimatedValue(); invalidate(); });
        glowAnim.start();

        if (jackpot) {
            resText = "🎰  JACKPOT!  🎰"; resColor = 0xFFFFD84A;
            showParticles = true; fireParticles();
        } else if (win) {
            // Show the actual multiplier of the landed segment
            String mult = LABELS[landedSeg];
            resText = "✨  " + mult + "  YOU WIN!"; resColor = 0xFF34D399;
            showParticles = true; fireParticles();
        } else if (landedSeg == PARTIAL_SEG[0]) {
            resText = "💸  0.5x  Partial Return"; resColor = 0xFFFF8C00;
        } else {
            resText = "❌  LOSE  Better luck!"; resColor = 0xFFF87171;
        }

        resultAnim = ValueAnimator.ofFloat(0f, 1f);
        resultAnim.setDuration(900);
        resultAnim.addUpdateListener(v -> { resAlpha = (float)v.getAnimatedValue(); invalidate(); });
        resultAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (onDone != null) onDone.run();
            }
        });
        resultAnim.start();
    }

    private void fireParticles() {
        if (particleAnim != null) particleAnim.cancel();
        particleAnim = ValueAnimator.ofFloat(0f, 1f);
        particleAnim.setDuration(1800);
        particleAnim.setRepeatCount(ValueAnimator.INFINITE);
        particleAnim.addUpdateListener(v -> { particleProgress = (float)v.getAnimatedValue(); invalidate(); });
        particleAnim.start();
    }

    public void reset() {
        if (spinAnim    != null) { spinAnim.cancel();     spinAnim = null; }
        if (resultAnim  != null) { resultAnim.cancel();   resultAnim = null; }
        if (glowAnim    != null) { glowAnim.cancel();     glowAnim = null; }
        if (particleAnim!= null) { particleAnim.cancel(); particleAnim = null; }
        angle = 0f; landedSeg = -1;
        resText = ""; resAlpha = 0f; glowAlpha = 0f;
        showParticles = false; particleProgress = 0f;
        invalidate();
    }
}
