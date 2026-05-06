package com.funGames.app.ui.games;

import android.animation.Animator;
import com.funGames.app.util.SoundManager;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import java.util.Locale;

public class PokerView extends View {
    private static final int CARD_COUNT = 5;
    private static final String[] SUITS = {"♠","♥","♦","♣"};
    private static final String[] RANKS = {"A","K","Q","J","10","9","8","7","6","5","4","3","2"};

    private final Paint tablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardFace   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rankPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suitPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint multPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint payBg      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint payText    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint confP      = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] slideIn  = new float[CARD_COUNT];
    private final float[] flipFrac = new float[CARD_COUNT];
    private final boolean[] glowing= new boolean[CARD_COUNT];
    private final String[] faceRank= new String[CARD_COUNT];
    private final String[] faceSuit= new String[CARD_COUNT];
    private final int[]    faceRed = new int[CARD_COUNT];

    private String handLabel="", multLabel="";
    private float  handAlpha=0f;
    private int    handColor=Color.WHITE;
    private float  glowAlpha=0f;
    private float  shimmerX=-1f;

    private boolean resultReady=false, win=false, jackpot=false;
    // shuffle animation
    private float[] shuffleX = new float[CARD_COUNT];
    private float   shuffleFrac = 0f;
    private boolean shuffling   = false;
    private ValueAnimator shuffleAnim;
    private double result=0, bet=0;
    private Runnable pendingDone;

    private ValueAnimator glowAnim, shimAnim;

    // confetti
    private static final int CONF=25;
    private final float[] cX=new float[CONF],cVY=new float[CONF];
    private final int[] cCol=new int[CONF];
    private float confProg=0f; private boolean showConf=false;
    private ValueAnimator confA;

    public PokerView(Context ctx) {
        super(ctx);
        cardFace.setStyle(Paint.Style.FILL);
        cardBorder.setStyle(Paint.Style.STROKE); cardBorder.setStrokeWidth(2.5f);
        rankPaint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        rankPaint.setTextAlign(Paint.Align.LEFT);
        suitPaint.setTypeface(Typeface.DEFAULT_BOLD);
        suitPaint.setTextAlign(Paint.Align.CENTER);
        handPaint.setTextAlign(Paint.Align.CENTER);
        handPaint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        handPaint.setShadowLayer(20f,0,0,0xFF000000);
        multPaint.setTextAlign(Paint.Align.CENTER);
        multPaint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        glowPaint.setStyle(Paint.Style.STROKE); glowPaint.setStrokeWidth(6f);
        payBg.setColor(0xFF080A18);
        payText.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        payText.setTextAlign(Paint.Align.CENTER);
        int[] pc={0xFFFFD84A,0xFF34D399,0xFFF87171,0xFF8B5CF6,0xFFFF8C00,0xFFFFFFFF};
        for(int i=0;i<CONF;i++){cX[i]=(float)Math.random();cVY[i]=.4f+(float)(Math.random()*.6f);cCol[i]=pc[i%pc.length];}
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int W=getWidth(),H=getHeight();
        canvas.drawColor(0xFF05060F);
        float tableH=H*.57f, payH=H-tableH;
        drawTable(canvas,W,tableH);
        drawCards(canvas,W,tableH);
        drawHandLabel(canvas,W,tableH);
        drawShimmer(canvas,W,tableH);
        drawPaytable(canvas,W,H,tableH,payH);
        if(showConf) drawConfetti(canvas,W,H);
    }

    private void drawTable(Canvas c,int W,float tH){
        RectF r=new RectF(W*.035f,tH*.03f,W*.965f,tH*.97f);
        Paint tp=new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setShader(new LinearGradient(0,0,0,tH,
                new int[]{0xFF0D3020,0xFF071A10,0xFF0D3020},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(r,22,22,tp);
        Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(4f);
        rim.setColor(0xFFD4AF37);rim.setShadowLayer(12f,0,0,0x88D4AF37);
        c.drawRoundRect(r,22,22,rim);
        // inner felt texture ring
        rim.setColor(0x33D4AF37);rim.setStrokeWidth(2f);rim.clearShadowLayer();
        RectF inner=new RectF(r.left+12,r.top+12,r.right-12,r.bottom-12);
        c.drawRoundRect(inner,16,16,rim);
        // title
        Paint tp2=new Paint(Paint.ANTI_ALIAS_FLAG);
        tp2.setColor(0xFFD4AF37);tp2.setTextAlign(Paint.Align.CENTER);
        tp2.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        tp2.setTextSize(tH*.065f);tp2.setShadowLayer(14f,0,0,0xAAD4AF37);
        c.drawText("♠  MEGA POKER  ♥",W/2f,tH*.09f,tp2);
    }

    private void drawCards(Canvas c,int W,float tH){
        float cardW=W*.157f, cardH=cardW*1.5f;
        float gap=(W-CARD_COUNT*cardW)/(CARD_COUNT+1f);
        float baseY=tH*.18f;
        for(int i=0;i<CARD_COUNT;i++){
            float cx=gap+i*(cardW+gap)+cardW/2f + (i < shuffleX.length ? shuffleX[i] : 0f);
            float cy=baseY-(1f-slideIn[i])*tH*.5f+cardH/2f;
            drawCard(c,cx-cardW/2f,cy-cardH/2f,cardW,cardH,i);
        }
    }

    private void drawCard(Canvas c,float x,float y,float cw,float ch,int idx){
        float f=flipFrac[idx];
        float sx=f<.5f?(1f-2f*f):(2f*(f-.5f));
        c.save();
        c.translate(x+cw/2f,y+ch/2f);
        c.scale(sx,1f);
        c.translate(-cw/2f,-ch/2f);
        RectF r=new RectF(0,0,cw,ch);
        // drop shadow
        Paint sh=new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(0x66000000);
        c.drawRoundRect(new RectF(4,5,cw+4,ch+5),12,12,sh);

        if(f<.5f){
            // back — premium pattern
            Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setShader(new LinearGradient(0,0,cw,ch,
                    new int[]{0xFF3B1E6B,0xFF1A0F3E,0xFF2D1558,0xFF1A0F3E},
                    new float[]{0f,.4f,.6f,1f},Shader.TileMode.CLAMP));
            c.drawRoundRect(r,12,12,bp);
            // diamond grid
            Paint gp=new Paint(Paint.ANTI_ALIAS_FLAG);
            gp.setStyle(Paint.Style.STROKE);gp.setColor(0x33FFD84A);gp.setStrokeWidth(.8f);
            for(float py=8;py<ch;py+=12) c.drawLine(0,py,cw,py,gp);
            for(float px=8;px<cw;px+=12) c.drawLine(px,0,px,ch,gp);
            // centre emblem
            Paint ep=new Paint(Paint.ANTI_ALIAS_FLAG);
            ep.setColor(0x55D4AF37);ep.setTextAlign(Paint.Align.CENTER);
            ep.setTypeface(Typeface.DEFAULT_BOLD);ep.setTextSize(cw*.5f);
            c.drawText("♦",cw/2f,ch*.6f,ep);
            cardBorder.setColor(0xFFD4AF37);c.drawRoundRect(r,12,12,cardBorder);
        } else {
            // face
            cardFace.setShader(new LinearGradient(0,0,cw,ch,
                    new int[]{0xFFFFFDF0,0xFFF5F0E0},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(r,12,12,cardFace);
            // glow border
            if(glowing[idx]){
                glowPaint.setColor(0xFFFFD84A);
                glowPaint.setAlpha((int)(glowAlpha*220));
                glowPaint.setShadowLayer(20f,0,0,0xFFFFD84A);
                c.drawRoundRect(r,12,12,glowPaint);
            } else {
                cardBorder.setColor(0xFFBBBB99);c.drawRoundRect(r,12,12,cardBorder);
            }
            if(faceRank[idx]!=null){
                int col=(faceRed[idx]==1)?0xFFCC1111:0xFF111111;
                // top-left rank+suit
                rankPaint.setColor(col);rankPaint.setTextSize(cw*.28f);
                c.drawText(faceRank[idx],cw*.09f,rankPaint.getTextSize()*1.05f,rankPaint);
                Paint srp=new Paint(rankPaint);srp.setTextSize(cw*.22f);
                c.drawText(faceSuit[idx],cw*.09f,rankPaint.getTextSize()*1.05f+srp.getTextSize()*1.1f,srp);
                // bottom-right (rotated)
                c.save();c.rotate(180,cw/2f,ch/2f);
                c.drawText(faceRank[idx],cw*.09f,rankPaint.getTextSize()*1.05f,rankPaint);
                c.drawText(faceSuit[idx],cw*.09f,rankPaint.getTextSize()*1.05f+srp.getTextSize()*1.1f,srp);
                c.restore();
                // large centre suit
                suitPaint.setColor(col);suitPaint.setTextSize(cw*.58f);
                c.drawText(faceSuit[idx],cw/2f,ch*.72f,suitPaint);
                // small rank repeat centre
                Paint crp=new Paint(rankPaint);crp.setTextSize(cw*.2f);crp.setTextAlign(Paint.Align.CENTER);crp.setAlpha(80);
                c.drawText(faceRank[idx],cw/2f,ch*.35f,crp);
            }
        }
        c.restore();
    }

    private void drawHandLabel(Canvas c,int W,float tH){
        if(handAlpha<=0f||handLabel.isEmpty()) return;
        // background pill
        Paint pill=new Paint(Paint.ANTI_ALIAS_FLAG);
        float tw=W*.8f,th=tH*.13f,tx=(W-tw)/2f,ty=tH*.84f;
        pill.setColor(0xCC000000);
        c.drawRoundRect(new RectF(tx,ty,tx+tw,ty+th),th/2f,th/2f,pill);
        handPaint.setAlpha((int)(handAlpha*255));handPaint.setColor(handColor);
        handPaint.setTextSize(tH*.085f);
        c.drawText(handLabel,W/2f,ty+th*.58f,handPaint);
        if(!multLabel.isEmpty()){
            multPaint.setAlpha((int)(handAlpha*200));multPaint.setColor(0xFFFFD84A);
            multPaint.setTextSize(tH*.056f);
            c.drawText(multLabel,W/2f,ty+th*.95f,multPaint);
        }
    }

    private void drawShimmer(Canvas c,int W,float tH){
        if(shimmerX<0) return;
        Paint sh=new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setShader(new LinearGradient(shimmerX-W*.18f,0,shimmerX+W*.18f,0,
                new int[]{0x00FFFFFF,0x44FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP));
        c.drawRect(W*.035f,tH*.03f,W*.965f,tH*.97f,sh);
    }

    private void drawPaytable(Canvas c,int W,int H,float top,float payH){
        float pad=W*.03f;
        RectF bg=new RectF(pad,top+pad*.3f,W-pad,H-pad*.3f);
        c.drawRoundRect(bg,14,14,payBg);
        Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(1.5f);rim.setColor(0xFF1E2450);
        c.drawRoundRect(bg,14,14,rim);

        float ts=payH*.115f, rowH=payH*.112f;
        float cx=W/2f, x2=W*.55f, x3=W*.93f;
        float y=top+pad*.4f+ts;

        payText.setTextSize(ts*.82f);payText.setColor(0xFFFFD84A);
        c.drawText("─  HAND RANKINGS  ─",cx,y,payText); y+=rowH*.9f;

        payText.setTextSize(ts*.72f);
        String[][] tbl={
            {"🎰","Royal Flush","JACKPOT","0xFFFF8C00"},
            {"⭐","Straight Flush","50x","0xFF34D399"},
            {"💎","Four of a Kind","25x","0xFF34D399"},
            {"🔔","Full House","9x","0xFF34D399"},
            {"♣","Flush","6x","0xFF34D399"},
            {"↕","Straight","4x","0xFF34D399"},
            {"3️⃣","Three of a Kind","3x","0xFF34D399"},
            {"2️⃣","Two Pair","2x","0xFF34D399"},
            {"1️⃣","One Pair","1x","0xFF34D399"},
            {"❌","High Card","0x  lose","0xFFF87171"},
        };
        Paint lP=new Paint(payText);lP.setTextAlign(Paint.Align.LEFT);
        Paint mP=new Paint(payText);mP.setTextAlign(Paint.Align.RIGHT);
        for(String[] row:tbl){
            if(y>H-pad) break;
            payText.setColor(0xFFCCCCDD);
            c.drawText(row[0],W*.07f,y,payText);
            lP.setColor(0xFFCCCCDD); c.drawText(row[1],W*.15f,y,lP);
            long col=Long.parseLong(row[3].replace("0x",""),16);
            mP.setColor((int)col); c.drawText(row[2],W*.95f,y,mP);
            y+=rowH;
        }
    }

    private void drawConfetti(Canvas c,int W,int H){
        for(int i=0;i<CONF;i++){
            confP.setColor(cCol[i]);confP.setAlpha((int)((1f-Math.min(1f,confProg))*220));
            c.drawCircle(cX[i]*W,(cVY[i]*confProg)*H,W*.013f,confP);
        }
    }

    /* ── deal API ─────────────────────────────────────────────────── */
    public void startDeal(){
        resultReady=false;handLabel="";handAlpha=0f;showConf=false;shimmerX=-1f;
        for(int i=0;i<CARD_COUNT;i++){slideIn[i]=0f;flipFrac[i]=0f;glowing[i]=false;faceRank[i]=null;}
        invalidate();
        // shuffle first
        shuffling=true; shuffleFrac=0f;
        if(shuffleAnim!=null) shuffleAnim.cancel();
        shuffleAnim = ValueAnimator.ofFloat(0f,1f);
        shuffleAnim.setDuration(600);
        shuffleAnim.addUpdateListener(v -> {
            shuffleFrac=(float)v.getAnimatedValue();
            for(int i=0;i<CARD_COUNT;i++){
                double t = shuffleFrac * Math.PI * 3;
                shuffleX[i]=(float)(Math.sin(t + i*0.8)*getWidth()*0.08f*(1-shuffleFrac));
            }
            invalidate();
        });
        shuffleAnim.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator a){
                shuffling=false;
                for(int i=0;i<CARD_COUNT;i++) shuffleX[i]=0f;
                startSlideIn();
            }
        });
        shuffleAnim.start();
    }
    private void startSlideIn(){
        for(int i=0;i<CARD_COUNT;i++){
            final int idx=i;
            ValueAnimator a=ValueAnimator.ofFloat(0f,1f);
            a.setDuration(260);a.setStartDelay(idx*210L);
            a.setInterpolator(new OvershootInterpolator(1.3f));
            a.addUpdateListener(v->{slideIn[idx]=(float)v.getAnimatedValue();invalidate();});
            a.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationStart(android.animation.Animator anim){
                    SoundManager.get().playCardDeal();
                }
            });
            a.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator anim){
                    if(idx==CARD_COUNT-1&&resultReady) revealCards();
                }
            });
            a.start();
        }
    }

    public void stopDeal(boolean w,boolean jk,double res,double bt,Runnable done){
        win=w;jackpot=jk;result=res;bet=bt;pendingDone=done;
        assignFaceValues();resultReady=true;
        boolean allIn=true;
        for(int i=0;i<CARD_COUNT;i++) if(slideIn[i]<.9f){allIn=false;break;}
        if(allIn) revealCards();
    }
    public void stopDeal(boolean w,boolean jk,Runnable done){stopDeal(w,jk,0,0,done);}

    private void assignFaceValues(){
        if(jackpot){
            String[] rr={"A","K","Q","J","10"};
            for(int i=0;i<CARD_COUNT;i++){faceRank[i]=rr[i];faceSuit[i]="♥";faceRed[i]=1;glowing[i]=true;}
            handLabel="Royal Flush  🎰";multLabel="JACKPOT";handColor=0xFFFFD84A;
        } else if(win){
            buildWinHand(bet>0?result/bet:1.0);
        } else {
            buildLoseHand();handLabel="High Card";multLabel="No win  ❌";handColor=0xFFF87171;
        }
    }

    private void buildWinHand(double m){
        if(m>=45) setHand("Straight Flush","50x",new String[]{"K","Q","J","10","9"},new int[]{0,0,0,0,0},new String[]{"♠","♠","♠","♠","♠"},true);
        else if(m>=20) setHand("Four of a Kind","25x",new String[]{"A","A","A","A","K"},new int[]{0,1,0,2,0},new String[]{"♠","♥","♦","♣","♠"},true);
        else if(m>=8)  setHand("Full House","9x",new String[]{"K","K","K","Q","Q"},new int[]{0,1,2,0,1},new String[]{"♠","♥","♦","♣","♥"},true);
        else if(m>=5)  setHand("Flush","6x",new String[]{"A","J","8","5","3"},new int[]{1,1,1,1,1},new String[]{"♥","♥","♥","♥","♥"},true);
        else if(m>=3.5)setHand("Straight","4x",new String[]{"9","8","7","6","5"},new int[]{0,1,2,3,1},new String[]{"♠","♥","♦","♣","♥"},false);
        else if(m>=2.5)setHand("Three of a Kind","3x",new String[]{"Q","Q","Q","7","3"},new int[]{1,2,3,0,1},new String[]{"♥","♦","♣","♠","♥"},false);
        else if(m>=1.8)setHand("Two Pair","2x",new String[]{"J","J","8","8","A"},new int[]{0,1,2,3,0},new String[]{"♠","♥","♣","♦","♠"},false);
        else           setHand("One Pair","1x",new String[]{"10","10","K","7","3"},new int[]{0,1,2,3,0},new String[]{"♠","♥","♦","♣","♠"},false);
    }

    private void setHand(String lbl,String mult,String[] ranks,int[] reds,String[] suits,boolean allGlow){
        for(int i=0;i<CARD_COUNT;i++){faceRank[i]=ranks[i];faceSuit[i]=suits[i];faceRed[i]=reds[i]%2;glowing[i]=allGlow;}
        handLabel=lbl;multLabel=mult;handColor=0xFF34D399;
    }

    private void buildLoseHand(){
        String[][] c={{"A","K","Q","J","9"},{"K","Q","J","9","7"},{"A","10","8","6","3"}};
        String[] combo=c[(int)(Math.random()*c.length)];
        int[] si={0,1,2,3,0};
        for(int i=0;i<CARD_COUNT;i++){faceRank[i]=combo[i];faceSuit[i]=SUITS[si[i]];faceRed[i]=(si[i]==1||si[i]==2)?1:0;glowing[i]=false;}
    }

    private void revealCards(){
        for(int i=0;i<CARD_COUNT;i++){
            final int idx=i;
            ValueAnimator flip=ValueAnimator.ofFloat(0f,1f);
            flip.setDuration(300);flip.setStartDelay(idx*180L);
            flip.setInterpolator(new DecelerateInterpolator(1.4f));
            flip.addUpdateListener(v->{flipFrac[idx]=(float)v.getAnimatedValue();invalidate();});
            flip.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationStart(android.animation.Animator anim){
                    SoundManager.get().playCardFlip();
                }
            });
            if(i==CARD_COUNT-1){
                flip.addListener(new AnimatorListenerAdapter(){
                    @Override public void onAnimationEnd(Animator anim){showHandLabel();}
                });
            }
            flip.start();
        }
    }

    private void showHandLabel(){
        if(jackpot||win){
            if(glowAnim!=null) glowAnim.cancel();
            glowAnim=ValueAnimator.ofFloat(0f,1f,.4f,1f);
            glowAnim.setDuration(1000);glowAnim.setRepeatCount(ValueAnimator.INFINITE);
            glowAnim.addUpdateListener(v->{glowAlpha=(float)v.getAnimatedValue();invalidate();});
            glowAnim.start();
        }
        if(jackpot){showConf=true;startConfetti();}
        if(jackpot||win){startShimmer();}

        ValueAnimator fade=ValueAnimator.ofFloat(0f,1f);
        fade.setDuration(650);
        fade.addUpdateListener(v->{handAlpha=(float)v.getAnimatedValue();invalidate();});
        fade.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator anim){if(pendingDone!=null)pendingDone.run();}
        });
        fade.start();
    }

    private void startShimmer(){
        if(shimAnim!=null) shimAnim.cancel();
        shimAnim=ValueAnimator.ofFloat(-getWidth()*.2f,getWidth()*1.2f);
        shimAnim.setDuration(1100);shimAnim.setRepeatCount(3);
        shimAnim.addUpdateListener(v->{shimmerX=(float)v.getAnimatedValue();invalidate();});
        shimAnim.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator a){shimmerX=-1f;}
        });
        shimAnim.start();
    }

    private void startConfetti(){
        confProg=0f;
        if(confA!=null) confA.cancel();
        confA=ValueAnimator.ofFloat(0f,1.3f);confA.setDuration(2200);confA.setRepeatCount(ValueAnimator.INFINITE);
        confA.addUpdateListener(v->{confProg=(float)v.getAnimatedValue();invalidate();});
        confA.start();
    }

    public void reset(){
        if(glowAnim!=null){glowAnim.cancel();glowAnim=null;}
        if(shimAnim!=null){shimAnim.cancel();shimAnim=null;}
        if(confA!=null){confA.cancel();confA=null;}
        handLabel="";handAlpha=0f;glowAlpha=0f;shimmerX=-1f;showConf=false;confProg=0f;
        for(int i=0;i<CARD_COUNT;i++){slideIn[i]=0f;flipFrac[i]=0f;glowing[i]=false;faceRank[i]=null;}
        invalidate();
    }
}
