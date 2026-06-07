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
            String discordId = "";
            boolean forceKickDueToLeftServer = false;
            
            // 🔒 漏洞防禦核心：標記遠端資料庫是否成功完成了權威性查詢
            boolean sqlQueryExecutedSuccessfully = false;

            // 1. 嘗試進行遠端 SQL 查詢
            if (ConfigHandler.databaseConfig.useRemoteSQL) {
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                    try (Connection conn = DriverManager.getConnection(ConfigHandler.databaseConfig.sqlUrl, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword)) {
                        String query = "SELECT `discord_id`, `discord_name` FROM `" + ConfigHandler.databaseConfig.sqlTableName + "` WHERE `username` = ? OR `uuid` = ? LIMIT 1;";
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            stmt.setString(1, username);
                            stmt.setString(2, uuid);
                            try (ResultSet rs = stmt.executeQuery()) {
                                sqlQueryExecutedSuccessfully = true; // 🟢 正常連線並執行完畢
                                if (rs.next()) {
                                    isVerified = true;
                                    discordId = rs.getString("discord_id");
                                    discordName = rs.getString("discord_name");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    sqlQueryExecutedSuccessfully = false; // 🔴 拋出 Exception，連線故障
                    RfgExampleMod.logger.error("[MCToDC] SQL Remote failure (Will fallback to local JSON): " + e.getMessage());
                }
            }

            // 情況 A：遠端 SQL 正常執行，且判定該玩家【已經被管理員手動刪除白名單】
            if (ConfigHandler.databaseConfig.useRemoteSQL && sqlQueryExecutedSuccessfully && !isVerified) {
                // 如果本地 JSON 還殘留有這個人的快取，必須順手抹除，以遠端權威數據為最高指導原則
                if (RfgExampleMod.boundPlayers.has(username)) {
                    RfgExampleMod.boundPlayers.remove(username);
                    RfgExampleMod.saveBinds();
                    RfgExampleMod.logger.warn("[MCToDC] Player " + username + " was deleted from Remote SQL. Synchronized and wiped local JSON cache.");
                }
            }

            // 情況 B：只有在「沒開啟 SQL」或者「SQL 連線故障斷線 (Exception)」時，才允許讀取本地 JSON 備援
            if (!isVerified) {
                boolean allowJsonFallback = !ConfigHandler.databaseConfig.useRemoteSQL || !sqlQueryExecutedSuccessfully;
                
                if (allowJsonFallback && RfgExampleMod.boundPlayers.has(username)) {
                    try {
                        com.google.gson.JsonObject playerData = RfgExampleMod.boundPlayers.getAsJsonObject(username);
                        discordId = playerData.get("discordID").getAsString();
                        discordName = playerData.get("discordName").getAsString();
                        isVerified = true;
                        RfgExampleMod.logger.info("[MCToDC] Discovered local JSON cache for " + username + " during Remote SQL breakdown fallback session.");
                    } catch (Exception e) {}
                }
            }

            // 2. 如果是遠端新綁定成功、本地還沒快取到的資料，進行回填
            if (isVerified && sqlQueryExecutedSuccessfully && !RfgExampleMod.boundPlayers.has(username)) {
                try {
                    com.google.gson.JsonObject playerData = new com.google.gson.JsonObject();
                    playerData.addProperty("discordID", discordId);
                    playerData.addProperty("discordName", discordName);
                    playerData.addProperty("uuid", uuid);
                    RfgExampleMod.boundPlayers.add(username, playerData);
                    RfgExampleMod.saveBinds();
                } catch (Exception e) {}
            }

            // 3. 跨平台二次校驗：檢查玩家是否還在 Discord 伺服器群組中
            if (isVerified) {
                boolean isInServer = DiscordListener.isUserInDiscordServer(discordId);
                if (!isInServer) {
                    isVerified = false;
                    forceKickDueToLeftServer = true; // 標記為退群踢出
                    
                    // 徹底除名
                    RfgExampleMod.boundPlayers.remove(username);
                    RfgExampleMod.saveBinds();
                    
                    if (ConfigHandler.databaseConfig.useRemoteSQL) {
                        try (Connection conn = DriverManager.getConnection(ConfigHandler.databaseConfig.sqlUrl, ConfigHandler.databaseConfig.sqlUser, ConfigHandler.databaseConfig.sqlPassword)) {
                            String delQuery = "DELETE FROM `" + ConfigHandler.databaseConfig.sqlTableName + "` WHERE `username` = ?;";
                            try (PreparedStatement delStmt = conn.prepareStatement(delQuery)) {
                                delStmt.setString(1, username);
                                delStmt.executeUpdate();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // 4. 最後准入判定
            final boolean finalVerified = isVerified;
            final String finalDiscordName = discordName;
            final boolean finalLeftKick = forceKickDueToLeftServer;

            if (finalVerified) {
                String formatPattern = MessageConfigHandler.messages != null && MessageConfigHandler.messages.playerJoined != null ? MessageConfigHandler.messages.playerJoined : "";
                if (formatPattern.isEmpty()) formatPattern = LanguageManager.getDiscordPlayerJoined(username, finalDiscordName);
                String announce = formatPattern.replace("%player%", username).replace("%discord%", finalDiscordName);
                DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, announce, false);
            } else {
                String code = String.format("%04d", (int)(Math.random() * 10000));
                RfgExampleMod.pendingVerifications.put(username, new String[]{code, uuid});
                
                try {
                    if (player.playerNetServerHandler != null) {
                        String kickReason = finalLeftKick ? 
                                LanguageManager.getMcLeftGuildKickReason() : LanguageManager.getMcKickReason(code);
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
            String formatPattern = MessageConfigHandler.messages != null && MessageConfigHandler.messages.playerLeft != null ? MessageConfigHandler.messages.playerLeft : "";
            if (formatPattern.isEmpty()) formatPattern = LanguageManager.getDiscordPlayerLeft(username);
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
                String formatPattern = MessageConfigHandler.messages != null && MessageConfigHandler.messages.chat != null ? MessageConfigHandler.messages.chat : "";
                if (formatPattern.isEmpty()) formatPattern = LanguageManager.getDiscordChatFormat(username, message);
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
        if (username.equals("@") || username.equals("Server")) return;

        final String commandName = event.command.getCommandName();
        final String[] args = event.parameters;
        
        StringBuilder cmdStr = new StringBuilder("/" + commandName);
        for (String arg : args) { cmdStr.append(" ").append(arg); }
        final String fullCommand = cmdStr.toString();

        RfgExampleMod.getExecutor().submit(() -> {
            String formatPattern = MessageConfigHandler.messages != null && MessageConfigHandler.messages.minecraftCommand != null ? MessageConfigHandler.messages.minecraftCommand : "[Command] %user%: %message%";
            if (formatPattern.isEmpty()) formatPattern = "[Command] %user%: %message%";
            String format = formatPattern.replace("%user%", username).replace("%message%", fullCommand);
            
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
            } catch (Exception e) { localDeathMessage = "died in battle."; }
            
            final String finalDeathMessage = localDeathMessage;

            RfgExampleMod.getExecutor().submit(() -> {
                String formatPattern = MessageConfigHandler.messages != null && MessageConfigHandler.messages.death != null ? MessageConfigHandler.messages.death : "";
                if (formatPattern.isEmpty()) formatPattern = LanguageManager.getDiscordDeathFormat(username, finalDeathMessage);
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
        StatisticsFile statsFile = net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().func_152602_a(playerMP);

        if (statsFile != null) {
            if (!statsFile.hasAchievementUnlocked(event.achievement)) {
                final String username = playerMP.getCommandSenderName();
                final String achievementName = event.achievement.func_150951_e().getUnformattedText();

                RfgExampleMod.getExecutor().submit(() -> {
                    String formatPattern = MessageConfigHandler.messages != null && MessageConfigHandler.messages.achievement != null ? MessageConfigHandler.messages.achievement : "";
                    if (formatPattern.isEmpty()) formatPattern = LanguageManager.getDiscordAchievementFormat(username, achievementName);
                    String format = formatPattern.replace("%player%", username).replace("%achievement%", achievementName);
                    DiscordListener.sendNativeChannelMessage(ConfigHandler.channelsConfig.chatChannelID, format, false);
                });
            }
        }
    }
}