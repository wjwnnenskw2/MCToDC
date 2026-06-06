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
                e.printStackTrace();
            }
        }
        loadConfig();
    }

    private static void writeDefaultConfig() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {
            writer.write("# MCToDC - Infrastructure Config\n\n");
            writer.write("[general]\nenabled = true\ndebugging = false\nlanguage = \"en_us\" # Available: en_us, zh_tw\nconfigVersion = 30\n\n");
            writer.write("[botConfig]\nbotToken = \"\"\nprintInviteLink = true\nsilentReplies = true\nstatusUpdateInterval = 30\n\n");
            writer.write("[channelsAndWebhooks]\n[channelsAndWebhooks.channels]\nchatChannelID = \"0\"\nconsoleChannelID = \"0\"\n\n");
            writer.write("[channelsAndWebhooks.webhooks]\nchatWebhook = \"\"\nconsoleWebhook = \"\"\n\n");
            writer.write("[chat]\nsendConsoleMessages = false\nsendCommandMessages = false\n\n");
            writer.write("[database]\nuseRemoteSQL = false\nsqlUrl = \"jdbc:mysql://localhost:3306/minecraft_db?useSSL=false&serverTimezone=UTC\"\nsqlUser = \"root\"\nsqlPassword = \"\"\nsqlTableName = \"mctodc_whitelist\"\n");
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
                    String encryptedData = new String(Files.readAllBytes(secretFile.toPath()), StandardCharsets.UTF_8).trim();
                    actualBotToken = decrypt(encryptedData, hwKey);
                } else {
                    logger.error("[MCToDC] Secure container asset (.mctodc-secret) missed. Reset your plain token in toml.");
                }
            } else if (rawToken != null && !rawToken.isEmpty()) {
                actualBotToken = rawToken;
                String encryptedData = encrypt(rawToken, hwKey);
                Files.write(secretFile.toPath(), encryptedData.getBytes(StandardCharsets.UTF_8));

                try {
                    String tomlContent = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
                    String obfuscatedContent = tomlContent.replace("botToken = \"" + rawToken + "\"", "botToken = \"ENCRYPTED_AND_LOADED\"");
                    Files.write(configFile.toPath(), obfuscatedContent.getBytes(StandardCharsets.UTF_8));
                    logger.info("[MCToDC] Plain token encrypted with hardware baseline successfully.");
                } catch (Exception e) {}
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
                    String encryptedPw = new String(Files.readAllBytes(dbSecretFile.toPath()), StandardCharsets.UTF_8).trim();
                    databaseConfig.sqlPassword = decrypt(encryptedPw, hwKey);
                } else {
                    databaseConfig.sqlPassword = "";
                    logger.error("[MCToDC] Secure database asset (.mctodc-db-secret) missed. Reset your plain password in toml.");
                }
            } else if (rawSqlPassword != null && !rawSqlPassword.isEmpty()) {
                databaseConfig.sqlPassword = rawSqlPassword;
                String encryptedPw = encrypt(rawSqlPassword, hwKey);
                Files.write(dbSecretFile.toPath(), encryptedPw.getBytes(StandardCharsets.UTF_8));

                try {
                    String tomlContent = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
                    String obfuscatedContent = tomlContent.replace("sqlPassword = \"\"\"", "sqlPassword = \"ENCRYPTED_AND_LOADED\"")
                                                          .replace("sqlPassword = \"" + rawSqlPassword + "\"", "sqlPassword = \"ENCRYPTED_AND_LOADED\"");
                    Files.write(configFile.toPath(), obfuscatedContent.getBytes(StandardCharsets.UTF_8));
                    logger.info("[MCToDC] Plaintext SQL password has been securely obfuscated on disk.");
                } catch (Exception e) {
                    logger.error("[MCToDC] Failed to overwrite plaintext SQL password: " + e.getMessage());
                }
            } else {
                databaseConfig.sqlPassword = "";
            }

        } catch (Exception e) {
            logger.error("[MCToDC] Config parsing failure: " + e.getMessage());
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