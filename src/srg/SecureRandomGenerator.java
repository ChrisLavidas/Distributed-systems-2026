package srg;

import shared.Protocol;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
//Each game has one producer thread that keeps a buffer of random numbers full.
//When a Worker requests a number, it is taken (consumed) from that buffer

//Example:
//Request  : SRG_REQUEST~~gameId~~secret
//Response : SRG_RESPONSE~~number~~sha256(number+secret)

public class SecureRandomGenerator {

    private static final int DEFAULT_PORT = 6000;
    private static final int BUFFER_SIZE  = 50;

    // gameId -> GameBuffer  (access synchronized on buffersLock)
    private static final Map<String, GameBuffer> buffers     = new HashMap<>();
    private static final Object                  buffersLock = new Object();

    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        System.out.println("[SRG] Starting on port " + port);

        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                Socket workerSocket = serverSocket.accept();
                new HandleWorker(workerSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // buffer per game
    static class GameBuffer {
        private final Queue<Integer> queue   = new LinkedList<>();
        private final String         secret;

        GameBuffer(String gameId, String secret) {
            this.secret = secret;
            // Producer thread: keep buffer filled up to BUFFER_SIZE
            Thread producer = new Thread() {
                public void run() {
                    SecureRandom rng = new SecureRandom();
                    while (!Thread.currentThread().isInterrupted()) {
                        synchronized (GameBuffer.this) {
                            while (queue.size() >= BUFFER_SIZE) {
                                try { GameBuffer.this.wait(); }
                                catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                            queue.add(Math.abs(rng.nextInt()));
                            GameBuffer.this.notifyAll();
                        }
                    }
                }
            };
            producer.setDaemon(true);
            producer.start();
            System.out.println("[SRG] Producer started for game: " + gameId);
        }

        // Consumer: blocks until a number is available
        synchronized int consume() throws InterruptedException {
            while (queue.isEmpty()) wait();
            int n = queue.poll();
            notifyAll();
            return n;
        }

        String getSecret() { return secret; }
    }

    // Worker connection handler
    static class HandleWorker extends Thread {
        private final Socket socket;

        HandleWorker(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream());

                // SRG_REQUEST~~gameId~~secret
                String   request = in.readUTF();
                String[] parts   = Protocol.parse(request);
                String   gameId  = parts[1];
                String   secret  = parts[2];

                // Create buffer for this game if first time seen
                GameBuffer buf;
                synchronized (buffersLock) { // ftiaxnoyme ta locks οπως του srg με διαφορετικα ονόματα
                    buf = buffers.get(gameId);
                    if (buf == null) {
                        buf = new GameBuffer(gameId, secret);
                        buffers.put(gameId, buf);
                    }
                }

                int    number = buf.consume();
                String hash   = sha256(number + buf.getSecret());

                out.writeUTF(Protocol.build(Protocol.SRG_RESPONSE,
                             String.valueOf(number), hash));
                out.flush();

            } catch (IOException | InterruptedException e) {
                System.err.println("[SRG] Handler error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // SHA-256 utility (also used by Worker to verify),

    public static String sha256(String input) {
        try {
            MessageDigest md    = MessageDigest.getInstance("SHA-256");
            byte[]        bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb    = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}
