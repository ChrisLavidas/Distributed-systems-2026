package com.funGames.app.net;

import android.os.Handler;
import android.os.Looper;

import com.funGames.app.model.Game;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP client that talks to the Master EXACTLY the way DummyPlayer does
 * in the backend — ObjectOutputStream / ObjectInputStream with
 * writeUTF / readUTF, Protocol.SEP-joined payloads.
 *
 * All network work happens on a background Thread and results are posted
 * back to the Android main (UI) thread via a Handler. The UI stays
 * interactive while the request is in flight
 */
public class MasterClient {

    //Callback for one-shot request/response calls (PLAY, ADD_BALANCE, RATE_GAME)
    public interface ResponseCallback {
        /** @param ok     true if the Master replied with OK, false for ERROR / exception
         *  @param payload the part after OK/ERROR (e.g. playerResult or error message) */
        void onResult(boolean ok, String payload);
    }

    //Callback for SEARCH which streams multiple MAP_RESULT rows ending with END
    public interface SearchCallback {
        void onSearchResult(List<Game> games);
        void onSearchError(String message);
    }

    // Connect/read timeouts
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS    = 15000;

    private final String  masterHost;
    private final int     masterPort;
    private final int     broadcastPort; // NEW
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Callback for broadcast notifications (jackpots and regular bets)
    public interface JackpotCallback {
        void onJackpot(String winnerId, String gameName, String amount);
    }

    // Callback for all bet broadcasts (for live ticker)
    public interface BetBroadcastCallback {
        void onBet(String playerId, String gameName, String bet, String result, boolean jackpot);
    }

    public MasterClient(String masterHost, int masterPort, int broadcastPort) {
        this.masterHost    = masterHost;
        this.masterPort    = masterPort;
        this.broadcastPort = broadcastPort;
    }

    // Backwards-compatible constructor (no broadcast)
    public MasterClient(String masterHost, int masterPort) {
        this(masterHost, masterPort, masterPort + 1);
    }

