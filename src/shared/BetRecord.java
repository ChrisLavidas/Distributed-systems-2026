package shared;

import java.io.Serializable;

public class BetRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String playerId;
    private final String gameName;
    private final String providerName;
    private final double betAmount;
    private final double playerResult; // positive = player won, negative = player lost

    public BetRecord(String playerId, String gameName, String providerName,
                     double betAmount, double playerResult) {
        this.playerId     = playerId;
        this.gameName     = gameName;
        this.providerName = providerName;
        this.betAmount    = betAmount;
        this.playerResult = playerResult;
    }

    public String getPlayerId()     { return playerId; }
    public String getGameName()     { return gameName; }
    public String getProviderName() { return providerName; }
    public double getBetAmount()    { return betAmount; }
    public double getPlayerResult() { return playerResult; }

    @Override
    public String toString() {
        return String.format("BetRecord{player=%s, game=%s, bet=%.2f, result=%.2f}",
                playerId, gameName, betAmount, playerResult);
    }
}
