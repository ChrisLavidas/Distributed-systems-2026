package com.funGames.app.ui.games;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * EXTREME pro European Roulette.
 *
 * • Real wheel order (37 pockets: 0-36)
 * • Ball with physics — fast orbit → deceleration → drop into pocket
 * • Pocket highlight + number flash when ball lands
 * • Interactive betting table (tap to select bet)
 * • WIN / LOSE overlay with confetti
 * • Shimmer on rim while spinning
 */
public class RouletteView extends View {

    /* ── real European wheel ─────────────────────────────────────── */
    private static final int[] WHEEL = {
            0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,
            5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26
    };
    private static final int POCKETS = 37;
    private static final int[] REDS = {1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36};

    public enum BetType { NONE,RED,BLACK,ODD,EVEN,LOW,HIGH,DOZEN1,DOZEN2,DOZEN3,STRAIGHT }
    public interface BetChangeListener { void onBetChanged(BetType t, int num); }

    /* ── paints ──────────────────────────────────────────────────── */
    private final Paint pktP    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borP    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numP    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ballP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimP    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btBgP   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btSelP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btTxtP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hlPktP  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resultP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint confP   = new Paint(Paint.ANTI_ALIAS_FLAG);

    /* ── state ───────────────────────────────────────────────────── */
    private float wheelAngle=0f, ballAngle=0f, ballOrbitR=0f;
    private float shimmer=0f;
    private ValueAnimator wheelA, ballA, shimmerA, resultA, glowA;
    private float glowAlpha=0f;

    private BetType selBet = BetType.NONE;
    private int     selNum = -1;
    private int     landed = -1;

    private String  resText =""; private int resColor=0xFFFFFFFF; private float resAlpha=0f;

    /* ── confetti ────────────────────────────────────────────────── */
    private static final int CONF=30;
    private final float[] cX=new float[CONF],cY=new float[CONF],cVY=new float[CONF];
    private final int[]   cCol=new int[CONF];
    private float confProg=0f; private boolean showConf=false;
    private ValueAnimator confA;

    /* ── hit-test rects ──────────────────────────────────────────── */
    private final RectF[] btRects  = new RectF[BetType.values().length];
    private final RectF[] numRects = new RectF[POCKETS];
    private float tableTop=0f, wheelCY=0f;

    // pinch zoom
    private float scaleF = 1f;
    private ScaleGestureDetector scaleDetector;

    // number rain
    private static final int RAIN_COUNT = 22;
    private final float[] rainX  = new float[RAIN_COUNT];
    private final float[] rainY  = new float[RAIN_COUNT];
    private final float[] rainSp = new float[RAIN_COUNT];
    private final int[]   rainN  = new int[RAIN_COUNT];
    private ValueAnimator rainAnim;
    private final Paint rainP = new Paint(Paint.ANTI_ALIAS_FLAG);

    private BetChangeListener listener;
    public void setBetChangeListener(BetChangeListener l){ listener=l; }
    public BetType getSelectedBet(){ return selBet; }
    public int getStraightNumber(){ return selNum; }

