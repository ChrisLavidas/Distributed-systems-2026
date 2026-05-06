package player;

import shared.*;
import java.io.*;
import java.net.*;
import java.util.*;


public class DummyPlayer {

    private static final String BACK = "__BACK__";

    private final String masterHost;
    private final int    masterPort;
    private final int    broadcastPort;
    private final String playerId;
    private double balance;

    public DummyPlayer(String masterHost, int masterPort, int broadcastPort, String playerId, double initialBalance) {
        this.masterHost    = masterHost;
        this.masterPort    = masterPort;
        this.broadcastPort = broadcastPort;
        this.playerId      = playerId;
        this.balance       = initialBalance;
    }

    public static void main(String[] args) {
        String master_Host   = (args.length > 0) ? args[0] : "localhost";
        int    master_Port   = (args.length > 1) ? Integer.parseInt(args[1]) : 5000;
        int    broadcastPort = (args.length > 2) ? Integer.parseInt(args[2]) : 5001;
        String playerId      = args[3];
        double initBalance   = Double.parseDouble(args[4]);

        if (!playerId.matches("[A-Z]{2}[0-9]{4}")) {
            System.err.println("Player ID must be exactly 2 uppercase letters followed by 4 digits. Please try again!");
            System.exit(1);
        }

        new DummyPlayer(master_Host, master_Port, broadcastPort, playerId, initBalance).run();
    }

    public void run() {
        Scanner sc = new Scanner(System.in);
        System.out.println("\nPlayer: " + playerId + " | Balance: " + balance + " FUN");

        List<Game> lastSearchResults = new ArrayList<>();

        while (true) {
            printMenu();
            String choice = readMenuChoiceNoBack(sc, "Choice: ",
                    new String[]{"1", "2", "3", "4"});

            switch (choice) {
                case "1":
                    List<Game> newResults = search(sc);
                    if (newResults != null) {
                        lastSearchResults = newResults;
                        if (!lastSearchResults.isEmpty()) {
                            promptPlayAfterSearch(sc, lastSearchResults);
                        }
                    }
                    break;
                case "2": addBalance(sc);                  break;
                case "3": rateGame(sc, lastSearchResults); break;
                case "4":
                    System.out.println("Thanks for playing! Goodbye!");
                    return;
            }
        }
    }

    private void printMenu() {
        System.out.println("--- PLAYER MENU ---");
        System.out.println("1. Search games");
        System.out.println("2. Add balance (FUN tokens)");
        System.out.println("3. Rate a game");
        System.out.println("4. Exit");
    }

    //  1.search()
    private List<Game> search(Scanner sc) {
        String minStars = null, betCat = null, risk = null;
        int step = 0;   // 0=minStars, 1=betCat, 2=risk, 3=done, betcat= bet category

        while (step < 3) {
            switch (step) {
                case 0: {
                    String v = readMinStars(sc);
                    if (v.equals(BACK)) return null;
                    minStars = v;
                    step = 1;
                    break;
                }
                case 1: {
                    String v = readBetCategory(sc);
                    if (v.equals(BACK)) { step = 0; break; }
                    betCat = v;
                    step = 2;
                    break;
                }
                case 2: {
                    String v = readRiskLevel(sc);
                    if (v.equals(BACK)) { step = 1; break; }
                    risk = v;
                    step = 3;
                    break;
                }
            }
        }

        List<Game> results = new ArrayList<>();
        try { //sends the search results to the master
            Socket server = new Socket(masterHost, masterPort);
            SecureChannel ch = SecureChannel.clientSide(server);

            final String request = Protocol.build(Protocol.SEARCH, minStars, betCat, risk);
            ch.writeUTF(request);

            System.out.println("\n--- Search Results ---");
            String line;
            int    idx = 1;
            while (!(line = ch.readUTF()).equals(Protocol.END)) {
                String[] p = Protocol.parse(line);
                if (p[0].equals(Protocol.MAP_RESULT)) {
                    StringBuilder tsb = new StringBuilder();
                    for (int i = 1; i < p.length; i++) {
                        if (i > 1) tsb.append(Protocol.SEP);
                        tsb.append(p[i]);
                    }
                    Game g = Game.fromTransportString(tsb.toString());
                    results.add(g);
                    System.out.printf("[%d] %s%n", idx++, g);
                }
            }
            if (results.isEmpty()) System.out.println("  (no games found)");
            server.close();

        } catch (Exception e) {
            System.out.println("Search error: " + e.getMessage());
        }
        return results;
    }

