package com.funGames.app.ui.games;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import com.funGames.app.util.SoundManager;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.Locale;

/**
 * PRO 3-reel slot machine.
 * • Symbols scroll and STOP with their centre exactly on the gold payline.
 * • Rich paytable drawn below the reels.
 * • Win / lose overlay with animated glow.
 */
public class SlotMachineView extends View {

    /* ── symbol strip (index 0 = jackpot) ───────────────────────── */
    private static final String[] SYM      = {"🎰","💎","7️⃣","🔔","⭐","🍇","🍋","🍒"};
    private static final String[] SYM_NAME = {"Jackpot","Diamond","Seven","Bell","Star","Grapes","Lemon","Cherry"};
    private static final String[] SYM_PAY  = {"JACKPOT","10x","5x","3x","2x","1.5x","1x","0.5x"};
    private static final int S_JACKPOT=0, S_DIAMOND=1, S_SEVEN=2, S_BELL=3,
                              S_STAR=4, S_GRAPES=5, S_LEMON=6, S_CHERRY=7;
    private static final int N = SYM.length;        // 8
    private static final int REEL = 3, VISIBLE = 3;

    /* ── paints ──────────────────────────────────────────────────── */
    private final Paint bgP     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reelP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symP    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hlP     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimP    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titleP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint payBgP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint payHdrP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paySymP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint payLblP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint payMulP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resultP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayP= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topFadeP= new Paint();
    private final Paint botFadeP= new Paint();

    /* ── animation state ─────────────────────────────────────────── */
    // offset[r] = exact symbol index at the TOP of the reel (float for sub-symbol scroll)
    private final float[] offset = new float[REEL];
    private final ValueAnimator[] anims = new ValueAnimator[REEL];
    private boolean spinning = false;

    /* ── result overlay ──────────────────────────────────────────── */
    private String  resText  = "";
    private int     resColor = 0xFFFFFFFF;
    private float   resAlpha = 0f;
    private float   shimmerX = -1f;
    private ValueAnimator resAnim, shimAnim, glowAnim;
    private float glowR = 0f;
    private boolean glowing = false;

    /* ── landed symbols for glow ─────────────────────────────────── */
    private final int[] landed = {S_LEMON, S_LEMON, S_LEMON};

    public SlotMachineView(Context c) {
        super(c);
        rimP.setStyle(Paint.Style.STROKE); rimP.setColor(0xFFD4AF37); rimP.setStrokeWidth(3.5f);
        lineP.setStyle(Paint.Style.STROKE); lineP.setStrokeWidth(3f);
        arrowP.setColor(0xFFFFD84A);
        titleP.setTextAlign(Paint.Align.CENTER);
        titleP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titleP.setColor(0xFFFFD84A);
        titleP.setShadowLayer(12f,0,0,0xAAFFD84A);
        payBgP.setColor(0xFF080A18);
        payHdrP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        payHdrP.setColor(0xFFFFD84A); payHdrP.setTextAlign(Paint.Align.CENTER);
        paySymP.setTextAlign(Paint.Align.CENTER);
        payLblP.setColor(0xFF9999BB); payLblP.setTextAlign(Paint.Align.LEFT);
        payLblP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        payMulP.setColor(0xFF34D399); payMulP.setTextAlign(Paint.Align.RIGHT);
        payMulP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        resultP.setTextAlign(Paint.Align.CENTER);
        resultP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        resultP.setShadowLayer(18f,0,0,0xFF000000);
        hlP.setStyle(Paint.Style.FILL);
        shimP.setStyle(Paint.Style.FILL);
        overlayP.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int W = getWidth(), H = getHeight();
        canvas.drawColor(0xFF05060F);

        float tableH  = H * 0.30f;           // paytable height at bottom
        float reelAreaH = H - tableH;

        drawMachine(canvas, W, reelAreaH);
        drawReels(canvas, W, reelAreaH);
        drawPaytable(canvas, W, H, reelAreaH, tableH);
        drawResult(canvas, W, reelAreaH);
    }