    public RouletteView(Context ctx){
        super(ctx); setClickable(true);
        rimP.setStyle(Paint.Style.STROKE); rimP.setStrokeWidth(16f);
        borP.setStyle(Paint.Style.STROKE); borP.setStrokeWidth(1.5f);
        numP.setColor(Color.WHITE); numP.setTextAlign(Paint.Align.CENTER);
        numP.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        ballP.setColor(0xFFF0F0F0); ballP.setShadowLayer(10f,0,0,0xDDFFFFFF);
        btSelP.setColor(0xCCFFD84A); btSelP.setStyle(Paint.Style.FILL);
        btTxtP.setColor(Color.WHITE); btTxtP.setTextAlign(Paint.Align.CENTER);
        btTxtP.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        hlPktP.setStyle(Paint.Style.FILL);
        resultP.setTextAlign(Paint.Align.CENTER);
        resultP.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        resultP.setShadowLayer(18f,0,0,0xFF000000);
        int[] pcols={0xFFFFD84A,0xFF34D399,0xFFF87171,0xFF8B5CF6,0xFFFF8C00,0xFFFFFFFF};
        for(int i=0;i<CONF;i++){
            cX[i]=(float)(Math.random()); cY[i]=(float)(Math.random()*.3f);
            cVY[i]=.4f+(float)(Math.random()*.6f); cCol[i]=pcols[i%pcols.length];
        }
        for(int i=0;i<btRects.length;i++) btRects[i]=new RectF();
        for(int i=0;i<RAIN_COUNT;i++){
            rainX[i]=(float)Math.random();
            rainY[i]=(float)Math.random();
            rainSp[i]=.003f+(float)(Math.random()*.005f);
            rainN[i]=(int)(Math.random()*37);
        }
        rainP.setColor(0x0BFFD84A);
        rainP.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        rainP.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<numRects.length;i++) numRects[i]=new RectF();
    }

    @Override
    protected void onDraw(Canvas c){
        int W=getWidth(),H=getHeight();
        c.drawColor(0xFF05060F);
        float wheelH=H*.54f, cx=W/2f, cy=wheelH/2f;
        wheelCY=cy; tableTop=wheelH;
        float outerR=Math.min(cx,cy)*.87f;
        drawNumberRain(c,W,H);
        // apply zoom to wheel area only
        c.save();
        c.scale(scaleF, scaleF, cx, cy);
        drawWheel(c,cx,cy,outerR);
        drawBall(c,cx,cy,outerR);
        c.restore();
        drawBall(c,cx,cy,outerR);
        drawTable(c,W,H);
        drawResultOverlay(c,W);
        if(showConf) drawConfetti(c,W,H);
    }

    /* ── wheel ───────────────────────────────────────────────────── */
    private void drawNumberRain(Canvas c, int W, int H){
        rainP.setTextSize(W*.035f);
        for(int i=0;i<RAIN_COUNT;i++){
            c.drawText(String.valueOf(rainN[i]),
                    rainX[i]*W, rainY[i]*tableTop, rainP);
        }
    }

    private void drawWheel(Canvas c, float cx, float cy, float outerR){
        // background glow
        Paint bgGlow=new Paint(Paint.ANTI_ALIAS_FLAG);
        bgGlow.setShader(new RadialGradient(cx,cy,outerR*1.1f,
                new int[]{0x22D4AF37,0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,outerR*1.1f,bgGlow);

        // rim shimmer
        rimP.setColor(0xFFD4AF37);
        rimP.setShadowLayer(shimmer>0?shimmer*20f:8f, 0,0, 0xAAD4AF37);
        c.drawCircle(cx,cy,outerR+rimP.getStrokeWidth()/2f,rimP);

        float innerR=outerR*.37f, sweep=360f/POCKETS;
        c.save(); c.rotate(wheelAngle,cx,cy);
        RectF oval=new RectF(cx-outerR,cy-outerR,cx+outerR,cy+outerR);

        for(int i=0;i<POCKETS;i++){
            int num=WHEEL[i];
            float start=i*sweep-90f;
            int col = num==0?0xFF1E7A1E : isRed(num)?0xFF8B0000 : 0xFF111111;
            // highlight landed pocket
            if(num==landed){
                col = num==0?0xFF2EBB2E : isRed(num)?0xFFCC1111 : 0xFF444444;
                hlPktP.setColor(0x66FFD84A);
                c.drawArc(oval,start,sweep,true,hlPktP);
            }
            pktP.setColor(col); c.drawArc(oval,start,sweep,true,pktP);
            borP.setColor(0xFF555522); c.drawArc(oval,start,sweep,true,borP);

            numP.setTextSize(outerR*.073f);
            float mid=(float)Math.toRadians(start+sweep/2f);
            c.drawText(String.valueOf(num),
                    (float)(cx+Math.cos(mid)*outerR*.76f),
                    (float)(cy+Math.sin(mid)*outerR*.76f)+numP.getTextSize()*.35f, numP);
        }

        // inner bowl
        Paint bowlP=new Paint(Paint.ANTI_ALIAS_FLAG);
        bowlP.setShader(new RadialGradient(cx,cy,innerR,
                new int[]{0xFF0A0F28,0xFF141A38}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,innerR,bowlP);
        borP.setColor(0xFFD4AF37); borP.setStrokeWidth(3f); c.drawCircle(cx,cy,innerR,borP);
        borP.setStrokeWidth(1.5f);

        // spokes
        Paint sp=new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setStyle(Paint.Style.STROKE); sp.setStrokeWidth(1.5f); sp.setColor(0x55D4AF37);
        for(int i=0;i<8;i++){
            double a=Math.toRadians(i*45.0);
            c.drawLine(cx+(float)(Math.cos(a)*innerR*.25f),cy+(float)(Math.sin(a)*innerR*.25f),
                       cx+(float)(Math.cos(a)*innerR*.92f),cy+(float)(Math.sin(a)*innerR*.92f),sp);
        }

        // hub
        Paint hubP=new Paint(Paint.ANTI_ALIAS_FLAG);
        hubP.setShader(new RadialGradient(cx,cy,innerR*.2f,
                new int[]{0xFFFFE680,0xFFB07A0E},null,Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,innerR*.2f,hubP);
        c.restore();

        // arrow
        drawArrow(c,cx,cy-outerR-rimP.getStrokeWidth()/2f-2f,outerR*.06f);
    }

    private void drawArrow(Canvas c, float cx, float tip, float s){
        Path p=new Path();
        p.moveTo(cx,tip); p.lineTo(cx-s,tip-s*2.3f); p.lineTo(cx+s,tip-s*2.3f); p.close();
        Paint ap=new Paint(Paint.ANTI_ALIAS_FLAG);
        ap.setColor(0xFFFFD84A); ap.setShadowLayer(10f,0,2,0x99000000);
        c.drawPath(p,ap);
        // shine
        ap.setColor(0x88FFFFFF); ap.clearShadowLayer();
        c.drawCircle(cx-s*.15f, tip-s*1.3f, s*.18f, ap);
    }

    /* ── ball ────────────────────────────────────────────────────── */
    private void drawBall(Canvas c, float cx, float cy, float outerR){
        float r=outerR*(ballOrbitR>0?ballOrbitR:.91f);
        float bx=(float)(cx+Math.cos(Math.toRadians(ballAngle))*r);
        float by=(float)(cy+Math.sin(Math.toRadians(ballAngle))*r);
        float br=outerR*.047f;
        // shadow
        Paint sh=new Paint(Paint.ANTI_ALIAS_FLAG); sh.setColor(0x55000000);
        c.drawCircle(bx+2,by+2,br,sh);
        // ball gradient
        Paint bpP=new Paint(Paint.ANTI_ALIAS_FLAG);
        bpP.setShader(new RadialGradient(bx-br*.3f,by-br*.3f,br,
                new int[]{0xFFFFFFFF,0xFFCCCCCC,0xFF888888},
                new float[]{0f,.5f,1f}, Shader.TileMode.CLAMP));
        c.drawCircle(bx,by,br,bpP);
    }

    /* ── betting table ───────────────────────────────────────────── */
    private void drawTable(Canvas c, int W, int H){
        float pad=W*.025f, avW=W-2*pad, avH=H-tableTop-pad;
        float rowH=avH/5.2f, btnH=rowH*.83f, y0=tableTop+pad*.35f;

        btTxtP.setColor(0xFF8888AA);
        btTxtP.setTextSize(btnH*.18f);
        c.drawText("▼  SELECT YOUR BET  ▼", W/2f, y0+btnH*.22f, btTxtP);
        btTxtP.setColor(Color.WHITE); y0+=btnH*.35f;

        float bw3=avW/3f-pad*.4f;
        drawBetBtn(c,BetType.RED,   pad,                       y0,bw3,btnH,0xFF8B0000,"RED\n2x");
        drawBetBtn(c,BetType.BLACK, pad+bw3+pad*.4f,           y0,bw3,btnH,0xFF111111,"BLACK\n2x");
        drawGreenBtn(c,             pad+2*(bw3+pad*.4f),        y0,bw3,btnH);

        float y1=y0+rowH, bw4=avW/4f-pad*.28f;
        drawBetBtn(c,BetType.ODD,  pad,                        y1,bw4,btnH,0xFF1C2047,"ODD\n2x");
        drawBetBtn(c,BetType.EVEN, pad+bw4+pad*.28f,           y1,bw4,btnH,0xFF1C2047,"EVEN\n2x");
        drawBetBtn(c,BetType.LOW,  pad+2*(bw4+pad*.28f),       y1,bw4,btnH,0xFF1C2047,"1-18\n2x");
        drawBetBtn(c,BetType.HIGH, pad+3*(bw4+pad*.28f),       y1,bw4,btnH,0xFF1C2047,"19-36\n2x");

        float y2=y1+rowH;
        drawBetBtn(c,BetType.DOZEN1,pad,                        y2,bw3,btnH,0xFF252A5A,"1st 12\n3x");
        drawBetBtn(c,BetType.DOZEN2,pad+bw3+pad*.4f,            y2,bw3,btnH,0xFF252A5A,"2nd 12\n3x");
        drawBetBtn(c,BetType.DOZEN3,pad+2*(bw3+pad*.4f),        y2,bw3,btnH,0xFF252A5A,"3rd 12\n3x");

        drawNumberGrid(c,pad,y2+rowH,avW,H-(y2+rowH)-pad*.3f);
    }

    private void drawBetBtn(Canvas c,BetType t,float x,float y,float w,float h,int bg,String lbl){
        btRects[t.ordinal()].set(x,y,x+w,y+h);
        boolean sel=(selBet==t);
        Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setShader(new LinearGradient(x,y,x,y+h,
                blend(bg,.25f),bg, Shader.TileMode.CLAMP));
        RectF r=btRects[t.ordinal()];
        c.drawRoundRect(r,9,9,bp);
        if(sel) c.drawRoundRect(r,9,9,btSelP);
        Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setColor(sel?0xFFFFD84A:0xFF2A3060); rim.setStrokeWidth(sel?3f:1.5f);
        c.drawRoundRect(r,9,9,rim);
        btTxtP.setTextSize(h*.24f);
        String[] lines=lbl.split("\n");
        float ly=y+h/2f-(lines.length-1)*btTxtP.getTextSize()*.55f;
        for(String l:lines){c.drawText(l,x+w/2f,ly,btTxtP);ly+=btTxtP.getTextSize()*1.15f;}
    }

    private void drawGreenBtn(Canvas c,float x,float y,float w,float h){
        boolean sel=(selBet==BetType.STRAIGHT&&selNum==0);
        RectF r=new RectF(x,y,x+w,y+h);
        numRects[0].set(r);
        Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setShader(new LinearGradient(x,y,x,y+h,0xFF2A7A2A,0xFF185A18,Shader.TileMode.CLAMP));
        c.drawRoundRect(r,9,9,bp);
        if(sel) c.drawRoundRect(r,9,9,btSelP);
        Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE); rim.setColor(sel?0xFFFFD84A:0xFF287028); rim.setStrokeWidth(sel?3f:1.5f);
        c.drawRoundRect(r,9,9,rim);
        if(landed==0){hlPktP.setColor(0x66FFD84A);c.drawRoundRect(r,9,9,hlPktP);}
        btTxtP.setTextSize(h*.32f); c.drawText("0",x+w/2f,y+h*.5f,btTxtP);
        btTxtP.setTextSize(h*.2f);  c.drawText("36x",x+w/2f,y+h*.77f,btTxtP);
    }

    private void drawNumberGrid(Canvas c,float x,float y,float totW,float totH){
        int cols=12,rows=3;
        float cw=totW/cols, rh=totH/rows;
        btTxtP.setTextSize(rh*.18f); btTxtP.setColor(0xFF666688);
        c.drawText("STRAIGHT  36x",x+totW/2f,y-2f,btTxtP);
        btTxtP.setColor(Color.WHITE);

        for(int col=0;col<cols;col++){
            for(int row=0;row<rows;row++){
                int num=col*3+row+1;
                float rx=x+col*cw, ry=y+row*rh;
                RectF r=new RectF(rx+1.5f,ry+1.5f,rx+cw-1.5f,ry+rh-1.5f);
                numRects[num].set(r);
                boolean sel=(selBet==BetType.STRAIGHT&&selNum==num);
                boolean isLand=(landed==num);

                Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG);
                int top2=isRed(num)?(isLand?0xFFAA1515:0xFF5C0C0C):(isLand?0xFF333333:0xFF0A0A18);
                int bot2=isRed(num)?(isLand?0xFF880E0E:0xFF3A0808):(isLand?0xFF222222:0xFF060610);
                bp.setShader(new LinearGradient(rx,ry,rx,ry+rh,top2,bot2,Shader.TileMode.CLAMP));
                c.drawRoundRect(r,5,5,bp);
                if(sel) c.drawRoundRect(r,5,5,btSelP);
                if(isLand){hlPktP.setColor(0x88FFD84A);c.drawRoundRect(r,5,5,hlPktP);}
                Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG);
                rim.setStyle(Paint.Style.STROKE);
                rim.setColor(sel?0xFFFFD84A:(isLand?0xFFFFD84A:0xFF1E2450));
                rim.setStrokeWidth(sel||isLand?2.5f:.8f);
                c.drawRoundRect(r,5,5,rim);
                btTxtP.setTextSize(rh*.44f);
                c.drawText(String.valueOf(num),rx+cw/2f,ry+rh*.7f,btTxtP);
            }
        }
    }

    private void drawResultOverlay(Canvas c,int W){
        if(resAlpha<=0f||resText.isEmpty()) return;
        resultP.setColor(resColor); resultP.setAlpha((int)(resAlpha*255));
        resultP.setTextSize(getHeight()*.058f);
        c.drawText(resText,W/2f,wheelCY*.3f,resultP);
    }

    private void drawConfetti(Canvas c,int W,int H){
        for(int i=0;i<CONF;i++){
            float px=cX[i]*W;
            float py=(cY[i]+confProg*cVY[i])*H;
            confP.setColor(cCol[i]);
            confP.setAlpha((int)((1f-confProg)*220));
            c.drawCircle(px,py,W*.012f,confP);
        }
    }

    /* ── touch ───────────────────────────────────────────────────── */
    @Override
    public boolean onTouchEvent(MotionEvent ev){
        if(scaleDetector!=null) scaleDetector.onTouchEvent(ev);
        if(scaleDetector!=null && scaleDetector.isInProgress()) return true;
        if(ev.getAction()!=MotionEvent.ACTION_UP) return true;
        float tx=ev.getX(),ty=ev.getY();
        if(ty<tableTop) return true;
        BetType[] named={BetType.RED,BetType.BLACK,BetType.ODD,BetType.EVEN,
                         BetType.LOW,BetType.HIGH,BetType.DOZEN1,BetType.DOZEN2,BetType.DOZEN3};
        for(BetType bt:named){
            if(btRects[bt.ordinal()].contains(tx,ty)){
                selBet=bt;selNum=-1;invalidate();
                if(listener!=null) listener.onBetChanged(bt,-1);
                return true;
            }
        }
        for(int n=0;n<POCKETS;n++){
            if(numRects[n].contains(tx,ty)){
                selBet=BetType.STRAIGHT;selNum=n;invalidate();
                if(listener!=null) listener.onBetChanged(BetType.STRAIGHT,n);
                return true;
            }
        }
        return true;
    }

    /* ── spin API ────────────────────────────────────────────────── */
    private void startRain(){
        if(rainAnim!=null) rainAnim.cancel();
        rainAnim=ValueAnimator.ofFloat(0f,1f);
        rainAnim.setDuration(800); rainAnim.setRepeatCount(ValueAnimator.INFINITE);
        rainAnim.setRepeatMode(ValueAnimator.RESTART);
        rainAnim.addUpdateListener(v->{
            for(int i=0;i<RAIN_COUNT;i++){
                rainY[i]+=rainSp[i];
                if(rainY[i]>1f){rainY[i]=0f;rainN[i]=(int)(Math.random()*37);}
            }
            invalidate();
        });
        rainAnim.start();
    }

    public void startSpin(){
        landed=-1; resText=""; resAlpha=0f; shimmer=0f; showConf=false;
        if(wheelA!=null)   wheelA.cancel();
        if(ballA!=null)    ballA.cancel();
        if(shimmerA!=null) shimmerA.cancel();
        if(resultA!=null)  resultA.cancel();
        if(glowA!=null)    glowA.cancel();
        if(confA!=null)    confA.cancel();
        ballOrbitR=.91f;

        wheelA=ValueAnimator.ofFloat(wheelAngle,wheelAngle+1800f);
        wheelA.setDuration(5000); wheelA.setRepeatCount(ValueAnimator.INFINITE);
        wheelA.setRepeatMode(ValueAnimator.RESTART);
        wheelA.addUpdateListener(v->{wheelAngle=(float)v.getAnimatedValue()%360f;invalidate();});
        wheelA.start();

        ballA=ValueAnimator.ofFloat(ballAngle,ballAngle-7200f);
        ballA.setDuration(5000); ballA.setRepeatCount(ValueAnimator.INFINITE);
        ballA.setRepeatMode(ValueAnimator.RESTART);
        ballA.addUpdateListener(v->{ballAngle=(float)v.getAnimatedValue();invalidate();});
        ballA.start();

        shimmerA=ValueAnimator.ofFloat(0f,1f,0f);
        shimmerA.setDuration(1500); shimmerA.setRepeatCount(ValueAnimator.INFINITE);
        shimmerA.addUpdateListener(v->{shimmer=(float)v.getAnimatedValue();invalidate();});
        shimmerA.start();
    }

    public void stopSpin(boolean win,boolean jackpot,double result,double bet,Runnable onDone){
        if(wheelA!=null) wheelA.cancel();
        if(ballA!=null)  ballA.cancel();
        if(shimmerA!=null){shimmerA.cancel();shimmer=0f;}

        int target=pickLanding(win,jackpot);
        landed=target;

        int pIdx=0;
        for(int i=0;i<POCKETS;i++) if(WHEEL[i]==target){pIdx=i;break;}
        float sweep=360f/POCKETS;
        float pktCentre=pIdx*sweep-90f+sweep/2f;

        float finalWheel=wheelAngle+540f;
        float wf=finalWheel%360f;
        float diff=((pktCentre+wf)-ballAngle%360f+720f)%360f;
        float finalBall=ballAngle-6*360f-(360f-diff);

        final float sW=wheelAngle,sB=ballAngle;
        ValueAnimator a=ValueAnimator.ofFloat(0f,1f);
        a.setDuration(4200); a.setInterpolator(new DecelerateInterpolator(4f));
        a.addUpdateListener(va->{
            float f=va.getAnimatedFraction();
            wheelAngle=(sW+(finalWheel-sW)*f)%360f;
            ballAngle=sB+(finalBall-sB)*f;
            // ball spirals inward
            ballOrbitR=.91f-.08f*f;
            invalidate();
        });
        a.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator anim){
                showResult(win,jackpot,result,bet);
                if(onDone!=null) onDone.run();
            }
        });
        a.start();
    }

    public void stopSpin(boolean win,boolean jackpot,Runnable onDone){
        stopSpin(win,jackpot,0,0,onDone);
    }

    private int pickLanding(boolean win,boolean jackpot){
        if(jackpot) return 0;
        if(selBet==BetType.NONE) return (int)(Math.random()*37);
        List<Integer> pool=new ArrayList<>();
        for(int n=0;n<=36;n++) if(betMatches(n)==win) pool.add(n);
        if(pool.isEmpty()) return (int)(Math.random()*37);
        return pool.get((int)(Math.random()*pool.size()));
    }

    private boolean betMatches(int n){
        switch(selBet){
            case RED:    return isRed(n);
            case BLACK:  return !isRed(n)&&n!=0;
            case ODD:    return n>0&&n%2==1;
            case EVEN:   return n>0&&n%2==0;
            case LOW:    return n>=1&&n<=18;
            case HIGH:   return n>=19;
            case DOZEN1: return n>=1&&n<=12;
            case DOZEN2: return n>=13&&n<=24;
            case DOZEN3: return n>=25&&n<=36;
            case STRAIGHT: return n==selNum;
            default:     return false;
        }
    }

    private void showResult(boolean win,boolean jackpot,double result,double bet){
        if(jackpot){
            resText="🎰  JACKPOT  🎰"; resColor=0xFFFFD84A;
            showConf=true; fireConfetti();
        } else if(win){
            double net=result-bet;
            resText="🎉  "+landed+"  •  +"+String.format(Locale.US,"%.2f",net)+" FUN";
            resColor=0xFF34D399;
        } else {
            resText="❌  "+landed+"  •  No win — try again!"; resColor=0xFFF87171;
        }
        glowA=ValueAnimator.ofFloat(0f,1f,0.5f,1f,0f);
        glowA.setDuration(win||jackpot?1600:800);
        if(win||jackpot) glowA.setRepeatCount(win?3:ValueAnimator.INFINITE);
        glowA.addUpdateListener(v->{glowAlpha=(float)v.getAnimatedValue();invalidate();});
        glowA.start();

        resultA=ValueAnimator.ofFloat(0f,1f); resultA.setDuration(700);
        resultA.addUpdateListener(v->{resAlpha=(float)v.getAnimatedValue();invalidate();});
        resultA.start();
    }

    private void fireConfetti(){
        confProg=0f;
        if(confA!=null) confA.cancel();
        confA=ValueAnimator.ofFloat(0f,1.2f); confA.setDuration(2500);
        confA.setRepeatCount(ValueAnimator.INFINITE);
        confA.addUpdateListener(v->{confProg=(float)v.getAnimatedValue();invalidate();});
        confA.start();
    }

    public void reset(){
        if(wheelA!=null){wheelA.cancel();wheelA=null;}
        if(ballA!=null) {ballA.cancel();ballA=null;}
        if(shimmerA!=null){shimmerA.cancel();shimmerA=null;}
        if(resultA!=null){resultA.cancel();resultA=null;}
        if(glowA!=null){glowA.cancel();glowA=null;}
        if(confA!=null){confA.cancel();confA=null;}
        if(rainAnim!=null){rainAnim.cancel();startRain();}
        wheelAngle=0f;ballAngle=0f;ballOrbitR=.91f;shimmer=0f;
        landed=-1;resText="";resAlpha=0f;showConf=false;confProg=0f;
        invalidate();
    }

    private boolean isRed(int n){for(int r:REDS)if(r==n)return true;return false;}

    /* lighten a colour for gradient top */
    private static int blend(int col,float amt){
        int r=Math.min(255,(int)(Color.red(col)*(1+amt)+255*amt));
        int g=Math.min(255,(int)(Color.green(col)*(1+amt)+255*amt));
        int b=Math.min(255,(int)(Color.blue(col)*(1+amt)+255*amt));
        return Color.rgb(r,g,b);
    }
}
