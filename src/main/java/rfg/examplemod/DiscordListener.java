package rfg.examplemod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class DiscordListener {

    public static void processIncomingMessage(com.google.gson.JsonObject msgObj) {
        com.google.gson.JsonObject author = msgObj.getAsJsonObject("author");
        if (author.has("bot") && author.get("bot").getAsBoolean()) return;

        String content = msgObj.get("content").getAsString().trim();
        String authorName = author.get("username").getAsString();
        String authorId = author.get("id").getAsString();
        String messageId = msgObj.get("id").getAsString();
        String channelId = msgObj.get("channel_id").getAsString();

        if (ConfigHandler.generalConfig.debugging) {
            RfgExampleMod.logger.info("[MCToDC] Dispatched packet directly inside DiscordListener hub from " + authorName);
        }

        if (content.startsWith("!verify")) {
            RfgExampleMod.getExecutor().submit(() -> RfgExampleMod.deleteDiscordMessage(channelId, messageId));

            String[] parts = content.split("\\s+");
            if (parts.length < 2) {
                String err = LanguageManager.getDiscordVerifyFormatError();
                RfgExampleMod.sendNativeChannelMessage(channelId, err);
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
                String err = LanguageManager.getDiscordVerifyInvalidError();
                RfgExampleMod.sendNativeChannelMessage(channelId, err);
                return;
            }

            RfgExampleMod.savePlayerBindingData(mcName, mcUuid, authorId, authorName);
            RfgExampleMod.pendingVerifications.remove(mcName);

            String succ = LanguageManager.getDiscordVerifySuccess(mcName);
            RfgExampleMod.sendNativeChannelMessage(channelId, succ);
            return;
        }

        if (ConfigHandler.botConfig.silentReplies && content.startsWith("!")) {
            return; 
        }

        String formatPattern = "%player%: %message%";
        try {
            java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("discordToMinecraftChat");
            String custom = (String) f.get(MessageConfigHandler.messages);
            if (custom != null && !custom.isEmpty()) formatPattern = custom;
        } catch (Exception ignored) {}

        String formatted = formatPattern.replace("%user%", authorName).replace("%message%", content);
        try {
            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(new ChatComponentText(formatted));
        } catch (Exception ignored) {}
    }
}