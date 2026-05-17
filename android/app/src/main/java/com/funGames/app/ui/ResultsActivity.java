package com.funGames.app.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import com.funGames.app.ui.GamePlayActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.funGames.app.ui.SwipeActionCallback;

import com.funGames.app.R;
import com.funGames.app.model.Game;
import com.funGames.app.util.SearchResults;
import com.funGames.app.util.Session;
import com.funGames.app.util.Validators;

import java.util.List;
import java.util.Locale;

/**
 * Full-screen list of games returned by the last SEARCH. Play / Rate actions
 * are delegated here directly — they talk to the Master through Session.
 */
public class ResultsActivity extends AppCompatActivity
        implements GamesAdapter.GameActionListener {

    private Session        session;
    private GamesAdapter   adapter;
    private TextView       tvCount;
    private TextView       tvFilterSummary;
    private TextView       tvBalanceTop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Session.get().isInitialized() || !Session.get().isPlayer()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_results);
        session = Session.get();

        tvCount         = findViewById(R.id.tvResultsCount);
        tvFilterSummary = findViewById(R.id.tvFilterSummary);
        tvBalanceTop    = (TextView) findViewById(R.id.tvBalanceTop);

        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GamesAdapter(this);
        rv.setAdapter(adapter);

        findViewById(R.id.btnBackResults).setOnClickListener(v -> finish());

        List<Game> games = SearchResults.get().getGames();
        adapter.setGames(games);
        tvCount.setText(getResources().getQuantityString(
                R.plurals.fmt_results_count, games.size(), games.size()));
        tvFilterSummary.setText(SearchResults.get().getSummary());

        View empty = findViewById(R.id.tvResultsEmpty);
        empty.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);

        refreshBalance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBalance();
    }

    private void refreshBalance() {
        tvBalanceTop.setText(String.format(Locale.US, "%.2f", session.getBalance()));
    }


    // Row actions
    @Override
    public void onPlay(Game game) {
        GamePlayActivity.launch(this, game.toTransportString());
    }

    @Override
    public void onRate(Game game) { openRateDialog(game); }

    // Play dialog — mirrors MainActivity's logic
    private void openPlayDialog(final Game game) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_play);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tvInfo    = d.findViewById(R.id.tvGameInfo);
        TextView tvLimits  = d.findViewById(R.id.tvBetLimits);
        EditText etBet     = d.findViewById(R.id.etBetAmount);
        Button btnCancel   = d.findViewById(R.id.btnCancel);
        Button btnPlaceBet = d.findViewById(R.id.btnPlaceBet);

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
            if (bet < game.getMinBet())     { Toast.makeText(this, R.string.msg_bet_too_low, Toast.LENGTH_SHORT).show(); return; }
            if (bet > game.getMaxBet())     { Toast.makeText(this, R.string.msg_bet_too_high, Toast.LENGTH_SHORT).show(); return; }
            if (bet > session.getBalance()) { Toast.makeText(this, R.string.msg_bet_over_balance, Toast.LENGTH_SHORT).show(); return; }

            btnPlaceBet.setEnabled(false);
            final double betAmount = bet;
            session.getClient().playAsync(session.getPlayerId(),
                    game.getGameName(), betAmount,
                    (ok, payload) -> {
                        if (ok) {
                            double result;
                            try { result = Double.parseDouble(payload.replace(',', '.')); }
                            catch (NumberFormatException e) { result = 0.0; }
                            session.addToBalance(-betAmount + result);
                            refreshBalance();
                            d.dismiss();
                            showPlayOutcome(game, betAmount, result);
                        } else {
                            btnPlaceBet.setEnabled(true);
                            Toast.makeText(this, "Server: " + payload, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        d.show();
    }

    private void showPlayOutcome(Game game, double bet, double result) {
        String title;
        String body;
        if (result >= bet && result > 0) {
            if (result == bet * game.getJackpot()) title = "🎰  JACKPOT!";
            else                                   title = "✨  You win!";
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

    // Rate dialog — mirrors MainActivity's logic
    private void openRateDialog(final Game game) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_rate);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle    = d.findViewById(R.id.tvRateGameName);
        Button   btnCancel  = d.findViewById(R.id.btnCancel);
        final Button btnSub = d.findViewById(R.id.btnSubmit);
        tvTitle.setText(game.getGameName());

        final TextView[] stars = new TextView[] {
                d.findViewById(R.id.star1), d.findViewById(R.id.star2),
                d.findViewById(R.id.star3), d.findViewById(R.id.star4),
                d.findViewById(R.id.star5) };
        final int[] selected = {0};

        final int gold  = getColor(R.color.accent_gold);
        final int muted = getColor(R.color.text_muted);

        for (int i = 0; i < stars.length; i++) {
            final int idx = i + 1;
            stars[i].setOnClickListener(v -> {
                selected[0] = idx;
                for (int j = 0; j < stars.length; j++) {
                    if (j < idx) { stars[j].setText("★"); stars[j].setTextColor(gold); }
                    else         { stars[j].setText("☆"); stars[j].setTextColor(muted); }
                }
                btnSub.setEnabled(true);
                btnSub.setAlpha(1.0f);
            });
        }

        btnCancel.setOnClickListener(v -> d.dismiss());
        btnSub.setOnClickListener(v -> {
            if (selected[0] < 1) return;
            btnSub.setEnabled(false);
            session.getClient().rateGameAsync(session.getPlayerId(),
                    game.getGameName(), selected[0],
                    (ok, payload) -> {
                        d.dismiss();
                        if (ok) Toast.makeText(this, R.string.msg_rated,
                                Toast.LENGTH_SHORT).show();
                        else    Toast.makeText(this, "Rate failed: " + payload,
                                Toast.LENGTH_LONG).show();
                    });
        });

        d.show();
    }
}
