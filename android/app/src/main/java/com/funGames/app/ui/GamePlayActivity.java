package com.funGames.app.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.funGames.app.R;
import com.funGames.app.model.Game;
import com.funGames.app.ui.games.LuckyWheelView;
import com.funGames.app.ui.games.SlotMachineView;
import com.funGames.app.ui.games.RouletteView;
import com.funGames.app.ui.games.PokerView;
import com.funGames.app.util.BetHistory;
import com.funGames.app.util.SoundManager;
import com.funGames.app.util.ThemeManager;
import com.funGames.app.ui.ShakeErrorView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.funGames.app.ui.AchievementToastView;
import com.funGames.app.ui.ParticleSystem;
import com.funGames.app.ui.StatsActivity;
import com.funGames.app.util.PlayerProfile;

import com.funGames.app.util.Session;
import com.funGames.app.util.Validators;

import java.util.Locale;

public class GamePlayActivity extends AppCompatActivity {

    public static final String EXTRA_GAME_TRANSPORT = "game_transport";

    private Session session;
    private Game    game;

    // Header
    private TextView     tvGameTitle, tvRiskBadge, tvJackpotBadge, tvBetRange, tvStatus;
    private AnimatedBalanceTextView tvBalance;
    private Button       btnSpin, btnBack;
    private EditText     etBet;
    private FrameLayout  gameCanvas;

    // Quick-bet buttons
    private Button btnBetMin, btnBetHalf, btnBetDouble, btnBetMax;
    private Button btnSoundToggle, btnBetCalc;

    // Extra overlay views
    private WinStreakBannerView   streakBanner;
    private ConnectionStatusView  connStatus;
    private BetHistoryView        historyView;
    private View                  historyPanel;

    // Game views
    private LuckyWheelView  luckyWheelView;
    private SlotMachineView slotMachineView;
    private RouletteView    rouletteView;
    private PokerView       pokerView;

    private boolean spinning = false;
    private ParticleSystem particles;
    private AchievementToastView achToast;
    private android.view.View particleOverlay;
    // shake sensor
    private SensorManager sensorMgr;
    private Sensor accel;
    private long lastShake = 0;
    private Vibrator vibrator;
    private ThemeManager themeManager;

    public static void launch(android.content.Context ctx, String transport) {
        android.content.Intent i = new android.content.Intent(ctx, GamePlayActivity.class);
        i.putExtra(EXTRA_GAME_TRANSPORT, transport);
        ctx.startActivity(i);
        if (ctx instanceof android.app.Activity) {
            ((android.app.Activity) ctx).overridePendingTransition(
                    R.anim.card_flip_enter, R.anim.card_flip_exit);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_play);

        session  = Session.get();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Game-specific ambient — started after game type is determined in setupGameView()
        // (called from there)

        if (!session.isInitialized()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish(); return;
        }

        String transport = getIntent().getStringExtra(EXTRA_GAME_TRANSPORT);
        if (transport == null) { finish(); return; }
        try { game = Game.fromTransportString(transport); }
        catch (Exception e) {
            Toast.makeText(this, "Could not load game", Toast.LENGTH_SHORT).show();
            finish(); return;
        }

        bindViews();
        populateHeader();
        buildGameView();
        setupButtons();
        refreshBalance(false);
        checkConnection();
        initParticles();
        initShakeSensor();
        initTheme();
    }

