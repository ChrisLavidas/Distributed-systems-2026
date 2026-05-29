package reducer;

import shared.Protocol;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Locale;

//Accepts two types of connections: from Worker (Map phase) and from Master (Reduce phase)
public class Reducer {

    private static final int DEFAULT_PORT = 7000;

    // mapId -> { key -> accumulated value }  (player stats)
    private final Map<String, Map<String, Double>> store = new HashMap<>();

    // mapId -> { provider -> { game -> balance } }  (provider stats)
    private final Map<String, Map<String, Map<String, Double>>> providerStore = new HashMap<>();

    // how many Workers have sent END per mapId
    private final Map<String, Integer> completedWorkers = new HashMap<>();

    // query type per mapId: "player" or "provider"
    private final Map<String, String> queryType = new HashMap<>();

    private final Object lock = new Object();

    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new Reducer().start(port); //reducer starts working
    }

    public void start(int port) {
        System.out.println("[Reducer] Starting on port " + port);
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                Socket s = serverSocket.accept();
                new HandleConnection(s).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class HandleConnection extends Thread {
        private final Socket socket;
        HandleConnection(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream());

                String   firstLine = in.readUTF();
                String[] parts     = Protocol.parse(firstLine);

                if (parts[0].equals(Protocol.REDUCE_FETCH)) {
                    handleReduceFetch(parts, out);
                } else {
                    // Worker sends MAP results
                    handleMapInput(firstLine, in);
                    out.writeUTF(Protocol.build(Protocol.OK, "MAP received"));
                    out.flush();
                }
            } catch (IOException e) {
                System.err.println("[Reducer] Connection error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        //Processes incoming map results from a Worker - for player/provider results
        //The loop continues until an END signal for the specific mapId is received
        private void handleMapInput(String firstLine, ObjectInputStream in) throws IOException {
            String currentLine = firstLine;

            while (true) {
                String[] p = Protocol.parse(currentLine);

                if (p[0].equals(Protocol.END) && p.length > 1) { //worker finished his job
                    // END~~mapId
                    String mapId = p[1];
                    synchronized (lock) {
                        completedWorkers.merge(mapId, 1, Integer::sum);
                        lock.notifyAll(); //wake up master to check if all workers have finished their jobs to then produce the player/provider statistics/results
                    }
                    System.out.println("[Reducer] Worker finished mapId=" + mapId
                            + " total=" + completedWorkers.get(mapId));
                    break;
                }

                if (p[0].equals(Protocol.MAP_RESULT) && p.length >= 4) {
                    // MAP_RESULT~~mapId~~playerId~~pnl
                    String mId = p[1];
                    String key = p[2];
                    double val = Double.parseDouble(p[3].replace(',', '.'));
                    synchronized (lock) {
                        store.computeIfAbsent(mId, k -> new LinkedHashMap<>())
                                .merge(key, val, Double::sum);
                        queryType.put(mId, "player");
                    }

                } else if (p[0].equals(Protocol.MAP_RESULT_PROVIDER) && p.length >= 5) {
                    // MAP_RESULT_PROVIDER~~mapId~~provider~~game~~balance
                    String mId      = p[1];
                    String provider = p[2];
                    String game     = p[3];
                    double balance  = Double.parseDouble(p[4].replace(',', '.'));
                    synchronized (lock) {
                        providerStore
                                .computeIfAbsent(mId, k -> new LinkedHashMap<>())
                                .computeIfAbsent(provider, k -> new LinkedHashMap<>())
                                .merge(game, balance, Double::sum);
                        queryType.put(mId, "provider");
                    }
                }

                currentLine = in.readUTF();
            }
        }

        /**
         * Handles a REDUCE_FETCH request.
         * This method blocks using wait/notify until all expected Workers have
         * finished their map tasks for the given mapId.
         * Once synchronization is complete, it transmits the aggregated (reduced) results
         * back to the requester and performs cleanup.
         */
        private void handleReduceFetch(String[] parts, ObjectOutputStream out) throws IOException {
            String mapId           = parts[1];
            int    expectedWorkers = Integer.parseInt(parts[2]);

            System.out.println("[Reducer] REDUCE_FETCH mapId=" + mapId
                    + " | waiting for " + expectedWorkers + " worker(s)...");

            synchronized (lock) {
                while (completedWorkers.getOrDefault(mapId, 0) < expectedWorkers) { //check if there is any worker who hasn't finished its task before trying to print the by player/by provider results (would maybe have wrong data if the results were printed while at least one worker was editing them)
                    try { lock.wait(5000); } catch (InterruptedException ignored) {}
                }
            }

            System.out.println("[Reducer] Reducing mapId=" + mapId);

            String type;
            synchronized (lock) { type = queryType.getOrDefault(mapId, "player"); }

            if ("provider".equals(type)) {
                sendProviderResults(mapId, out);
            } else {
                sendPlayerResults(mapId, out);
            }

            // Data cleaning
            synchronized (lock) {
                store.remove(mapId);
                providerStore.remove(mapId);
                completedWorkers.remove(mapId);
                queryType.remove(mapId);
            }
        }

        private void sendPlayerResults(String mapId, ObjectOutputStream out) throws IOException {
            Map<String, Double> playerMap;
            synchronized (lock) {
                playerMap = store.getOrDefault(mapId, new LinkedHashMap<>());
            }
            for (Map.Entry<String, Double> e : playerMap.entrySet()) {
                out.writeUTF(Protocol.build(Protocol.MAP_RESULT,
                        e.getKey(),
                        String.format(Locale.US, "%.2f", e.getValue())));
                out.flush();
            }
            out.writeUTF(Protocol.END);
            out.flush();
        }

        private void sendProviderResults(String mapId, ObjectOutputStream out) throws IOException {
            Map<String, Map<String, Double>> pMap;
            synchronized (lock) {
                pMap = providerStore.getOrDefault(mapId, new LinkedHashMap<>());
            }
            for (Map.Entry<String, Map<String, Double>> pe : pMap.entrySet()) {
                for (Map.Entry<String, Double> ge : pe.getValue().entrySet()) {
                    out.writeUTF(Protocol.build(Protocol.MAP_RESULT,
                            pe.getKey(),
                            ge.getKey(),
                            String.format(Locale.US, "%.2f", ge.getValue())));
                    out.flush();
                }
            }
            out.writeUTF(Protocol.END);
            out.flush();
        }
    }
}