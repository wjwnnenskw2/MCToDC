package rfg.examplemod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

<<<<<<< HEAD
import java.io.File;
=======
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(
    modid = RfgExampleMod.MODID,
    name = RfgExampleMod.NAME,
    version = RfgExampleMod.VERSION,
    acceptableRemoteVersions = "*"
)
public class RfgExampleMod {
    public static final String MODID = "mctodc";
    public static final String NAME = "MCToDC";
    public static final String VERSION = "1.0.0";

    @Instance(MODID)
    public static RfgExampleMod instance;
    public static Logger logger;

    private static final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "MCToDC-Async-Executor");
        t.setDaemon(true);
        return t;
    });
    private static final ScheduledExecutorService timerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MCToDC-Timer-Tracker");
        t.setDaemon(true);
        return t;
    });

    public static final java.util.Map<String, String[]> pendingVerifications = new java.util.concurrent.ConcurrentHashMap<>();
    public static com.google.gson.JsonObject boundPlayers = new com.google.gson.JsonObject(); 
    private static File bindFile;
<<<<<<< HEAD
=======
    private static String lastChannelMessageId = "0";
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        File configDir = event.getModConfigurationDirectory();

        try {
            ConfigHandler.init(configDir, logger);
            MessageConfigHandler.init(configDir, logger);
        } catch (Exception e) {
            logger.error("Config initialization failed: " + e.getMessage());
        }

<<<<<<< HEAD
        // 🛑 總開關攔截：若設定為 false，直接退出不進行任何核心綁定與加載
        if (!ConfigHandler.generalConfig.enabled) {
            logger.info(LanguageManager.getLogModuleDisabled());
            return;
        }

=======
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
        bindFile = new File(configDir, "mctodc-bnd.json");
        if (bindFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(bindFile.toPath()), StandardCharsets.UTF_8);
                boundPlayers = new com.google.gson.JsonParser().parse(content).getAsJsonObject();
            } catch (Exception e) { logger.error("Failed to parse local binding database json"); }
        }

        if (ConfigHandler.chatConfig.sendConsoleMessages) {
            setupConsoleAppender();
        }
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
<<<<<<< HEAD
        // 🛑 總開關二次防護
        if (!ConfigHandler.generalConfig.enabled) return;

=======
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
        MinecraftListener minecraftListener = new MinecraftListener();
        MinecraftForge.EVENT_BUS.register(minecraftListener);
        FMLCommonHandler.instance().bus().register(minecraftListener);

        String activeToken = ConfigHandler.getBotToken();
        if (activeToken.isEmpty() || ConfigHandler.channelsConfig.chatChannelID.equals("0")) {
<<<<<<< HEAD
            logger.warn("[MCToDC] Bot connection configurations incomplete, proxy service aborted.");
            return;
        }

        // 🔗 動態解碼產生邀請連結
        if (ConfigHandler.botConfig.printInviteLink) {
            try {
                String clientId = new String(Base64.getDecoder().decode(activeToken.split("\\.")[0]), StandardCharsets.UTF_8);
                logger.info("[MCToDC] 🤖 Bot Invite Link: https://discord.com/oauth2/authorize?client_id=" + clientId + "&permissions=536870912&scope=bot");
            } catch (Exception e) {
                logger.warn("[MCToDC] Could not parse Client ID from token for invite link.");
            }
        }

=======
            logger.warn("Bot connection configurations incomplete, proxy service aborted.");
            return;
        }

>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
        if (ConfigHandler.databaseConfig.useRemoteSQL) {
            executor.submit(() -> setupRemoteSQLTable());
        }

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
<<<<<<< HEAD
                    // 📡 載入多語系初始化日誌
                    logger.info(LanguageManager.getLogGatewayInitializing());
=======
                    // TODO: 將閘道初始化日誌納入多語系規範
                    if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
                        logger.info("[MCToDC] 正在初始化輕量化原生 HTTP REST 閘道...");
                    } else {
                        logger.info("[MCToDC] Initializing light-weight native HTTP REST gateway...");
                    }
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
                    
                    System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
                    System.setProperty("jdk.tls.client.protocols", "TLSv1.2,TLSv1.3");

                    String activeNotice = LanguageManager.getDiscordServerStarted();
                    if (MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordServerStarted != null && !MessageConfigHandler.messages.discordServerStarted.isEmpty()) {
                        activeNotice = MessageConfigHandler.messages.discordServerStarted;
                    }
