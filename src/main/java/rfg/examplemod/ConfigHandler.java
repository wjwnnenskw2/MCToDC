package rfg.examplemod;

import com.moandjiezana.toml.Toml;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;

public class ConfigHandler {
    private static Logger logger;
    private static Toml config;
    private static File configFile;
    private static String actualBotToken = "";

    public static class GeneralConfig {
        public boolean enabled = true;
        public boolean debugging = false;
        public String language = "en_us";
        public int configVersion = 30;
    }

    public static class BotConfig {
        public boolean printInviteLink = true;
        public boolean silentReplies = true;
        public int statusUpdateInterval = 30;
    }

    public static class ChannelsAndWebhooksConfig {
        public String chatChannelID = "0";
        public String consoleChannelID = "0";
        public String chatWebhook = "";
        public String consoleWebhook = "";
    }

    public static class ChatConfig {
        public boolean sendConsoleMessages = false;
        public boolean sendCommandMessages = false;
    }

    public static class DatabaseConfig {
        public boolean useRemoteSQL = false;
        public String sqlUrl = "jdbc:mysql://localhost:3306/minecraft_db?useSSL=false&serverTimezone=UTC";
        public String sqlUser = "root";
        public String sqlPassword = "";
        public String sqlTableName = "mctodc_whitelist";
    }

    public static GeneralConfig generalConfig = new GeneralConfig();
    public static BotConfig botConfig = new BotConfig();
    public static ChannelsAndWebhooksConfig channelsConfig = new ChannelsAndWebhooksConfig();
    public static ChatConfig chatConfig = new ChatConfig();
    public static DatabaseConfig databaseConfig = new DatabaseConfig();

