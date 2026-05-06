package shared;

import java.io.Serializable;

public class Game implements Serializable {
    private static final long serialVersionUID = 1L;

    private String  gameName;
    private String  providerName;
    private double  stars;
    private int     noOfVotes;
    private String  gameLogo;
    private double  minBet;
    private double  maxBet;
    private String  riskLevel;
    private String  hashKey;

    // Computed by system — NOT in JSON
    private String  betCategory;
    private double  jackpot;
    private boolean active;

    // Multiplier tables per risk level
    public static final double[] LOW_TABLE    = {0.0,0.0,0.0,0.1,0.5,1.0,1.1,1.3,2.0,2.5};
    public static final double[] MEDIUM_TABLE = {0.0,0.0,0.0,0.0,0.0,0.5,1.0,1.5,2.5,3.5};
    public static final double[] HIGH_TABLE   = {0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,2.0,6.5};

    public static final double LOW_JACKPOT    = 10.0;
    public static final double MEDIUM_JACKPOT = 20.0;
    public static final double HIGH_JACKPOT   = 40.0;

    public Game(String gameName, String providerName, double stars, int noOfVotes,
                String gameLogo, double minBet, double maxBet,
                String riskLevel, String hashKey) {
        this.gameName     = gameName;
        this.providerName = providerName;
        this.stars        = stars;
        this.noOfVotes    = noOfVotes;
        this.gameLogo     = gameLogo;
        this.minBet       = minBet;
        this.maxBet       = maxBet;
        this.riskLevel    = riskLevel;
        this.hashKey      = hashKey;
        this.active       = true;
        computeDerivedFields();
    }

    public void computeDerivedFields() {
        // Bet category based on minBet
        if      (minBet <= 0.1) betCategory = "$";
        else if (minBet <= 1.0) betCategory = "$$";
        else                    betCategory = "$$$";

        // Jackpot based on risk
        switch (riskLevel.toLowerCase()) {
            case "medium": jackpot = MEDIUM_JACKPOT; break;
            case "high":   jackpot = HIGH_JACKPOT;   break;
            default:       jackpot = LOW_JACKPOT;
        }
    }

    public double[] getMultiplierTable() {
        switch (riskLevel.toLowerCase()) {
            case "medium": return MEDIUM_TABLE;
            case "high":   return HIGH_TABLE;
            default:       return LOW_TABLE;
        }
    }

    // Getters
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

    // Setters (manager use)
    public void setStars(double s)     { this.stars = s; }
    public void setNoOfVotes(int n)    { this.noOfVotes = n; }
    public void setRiskLevel(String r) { this.riskLevel = r; computeDerivedFields(); }
    public void setActive(boolean a)   { this.active = a; }


     //Serialize to a single-line string for writeUTF() transfer.
     //Fields separated by "~~" to avoid collisions with game data since this symbol isn't used for anything else.
    public String toTransportString() {
        return gameName + "~~" + providerName + "~~" + stars + "~~" + noOfVotes
             + "~~" + gameLogo + "~~" + minBet + "~~" + maxBet
             + "~~" + riskLevel + "~~" + hashKey + "~~" + active;
    }

    public static Game fromTransportString(String s) {
        String[] p = s.split("~~", -1);
        Game g = new Game(p[0], p[1],
                Double.parseDouble(p[2]), Integer.parseInt(p[3]),
                p[4],
                Double.parseDouble(p[5]), Double.parseDouble(p[6]),
                p[7], p[8]);
        g.active = Boolean.parseBoolean(p[9]);
        return g;
    }

    @Override
    public String toString() {
        return String.format(
            "%-20s | Provider: %-12s | Stars: %.1f (%d votes) | " +
            "Bet: %.2f-%.2f (%s) | Risk: %-6s | Jackpot: %.0f | %s",
            gameName, providerName, stars, noOfVotes,
            minBet, maxBet, betCategory, riskLevel, jackpot,
            active ? "ACTIVE" : "INACTIVE");
    }
}
