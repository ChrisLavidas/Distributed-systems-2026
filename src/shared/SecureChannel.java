package shared;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;

/**
 * Encrypted channel over a plain TCP socket.
 *
 * Handshake  : Diffie-Hellman key agreement (RFC 2409 Group 2, 1024-bit MODP).
 * Encryption : AES-128-CBC with a fresh random IV per message.
 * Key derivation : SHA-256 of the raw DH shared secret → first 16 bytes.
 *
 * Wire format per message: [int: total_bytes][16-byte IV][ciphertext]
 *
 * Used only on the external-facing masterPort (Manager ↔ Master,
 * Player ↔ Master).  Internal channels (Master↔Worker, Worker↔SRG,
 * Worker↔Reducer) remain plain ObjectStream TCP as required.
 */
public class SecureChannel {

    // RFC 2409 Oakley Group 2 — 1024-bit MODP, generator 2.
    // Same well-known parameters on every JVM and Android, so no negotiation needed.
    private static final BigInteger DH_P = new BigInteger(
            "FFFFFFFFFFFFFFFF" +
            "C90FDAA22168C234" +
            "C4C6628B80DC1CD1" +
            "29024E088A67CC74" +
            "020BBEA63B139B22" +
            "514A08798E3404DD" +
            "EF9519B3CD3A431B" +
            "302B0A6DF25F1437" +
            "4FE1356D6D51C245" +
            "E485B576625E7EC6" +
            "F44C42E9A637ED6B" +
            "0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5" +
            "AE9F24117C4B1FE6" +
            "49286651ECE65381" +
            "FFFFFFFFFFFFFFFF", 16);
    private static final BigInteger DH_G = BigInteger.valueOf(2);

    private final DataOutputStream dOut;
    private final DataInputStream  dIn;
    private final SecretKeySpec    aesKey;
    private final SecureRandom     rng = new SecureRandom();

    // ── Factory methods ───────────────────────────────────────────────────────

    /** Server side: waits for client's public key first, then sends its own. */
    public static SecureChannel serverSide(Socket socket) throws Exception {
        return new SecureChannel(socket, false);
    }

    /** Client side: sends its public key first, then waits for server's. */
    public static SecureChannel clientSide(Socket socket) throws Exception {
        return new SecureChannel(socket, true);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    private SecureChannel(Socket socket, boolean isClient) throws Exception {
        // BufferedOutputStream avoids tiny system-call writes during the handshake.
        this.dOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.dIn  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.aesKey = doHandshake(isClient);
    }

    // ── Diffie-Hellman handshake ──────────────────────────────────────────────

    private SecretKeySpec doHandshake(boolean isClient) throws Exception {
        // Generate an ephemeral DH key pair using the shared group parameters.
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(new DHParameterSpec(DH_P, DH_G));
        KeyPair myKP     = kpg.generateKeyPair();
        byte[]  myPubEnc = myKP.getPublic().getEncoded();  // X.509 SubjectPublicKeyInfo

        byte[] otherPubEnc;
        if (isClient) {
            // Client goes first: send, then receive.
            dOut.writeInt(myPubEnc.length);
            dOut.write(myPubEnc);
            dOut.flush();
            int len = dIn.readInt();
            otherPubEnc = new byte[len];
            dIn.readFully(otherPubEnc);
        } else {
            // Server goes second: receive, then send.
            int len = dIn.readInt();
            otherPubEnc = new byte[len];
            dIn.readFully(otherPubEnc);
            dOut.writeInt(myPubEnc.length);
            dOut.write(myPubEnc);
            dOut.flush();
        }

        // Reconstruct the other party's public key and compute the shared secret.
        PublicKey otherPub = KeyFactory.getInstance("DH")
                .generatePublic(new X509EncodedKeySpec(otherPubEnc));
        KeyAgreement ka = KeyAgreement.getInstance("DH");
        ka.init(myKP.getPrivate());
        ka.doPhase(otherPub, true);
        byte[] shared = ka.generateSecret();

        // Derive a 128-bit AES key: SHA-256(shared_secret) → take first 16 bytes.
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(shared);
        return new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
    }

    // ── Encrypted I/O ─────────────────────────────────────────────────────────

    /** Encrypts {@code msg} with AES-128-CBC (fresh IV) and writes it to the socket. */
    public void writeUTF(String msg) throws IOException {
        try {
            byte[] iv = new byte[16];
            rng.nextBytes(iv);                               // random IV per message
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
            byte[] ct = c.doFinal(msg.getBytes("UTF-8"));
            // Wire: [4-byte total length][16-byte IV][ciphertext]
            dOut.writeInt(iv.length + ct.length);
            dOut.write(iv);
            dOut.write(ct);
            dOut.flush();
        } catch (GeneralSecurityException e) {
            throw new IOException("AES encryption failed", e);
        }
    }

    /** Reads one encrypted message, decrypts, and returns the UTF-8 string. */
    public String readUTF() throws IOException {
        try {
            int    total = dIn.readInt();
            byte[] buf   = new byte[total];
            dIn.readFully(buf);
            byte[] iv = Arrays.copyOf(buf, 16);
            byte[] ct = Arrays.copyOfRange(buf, 16, buf.length);
            Cipher c  = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
            return new String(c.doFinal(ct), "UTF-8");
        } catch (GeneralSecurityException e) {
            throw new IOException("AES decryption failed", e);
        }
    }
}
