package rfg.examplemod;

import net.minecraft.server.MinecraftServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordListener {
    
    // 📡 核心修正：初始設為 "0"，第一次輪詢後會鎖定最新訊息，之後透過 after 嚴密抓取，絕不丟包
    private static String lastChannelMessageId = "0";

    public static boolean isUserInDiscordServer(String discordUserId) {
        String token = ConfigHandler.getBotToken();
        String guildId = ConfigHandler.channelsConfig.guildID;
        
        if (guildId == null || guildId.equals("0") || guildId.isEmpty() || token.isEmpty()) {
            return true; 
        }
        
        try {
            URL url = new URL("https://discord.com/api/v9/guilds/" + guildId + "/members/" + discordUserId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bot " + token);
            conn.setRequestProperty("User-Agent", "DiscordBot (Minecraft 1.7.10, Native-REST)");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int responseCode = conn.getResponseCode();
            return responseCode == 200; 
        } catch (Exception e) {
            if (ConfigHandler.generalConfig.debugging) e.printStackTrace();
            return false; 
        }
    }

    public static void pollChannelMessages() {
        String channelId = ConfigHandler.channelsConfig.chatChannelID;
        if (channelId == null || channelId.equals("0") || channelId.isEmpty()) return;
        try {
            // 📡 核心修正：如果已有歷史最新訊息 ID，引進 after 分頁，確保洗頻時絕不吃掉驗證指令
            String urlStr = "https://discord.com/api/v9/channels/" + channelId + "/messages?limit=10";
            if (!lastChannelMessageId.equals("0")) {
                urlStr += "&after=" + lastChannelMessageId;
            }
            
            URL url = new URL(urlStr);
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
                    // 如果是伺服器剛啟動的第一次輪詢，鎖定最新一條訊息作為基線，防範歷史舊訊息刷屏
                    if (lastChannelMessageId.equals("0")) {
                        lastChannelMessageId = messages.get(0).getAsJsonObject().get("id").getAsString();
                        return;
                    }

                    // 因為 API 帶 after 參數時，回傳陣列的 index=0 是最舊的訊息，所以改為正序解析
                    for (int i = 0; i < messages.size(); i++) {
                        com.google.gson.JsonObject msgObj = messages.get(i).getAsJsonObject();
                        String id = msgObj.get("id").getAsString();
                        
                        // 雙重安全校驗：確保訊息 ID 比基線新
                        if (id.compareTo(lastChannelMessageId) > 0) {
                            lastChannelMessageId = id; // 實時推動輸送帶指標
                            processIncomingMessage(msgObj);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (ConfigHandler.generalConfig.debugging) {
                RfgExampleMod.logger.error("[MCToDC] Poll Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void processIncomingMessage(com.google.gson.JsonObject msgObj) {
        com.google.gson.JsonObject author = msgObj.getAsJsonObject("author");
        if (author.has("bot") && author.get("bot").getAsBoolean()) return;

        String content = msgObj.get("content").getAsString().trim();
        String authorName = author.get("username").getAsString();
        String authorId = author.get("id").getAsString();
        String messageId = msgObj.get("id").getAsString();
        String channelId = msgObj.get("channel_id").getAsString();

        // 🔓 處理 !verify 指令
        if (content.startsWith("!verify")) {
            RfgExampleMod.getExecutor().submit(() -> deleteDiscordMessage(channelId, messageId));

            String[] parts = content.split("\\s+");
            if (parts.length < 2) {
                String err = MessageConfigHandler.messages.discordVerifyFormatError.isEmpty() ? 
                             LanguageManager.getDiscordVerifyFormatError() : MessageConfigHandler.messages.discordVerifyFormatError;
                sendNativeChannelMessage(channelId, err, ConfigHandler.botConfig.silentReplies);
                return;
            }

            String inputCode = parts[1];
            String mcName = null;
            String mcUuid = "";

            for (java.util.Map.Entry<String, String[]> entry : RfgExampleMod.pendingVerifications.entrySet()) {
                if (entry.getValue()[0].equals(inputCode)) {
                    mcName = entry.getKey();
                    mcUuid = entry.getValue()[1];
                    break;
                }
            }

            if (mcName == null) {
                String err = MessageConfigHandler.messages.discordVerifyInvalidError.isEmpty() ? 
                             LanguageManager.getDiscordVerifyInvalidError() : MessageConfigHandler.messages.discordVerifyInvalidError;
                sendNativeChannelMessage(channelId, err, ConfigHandler.botConfig.silentReplies);
                return;
            }

            RfgExampleMod.savePlayerBindingData(mcName, mcUuid, authorId, authorName);
            RfgExampleMod.pendingVerifications.remove(mcName);

            String succ = MessageConfigHandler.messages.discordVerifySuccess.isEmpty() ? 
                          LanguageManager.getDiscordVerifySuccess(mcName) : MessageConfigHandler.messages.discordVerifySuccess;
            sendNativeChannelMessage(channelId, succ.replace("%user%", mcName), ConfigHandler.botConfig.silentReplies);
            return;
        }

        // 🔗 補齊：處理 !unlink 自願解綁指令
        if (content.equalsIgnoreCase("!unlink")) {
            RfgExampleMod.getExecutor().submit(() -> deleteDiscordMessage(channelId, messageId));
            
            String targetMcName = null;
            // 遍歷本地 JSON 尋找這個 Discord ID 綁定的 Minecraft 帳號
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : RfgExampleMod.boundPlayers.entrySet()) {
                try {
                    com.google.gson.JsonObject pData = entry.getValue().getAsJsonObject();
                    if (pData.has("discordID") && pData.get("discordID").getAsString().equals(authorId)) {
                        targetMcName = entry.getKey();
                        break;
                    }
                } catch (Exception ignored) {}
            }

            if (targetMcName == null) {
                sendNativeChannelMessage(channelId, "❌ **解綁失敗**：您的 Discord 帳號目前並未綁定任何 Minecraft 角色。", true);
                return;
            }

            // A. 從本地 JSON 快取中刪除
            RfgExampleMod.boundPlayers.remove(targetMcName);
            RfgExampleMod.saveBinds();

            // B. 如果有開遠端 SQL，非同步發送 SQL 刪除指令
            if (ConfigHandler.databaseConfig.useRemoteSQL) {
                final String finalTarget = targetMcName;
                RfgExampleMod.getExecutor().submit(() -> {
                    try {
                        java.sql.Connection conn = java.sql.DriverManager.getConnection(ConfigHandler.databaseConfig.sqlUrl, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword);
                        String delQuery = "DELETE FROM `" + ConfigHandler.databaseConfig.sqlTableName + "` WHERE `username` = ?;";
                        try (java.sql.PreparedStatement delStmt = conn.prepareStatement(delQuery)) {
                            delStmt.setString(1, finalTarget);
                            delStmt.executeUpdate();
                        }
                        conn.close();
                    } catch (Exception e) {
                        RfgExampleMod.logger.error("[MCToDC] Failed to unlink from remote SQL: " + e.getMessage());
                    }
                });
            }

            sendNativeChannelMessage(channelId, "✅ **解綁成功**：已成功解除您與遊戲角色 **" + targetMcName + "** 的所有認證綁定！", ConfigHandler.botConfig.silentReplies);
            return;
        }

        // 一般玩家對話轉發至遊戲內
        String formatPattern = MessageConfigHandler.messages.discordToMinecraftChat;
        if (formatPattern == null || formatPattern.isEmpty()) formatPattern = "%player%: %message%";
        String formatted = formatPattern.replace("%user%", authorName).replace("%message%", content);
        
        try {
            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(new net.minecraft.util.ChatComponentText(formatted));
        } catch (Exception e) {}
    }

    public static void sendNativeChannelMessage(String channelId, String content, boolean silent) {
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
            if (silent) json.addProperty("flags", 4096);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception e) {
            if (ConfigHandler.generalConfig.debugging) e.printStackTrace();
        }
    }

    private static void deleteDiscordMessage(String channelId, String messageId) {
        try {
            URL url = new URL("https://discord.com/api/v9/channels/" + channelId + "/messages/" + messageId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Bot " + ConfigHandler.getBotToken());
            conn.getResponseCode();
        } catch (Exception e) {
            if (ConfigHandler.generalConfig.debugging) e.printStackTrace();
        }
    }
}