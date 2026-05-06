package com.funGames.app.net;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Android counterpart of the backend SecureChannel.
 *
 * Performs a Diffie-Hellman key exchange (RFC 2409 Group 2, 1024-bit MODP)
 * then encrypts every message with AES-128-CBC (random IV per message).
 *
 * Used in MasterClient for all traffic on the masterPort.
 * The broadcastPort (jackpot notifications) stays plaintext.
 */
public class SecureChannel {

    // RFC 2409 Oakley Group 2 — identical to the backend constant.
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

    /** Android is always the client: sends its public key first. */
    public static SecureChannel clientSide(Socket socket) throws Exception {
        return new SecureChannel(socket);
    }

    private SecureChannel(Socket socket) throws Exception {
        this.dOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.dIn  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.aesKey = doHandshake();
    }

    private SecretKeySpec doHandshake() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(new DHParameterSpec(DH_P, DH_G));
        KeyPair myKP     = kpg.generateKeyPair();
        byte[]  myPubEnc = myKP.getPublic().getEncoded();

        // Client sends first, then waits for server.
        dOut.writeInt(myPubEnc.length);
        dOut.write(myPubEnc);
        dOut.flush();

        int    len         = dIn.readInt();
        byte[] otherPubEnc = new byte[len];
        dIn.readFully(otherPubEnc);

        PublicKey otherPub = KeyFactory.getInstance("DH")
                .generatePublic(new X509EncodedKeySpec(otherPubEnc));
        KeyAgreement ka = KeyAgreement.getInstance("DH");
        ka.init(myKP.getPrivate());
        ka.doPhase(otherPub, true);
        byte[] shared  = ka.generateSecret();
        byte[] digest  = MessageDigest.getInstance("SHA-256").digest(shared);
        return new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
    }

    /** Encrypts {@code msg} with AES-128-CBC (fresh IV) and writes it. */
    public void writeUTF(String msg) throws IOException {
        try {
            byte[] iv = new byte[16];
            rng.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
            byte[] ct = c.doFinal(msg.getBytes("UTF-8"));
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