    private void initTheme() {
        themeManager = ThemeManager.get();
        if (game != null) themeManager.applyTheme(game.getRiskLevel());
        themeManager.setListener((accent, soft, tint) -> {
            // Update spin button color
            if (btnSpin != null) btnSpin.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(accent));
        });
    }


    private void initParticles(){
        particles = new ParticleSystem();
        // Transparent overlay drawn on top of everything inside the root FrameLayout
        particleOverlay = new android.view.View(this){
            @Override protected void onDraw(android.graphics.Canvas c){
                if(particles != null && particles.isAlive()){
                    particles.draw(c);
                    postInvalidateOnAnimation();
                }
            }
        };
        particleOverlay.setBackgroundColor(0x00000000);
        // Walk up to the root FrameLayout (activity_game_play root)
        android.view.ViewGroup rootView = (android.view.ViewGroup) getWindow().getDecorView()
                .findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.addView(particleOverlay, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        // achievement toast
        achToast = new AchievementToastView(this);
        if (rootView != null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, 110);
            lp.topMargin = 80;
            rootView.addView(achToast, lp);
        }
    }

    private void initShakeSensor(){
        sensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if(sensorMgr != null){
            accel = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorMgr.registerListener(new SensorEventListener(){
                @Override public void onSensorChanged(SensorEvent e){
                    float x=e.values[0],y=e.values[1],z=e.values[2];
                    float grav = SensorManager.GRAVITY_EARTH;
                    double force = Math.sqrt(x*x+y*y+z*z)/grav;
                    if(force > 2.2){
                        long now = System.currentTimeMillis();
                        if(now - lastShake > 1500){
                            lastShake = now;
                            runOnUiThread(()-> { if(!spinning) attemptPlay(); });
                        }
                    }
                }
                @Override public void onAccuracyChanged(Sensor s, int a){}
            }, accel, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    // ── bind ──────────────────────────────────────────────────────────
    private void bindViews() {
        tvGameTitle    = findViewById(R.id.tvGameTitle);
        tvRiskBadge    = findViewById(R.id.tvRiskBadge);
        tvJackpotBadge = findViewById(R.id.tvJackpotBadge);
        tvBetRange     = findViewById(R.id.tvBetRange);
        tvBalance      = findViewById(R.id.tvBalance);
        tvStatus       = findViewById(R.id.tvStatus);
        etBet          = findViewById(R.id.etBetAmount);
        btnSpin        = findViewById(R.id.btnSpin);
        btnBack        = findViewById(R.id.btnBack);
        gameCanvas     = findViewById(R.id.gameCanvas);
        btnBetMin      = findViewById(R.id.btnBetMin);
        btnBetHalf     = findViewById(R.id.btnBetHalf);
        btnBetDouble   = findViewById(R.id.btnBetDouble);
        btnBetMax      = findViewById(R.id.btnBetMax);
        btnSoundToggle = findViewById(R.id.btnSoundToggle);
        btnBetCalc     = findViewById(R.id.btnBetCalc);
        streakBanner   = findViewById(R.id.streakBanner);
        connStatus     = findViewById(R.id.connStatus);
        historyPanel   = findViewById(R.id.historyPanel);
        historyView    = findViewById(R.id.historyView);
    }

    private void populateHeader() {
        tvGameTitle.setText(game.getGameName());
        String risk = game.getRiskLevel() == null ? "low" : game.getRiskLevel().toLowerCase(Locale.US);
        tvRiskBadge.setText(risk.toUpperCase(Locale.US) + " RISK");
        switch (risk) {
            case "high":   tvRiskBadge.setTextColor(getColor(R.color.risk_high));   break;
            case "medium": tvRiskBadge.setTextColor(getColor(R.color.risk_medium)); break;
            default:       tvRiskBadge.setTextColor(getColor(R.color.risk_low));    break;
        }
        tvJackpotBadge.setText(String.format(Locale.US, "JACKPOT %.0fx", game.getJackpot()));
        tvBetRange.setText(String.format(Locale.US, "%.2f – %.2f FUN", game.getMinBet(), game.getMaxBet()));
    }

    // ── game view ─────────────────────────────────────────────────────
    private void buildGameView() {
        String name = game.getGameName() == null ? "" : game.getGameName().toLowerCase(Locale.US);
        if (name.contains("wheel")) {
            luckyWheelView = new LuckyWheelView(this);
            addGameView(luckyWheelView); btnSpin.setText("SPIN");
            SoundManager.get().playGameAmbient("wheel");
        } else if (name.contains("slot")) {
            slotMachineView = new SlotMachineView(this);
            addGameView(slotMachineView); btnSpin.setText("PULL");
            SoundManager.get().playGameAmbient("slot");
        } else if (name.contains("roulette")) {
            rouletteView = new RouletteView(this);
            rouletteView.setBetChangeListener((t, n) ->
                    tvStatus.setText("Bet: " + describeBet(t, n)));
            addGameView(rouletteView); btnSpin.setText("SPIN");
            SoundManager.get().playGameAmbient("roulette");
            tvStatus.setText("Tap the table to choose your bet");
        } else if (name.contains("poker")) {
            pokerView = new PokerView(this);
            addGameView(pokerView); btnSpin.setText("DEAL");
            SoundManager.get().playGameAmbient("poker");
        } else {
            luckyWheelView = new LuckyWheelView(this);
            addGameView(luckyWheelView); btnSpin.setText("PLAY");
        }
    }

    private void addGameView(View v) {
        gameCanvas.addView(v, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private String describeBet(RouletteView.BetType t, int n) {
        switch (t) {
            case RED:    return "RED (2x)";
            case BLACK:  return "BLACK (2x)";
            case ODD:    return "ODD (2x)";
            case EVEN:   return "EVEN (2x)";
            case LOW:    return "1-18 (2x)";
            case HIGH:   return "19-36 (2x)";
            case DOZEN1: return "1st Dozen (3x)";
            case DOZEN2: return "2nd Dozen (3x)";
            case DOZEN3: return "3rd Dozen (3x)";
            case STRAIGHT: return "Number " + n + " (36x)";
            default:     return "";
        }
    }

    // ── buttons ───────────────────────────────────────────────────────
    private void setupButtons() {
        btnBack.setOnClickListener(v -> finish());
        btnSpin.setOnClickListener(v -> { SoundManager.get().playClick(); attemptPlay(); });

        // Quick-bet buttons
        btnBetMin.setOnClickListener(v -> { SoundManager.get().playClick();
            etBet.setText(String.format(Locale.US, "%.2f", game.getMinBet())); });
        btnBetHalf.setOnClickListener(v -> { SoundManager.get().playClick();
            double cur = parseBetField();
            if (cur > 0) etBet.setText(String.format(Locale.US, "%.2f", Math.max(game.getMinBet(), cur / 2)));
        });
        btnBetDouble.setOnClickListener(v -> { SoundManager.get().playClick();
            double cur = parseBetField();
            if (cur > 0) etBet.setText(String.format(Locale.US, "%.2f", Math.min(game.getMaxBet(), cur * 2)));
        });
        btnBetMax.setOnClickListener(v ->
                etBet.setText(String.format(Locale.US, "%.2f", game.getMaxBet())));

        // Sound toggle
        updateSoundToggleIcon();
        btnSoundToggle.setOnClickListener(v -> {
            boolean nowEnabled = !SoundManager.get().isEnabled();
            SoundManager.get().setEnabled(nowEnabled);
            updateSoundToggleIcon();
            if (nowEnabled) SoundManager.get().playClick();
        });

        // Bet calculator
        btnBetCalc.setOnClickListener(v -> {
            SoundManager.get().playClick();
            showBetCalculator();
        });
        Button btnStats = findViewById(R.id.btnStats);
        if (btnStats != null) {
            btnStats.setOnClickListener(v -> StatsActivity.start(this));
        }

        // History toggle
        Button btnHist = findViewById(R.id.btnHistory);
        if (btnHist != null) {
            btnHist.setOnClickListener(v -> {
                if (historyPanel.getVisibility() == View.VISIBLE) {
                    historyPanel.setVisibility(View.GONE);
                } else {
                    historyView.refresh();
                    historyPanel.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private double parseBetField() {
        try { return Double.parseDouble(etBet.getText().toString()); }
        catch (Exception e) { return 0; }
    }

    // ── connection check ──────────────────────────────────────────────
    private void checkConnection() {
        if (connStatus == null) return;
        connStatus.setConnecting();
        long start = System.currentTimeMillis();
        session.getClient().checkAnyGameAsync((ok, payload) -> {
            long ms = System.currentTimeMillis() - start;
            boolean connected = ok || !payload.startsWith("Network error");
            if (connected) connStatus.setOnline(ms);
            else           connStatus.setOffline();
        });
    }

    // ── balance ───────────────────────────────────────────────────────
    private void refreshBalance(boolean animate) {
        if (tvBalance == null) return;
        if (animate) tvBalance.animateTo(session.getBalance());
        else         tvBalance.setValueImmediate(session.getBalance());
    }

    // ── play flow ─────────────────────────────────────────────────────
    private void attemptPlay() {
        if (spinning) return;

        if (rouletteView != null &&
                rouletteView.getSelectedBet() == RouletteView.BetType.NONE) {
            Toast.makeText(this, "Please select a bet type on the table first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Double bet = Validators.parseDecimal(etBet.getText().toString());
        if (bet == null || bet <= 0) {
            ShakeErrorView.shake(etBet, getString(R.string.msg_bet_positive));
            return;
        }
        if (bet < game.getMinBet()) {
            ShakeErrorView.shake(etBet, getString(R.string.msg_bet_too_low));
            return;
        }
        if (bet > game.getMaxBet()) {
            ShakeErrorView.shake(etBet, getString(R.string.msg_bet_too_high));
            return;
        }
        if (bet > session.getBalance()) {
            ShakeErrorView.shake(etBet, getString(R.string.msg_bet_over_balance));
            return;
        }

        final double betAmount = bet;
        setSpinning(true);
        tvStatus.setText("⏳ Waiting for server…");
        historyPanel.setVisibility(View.GONE);

        // Haptic: short buzz when spin starts
        vibrate(40);
        // Game-specific spin sound
        if (slotMachineView != null)       SoundManager.get().playSlotReels();
        else if (rouletteView != null)     SoundManager.get().playRouletteBall();
        else                               SoundManager.get().playSpin();

        startAnimation();

        long reqStart = System.currentTimeMillis();
        session.getClient().playAsync(
                session.getPlayerId(),
                game.getGameName(),
                betAmount,
                (ok, payload) -> {
                    long ms = System.currentTimeMillis() - reqStart;
                    if (connStatus != null) connStatus.setOnline(ms);

                    double result = 0.0;
                    if (ok) {
                        try { result = Double.parseDouble(payload.replace(',', '.')); }
                        catch (NumberFormatException ignored) {}
                    }
                    final double finalResult = result;

                    if (ok) {
                        session.addToBalance(-betAmount + finalResult);
                        BetHistory.get().add(game.getGameName(), betAmount, finalResult);
                        refreshBalance(true);
                        deliverResult(finalResult, betAmount);
                    } else {
                        setSpinning(false);
                        tvStatus.setText("");
                        Toast.makeText(this, "Server: " + payload, Toast.LENGTH_LONG).show();
                        // Retry suggestion
                        new AlertDialog.Builder(this)
                                .setTitle("Connection Issue")
                                .setMessage("Server: " + payload + "\n\nRetry?")
                                .setPositiveButton("Retry", (d, w) -> attemptPlay())
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
    }

    private void startAnimation() {
        if (luckyWheelView  != null) luckyWheelView.startSpin();
        if (slotMachineView != null) slotMachineView.startSpin();
        if (rouletteView    != null) rouletteView.startSpin();
        if (pokerView       != null) pokerView.startDeal();
    }

    private void deliverResult(double result, double bet) {
        boolean win     = result >= bet && result > 0;
        boolean jackpot = result > 0 && result == bet * game.getJackpot();

        if (luckyWheelView  != null) luckyWheelView.stopSpin(win, jackpot, () -> onAnimDone(result, bet));
        if (slotMachineView != null) slotMachineView.stopSpin(win, jackpot, result, bet, () -> onAnimDone(result, bet));
        if (rouletteView    != null) rouletteView.stopSpin(win, jackpot, result, bet, () -> onAnimDone(result, bet));
        if (pokerView       != null) pokerView.stopDeal(win, jackpot, result, bet, () -> onAnimDone(result, bet));
    }

    private void onAnimDone(double result, double bet) {
        setSpinning(false);

        boolean win     = result >= bet && result > 0;
        boolean jackpot = result > 0 && result == bet * game.getJackpot();

        // Sound feedback
        SoundManager.get().stopSpin();
        if (jackpot)    SoundManager.get().playJackpot();
        else if (win)   SoundManager.get().playWin();
        else            SoundManager.get().playLose();

        // Haptic feedback
        // Fire particles
        if(particles!=null && particleOverlay!=null){
            int cx = gameCanvas.getWidth()/2;
            int cy = gameCanvas.getHeight()/2;
            particles.burst(cx, cy, gameCanvas.getWidth(), gameCanvas.getHeight(), jackpot);
            particleOverlay.invalidate();
        }
        // Achievements
        if(achToast != null){
            java.util.List<String> newAchs = PlayerProfile.get(this)
                    .recordBet(this, game.getGameName(), bet, result, jackpot);
            if(!newAchs.isEmpty()){
                for(PlayerProfile.Achievement ach : PlayerProfile.ALL_ACHIEVEMENTS){
                    for(String id : newAchs){
                        if(ach.id.equals(id)){ achToast.show(ach); break; }
                    }
                }
            }
        }
        if (jackpot)    vibrate(500);
        else if (win)   vibratePulse();
        else            vibrate(80);

        // Win streak banner
        int streak = BetHistory.get().getStreak();
        if (win && streak >= 2 && streakBanner != null) {
            streakBanner.show(streak);
        }

        // Build dialog
        String title, body;
        if (jackpot) {
            title = "🎰  JACKPOT!";
            tvStatus.setText("🎰 JACKPOT!");
            body = String.format(Locale.US,
                    "Bet: %.2f FUN\nReturn: %.2f FUN\nNet: +%.2f FUN\n\n🏆 JACKPOT WIN!",
                    bet, result, result - bet);
        } else if (win) {
            double net = result - bet;
            if (net < 0.001) {
                // Break-even (multiplier = 1.0 exactly)
                title = "↩️  Break Even";
                tvStatus.setText("Break even — your bet returned");
                body = String.format(Locale.US,
                        "Bet: %.2f FUN\nReturn: %.2f FUN\nNet: 0.00 FUN\n\nYou got your bet back!",
                        bet, result);
            } else {
                title = "✨  You Win!";
                tvStatus.setText("✨ You Win!  +" + String.format(Locale.US, "%.2f", net) + " FUN");
                body = String.format(Locale.US,
                        "Bet: %.2f FUN\nReturn: %.2f FUN\nNet: +%.2f FUN",
                        bet, result, net);
                if (streak >= 2) body += "\n\n🔥 " + streak + " wins in a row!";
            }
        } else if (result > 0) {
            title = "💸  Partial Return";
            tvStatus.setText("Partial return  -" + String.format(Locale.US, "%.2f", bet - result) + " FUN");
            body = String.format(Locale.US,
                    "Bet: %.2f FUN\nReturn: %.2f FUN\nNet: -%.2f FUN\n\nYou got some back — try again!",
                    bet, result, bet - result);
        } else {
            title = "❌  No Win";
            tvStatus.setText("No win — try again!");
            body = String.format(Locale.US,
                    "Bet: %.2f FUN\nReturn: 0.00 FUN\nNet: -%.2f FUN\n\nBetter luck next time!", bet, bet);
        }

        body += String.format(Locale.US,
                "\n\nBalance: %.2f FUN\nWin Rate: %.0f%% (%d/%d)",
                session.getBalance(),
                BetHistory.get().getWinRate(),
                BetHistory.get().getWins(),
                BetHistory.get().getTotal());

        showPremiumResultDialog(title, body, win || jackpot, jackpot);
    }

    private void showPremiumResultDialog(String title, String body, boolean win, boolean jackpot) {
        android.app.Dialog d = new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setCancelable(false);

        // Build dialog view programmatically
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, 0);

        // Background
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32f);
        bg.setColor(jackpot ? 0xFF12092A : win ? 0xFF081820 : 0xFF180808);
        bg.setStroke(2, jackpot ? 0xFFFFD84A : win ? 0xFF2DD4BF : 0xFFF87171);
        root.setBackground(bg);

        // Accent bar at top
        android.view.View bar = new android.view.View(this);
        android.widget.LinearLayout.LayoutParams barLp =
                new android.widget.LinearLayout.LayoutParams(-1, (int)(6 * getResources().getDisplayMetrics().density));
        bar.setBackgroundColor(jackpot ? 0xFFFFD84A : win ? 0xFF2DD4BF : 0xFFF87171);
        android.graphics.drawable.GradientDrawable barBg = new android.graphics.drawable.GradientDrawable();
        barBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        barBg.setCornerRadii(new float[]{32,32,32,32,0,0,0,0});
        barBg.setColor(jackpot ? 0xFFFFD84A : win ? 0xFF2DD4BF : 0xFFF87171);
        bar.setBackground(barBg);
        root.addView(bar, barLp);

        int pad = (int)(24 * getResources().getDisplayMetrics().density);

        // Title
        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(jackpot ? 0xFFFFD84A : win ? 0xFF2DD4BF : 0xFFF87171);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
        tvTitle.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        tvTitle.setPadding(pad, pad, pad, pad/2);
        if (jackpot || win) {
            tvTitle.setShadowLayer(18f, 0, 0, jackpot ? 0x88FFD84A : 0x882DD4BF);
        }
        root.addView(tvTitle);

        // Divider
        android.view.View div = new android.view.View(this);
        div.setBackgroundColor(jackpot ? 0x33FFD84A : win ? 0x332DD4BF : 0x33F87171);
        root.addView(div, new android.widget.LinearLayout.LayoutParams(-1, 1));

        // Body
        android.widget.TextView tvBody = new android.widget.TextView(this);
        tvBody.setText(body);
        tvBody.setTextColor(0xCCE8EAF6);
        tvBody.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        tvBody.setLineSpacing(6f, 1f);
        tvBody.setPadding(pad, pad, pad, pad);
        root.addView(tvBody);

        // Buttons row
        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnRow.setPadding(pad/2, 0, pad/2, pad);
        btnRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        android.widget.Button btnHistory = makeDialogBtn("HISTORY", 0xFF1A2030);
        android.widget.Button btnBack    = makeDialogBtn("BACK",    0xFF1A2030);
        android.widget.Button btnAgain   = makeDialogBtn(jackpot ? "CLAIM 🏆" : "PLAY AGAIN",
                jackpot ? 0xFFB8920A : win ? 0xFF0F5040 : 0xFF3A1010);
        if (jackpot || win) {
            ((android.graphics.drawable.GradientDrawable)btnAgain.getBackground())
                    .setStroke(2, jackpot ? 0xFFFFD84A : 0xFF2DD4BF);
            btnAgain.setTextColor(jackpot ? 0xFFFFD84A : 0xFF2DD4BF);
        } else {
            btnAgain.setTextColor(0xFFF87171);
        }

        btnHistory.setOnClickListener(v -> {
            d.dismiss();
            historyView.refresh();
            historyPanel.setVisibility(android.view.View.VISIBLE);
        });
        btnBack.setOnClickListener(v -> { d.dismiss(); finish(); });
        btnAgain.setOnClickListener(v -> {
            d.dismiss();
            tvStatus.setText(rouletteView != null ? "Tap the table to choose your bet" : "");
            resetView();
        });

        android.widget.LinearLayout.LayoutParams btnLp =
                new android.widget.LinearLayout.LayoutParams(0, (int)(44 * getResources().getDisplayMetrics().density), 1f);
        btnLp.setMargins(8, 0, 8, 0);
        btnRow.addView(btnHistory, btnLp);
        btnRow.addView(btnBack,    new android.widget.LinearLayout.LayoutParams(0, (int)(44 * getResources().getDisplayMetrics().density), 1f) {{ setMargins(8,0,8,0); }});
        btnRow.addView(btnAgain,   new android.widget.LinearLayout.LayoutParams(0, (int)(44 * getResources().getDisplayMetrics().density), 1f) {{ setMargins(8,0,8,0); }});
        root.addView(btnRow);

        d.setContentView(root);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.88f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // Entrance animation
        root.setAlpha(0f);
        root.setScaleX(0.85f);
        root.setScaleY(0.85f);
        d.show();
        root.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                .start();
    }

    private android.widget.Button makeDialogBtn(String text, int bgColor) {
        android.widget.Button btn = new android.widget.Button(this);
        btn.setText(text);
        btn.setTextColor(0xAAB4B9D4);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        btn.setAllCaps(true);
        btn.setPadding(0, 0, 0, 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(22f);
        bg.setColor(bgColor);
        btn.setBackground(bg);
        return btn;
    }

    private void resetView() {
        if (luckyWheelView  != null) luckyWheelView.reset();
        if (slotMachineView != null) slotMachineView.reset();
        if (rouletteView    != null) rouletteView.reset();
        if (pokerView       != null) pokerView.reset();
    }

    private void setSpinning(boolean s) {
        spinning = s;
        btnSpin.setEnabled(!s);
        btnSpin.setAlpha(s ? 0.45f : 1.0f);
        etBet.setEnabled(!s);
        btnBetMin.setEnabled(!s);
        btnBetHalf.setEnabled(!s);
        btnBetDouble.setEnabled(!s);
        btnBetMax.setEnabled(!s);
    }

    // ── haptics ───────────────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                        ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void vibratePulse() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(
                        new long[]{0, 60, 80, 60}, -1));
            } else {
                vibrator.vibrate(200);
            }
        } catch (Exception ignored) {}
    }

    private void updateSoundToggleIcon() {
        if (btnSoundToggle == null) return;
        btnSoundToggle.setText(SoundManager.get().isEnabled() ? "🔊" : "🔇");
        btnSoundToggle.setAlpha(SoundManager.get().isEnabled() ? 1.0f : 0.5f);
    }

    private void showBetCalculator() {
        double bet = parseBetField();
        if (bet <= 0) bet = game.getMinBet();

        double[] table = game.getMultiplierTable();
        double jackpot  = game.getJackpot();
        final double betFinal = bet;

        // Build dialog programmatically
        android.app.Dialog d = new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28f);
        bg.setColor(0xFF080A18);
        bg.setStroke(2, 0xFFFFD84A);
        root.setBackground(bg);

        // Title
        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText(String.format(java.util.Locale.US,
                "🧮 Bet Calculator  —  %.2f FUN", betFinal));
        tvTitle.setTextColor(0xFFFFD84A);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        tvTitle.setPadding(0, 0, 0, pad);
        root.addView(tvTitle);

        // Header row
        root.addView(makeCalcRow("Outcome", "Multiplier", "Return", true));

        // Jackpot row (1% chance)
        root.addView(makeCalcRow(
                "🎰 JACKPOT (1%)",
                String.format(java.util.Locale.US, "%.0fx", jackpot),
                String.format(java.util.Locale.US, "+%.2f FUN", betFinal * jackpot - betFinal),
                false));

        // Divider
        android.view.View div = new android.view.View(this);
        div.setBackgroundColor(0x33FFD84A);
        root.addView(div, new android.widget.LinearLayout.LayoutParams(-1, 1));

        // Multiplier table rows — group identical multipliers
        java.util.LinkedHashMap<Double, Integer> groups = new java.util.LinkedHashMap<>();
        for (double m : table) groups.merge(m, 1, Integer::sum);

        for (java.util.Map.Entry<Double, Integer> e : groups.entrySet()) {
            double mult = e.getKey();
            int count   = e.getValue();
            int pct     = count * 10; // each index = 10% chance

            String outcome;
            int textColor;
            if (mult == 0.0) {
                outcome = String.format("❌ No win (%d%%)", pct);
                textColor = 0xFFF87171;
            } else if (mult < 1.0) {
                outcome = String.format("💸 Partial (%d%%)", pct);
                textColor = 0xFFFFD84A;
            } else if (mult == 1.0) {
                outcome = String.format("↩️ Break even (%d%%)", pct);
                textColor = 0xFF9999BB;
            } else {
                outcome = String.format("✨ Win (%d%%)", pct);
                textColor = 0xFF34D399;
            }

            double net = betFinal * mult - betFinal;
            String netStr = net >= 0
                    ? String.format(java.util.Locale.US, "+%.2f FUN", net)
                    : String.format(java.util.Locale.US, "%.2f FUN", net);

            android.widget.LinearLayout row = makeCalcRow(
                    outcome,
                    String.format(java.util.Locale.US, "%.1fx", mult),
                    netStr, false);

            // Color the net column
            android.widget.TextView tvNet =
                    (android.widget.TextView) row.getChildAt(2);
            tvNet.setTextColor(textColor);
            root.addView(row);
        }

        // Expected value
        double ev = 0;
        for (double m : table) ev += m * betFinal / table.length;
        ev += betFinal * jackpot * 0.01; // jackpot 1%
        ev -= betFinal * 0.99;           // subtract bet (minus jackpot cases)
        android.view.View div2 = new android.view.View(this);
        div2.setBackgroundColor(0x33FFD84A);
        root.addView(div2, new android.widget.LinearLayout.LayoutParams(-1, 1));
        android.widget.TextView tvEV = new android.widget.TextView(this);
        double evFinal = ev;
        tvEV.setText(String.format(java.util.Locale.US,
                "Expected value: %+.2f FUN per bet", evFinal));
        tvEV.setTextColor(ev >= 0 ? 0xFF34D399 : 0xFF9999BB);
        tvEV.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        tvEV.setPadding(0, pad / 2, 0, 0);
        root.addView(tvEV);

        // Close button
        android.widget.Button btnClose = new android.widget.Button(this);
        btnClose.setText("CLOSE");
        btnClose.setTextColor(0xFFFFD84A);
        btnClose.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        btnClose.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        android.graphics.drawable.GradientDrawable btnBg =
                new android.graphics.drawable.GradientDrawable();
        btnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(20f);
        btnBg.setColor(0xFF1A1400);
        btnBg.setStroke(1, 0xFFFFD84A);
        btnClose.setBackground(btnBg);
        android.widget.LinearLayout.LayoutParams btnLp =
                new android.widget.LinearLayout.LayoutParams(-1,
                        (int)(44 * getResources().getDisplayMetrics().density));
        btnLp.topMargin = pad;
        btnClose.setOnClickListener(v -> d.dismiss());
        root.addView(btnClose, btnLp);

        d.setContentView(root);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    private android.widget.LinearLayout makeCalcRow(
            String col1, String col2, String col3, boolean isHeader) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int vPad = (int)(5 * getResources().getDisplayMetrics().density);
        row.setPadding(0, vPad, 0, vPad);

        int color  = isHeader ? 0xFF888899 : 0xFFCCCCDD;
        int tsInt  = isHeader ? 10 : 12;
        int style  = isHeader
                ? android.graphics.Typeface.NORMAL
                : android.graphics.Typeface.NORMAL;

        android.widget.TextView tv1 = new android.widget.TextView(this);
        tv1.setText(col1); tv1.setTextColor(color);
        tv1.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tsInt);
        tv1.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, style));
        row.addView(tv1, new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2.2f));

        android.widget.TextView tv2 = new android.widget.TextView(this);
        tv2.setText(col2); tv2.setTextColor(color); tv2.setGravity(android.view.Gravity.END);
        tv2.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tsInt);
        tv2.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, style));
        row.addView(tv2, new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));

        android.widget.TextView tv3 = new android.widget.TextView(this);
        tv3.setText(col3); tv3.setTextColor(color); tv3.setGravity(android.view.Gravity.END);
        tv3.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tsInt);
        tv3.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                isHeader ? style : android.graphics.Typeface.BOLD));
        row.addView(tv3, new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f));

        return row;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SoundManager.get().stopAmbient();
        SoundManager.get().stopSpin();
    }
}