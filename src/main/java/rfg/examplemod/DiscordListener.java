package rfg.examplemod;

<<<<<<< HEAD
import net.minecraft.server.MinecraftServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordListener {
    
    private static String lastChannelMessageId = "0";

    public static void pollChannelMessages() {
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
            if (silent) json.addProperty("flags", 4096); // 注入 Discord 靜音回覆標籤
            
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
        // ... (原來的 deleteDiscordMessage 內容，捕捉異常時加入 debugging 判斷)
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
=======
/**
 * MCToDC - Native REST API Override Pipeline
 * Obsolete context file kept for structure compatibility. All routines decoupled to main core threads.
 */
public class DiscordListener {
    // Completely empty to solve compile symbol references safely
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
}