<<<<<<< HEAD
                    DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, activeNotice, false);

                    // 📡 載入多語系成功日誌
                    logger.info(LanguageManager.getLogGatewaySuccess());
                    
                    // 將輪詢指向已解耦的 DiscordListener
                    timerExecutor.scheduleAtFixedRate(() -> DiscordListener.pollChannelMessages(), 1, 2500, TimeUnit.MILLISECONDS);

                    // 📡 實作狀態更新 (透過修改頻道主題，最低限制 5 分鐘避免 429 Rate Limit)
                    int interval = Math.max(300, ConfigHandler.botConfig.statusUpdateInterval); 
                    timerExecutor.scheduleAtFixedRate(() -> updateDiscordChannelTopic(), 10, interval, TimeUnit.SECONDS);

                } catch (Exception e) { logger.error("[MCToDC] Gateway link error: " + e.getMessage()); }
=======
                    sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, activeNotice);

                    // TODO: 修正圖中第二行硬編碼日誌，對齊多語系分流
                    if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
                        logger.info("[MCToDC] 本地資料庫與安全代理閘道已成功加載完畢。");
                    } else {
                        logger.info("[MCToDC] Server has loaded local database and secure proxy channel successfully.");
                    }
                    
                    timerExecutor.scheduleAtFixedRate(() -> pollChannelMessages(), 1, 2500, TimeUnit.MILLISECONDS);
                } catch (Exception e) { logger.error("Gateway link error: " + e.getMessage()); }
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
            }
        });
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
<<<<<<< HEAD
        if (!ConfigHandler.generalConfig.enabled) return;

        // 📡 載入多語系關閉日誌
        logger.info(LanguageManager.getLogServerShuttingDown());
=======
        // TODO: 修正圖中第一行關服硬編碼日誌，對齊多語系分流
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            logger.info("[MCToDC] 伺服器正在關閉...");
        } else {
            logger.info("[MCToDC] Server is shutting down...");
        }
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)

        String stopNotice = "";
        if (MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordServerStopped != null && !MessageConfigHandler.messages.discordServerStopped.isEmpty()) {
            stopNotice = MessageConfigHandler.messages.discordServerStopped;
        }

        if (stopNotice.isEmpty()) {
            stopNotice = LanguageManager.getDiscordServerStopped();
        }
        
<<<<<<< HEAD
        DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, stopNotice, false);
=======
        sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, stopNotice);
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
        
        try { 
            timerExecutor.shutdown(); 
            executor.shutdown(); 
        } catch (Exception e) {}
    }

    private static void setupRemoteSQLTable() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(ConfigHandler.databaseConfig.sqlUrl, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword)) {
                String query = "CREATE TABLE IF NOT EXISTS `" + ConfigHandler.databaseConfig.sqlTableName + "` (" +
                        "`username` VARCHAR(64) NOT NULL, " +
                        "`uuid` VARCHAR(64) NOT NULL, " +
                        "`discord_id` VARCHAR(64) NOT NULL, " +
                        "`discord_name` VARCHAR(64) NOT NULL, " +
                        "PRIMARY KEY (`username`), KEY `uuid_idx` (`uuid`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.executeUpdate();
                    logger.info("[MCToDC] Relational SQL table structure synchronized successfully.");
                }
            }
        } catch (Exception e) {
            logger.error("[MCToDC] SQL integration initialization error (Will fallback to JSON): " + e.getMessage());
        }
    }

    public static void savePlayerBindingData(String username, String uuid, String discordId, String discordName) {
        if (ConfigHandler.databaseConfig.useRemoteSQL) {
            executor.submit(() -> {
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                    try (Connection conn = DriverManager.getConnection(ConfigHandler.databaseConfig.sqlUrl, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword)) {
                        String query = "REPLACE INTO `" + ConfigHandler.databaseConfig.sqlTableName + "` (`username`, `uuid`, `discord_id`, `discord_name`) VALUES (?, ?, ?, ?);";
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            stmt.setString(1, username);
                            stmt.setString(2, uuid);
                            stmt.setString(3, discordId);
                            stmt.setString(4, discordName);
                            stmt.executeUpdate();
                        }
                    }
                } catch (Exception e) {
                    logger.error("[MCToDC] Failed to execute remote SQL data insertion: " + e.getMessage());
                }
            });
        }
        
        com.google.gson.JsonObject playerData = new com.google.gson.JsonObject();
        playerData.addProperty("discordID", discordId);
        playerData.addProperty("discordName", discordName);
        playerData.addProperty("uuid", uuid);
        boundPlayers.add(username, playerData);
        saveBinds();
    }

