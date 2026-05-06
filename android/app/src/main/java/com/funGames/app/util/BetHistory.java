package com.funGames.app.util;

import com.funGames.app.util.Session;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** In-memory last-10 bet history. Singleton, no disk. */
public class BetHistory {

    public static class Entry {
        public final String game;
        public final double bet;
        public final double result;
        public final long   time;

        public Entry(String game, double bet, double result) {
            this.game   = game;
            this.bet    = bet;
            this.result = result;
            this.time   = System.currentTimeMillis();
        }

        public boolean isWin()     { return result >= bet && result > 0; }
        public boolean isJackpot() { return result > bet * 5 && result > 0; }
        public double  net()       { return result - bet; }
    }

    private static BetHistory INSTANCE;
    private static String lastPlayerId = "";
    private final ArrayDeque<Entry> entries = new ArrayDeque<>(10);
    private int wins = 0, total = 0, streak = 0, bestStreak = 0;

    public static BetHistory get() {
        String playerId = "";
        try { playerId = Session.get().getPlayerId(); } catch (Exception ignored) {}
        if (INSTANCE == null || !playerId.equals(lastPlayerId)) {
            INSTANCE = new BetHistory();
            lastPlayerId = playerId;
        }
        return INSTANCE;
    }

    public synchronized void add(String game, double bet, double result) {
        if (entries.size() >= 10) entries.pollFirst();
        entries.addLast(new Entry(game, bet, result));
        total++;
        if (result >= bet && result > 0) {
            wins++; streak++;
            if (streak > bestStreak) bestStreak = streak;
        } else {
            streak = 0;
        }
    }

    public synchronized List<Entry> getAll() { return new ArrayList<>(entries); }
    public synchronized int getWins()        { return wins; }
    public synchronized int getTotal()       { return total; }
    public synchronized int getStreak()      { return streak; }
    public synchronized int getBestStreak()  { return bestStreak; }

    public synchronized double getWinRate() {
        return total == 0 ? 0.0 : (double) wins / total * 100.0;
    }
}
