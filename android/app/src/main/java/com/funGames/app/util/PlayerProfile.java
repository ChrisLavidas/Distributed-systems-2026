package com.funGames.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/** XP, Level, Achievements — stored in SharedPreferences. */
public class PlayerProfile {

    private static final String PREF_PREFIX = "player_profile_";
    private static PlayerProfile INSTANCE;
    private static String lastPlayerId = "";

    // XP thresholds per level
    private static final int[] LEVEL_XP = {
        0,100,250,500,1000,2000,3500,5500,8000,12000,18000
    };
    private static final String[] LEVEL_TITLES = {
        "Beginner","Rookie","Regular","Pro","Expert",
        "Veteran","Elite","Master","Grand Master","Legend","Casino God"
    };

    // Achievement IDs
    public static final String ACH_FIRST_WIN     = "first_win";
    public static final String ACH_JACKPOT_HUNTER= "jackpot_hunter";
    public static final String ACH_HIGH_ROLLER   = "high_roller";
    public static final String ACH_LUCKY_STREAK  = "lucky_streak";
    public static final String ACH_CENTURION     = "centurion";     // 100 bets

    public static class Achievement {
        public final String id, title, desc, emoji;
        public Achievement(String id,String title,String desc,String emoji){
            this.id=id;this.title=title;this.desc=desc;this.emoji=emoji;
        }
    }

    public static final Achievement[] ALL_ACHIEVEMENTS = {
        new Achievement(ACH_FIRST_WIN,    "First Win",      "Win your first bet",              "🏆"),
        new Achievement(ACH_JACKPOT_HUNTER,"Jackpot Hunter","Hit 5 jackpots",                  "🎰"),
        new Achievement(ACH_HIGH_ROLLER,  "High Roller",    "Place a bet over 50 FUN",         "💎"),
        new Achievement(ACH_LUCKY_STREAK, "Lucky Streak",   "Win 5 in a row",                  "🔥"),
        new Achievement(ACH_CENTURION,    "Centurion",      "Place 100 bets",                  "⚡"),
    };

    private SharedPreferences prefs;
    private int xp=0, level=0, jackpots=0, totalBets=0;
    private double bestWin=0, biggestBet=0, totalWon=0, totalLost=0;
    private String favGame="";

    public static PlayerProfile get(Context ctx) {
        String playerId = "";
        try { playerId = com.funGames.app.util.Session.get().getPlayerId(); } catch (Exception ignored) {}
        if (INSTANCE == null || !playerId.equals(lastPlayerId)) {
            INSTANCE = new PlayerProfile();
            lastPlayerId = playerId;
            INSTANCE.load(ctx, playerId);
        }
        return INSTANCE;
    }

    private void load(Context ctx, String playerId) {
        String key = PREF_PREFIX + (playerId.isEmpty() ? "default" : playerId);
        prefs = ctx.getApplicationContext().getSharedPreferences(key, Context.MODE_PRIVATE);
        xp          = prefs.getInt("xp",0);
        level       = prefs.getInt("level",0);
        jackpots    = prefs.getInt("jackpots",0);
        totalBets   = prefs.getInt("total_bets",0);
        bestWin     = Double.longBitsToDouble(prefs.getLong("best_win",0));
        biggestBet  = Double.longBitsToDouble(prefs.getLong("biggest_bet",0));
        totalWon    = Double.longBitsToDouble(prefs.getLong("total_won",0));
        totalLost   = Double.longBitsToDouble(prefs.getLong("total_lost",0));
        favGame     = prefs.getString("fav_game","—");
    }

    /** Called after every bet result. Returns list of newly unlocked achievement IDs. */
    public java.util.List<String> recordBet(Context ctx, String game, double bet,
                                             double result, boolean jackpot) {
        java.util.List<String> unlocked = new java.util.ArrayList<>();
        totalBets++;
        boolean win = result >= bet && result > 0;
        double net = result - bet;
        if (win)  { totalWon  += net; if (net > bestWin)   bestWin  = net; }
        else       { totalLost += Math.abs(net); }
        if (bet > biggestBet) biggestBet = bet;
        if (jackpot) jackpots++;

        // XP: 10 per bet, 30 per win, 100 per jackpot
        int earned = 10 + (win ? 30 : 0) + (jackpot ? 100 : 0);
        xp += earned;
        int newLevel = computeLevel();
        level = newLevel;

        // Check achievements
        if (win && !hasAchievement(ACH_FIRST_WIN))                         { unlock(ACH_FIRST_WIN);     unlocked.add(ACH_FIRST_WIN);     }
        if (jackpots >= 5 && !hasAchievement(ACH_JACKPOT_HUNTER))          { unlock(ACH_JACKPOT_HUNTER);unlocked.add(ACH_JACKPOT_HUNTER);}
        if (bet >= 50 && !hasAchievement(ACH_HIGH_ROLLER))                 { unlock(ACH_HIGH_ROLLER);   unlocked.add(ACH_HIGH_ROLLER);   }
        if (BetHistory.get().getBestStreak() >= 5 && !hasAchievement(ACH_LUCKY_STREAK)) { unlock(ACH_LUCKY_STREAK); unlocked.add(ACH_LUCKY_STREAK); }
        if (totalBets >= 100 && !hasAchievement(ACH_CENTURION))            { unlock(ACH_CENTURION);     unlocked.add(ACH_CENTURION);     }

        save();
        return unlocked;
    }

    private int computeLevel() {
        for (int i = LEVEL_XP.length - 1; i >= 0; i--)
            if (xp >= LEVEL_XP[i]) return i;
        return 0;
    }

    public int  getXP()        { return xp; }
    public int  getLevel()     { return level; }
    public String getLevelTitle(){ return LEVEL_TITLES[Math.min(level,LEVEL_TITLES.length-1)]; }
    public int  getXPForNext() { return level < LEVEL_XP.length-1 ? LEVEL_XP[level+1] : xp; }
    public float getLevelProgress() {
        int cur=LEVEL_XP[Math.min(level,LEVEL_XP.length-1)];
        int next=getXPForNext();
        return next==cur?1f:(float)(xp-cur)/(next-cur);
    }
    public double getBestWin()    { return bestWin; }
    public double getBiggestBet() { return biggestBet; }
    public double getTotalWon()   { return totalWon; }
    public double getTotalLost()  { return totalLost; }
    public int    getTotalBets()  { return totalBets; }
    public String getFavGame()    { return favGame; }

    public boolean hasAchievement(String id) {
        return prefs != null && prefs.getBoolean("ach_"+id, false);
    }
    private void unlock(String id) {
        if (prefs != null) prefs.edit().putBoolean("ach_"+id, true).apply();
    }
    private void save() {
        if (prefs == null) return;
        prefs.edit()
            .putInt("xp",xp).putInt("level",level)
            .putInt("jackpots",jackpots).putInt("total_bets",totalBets)
            .putLong("best_win", Double.doubleToLongBits(bestWin))
            .putLong("biggest_bet", Double.doubleToLongBits(biggestBet))
            .putLong("total_won", Double.doubleToLongBits(totalWon))
            .putLong("total_lost", Double.doubleToLongBits(totalLost))
            .putString("fav_game", favGame)
            .apply();
    }
}