    // play or go back prompt after the search
    private void promptPlayAfterSearch(Scanner sc, List<Game> lastResults) {
        System.out.println("\nWhat do you want to do?");
        System.out.println("1. Play a game");
        System.out.println("2. Back to main menu");
        String choice = readMenuChoiceNoBack(sc, "Choice: ", new String[]{"1", "2"});
        if (choice.equals("1")) {
            // First play: full selection (pick game + bet)
            Game lastPlayed = play(sc, lastResults);

            // If player pressed back during game/bet selection, return to main menu
            if (lastPlayed == null) return;

            // "Play again?" loop
            while (true) {
                System.out.print("\nPlay again? (Yes / No): ");
                String answer = sc.nextLine().trim().toLowerCase();

                if (answer.equals("yes")) {
                    Game chosenGame = askThisOrAnother(sc, lastResults, lastPlayed);
                    if (chosenGame == null) break; // backed out → stop loop
                    lastPlayed = playWithGame(sc, chosenGame);

                } else if (answer.equals("no")) {
                    break;

                } else {
                    System.out.println("  Incorrect answer! Try again!");
                }
            }
        }
    }


    //(This / Another)
    //"this"    → returns lastPlayed directly (no search needed)
    //"another" → runs a new search, player picks from results;
    private Game askThisOrAnother(Scanner sc, List<Game> lastResults, Game lastPlayed) {
        while (true) {
            System.out.print("This game or another one? (This / Another): ");
            String answer = sc.nextLine().trim().toLowerCase();

            if (answer.equals("this")) {
                System.out.println("Replaying: " + lastPlayed.getGameName()
                        + " | Bet range: " + lastPlayed.getMinBet() + "-" + lastPlayed.getMaxBet()
                        + " FUN | Risk: " + lastPlayed.getRiskLevel()
                        + " | Jackpot: " + lastPlayed.getJackpot() + "x");
                return lastPlayed;

            } else if (answer.equals("another")) {
                List<Game> newResults = search(sc);
                if (newResults == null || newResults.isEmpty()) {
                    System.out.println("No games found. Returning to main menu...");
                    return null;
                }
                Integer v = readIntInRangeOrBack(sc,
                        "Game number from last search (1-" + newResults.size() + "): ",
                        1, newResults.size());
                if (v == null) return null;
                Game chosen = newResults.get(v - 1);
                System.out.println("Selected: " + chosen.getGameName()
                        + " | Bet range: " + chosen.getMinBet() + "-" + chosen.getMaxBet()
                        + " FUN | Risk: " + chosen.getRiskLevel()
                        + " | Jackpot: " + chosen.getJackpot() + "x");
                return chosen;

            } else {
                System.out.println("  Please type 'This' or 'Another'!");
            }
        }
    }

    // play(): (pick game + bet)
    private Game play(Scanner sc, List<Game> lastResults) {
        if (lastResults.isEmpty()) {
            System.out.println("No games loaded. Search is needed first.");
            return null;
        }

        int step = 0;
        Game chosen = null;
        double bet = 0.0;

        while (step < 2) {
            switch (step) {
                case 0: {
                    Integer v = readIntInRangeOrBack(sc,
                            "Game number from last search (1-" + lastResults.size() + "): ",
                            1, lastResults.size());
                    if (v == null) return null;
                    chosen = lastResults.get(v - 1);
                    System.out.println("Selected: " + chosen.getGameName()
                            + " | Bet range: " + chosen.getMinBet() + "-" + chosen.getMaxBet()
                            + " FUN | Risk: " + chosen.getRiskLevel()
                            + " | Jackpot: " + chosen.getJackpot() + "x");
                    step = 1;
                    break;
                }
                case 1: {
                    Double b = readBetAmountOrBack(sc, chosen);
                    if (b == null) { step = 0; break; }
                    bet = b;
                    step = 2;
                    break;
                }
            }
        }

        sendBet(chosen, bet);
        return chosen;
    }

    // playWithGame(): bet only on an already-chosen game. Returns the Game.
    private Game playWithGame(Scanner sc, Game chosen) {
        Double bet = readBetAmountOrBack(sc, chosen);
        if (bet == null) return chosen;
        sendBet(chosen, bet);
        return chosen;
    }

