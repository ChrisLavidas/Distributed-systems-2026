package com.funGames.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.funGames.app.R;
import com.funGames.app.model.Game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Binds Game rows onto the RecyclerView. Delegates play/rate taps to a listener. */
public class GamesAdapter extends RecyclerView.Adapter<GamesAdapter.VH> {

    public interface GameActionListener {
        void onPlay(Game game);
        void onRate(Game game);
    }

    private final List<Game> data = new ArrayList<>();
    private final GameActionListener listener;

    public GamesAdapter(GameActionListener l) { this.listener = l; }

    public void setGames(List<Game> games) {
        data.clear();
        if (games != null) data.addAll(games);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        final Game g = data.get(pos);

        // Game icon based on name keywords
        h.tvLogoLetter.setText(gameIcon(g.getGameName()));
        h.tvLogoLetter.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30);

        h.tvGameName.setText(g.getGameName());
        h.tvProvider.setText(g.getProviderName());

        int fullStars = (int) Math.round(g.getStars());
        if (fullStars < 0) fullStars = 0;
        if (fullStars > 5) fullStars = 5;
        StringBuilder starBar = new StringBuilder();
        for (int i = 0; i < fullStars; i++) starBar.append('★');
        for (int i = fullStars; i < 5; i++) starBar.append('☆');
        h.tvStars.setText(String.format(Locale.US, "%s  %.1f (%d)",
                starBar, g.getStars(), g.getNoOfVotes()));

        String risk = g.getRiskLevel() == null ? "low" : g.getRiskLevel().toLowerCase();
        switch (risk) {
            case "high":
                h.pillRisk.setText("HIGH RISK");
                h.pillRisk.setTextColor(h.itemView.getContext().getColor(R.color.risk_high));
                break;
            case "medium":
                h.pillRisk.setText("MED RISK");
                h.pillRisk.setTextColor(h.itemView.getContext().getColor(R.color.risk_medium));
                break;
            default:
                h.pillRisk.setText("LOW RISK");
                h.pillRisk.setTextColor(h.itemView.getContext().getColor(R.color.risk_low));
                break;
        }

        h.pillJackpot.setText(String.format(Locale.US,
                "JACKPOT %.0fx", g.getJackpot()));

        // Bet category badge ($ / $$ / $$$) — computed from minBet per spec
        if (h.pillBetCat != null) {
            double min = g.getMinBet();
            String cat = min >= 5.0 ? "$$$" : min >= 1.0 ? "$$" : "$";
            h.pillBetCat.setText(cat);
            int catColor = min >= 5.0 ? 0xFFF87171 : min >= 1.0 ? 0xFFFFD84A : 0xFF34D399;
            h.pillBetCat.setTextColor(catColor);
        }

        h.tvBetRange.setText(String.format(Locale.US,
                "%.2f – %.2f FUN", g.getMinBet(), g.getMaxBet()));

        h.btnPlay.setOnClickListener(v -> {
            if (listener != null) listener.onPlay(g);
        });
        h.btnRate.setOnClickListener(v -> {
            if (listener != null) listener.onRate(g);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    public Game getGameAt(int pos) { return (pos>=0&&pos<data.size()) ? data.get(pos) : null; }

    private static String gameIcon(String name) {
        if (name == null) return "🎮";
        String n = name.toLowerCase(Locale.US);
        if (n.equals("luckyslots")  || n.equals("lucky slots"))  return "🎰";
        if (n.equals("luckywheel")  || n.equals("lucky wheel"))  return "🎡";
        if (n.equals("megapoker")   || n.equals("mega poker"))   return "🃏";
        if (n.equals("roulettex")   || n.equals("roulette x"))   return "🎲";
        if (n.contains("slot"))                                    return "🎰";
        if (n.contains("poker") || n.contains("card"))           return "🃏";
        if (n.contains("roulett"))                                 return "🎲";
        if (n.contains("wheel") || n.contains("spin"))           return "🎡";
        if (n.contains("dice")  || n.contains("craps"))          return "🎲";
        if (n.contains("bingo"))                                   return "🔢";
        if (n.contains("crash"))                                   return "📈";
        if (n.contains("mine"))                                    return "💣";
        if (n.contains("lucky") && n.contains("slot"))           return "🎰";
        if (n.contains("lucky") && n.contains("wheel"))          return "🎡";
        if (n.contains("lucky"))                                   return "🍀";
        if (n.contains("mega")  || n.contains("super"))          return "⚡";
        if (n.contains("gold")  || n.contains("treasure"))       return "💰";
        if (n.contains("dragon"))                                  return "🐉";
        if (n.contains("fruit"))                                   return "🍒";
        return "🎮";
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvLogoLetter, tvGameName, tvProvider, tvStars,
                       pillRisk, pillJackpot, tvBetRange, pillBetCat;
        final Button btnPlay, btnRate;

        VH(@NonNull View v) {
            super(v);
            tvLogoLetter = v.findViewById(R.id.tvLogoLetter);
            tvGameName   = v.findViewById(R.id.tvGameName);
            tvProvider   = v.findViewById(R.id.tvProvider);
            tvStars      = v.findViewById(R.id.tvStars);
            pillRisk     = v.findViewById(R.id.pillRisk);
            pillJackpot  = v.findViewById(R.id.pillJackpot);
            tvBetRange   = v.findViewById(R.id.tvBetRange);
            pillBetCat   = v.findViewById(R.id.pillBetCat);
            btnPlay      = v.findViewById(R.id.btnPlay);
            btnRate      = v.findViewById(R.id.btnRate);
        }
    }
}
