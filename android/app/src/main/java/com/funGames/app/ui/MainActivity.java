package com.funGames.app.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import com.funGames.app.ui.GamePlayActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.funGames.app.R;
import com.funGames.app.model.Game;
import com.funGames.app.net.MasterClient;
import com.funGames.app.util.Session;
import com.funGames.app.util.Validators;

import java.util.List;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.funGames.app.ui.AnimatedBalanceTextView;
import com.funGames.app.ui.ConnectionStatusView;
import com.funGames.app.ui.SkeletonLoadingView;
import com.funGames.app.util.BetHistory;
import com.funGames.app.util.SoundManager;
import com.funGames.app.util.DailyBonus;
import java.util.ArrayList;
import java.util.Collections;

import java.util.Locale;
import com.funGames.app.ui.NaturalLanguageSearchView;
import com.funGames.app.ui.AnimatedStarRatingView;
import com.funGames.app.ui.LiveTickerView;

/**
 * Main screen. Holds filter chips, triggers the async search, renders
 * results in a RecyclerView, and routes row actions to the play / rate
 * dialogs. Also owns the add-balance button on the top bar.
 *
 * All network calls go through {@link MasterClient}; their callbacks run
 * on the main thread (MasterClient posts via a Handler), so updating
 * views here is safe.
 */
