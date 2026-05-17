package worker;

import shared.*;
import srg.SecureRandomGenerator;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.Locale;


//Stores games assigned to it in memory .
//Handles: ADD, REMOVE, UPDATE_RISK, SEARCH (map phase), PLAY, RATE, STATS.
//For PLAY: connects to SRG for a random number, computes result.

public class WorkerNode {

    private final int    workerId;
    private final int    port;
    private final String srgHost;
    private final int    srgPort;
    private final String reducerHost;
    private final int    reducerPort;

    // In-memory storage
    private final Object                  lock         = new Object();
    private final Map<String, Game>       games        = new HashMap<>();
    // gameName -> net result from house perspective (positive = house earned)
    private final Map<String, Double>     houseBalance = new HashMap<>();
    // all bets ever placed (for MapReduce queries)
    private final List<BetRecord>         allBets      = new ArrayList<>();

    // Active Replication (Bonus)
    // games for which THIS worker is the primary (determined by H(name) % N)
    private final Set<String>            primaryGameNames = new HashSet<>();
    // gameName -> primary worker index (for replica games only)
    private final Map<String, Integer>   replicaOwnerMap  = new HashMap<>();

    public WorkerNode(int workerId, int port,
                      String srgHost, int srgPort,
                      String reducerHost, int reducerPort) {
        this.workerId    = workerId;
        this.port        = port;
        this.srgHost     = srgHost;
        this.srgPort     = srgPort;
        this.reducerHost = reducerHost;
        this.reducerPort = reducerPort;
    }

    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println(" WorkerNode needs the following parameters: <id> <port> <srgHost> <srgPort> <reducerHost> <reducerPort>");
            return;
        }
        WorkerNode node = new WorkerNode(
                Integer.parseInt(args[0]), Integer.parseInt(args[1]),
                args[2], Integer.parseInt(args[3]),
                args[4], Integer.parseInt(args[5]));
        node.start();
    }

    public void start() {
        System.out.println("[Worker-" + workerId + "] Starting on port " + port);
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                Socket masterSocket = serverSocket.accept();
                new HandleMaster(masterSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handle one connection from Master
    class HandleMaster extends Thread {
        private final Socket socket;

        HandleMaster(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream());

                String   msg   = in.readUTF();
                String[] parts = Protocol.parse(msg);
                String   cmd   = parts[0];

                switch (cmd) {
                    case Protocol.WORKER_ADD:            handleAdd(parts, out);          break;
                    case Protocol.WORKER_REMOVE:         handleRemove(parts, out);       break;
                    case Protocol.WORKER_UPDATE_RISK:    handleUpdateRisk(parts, out);   break;
                    case Protocol.WORKER_SEARCH:         handleSearch(parts, out);       break;
                    case Protocol.WORKER_PLAY:           handlePlay(parts, out);         break;
                    case Protocol.WORKER_RATE:           handleRate(parts, out);         break;
                    case Protocol.WORKER_STATS_PROVIDER_MR: handleStatsProvider(parts, out); break;
                    case Protocol.WORKER_STATS_PLAYER_MR:   handleStatsPlayer(parts, out);   break;
                    case Protocol.WORKER_CHECK:          handleCheck(parts, out);        break;
                    case Protocol.WORKER_CHECK_ANY:      handleCheckAny(out);            break;
                    case Protocol.WORKER_PING:           handlePing(out);                break;
                    case Protocol.WORKER_GET_GAME:       handleGetGame(parts, out);      break;
                    // Active Replication (Bonus)
                    case Protocol.WORKER_ADD_REPLICA:               handleAddReplica(parts, out);           break;
                    case Protocol.WORKER_SYNC_PLAY:                 handleSyncPlay(parts, out);             break;
                    case Protocol.WORKER_SEARCH_ALL:                handleSearchAll(parts, out);            break;
                    case Protocol.WORKER_STATS_PROVIDER_MR_REPLICA: handleStatsProviderReplica(parts, out); break;
                    case Protocol.WORKER_STATS_PLAYER_MR_REPLICA:   handleStatsPlayerReplica(parts, out);   break;
                    case Protocol.WORKER_LEADERBOARD_MR:            handleLeaderboard(parts, out);          break; // NEW
                    case Protocol.WORKER_LEADERBOARD_MR_REPLICA:    handleLeaderboardReplica(parts, out);   break; // NEW
                    default:
                        out.writeUTF(Protocol.build(Protocol.ERROR, "Unknown command: " + cmd));
                        out.flush();
                }
            } catch (IOException e) {
                System.err.println("[Worker-" + workerId + "] Connection error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // Command handlers
    // WORKER_PING - returns worker stats: activeGames, totalBets
    private void handlePing(ObjectOutputStream out) throws IOException {
        int activeGames = 0;
        int totalBets;
        synchronized (lock) {
            for (Game g : games.values()) if (g.isActive() && primaryGameNames.contains(g.getGameName())) activeGames++;
            totalBets = allBets.size();
        }
        out.writeUTF(Protocol.build(Protocol.OK, String.valueOf(activeGames), String.valueOf(totalBets)));
        out.flush();
    }

    // WORKER_CHECK_ANY returns OK if there is at least one active game, otherwise ERROR.
    private void handleCheckAny(ObjectOutputStream out) throws IOException {
        synchronized (lock) {
            for (Game g : games.values()) {
                if (g.isActive()) {
                    out.writeUTF(Protocol.build(Protocol.OK, "Active games exist"));
                    out.flush();
                    return;
                }
            }
        }
        out.writeUTF(Protocol.build(Protocol.ERROR, "No active games"));
        out.flush();
    }

    // WORKER_CHECK ( returns OK if present & active, otherwise ERROR.)
    private void handleCheck(String[] parts, ObjectOutputStream out) throws IOException {
        String gameName = parts[1];
        synchronized (lock) {
            Game g = games.get(gameName);
            if (g == null) {
                out.writeUTF(Protocol.build(Protocol.ERROR, "Game not found: " + gameName));
                out.flush();
                return;
            }
            if (!g.isActive()) {
                out.writeUTF(Protocol.build(Protocol.ERROR, "Game is inactive: " + gameName));
                out.flush();
                return;
            }
        }
        out.writeUTF(Protocol.build(Protocol.OK, "OK"));
        out.flush();
    }

    // WORKER_GET_GAME~~gameName
    // Returns the full transport string of an active game (primary or replica).
    // Used by the Master to re-hydrate a primary worker that has restarted.
    private void handleGetGame(String[] parts, ObjectOutputStream out) throws IOException {
        String gameName = parts[1];
        synchronized (lock) {
            Game g = games.get(gameName);
            if (g == null || !g.isActive()) {
                out.writeUTF(Protocol.build(Protocol.ERROR, "Game not found or inactive: " + gameName));
                out.flush();
                return;
            }
            out.writeUTF(Protocol.build(Protocol.OK, g.toTransportString()));
        }
        out.flush();
    }

    // WORKER_ADD
    private void handleAdd(String[] parts, ObjectOutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) sb.append(Protocol.SEP);
            sb.append(parts[i]);
        }
        Game game = Game.fromTransportString(sb.toString());
        synchronized (lock) {
            Game existing = games.get(game.getGameName());
            if (existing != null) {
                if (existing.isActive()) {
                    System.out.println("[Worker-" + workerId + "] Duplicate: " + game.getGameName());
                    out.writeUTF(Protocol.build(Protocol.ERROR,
                            "Game already exists: " + game.getGameName()));
                    out.flush();
                    return;
                } else {
                    games.put(game.getGameName(), game);
                    primaryGameNames.add(game.getGameName()); // ensure it's tracked as primary again
                    System.out.println("[Worker-" + workerId + "] Game re-activated: " + game.getGameName());
                    out.writeUTF(Protocol.build(Protocol.OK,
                            "Game re-activated: " + game.getGameName()));
                    out.flush();
                    return;
                }
            }
            games.put(game.getGameName(), game);
            houseBalance.put(game.getGameName(), 0.0);
            primaryGameNames.add(game.getGameName());
        }
        System.out.println("[Worker-" + workerId + "] Game added: " + game.getGameName());
        out.writeUTF(Protocol.build(Protocol.OK, "Game added: " + game.getGameName()));
        out.flush();
    }

    //WORKER_REMOVE
    private void handleRemove(String[] parts, ObjectOutputStream out) throws IOException {
        String gameName = parts[1];
        synchronized (lock) {
            Game g = games.get(gameName);
            if (g == null) {
                out.writeUTF(Protocol.build(Protocol.ERROR, "Game not found: " + gameName));
                out.flush();
                return;
            }
            if (!g.isActive()) {
                out.writeUTF(Protocol.build(Protocol.ERROR,
                        "Game is already inactive: " + gameName));
                out.flush();
                return;
            }
            g.setActive(false); // keep for stats, hide from search
        }
        System.out.println("[Worker-" + workerId + "] Game deactivated: " + gameName);
        out.writeUTF(Protocol.build(Protocol.OK, "Game deactivated: " + gameName));
        out.flush();
    }

    // WORKER_UPDATE_RISK
    private void handleUpdateRisk(String[] parts, ObjectOutputStream out) throws IOException {
        String gameName = parts[1];
        String newRisk  = parts[2];
        synchronized (lock) {
            Game g = games.get(gameName);
            if (g == null) {
                out.writeUTF(Protocol.build(Protocol.ERROR, "Game not found: " + gameName));
                out.flush();
                return;
            }
            if (!g.isActive()) {
                out.writeUTF(Protocol.build(Protocol.ERROR,
                        "Cannot update inactive game: " + gameName));
                out.flush();
                return;
            }
            g.setRiskLevel(newRisk);
        }
        System.out.println("[Worker-" + workerId + "] Risk updated: " + gameName + " -> " + newRisk);
        out.writeUTF(Protocol.build(Protocol.OK, "Risk updated"));
        out.flush();
    }

    //WORKER_SEARCH
    private void handleSearch(String[] parts, ObjectOutputStream out) throws IOException {
        String minStarsStr  = parts[1];
        String betCatFilter = parts[2];
        String riskFilter   = parts[3];

        List<Game> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(games.values());
        }

        int found = 0;
        for (Game g : snapshot) {
            if (!g.isActive()) continue;
            if (!primaryGameNames.contains(g.getGameName())) continue; // skip replicas
            if (!matchesFilters(g, minStarsStr, betCatFilter, riskFilter)) continue;
            out.writeUTF(Protocol.build(Protocol.MAP_RESULT, g.toTransportString()));
            out.flush();
            found++;
        }
        out.writeUTF(Protocol.END);
        out.flush();
        System.out.println("[Worker-" + workerId + "] Search sent " + found + " result(s).");
    }

    private boolean matchesFilters(Game g, String minStarsStr, String betCat, String risk) {
        if (!minStarsStr.isEmpty() && !minStarsStr.equals("0")) {
            try {
                if (g.getStars() < Double.parseDouble(minStarsStr)) return false;
            } catch (NumberFormatException ignored) {}
        }
        if (!betCat.isEmpty() && !betCat.equalsIgnoreCase("ANY")) {
            if (!g.getBetCategory().equals(betCat)) return false;
        }
        if (!risk.isEmpty() && !risk.equalsIgnoreCase("ANY")) {
            if (!g.getRiskLevel().equalsIgnoreCase(risk)) return false;
        }
        return true;
    }

    // WORKER_PLAY
    private void handlePlay(String[] parts, ObjectOutputStream out) throws IOException {
        String playerId  = parts[1];
        String gameName  = parts[2];
        double betAmount;
        try { betAmount = Double.parseDouble(parts[3]); }
        catch (NumberFormatException e) {
            out.writeUTF(Protocol.build(Protocol.ERROR, "Invalid bet amount"));
            out.flush();
            return;
        }

        // Snapshot game info under lock, then release before calling SRG
        // This prevents one worker's SRG timeout from blocking all others
        final Game game;
        synchronized (lock) {
            Game g = games.get(gameName);
            if (g == null || !g.isActive()) {
                out.writeUTF(Protocol.build(Protocol.ERROR, "Game not found or inactive"));
                out.flush();
                return;
            }
            if (betAmount < g.getMinBet() || betAmount > g.getMaxBet()) {
                out.writeUTF(Protocol.build(Protocol.ERROR,
                        "Bet must be between " + g.getMinBet() + " and " + g.getMaxBet()));
                out.flush();
                return;
            }
            game = g; // snapshot reference — lock released after this block
        }

        // SRG call happens OUTSIDE the lock — other requests can proceed in parallel
        int randomNumber;
        try {
            randomNumber = getRandomFromSRG(gameName, game.getHashKey());
        } catch (Exception e) {
            System.err.println("[Worker-" + workerId + "] SRG error for game=" + gameName + ": " + e.getMessage());
            out.writeUTF(Protocol.build(Protocol.ERROR, "SRG error: " + e.getMessage()));
            out.flush();
            return;
        }

        double playerResult = computeResult(randomNumber, betAmount, game);
        double houseEarning = betAmount - playerResult;

        // Re-acquire lock only for the quick in-memory update
        synchronized (lock) {
            houseBalance.merge(gameName, houseEarning, Double::sum);
            allBets.add(new BetRecord(playerId, gameName,
                    game.getProviderName(), betAmount, playerResult));
        }

        System.out.printf("[Worker-%d] PLAY player=%s game=%s bet=%.2f result=%.2f%n",
                workerId, playerId, gameName, betAmount, playerResult);

        out.writeUTF(Protocol.build(Protocol.OK, String.format(Locale.US, "%.2f", playerResult)));
        out.flush();
    }

    private static final int SRG_TIMEOUT_MS = 5000; // 5 seconds max wait for SRG

    private int getRandomFromSRG(String gameId, String secret) throws IOException {
        Socket srgSocket = new Socket();
        try {
            // Connect with timeout — prevents hanging if SRG is unreachable
            srgSocket.connect(new InetSocketAddress(srgHost, srgPort), SRG_TIMEOUT_MS);
            srgSocket.setSoTimeout(SRG_TIMEOUT_MS);

            ObjectOutputStream srgOut = new ObjectOutputStream(srgSocket.getOutputStream());
            srgOut.flush();
            ObjectInputStream  srgIn  = new ObjectInputStream(srgSocket.getInputStream());

            srgOut.writeUTF(Protocol.build(Protocol.SRG_REQUEST, gameId, secret));
            srgOut.flush();

            String   response     = srgIn.readUTF();
            String[] p            = Protocol.parse(response);
            int      number       = Integer.parseInt(p[1]);
            String   receivedHash = p[2];

            String expectedHash = SecureRandomGenerator.sha256(number + secret);
            if (!expectedHash.equals(receivedHash)) {
                throw new IOException("SRG hash verification FAILED for game " + gameId);
            }
            return number;
        } finally {
            try { srgSocket.close(); } catch (IOException ignored) {}
        }
    }

    private double computeResult(int randomNumber, double betAmount, Game game) {
        int mod100 = randomNumber % 100;
        if (mod100 == 0) {
            return betAmount * game.getJackpot();
        }
        int    idx        = randomNumber % 10;
        double multiplier = game.getMultiplierTable()[idx];
        return betAmount * multiplier;
    }

    // WORKER_RATE
    private void handleRate(String[] parts, ObjectOutputStream out) throws IOException {
        String gameName = parts[1];
        int    newStars;
        try { newStars = Integer.parseInt(parts[3]); }
        catch (NumberFormatException e) {
            out.writeUTF(Protocol.build(Protocol.ERROR, "Invalid stars value"));
            out.flush();
            return;
        }
        if (newStars < 1 || newStars > 5) {
            out.writeUTF(Protocol.build(Protocol.ERROR, "Stars must be 1-5"));
            out.flush();
            return;
        }
        synchronized (lock) {
            Game g = games.get(gameName);
            if (g == null) {
                out.writeUTF(Protocol.build(Protocol.ERROR, "Game not found"));
                out.flush();
                return;
            }
            if (!g.isActive()) {
                out.writeUTF(Protocol.build(Protocol.ERROR,
                        "Cannot rate inactive game: " + gameName));
                out.flush();
                return;
            }
            double total = g.getStars() * g.getNoOfVotes() + newStars;
            g.setNoOfVotes(g.getNoOfVotes() + 1);
            g.setStars(total / g.getNoOfVotes());
        }
        out.writeUTF(Protocol.build(Protocol.OK, "Rating updated"));
        out.flush();
    }


    //WORKER_STATS_PROVIDER_MR
    private void handleStatsProvider(String[] parts, ObjectOutputStream out) throws IOException {
        String mapId = parts[1];

        // Calculate house balance from allBets excluding stress-test bots (SM prefix)
        Map<String, String>  gameProvider   = new LinkedHashMap<>(); // gameName -> providerName
        Map<String, Double>  balanceByGame  = new LinkedHashMap<>();
        synchronized (lock) {
            for (BetRecord bet : allBets) {
                if (!primaryGameNames.contains(bet.getGameName())) continue; // skip replica bets
                if (bet.getPlayerId().startsWith("SM")) continue;            // skip stress-test bots
                gameProvider.put(bet.getGameName(), bet.getProviderName());
                balanceByGame.merge(bet.getGameName(),
                        bet.getBetAmount() - bet.getPlayerResult(), Double::sum);
            }
        }

        // Connect to Reducer OUTSIDE lock — stats are already snapshotted above
        Socket             reducerSocket = new Socket();
        reducerSocket.connect(new InetSocketAddress(reducerHost, reducerPort), 5000);
        reducerSocket.setSoTimeout(5000);
        ObjectOutputStream rOut = new ObjectOutputStream(reducerSocket.getOutputStream());
        rOut.flush();
        ObjectInputStream  rIn  = new ObjectInputStream(reducerSocket.getInputStream());

        for (Map.Entry<String, Double> e : balanceByGame.entrySet()) {
            String gameName    = e.getKey();
            String providerName = gameProvider.get(gameName);
            // MAP_RESULT_PROVIDER~~mapId~~providerName~~gameName~~houseBalance
            rOut.writeUTF(Protocol.build(Protocol.MAP_RESULT_PROVIDER,
                    mapId,
                    providerName,
                    gameName,
                    String.format(Locale.US, "%.2f", e.getValue())));
            rOut.flush();
        }
        rOut.writeUTF(Protocol.build(Protocol.END, mapId));
        rOut.flush();

        rIn.readUTF(); // waiting ACK από Reducer
        reducerSocket.close();

        System.out.println("[Worker-" + workerId + "] MAP provider done, mapId=" + mapId);
        out.writeUTF(Protocol.build(Protocol.OK, "MAP sent to Reducer"));
        out.flush();
    }


    // ── Active Replication handlers (Bonus) ──────────────────────────────────

    // WORKER_ADD_REPLICA~~ownerIndex~~<game transport fields>
    private void handleAddReplica(String[] parts, ObjectOutputStream out) throws IOException {
        int ownerIndex = Integer.parseInt(parts[1]);
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            if (i > 2) sb.append(Protocol.SEP);
            sb.append(parts[i]);
        }
        Game game = Game.fromTransportString(sb.toString());
        synchronized (lock) {
            Game existing = games.get(game.getGameName());
            if (existing != null && existing.isActive()) {
                out.writeUTF(Protocol.build(Protocol.OK, "Replica already stored: " + game.getGameName()));
                out.flush();
                return;
            }
            games.put(game.getGameName(), game);
            houseBalance.put(game.getGameName(), 0.0);
            replicaOwnerMap.put(game.getGameName(), ownerIndex);
        }
        System.out.println("[Worker-" + workerId + "] Replica added: " + game.getGameName() + " (owner=" + ownerIndex + ")");
        out.writeUTF(Protocol.build(Protocol.OK, "Replica added: " + game.getGameName()));
        out.flush();
    }

    // WORKER_SYNC_PLAY~~playerId~~gameName~~betAmount~~playerResult~~houseEarning
    private void handleSyncPlay(String[] parts, ObjectOutputStream out) throws IOException {
        String playerId     = parts[1];
        String gameName     = parts[2];
        double betAmount    = Double.parseDouble(parts[3]);
        double playerResult = Double.parseDouble(parts[4]);
        double houseEarning = Double.parseDouble(parts[5]);
        synchronized (lock) {
            Game g = games.get(gameName);
            String providerName = (g != null) ? g.getProviderName() : "unknown";
            houseBalance.merge(gameName, houseEarning, Double::sum);
            allBets.add(new BetRecord(playerId, gameName, providerName, betAmount, playerResult));
        }
        System.out.println("[Worker-" + workerId + "] SYNC_PLAY recorded: game=" + gameName);
        out.writeUTF(Protocol.build(Protocol.OK, "Synced"));
        out.flush();
    }

    // WORKER_SEARCH_ALL~~minStars~~betCat~~risk  — returns ALL active games (primary + replica)
    // Used for failover when a primary worker is down
    private void handleSearchAll(String[] parts, ObjectOutputStream out) throws IOException {
        String minStarsStr  = parts[1];
        String betCatFilter = parts[2];
        String riskFilter   = parts[3];

        List<Game> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(games.values());
        }

        int found = 0;
        for (Game g : snapshot) {
            if (!g.isActive()) continue;
            if (!matchesFilters(g, minStarsStr, betCatFilter, riskFilter)) continue;
            out.writeUTF(Protocol.build(Protocol.MAP_RESULT, g.toTransportString()));
            out.flush();
            found++;
        }
        out.writeUTF(Protocol.END);
        out.flush();
        System.out.println("[Worker-" + workerId + "] SearchAll (failover) sent " + found + " result(s).");
    }

    // WORKER_STATS_PROVIDER_MR_REPLICA~~mapId~~deadWorkerIndex
    // Emits provider stats only for replica games belonging to the dead primary worker
    private void handleStatsProviderReplica(String[] parts, ObjectOutputStream out) throws IOException {
        String mapId        = parts[1];
        int    deadWorkerIdx = Integer.parseInt(parts[2]);

        List<Game>          gameSnapshot;
        Map<String, Double> balanceSnapshot;
        synchronized (lock) {
            gameSnapshot    = new ArrayList<>(games.values());
            balanceSnapshot = new HashMap<>(houseBalance);
        }

        Socket             reducerSocket = new Socket();
        reducerSocket.connect(new InetSocketAddress(reducerHost, reducerPort), 5000);
        reducerSocket.setSoTimeout(5000);
        ObjectOutputStream rOut = new ObjectOutputStream(reducerSocket.getOutputStream());
        rOut.flush();
        ObjectInputStream  rIn  = new ObjectInputStream(reducerSocket.getInputStream());

        for (Game g : gameSnapshot) {
            Integer owner = replicaOwnerMap.get(g.getGameName());
            if (owner == null || owner != deadWorkerIdx) continue;
            double balance = balanceSnapshot.getOrDefault(g.getGameName(), 0.0);
            rOut.writeUTF(Protocol.build(Protocol.MAP_RESULT_PROVIDER,
                    mapId, g.getProviderName(), g.getGameName(),
                    String.format(Locale.US, "%.2f", balance)));
            rOut.flush();
        }
        rOut.writeUTF(Protocol.build(Protocol.END, mapId));
        rOut.flush();

        rIn.readUTF(); // ACK
        reducerSocket.close();

        System.out.println("[Worker-" + workerId + "] Replica MAP_PROVIDER done, mapId=" + mapId + " deadWorker=" + deadWorkerIdx);
        out.writeUTF(Protocol.build(Protocol.OK, "Replica MAP sent"));
        out.flush();
    }

    // WORKER_STATS_PLAYER_MR_REPLICA~~mapId~~deadWorkerIndex
    // Emits player P&L only for bets on replica games belonging to the dead primary worker
    private void handleStatsPlayerReplica(String[] parts, ObjectOutputStream out) throws IOException {
        String mapId        = parts[1];
        int    deadWorkerIdx = Integer.parseInt(parts[2]);

        Map<String, Double> playerPnL = new LinkedHashMap<>();
        synchronized (lock) {
            for (BetRecord bet : allBets) {
                Integer owner = replicaOwnerMap.get(bet.getGameName());
                if (owner == null || owner != deadWorkerIdx) continue;
                playerPnL.merge(bet.getPlayerId(),
                        bet.getPlayerResult() - bet.getBetAmount(), Double::sum);
            }
        }

        Socket             reducerSocket = new Socket();
        reducerSocket.connect(new InetSocketAddress(reducerHost, reducerPort), 5000);
        reducerSocket.setSoTimeout(5000);
        ObjectOutputStream rOut = new ObjectOutputStream(reducerSocket.getOutputStream());
        rOut.flush();
        ObjectInputStream  rIn  = new ObjectInputStream(reducerSocket.getInputStream());

        for (Map.Entry<String, Double> e : playerPnL.entrySet()) {
            rOut.writeUTF(Protocol.build(Protocol.MAP_RESULT,
                    mapId, e.getKey(),
                    String.format(Locale.US, "%.2f", e.getValue())));
            rOut.flush();
        }
        rOut.writeUTF(Protocol.build(Protocol.END, mapId));
        rOut.flush();

        rIn.readUTF(); // ACK
        reducerSocket.close();

        System.out.println("[Worker-" + workerId + "] Replica MAP_PLAYER done, mapId=" + mapId + " deadWorker=" + deadWorkerIdx);
        out.writeUTF(Protocol.build(Protocol.OK, "Replica MAP sent"));
        out.flush();
    }

    // ─────────────────────────────────────────────────────────────────────────

    // WORKER_STATS_PLAYER
    private void handleStatsPlayer(String[] parts, ObjectOutputStream out) throws IOException {
        String mapId = parts[1];

        Map<String, Double> playerPnL = new LinkedHashMap<>();
        synchronized (lock) {
            for (BetRecord bet : allBets) {
                if (!primaryGameNames.contains(bet.getGameName())) continue; // skip replica bets
                if (bet.getPlayerId().startsWith("SM")) continue;            // skip stress-test bots
                playerPnL.merge(bet.getPlayerId(),
                        bet.getPlayerResult() - bet.getBetAmount(), Double::sum);
            }
        }

        Socket             reducerSocket = new Socket();
        reducerSocket.connect(new InetSocketAddress(reducerHost, reducerPort), 5000);
        reducerSocket.setSoTimeout(5000);
        ObjectOutputStream rOut = new ObjectOutputStream(reducerSocket.getOutputStream());
        rOut.flush();
        ObjectInputStream  rIn  = new ObjectInputStream(reducerSocket.getInputStream());

        for (Map.Entry<String, Double> e : playerPnL.entrySet()) {
            // MAP_RESULT~~mapId~~playerId~~totalPnL
            rOut.writeUTF(Protocol.build(Protocol.MAP_RESULT,
                    mapId,
                    e.getKey(),
                    String.format(Locale.US, "%.2f", e.getValue())));
            rOut.flush();
        }
        rOut.writeUTF(Protocol.build(Protocol.END, mapId));
        rOut.flush();

        rIn.readUTF(); // needs ACK from Reducer
        reducerSocket.close();

        System.out.println("[Worker-" + workerId + "] MAP player done, mapId=" + mapId);
        out.writeUTF(Protocol.build(Protocol.OK, "MAP sent to Reducer"));
        out.flush();
    }

    // WORKER_LEADERBOARD_MR~~mapId
    private void handleLeaderboard(String[] parts, ObjectOutputStream out) throws IOException {
        String mapId = parts[1];
        Map<String, Double> playerPnL = new LinkedHashMap<>();
        synchronized (lock) {
            for (BetRecord bet : allBets) {
                if (!primaryGameNames.contains(bet.getGameName())) continue;
                playerPnL.merge(bet.getPlayerId(),
                        bet.getPlayerResult() - bet.getBetAmount(), Double::sum);
            }
        }
        Socket reducerSocket = new Socket();
        reducerSocket.connect(new java.net.InetSocketAddress(reducerHost, reducerPort), 5000);
        reducerSocket.setSoTimeout(5000);
        ObjectOutputStream rOut = new ObjectOutputStream(reducerSocket.getOutputStream());
        rOut.flush();
        ObjectInputStream rIn = new ObjectInputStream(reducerSocket.getInputStream());
        for (Map.Entry<String, Double> e : playerPnL.entrySet()) {
            rOut.writeUTF(Protocol.build(Protocol.MAP_RESULT, mapId, e.getKey(),
                    String.format(Locale.US, "%.2f", e.getValue())));
            rOut.flush();
        }
        rOut.writeUTF(Protocol.build(Protocol.END, mapId));
        rOut.flush();
        rIn.readUTF();
        reducerSocket.close();
        System.out.println("[Worker-" + workerId + "] LEADERBOARD MAP done, mapId=" + mapId);
        out.writeUTF(Protocol.build(Protocol.OK, "Leaderboard MAP sent"));
        out.flush();
    }

    // WORKER_LEADERBOARD_MR_REPLICA~~mapId~~deadWorkerIndex
    private void handleLeaderboardReplica(String[] parts, ObjectOutputStream out) throws IOException {
        String mapId = parts[1];
        int deadWorkerIdx = Integer.parseInt(parts[2]);
        Map<String, Double> playerPnL = new LinkedHashMap<>();
        synchronized (lock) {
            for (BetRecord bet : allBets) {
                Integer owner = replicaOwnerMap.get(bet.getGameName());
                if (owner == null || owner != deadWorkerIdx) continue;
                playerPnL.merge(bet.getPlayerId(),
                        bet.getPlayerResult() - bet.getBetAmount(), Double::sum);
            }
        }
        Socket reducerSocket = new Socket();
        reducerSocket.connect(new java.net.InetSocketAddress(reducerHost, reducerPort), 5000);
        reducerSocket.setSoTimeout(5000);
        ObjectOutputStream rOut = new ObjectOutputStream(reducerSocket.getOutputStream());
        rOut.flush();
        ObjectInputStream rIn = new ObjectInputStream(reducerSocket.getInputStream());
        for (Map.Entry<String, Double> e : playerPnL.entrySet()) {
            rOut.writeUTF(Protocol.build(Protocol.MAP_RESULT, mapId, e.getKey(),
                    String.format(Locale.US, "%.2f", e.getValue())));
            rOut.flush();
        }
        rOut.writeUTF(Protocol.build(Protocol.END, mapId));
        rOut.flush();
        rIn.readUTF();
        reducerSocket.close();
        System.out.println("[Worker-" + workerId + "] LEADERBOARD REPLICA MAP done, mapId=" + mapId);
        out.writeUTF(Protocol.build(Protocol.OK, "Leaderboard replica MAP sent"));
        out.flush();
    }
}