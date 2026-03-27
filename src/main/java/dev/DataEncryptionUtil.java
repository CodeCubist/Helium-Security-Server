
package dev;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class DataEncryptionUtil {
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    
    private static String ENCRYPTION_KEY = null;

    
    public static void setEncryptionKey(String key) {
        if (key != null && !key.trim().isEmpty()) {
            ENCRYPTION_KEY = key.trim();
        }
    }

    
    public static boolean isKeySet() {
        return ENCRYPTION_KEY != null && !ENCRYPTION_KEY.isEmpty();
    }

    
    public static String encrypt(String data) {
        if (!isKeySet()) {
            throw new IllegalStateException("加密密钥未设置，请先调用setEncryptionKey方法设置密钥");
        }

        try {
            
            byte[] keyBytes = ensureKeyLength(ENCRYPTION_KEY.getBytes("UTF-8"));
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            System.err.println("数据加密失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    
    public static String decrypt(String encryptedData) {
        if (!isKeySet()) {
            throw new IllegalStateException("加密密钥未设置，请先调用setEncryptionKey方法设置密钥");
        }

        try {
            
            byte[] keyBytes = ensureKeyLength(ENCRYPTION_KEY.getBytes("UTF-8"));
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            System.err.println("数据解密失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    
    private static byte[] ensureKeyLength(byte[] key) {
        int length = key.length;
        if (length == 16 || length == 24 || length == 32) {
            return key;
        }

        
        byte[] newKey = new byte[16]; 
        System.arraycopy(key, 0, newKey, 0, Math.min(key.length, newKey.length));

        
        for (int i = key.length; i < newKey.length; i++) {
            newKey[i] = 0;
        }

        return newKey;
    }

    
    public static String encryptHwid(String hwid) {
        if (hwid == null || hwid.trim().isEmpty()) {
            return hwid;
        }
        String encrypted = encrypt(hwid.trim());
        return encrypted != null ? encrypted : hwid; 
    }

    
    public static String decryptHwid(String encryptedHwid) {
        if (encryptedHwid == null || encryptedHwid.trim().isEmpty()) {
            return encryptedHwid;
        }
        String decrypted = decrypt(encryptedHwid);
        return decrypted != null ? decrypted : encryptedHwid; 
    }

    
    public static String encryptIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return ip;
        }
        String encrypted = encrypt(ip.trim());
        return encrypted != null ? encrypted : ip; 
    }

    
    public static String decryptIp(String encryptedIp) {
        if (encryptedIp == null || encryptedIp.trim().isEmpty()) {
            return encryptedIp;
        }
        String decrypted = decrypt(encryptedIp);
        return decrypted != null ? decrypted : encryptedIp; 
    }

    
    public static String generateRandomAesKey() {
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[16]; 
        random.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}