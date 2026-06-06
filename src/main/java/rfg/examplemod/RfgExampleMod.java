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

import java.io.File;
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

        // 🛑 總開關攔截
        if (!ConfigHandler.generalConfig.enabled) {
            logger.info("[MCToDC] Module is disabled in config. Skipping core load.");
            return;
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
        if (!ConfigHandler.generalConfig.enabled) return;

        MinecraftListener minecraftListener = new MinecraftListener();
        MinecraftForge.EVENT_BUS.register(minecraftListener);
        FMLCommonHandler.instance().bus().register(minecraftListener);

        String activeToken = ConfigHandler.getBotToken();
        if (activeToken.isEmpty() || ConfigHandler.channelsConfig.chatChannelID.equals("0")) {
            logger.warn("Bot connection configurations incomplete, proxy service aborted.");
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

        if (ConfigHandler.databaseConfig.useRemoteSQL) {
            executor.submit(() -> setupRemoteSQLTable());
        }

        executor.submit(new Runnable() {
            @Override
            public void run() {
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
                    DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, activeNotice, false);

                    if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
                        logger.info("[MCToDC] 本地資料庫與安全代理閘道已成功加載完畢。");
                    } else {
                        logger.info("[MCToDC] Server has loaded local database and secure proxy channel successfully.");
                    }
                    
                    // 將輪詢指向新的 DiscordListener
                    timerExecutor.scheduleAtFixedRate(() -> DiscordListener.pollChannelMessages(), 1, 2500, TimeUnit.MILLISECONDS);

                    // 📡 實作狀態更新 (透過修改頻道主題，最低限制 5 分鐘避免 429 Rate Limit)
                    int interval = Math.max(300, ConfigHandler.botConfig.statusUpdateInterval); 
                    timerExecutor.scheduleAtFixedRate(() -> updateDiscordChannelTopic(), 10, interval, TimeUnit.SECONDS);

                } catch (Exception e) { logger.error("Gateway link error: " + e.getMessage()); }
            }
        });
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;

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
        
        DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, stopNotice, false);
        
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
                            DiscordListener.sendNativeChannelMessage(cid, "`" + logMessage + "`", false);
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

    // 狀態更新實作
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

    public static synchronized void saveBinds() {
        if (bindFile == null) return;
        try { java.nio.file.Files.write(bindFile.toPath(), boundPlayers.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception e) {}
    }

    public static ExecutorService getExecutor() { return executor; }
}