    // Sends the bet to Master and prints the outcome
    private void sendBet(Game chosen, double bet) {
        try {
            String response = sendToMaster(Protocol.build(
                    Protocol.PLAY, playerId, chosen.getGameName(), String.valueOf(bet)));
            String[] p = Protocol.parse(response);

            if (p[0].equals(Protocol.OK)) {
                double result = Double.parseDouble(p[1].replace(',', '.'));
                balance -= bet;
                balance += result;
                if (result > bet) {
                    System.out.printf(" YOU WIN! Result: +%.2f FUN (net: +%.2f)%n", result, result - bet);
                } else if (result > 0) {
                    System.out.printf("Partial return: %.2f FUN (net: -%.2f)%n", result, bet - result);
                } else {
                    System.out.printf("No win. Lost %.2f FUN%n", bet);
                }
                System.out.printf("New balance: %.2f FUN%n", balance);
            } else {
                System.out.println("Error from server: " + (p.length > 1 ? p[1] : response));
            }
        } catch (IOException e) {
            System.out.println("Play error: " + e.getMessage());
        }
    }

    // 2. addBalance()
    private void addBalance(Scanner sc) {
        // Loop: keep adding balance until the player says No
        while (true) {
            Double amount = readPositiveDoubleOrBack(sc, "Amount to add (FUN): ");
            if (amount == null) return; // back → main menu

            balance += amount;
            try {
                sendToMaster(Protocol.build(Protocol.ADD_BALANCE,
                        playerId, String.valueOf(amount)));
            } catch (IOException e) {
                System.out.println("Warning: could not notify server: " + e.getMessage());
            }
            System.out.printf("Balance updated: %.2f FUN%n", balance);

            // Ask whether to add more
            if (!askYesNo(sc, "Add more balance?")) break;
        }
    }