    /* ── machine frame ───────────────────────────────────────────── */
    private void drawMachine(Canvas c, int W, float aH) {
        // outer chrome frame
        RectF frame = new RectF(W*.025f, aH*.02f, W*.975f, aH*.97f);
        Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient frameGrad = new LinearGradient(0,0,0,aH,
                new int[]{0xFF2A2050,0xFF0D0F22,0xFF2A2050}, null, Shader.TileMode.CLAMP);
        fp.setShader(frameGrad); c.drawRoundRect(frame,20,20,fp);
        rimP.setColor(0xFFD4AF37); rimP.setStrokeWidth(4f);
        c.drawRoundRect(frame,20,20,rimP);

        // top banner
        RectF banner = new RectF(W*.04f, aH*.025f, W*.96f, aH*.115f);
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setShader(new LinearGradient(0,0,W,0,
                new int[]{0xFF1A0050,0xFF3B1E6B,0xFF1A0050}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(banner,12,12,bp);
        rimP.setStrokeWidth(2f); c.drawRoundRect(banner,12,12,rimP);

        titleP.setTextSize(aH*.058f);
        c.drawText("✦  LUCKY SLOTS  ✦", W/2f, aH*.093f, titleP);
    }

    /* ── reel strip ──────────────────────────────────────────────── */
    private void drawReels(Canvas c, int W, float aH) {
        float margin = W * .045f;
        float reelW  = (W - margin*(REEL+1)) / REEL;
        float reelTop= aH * .125f;
        float reelH  = aH * .82f;
        float symH   = reelH / VISIBLE;        // height of one symbol cell

        symP.setTextSize(symH * .64f);
        symP.setTextAlign(Paint.Align.CENTER);

        for (int r = 0; r < REEL; r++) {
            float rx = margin + r*(reelW+margin);

            /* reel background */
            RectF rRect = new RectF(rx, reelTop, rx+reelW, reelTop+reelH);
            Paint rbP = new Paint(Paint.ANTI_ALIAS_FLAG);
            rbP.setShader(new LinearGradient(rx,reelTop,rx,reelTop+reelH,
                    new int[]{0xFF060918,0xFF0B0D22,0xFF060918}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(rRect,12,12,rbP);
            rimP.setColor(0xFF2A3060); rimP.setStrokeWidth(2f);
            c.drawRoundRect(rRect,12,12,rimP);

            /* clip so symbols don't bleed outside reel */
            c.save();
            c.clipRect(rx, reelTop, rx+reelW, reelTop+reelH);

            /*
             * offset[r] is the FRACTIONAL symbol index at the top of the reel.
             * The TOP of visible row-0 starts at:  reelTop - frac*symH
             * where frac = offset[r] % 1.
             * Symbol at visible row i = floor(offset[r]) + i  (mod N)
             */
            float frac    = offset[r] - (float)Math.floor(offset[r]);
            int   topIdx  = (int)Math.floor(offset[r]);
            float drawY   = reelTop - frac * symH;   // y of top edge of first visible symbol

            for (int row = -1; row <= VISIBLE; row++) {
                int sym = ((topIdx + row) % N + N) % N;
                float sy = drawY + row * symH;
                // subtle per-symbol cell bg for jackpot/diamond
                if (sym == S_JACKPOT) {
                    Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    gp.setColor(0x22FF8C00);
                    c.drawRect(rx, sy, rx+reelW, sy+symH, gp);
                } else if (sym == S_DIAMOND) {
                    Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    gp.setColor(0x1522D3EE);
                    c.drawRect(rx, sy, rx+reelW, sy+symH, gp);
                }
                c.drawText(SYM[sym], rx+reelW/2f, sy + symH*.76f, symP);
            }
            c.restore();

            /* top & bottom gradient fade — makes symbols "emerge" from darkness */
            int fadeH = (int)(symH * .55f);
            Paint fadeTop = new Paint();
            fadeTop.setShader(new LinearGradient(0, reelTop, 0, reelTop+fadeH,
                    0xFF060918, 0x00060918, Shader.TileMode.CLAMP));
            c.drawRect(rx, reelTop, rx+reelW, reelTop+fadeH, fadeTop);

            Paint fadeBot = new Paint();
            fadeBot.setShader(new LinearGradient(0, reelTop+reelH-fadeH, 0, reelTop+reelH,
                    0x00060918, 0xFF060918, Shader.TileMode.CLAMP));
            c.drawRect(rx, reelTop+reelH-fadeH, rx+reelW, reelTop+reelH, fadeBot);

            /* gold payline highlight strip (centre row) */
            float hlY = reelTop + symH;   // top of centre row
            hlP.setColor(0x1AFFD700);
            c.drawRect(rx, hlY, rx+reelW, hlY+symH, hlP);

            /* glow on winning reel */
            if (glowing && !spinning) {
                Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
                glow.setStyle(Paint.Style.STROKE);
                glow.setStrokeWidth(6f);
                glow.setColor(0xFFFFD84A);
                glow.setAlpha((int)(Math.abs(Math.sin(glowR)) * 200));
                c.drawRoundRect(rRect, 12, 12, glow);
            }
        }

        /* payline markers left & right */
        float lineY = reelTop + symH * 1.5f;   // centre of middle row
        lineP.setColor(0xFFFFD84A);
        float lx1 = margin*.2f, lx2 = W - margin*.2f;
        c.drawLine(lx1, lineY, margin*.85f, lineY, lineP);
        c.drawLine(W-margin*.85f, lineY, lx2, lineY, lineP);
        // arrows
        drawArrow(c, margin*.55f, lineY, margin*.22f, true);
        drawArrow(c, W-margin*.55f, lineY, margin*.22f, false);
    }

    private void drawArrow(Canvas c, float x, float cy, float s, boolean right) {
        Path p = new Path();
        if (right) { p.moveTo(x,cy); p.lineTo(x-s,cy-s*.6f); p.lineTo(x-s,cy+s*.6f); }
        else       { p.moveTo(x,cy); p.lineTo(x+s,cy-s*.6f); p.lineTo(x+s,cy+s*.6f); }
        p.close(); c.drawPath(p, arrowP);
    }

    /* ── paytable ────────────────────────────────────────────────── */
    private void drawPaytable(Canvas c, int W, int H, float top, float tableH) {
        float pad = W*.03f;
        RectF bg = new RectF(pad, top+pad*.3f, W-pad, H-pad*.3f);
        c.drawRoundRect(bg,14,14,payBgP);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(1.5f); rim.setColor(0xFF1E2450);
        c.drawRoundRect(bg,14,14,rim);

        float ts    = tableH * .12f;
        float rowH  = tableH * .115f;
        float colSym= W*.08f, colLbl=W*.22f, colMul=W*.93f;
        float y     = top + pad*.5f + ts;

        payHdrP.setTextSize(ts*.85f);
        c.drawText("─  PAYTABLE  ─", W/2f, y, payHdrP);
        y += rowH*.9f;

        paySymP.setTextSize(ts*.9f);
        payLblP.setTextSize(ts*.78f);
        payMulP.setTextSize(ts*.78f);

        for (int i = 0; i < SYM.length; i++) {
            if (y > H - pad) break;
            // highlight jackpot row
            if (i == S_JACKPOT) {
                Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
                rp.setColor(0x22FF8C00);
                c.drawRect(pad, y-ts*.85f, W-pad, y+ts*.25f, rp);
                payMulP.setColor(0xFFFF8C00);
            } else {
                payMulP.setColor(0xFF34D399);
            }
            c.drawText(SYM[i],  colSym, y, paySymP);
            c.drawText(SYM_NAME[i], colLbl, y, payLblP);
            c.drawText(SYM_PAY[i],  colMul, y, payMulP);
            y += rowH;
        }
    }

    /* ── win/lose overlay ────────────────────────────────────────── */
    private void drawResult(Canvas c, int W, float aH) {
        if (resAlpha <= 0f || resText.isEmpty()) return;
        // shimmer bar
        if (shimmerX >= 0) {
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setShader(new LinearGradient(shimmerX-W*.15f, 0, shimmerX+W*.15f, 0,
                    new int[]{0x00FFFFFF,0x55FFFFFF,0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, aH*.01f, W, aH*.95f, sh);
        }
        resultP.setColor(resColor);
        resultP.setAlpha((int)(resAlpha*255));
        resultP.setTextSize(aH*.072f);
        c.drawText(resText, W/2f, aH*.095f, resultP);
    }

    /* ── spin API ────────────────────────────────────────────────── */
    public void startSpin() {
        spinning = true; glowing = false; resText = ""; resAlpha = 0f;
        if (resAnim  != null) resAnim.cancel();
        if (shimAnim != null) shimAnim.cancel();
        if (glowAnim != null) glowAnim.cancel();

        for (int r = 0; r < REEL; r++) {
            final int ri = r;
            if (anims[ri] != null) anims[ri].cancel();
            // spin: advance by many full symbol-lengths
            float target = offset[ri] + N * 30f;
            ValueAnimator a = ValueAnimator.ofFloat(offset[ri], target);
            a.setDuration(4200 + r*380L);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.RESTART);
            a.addUpdateListener(v -> { offset[ri] = (float)v.getAnimatedValue(); invalidate(); });
            anims[ri] = a; a.start();
        }
    }

    public void stopSpin(boolean win, boolean jackpot, double result, double bet, Runnable onDone) {
        int[] targets = pickSymbols(win, jackpot, result, bet);

        for (int r = 0; r < REEL; r++) {
            final int ri = r;
            if (anims[ri] != null) anims[ri].cancel();
            landed[ri] = targets[ri];

            /*
             * We want offset[ri] to land so that targets[ri] is centred on
             * the MIDDLE visible row (row index 1 out of 0,1,2).
             *
             * When offset = k (integer), symbol k appears at row 0.
             * So symbol k+1 appears at row 1 (centre).
             * Therefore we want:  floor(offset_final) + 1 ≡ targets[ri]  (mod N)
             *   → offset_final integer part = targets[ri] - 1
             * We add enough full N-cycles so we overshoot current offset.
             */
            float wantInt   = targets[ri] - 1;   // integer part of final offset
            float curFloor  = (float)Math.floor(offset[ri]);
            float extra     = (float)Math.ceil((curFloor - wantInt) / N) * N
                              + N * (4 + ri);     // extra N-cycles for slow-down drama
            float finalOff  = wantInt + extra;    // still fractional = 0

            ValueAnimator a = ValueAnimator.ofFloat(offset[ri], finalOff);
            a.setDuration(2800 + ri*600L);
            a.setInterpolator(new DecelerateInterpolator(3.0f));
            a.setStartDelay(ri * 380L);
            a.addUpdateListener(v -> { offset[ri] = (float)v.getAnimatedValue(); invalidate(); });
            if (ri == REEL-1) {
                final int reelIdx = ri;
                a.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator anim) {
                        SoundManager.get().playSlotReelStop(reelIdx);
                        spinning = false;
                        startResultAnim(win, jackpot, result, bet);
                        if (onDone != null) onDone.run();
                    }
                });
            }
            anims[ri] = a; a.start();
        }
    }

    public void stopSpin(boolean win, boolean jackpot, Runnable onDone) {
        stopSpin(win, jackpot, 0, 0, onDone);
    }

    private int[] pickSymbols(boolean win, boolean jackpot, double result, double bet) {
        int[] t = new int[REEL];
        if (jackpot) { for (int i=0;i<REEL;i++) t[i]=S_JACKPOT; return t; }
        if (win) {
            double m = bet>0 ? result/bet : 1.0;
            int s = m>=9?S_DIAMOND : m>=4.5?S_SEVEN : m>=2.5?S_BELL : m>=1.8?S_STAR : m>=1.3?S_GRAPES : S_LEMON;
            for (int i=0;i<REEL;i++) t[i]=s; return t;
        }
        // lose: all different, none matching
        t[0]=S_CHERRY; t[1]=S_LEMON; t[2]=S_GRAPES;
        return t;
    }

    private void startResultAnim(boolean win, boolean jackpot, double result, double bet) {
        if (jackpot) {
            resText="🎰  JACKPOT !"; resColor=0xFFFFD84A;
            glowing=true; startGlow(); startShimmer();
        } else if (win) {
            double net=result-bet;
            resText="✨  WIN  +"+String.format(Locale.US,"%.2f",net)+" FUN";
            resColor=0xFF34D399; glowing=true; startGlow();
        } else {
            resText="❌  No match — try again!"; resColor=0xFFF87171;
        }
        if (resAnim!=null) resAnim.cancel();
        resAnim=ValueAnimator.ofFloat(0f,1f); resAnim.setDuration(700);
        resAnim.addUpdateListener(v->{ resAlpha=(float)v.getAnimatedValue(); invalidate(); });
        resAnim.start();
    }

    private void startGlow() {
        if (glowAnim!=null) glowAnim.cancel();
        glowAnim=ValueAnimator.ofFloat(0f,(float)(Math.PI*4));
        glowAnim.setDuration(2000); glowAnim.setRepeatCount(ValueAnimator.INFINITE);
        glowAnim.addUpdateListener(v->{ glowR=(float)v.getAnimatedValue(); invalidate(); });
        glowAnim.start();
    }
    private void startShimmer() {
        if (shimAnim!=null) shimAnim.cancel();
        shimAnim=ValueAnimator.ofFloat(-getWidth()*.2f, getWidth()*1.2f);
        shimAnim.setDuration(1200); shimAnim.setRepeatCount(ValueAnimator.INFINITE);
        shimAnim.addUpdateListener(v->{ shimmerX=(float)v.getAnimatedValue(); invalidate(); });
        shimAnim.start();
    }

    public void reset() {
        for (int r=0;r<REEL;r++) { if(anims[r]!=null){anims[r].cancel();anims[r]=null;} offset[r]=0f; }
        if(resAnim!=null){resAnim.cancel();resAnim=null;}
        if(shimAnim!=null){shimAnim.cancel();shimAnim=null;}
        if(glowAnim!=null){glowAnim.cancel();glowAnim=null;}
        spinning=false; glowing=false; resText=""; resAlpha=0f; shimmerX=-1f;
        invalidate();
    }
}
