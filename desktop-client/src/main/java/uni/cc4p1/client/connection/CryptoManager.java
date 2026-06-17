package uni.cc4p1.client.connection;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona encriptación E2E usando ECDH (secp256r1) + AES-256/CBC.
 * Cada par de usuarios deriva independientemente una clave AES-256 compartida
 * a partir del secreto DH — el servidor nunca la conoce.
 *
 * Patrón: una instancia por sesión de usuario, compartida entre SenderThread y ReceiverThread.
 */
public class CryptoManager {

    private final KeyPair                         myKeyPair;
    private final ConcurrentHashMap<Short, SecretKey> sessionKeys = new ConcurrentHashMap<>();

    public CryptoManager() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        myKeyPair = kpg.generateKeyPair();
    }

    /** Bytes de la clave pública (formato X.509, ~91 bytes) para enviar al servidor. */
    public byte[] getPublicKeyBytes() {
        return myKeyPair.getPublic().getEncoded();
    }

    /**
     * Deriva la clave AES-256 compartida con peerId a partir de su clave pública.
     * Llamado cuando ReceiverThread recibe un DH_EXCHANGE del servidor.
     */
    public void computeSharedKey(short peerId, byte[] peerPublicKeyBytes) {
        try {
            KeyFactory   kf        = KeyFactory.getInstance("EC");
            PublicKey    peerKey   = kf.generatePublic(new X509EncodedKeySpec(peerPublicKeyBytes));
            KeyAgreement ka        = KeyAgreement.getInstance("ECDH");
            ka.init(myKeyPair.getPrivate());
            ka.doPhase(peerKey, true);
            byte[] secret  = ka.generateSecret();
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secret);
            sessionKeys.put(peerId, new SecretKeySpec(keyBytes, "AES"));
            System.out.println("[Crypto] Clave E2E derivada con usuario #" + Short.toUnsignedInt(peerId));
        } catch (Exception e) {
            System.err.println("[Crypto] Error derivando clave con #" + peerId + ": " + e.getMessage());
        }
    }

    public boolean hasKeyFor(short peerId) {
        return sessionKeys.containsKey(peerId);
    }

    /** Retorna payload cifrado: IV(16B) || ciphertext */
    public byte[] encrypt(short peerId, byte[] plaintext) throws GeneralSecurityException {
        SecretKey key = sessionKeys.get(peerId);
        if (key == null) return plaintext;
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv         = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] result     = new byte[16 + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, 16);
        System.arraycopy(ciphertext, 0, result, 16, ciphertext.length);
        return result;
    }

    /** Descifra payload con formato IV(16B) || ciphertext */
    public byte[] decrypt(short peerId, byte[] data) throws GeneralSecurityException {
        SecretKey key = sessionKeys.get(peerId);
        if (key == null || data.length < 17) return data;
        byte[] iv         = Arrays.copyOfRange(data, 0, 16);
        byte[] ciphertext = Arrays.copyOfRange(data, 16, data.length);
        Cipher cipher     = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(ciphertext);
    }
}
