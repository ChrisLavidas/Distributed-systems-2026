package shared;

public class Protocol {
    public static final String SEP = "~~";

    // Manager -> Master
    public static final String ADD_GAME        = "ADD_GAME";
    public static final String REMOVE_GAME     = "REMOVE_GAME";
    public static final String UPDATE_RISK     = "UPDATE_RISK";
    public static final String STATS_PROVIDER  = "STATS_PROVIDER";
    public static final String STATS_PLAYER    = "STATS_PLAYER";
    public static final String CHECK_GAME      = "CHECK_GAME";
    public static final String CHECK_ANY_GAME  = "CHECK_ANY_GAME";
    public static final String LEADERBOARD     = "LEADERBOARD";
    public static final String WORKER_STATUS   = "WORKER_STATUS";   // NEW: health check
    public static final String STRESS_TEST     = "STRESS_TEST";     // NEW: stress test

    // Player -> Master
    public static final String SEARCH          = "SEARCH";
    public static final String PLAY            = "PLAY";
    public static final String ADD_BALANCE     = "ADD_BALANCE";
    public static final String GET_BALANCE     = "GET_BALANCE";
    public static final String RATE_GAME       = "RATE_GAME";
    public static final String SUBSCRIBE       = "SUBSCRIBE";

    // Master -> Worker
    public static final String WORKER_ADD            = "WORKER_ADD";
    public static final String WORKER_REMOVE         = "WORKER_REMOVE";
    public static final String WORKER_UPDATE_RISK    = "WORKER_UPDATE_RISK";
    public static final String WORKER_SEARCH         = "WORKER_SEARCH";
    public static final String WORKER_PLAY           = "WORKER_PLAY";
    public static final String WORKER_RATE           = "WORKER_RATE";
    public static final String WORKER_STATS_PROVIDER = "WORKER_STATS_PROVIDER";
    public static final String WORKER_STATS_PLAYER   = "WORKER_STATS_PLAYER";
    public static final String WORKER_CHECK          = "WORKER_CHECK";
    public static final String WORKER_CHECK_ANY      = "WORKER_CHECK_ANY";
    public static final String WORKER_GET_GAME       = "WORKER_GET_GAME";
    public static final String WORKER_PING           = "WORKER_PING";  // NEW

    // Master -> Worker (stats with mapId)
    public static final String WORKER_STATS_PROVIDER_MR  = "WORKER_STATS_PROVIDER_MR";
    public static final String WORKER_STATS_PLAYER_MR    = "WORKER_STATS_PLAYER_MR";
    public static final String WORKER_LEADERBOARD_MR     = "WORKER_LEADERBOARD_MR";

    // Active Replication (Bonus)
    public static final String WORKER_ADD_REPLICA               = "WORKER_ADD_REPLICA";
    public static final String WORKER_SYNC_PLAY                 = "WORKER_SYNC_PLAY";
    public static final String WORKER_SEARCH_ALL                = "WORKER_SEARCH_ALL";
    public static final String WORKER_STATS_PROVIDER_MR_REPLICA = "WORKER_STATS_PROVIDER_MR_REPLICA";
    public static final String WORKER_STATS_PLAYER_MR_REPLICA   = "WORKER_STATS_PLAYER_MR_REPLICA";
    public static final String WORKER_LEADERBOARD_MR_REPLICA    = "WORKER_LEADERBOARD_MR_REPLICA";

    // Worker -> Reducer
    public static final String MAP_RESULT          = "MAP_RESULT";
    public static final String MAP_RESULT_PROVIDER = "MAP_RESULT_PROVIDER";

    // Master -> Reducer
    public static final String REDUCE_FETCH = "REDUCE_FETCH";

    // SRG
    public static final String SRG_REQUEST  = "SRG_REQUEST";
    public static final String SRG_RESPONSE = "SRG_RESPONSE";

    // Broadcast events
    public static final String JACKPOT_EVENT     = "JACKPOT_EVENT";
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
}
