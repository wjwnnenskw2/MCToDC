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
import java.util.Base64;
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
    
    private static final AtomicBoolean isPollingInProgress = new AtomicBoolean(false);
    private static long coolDownUntil = 0L;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        File configDir = event.getModConfigurationDirectory();

        ConfigHandler.init(configDir, logger);
        MessageConfigHandler.init(configDir, logger);

        // 🎯【核心漏洞核對三修復：實現總開關完全防線】
        if (!ConfigHandler.generalConfig.enabled) {
            logger.info("[MCToDC] Mod initialization bypassed because enabled=false in config.");
            return;
        }

        bindFile = new File(configDir, "mctodc-bnd.json");
        if (bindFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(bindFile.toPath()), StandardCharsets.UTF_8);
                boundPlayers = new com.google.gson.JsonParser().parse(content).getAsJsonObject();
            } catch (Exception e) { logger.error("[MCToDC] Parsing error on local configuration json file."); }
        }

        if (ConfigHandler.chatConfig.sendConsoleMessages) {
            setupConsoleAppender();
        }
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // 🎯【總開關攔截點二】
        if (!ConfigHandler.generalConfig.enabled) return;

        MinecraftListener minecraftListener = new MinecraftListener();
        MinecraftForge.EVENT_BUS.register(minecraftListener);
        FMLCommonHandler.instance().bus().register(minecraftListener);

        String activeToken = ConfigHandler.getBotToken();
        if (activeToken.isEmpty() || ConfigHandler.channelsConfig.chatChannelID.equals("0")) {
            logger.warn("[MCToDC] Configuration fields are invalid. Service lifecycle aborted.");
            return;
        }

        // 🎯【核心漏洞核對四修復之一：自動解算印出邀請連結】
        if (ConfigHandler.botConfig.printInviteLink) {
            try {
                String[] segments = activeToken.split("\\.");
                if (segments.length > 0) {
                    String decodedId = new String(Base64.getDecoder().decode(segments[0]), StandardCharsets.UTF_8);
                    if (decodedId.matches("\\d+")) {
                        logger.info("[MCToDC] Discord Application Authorization URL Link generated:");
                        logger.info("-> https://discord.com/api/oauth2/authorize?client_id=" + decodedId + "&permissions=82944&scope=bot");
                    }
                }
            } catch (Exception ignored) {}
        }

        if (ConfigHandler.databaseConfig.useRemoteSQL) {
            executor.submit(() -> setupRemoteSQLTable());
        }

        executor.submit(() -> {
            try {
                logger.info(LanguageManager.getLogGatewayInit());
                
                System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
                System.setProperty("jdk.tls.client.protocols", "TLSv1.2,TLSv1.3");

                String activeNotice = LanguageManager.getDiscordServerStarted();
                if (MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordServerStarted != null && !MessageConfigHandler.messages.discordServerStarted.isEmpty()) {
                    activeNotice = MessageConfigHandler.messages.discordServerStarted;
                }
                sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, activeNotice);

                logger.info(LanguageManager.getLogGatewaySuccess());
                
                // 定時任務一：輪詢 Discord 對話訊息
                timerExecutor.scheduleAtFixedRate(() -> pollChannelMessages(), 1, 2500, TimeUnit.MILLISECONDS);
                
                // 🎯【核心漏洞核對二修復：開闢獨立定時任務，定時調用 REST API 刷新 Presence 線上狀態】
                long interval = Math.max(10, ConfigHandler.botConfig.statusUpdateInterval);
                timerExecutor.scheduleAtFixedRate(() -> updateBotPresenceStatus(), 5, interval, TimeUnit.SECONDS);

            } catch (Exception e) { logger.error("[MCToDC] Gateway connection thread execution error: " + e.getMessage()); }
        });
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;

        logger.info(LanguageManager.getLogShuttingDown());

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
        } catch (Exception ignored) {}
    }

    private static void setupRemoteSQLTable() {
        try {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                logger.error("[MCToDC] MySQL JDBC driver dependency missing. Remote integration aborted. Using fallback storage profile.");
                ConfigHandler.databaseConfig.useRemoteSQL = false;
                return;
            }

            try (Connection conn = DriverManager.getConnection(ConfigHandler.databaseConfig.sqlUrl, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword)) {
                String query = "CREATE TABLE IF NOT EXISTS `" + ConfigHandler.databaseConfig.sqlTableName + "` (" +
                        "`username` VARCHAR(64) NOT NULL, " +
                        "`uuid` VARCHAR(64) NOT NULL, " +
                        "`discord_id` VARCHAR(64) NOT NULL, " +
                        "`discord_name` VARCHAR(64) NOT NULL, " +
                        "PRIMARY KEY (`username`), KEY `uuid_idx` (`uuid`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.executeUpdate();
                    logger.info("[MCToDC] Remote database storage schema verified.");
                }
            }
        } catch (Exception e) {
            logger.error("[MCToDC] Failed to initialize SQL database table mapping: " + e.getMessage());
        }
    }

    private static void updateBotPresenceStatus() {
        String token = ConfigHandler.getBotToken();
        if (token.isEmpty()) return;
        try {
            int onlineCount = MinecraftServer.getServer().getCurrentPlayerCount();
            int maxPlayers = MinecraftServer.getServer().getMaxPlayers();
            
            if (ConfigHandler.generalConfig.debugging) {
                logger.info("[MCToDC-Debug] Dispatching presence update. Metrics: " + onlineCount + "/" + maxPlayers);
            }

            // 利用原生 v9 RESTful API 的使用者設定端點，更新 Bot 的自訂狀態
            URL url = new URL("https://discord.com/api/v9/users/@me/settings");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bot " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "DiscordBot (Minecraft 1.7.10, Native-REST)");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            com.google.gson.JsonObject status = new com.google.gson.JsonObject();
            status.addProperty("custom_status", "Online: " + onlineCount + "/" + maxPlayers);
            json.add("status", status);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception ignored) {}
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
                    logger.error("[MCToDC] Failed to upload database mapping entry: " + e.getMessage());
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
        
        long now = System.currentTimeMillis();
        if (now < coolDownUntil) return;

        if (!isPollingInProgress.compareAndSet(false, true)) return;

        try {
            URL url = new URL("https://discord.com/api/v9/channels/" + channelId + "/messages?limit=10");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bot " + ConfigHandler.getBotToken());
            conn.setRequestProperty("User-Agent", "DiscordBot (Minecraft 1.7.10, Native-REST)");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int responseCode = conn.getResponseCode();
            
            if (responseCode == 429) {
                coolDownUntil = System.currentTimeMillis() + 15000L; 
                logger.warn("[MCToDC] HTTP 429 encountered. Request pipeline throttling activated for 15s.");
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
                        
                        if (id.compareTo(lastChannelMessageId) > 0) {
                            lastChannelMessageId = id;
                            
                            // 🎯【核心漏洞核對五修復：完美交由分流類別處理，絕不死代碼】
                            DiscordListener.processIncomingMessage(msgObj);
                        }
                    }
                }
            }
        } catch (Exception e) {
            coolDownUntil = System.currentTimeMillis() + 3000L;
        } finally {
            isPollingInProgress.set(false); 
        }
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
            
            // 🎯【核心漏洞核對四修復之二：實現 silentReplies 靜音通知 flag】
            if (ConfigHandler.botConfig.silentReplies) {
                // Discord 官方規範：flags = 4096 代表發送無震動/無聲音的靜默訊息
                json.addProperty("flags", 4096);
            }
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    public static void deleteDiscordMessage(String channelId, String messageId) {
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
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {} finally { if (conn != null) conn.disconnect(); }
    }

    public static synchronized void saveBinds() {
        if (bindFile == null) return;
        try { java.nio.file.Files.write(bindFile.toPath(), boundPlayers.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    public static ExecutorService getExecutor() { return executor; }
}