    //3. Rate game
    private void rateGame(Scanner sc, List<Game> lastResults) {
        if (lastResults.isEmpty()) {
            System.out.println("No games loaded. Do a search first.");
            return;
        }

        int step = 0;
        int selectedIdx = -1;
        int stars = 0;

        while (step < 2) {
            switch (step) {
                case 0: {
                    Integer v = readIntInRangeOrBack(sc,
                            "Game number from last search (1-" + lastResults.size() + "): ",
                            1, lastResults.size());
                    if (v == null) return;
                    selectedIdx = v - 1;
                    step = 1;
                    break;
                }
                case 1: {
                    Integer v = readIntInRangeOrBack(sc, "Stars (1-5): ", 1, 5);
                    if (v == null) { step = 0; break; }
                    stars = v;
                    step = 2;
                    break;
                }
            }
        }

        try {
            String response = sendToMaster(Protocol.build(
                    Protocol.RATE_GAME, playerId,
                    lastResults.get(selectedIdx).getGameName(), String.valueOf(stars)));
            System.out.println("Server: " + response);
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    //Yes/No helper
    private boolean askYesNo(Scanner sc, String question) {
        while (true) {
            System.out.print("\n" + question + " (Yes / No): ");
            String answer = sc.nextLine().trim().toLowerCase();
            if (answer.equals("yes") || answer.equals("y")) return true;
            if (answer.equals("no")  || answer.equals("n")) return false;
            System.out.println("  Answear incorrect. Please type 'Yes' or 'No'!");
        }
    }

    //"back"
    private String injectBackHint(String prompt) {
        int close = prompt.lastIndexOf(')');
        if (close > 0) {
            return prompt.substring(0, close) + " | Back" + prompt.substring(close);
        }
        int colon = prompt.lastIndexOf(':');
        if (colon > 0) {
            return prompt.substring(0, colon).trim() + " (back)" + prompt.substring(colon);
        }
        return prompt + " (back) ";
    }

    private String readMenuChoiceNoBack(Scanner sc, String prompt, String[] validOptions) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                System.out.println(" You did not enter anything. Please try again!");
                continue;
            }
            for (String v : validOptions) {
                if (input.equals(v)) return input;
            }
            System.out.println(" Invalid option. Please try again!");
        }
    }

    private Integer readIntInRangeOrBack(Scanner sc, String prompt, int min, int max) {
        String promptWithBack = injectBackHint(prompt);
        while (true) {
            System.out.print(promptWithBack);
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return null;
            if (input.isEmpty()) {
                System.out.println(" You did not enter anything. Please try again!");
                continue;
            }
            try {
                int v = Integer.parseInt(input);
                if (v < min || v > max) {
                    System.out.printf(" Value must be between %d and %d. Please try again!%n", min, max);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println(" Invalid number. Please try again!");
            }
        }
    }

    private Double readPositiveDoubleOrBack(Scanner sc, String prompt) {
        String promptWithBack = injectBackHint(prompt);
        while (true) {
            System.out.print(promptWithBack);
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return null;
            if (input.isEmpty()) {
                System.out.println(" You did not enter anything. Please try again!");
                continue;
            }
            try {
                double v = Double.parseDouble(input.replace(',', '.'));
                if (v <= 0) {
                    System.out.println(" Value must be greater than 0. Please try again!");
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println(" Invalid amount. Please try again!");
            }
        }
    }

    private Double readBetAmountOrBack(Scanner sc, Game chosen) {
        while (true) {
            System.out.print("Bet amount (FUN | Back): ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return null;
            if (input.isEmpty()) {
                System.out.println(" You did not enter anything. Please try again!");
                continue;
            }
            double v;
            try {
                v = Double.parseDouble(input.replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println(" Invalid amount. Please try again!");
                continue;
            }
            if (v <= 0) {
                System.out.println(" Value must be greater than 0. Please try again!");
                continue;
            }
            if (v > balance) {
                System.out.printf(" Insufficient balance (you have %.2f FUN). Please try again!%n", balance);
                continue;
            }
            if (v < chosen.getMinBet() || v > chosen.getMaxBet()) {
                System.out.printf(" Bet must be between %.2f and %.2f FUN. Please try again!%n",
                        chosen.getMinBet(), chosen.getMaxBet());
                continue;
            }
            return v;
        }
    }

    private String readMinStars(Scanner sc) {
        while (true) {
            System.out.print("Min stars (0 = any | Back): ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return BACK;
            if (input.isEmpty()) {
                System.out.println(" You did not enter anything. Please try again!");
                continue;
            }
            if (input.equalsIgnoreCase("any")) return "0";
            try {
                double v = Double.parseDouble(input.replace(',', '.'));
                if (v < 0 || v > 5) {
                    System.out.println(" Value must be between 0 and 4. Please try again!");
                    continue;
                }
                return input;
            } catch (NumberFormatException e) {
                System.out.println(" Invalid number. Please try again!");
            }
        }
    }

    private String readBetCategory(Scanner sc) {
        while (true) {
            System.out.print("Bet category ($ / $$ / $$$ / ANY / Back): ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("Back") || input.equalsIgnoreCase("back")) return BACK;
            if (input.isEmpty()) {
                System.out.println(" You did not enter anything. Please try again!");
                continue;
            }
            String upper = input.toUpperCase();
            if (upper.equals("$") || upper.equals("$$") || upper.equals("$$$") || upper.equals("ANY")) {
                return upper;
            }
            System.out.println(" Invalid category. Use $, $$, $$$ or ANY. Please try again!");
        }
    }

    private String readRiskLevel(Scanner sc) {
        while (true) {
            System.out.print("Risk level (low / medium / high / ANY / Back): ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("Back") || input.equalsIgnoreCase("back")) return BACK;
            if (input.isEmpty()) {
                System.out.println("  You did not enter anything. Please try again!");
                continue;
            }
            String lower = input.toLowerCase();
            if (lower.equals("low") || lower.equals("medium") || lower.equals("high") || lower.equals("any")) {
                return lower;
            }
            System.out.println("  Invalid risk level. Use low, medium, high or ANY. Please try again!");
        }
    }

    // NEW: background thread that listens for jackpot broadcast notifications
    private void startBroadcastListener() {
        Thread listener = new Thread(() -> {
            try {
                Socket             bSock = new Socket(masterHost, broadcastPort);
                ObjectOutputStream bOut  = new ObjectOutputStream(bSock.getOutputStream());
                bOut.flush();
                ObjectInputStream  bIn   = new ObjectInputStream(bSock.getInputStream());
                bOut.writeUTF(Protocol.build(Protocol.SUBSCRIBE, playerId));
                bOut.flush();
                System.out.println("[Broadcast] Subscribed to jackpot notifications.");
                while (true) {
                    String msg = bIn.readUTF();
                    String[] p = Protocol.parse(msg);
                    if (p[0].equals(Protocol.JACKPOT_BROADCAST)) {
                        System.out.println();
                        System.out.println("**** JACKPOT! Player " + p[1] + " won " + p[3] + " FUN on " + p[2] + " ****");
                    }
                }
            } catch (IOException e) {
                System.err.println("[Broadcast] Listener error: " + e.getMessage());
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    // single request/response to Master — traffic is AES-encrypted after DH handshake
    private String sendToMaster(String message) throws IOException {
        Socket socket = new Socket(masterHost, masterPort);
        try {
            SecureChannel ch = SecureChannel.clientSide(socket);
            ch.writeUTF(message);
            return ch.readUTF();
        } catch (Exception e) {
            throw new IOException("Secure connection failed: " + e.getMessage(), e);
        } finally {
            socket.close();
        }
    }
}