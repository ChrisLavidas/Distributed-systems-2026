package master;

import shared.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.LinkedHashMap;

public class Master {

    private final int           masterPort;
    private final int           broadcastPort; //broadcast port for persistent player connections (each player can see the actions of the others live)
    private final String        reducerHost;
    private final int           reducerPort;
    private final List<String>  workerHosts = new ArrayList<>();
    private final List<Integer> workerPorts = new ArrayList<>();

    //subscribed player output streams for jackpot broadcast, with "subscribed" we mean players connected  to the broadcast server
    private final List<ObjectOutputStream> subscribedPlayers = new ArrayList<>();
    private final Object  subscribedLock = new Object();

    public Master(int masterPort, int broadcastPort, String reducerHost, int reducerPort,
                  List<String> wHosts, List<Integer> wPorts) {
        this.masterPort    = masterPort;
        this.broadcastPort = broadcastPort;
        this.reducerHost   = reducerHost;
        this.reducerPort   = reducerPort;
        this.workerHosts.addAll(wHosts);
        this.workerPorts.addAll(wPorts);
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Given parameters should have the following order: <masterPort> <broadcastPort> <reducerHost> <reducerPort> <wHost:wPort>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        int broadcastPort = Integer.parseInt(args[1]);
        String reducerHost   = args[2];
        int    reducerPort   = Integer.parseInt(args[3]);

        List<String>  wHosts = new ArrayList<>();
        List<Integer> wPorts = new ArrayList<>();
        for (int i = 4; i < args.length; i++) {
            String[] hp = args[i].split(":");
            wHosts.add(hp[0]);
            wPorts.add(Integer.parseInt(hp[1]));
        }

        Master master = new Master(port, broadcastPort, reducerHost, reducerPort, wHosts, wPorts);
        master.start();
    }