public class MainActivity extends AppCompatActivity
        implements GamesAdapter.GameActionListener {

    // ---- View state ----
    private TextView tvPlayerId, tvEmpty;
    private AnimatedBalanceTextView tvBalance;
    private SkeletonLoadingView skeletonLoader;
    private LinearLayout dailyBonusBanner;
    private View readyState;
    private ProgressBar progress;
    private RecyclerView rvGames;
    private GamesAdapter adapter;

    // Filter chips
    private TextView chipBetAny, chipBetDollar, chipBetDollar2, chipBetDollar3;
    private TextView chipRiskAny, chipRiskLow, chipRiskMed, chipRiskHigh;
    private TextView chipStarAny, chipStar1, chipStar2, chipStar3, chipStar4, chipStar5;

    // Selected values
    private String selectedBetCat  = "ANY";
    private String selectedRisk    = "ANY";
    private String selectedMinStars = "0";

    private Session session;
    private com.funGames.app.ui.LiveTickerView liveTicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        session = Session.get();
        if (!session.isInitialized()) {
            // Defensive: if Android killed us and restored this Activity, go back to login.
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        bindViews();
        setupChips();
        setupRecycler();
        setupButtons();
        refreshBalance();
        tvPlayerId.setText("Player " + session.getPlayerId());
        bindExtraViews();
        checkDailyBonus();
        checkServerConnection();
        subscribeToJackpots(); // NEW
    }

    // -------------------------------------------------------------------
    // View wiring
    // -------------------------------------------------------------------
    private void bindViews() {
        tvPlayerId = findViewById(R.id.tvPlayerId);
        tvBalance  = (AnimatedBalanceTextView) findViewById(R.id.tvBalance);
        tvEmpty    = findViewById(R.id.tvEmpty);
        progress   = findViewById(R.id.progress);
        rvGames    = findViewById(R.id.rvGames);

        chipBetAny     = findViewById(R.id.chipBetAny);
        chipBetDollar  = findViewById(R.id.chipBetDollar);
        chipBetDollar2 = findViewById(R.id.chipBetDollar2);
        chipBetDollar3 = findViewById(R.id.chipBetDollar3);

        chipRiskAny  = findViewById(R.id.chipRiskAny);
        chipRiskLow  = findViewById(R.id.chipRiskLow);
        chipRiskMed  = findViewById(R.id.chipRiskMed);
        chipRiskHigh = findViewById(R.id.chipRiskHigh);

        chipStarAny = findViewById(R.id.chipStarAny);
        chipStar1   = findViewById(R.id.chipStar1);
        chipStar2   = findViewById(R.id.chipStar2);
        chipStar3   = findViewById(R.id.chipStar3);
        chipStar4   = findViewById(R.id.chipStar4);
        chipStar5   = findViewById(R.id.chipStar5);
    }

    private void setupChips() {
        // Default selection
        chipBetAny.setSelected(true);
        chipRiskAny.setSelected(true);
        chipStarAny.setSelected(true);

        View.OnClickListener starListener = v -> {
            chipStarAny.setSelected(false); chipStar1.setSelected(false);
            chipStar2.setSelected(false);   chipStar3.setSelected(false);
            chipStar4.setSelected(false);   chipStar5.setSelected(false);
            ((TextView) v).setSelected(true);
            if      (v == chipStarAny) selectedMinStars = "0";
            else if (v == chipStar1)   selectedMinStars = "1";
            else if (v == chipStar2)   selectedMinStars = "2";
            else if (v == chipStar3)   selectedMinStars = "3";
            else if (v == chipStar4)   selectedMinStars = "4";
            else                       selectedMinStars = "5";
        };
        chipStarAny.setOnClickListener(starListener);
        chipStar1.setOnClickListener(starListener);
        chipStar2.setOnClickListener(starListener);
        chipStar3.setOnClickListener(starListener);
        chipStar4.setOnClickListener(starListener);
        chipStar5.setOnClickListener(starListener);

        View.OnClickListener betListener = v -> {
            clearBetChips();
            v.setSelected(true);
            if (v == chipBetAny)          selectedBetCat = "ANY";
            else if (v == chipBetDollar)  selectedBetCat = "$";
            else if (v == chipBetDollar2) selectedBetCat = "$$";
            else if (v == chipBetDollar3) selectedBetCat = "$$$";
        };
        chipBetAny.setOnClickListener(betListener);
        chipBetDollar.setOnClickListener(betListener);
        chipBetDollar2.setOnClickListener(betListener);
        chipBetDollar3.setOnClickListener(betListener);

        View.OnClickListener riskListener = v -> {
            clearRiskChips();
            v.setSelected(true);
            if (v == chipRiskAny)       selectedRisk = "ANY";
            else if (v == chipRiskLow)  selectedRisk = "low";
            else if (v == chipRiskMed)  selectedRisk = "medium";
            else if (v == chipRiskHigh) selectedRisk = "high";
        };
        chipRiskAny.setOnClickListener(riskListener);
        chipRiskLow.setOnClickListener(riskListener);
        chipRiskMed.setOnClickListener(riskListener);
        chipRiskHigh.setOnClickListener(riskListener);
    }

    private void clearBetChips() {
        chipBetAny.setSelected(false);
        chipBetDollar.setSelected(false);
        chipBetDollar2.setSelected(false);
        chipBetDollar3.setSelected(false);
    }

    private void clearRiskChips() {
        chipRiskAny.setSelected(false);
        chipRiskLow.setSelected(false);
        chipRiskMed.setSelected(false);
        chipRiskHigh.setSelected(false);
    }

    private void setupRecycler() {
        rvGames.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GamesAdapter(this);
        rvGames.setAdapter(adapter);
    }

    private void setupButtons() {
        findViewById(R.id.btnSearch).setOnClickListener(v -> doSearch());
        findViewById(R.id.btnAddBalance).setOnClickListener(v -> openAddBalanceDialog());
        View btnStats = findViewById(R.id.btnStats);
        if (btnStats != null) btnStats.setOnClickListener(v -> StatsActivity.start(this));
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            // No explicit session teardown needed — Session is in-memory only.
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
    }


    private void bindExtraViews() {
        skeletonLoader   = findViewById(R.id.skeletonLoader);
        readyState       = findViewById(R.id.readyState);
        dailyBonusBanner = findViewById(R.id.dailyBonusBanner);
        liveTicker       = findViewById(R.id.liveTicker);

        // Claim bonus
        View btnClaim = findViewById(R.id.btnClaimBonus);
        if (btnClaim != null) btnClaim.setOnClickListener(v -> claimDailyBonus());
    }

    private void checkDailyBonus() {
        if (dailyBonusBanner != null && DailyBonus.isAvailable(this)) {
            dailyBonusBanner.setVisibility(View.VISIBLE);
        }
    }

    private void claimDailyBonus() {
        DailyBonus.claim(this);
        session.addToBalance(DailyBonus.AMOUNT);
        refreshBalance();
        if (dailyBonusBanner != null) dailyBonusBanner.setVisibility(View.GONE);
        // Notify server
        session.getClient().addBalanceAsync(session.getPlayerId(), DailyBonus.AMOUNT, (ok, p) -> {});
        Toast.makeText(this, "🎁 +100 FUN Daily Bonus claimed!", Toast.LENGTH_SHORT).show();
    }

    private void checkServerConnection() {
        ConnectionStatusView csv = findViewById(R.id.connStatusMain);
        if (csv == null) return;
        csv.setConnecting();
        long t = System.currentTimeMillis();
        session.getClient().checkAnyGameAsync((ok, p) -> {
            long ms = System.currentTimeMillis() - t;
            if (ok) csv.setOnline(ms); else csv.setOffline();
        });
    }

    private String sortMode = "stars";
    private void showSortDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Sort games by")
            .setItems(new String[]{"Stars ★","Min Bet ↑","Max Bet ↓","Risk Level"},
                (d, which) -> {
                    sortMode = which == 0 ? "stars" : which == 1 ? "minbet" : which == 2 ? "maxbet" : "risk";
                    Toast.makeText(this, "Sorted by " + sortMode, Toast.LENGTH_SHORT).show();
                })
            .show();
    }


    // Subscribe to all bet broadcasts — updates ticker + shows jackpot Toast
    private void subscribeToJackpots() {
        session.getClient().subscribeToAll(
            session.getPlayerId(),
            // Jackpot callback — show Toast
            (winnerId, gameName, amount) -> {
                String msg = winnerId.equals(session.getPlayerId())
                        ? "YOU HIT THE JACKPOT! " + amount + " FUN on " + gameName + "!"
                        : "JACKPOT! " + winnerId + " won " + amount + " FUN on " + gameName;
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            },
            // All bets callback — update ticker
            (playerId, gameName, bet, result, jackpot) -> {
                if (liveTicker != null) {
                    try {
                        double betD    = Double.parseDouble(bet.replace(",", "."));
                        double resultD = Double.parseDouble(result.replace(",", "."));
                        liveTicker.addBetEvent(playerId, gameName, betD, resultD, jackpot);
                    } catch (NumberFormatException ignored) {}
                }
            }
        );
    }

    private void refreshBalance() {
        if (tvBalance != null) tvBalance.animateTo(session.getBalance());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBalance();
    }

    // -------------------------------------------------------------------
    // Search (async)
    // -------------------------------------------------------------------
    private void doSearch() {
        final String minStars = selectedMinStars;

        showLoading(true);
        final String betCat = selectedBetCat;
        final String risk   = selectedRisk;
        session.getClient().searchAsync(minStars, betCat, risk,
                new MasterClient.SearchCallback() {
                    @Override public void onSearchResult(java.util.List<Game> games) {
                        showLoading(false);
                        // Build a short filter summary string for the results header
                        StringBuilder sum = new StringBuilder();
                        if (!"any".equalsIgnoreCase(betCat) && !betCat.isEmpty())
                            sum.append("Bet ").append(betCat).append("  ·  ");
                        if (!"any".equalsIgnoreCase(risk) && !risk.isEmpty())
                            sum.append(risk.toUpperCase(java.util.Locale.US)).append(" risk  ·  ");
                        sum.append("Min ").append(minStars).append("★");

                        com.funGames.app.util.SearchResults.get().set(games, sum.toString());
                        startActivity(new android.content.Intent(MainActivity.this,
                                com.funGames.app.ui.ResultsActivity.class));
                    }
                    @Override public void onSearchError(String message) {
                        showLoading(false);
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (skeletonLoader != null) skeletonLoader.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (readyState != null)    readyState.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (loading && tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        View btnSearch = findViewById(R.id.btnSearch);
        if (btnSearch != null) btnSearch.setEnabled(!loading);
    }

    // -------------------------------------------------------------------
    // Row actions — from GamesAdapter
    // -------------------------------------------------------------------
    @Override
    public void onPlay(Game game) {
        // Launch immersive game-play screen instead of the plain dialog
        GamePlayActivity.launch(this, game.toTransportString());
    }

    @Override
    public void onRate(Game game) {
        openRateDialog(game);
    }

    // -------------------------------------------------------------------
    // Dialogs
    // -------------------------------------------------------------------
    private void openPlayDialog(final Game game) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_play);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvInfo     = d.findViewById(R.id.tvGameInfo);
        TextView tvLimits   = d.findViewById(R.id.tvBetLimits);
        EditText etBet      = d.findViewById(R.id.etBetAmount);
        Button btnCancel    = d.findViewById(R.id.btnCancel);
        Button btnPlaceBet  = d.findViewById(R.id.btnPlaceBet);

        tvInfo.setText(game.getGameName() + "  ·  Risk: " + game.getRiskLevel()
                + "  ·  Jackpot: " + String.format(Locale.US, "%.0fx", game.getJackpot()));
        tvLimits.setText(String.format(Locale.US,
                "Min %.2f – Max %.2f FUN  ·  Your balance: %.2f FUN",
                game.getMinBet(), game.getMaxBet(), session.getBalance()));

        btnCancel.setOnClickListener(v -> d.dismiss());
        btnPlaceBet.setOnClickListener(v -> {
            Double bet = Validators.parseDecimal(etBet.getText().toString());
            if (bet == null || bet <= 0) {
                Toast.makeText(this, R.string.msg_bet_positive, Toast.LENGTH_SHORT).show();
                return;
            }
            if (bet < game.getMinBet()) {
                Toast.makeText(this, R.string.msg_bet_too_low, Toast.LENGTH_SHORT).show();
                return;
            }
            if (bet > game.getMaxBet()) {
                Toast.makeText(this, R.string.msg_bet_too_high, Toast.LENGTH_SHORT).show();
                return;
            }
            if (bet > session.getBalance()) {
                Toast.makeText(this, R.string.msg_bet_over_balance, Toast.LENGTH_SHORT).show();
                return;
            }
            btnPlaceBet.setEnabled(false);
            final double betAmount = bet;
            session.getClient().playAsync(session.getPlayerId(),
                    game.getGameName(), betAmount,
                    (ok, payload) -> {
                        if (ok) {
                            double result;
                            try { result = Double.parseDouble(payload.replace(',', '.')); }
                            catch (NumberFormatException e) { result = 0.0; }
                            // Update balance: subtract bet, add back result
                            session.addToBalance(-betAmount + result);
                            refreshBalance();
                            d.dismiss();
                            showPlayOutcome(game, betAmount, result);
                        } else {
                            btnPlaceBet.setEnabled(true);
                            Toast.makeText(this,
                                    "Server: " + payload,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });

        d.show();
    }

    private void showPlayOutcome(Game game, double bet, double result) {
        String title;
        String body;
        if (result >= bet && result > 0) {
            if (result == bet * game.getJackpot()) {
                title = "🎰  JACKPOT!";
            } else {
                title = "✨  You win!";
            }
            body = String.format(Locale.US,
                    "Bet: %.2f FUN\nReturn: %.2f FUN\nNet: +%.2f FUN",
                    bet, result, result - bet);
        } else if (result > 0) {
            title = "Partial return";
            body = String.format(Locale.US,
                    "Bet: %.2f FUN\nReturn: %.2f FUN\nNet: -%.2f FUN",
                    bet, result, bet - result);
        } else {
            title = "No win";
            body = String.format(Locale.US, "You lost %.2f FUN.", bet);
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body + String.format(Locale.US,
                        "\n\nNew balance: %.2f FUN", session.getBalance()))
                .setPositiveButton("OK", null)
                .show();
    }

    private void openRateDialog(final Game game) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_rate);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle   = d.findViewById(R.id.tvRateGameName);
        Button   btnCancel = d.findViewById(R.id.btnCancel);
        final Button btnSubmit = d.findViewById(R.id.btnSubmit);

        tvTitle.setText(game.getGameName());

        final TextView[] stars = new TextView[]{
                d.findViewById(R.id.star1), d.findViewById(R.id.star2),
                d.findViewById(R.id.star3), d.findViewById(R.id.star4),
                d.findViewById(R.id.star5)
        };
        final int[] selected = {0};

        final int goldColor   = getColor(R.color.accent_gold);
        final int mutedColor  = getColor(R.color.text_muted);

        for (int i = 0; i < stars.length; i++) {
            final int idx = i + 1;
            stars[i].setOnClickListener(v -> {
                selected[0] = idx;
                for (int j = 0; j < stars.length; j++) {
                    if (j < idx) {
                        stars[j].setText("★");
                        stars[j].setTextColor(goldColor);
                    } else {
                        stars[j].setText("☆");
                        stars[j].setTextColor(mutedColor);
                    }
                }
                btnSubmit.setEnabled(true);
                btnSubmit.setAlpha(1.0f);
            });
        }

        btnCancel.setOnClickListener(v -> d.dismiss());
        btnSubmit.setOnClickListener(v -> {
            if (selected[0] < 1) return;
            btnSubmit.setEnabled(false);
            session.getClient().rateGameAsync(session.getPlayerId(),
                    game.getGameName(), selected[0],
                    (ok, payload) -> {
                        d.dismiss();
                        if (ok) {
                            Toast.makeText(this, R.string.msg_rated,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Rate failed: " + payload,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });

        d.show();
    }

    private void openAddBalanceDialog() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_add_balance);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        final EditText etAmount = d.findViewById(R.id.etAmount);
        Button btnCancel = d.findViewById(R.id.btnCancel);
        final Button btnAdd = d.findViewById(R.id.btnAdd);

        btnCancel.setOnClickListener(v -> d.dismiss());

        // Quick preset buttons
        Button bp100  = d.findViewById(R.id.btnPreset100);
        Button bp500  = d.findViewById(R.id.btnPreset500);
        Button bp1000 = d.findViewById(R.id.btnPreset1000);
        if (bp100  != null) bp100 .setOnClickListener(v -> { SoundManager.get().playClick(); addAmount(d, btnAdd, 100.0);  });
        if (bp500  != null) bp500 .setOnClickListener(v -> { SoundManager.get().playClick(); addAmount(d, btnAdd, 500.0);  });
        if (bp1000 != null) bp1000.setOnClickListener(v -> { SoundManager.get().playClick(); addAmount(d, btnAdd, 1000.0); });

        btnAdd.setOnClickListener(v -> {
            Double amount = Validators.parseDecimal(etAmount.getText().toString());
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Amount must be > 0", Toast.LENGTH_SHORT).show();
                return;
            }
            SoundManager.get().playClick();
            addAmount(d, btnAdd, amount);
        });

        d.show();
    }
    private void addAmount(android.app.Dialog d, Button btnAdd, double amt) {
        btnAdd.setEnabled(false);
        session.addToBalance(amt);
        refreshBalance();
        session.getClient().addBalanceAsync(session.getPlayerId(), amt,
                (ok, payload) -> runOnUiThread(() -> {
                    d.dismiss();
                    Toast.makeText(this,
                            ok ? getString(R.string.msg_balance_added)
                               : "Warning: server did not confirm: " + payload,
                            ok ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                }));
    }

}
