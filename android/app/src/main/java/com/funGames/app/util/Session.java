package com.funGames.app.util;

import com.funGames.app.net.MasterClient;

/**
 * In-memory session holder. No persistence — matches the project's
 * "memory only" rule. A singleton avoids threading state through Intents.
 *
 * Supports two roles: PLAYER and MANAGER. Both use the same Master TCP
 * connection; only the available operations differ in the UI layer.
 */
public class Session {

    public enum Role { PLAYER, MANAGER }

    private static Session INSTANCE;

    private Role   role;
    private String userId;       // playerId OR managerId, depending on role
    private double balance;      // meaningful only for PLAYER
    private String masterHost;
    private int    masterPort;
    private int    broadcastPort; // NEW
    private MasterClient client;

    public static synchronized Session get() {
        if (INSTANCE == null) INSTANCE = new Session();
        return INSTANCE;
    }

    /** Initialise for a PLAYER. */
    public void initPlayer(String playerId, double initialBalance,
                           String masterHost, int masterPort, int broadcastPort) {
        this.role          = Role.PLAYER;
        this.userId        = playerId;
        this.balance       = initialBalance;
        this.masterHost    = masterHost;
        this.masterPort    = masterPort;
        this.broadcastPort = broadcastPort;
        this.client        = new MasterClient(masterHost, masterPort, broadcastPort);
    }

    /** Initialise for a MANAGER. */
    public void initManager(String managerId,
                            String masterHost, int masterPort) {
        this.role       = Role.MANAGER;
        this.userId     = managerId;
        this.balance    = 0d;                 // N/A for manager
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.client     = new MasterClient(masterHost, masterPort);
    }

    // ---- Back-compat with existing player code --------------------------
    // The existing MainActivity / LoginActivity call init(...) and
    // getPlayerId(). We keep those working.
    public void init(String playerId, double initialBalance,
                     String masterHost, int masterPort) {
        initPlayer(playerId, initialBalance, masterHost, masterPort, masterPort + 10);
    }
    public String getPlayerId() { return userId; }

    // ---- New manager-friendly accessors ---------------------------------
    public Role   getRole()       { return role; }
    public String getManagerId()  { return userId; }
    public String getUserId()     { return userId; }
    public boolean isManager()    { return role == Role.MANAGER; }
    public boolean isPlayer()     { return role == Role.PLAYER; }

    public double       getBalance()       { return balance; }
    public String       getMasterHost()    { return masterHost; }
    public int          getMasterPort()    { return masterPort; }
    public int          getBroadcastPort() { return broadcastPort; }
    public MasterClient getClient()     { return client; }

    public synchronized void setBalance(double b) { this.balance = b; }
    public synchronized void addToBalance(double delta) { this.balance += delta; }

    public boolean isInitialized() {
        return client != null && userId != null;
    }
}
