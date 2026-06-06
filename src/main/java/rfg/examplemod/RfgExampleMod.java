package rfg.examplemod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static String lastChannelMessageId = "0";
    
    // // TODO: 建立 HTTP 429 防洪防爆動態原子鎖與冷卻機制
    private static final AtomicBoolean isPollingInProgress = new AtomicBoolean(false);
    private static long coolDownUntil = 0L;

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
        MinecraftListener minecraftListener = new MinecraftListener();
        MinecraftForge.EVENT_BUS.register(minecraftListener);
        FMLCommonHandler.instance().bus().register(minecraftListener);

        String activeToken = ConfigHandler.getBotToken();
        if (activeToken.isEmpty() || ConfigHandler.channelsConfig.chatChannelID.equals("0")) {
            logger.warn("Bot connection configurations incomplete, proxy service aborted.");
            return;
        }

        if (ConfigHandler.databaseConfig.useRemoteSQL) {
            executor.submit(() -> setupRemoteSQLTable());
        }

        executor.submit(() -> {
            try {
                if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
                    logger.info("[MCToDC] 正在初始化輕量化原生 HTTP REST 閘道...");
                } else {
                    logger.info("[MCToDC] Initializing light-weight native HTTP REST gateway...");
                }
                
                System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
                System.setProperty("jdk.tls.client.protocols", "TLSv1.2,TLSv1.3");

                String activeNotice = LanguageManager.getDiscordServerStarted();
                if (MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordServerStarted != null && !MessageConfigHandler.messages.discordServerStarted.isEmpty()) {
                    activeNotice = MessageConfigHandler.messages.discordServerStarted;
                }
                sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, activeNotice);

                if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
                    logger.info("[MCToDC] 本地資料庫與安全代理閘道已成功加載完畢。");
                } else {
                    logger.info("[MCToDC] Server has loaded local database and secure proxy channel successfully.");
                }
                
                timerExecutor.scheduleAtFixedRate(() -> pollChannelMessages(), 1, 2500, TimeUnit.MILLISECONDS);
            } catch (Exception e) { logger.error("Gateway link error: " + e.getMessage()); }
        });
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            logger.info("[MCToDC] 伺服器正在關閉...");
        } else {
            logger.info("[MCToDC] Server is shutting down...");
        }

        String stopNotice = "";
        if (MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordServerStopped != null && !MessageConfigHandler.messages.discordServerStopped.isEmpty()) {
            stopNotice = MessageConfigHandler.messages.discordServerStopped;
        }

        if (stopNotice.isEmpty()) {
            stopNotice = LanguageManager.getDiscordServerStopped();
        }
        
        sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, stopNotice);
        
        try { 
            timerExecutor.shutdown(); 
            executor.shutdown(); 
        } catch (Exception e) {}
    }

    private static void setupRemoteSQLTable() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            // 呼叫具備自癒防禦功能的輕量化對接鏈
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

    private void pollChannelMessages() {
        String channelId = ConfigHandler.channelsConfig.chatChannelID;
        if (channelId == null || channelId.equals("0") || channelId.isEmpty()) return;
        
        // 🚀【防洪限流防禦一】：如果目前處於 429 被處罰冷卻期，直接跳過不打 API
        long now = System.currentTimeMillis();
        if (now < coolDownUntil) return;

        // 🚀【防洪限流防禦二】：利用原子鎖確保同時間只有一個 HTTP Poll 請求在飛，防止請求堆積卡死
        if (!isPollingInProgress.compareAndSet(false, true)) return;

        try {
            // 透過 limit=10 配合動態歷史指針優化頻寬
            URL url = new URL("https://discord.com/api/v9/channels/" + channelId + "/messages?limit=10");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bot " + ConfigHandler.getBotToken());
            conn.setRequestProperty("User-Agent", "DiscordBot (Minecraft 1.7.10, Native-REST)");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int responseCode = conn.getResponseCode();
            
            // 🚀【防洪限流防禦三】：如果遇到 HTTP 429 Rate Limited，啟動極端防禦避險
            if (responseCode == 429) {
                coolDownUntil = System.currentTimeMillis() + 15000L; // 自動進入 15 秒物理冷卻
                logger.warn("[MCToDC] ⚠️ Detected Discord HTTP 429 Rate Limit. Entering cooling down defense for 15s.");
                return;
            }

            if (responseCode == 200) {
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
                        
                        // 動態時間指針判斷：只有比上次收到的 ID 更全新的訊息才放行處理
                        if (id.compareTo(lastChannelMessageId) > 0) {
                            lastChannelMessageId = id;
                            processIncomingMessage(msgObj);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 萬一發生網路波動異常，給予輕微冷卻保護
            coolDownUntil = System.currentTimeMillis() + 3000L;
        } finally {
            isPollingInProgress.set(false); // 解鎖
        }
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
            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(new ChatComponentText(formatted));
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
                            sendNativeChannelMessage(cid, "`" + logMessage + "`");
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

    public static synchronized void saveBinds() {
        if (bindFile == null) return;
        try { java.nio.file.Files.write(bindFile.toPath(), boundPlayers.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception e) {}
    }

    public static ExecutorService getExecutor() { return executor; }
}