    public static void init(File configDirectory, Logger log) {
        logger = log;
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }
        configFile = new File(configDirectory, "rfg-discord-bridge.toml");
        if (!configFile.exists()) {
            try {
                writeDefaultConfig();
            } catch (IOException e) {
                logger.error("[MCToDC] Failed to generate configuration file: " + e.getMessage());
            }
        }
        loadConfig();
    }

    private static void writeDefaultConfig() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {
            writer.write("# MCToDC Infrastructure Configuration Profile\n\n");
            
            writer.write("[general]\n");
            writer.write("enabled = true\n");
            writer.write("debugging = false\n");
            writer.write("language = \"en_us\" # Supported: en_us, zh_tw\n");
            writer.write("configVersion = 30\n\n");
            
            writer.write("[botConfig]\n");
            writer.write("botToken = \"\"\n");
            writer.write("printInviteLink = true\n");
            writer.write("silentReplies = true\n");
            writer.write("statusUpdateInterval = 30\n\n");
            
            writer.write("[channelsAndWebhooks]\n");
            writer.write("[channelsAndWebhooks.channels]\n");
            writer.write("chatChannelID = \"0\"\n");
            writer.write("consoleChannelID = \"0\"\n\n");
            writer.write("[channelsAndWebhooks.webhooks]\n");
            writer.write("chatWebhook = \"\"\n");
            writer.write("consoleWebhook = \"\"\n\n");
            
            writer.write("[chat]\n");
            writer.write("sendConsoleMessages = false\n");
            writer.write("sendCommandMessages = false\n\n");
            
            writer.write("[database]\n");
            writer.write("useRemoteSQL = false\n");
            writer.write("sqlUrl = \"jdbc:mysql://localhost:3306/minecraft_db?useSSL=false&serverTimezone=UTC\"\n");
            writer.write("sqlUser = \"root\"\n");
            writer.write("sqlPassword = \"\"\n");
            writer.write("sqlTableName = \"mctodc_whitelist\"\n");
        }
    }

    public static void loadConfig() {
        try {
            config = new Toml().read(configFile);

            generalConfig.enabled = config.getBoolean("general.enabled", true);
            generalConfig.debugging = config.getBoolean("general.debugging", false);
            generalConfig.language = config.getString("general.language", "en_us");
            Long cv = config.getLong("general.configVersion");
            if (cv != null) generalConfig.configVersion = cv.intValue();

            String hwKey = getHardwareKey();
            File secretFile = new File(configFile.getParentFile(), ".mctodc-secret");

            String rawToken = config.getString("botConfig.botToken", "");
            if ("ENCRYPTED_AND_LOADED".equals(rawToken)) {
                if (secretFile.exists()) {
                    try {
                        String encryptedData = new String(Files.readAllBytes(secretFile.toPath()), StandardCharsets.UTF_8).trim();
                        actualBotToken = decrypt(encryptedData, hwKey);
                    } catch (Exception e) {
                        actualBotToken = "";
                        logger.error("[MCToDC] Configuration decryption failed. Hardware environment signature mismatch.");
                        logger.error("[MCToDC] Resolution: Delete '.mctodc-secret' and update the token value in the configuration file.");
                    }
                } else {
                    logger.error("[MCToDC] Security storage file missing. Please update token configuration.");
                }
            } else if (rawToken != null && !rawToken.isEmpty()) {
                actualBotToken = rawToken;
                String encryptedData = encrypt(rawToken, hwKey);
                Files.write(secretFile.toPath(), encryptedData.getBytes(StandardCharsets.UTF_8));

                try {
                    String tomlContent = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
                    String obfuscatedContent = tomlContent.replace("botToken = \"" + rawToken + "\"", "botToken = \"ENCRYPTED_AND_LOADED\"");
                    Files.write(configFile.toPath(), obfuscatedContent.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }

            botConfig.printInviteLink = config.getBoolean("botConfig.printInviteLink", true);
            botConfig.silentReplies = config.getBoolean("botConfig.silentReplies", true);
            Long sui = config.getLong("botConfig.statusUpdateInterval");
            if (sui != null) botConfig.statusUpdateInterval = sui.intValue();

            channelsConfig.chatChannelID = config.getString("channelsAndWebhooks.channels.chatChannelID", "0");
            channelsConfig.consoleChannelID = config.getString("channelsAndWebhooks.channels.consoleChannelID", "0");
            channelsConfig.chatWebhook = config.getString("channelsAndWebhooks.webhooks.chatWebhook", "");
            channelsConfig.consoleWebhook = config.getString("channelsAndWebhooks.webhooks.consoleWebhook", "");

            chatConfig.sendConsoleMessages = config.getBoolean("chat.sendConsoleMessages", false);
            chatConfig.sendCommandMessages = config.getBoolean("chat.sendCommandMessages", false);

            databaseConfig.useRemoteSQL = config.getBoolean("database.useRemoteSQL", false);
            databaseConfig.sqlUrl = config.getString("database.sqlUrl", databaseConfig.sqlUrl);
            databaseConfig.sqlUser = config.getString("database.sqlUser", databaseConfig.sqlUser);
            databaseConfig.sqlTableName = config.getString("database.sqlTableName", databaseConfig.sqlTableName);

            String rawSqlPassword = config.getString("database.sqlPassword", "");
            File dbSecretFile = new File(configFile.getParentFile(), ".mctodc-db-secret");

            if ("ENCRYPTED_AND_LOADED".equals(rawSqlPassword)) {
                if (dbSecretFile.exists()) {
                    try {
                        String encryptedPw = new String(Files.readAllBytes(dbSecretFile.toPath()), StandardCharsets.UTF_8).trim();
                        databaseConfig.sqlPassword = decrypt(encryptedPw, hwKey);
                    } catch (Exception e) {
                        databaseConfig.sqlPassword = "";
                        logger.error("[MCToDC] Database credential decryption failed. Mismatched environment profile.");
                    }
                } else {
                    databaseConfig.sqlPassword = "";
                    logger.error("[MCToDC] Database secure storage file missing. Fallback initiated.");
                }
            } else if (rawSqlPassword != null && !rawSqlPassword.isEmpty()) {
                databaseConfig.sqlPassword = rawSqlPassword;
                String encryptedPw = encrypt(rawSqlPassword, hwKey);
                Files.write(dbSecretFile.toPath(), encryptedPw.getBytes(StandardCharsets.UTF_8));

                try {
                    String tomlContent = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
                    String obfuscatedContent = tomlContent.replace("sqlPassword = \"" + rawSqlPassword + "\"", "sqlPassword = \"ENCRYPTED_AND_LOADED\"");
                    Files.write(configFile.toPath(), obfuscatedContent.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    logger.error("[MCToDC] Encrypted credential writing failed: " + e.getMessage());
                }
            } else {
                databaseConfig.sqlPassword = "";
            }

        } catch (Exception e) {
            logger.error("[MCToDC] Configuration parsing exception: " + e.getMessage());
        }
    }

    public static String getBotToken() {
        return actualBotToken;
    }

    private static String getHardwareKey() throws Exception {
        String rawId = System.getProperty("user.name") + System.getProperty("os.arch") + Runtime.getRuntime().availableProcessors();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha.digest(rawId.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString().substring(0, 16);
    }

    private static String encrypt(String value, String key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String decrypt(String value, String key) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        return new String(cipher.doFinal(Base64.getDecoder().decode(value)), StandardCharsets.UTF_8);
    }

    public static File getConfigFile() { return configFile; }
}