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
                } catch (Exception e) {
                    RfgExampleMod.logger.error("[MCToDC] Asynchronous SQL check failure: " + e.getMessage());
                }
            }

            if (isVerified && !RfgExampleMod.boundPlayers.has(username)) {
                try {
                    com.google.gson.JsonObject playerData = new com.google.gson.JsonObject();
                    playerData.addProperty("discordID", discordIdFromSQL);
                    playerData.addProperty("discordName", discordName);
                    playerData.addProperty("uuid", uuid);
                    RfgExampleMod.boundPlayers.add(username, playerData);
                    RfgExampleMod.saveBinds();
                    RfgExampleMod.logger.info("[MCToDC] Successfully backfilled remote SQL data into local JSON cache for player: " + username);
                } catch (Exception e) {}
            }

            if (!isVerified) {
                if (RfgExampleMod.boundPlayers.has(username)) {
                    isVerified = true;
                    try {
                        discordName = RfgExampleMod.boundPlayers.getAsJsonObject(username).get("discordName").getAsString();
                    } catch (Exception e) {}
                }
            }

            final boolean verifiedStatus = isVerified;
            final String finalDiscordName = discordName;

            if (verifiedStatus) {
                String formatPattern = "";
                try {
                    java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("discordPlayerJoined");
                    String custom = (String) f.get(MessageConfigHandler.messages);
                    if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
                } catch (Exception e1) {
                    try {
                        java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("playerJoined");
                        String custom = (String) f.get(MessageConfigHandler.messages);
                        if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
                    } catch (Exception e2) {}
                }

                if (formatPattern.isEmpty()) {
                    formatPattern = LanguageManager.getDiscordPlayerJoined(username, finalDiscordName);
                }

                String announce = formatPattern.replace("%player%", username).replace("%discord%", finalDiscordName);
                DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, announce, false);
            } else {
                String code = String.format("%04d", (int)(Math.random() * 10000));
                RfgExampleMod.pendingVerifications.put(username, new String[]{code, uuid});
                
                try {
                    if (player.playerNetServerHandler != null) {
                        String kickReason = LanguageManager.getMcKickReason(code);
                        player.playerNetServerHandler.kickPlayerFromServer(kickReason);
                        RfgExampleMod.logger.info("[MCToDC] Kicked unverified player: " + username + " with temp code: " + code);
                    }
                } catch (Exception e) {
                    RfgExampleMod.logger.error("[MCToDC] Disconnection execution fault: " + e.getMessage());
                }
            }
        });
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        final String username = event.player.getCommandSenderName();
        if (RfgExampleMod.pendingVerifications.containsKey(username)) {
            return; 
        }

        RfgExampleMod.getExecutor().submit(() -> {
            String formatPattern = "";
            try {
                java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("discordPlayerLeft");
                String custom = (String) f.get(MessageConfigHandler.messages);
                if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("playerLeft");
                    String custom = (String) f.get(MessageConfigHandler.messages);
                    if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
                } catch (Exception e2) {}
            }

            if (formatPattern.isEmpty()) {
                formatPattern = LanguageManager.getDiscordPlayerLeft(username);
            }

            String announce = formatPattern.replace("%player%", username);
            DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, announce, false);
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
                String formatPattern = "";
                try {
                    java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("discordChat");
                    String custom = (String) f.get(MessageConfigHandler.messages);
                    if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
                } catch (Exception e1) {
                    try {
                        java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("chat");
                        String custom = (String) f.get(MessageConfigHandler.messages);
                        if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
                    } catch (Exception e2) {}
                }

                if (formatPattern.isEmpty()) {
                    formatPattern = LanguageManager.getDiscordChatFormat(username, message);
                }

                String format = formatPattern.replace("%player%", username).replace("%message%", message);
                DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, format, false);
            }
        });
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!ConfigHandler.chatConfig.sendCommandMessages) return;
        if (event.sender == null) return;

        final String username = event.sender.getCommandSenderName();
        // 過濾掉命令方塊與後台伺服器本身的指令洗頻
        if (username.equals("@") || username.equals("Server")) return;

        final String commandName = event.command.getCommandName();
        final String[] args = event.parameters;
        
        StringBuilder cmdStr = new StringBuilder("/" + commandName);
        for (String arg : args) {
            cmdStr.append(" ").append(arg);
        }
        final String fullCommand = cmdStr.toString();

        RfgExampleMod.getExecutor().submit(() -> {
            String formatPattern = MessageConfigHandler.messages.minecraftCommand;
            if (formatPattern == null || formatPattern.isEmpty()) {
                formatPattern = "[Command] %user%: %message%";
            }
            String format = formatPattern.replace("%user%", username).replace("%message%", fullCommand);
            // 預設將管理員指令日誌發送至 Console 頻道
            String targetChannel = ConfigHandler.channelsConfig.consoleChannelID.equals("0") ? 
                                   ConfigHandler.channelsConfig.chatChannelID : ConfigHandler.channelsConfig.consoleChannelID;
            
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
                if (localDeathMessage.startsWith(username + " ")) {
                    localDeathMessage = localDeathMessage.substring(username.length() + 1);
                }
            } catch (Exception e) {
                localDeathMessage = "died in battle.";
            }
            
            final String finalDeathMessage = localDeathMessage;

            RfgExampleMod.getExecutor().submit(() -> {
                String formatPattern = "";
                try {
                    java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("discordDeath");
                    String custom = (String) f.get(MessageConfigHandler.messages);
                    if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
                } catch (Exception e1) {
                    try {
                        java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("death");
                        String custom = (String) f.get(MessageConfigHandler.messages);
                        if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
                    } catch (Exception e2) {}
                }

                if (formatPattern.isEmpty()) {
                    formatPattern = LanguageManager.getDiscordDeathFormat(username, finalDeathMessage);
                }

                String format = formatPattern.replace("%player%", username).replace("%message%", finalDeathMessage);
                DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, format, false);
            });
        }
    }

    @SubscribeEvent
    public void onPlayerAchievement(AchievementEvent event) {
        if (event.entityPlayer == null || event.achievement == null) return;
        if (!(event.entityPlayer instanceof EntityPlayerMP)) return;
        
        final EntityPlayerMP playerMP = (EntityPlayerMP) event.entityPlayer;
        
        StatisticsFile statsFile = net.minecraft.server.MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152602_a(playerMP);

        if (statsFile != null) {
            if (!statsFile.hasAchievementUnlocked(event.achievement)) {
                final String username = playerMP.getCommandSenderName();
                final String achievementName = event.achievement.func_150951_e().getUnformattedText();

                RfgExampleMod.getExecutor().submit(() -> {
                    String formatPattern = "";
                    try {
                        java.lang.reflect.Field f = MessageConfigHandler.messages.getClass().getDeclaredField("discordAchievement");
                        String custom = (String) f.get(MessageConfigHandler.messages);
                        if (custom != null && !custom.trim().isEmpty()) formatPattern = custom;
                    } catch (Exception e) {}

                    if (formatPattern.isEmpty()) {
                        formatPattern = LanguageManager.getDiscordAchievementFormat(username, achievementName);
                    }

                    String format = formatPattern.replace("%player%", username).replace("%achievement%", achievementName);
                    DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, format, false);
                });
            }
        }
    }
}