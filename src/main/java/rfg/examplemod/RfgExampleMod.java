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
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    // 📡 核心加固：建立日誌非同步緩衝佇列，阻斷 429 速率限制頻率爆擊
    private static final ConcurrentLinkedQueue<String> consoleLogBuffer = new ConcurrentLinkedQueue<>();

    public static final java.util.Map<String, String[]> pendingVerifications = new java.util.concurrent.ConcurrentHashMap<>();
    public static com.google.gson.JsonObject boundPlayers = new com.google.gson.JsonObject(); 
    private static File bindFile;

    public static java.sql.Connection getSQLConnection() throws Exception {
        // 🛡️ 修復：動態調配並自我調試相容現代 MySQL 8+ 驅動程序與舊版驅動程序
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            Class.forName("com.mysql.jdbc.Driver");
        }
        String url = ConfigHandler.databaseConfig.sqlUrl;
        if (!url.contains("serverTimezone=")) {
            url += (url.contains("?") ? "&" : "?") + "serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
        }
        return DriverManager.getConnection(url, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword);
    }

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

        if (!ConfigHandler.generalConfig.enabled) {
            logger.info(LanguageManager.getLogModuleDisabled());
            return;
        }

        bindFile = new File(configDir, "mctodc-bnd.json");
        if (bindFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(bindFile.toPath()), StandardCharsets.UTF_8);
                boundPlayers = new com.google.gson.JsonParser().parse(content).getAsJsonObject();
            } catch (Exception e) {}
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
            logger.warn("[MCToDC] Bot connection configurations incomplete, proxy service aborted.");
            return;
        }

        if (ConfigHandler.botConfig.printInviteLink) {
            try {
                String clientId = new String(Base64.getDecoder().decode(activeToken.split("\\.")[0]), StandardCharsets.UTF_8);
                logger.info("[MCToDC] 🤖 Bot Invite Link: https://discord.com/oauth2/authorize?client_id=" + clientId + "&permissions=536870912&scope=bot");
            } catch (Exception e) {}
        }

        if (ConfigHandler.databaseConfig.useRemoteSQL) {
            executor.submit(() -> setupRemoteSQLTable());
        }

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    logger.info(LanguageManager.getLogGatewayInitializing());
                    System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
                    
                    String activeNotice = MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordServerStarted != null ? MessageConfigHandler.messages.discordServerStarted : "";
                    if (activeNotice.isEmpty()) activeNotice = LanguageManager.getDiscordServerStarted();
                    DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, activeNotice, false);

                    logger.info(LanguageManager.getLogGatewaySuccess());
                    
                    // 🛡️ 核心加固：最外層加入頂層 Throwable 捕捉，保證線程永不因未捕獲異常而意外中斷死亡
                    timerExecutor.scheduleAtFixedRate(() -> {
                        try { DiscordListener.pollChannelMessages(); } 
                        catch (Throwable t) { if (ConfigHandler.generalConfig.debugging) t.printStackTrace(); }
                    }, 1, 2500, TimeUnit.MILLISECONDS);

                    int interval = Math.max(300, ConfigHandler.botConfig.statusUpdateInterval); 
                    timerExecutor.scheduleAtFixedRate(() -> {
                        try { updateDiscordChannelTopic(); } 
                        catch (Throwable t) { if (ConfigHandler.generalConfig.debugging) t.printStackTrace(); }
                    }, 10, interval, TimeUnit.SECONDS);

                    // 🛡️ 核心加固：啟動每 1.5 秒執行一次的非同步緩衝日誌拼接打包發送任務（極限壓制 429 速率限制）
                    if (ConfigHandler.chatConfig.sendConsoleMessages) {
                        timerExecutor.scheduleAtFixedRate(() -> {
                            try { flushConsoleLogBuffer(); } 
                            catch (Throwable t) { if (ConfigHandler.generalConfig.debugging) t.printStackTrace(); }
                        }, 2, 1500, TimeUnit.MILLISECONDS);
                    }

                } catch (Exception e) {}
            }
        });
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;
        logger.info(LanguageManager.getLogServerShuttingDown());

        String stopNotice = MessageConfigHandler.messages != null && MessageConfigHandler.messages.discordServerStopped != null ? MessageConfigHandler.messages.discordServerStopped : "";
        if (stopNotice.isEmpty()) stopNotice = LanguageManager.getDiscordServerStopped();
        
        DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, stopNotice, false);
        
        try { timerExecutor.shutdown(); executor.shutdown(); } catch (Exception e) {}
    }

    private static void setupRemoteSQLTable() {
        try (Connection conn = getSQLConnection()) {
            String query = "CREATE TABLE IF NOT EXISTS `" + ConfigHandler.databaseConfig.sqlTableName + "` (" +
                    "`username` VARCHAR(64) NOT NULL, `uuid` VARCHAR(64) NOT NULL, `discord_id` VARCHAR(64) NOT NULL, `discord_name` VARCHAR(64) NOT NULL, " +
                    "PRIMARY KEY (`username`), KEY `uuid_idx` (`uuid`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.executeUpdate();
                logger.info("[MCToDC] Relational SQL table structure synchronized successfully.");
            }
        } catch (Exception e) {
            logger.error("[MCToDC] SQL Sync failed: " + e.getMessage());
        }
    }

    public static void savePlayerBindingData(String username, String uuid, String discordId, String discordName) {
        if (ConfigHandler.databaseConfig.useRemoteSQL) {
            executor.submit(() -> {
                try (Connection conn = getSQLConnection()) {
                    String query = "REPLACE INTO `" + ConfigHandler.databaseConfig.sqlTableName + "` (`username`, `uuid`, `discord_id`, `discord_name`) VALUES (?, ?, ?, ?);";
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.setString(1, username); stmt.setString(2, uuid); stmt.setString(3, discordId); stmt.setString(4, discordName);
                        stmt.executeUpdate();
                    }
                } catch (Exception e) {}
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

                // 🛡️ 修復：絕不執行高頻Rest請求，只將Log丟入非同步緩衝隊列，0延遲、絕不卡主執行緒
                consoleLogBuffer.add(logMessage);
            }
        };
        consoleAppender.start();
        rootLogger.addAppender(consoleAppender);
    }

    private static void flushConsoleLogBuffer() {
        // 🛡️ 修復：高吞吐量日誌合併打包發送實作（每 1.5 秒將隊列內堆積的所有 Log 壓縮成單一大 Markdown 區塊發送）
        if (consoleLogBuffer.isEmpty()) return;

        String webhookUrl = ConfigHandler.channelsConfig.consoleWebhook;
        String cid = ConfigHandler.channelsConfig.consoleChannelID;
        boolean hasWebhook = (webhookUrl != null && !webhookUrl.trim().isEmpty() && !webhookUrl.equals("0") && webhookUrl.startsWith("http"));
        
        StringBuilder chunk = new StringBuilder();
        String line;
        
        while ((line = consoleLogBuffer.poll()) != null) {
            // 過濾顏色與異常亂碼
            line = line.replaceAll("\u001B\\[[;\\d]*m", "");
            if (chunk.length() + line.length() + 5 > 1900) {
                // 超過 Discord 單條訊息 2000 字元限制，進行分批切片發送
                sendCompiledLogChunk(chunk.toString(), webhookUrl, cid, hasWebhook);
                chunk.setLength(0);
            }
            chunk.append(line).append("\n");
        }
        
        if (chunk.length() > 0) {
            sendCompiledLogChunk(chunk.toString(), webhookUrl, cid, hasWebhook);
        }
    }

    private static void sendCompiledLogChunk(String body, String wh, String cid, boolean useWebhook) {
        String wrappedLog = "```js\n" + body + "```";
        if (useWebhook) {
            sendNativeHttpWebhook(wh, "Server Console", wrappedLog);
        } else if (cid != null && !cid.equals("0") && !cid.isEmpty()) {
            DiscordListener.sendNativeChannelMessage(cid, wrappedLog, false);
        }
    }

    public static void sendNativeHttpWebhook(String webhookUrl, String username, String content) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || !webhookUrl.startsWith("http")) return;
        HttpURLConnection conn = null;
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("username", username); 
            json.addProperty("content", content);
            byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);

            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(2000); conn.setReadTimeout(2000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = conn.getOutputStream()) { os.write(payload); os.flush(); }
            conn.getResponseCode();
        } catch (Exception e) {} finally { if (conn != null) conn.disconnect(); }
    }

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

            try (OutputStream os = conn.getOutputStream()) { os.write(json.toString().getBytes(StandardCharsets.UTF_8)); os.flush(); }
            conn.getResponseCode();
        } catch (Exception e) {}
    }

    public static synchronized void saveBinds() {
        if (bindFile == null) return;
        try { java.nio.file.Files.write(bindFile.toPath(), boundPlayers.toString().getBytes(StandardCharsets.UTF_8)); } catch (Exception e) {}
    }

    public static ExecutorService getExecutor() { return executor; }
}