<<<<<<< HEAD
=======
    private void pollChannelMessages() {
        String channelId = ConfigHandler.channelsConfig.chatChannelID;
        if (channelId == null || channelId.equals("0") || channelId.isEmpty()) return;
        try {
            URL url = new URL("https://discord.com/api/v9/channels/" + channelId + "/messages?limit=5");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bot " + ConfigHandler.getBotToken());
            conn.setRequestProperty("User-Agent", "DiscordBot (Minecraft 1.7.10, Native-REST)");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                com.google.gson.JsonArray messages = new com.google.gson.JsonParser().parse(sb.toString()).getAsJsonArray();
                if (messages.size() > 0) {
                    if (lastChannelMessageId.equals("0")) {
                        lastChannelMessageId = messages.get(0).getAsJsonObject().get("id").getAsString();
                        return;
                    }

                    for (int i = messages.size() - 1; i >= 0; i--) {
                        com.google.gson.JsonObject msgObj = messages.get(i).getAsJsonObject();
                        String id = msgObj.get("id").getAsString();
                        
                        if (id.compareTo(lastChannelMessageId) > 0) {
                            lastChannelMessageId = id;
                            processIncomingMessage(msgObj);
                        }
                    }
                }
            }
        } catch (Exception e) {}
    }

    private void processIncomingMessage(com.google.gson.JsonObject msgObj) {
        com.google.gson.JsonObject author = msgObj.getAsJsonObject("author");
        if (author.has("bot") && author.get("bot").getAsBoolean()) return;

        String content = msgObj.get("content").getAsString().trim();
        String authorName = author.get("username").getAsString();
        String authorId = author.get("id").getAsString();
        String messageId = msgObj.get("id").getAsString();
        String channelId = msgObj.get("channel_id").getAsString();

        if (content.startsWith("!verify")) {
            executor.submit(() -> deleteDiscordMessage(channelId, messageId));

            String[] parts = content.split("\\s+");
            if (parts.length < 2) {
                String err = "";
                if (MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordVerifyFormatError != null && !MessageConfigHandler.messages.discordVerifyFormatError.isEmpty()) {
                    err = MessageConfigHandler.messages.discordVerifyFormatError;
                }
                if (err.isEmpty()) {
                    err = LanguageManager.getDiscordVerifyFormatError();
                }
                sendNativeChannelMessage(channelId, err);
                return;
            }

            String inputCode = parts[1];
            String mcName = null;
            String mcUuid = "";

            for (java.util.Map.Entry<String, String[]> entry : pendingVerifications.entrySet()) {
                if (entry.getValue()[0].equals(inputCode)) {
                    mcName = entry.getKey();
                    mcUuid = entry.getValue()[1];
                    break;
                }
            }

            if (mcName == null) {
                String err = "";
                if (MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordVerifyInvalidError != null && !MessageConfigHandler.messages.discordVerifyInvalidError.isEmpty()) {
                    err = MessageConfigHandler.messages.discordVerifyInvalidError;
                }
                if (err.isEmpty()) {
                    err = LanguageManager.getDiscordVerifyInvalidError();
                }
                sendNativeChannelMessage(channelId, err);
                return;
            }

            savePlayerBindingData(mcName, mcUuid, authorId, authorName);
            pendingVerifications.remove(mcName);

            String succ = "";
            if (MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordVerifySuccess != null && !MessageConfigHandler.messages.discordVerifySuccess.isEmpty()) {
                succ = MessageConfigHandler.messages.discordVerifySuccess;
            }
            if (succ.isEmpty()) {
                succ = LanguageManager.getDiscordVerifySuccess(mcName);
            }
            String response = succ.replace("%user%", mcName);
            sendNativeChannelMessage(channelId, response);
            return;
        }

        String formatPattern = "%player%: %message%";
        try {
            java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("discordToMinecraftChat");
            String custom = (String) f.get(MessageConfigHandler.messages);
            if (custom != null && !custom.isEmpty()) formatPattern = custom;
        } catch (Exception e) {}

        String formatted = formatPattern.replace("%user%", authorName).replace("%message%", content);
        try {
            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(new net.minecraft.util.ChatComponentText(formatted));
        } catch (Exception e) {}
    }

    public static void sendNativeChannelMessage(String channelId, String content) {
        String token = ConfigHandler.getBotToken();
        if (channelId == null || channelId.equals("0") || channelId.isEmpty() || token.isEmpty()) return;
        try {
            URL url = new URL("https://discord.com/api/v9/channels/" + channelId + "/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestProperty("Authorization", "Bot " + token);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "DiscordBot (Minecraft 1.7.10, Native-REST)");

            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("content", content);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception e) {}
    }

    private static void deleteDiscordMessage(String channelId, String messageId) {
        String token = ConfigHandler.getBotToken();
        if (token.isEmpty()) return;
        try {
            URL url = new URL("https://discord.com/api/v9/channels/" + channelId + "/messages/" + messageId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestProperty("Authorization", "Bot " + token);
            conn.setRequestProperty("User-Agent", "DiscordBot (Minecraft 1.7.10, Native-REST)");
            conn.getResponseCode();
        } catch (Exception e) {}
    }

>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
    private void setupConsoleAppender() {
        org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        Appender consoleAppender = new AbstractAppender("MCToDC-ConsoleInterceptor", null, null) {
            @Override
            public void append(LogEvent logEvent) {
                String logMessage = logEvent.getMessage().getFormattedMessage();
                if (logMessage.contains("[MCToDC") || logMessage.contains("Discord")) return;

                executor.submit(() -> {
                    String wh = ConfigHandler.channelsConfig.consoleWebhook;
                    if (wh != null && !wh.trim().isEmpty() && !wh.equals("0") && wh.startsWith("http")) {
                        sendNativeHttpWebhook(wh, "Server Console", logMessage);
                    } else {
                        String cid = ConfigHandler.channelsConfig.consoleChannelID;
                        if (cid != null && !cid.equals("0") && !cid.isEmpty()) {
<<<<<<< HEAD
                            DiscordListener.sendNativeChannelMessage(cid, "`" + logMessage + "`", false);
=======
                            sendNativeChannelMessage(cid, "`" + logMessage + "`");
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
                        }
                    }
                });
            }
        };
        consoleAppender.start();
        rootLogger.addAppender(consoleAppender);
    }

    public static void sendNativeHttpWebhook(String webhookUrl, String username, String content) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || !webhookUrl.startsWith("http")) return;
        HttpURLConnection conn = null;
        try {
            if (content.length() > 1800) content = content.substring(0, 1800) + "... (Truncated)";
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("username", username);
            json.addProperty("content", content);
            byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);

            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = conn.getOutputStream()) { os.write(payload); os.flush(); }
            conn.getResponseCode();
        } catch (Exception e) {} finally { if (conn != null) conn.disconnect(); }
    }

<<<<<<< HEAD
    // 狀態動態更新實作
    private static void updateDiscordChannelTopic() {
        String channelId = ConfigHandler.channelsConfig.chatChannelID;
        if (channelId == null || channelId.equals("0")) return;
        try {
            int players = MinecraftServer.getServer().getCurrentPlayerCount();
            int maxPlayers = MinecraftServer.getServer().getMaxPlayers();
            String topic = String.format("Minecraft Server Online | 🟢 Players: %d/%d", players, maxPlayers);

            URL url = new URL("https://discord.com/api/v9/channels/" + channelId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bot " + ConfigHandler.getBotToken());
            conn.setRequestProperty("Content-Type", "application/json");

            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("topic", topic);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception e) {
            if (ConfigHandler.generalConfig.debugging) e.printStackTrace();
        }
    }

=======
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
    public static synchronized void saveBinds() {
        if (bindFile == null) return;
        try { java.nio.file.Files.write(bindFile.toPath(), boundPlayers.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception e) {}
    }

    public static ExecutorService getExecutor() { return executor; }
}