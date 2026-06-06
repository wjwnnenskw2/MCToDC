package rfg.examplemod;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AchievementEvent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MinecraftListener {

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;

        if (!(event.player instanceof EntityPlayerMP)) return;
        
        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        final String username = player.getCommandSenderName();
        final String uuid = player.getUniqueID().toString();

        RfgExampleMod.getExecutor().submit(() -> {
            boolean isVerified = false;
            String discordName = "Unknown";
            String discordIdFromSQL = "";

            if (ConfigHandler.databaseConfig.useRemoteSQL) {
                int maxRetries = 2;
                while (maxRetries > 0) {
                    try {
                        Class.forName("com.mysql.jdbc.Driver");
                        try (Connection conn = DriverManager.getConnection(ConfigHandler.databaseConfig.sqlUrl, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword)) {
                            if (conn != null && conn.isValid(2)) {
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
                                break; 
                            } else {
                                throw new java.sql.SQLException("Stale connections detected.");
                            }
                        }
                    } catch (Exception e) {
                        maxRetries--;
                        if (maxRetries == 0) {
                            RfgExampleMod.logger.error("[MCToDC] Query verification transaction tracking failure: " + e.getMessage());
                        } else {
                            try { Thread.sleep(500); } catch (Exception ignored) {} 
                        }
                    }
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
                } catch (Exception ignored) {}
            }

            if (!isVerified) {
                if (RfgExampleMod.boundPlayers.has(username)) {
                    isVerified = true;
                    try {
                        discordName = RfgExampleMod.boundPlayers.getAsJsonObject(username).get("discordName").getAsString();
                    } catch (Exception ignored) {}
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
                    } catch (Exception ignored) {}
                }

                if (formatPattern.isEmpty()) {
                    formatPattern = LanguageManager.getDiscordPlayerJoined(username, finalDiscordName);
                }

                String announce = formatPattern.replace("%player%", username).replace("%discord%", finalDiscordName);
                RfgExampleMod.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, announce);
            } else {
                String code = String.format("%04d", (int)(Math.random() * 10000));
                RfgExampleMod.pendingVerifications.put(username, new String[]{code, uuid});
                
                try {
                    if (player.playerNetServerHandler != null) {
                        String kickReason = LanguageManager.getMcKickReason(code);
                        player.playerNetServerHandler.kickPlayerFromServer(kickReason);
                    }
                } catch (Exception e) {
                    RfgExampleMod.logger.error("[MCToDC] Exception during player connection drop tracking: " + e.getMessage());
                }
            }
        });
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;

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
                } catch (Exception ignored) {}
            }

            if (formatPattern.isEmpty()) {
                formatPattern = LanguageManager.getDiscordPlayerLeft(username);
            }

            String announce = formatPattern.replace("%player%", username);
            RfgExampleMod.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, announce);
        });
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;

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
                    } catch (Exception ignored) {}
                }

                if (formatPattern.isEmpty()) {
                    formatPattern = LanguageManager.getDiscordChatFormat(username, message);
                }

                String format = formatPattern.replace("%player%", username).replace("%message%", message);
                RfgExampleMod.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, format);
            }
        });
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;

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
                    } catch (Exception ignored) {}
                }

                if (formatPattern.isEmpty()) {
                    formatPattern = LanguageManager.getDiscordDeathFormat(username, finalDeathMessage);
                }

                String format = formatPattern.replace("%player%", username).replace("%message%", finalDeathMessage);
                RfgExampleMod.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, format);
            });
        }
    }

    @SubscribeEvent
    public void onPlayerAchievement(AchievementEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;

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
                    } catch (Exception ignored) {}

                    if (formatPattern.isEmpty()) {
                        formatPattern = LanguageManager.getDiscordAchievementFormat(username, achievementName);
                    }

                    String format = formatPattern.replace("%player%", username).replace("%achievement%", achievementName);
                    RfgExampleMod.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, format);
                });
            }
        }
    }

    // 🎯【漏洞修復一：補齊獨立的指令監聽器事件通道】
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!ConfigHandler.generalConfig.enabled) return;
        if (!ConfigHandler.chatConfig.sendCommandMessages) return;
        if (event.sender == null || event.command == null) return;

        final String senderName = event.sender.getCommandSenderName();
        final String commandName = event.command.getCommandName();
        
        StringBuilder sb = new StringBuilder("/").append(commandName);
        if (event.parameters != null) {
            for (String param : event.parameters) {
                sb.append(" ").append(param);
            }
        }
        final String rawCommandFull = sb.toString();

        RfgExampleMod.getExecutor().submit(() -> {
            String lower = commandName.toLowerCase();
            if (lower.contains("login") || lower.contains("register") || lower.contains("passwd") || lower.contains("auth")) {
                String securedPayload = "`/" + commandName + " ****** (Sensitive parameters hidden)`";
                sendSecuredAdminCommandMessage(senderName, securedPayload);
                return;
            }

            sendSecuredAdminCommandMessage(senderName, "`" + rawCommandFull + "`");
        });
    }

    private void sendSecuredAdminCommandMessage(String sender, String payload) {
        String consoleChannelId = ConfigHandler.channelsConfig.consoleChannelID;
        if (consoleChannelId == null || consoleChannelId.equals("0") || consoleChannelId.isEmpty()) {
            consoleChannelId = ConfigHandler.channelsConfig.chatChannelID;
        }
        
        String formatted = "[Command Log] " + sender + " executed: " + payload;
        RfgExampleMod.sendNativeChannelMessage(consoleChannelId, formatted);
    }
}