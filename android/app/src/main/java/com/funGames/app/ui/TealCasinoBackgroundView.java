package com.funGames.app.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;

/**
 * Luxury black/gold casino background for the Manager screen.
 */
public class TealCasinoBackgroundView extends CasinoBackgroundView {

    public TealCasinoBackgroundView(Context ctx) { super(ctx); }
    public TealCasinoBackgroundView(Context ctx, AttributeSet a) { super(ctx, a); }

    @Override
    protected void onDraw(Canvas canvas) {
        int W = getWidth(), H = getHeight();
        if (W == 0 || H == 0) return;

        // 1. Deep black-gold base gradient
        Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        base.setShader(new RadialGradient(W/2f, H*.35f, Math.max(W,H)*.9f,
                new int[]{0xFF1A1200, 0xFF0D0900, 0xFF050300},
                new float[]{0f,.55f,1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,W,H,base);

        // 2. Gold bokeh orbs
        int[] goldColors = {
            0x1AFFD700,0x14D4AF37,0x10B8860B,0x18FFD700,
            0x0CD4AF37,0x0EFFD700,0x08B8860B,0x06FFD700,
            0x14D4AF37,0x0CFFD700,0x12B8860B,0x0CD4AF37,
            0x0AFFD700,0x08D4AF37,0x10FFD700,0x08B8860B
        };

        for (int i = 0; i < 16; i++) {
            float phase = (float)(bph[i] + tick * bsp[i] * Math.PI * 2);
            float cx = (bx[i] + (float)(Math.sin(phase)*.06f)) * W;
            float cy = (by[i] + (float)(Math.cos(phase*.7f)*.05f)) * H;
            float r  = br[i] * Math.min(W,H);
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setShader(new RadialGradient(cx,cy,r,
                    goldColors[i % goldColors.length], 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,r,bp);
        }

        // 3. Diagonal gold grid
        Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridP.setStyle(Paint.Style.STROKE); gridP.setStrokeWidth(1f);
        int gridStep = Math.max(W,H)/22;
        gridP.setColor(0x08D4AF37);
        for (int i=-H;i<W+H;i+=gridStep){canvas.drawLine(i,0,i+H,H,gridP);canvas.drawLine(i,H,i+H,0,gridP);}
        gridP.setColor(0x05FFD700);
        for (int x=0;x<W;x+=gridStep) canvas.drawLine(x,0,x,H,gridP);
        for (int y=0;y<H;y+=gridStep) canvas.drawLine(0,y,W,y,gridP);

        // 4. Floating suits in gold
        Paint suitP2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        suitP2.setTextAlign(Paint.Align.CENTER);
        suitP2.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        String[] SUITS={"♠","♥","♦","♣","★"};
        for (int i=0;i<12;i++){
            float rot=srt[i]+tick*ssp[i]*360f;
            float cx=sx[i]*W, cy=sy[i]*H;
            float size=ssc[i]*Math.min(W,H)*.055f;
            suitP2.setTextSize(size); suitP2.setColor(0x0FFFD700);
            canvas.save(); canvas.rotate(rot,cx,cy);
            canvas.drawText(SUITS[sidx[i]%SUITS.length],cx,cy+size*.35f,suitP2);
            canvas.restore();
        }

        // 5. Centre gold glow pulse
        float pulse=.5f+.5f*(float)Math.sin(tick*Math.PI*2*.8f);
        Paint gp=new Paint(Paint.ANTI_ALIAS_FLAG);
        gp.setShader(new RadialGradient(W/2f,H/2f,Math.min(W,H)*.55f,
                new int[]{Color.argb((int)(15*pulse),0xFF,0xD7,0x00),0x00000000},
                null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,W,H,gp);

        // 6. Orbiting gold chips
        int[] chipCols={0xFFD4AF37,0xFFB8860B,0xFFFFD700,0xFFC5A028,
                        0xFF8B6914,0xFFD4AF37,0xFFFFE84D,0xFFA07830,
                        0xFFD4AF37,0xFFFFD700};
        Paint chipP2=new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint chipRP2=new Paint(Paint.ANTI_ALIAS_FLAG);
        chipRP2.setStyle(Paint.Style.STROKE); chipRP2.setStrokeWidth(2f);
        for (int i=0;i<10;i++){
            float a=(float)Math.toRadians(chipAngle[i]+tick*chipSp[i]*360f);
            float r2=chipR[i]*Math.min(W,H)*.44f;
            float cx=(float)(W/2f+Math.cos(a)*r2);
            float cy=(float)(H/2f+Math.sin(a)*r2);
            float cr=Math.min(W,H)*.025f;
            chipP2.setColor(chipCols[i]&0x00FFFFFF|0x18000000);
            canvas.drawCircle(cx,cy,cr,chipP2);
            chipRP2.setColor(chipCols[i]&0x00FFFFFF|0x22000000);
            canvas.drawCircle(cx,cy,cr,chipRP2);
            chipRP2.setColor(chipCols[i]&0x00FFFFFF|0x15000000);
            canvas.drawCircle(cx,cy,cr*.7f,chipRP2);
        }

        // 7. Vignettes
        Paint topFade=new Paint();
        topFade.setShader(new LinearGradient(0,0,0,H*.25f,0x66000000,0x00000000,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,W,H*.25f,topFade);
        Paint botFade=new Paint();
        botFade.setShader(new LinearGradient(0,H*.75f,0,H,0x00000000,0x55000000,Shader.TileMode.CLAMP));
        canvas.drawRect(0,H*.75f,W,H,botFade);
    }
}
