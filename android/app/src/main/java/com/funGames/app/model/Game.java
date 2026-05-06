package com.funGames.app.model;

import java.io.Serializable;

/**
 * Player-side view of a Game. Fields and {@code toTransportString()} /
 * {@code fromTransportString()} mirror the backend {@code shared.Game}.
 * The Android app only needs to DESERIALIZE games (it receives them
 * from the Master); serialization is included for completeness.
 */
public class Game implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String gameName;
    private final String providerName;
    private final double stars;
    private final int    noOfVotes;
    private final String gameLogo;
    private final double minBet;
    private final double maxBet;
    private final String riskLevel;
    private final String hashKey;
    private final boolean active;

    // Derived (computed from riskLevel / minBet, same rules as backend)
    private final String betCategory;
    private final double jackpot;

    public static final double LOW_JACKPOT    = 10.0;
    public static final double MEDIUM_JACKPOT = 20.0;
    public static final double HIGH_JACKPOT   = 40.0;

    public Game(String gameName, String providerName, double stars, int noOfVotes,
                String gameLogo, double minBet, double maxBet,
                String riskLevel, String hashKey, boolean active) {
        this.gameName     = gameName;
        this.providerName = providerName;
        this.stars        = stars;
        this.noOfVotes    = noOfVotes;
        this.gameLogo     = gameLogo;
        this.minBet       = minBet;
        this.maxBet       = maxBet;
        this.riskLevel    = riskLevel;
        this.hashKey      = hashKey;
        this.active       = active;

        // betCategory — same rules as backend
        if      (minBet <= 0.1) this.betCategory = "$";
        else if (minBet <= 1.0) this.betCategory = "$$";
        else                    this.betCategory = "$$$";

        // jackpot — same rules as backend
        switch (riskLevel == null ? "low" : riskLevel.toLowerCase()) {
            case "medium": this.jackpot = MEDIUM_JACKPOT; break;
            case "high":   this.jackpot = HIGH_JACKPOT;   break;
            default:       this.jackpot = LOW_JACKPOT;
        }
    }

    /** Serialises this game into the transport format used by the backend. */
    public String toTransportString() {
        return gameName     + "~~" +
                providerName + "~~" +
                stars        + "~~" +
                noOfVotes    + "~~" +
                (gameLogo == null ? "" : gameLogo) + "~~" +
                minBet       + "~~" +
                maxBet       + "~~" +
                (riskLevel == null ? "low" : riskLevel) + "~~" +
                (hashKey == null ? "" : hashKey) + "~~" +
                active;
    }

    /** Parses a transport string of the form produced by backend Game#toTransportString. */
    public static Game fromTransportString(String s) {
        String[] p = s.split("~~", -1);
        return new Game(
                p[0],
                p[1],
                Double.parseDouble(p[2]),
                Integer.parseInt(p[3]),
                p[4],
                Double.parseDouble(p[5]),
                Double.parseDouble(p[6]),
                p[7],
                p[8],
                Boolean.parseBoolean(p[9])
        );
    }

    public String  getGameName()     { return gameName; }
    public String  getProviderName() { return providerName; }
    public double  getStars()        { return stars; }
    public int     getNoOfVotes()    { return noOfVotes; }
    public String  getGameLogo()     { return gameLogo; }
    public double  getMinBet()       { return minBet; }
    public double  getMaxBet()       { return maxBet; }
    public String  getRiskLevel()    { return riskLevel; }
    public String  getHashKey()      { return hashKey; }
    public String  getBetCategory()  { return betCategory; }
    public double  getJackpot()      { return jackpot; }
    public boolean isActive()        { return active; }

    public double[] getMultiplierTable() {
        if (riskLevel == null) return new double[]{0.0,0.0,0.0,0.1,0.5,1.0,1.1,1.3,2.0,2.5};
        switch (riskLevel.toLowerCase()) {
            case "medium": return new double[]{0.0,0.0,0.0,0.0,0.0,0.5,1.0,1.5,2.5,3.5};
            case "high":   return new double[]{0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,2.0,6.5};
            default:       return new double[]{0.0,0.0,0.0,0.1,0.5,1.0,1.1,1.3,2.0,2.5};
        }
    }
}