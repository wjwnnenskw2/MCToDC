package rfg.examplemod;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AchievementEvent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MinecraftListener {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        final String username = player.getCommandSenderName();
        final String uuid = player.getUniqueID().toString();

        RfgExampleMod.getExecutor().submit(() -> {
            boolean isVerified = false;
            String discordName = "Unknown";
            String discordIdFromSQL = "";

            if (ConfigHandler.databaseConfig.useRemoteSQL) {
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                    try (Connection conn = DriverManager.getConnection(ConfigHandler.databaseConfig.sqlUrl, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword)) {
                        String query = "SELECT `discord_id`, `discord_name` FROM `" + ConfigHandler.databaseConfig.sqlTableName + "` WHERE `username` = ? OR `uuid` = ? LIMIT 1;";
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            stmt.setString(1, username);
                            stmt.setString(2, uuid);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) {
                                    isVerified = true;
                                    discordIdFromSQL = rs.getString("discord_id");
                                    discordName = rs.getString("discord_name");
                                }
                            }
                        }
                    }
                } catch (Exception e) {}
            }

            if (isVerified && !RfgExampleMod.boundPlayers.has(username)) {
                try {
                    com.google.gson.JsonObject playerData = new com.google.gson.JsonObject();
                    playerData.addProperty("discordID", discordIdFromSQL);
                    playerData.addProperty("discordName", discordName);
                    playerData.addProperty("uuid", uuid);
                    RfgExampleMod.boundPlayers.add(username, playerData);
                    RfgExampleMod.saveBinds();
                } catch (Exception e) {}
            }

            if (!isVerified) {
                if (RfgExampleMod.boundPlayers.has(username)) {
                    isVerified = true;
                    try { discordName = RfgExampleMod.boundPlayers.getAsJsonObject(username).get("discordName").getAsString(); } catch (Exception e) {}
                }
            }

            if (isVerified) {
                String formatPattern = LanguageManager.getDiscordPlayerJoined(username, discordName);
                String announce = formatPattern.replace("%player%", username).replace("%discord%", discordName);
                DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, announce, false);
            } else {
                String code = String.format("%04d", (int)(Math.random() * 10000));
                RfgExampleMod.pendingVerifications.put(username, new String[]{code, uuid});
                try {
                    if (player.playerNetServerHandler != null) {
                        String kickReason = LanguageManager.getMcKickReason(code);
                        player.playerNetServerHandler.kickPlayerFromServer(kickReason);
                    }
                } catch (Exception e) {}
            }
        });
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        final String username = event.player.getCommandSenderName();
        if (RfgExampleMod.pendingVerifications.containsKey(username)) return; 
        RfgExampleMod.getExecutor().submit(() -> {
            String formatPattern = LanguageManager.getDiscordPlayerLeft(username);
            DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, formatPattern.replace("%player%", username), false);
        });
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        final String username = event.username;
        final String message = event.message;
        RfgExampleMod.getExecutor().submit(() -> {
            String webhookUrl = ConfigHandler.channelsConfig.chatWebhook;
            if (webhookUrl != null && !webhookUrl.trim().isEmpty() && !webhookUrl.equals("0") && webhookUrl.startsWith("http")) {
                RfgExampleMod.sendNativeHttpWebhook(webhookUrl, username, message);
            } else {
                String formatPattern = LanguageManager.getDiscordChatFormat(username, message);
                DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, formatPattern.replace("%player%", username).replace("%message%", message), false);
            }
        });
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!ConfigHandler.chatConfig.sendCommandMessages) return;
        if (event.sender == null) return;
        final String username = event.sender.getCommandSenderName();
        if (username.equals("@") || username.equals("Server")) return;

        StringBuilder cmdStr = new StringBuilder("/" + event.command.getCommandName());
        for (String arg : event.parameters) cmdStr.append(" ").append(arg);
        final String fullCommand = cmdStr.toString();

        RfgExampleMod.getExecutor().submit(() -> {
            String format = "[Command] " + username + ": " + fullCommand;
            String targetChannel = ConfigHandler.channelsConfig.consoleChannelID.equals("0") ? ConfigHandler.channelsConfig.chatChannelID : ConfigHandler.channelsConfig.consoleChannelID;
            DiscordListener.sendNativeChannelMessage(targetChannel, "`" + format + "`", false);
        });
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.entityLiving instanceof EntityPlayer) {
            final EntityPlayer player = (EntityPlayer) event.entityLiving;
            final String username = player.getCommandSenderName();
            String localDeathMessage = "died.";
            try {
                ChatComponentTranslation trans = (ChatComponentTranslation) player.func_110142_aN().func_151521_b();
                localDeathMessage = trans.getUnformattedText();
                if (localDeathMessage.startsWith(username + " ")) localDeathMessage = localDeathMessage.substring(username.length() + 1);
            } catch (Exception e) {}
            
            final String finalDeathMessage = localDeathMessage;
            RfgExampleMod.getExecutor().submit(() -> {
                String formatPattern = LanguageManager.getDiscordDeathFormat(username, finalDeathMessage);
                DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, formatPattern.replace("%player%", username).replace("%message%", finalDeathMessage), false);
            });
        }
    }

    @SubscribeEvent
    public void onPlayerAchievement(AchievementEvent event) {
        if (event.entityPlayer == null || event.achievement == null) return;
        if (!(event.entityPlayer instanceof EntityPlayerMP)) return;
        final EntityPlayerMP playerMP = (EntityPlayerMP) event.entityPlayer;
        StatisticsFile statsFile = net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().func_152602_a(playerMP);
        if (statsFile != null && !statsFile.hasAchievementUnlocked(event.achievement)) {
            final String username = playerMP.getCommandSenderName();
            final String achievementName = event.achievement.func_150951_e().getUnformattedText();
            RfgExampleMod.getExecutor().submit(() -> {
                String formatPattern = LanguageManager.getDiscordAchievementFormat(username, achievementName);
                DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, formatPattern.replace("%player%", username).replace("%achievement%", achievementName), false);
            });
        }
    }
}