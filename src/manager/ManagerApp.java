package manager;

import shared.*;
import java.io.*;
import java.net.*;
import java.util.Scanner;


public class ManagerApp {
    private static final String BACK = "__BACK__";

    private final String masterHost;
    private final int    masterPort;
    private final String managerId;

    public ManagerApp(String masterHost, int masterPort, String managerId) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.managerId= managerId;
    }

    public static void main(String[] args) {
        String master_Host = (args.length > 0) ? args[0] : "localhost"; //default parameters if no given host/port (applies for all the other members of the system)
        int master_Port = (args.length > 1) ? Integer.parseInt(args[1]) : 5000;
        String managerId = args[2];

        // Validate managerId: exactly 2 uppercase letters followed by 4 digits (for example AK1214)
        if (!managerId.matches("[A-Z]{2}[0-9]{4}")) {
            System.err.println("Error: invalid manager ID '" + managerId );
            System.err.println("Manager ID must be exactly 2 uppercase letters followed by 4 digits. Please try again!");
            System.exit(1);
        }

        new ManagerApp(master_Host, master_Port, managerId).run();
    }

    public void run() {
        Scanner sc = new Scanner(System.in);
        System.out.println("\nManager with id " + managerId + " is now open on port " + masterPort + " and connected with the master");

        while (true) {
            printMenu();
            String choice = readMenuChoiceNoBack(sc, "Choice: ",
                    new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8"});

            switch (choice) {
                case "1": addGame(sc);       break;
                case "2": removeGame(sc);    break;
                case "3": updateRisk(sc);    break;
                case "4": statsProvider(sc); break;
                case "5": statsPlayer(sc);   break;
                case "6": leaderboard(sc);   break;
                case "7": workerStatus(sc); break;
                case "8": stressTest(sc);   break;
                case "0":
                    System.out.println("Thank you for your service! Goodbye!");
                    return;
            }
        }
    }

    private void printMenu() {
        System.out.println("--- MANAGER MENU ---");
        System.out.println("1. Add game (from JSON file)");
        System.out.println("2. Remove game");
        System.out.println("3. Update game risk level");
        System.out.println("4. Show stats by Provider");
        System.out.println("5. Show stats by Player");
        System.out.println("6. Leaderboard (all players ranked by P/L)");
        System.out.println("7. Worker Health Status");
        System.out.println("8. Run Stress Test");
        System.out.println("0. Exit");
    }

    // 1.Add game
    private void addGame(Scanner sc) {
        // Loop: keep adding games until the manager says No
        while (true) {
            String path = readNonEmptyOrBack(sc, "JSON file path: ");
            if (path.equals(BACK)) return; // back → main menu

            try {
                Game game = JsonGameParser.parseFile(path);
                String response = sendToMaster(Protocol.build(Protocol.ADD_GAME,
                        game.toTransportString()));
                String[] p = Protocol.parse(response);
                String body = (p.length > 1 ? p[1] : response);
                if (p[0].equals(Protocol.ERROR)) {
                    System.out.println("! " + body);
                } else {
                    System.out.println("Parsed game: " + game);
                    System.out.println(body);
                }
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Ask whether to add another game
            if (!askYesNo(sc, "Add another game?")) break;
        }
    }

    // 2.Remove game
    private void removeGame(Scanner sc) {
        // Loop: keep removing games until the manager says No
        while (true) {
            // Check that at least one active game exists before asking for a name
            if (!checkAtLeastOneGameExists()) return; // error already printed

            String gameName = readNonEmptyOrBack(sc, "Game name: ");
            if (gameName.equals(BACK)) return; // back → main menu

            try {
                String response = sendToMaster(Protocol.build(Protocol.REMOVE_GAME, gameName));
                printResponse(response);
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Ask whether to remove another game
            if (!askYesNo(sc, "Remove another game?")) break;
        }
    }

    // 3.Update risk
    private void updateRisk(Scanner sc) {
        // Loop: keep updating until the manager says No
        while (true) {
            // State machine: 0=game name, 1=risk level, 2=done
            int step = 0;
            String gameName = null, newRisk = null;

            while (step < 2) {
                switch (step) {
                    case 0: {
                        String v = readNonEmptyOrBack(sc, "Game name: ");
                        if (v.equals(BACK)) return; // back → main menu
                        if (!checkGameExists(v)) break; // error printed, re-ask game name
                        gameName = v;
                        step = 1;
                        break;
                    }
                    case 1: {
                        String v = readRiskLevel(sc);
                        if (v.equals(BACK)) { step = 0; break; } // back → re-pick game
                        newRisk = v;
                        step = 2;
                        break;
                    }
                }
            }

            try {
                String response = sendToMaster(Protocol.build(Protocol.UPDATE_RISK, gameName, newRisk));
                printResponse(response);
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }

            // Ask whether to do another update (same or different game)
            if (!askYesNo(sc, "Perform another update?")) break;
        }
    }


    //Asks the Master whether at least one active game exists.
    private boolean checkAtLeastOneGameExists() {
        try {
            String response = sendToMaster(Protocol.build(Protocol.CHECK_ANY_GAME));
            String[] p = Protocol.parse(response);
            if (p[0].equals(Protocol.ERROR)) {
                System.out.println( (p.length > 1 ? p[1] : response));
                return false;
            }
            return true;
        } catch (IOException e) {
            System.out.println("Network error: " + e.getMessage());
            return false;
        }
    }

    // Checks if the game exists and if it is active.
    private boolean checkGameExists(String gameName) {
        try {
            String response = sendToMaster(Protocol.build(Protocol.CHECK_GAME, gameName));
            String[] p = Protocol.parse(response);
            if (p[0].equals(Protocol.ERROR)) {
                System.out.println("! " + (p.length > 1 ? p[1] : response));
                return false;
            }
            return true;
        } catch (IOException e) {
            System.out.println("Network error: " + e.getMessage());
            return false;
        }
    }

    // 4.Stats by Provider
    private void statsProvider(Scanner sc) {
        try {
            String response = sendToMaster(Protocol.build(Protocol.STATS_PROVIDER));
            String[] p = Protocol.parse(response);
            System.out.println("\n" + (p.length > 1 ? p[1] : response));
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // 6. Leaderboard
    private void leaderboard(Scanner sc) {
        try {
            String response = sendToMaster(Protocol.build(Protocol.LEADERBOARD));
            String[] p = Protocol.parse(response);
            System.out.println("\n" + (p.length > 1 ? p[1] : response));
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // 5.Stats by Player
    private void statsPlayer(Scanner sc) {
        try {
            String response = sendToMaster(Protocol.build(Protocol.STATS_PLAYER));
            String[] p = Protocol.parse(response);
            System.out.println("\n" + (p.length > 1 ? p[1] : response));
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // Displays the Master's response.
    private void printResponse(String response) {
        String[] p = Protocol.parse(response);
        String body = (p.length > 1 ? p[1] : response);
        if (p[0].equals(Protocol.ERROR)) {
            System.out.println(" " + body);
        } else {
            System.out.println(body);
        }
    }

    // Yes/No helper
    //Prints the question and loops until the user types yes or no. Returns true for yes.
    private boolean askYesNo(Scanner sc, String question) {
        while (true) {
            System.out.print("\n" + question + " (Yes / No): ");
            String answer = sc.nextLine().trim().toLowerCase();
            if (answer.equals("yes") || answer.equals("y")) return true;
            if (answer.equals("no")  || answer.equals("n")) return false;
            System.out.println("  Incorrect answer! The only available options are 'Yes' or 'No'! Please try again!");
        }
    }

    // "back"
    private String injectBackHint(String prompt) {
        int close = prompt.lastIndexOf(')');
        if (close > 0) {
            return prompt.substring(0, close) + " / Back" + prompt.substring(close);
        }
        int colon = prompt.lastIndexOf(':');
        if (colon > 0) {
            return prompt.substring(0, colon).trim() + " / Back" + prompt.substring(colon);
        }
        return prompt + " / Back ";
    }

    private String readMenuChoiceNoBack(Scanner sc, String prompt, String[] validOptions) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                System.out.println("  You did not enter anything. Please try again!");
                continue;
            }
            for (String v : validOptions) {
                if (input.equals(v)) return input;
            }
            System.out.println("   Invalid option. Please try again!");
        }
    }

    private String readNonEmptyOrBack(Scanner sc, String prompt) {
        String promptWithBack = injectBackHint(prompt);
        while (true) {
            System.out.print(promptWithBack);
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("Back" ) || input.equalsIgnoreCase("back")) return BACK;
            if (input.isEmpty()) {
                System.out.println("  You did not enter anything. Please try again.");
                continue;
            }
            return input;
        }
    }

    private String readRiskLevel(Scanner sc) {
        while (true) {
            System.out.print("New risk level (low / medium / high / Back): ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("Back") || input.equalsIgnoreCase("back")) return BACK;
            if (input.isEmpty()) {
                System.out.println("   You did not enter anything. Please try again.");
                continue;
            }
            String lower = input.toLowerCase();
            if (lower.equals("low") || lower.equals("medium") || lower.equals("high")) {
                return lower;
            }
            System.out.println("Invalid risk level. Use low, medium or high. Please try again.");
        }
    }

    // 7. Worker Health Status
    private void workerStatus(Scanner sc) {
        try {
            String response = sendToMaster(Protocol.build(Protocol.WORKER_STATUS));
            String[] p = Protocol.parse(response);
            System.out.println("\n" + (p.length > 1 ? p[1] : response));
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // 8. Stress Test
    private void stressTest(Scanner sc) {
        System.out.print("Number of concurrent bets (default 50): ");
        String input = sc.nextLine().trim();
        int n = 50;
        if (!input.isEmpty()) {
            try { n = Integer.parseInt(input); } catch (NumberFormatException ignored) {}
        }
        System.out.println("Running stress test with " + n + " concurrent bets...");
        try {
            String response = sendToMaster(Protocol.build(Protocol.STRESS_TEST, String.valueOf(n)));
            String[] p = Protocol.parse(response);
            if (p[0].equals(Protocol.ERROR)) {
                System.out.println("! " + (p.length > 1 ? p[1] : response));
            } else {
                System.out.println("\n" + (p.length > 1 ? p[1] : response));
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

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