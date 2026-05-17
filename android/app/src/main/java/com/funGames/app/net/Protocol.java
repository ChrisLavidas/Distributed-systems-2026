package com.funGames.app.net;

public class Protocol {
    public static final String SEP = "~~";

    // Player -> Master
    public static final String SEARCH      = "SEARCH";
    public static final String PLAY        = "PLAY";
    public static final String ADD_BALANCE = "ADD_BALANCE";
    public static final String GET_BALANCE = "GET_BALANCE";
    public static final String RATE_GAME   = "RATE_GAME";
    public static final String SUBSCRIBE   = "SUBSCRIBE";

    // Manager -> Master
    public static final String ADD_GAME       = "ADD_GAME";
    public static final String REMOVE_GAME    = "REMOVE_GAME";
    public static final String UPDATE_RISK    = "UPDATE_RISK";
    public static final String STATS_PROVIDER = "STATS_PROVIDER";
    public static final String STATS_PLAYER   = "STATS_PLAYER";
    public static final String CHECK_GAME     = "CHECK_GAME";
    public static final String CHECK_ANY_GAME = "CHECK_ANY_GAME";
    public static final String LEADERBOARD    = "LEADERBOARD";
    public static final String WORKER_STATUS  = "WORKER_STATUS";
    public static final String STRESS_TEST    = "STRESS_TEST";

    // Master -> Player
    public static final String MAP_RESULT        = "MAP_RESULT";
    public static final String JACKPOT_BROADCAST = "JACKPOT_BROADCAST";
    public static final String BET_BROADCAST     = "BET_BROADCAST";

    // Generic
    public static final String OK    = "OK";
    public static final String ERROR = "ERROR";
    public static final String END   = "END";

    public static String build(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(SEP);
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    public static String[] parse(String msg) {
        return msg.split(SEP, -1);
    }

    private Protocol() {}
}
