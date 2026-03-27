package dev;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSAUtil {
    private static final String RSA_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        keyGen.initialize(KEY_SIZE);
        return keyGen.generateKeyPair();
    }

    public static String encrypt(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        byte[] dataBytes = data.getBytes("UTF-8");
        int maxLength = KEY_SIZE / 8 - 11;
        int dataLength = dataBytes.length;

        if (dataLength <= maxLength) {
            byte[] encryptedBytes = cipher.doFinal(dataBytes);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } else {
            StringBuilder encryptedData = new StringBuilder();
            int offset = 0;
            while (offset < dataLength) {
                int length = Math.min(maxLength, dataLength - offset);
                byte[] segment = new byte[length];
                System.arraycopy(dataBytes, offset, segment, 0, length);
                byte[] encryptedSegment = cipher.doFinal(segment);
                encryptedData.append(Base64.getEncoder().encodeToString(encryptedSegment));
                if (offset + length < dataLength) {
                    encryptedData.append("|");
                }
                offset += length;
            }
            return encryptedData.toString();
        }
    }

    public static String decrypt(String encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        if (encryptedData.contains("|")) {
            StringBuilder decryptedData = new StringBuilder();
            String[] segments = encryptedData.split("\\|");
            for (String segment : segments) {
                byte[] encryptedBytes = Base64.getDecoder().decode(segment);
                byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
                decryptedData.append(new String(decryptedBytes, "UTF-8"));
            }
            return decryptedData.toString();
        } else {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, "UTF-8");
        }
    }

    public static String publicKeyToString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static PublicKey stringToPublicKey(String keyStr) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyStr);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        return keyFactory.generatePublic(keySpec);
    }

    public static String privateKeyToString(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public static PrivateKey stringToPrivateKey(String keyStr) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyStr);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        return keyFactory.generatePrivate(keySpec);
    }
}