    public void start() {
        System.out.println("[Master] Starting on port " + masterPort
                + " broadcast port=" + broadcastPort
                + " | Workers: " + workerHosts.size());
        new Thread(() -> startBroadcastServer()).start();

        try {
            ServerSocket serverSocket = new ServerSocket(masterPort);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new HandleClient(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void startBroadcastServer() {
        try {
            ServerSocket bcs = new ServerSocket(broadcastPort);
            System.out.println("[Master] Broadcast server ready on port " + broadcastPort);
            while (true) {
                Socket playerSock = bcs.accept();
                new HandleBroadcastSubscriber(playerSock).start();
            }
        } catch (IOException e) {
            System.err.println("[Master] Broadcast server error: " + e.getMessage());
        }
    }
    class HandleBroadcastSubscriber extends Thread {
        private final Socket socket;
        HandleBroadcastSubscriber(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream());

                //if the message is not "SUBSCRIBE", we close the socket
                String   msg = in.readUTF();
                String[] parts = Protocol.parse(msg);
                if (!parts[0].equals(Protocol.SUBSCRIBE)) {
                    socket.close();
                    return;
                }
                String playerId = parts[1];
                System.out.println("[Master] Player " + playerId + " subscribed to broadcasts");

                synchronized (subscribedLock) {
                    subscribedPlayers.add(out);
                }

                // Keep connection alive until player disconnects
                try { while (in.readUTF() != null) {} }
                catch (IOException ignored) {}

            } catch (IOException e) {
                System.err.println("[Master] Subscriber disconnected: " + e.getMessage());
            } finally {
                // player is removed from the broadcast when he disconnects
                try {
                    ObjectOutputStream toRemove = null;
                    try {
                    } catch (Exception ignored) {}
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    // broadcast any bet event to all subscribed players (for live ticker)
    public void broadcastBet(String playerId, String gameName, String bet, String result, boolean jackpot) {
        String type = jackpot ? Protocol.JACKPOT_BROADCAST : Protocol.BET_BROADCAST;
        String msg = Protocol.build(type, playerId, gameName, bet, result);
        List<ObjectOutputStream> dead = new ArrayList<>();
        synchronized (subscribedLock) {
            for (ObjectOutputStream out : subscribedPlayers) {
                try {
                    out.writeUTF(msg);
                    out.flush();
                } catch (IOException e) {
                    dead.add(out);
                }
            }
            subscribedPlayers.removeAll(dead);
        }
        if (!dead.isEmpty())
            System.out.println("[Master] Removed " + dead.size() + " disconnected subscriber(s)");
    }
    public void broadcastJackpot(String playerId, String gameName, String result) {
        broadcastBet(playerId, gameName, "0", result, true);
    }
    private int getWorkerIndex(String gameName) {
        return Math.abs(gameName.hashCode()) % workerHosts.size();
    }

    private String workerHost(String gameName) { return workerHosts.get(getWorkerIndex(gameName)); }
    private int    workerPort(String gameName) { return workerPorts.get(getWorkerIndex(gameName)); }

    private boolean hasReplica() { return workerHosts.size() > 1; }

    private List<Integer> replicaIndices(String gameName) {
        int primary = getWorkerIndex(gameName);
        List<Integer> replicas = new ArrayList<>();
        for (int i = 0; i < workerHosts.size(); i++) {
            if (i != primary) replicas.add(i);
        }
        return replicas;
    }
    private String sendToWorker(String host, int port, String message) throws IOException {
        Socket             worker = new Socket(host, port);
        ObjectOutputStream out    = new ObjectOutputStream(worker.getOutputStream());
        out.flush();
        ObjectInputStream  in     = new ObjectInputStream(worker.getInputStream());
        out.writeUTF(message);
        out.flush();
        String result = in.readUTF();
        worker.close();
        return result;
    }

    private String sendToWorkerSafe(String host, int port, String message) {
        try {
            return sendToWorker(host, port, message);
        } catch (IOException e) {
            System.err.println("[Master] Worker unreachable at " + host + ":" + port + e.getMessage());
            return null;
        }
    }

    private void tryRevivePrimaryFromReplica(String gameName) {
        if (!hasReplica()) return; // No replica available
        int primaryIdx = getWorkerIndex(gameName); //get the index of the primary worker

        for (int repIdx : replicaIndices(gameName)) {
            try {
                String repResult = sendToWorker(
                        workerHosts.get(repIdx), workerPorts.get(repIdx),
                        Protocol.build(Protocol.WORKER_GET_GAME, gameName));
                String[] rp = Protocol.parse(repResult);
                if (!rp[0].equals(Protocol.OK)) continue;

                StringBuilder ts = new StringBuilder();
                for (int i = 1; i < rp.length; i++) {
                    if (i > 1) ts.append(Protocol.SEP);
                    ts.append(rp[i]);
                }
                String addResult = sendToWorkerSafe(
                        workerHosts.get(primaryIdx), workerPorts.get(primaryIdx),
                        Protocol.build(Protocol.WORKER_ADD, ts.toString()));
                if (addResult != null)
                    System.out.println("[Master] Revived primary worker " + primaryIdx
                            + "for game: " + gameName + " from replica worker " + repIdx);
                return;
            } catch (IOException e) {
                System.err.println("[Master] Revival attempt from worker " + repIdx
                        + " failed for " + gameName + ": " + e.getMessage());
            }
        }
        System.err.println("[Master] Revival failed for " + gameName + " since there was no replica available");
    }
    private String sendWithFullFailover(String gameName, String workerCommand) throws IOException {
        String result;
        boolean primaryReachable = true;
        try {
            result = sendToWorker(workerHost(gameName), workerPort(gameName), workerCommand);
        } catch (IOException e) { //primary worker couldn't get the command, it will be resend to the replicas
            System.err.println("[Master] Primary down for " + gameName + ", trying replicas");
            primaryReachable = false;
            result = Protocol.build(Protocol.ERROR, "Primary unreachable");
        }
         // if primary worker is up but has lost its data, a revival will be tried for it to retrieve its data
        if (primaryReachable && Protocol.parse(result)[0].equals(Protocol.ERROR) && hasReplica()) {
            System.err.println("[Master] Primary up but game " + gameName + " missing, attempting revival");
            tryRevivePrimaryFromReplica(gameName);
            try {
                String retried = sendToWorker(workerHost(gameName), workerPort(gameName), workerCommand);
                if (Protocol.parse(retried)[0].equals(Protocol.OK)) return retried;
                result = retried;
            } catch (IOException ignored) {
                primaryReachable = false;
            }
        }

        if (!primaryReachable || Protocol.parse(result)[0].equals(Protocol.ERROR)) {
            for (int repIdx : replicaIndices(gameName)) {
                try {
                    String repResult = sendToWorker(
                            workerHosts.get(repIdx), workerPorts.get(repIdx), workerCommand);
                    if (Protocol.parse(repResult)[0].equals(Protocol.OK)) return repResult;
                    result = repResult;
                } catch (IOException e) { //replica worker couldn't get the command either
                    System.err.println("[Master] Replica worker " + repIdx
                            + " also failed for " + gameName + ": " + e.getMessage());
                }
            }
        }
        return result;
    }

    //MapReduce dispatch
    private int dispatchStatsToWorkers(String mapId, boolean forProvider) {
        int n = workerHosts.size();
        final boolean[] failed = new boolean[n];

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                String cmd = forProvider
                        ? Protocol.WORKER_STATS_PROVIDER_MR
                        : Protocol.WORKER_STATS_PLAYER_MR;
                try {
                    sendToWorker(workerHosts.get(idx), workerPorts.get(idx),
                            Protocol.build(cmd, mapId));
                } catch (IOException e) {
                    System.err.println("[Master] Worker " + idx + " map failed: " + e.getMessage());
                    failed[idx] = true;
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }

        int expected = n;
        if (hasReplica()) {
            for (int i = 0; i < n; i++) {
                if (!failed[i]) continue;
                boolean covered = false;
                for (int j = 1; j < n; j++) {
                    int    repIdx  = (i + j) % n;
                    String repHost = workerHosts.get(repIdx);
                    int    repPort = workerPorts.get(repIdx);
                    String cmd = forProvider
                            ? Protocol.WORKER_STATS_PROVIDER_MR_REPLICA
                            : Protocol.WORKER_STATS_PLAYER_MR_REPLICA;
                    try {
                        sendToWorker(repHost, repPort,
                                Protocol.build(cmd, mapId, String.valueOf(i)));
                        covered = true;
                        break;
                    } catch (IOException e) {
                        System.err.println("[Master] Replica worker " + repIdx + " also failed for dead primary " + i);
                    }
                }
                if (!covered) expected--;
            }
        } else {
            for (int i = 0; i < n; i++) if (failed[i]) expected--;
        }
        return expected;
    }

    // dispatch leaderboard using MapReduce to all workers (player leaderboard based on their P/L)
    private int dispatchLeaderboardToWorkers(String mapId) {
        int n = workerHosts.size();
        final boolean[] failed = new boolean[n];

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    sendToWorker(workerHosts.get(idx), workerPorts.get(idx),
                            Protocol.build(Protocol.WORKER_LEADERBOARD_MR, mapId));
                } catch (IOException e) {
                    System.err.println("[Master] Worker " + idx + " leaderboard map failed: " + e.getMessage());
                    failed[idx] = true;
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }

        int expected = n;
        if (hasReplica()) {
            for (int i = 0; i < n; i++) {
                if (!failed[i]) continue;
                boolean covered = false;
                for (int j = 1; j < n; j++) {
                    int repIdx = (i + j) % n;
                    try {
                        sendToWorker(workerHosts.get(repIdx), workerPorts.get(repIdx),
                                Protocol.build(Protocol.WORKER_LEADERBOARD_MR_REPLICA, mapId, String.valueOf(i)));
                        covered = true;
                        break;
                    } catch (IOException e) {
                        System.err.println("[Master] Replica worker " + repIdx + " also failed for leaderboard");
                    }
                }
                if (!covered) expected--;
            }
        } else {
            for (int i = 0; i < n; i++) if (failed[i]) expected--;
        }
        return expected;
    }

    //function for handling requests from managers or players (clients) (each client has its own thread and the master can receive multiple requests from clients at the same time)
    class HandleClient extends Thread {
        private final Socket socket;

        HandleClient(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                // DH handshake — all traffic on masterPort is encrypted.
                SecureChannel ch;
                try {
                    ch = SecureChannel.serverSide(socket);
                } catch (Exception e) {
                    System.err.println("[Master] Handshake failed: " + e.getMessage());
                    return;
                }

                String   msg   = ch.readUTF();
                String[] parts = Protocol.parse(msg);
                String   cmd   = parts[0];

                System.out.println("[Master] Received: " + cmd);

                switch (cmd) {
                    case Protocol.ADD_GAME:       handleAddGame(parts, ch);       break;
                    case Protocol.REMOVE_GAME:    handleRemoveGame(parts, ch);    break;
                    case Protocol.UPDATE_RISK:    handleUpdateRisk(parts, ch);    break;
                    case Protocol.SEARCH:         handleSearch(parts, ch);        break;
                    case Protocol.PLAY:           handlePlay(parts, ch);          break;
                    case Protocol.ADD_BALANCE:    handleAddBalance(parts, ch);    break;
                    case Protocol.RATE_GAME:      handleRateGame(parts, ch);      break;
                    case Protocol.STATS_PROVIDER: handleStatsProvider(parts, ch); break;
                    case Protocol.STATS_PLAYER:   handleStatsPlayer(parts, ch);   break;
                    case Protocol.CHECK_GAME:     handleCheckGame(parts, ch);     break;
                    case Protocol.CHECK_ANY_GAME: handleCheckAnyGame(ch);         break;
                    case Protocol.LEADERBOARD:    handleLeaderboard(parts, ch);   break;
                    case Protocol.WORKER_STATUS:  handleWorkerStatus(ch);         break;
                    case Protocol.STRESS_TEST:    handleStressTest(parts, ch);    break;
                    default:
                        ch.writeUTF(Protocol.build(Protocol.ERROR, "Unknown: " + cmd));
                }
            } catch (IOException e) {
                System.err.println("[Master] Client error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        // Handler for ADD_GAME
        private void handleAddGame(String[] parts, SecureChannel ch) throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) sb.append(Protocol.SEP);
                sb.append(parts[i]);
            }
            Game game       = Game.fromTransportString(sb.toString());
            int  primaryIdx = getWorkerIndex(game.getGameName());
            System.out.println("[Master] Routing " + game.getGameName() + " to primary worker " + primaryIdx);

            String result = sendToWorker(
                    workerHosts.get(primaryIdx), workerPorts.get(primaryIdx),
                    Protocol.build(Protocol.WORKER_ADD, game.toTransportString()));

            if (hasReplica()) {
                for (int repIdx : replicaIndices(game.getGameName())) {
                    String repResult = sendToWorkerSafe(
                            workerHosts.get(repIdx), workerPorts.get(repIdx),
                            Protocol.build(Protocol.WORKER_ADD_REPLICA,
                                    String.valueOf(primaryIdx), game.toTransportString()));
                    if (repResult != null)
                        System.out.println("[Master] Replica of " + game.getGameName() + " stored on worker " + repIdx);
                }
            }

            ch.writeUTF(result);
        }


        // Handler for CHECK_GAME
        private void handleCheckGame(String[] parts, SecureChannel ch) throws IOException {
            String gameName = parts[1];
            String result = sendWithFullFailover(gameName, Protocol.build(Protocol.WORKER_CHECK, gameName));
            ch.writeUTF(result);
        }

        // Handler for CHECK_ANY_GAME
        // Returns OK if at least one of the workers has a game saved, error if none do
        private void handleCheckAnyGame(SecureChannel ch) throws IOException {
            for (int i = 0; i < workerHosts.size(); i++) {
                try {
                    String result = sendToWorker(workerHosts.get(i), workerPorts.get(i),
                            Protocol.build(Protocol.WORKER_CHECK_ANY));
                    if (Protocol.parse(result)[0].equals(Protocol.OK)) {
                        ch.writeUTF(Protocol.build(Protocol.OK, "Games exist"));
                        return;
                    }
                } catch (IOException e) {
                    System.err.println("[Master] Worker check-any error: " + e.getMessage());
                }
            }
            ch.writeUTF(Protocol.build(Protocol.ERROR, "No active games in the system"));
        }


        // Handler for REMOVE_GAME
        private void handleRemoveGame(String[] parts, SecureChannel ch) throws IOException {
            String gameName  = parts[1];
            String removeCmd = Protocol.build(Protocol.WORKER_REMOVE, gameName);
            String result    = sendWithFullFailover(gameName, removeCmd);

            if (hasReplica()) {
                for (int repIdx : replicaIndices(gameName)) {
                    sendToWorkerSafe(workerHosts.get(repIdx), workerPorts.get(repIdx), removeCmd);
                }
            }
            ch.writeUTF(result);
        }

        // Handler for UPDATE_RISK
        private void handleUpdateRisk(String[] parts, SecureChannel ch) throws IOException {
            String gameName  = parts[1];
            String newRisk   = parts[2];
            String updateCmd = Protocol.build(Protocol.WORKER_UPDATE_RISK, gameName, newRisk);
            String result    = sendWithFullFailover(gameName, updateCmd);

            if (hasReplica()) {
                for (int repIdx : replicaIndices(gameName)) {
                    sendToWorkerSafe(workerHosts.get(repIdx), workerPorts.get(repIdx), updateCmd);
                }
            }
            ch.writeUTF(result);
        }

        // Handler for SEARCH
        private void handleSearch(String[] parts, SecureChannel ch) throws IOException {
            String minStars = parts.length > 1 ? parts[1] : "0";
            String betCat   = parts.length > 2 ? parts[2] : "ANY";
            String risk     = parts.length > 3 ? parts[3] : "ANY";

            List<WorkerSearchThread> threads = new ArrayList<>();
            for (int i = 0; i < workerHosts.size(); i++) {
                WorkerSearchThread t = new WorkerSearchThread(
                        i, workerHosts.get(i), workerPorts.get(i),
                        Protocol.build(Protocol.WORKER_SEARCH, minStars, betCat, risk));
                threads.add(t);
                t.start();
            }

            Map<String, Game> resultMap = new LinkedHashMap<>();
            for (WorkerSearchThread t : threads) {
                try { t.join(); } catch (InterruptedException ignored) {}
                if (!t.hasFailed())
                    for (Game g : t.getResults()) resultMap.put(g.getGameName(), g);
            }

            if (hasReplica()) {
                for (WorkerSearchThread t : threads) {
                    if (!t.hasFailed()) continue;
                    int deadIdx = t.getWorkerIdx();
                    boolean covered = false;
                    for (int j = 1; j < workerHosts.size(); j++) {
                        int repIdx = (deadIdx + j) % workerHosts.size();
                        WorkerSearchThread fallback = new WorkerSearchThread(
                                repIdx, workerHosts.get(repIdx), workerPorts.get(repIdx),
                                Protocol.build(Protocol.WORKER_SEARCH_ALL, minStars, betCat, risk));
                        fallback.start();
                        try { fallback.join(); } catch (InterruptedException ignored) {}
                        if (!fallback.hasFailed()) {
                            for (Game g : fallback.getResults())
                                resultMap.putIfAbsent(g.getGameName(), g);
                            covered = true;
                            break;
                        }
                    }
                    if (!covered)
                        System.err.println("[Master] No replica available for dead worker " + deadIdx);
                }
            }

            for (Game g : resultMap.values()) {
                ch.writeUTF(Protocol.build(Protocol.MAP_RESULT, g.toTransportString()));
            }
            ch.writeUTF(Protocol.END);
            System.out.println("[Master] Search returned " + resultMap.size() + " games");
        }

        // Handler for PLAY
        private void handlePlay(String[] parts, SecureChannel ch) throws IOException {
            String playerId  = parts[1];
            String gameName  = parts[2];
            String betAmount = parts[3];

            String playCmd = Protocol.build(Protocol.WORKER_PLAY, playerId, gameName, betAmount);

            int    usedWorkerIdx    = getWorkerIndex(gameName);
            String result;
            boolean primaryReachable = true;

            try {
                result = sendToWorker(workerHost(gameName), workerPort(gameName), playCmd);
                if (!Protocol.parse(result)[0].equals(Protocol.OK) && hasReplica()) {
                    tryRevivePrimaryFromReplica(gameName);
                    String retried = sendToWorkerSafe(workerHost(gameName), workerPort(gameName), playCmd);
                    if (retried != null) result = retried;
                }
            } catch (IOException e) {
                primaryReachable = false;
                result = Protocol.build(Protocol.ERROR, "Primary unreachable");
            }

            if (!primaryReachable || !Protocol.parse(result)[0].equals(Protocol.OK)) {
                for (int repIdx : replicaIndices(gameName)) {
                    try {
                        String repResult = sendToWorker(
                                workerHosts.get(repIdx), workerPorts.get(repIdx), playCmd);
                        if (Protocol.parse(repResult)[0].equals(Protocol.OK)) {
                            usedWorkerIdx = repIdx;
                            result = repResult;
                            break;
                        }
                    } catch (IOException e) {
                        System.err.println("[Master] Replica worker " + repIdx + " also failed for PLAY " + gameName);
                    }
                }
            }

            // Async sync replicas + jackpot broadcast
            String[] resParts = Protocol.parse(result);
            if (resParts[0].equals(Protocol.OK)) {
                final int    finalUsed       = usedWorkerIdx;
                final String playerResultStr = resParts[1];
                new Thread(() -> {
                    try {
                        double playerResultVal = Double.parseDouble(playerResultStr);
                        double betAmountVal    = Double.parseDouble(betAmount);
                        double houseEarning    = betAmountVal - playerResultVal;

                        // Sync to all other replica workers
                        if (hasReplica()) {
                            String syncMsg = Protocol.build(Protocol.WORKER_SYNC_PLAY,
                                    playerId, gameName, betAmount,
                                    String.format(java.util.Locale.US, "%.2f", playerResultVal),
                                    String.format(java.util.Locale.US, "%.2f", houseEarning));
                            for (int i = 0; i < workerHosts.size(); i++) {
                                if (i == finalUsed) continue;
                                sendToWorkerSafe(workerHosts.get(i), workerPorts.get(i), syncMsg);
                            }
                        }

                        // Broadcast ALL bets to subscribers (for live ticker)
                        boolean isJackpot = (playerResultVal > betAmountVal * 2.0)
                                && (playerResultVal / betAmountVal == Math.round(playerResultVal / betAmountVal));
                        broadcastBet(playerId, gameName,
                                String.format(java.util.Locale.US, "%.2f", betAmountVal),
                                String.format(java.util.Locale.US, "%.2f", playerResultVal),
                                isJackpot);

                    } catch (Exception ex) {
                        System.err.println("[Master] Post-play async error: " + ex.getMessage());
                    }
                }).start();
            }

            ch.writeUTF(result);
        }

        private void handleAddBalance(String[] parts, SecureChannel ch) throws IOException {
            ch.writeUTF(Protocol.build(Protocol.OK, "Balance updated"));
        }

        private void handleRateGame(String[] parts, SecureChannel ch) throws IOException {
            String playerId = parts[1];
            String gameName = parts[2];
            String stars    = parts[3];
            String rateCmd  = Protocol.build(Protocol.WORKER_RATE, gameName, playerId, stars);
            String result   = sendWithFullFailover(gameName, rateCmd);

            if (hasReplica()) {
                for (int repIdx : replicaIndices(gameName)) {
                    sendToWorkerSafe(workerHosts.get(repIdx), workerPorts.get(repIdx), rateCmd);
                }
            }
            ch.writeUTF(result);
        }


        // Handler for STATS_PROVIDER — MapReduce via the Reducer
        private void handleStatsProvider(String[] parts, SecureChannel ch) throws IOException {
            String mapId    = "prov-" + System.currentTimeMillis();
            int    expected = dispatchStatsToWorkers(mapId, true);

            // Reducer connection stays plaintext (internal LAN).
            Socket             reducer = new Socket(reducerHost, reducerPort);
            ObjectOutputStream rOut    = new ObjectOutputStream(reducer.getOutputStream());
            rOut.flush();
            ObjectInputStream  rIn     = new ObjectInputStream(reducer.getInputStream());
            rOut.writeUTF(Protocol.build(Protocol.REDUCE_FETCH, mapId, String.valueOf(expected)));
            rOut.flush();

            Map<String, Map<String, Double>> byProvider = new LinkedHashMap<>();
            String line;
            while (!(line = rIn.readUTF()).equals(Protocol.END)) {
                String[] p = Protocol.parse(line);
                if (p[0].equals(Protocol.MAP_RESULT) && p.length >= 4) {
                    byProvider
                            .computeIfAbsent(p[1], k -> new LinkedHashMap<>())
                            .merge(p[2], Double.parseDouble(p[3].replace(',', '.')), Double::sum);
                }
            }
            reducer.close();

            StringBuilder sb = new StringBuilder();
            if (byProvider.isEmpty()) {
                sb.append("No stats are available");
            } else {
                for (Map.Entry<String, Map<String, Double>> pe : byProvider.entrySet()) {
                    sb.append("Provider: ").append(pe.getKey()).append("\n");
                    double total = 0.0;
                    for (Map.Entry<String, Double> ge : pe.getValue().entrySet()) {
                        sb.append("  ").append(ge.getKey())
                                .append(": ").append(String.format("%+.2f", ge.getValue()))
                                .append(" FUN\n");
                        total += ge.getValue();
                    }
                    sb.append("  Total: ").append(String.format("%+.2f", total)).append(" FUN\n");
                }
            }
            ch.writeUTF(Protocol.build(Protocol.OK, sb.toString().trim()));
        }

        private void handleStatsPlayer(String[] parts, SecureChannel ch) throws IOException {
            String mapId    = "play-" + System.currentTimeMillis();
            int    expected = dispatchStatsToWorkers(mapId, false);

            Socket             reducer = new Socket(reducerHost, reducerPort);
            ObjectOutputStream rOut    = new ObjectOutputStream(reducer.getOutputStream());
            rOut.flush();
            ObjectInputStream  rIn     = new ObjectInputStream(reducer.getInputStream());
            rOut.writeUTF(Protocol.build(Protocol.REDUCE_FETCH, mapId, String.valueOf(expected)));
            rOut.flush();

            Map<String, Double> byPlayer = new LinkedHashMap<>();
            String line;
            while (!(line = rIn.readUTF()).equals(Protocol.END)) {
                String[] p = Protocol.parse(line);
                if (p[0].equals(Protocol.MAP_RESULT) && p.length >= 3) {
                    byPlayer.merge(p[1], Double.parseDouble(p[2].replace(',', '.')), Double::sum);
                }
            }
            reducer.close();

            StringBuilder sb = new StringBuilder();
            if (byPlayer.isEmpty()) {
                sb.append("No stats are available");
            } else {
                for (Map.Entry<String, Double> e : byPlayer.entrySet()) {
                    sb.append("Player ").append(e.getKey())
                            .append(" — Total P/L: ").append(String.format("%+.2f", e.getValue()))
                            .append(" FUN\n");
                }
            }
            ch.writeUTF(Protocol.build(Protocol.OK, sb.toString().trim()));
        }

        private void handleWorkerStatus(SecureChannel ch) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("=== WORKER STATUS ===\n");
            for (int i = 0; i < workerHosts.size(); i++) {
                String host = workerHosts.get(i);
                int    port = workerPorts.get(i);
                try {
                    String result = sendToWorker(host, port,
                            Protocol.build(Protocol.WORKER_PING));
                    String[] p = Protocol.parse(result);
                    if (p[0].equals(Protocol.OK)) {
                        int activeGames = Integer.parseInt(p[1]);
                        int totalBets   = Integer.parseInt(p[2]);
                        sb.append(String.format("Worker %-2d  ● ONLINE   %s:%-5d  Games: %d   Bets: %d%n",
                                i + 1, host, port, activeGames, totalBets));
                    } else {
                        sb.append(String.format("Worker %-2d  ◌ ERROR    %s:%-5d%n", i + 1, host, port));
                    }
                } catch (Exception e) {
                    sb.append(String.format("Worker %-2d  ✗ OFFLINE  %s:%-5d%n", i + 1, host, port));
                }
            }
            ch.writeUTF(Protocol.build(Protocol.OK, sb.toString().trim()));
        }

        private void handleStressTest(String[] parts, SecureChannel ch) throws IOException {
            int n = (parts.length > 1) ? Integer.parseInt(parts[1]) : 50;
            // Worker connections inside stress test are plaintext (internal).
            java.util.List<shared.Game> activeGames = new java.util.ArrayList<>();
            for (int i = 0; i < workerHosts.size(); i++) {
                try {
                    Socket             w    = new Socket(workerHosts.get(i), workerPorts.get(i));
                    ObjectOutputStream wOut = new ObjectOutputStream(w.getOutputStream());
                    wOut.flush();
                    ObjectInputStream  wIn  = new ObjectInputStream(w.getInputStream());
                    wOut.writeUTF(Protocol.build(Protocol.WORKER_SEARCH, "0", "ANY", "any"));
                    wOut.flush();
                    String line;
                    while (!(line = wIn.readUTF()).equals(Protocol.END)) {
                        String[] p = Protocol.parse(line);
                        if (p[0].equals(Protocol.MAP_RESULT)) {
                            StringBuilder ts = new StringBuilder();
                            for (int j = 1; j < p.length; j++) { if (j>1) ts.append(Protocol.SEP); ts.append(p[j]); }
                            activeGames.add(shared.Game.fromTransportString(ts.toString()));
                        }
                    }
                    w.close();
                } catch (Exception ignored) {}
            }

            if (activeGames.isEmpty()) {
                ch.writeUTF(Protocol.build(Protocol.ERROR, "No active games found. Add games first."));
                return;
            }

            java.util.Random rnd = new java.util.Random();
            final int[] wins = {0}, losses = {0};
            final long startTime = System.currentTimeMillis();
            final java.util.List<String> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

            java.util.List<Thread> threads = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int idx = i;
                final shared.Game game = activeGames.get(rnd.nextInt(activeGames.size()));
                final String simPlayer = String.format("SM%04d", idx + 1);
                double betRange = game.getMaxBet() - game.getMinBet();
                final double bet = Math.round((game.getMinBet() + rnd.nextDouble() * betRange) * 100.0) / 100.0;

                Thread t = new Thread(() -> {
                    try {
                        // Route through the Master's own handlePlay (loopback socket) so that:
                        // 1. Replica SYNC_PLAY fires and all Workers stay in sync
                        // 2. broadcastBet fires for the live ticker
                        // 3. Stats appear on all Workers, not just the primary
                        Socket loopback = new Socket("localhost", masterPort);
                        String response;
                        try {
                            SecureChannel loopCh = SecureChannel.clientSide(loopback);
                            loopCh.writeUTF(Protocol.build(Protocol.PLAY, simPlayer,
                                    game.getGameName(),
                                    String.format(java.util.Locale.US, "%.2f", bet)));
                            response = loopCh.readUTF();
                        } finally {
                            loopback.close();
                        }
                        String[] p = Protocol.parse(response);
                        if (p[0].equals(Protocol.OK)) {
                            double result = Double.parseDouble(p[1]);
                            double net    = result - bet;
                            if (net > 0) {
                                synchronized (wins) { wins[0]++; }
                                boolean jackpot = result > bet * 5;
                                results.add(String.format("%s  %-15s  %s%.2f FUN%s",
                                        simPlayer, game.getGameName(),
                                        jackpot ? "JACKPOT +" : "+", net,
                                        jackpot ? " ★" : ""));
                            } else {
                                synchronized (losses) { losses[0]++; }
                                results.add(String.format("%s  %-15s  %.2f FUN",
                                        simPlayer, game.getGameName(), net));
                            }
                        } else {
                            results.add(simPlayer + "  ERROR: " + (p.length > 1 ? p[1] : response));
                        }
                    } catch (Exception e) {
                        results.add(simPlayer + "  ERROR: " + e.getMessage());
                    }
                });
                threads.add(t);
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) { try { t.join(10000); } catch (InterruptedException ignored) {} }

            long elapsed = System.currentTimeMillis() - startTime;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== STRESS TEST: %d concurrent bets ===%n", n));
            sb.append(String.format("Time: %.2fs  |  Wins: %d  |  Losses: %d%n%n",
                    elapsed / 1000.0, wins[0], losses[0]));
            for (String r : results) sb.append(r).append("\n");

            ch.writeUTF(Protocol.build(Protocol.OK, sb.toString().trim()));
        }

        private void handleLeaderboard(String[] parts, SecureChannel ch) throws IOException {
            String mapId    = "lead-" + System.currentTimeMillis();
            int    expected = dispatchLeaderboardToWorkers(mapId);

            Socket             reducer = new Socket(reducerHost, reducerPort);
            ObjectOutputStream rOut    = new ObjectOutputStream(reducer.getOutputStream());
            rOut.flush();
            ObjectInputStream  rIn     = new ObjectInputStream(reducer.getInputStream());
            rOut.writeUTF(Protocol.build(Protocol.REDUCE_FETCH, mapId, String.valueOf(expected)));
            rOut.flush();

            Map<String, Double> byPlayer = new LinkedHashMap<>();
            String line;
            while (!(line = rIn.readUTF()).equals(Protocol.END)) {
                String[] p = Protocol.parse(line);
                if (p[0].equals(Protocol.MAP_RESULT) && p.length >= 3) {
                    byPlayer.merge(p[1], Double.parseDouble(p[2].replace(',', '.')), Double::sum);
                }
            }
            reducer.close();

            // Sort by P/L descending
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(byPlayer.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            StringBuilder sb = new StringBuilder();
            if (sorted.isEmpty()) {
                sb.append("No bets placed yet.");
            } else {
                sb.append("=== LEADERBOARD ===\n");
                int rank = 1;
                for (Map.Entry<String, Double> e : sorted) {
                    sb.append(String.format("#%-3d %-10s  %+.2f FUN%n",
                            rank++, e.getKey(), e.getValue()));
                }
            }
            ch.writeUTF(Protocol.build(Protocol.OK, sb.toString().trim()));
        }
    }

    class WorkerSearchThread extends Thread {
        private final int        workerIdx;
        private final String     host;
        private final int        port;
        private final String     request;
        private final List<Game> results = new ArrayList<>();
        private boolean          failed  = false;

        WorkerSearchThread(int workerIdx, String host, int port, String request) {
            this.workerIdx = workerIdx;
            this.host      = host;
            this.port      = port;
            this.request   = request;
        }

        public void run() {
            try {
                Socket             worker = new Socket(host, port);
                ObjectOutputStream out    = new ObjectOutputStream(worker.getOutputStream());
                out.flush();
                ObjectInputStream  in     = new ObjectInputStream(worker.getInputStream());
                out.writeUTF(request);
                out.flush();

                String line;
                while (!(line = in.readUTF()).equals(Protocol.END)) {
                    String[] p = Protocol.parse(line);
                    if (p[0].equals(Protocol.MAP_RESULT)) {
                        StringBuilder tsb = new StringBuilder();
                        for (int i = 1; i < p.length; i++) {
                            if (i > 1) tsb.append(Protocol.SEP);
                            tsb.append(p[i]);
                        }
                        results.add(Game.fromTransportString(tsb.toString()));
                    }
                }
                worker.close();
            } catch (IOException e) {
                System.err.println("[Master] Worker " + workerIdx + " search error: " + e.getMessage());
                failed = true;
            }
        }

        int        getWorkerIdx() { return workerIdx; }
        boolean    hasFailed()    { return failed; }
        List<Game> getResults()   { return results; }
    }
}