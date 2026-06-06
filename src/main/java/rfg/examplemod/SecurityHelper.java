package rfg.examplemod;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Enumeration;

public class SecurityHelper {

    private static SecretKeySpec secretKey;

    // TODO: 動態鎖定硬體特徵生成 128-bit AES 金鑰，不將金鑰明文寫死在代碼中
    private static void generateKey() {
        try {
            StringBuilder sb = new StringBuilder();
            // 撈取本機網卡的 MAC 位址作為物理錨點
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface net = networks.nextElement();
                byte[] mac = net.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) sb.append(String.format("%02X", b));
                }
            }
            // 融合環境變數與處理器規格增加熵值
            sb.append(System.getProperty("user.name", "mctodc"));
            sb.append(Runtime.getRuntime().availableProcessors());

            byte[] key = sb.toString().getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            byte[] key16 = new byte[16];
            System.arraycopy(key, 0, key16, 0, 16); // 截取前 16 位元組符合 AES-128
            secretKey = new SecretKeySpec(key16, "AES");
        } catch (Exception e) {
            // 萬一作業系統權限封鎖硬體撈取，採用安全的記憶體墊底方案
            secretKey = new SecretKeySpec("MCToDCFallBackKey".getBytes(StandardCharsets.UTF_8), "AES");
        }
    }

    public static String encrypt(String strToEncrypt) {
        try {
            if (secretKey == null) generateKey();
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return strToEncrypt;
        }
    }

    public static String decrypt(String strToDecrypt) {
        try {
            if (secretKey == null) generateKey();
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return strToDecrypt; // 如果本來就不是密文，直接返回原樣（相容明文模式）
        }
    }

    // 判斷當前字串是否已經是處理過的 Base64 密文
    public static boolean isEncrypted(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        if (str.length() % 4 != 0) return false;
        String pattern = "^[A-Za-z0-9+/=]+$";
        return str.matches(pattern) && (str.contains("=") || str.length() > 20);
    }
}