    // SEARCH — streamed response: many MAP_RESULT lines, terminated by END
    public void searchAsync(final String minStars,
                            final String betCat,
                            final String risk,
                            final SearchCallback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                Socket s = null;
                try {
                    s = openSocket();
                    ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    out.flush();
                    ObjectInputStream  in  = new ObjectInputStream(s.getInputStream());

                    out.writeUTF(Protocol.build(Protocol.SEARCH, minStars, betCat, risk));
                    out.flush();

                    final List<Game> results = new ArrayList<>();
                    String line;
                    while (!(line = in.readUTF()).equals(Protocol.END)) {
                        String[] p = Protocol.parse(line);
                        if (Protocol.MAP_RESULT.equals(p[0])) {
                            StringBuilder tsb = new StringBuilder();
                            for (int i = 1; i < p.length; i++) {
                                if (i > 1) tsb.append(Protocol.SEP);
                                tsb.append(p[i]);
                            }
                            try {
                                results.add(Game.fromTransportString(tsb.toString()));
                            } catch (Exception parseEx) {
                                // skip malformed row, keep going
                            }
                        }
                    }
                    post(new Runnable() {
                        @Override public void run() { cb.onSearchResult(results); }
                    });
                } catch (final Exception ioe) {
                    post(new Runnable() {
                        @Override public void run() {
                            cb.onSearchError("Network error: " + ioe.getMessage());
                        }
                    });
                } finally {
                    closeQuietly(s);
                }
            }
        }, "MasterClient-search").start();
    }

    // PLAY — single request/response, payload = player result amount
    public void playAsync(String playerId, String gameName, double bet,
                          ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(
                Protocol.PLAY, playerId, gameName, String.valueOf(bet)), cb);
    }

    // ADD_BALANCE — single request/response, informational
    public void addBalanceAsync(String playerId, double amount,
                                ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(
                Protocol.ADD_BALANCE, playerId, String.valueOf(amount)), cb);
    }

    // GET_BALANCE — fetch current balance from server
    public void getBalanceAsync(String playerId, ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.GET_BALANCE, playerId), cb);
    }

    // RATE_GAME — single request/response, 1..5 stars
    public void rateGameAsync(String playerId, String gameName, int stars,
                              ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(
                Protocol.RATE_GAME, playerId, gameName, String.valueOf(stars)), cb);
    }

    // MANAGER OPERATIONS

    //Add a game given its full transport string (produced by Game.toTransportString)
    public void addGameAsync(String gameTransportString, ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.ADD_GAME, gameTransportString), cb);
    }

    // Remove (deactivate) a game by name
    public void removeGameAsync(String gameName, ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.REMOVE_GAME, gameName), cb);
    }

    // Change the risk level of an existing game. newRisk must be "low"/"medium"/"high"
    public void updateRiskAsync(String gameName, String newRisk, ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.UPDATE_RISK, gameName, newRisk), cb);
    }

    // Aggregated stats by provider (MapReduce in the backend)
    public void statsProviderAsync(ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.STATS_PROVIDER), cb);
    }

    // Aggregated stats by player (MapReduce in the backend)
    public void statsPlayerAsync(ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.STATS_PLAYER), cb);
    }

    // Check if any active game exists
    public void checkAnyGameAsync(ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.CHECK_ANY_GAME), cb);
    }

    // Check existence/status of a specific game
    public void checkGameAsync(String gameName, ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.CHECK_GAME, gameName), cb);
    }

    // Jackpot broadcast subscription
    public void subscribeToJackpots(final String playerId,
                                    final JackpotCallback jackpotCb) {
        subscribeToAll(playerId, jackpotCb, null);
    }

    public void subscribeToAll(final String playerId,
                               final JackpotCallback jackpotCb,
                               final BetBroadcastCallback betCb) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(masterHost, broadcastPort), CONNECT_TIMEOUT_MS); //connect with master ports from the backend
                    ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    out.flush();
                    ObjectInputStream  in  = new ObjectInputStream(s.getInputStream());

                    out.writeUTF(Protocol.build(Protocol.SUBSCRIBE, playerId));
                    out.flush();

                    while (true) {
                        final String msg = in.readUTF();
                        final String[] p = Protocol.parse(msg);

                        if (Protocol.JACKPOT_BROADCAST.equals(p[0]) && p.length >= 5) {
                            final String wId  = p[1], gName = p[2], bet = p[3], res = p[4];
                            post(new Runnable() {
                                @Override public void run() {
                                    if (jackpotCb != null) jackpotCb.onJackpot(wId, gName, res);
                                    if (betCb     != null) betCb.onBet(wId, gName, bet, res, true);
                                }
                            });
                        } else if (Protocol.BET_BROADCAST.equals(p[0]) && p.length >= 5) {
                            final String wId = p[1], gName = p[2], bet = p[3], res = p[4];
                            post(new Runnable() {
                                @Override public void run() {
                                    if (betCb != null) betCb.onBet(wId, gName, bet, res, false);
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    // disconnected — stop silently
                }
            }
        }, "MasterClient-broadcast").start();
    }

    // Leaderboard (all players ranked by total P/L)
    public void leaderboardAsync(ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.LEADERBOARD), cb);
    }

    // Worker health status
    public void workerStatusAsync(ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.WORKER_STATUS), cb);
    }

    // Stress test with N concurrent bets
    public void stressTestAsync(int n, ResponseCallback cb) {
        sendOneShotAsync(Protocol.build(Protocol.STRESS_TEST, String.valueOf(n)), cb);
    }

    // Internals - sending commands to master in the backend
    private void sendOneShotAsync(final String message, final ResponseCallback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                Socket s = null;
                try {
                    s = openSocket(); // call method for connection with backend master
                    ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    out.flush();
                    ObjectInputStream  in  = new ObjectInputStream(s.getInputStream());

                    out.writeUTF(message);
                    out.flush();

                    final String raw = in.readUTF();
                    String[] p = Protocol.parse(raw);
                    final boolean ok = Protocol.OK.equals(p[0]);
                    final String payload = (p.length > 1) ? p[1] : "";
                    post(new Runnable() {
                        @Override public void run() { cb.onResult(ok, payload); }
                    });
                } catch (final Exception ioe) {
                    post(new Runnable() {
                        @Override public void run() {
                            cb.onResult(false, "Network error: " + ioe.getMessage());
                        }
                    });
                } finally {
                    closeQuietly(s);
                }
            }
        }, "MasterClient-oneshot").start();
    }

    private Socket openSocket() throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(masterHost, masterPort), CONNECT_TIMEOUT_MS); //connect with master's socket in the backend
        s.setSoTimeout(READ_TIMEOUT_MS);
        return s;
    }

    private void post(Runnable r) { mainHandler.post(r); }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }
}
