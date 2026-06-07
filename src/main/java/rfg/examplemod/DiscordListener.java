package rfg.examplemod;

import net.minecraft.server.MinecraftServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordListener {
    
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
                    if (lastChannelMessageId.equals("0")) {
                        lastChannelMessageId = messages.get(0).getAsJsonObject().get("id").getAsString();
                        return;
                    }

                    for (int i = 0; i < messages.size(); i++) {
                        com.google.gson.JsonObject msgObj = messages.get(i).getAsJsonObject();
                        String id = msgObj.get("id").getAsString();
                        
                        if (id.compareTo(lastChannelMessageId) > 0) {
                            lastChannelMessageId = id; 
                            processIncomingMessage(msgObj);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            if (ConfigHandler.generalConfig.debugging) e.printStackTrace();
        }
    }

    private static void processIncomingMessage(com.google.gson.JsonObject msgObj) {
        com.google.gson.JsonObject author = msgObj.getAsJsonObject("author");
        if (author.has("bot") && author.get("bot").getAsBoolean()) return;

        // 🛡️ 修復：防範 Discord 傳送圖片/純貼圖等多媒體 Payload 引發空指針崩潰
        if (!msgObj.has("content") || msgObj.get("content").isJsonNull()) return;

        String content = msgObj.get("content").getAsString().trim();
        String authorName = author.get("username").getAsString();
        String authorId = author.get("id").getAsString();
        String messageId = msgObj.get("id").getAsString();
        String channelId = msgObj.get("channel_id").getAsString();

        if (content.startsWith("!verify")) {
            RfgExampleMod.getExecutor().submit(() -> deleteDiscordMessage(channelId, messageId));

            String[] parts = content.split("\\s+");
            if (parts.length < 2) {
                String err = MessageConfigHandler.messages.discordVerifyFormatError.isEmpty() ? 
                             LanguageManager.getDiscordVerifyFormatError() : MessageConfigHandler.messages.discordVerifyFormatError;
                sendNativeChannelMessage(channelId, err, ConfigHandler.botConfig.silentReplies);
                return;
            }

            String argument = parts[1];
            
            // 📡 補齊項目：判斷是 6 位數驗證碼（線上驗證）還是遊戲 ID（離線預綁定）
            if (argument.matches("\\d{6}")) {
                // 機制 A：線上驗證碼對齊
                String mcName = null;
                String mcUuid = "";

                for (java.util.Map.Entry<String, String[]> entry : RfgExampleMod.pendingVerifications.entrySet()) {
                    if (entry.getValue()[0].equals(argument)) {
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
            } else {
                // 機制 B：離線預綁定（免先進服被踢，直接向 Mojang API 發送正版驗證查詢）
                final String mcTargetName = argument;
                RfgExampleMod.getExecutor().submit(() -> {
                    try {
                        URL mojangUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + mcTargetName);
                        HttpURLConnection mConn = (HttpURLConnection) mojangUrl.openConnection();
                        mConn.setRequestMethod("GET");
                        mConn.setConnectTimeout(3000);
                        mConn.setReadTimeout(3000);

                        if (mConn.getResponseCode() == 200) {
                            BufferedReader mReader = new BufferedReader(new InputStreamReader(mConn.getInputStream(), StandardCharsets.UTF_8));
                            StringBuilder mSb = new StringBuilder();
                            String mLine;
                            while ((mLine = mReader.readLine()) != null) mSb.append(mLine);
                            mReader.close();

                            com.google.gson.JsonObject mojangJson = new com.google.gson.JsonParser().parse(mSb.toString()).getAsJsonObject();
                            String rawUuid = mojangJson.get("id").getAsString();
                            // 標準化 UUID 格式 (加上 Dash)
                            String formattedUuid = rawUuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                            String officialName = mojangJson.get("name").getAsString();

                            RfgExampleMod.savePlayerBindingData(officialName, formattedUuid, authorId, authorName);
                            sendNativeChannelMessage(channelId, "✅ **離線預綁定成功**：已成功預先將您的 Discord 帳號鎖定綁定至正版遊戲帳號 **" + officialName + "**！現在您可以隨時進入遊戲了。", ConfigHandler.botConfig.silentReplies);
                        } else {
                            // 離線預綁定 Fallback：非正版或 Mojang 伺服器超時，使用本地虛擬 UUID 補丁生成
                            String offlineUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + mcTargetName).getBytes(StandardCharsets.UTF_8)).toString();
                            RfgExampleMod.savePlayerBindingData(mcTargetName, offlineUuid, authorId, authorName);
                            sendNativeChannelMessage(channelId, "⚠️ **離線預綁定完成 (非正版/離線模式模式)**：未偵測到正版 ID，已為您生成本地 UUID 並完成與 **" + mcTargetName + "** 的預綁定！", ConfigHandler.botConfig.silentReplies);
                        }
                    } catch (Exception e) {
                        sendNativeChannelMessage(channelId, "❌ **預綁定失敗**：向驗證網關通訊時發生異常，請稍後再試。", true);
                    }
                });
            }
            return;
        }

        // !unlink 自願解綁指令
        if (content.equalsIgnoreCase("!unlink")) {
            RfgExampleMod.getExecutor().submit(() -> deleteDiscordMessage(channelId, messageId));
            
            String targetMcName = null;
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

            RfgExampleMod.boundPlayers.remove(targetMcName);
            RfgExampleMod.saveBinds();

            if (ConfigHandler.databaseConfig.useRemoteSQL) {
                final String finalTarget = targetMcName;
                RfgExampleMod.getExecutor().submit(() -> {
                    try {
                        java.sql.Connection conn = rfg.examplemod.RfgExampleMod.getSQLConnection();
                        if (conn != null) {
                            String delQuery = "DELETE FROM `" + ConfigHandler.databaseConfig.sqlTableName + "` WHERE `username` = ?;";
                            try (java.sql.PreparedStatement delStmt = conn.prepareStatement(delQuery)) {
                                delStmt.setString(1, finalTarget);
                                delStmt.executeUpdate();
                            }
                            conn.close();
                        }
                    } catch (Exception e) {
                        RfgExampleMod.logger.error("[MCToDC] Failed to unlink from remote SQL: " + e.getMessage());
                    }
                });
            }

            sendNativeChannelMessage(channelId, "✅ **解綁成功**：已成功解除您與遊戲角色 **" + targetMcName + "** 的所有認證綁定！", ConfigHandler.botConfig.silentReplies);
            return;
        }

        // 轉發 Discord 對話
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