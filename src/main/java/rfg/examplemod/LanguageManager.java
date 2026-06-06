package rfg.examplemod;

public class LanguageManager {

    public static String getMcKickReason(String code) {
        String notice = MessageConfigHandler.messages.minecraftLoginPanelNotice;
        String instruction = MessageConfigHandler.messages.minecraftLoginPanelInstruction;
        String footer = MessageConfigHandler.messages.minecraftLoginPanelFooter;

        if (notice == null || notice.isEmpty()) notice = "Verification Required: You are currently isolated.";
        if (instruction == null || instruction.isEmpty()) instruction = "Please check your verification code and enter the command in the Discord channel:";
        if (footer == null || footer.isEmpty()) footer = "Action will be automatically un-restricted upon successful linking.";

        return "§c" + notice + "\n\n" +
               "§e" + instruction + "\n" +
               "§b!verify " + code + "\n\n" +
               "§7" + footer;
    }

    // 👈 新增：當玩家被發現退群時的專用遊戲內 Kick 面板提示
    public static String getMcLeftGuildKickReason() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "§c連線拒絕：您目前不在指定的 Discord 伺服器中！\n\n" +
                   "§e檢測到您的帳號雖然曾有綁定紀錄，但目前已退出了官方群組。\n" +
                   "§b請重新加入 Discord 伺服器，並於進服時獲取新的驗證碼重新綁定。";
        }
        return "§cConnection Refused: You are not in the designated Discord Server!\n\n" +
               "§eWe detected that you have left or been kicked from the official community.\n" +
               "§bPlease re-join the Discord Guild to recover your gameplay access permit.";
    }

    public static String getDiscordPlayerJoined(String username, String discordName) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "**" + username + "** 進入了伺服器 ";
        return "**" + username + "** joined the server! ";
    }

    public static String getDiscordPlayerLeft(String username) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "**" + username + "** 離開了伺服器";
        return "**" + username + "** left the server";
    }

    public static String getDiscordChatFormat(String username, String message) {
        return "**" + username + "**: " + message;
    }

    public static String getDiscordDeathFormat(String username, String deathMessage) {
        return "**" + username + "** " + deathMessage;
    }

    public static String getDiscordServerStarted() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "**伺服器已開啟。**";
        return "**Server has started!** Bridge gateway online.";
    }

    public static String getDiscordServerStopped() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "**伺服器已關閉。**";
        return "**Server has been shut down.**";
    }

    public static String getDiscordVerifyFormatError() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "**驗證失敗**：格式錯誤！請使用 `!verify <四位數驗證碼>`。";
        return "**Verification Failed**: Invalid format! Please use `!verify <4-digit code>`.";
    }

    public static String getDiscordVerifyInvalidError() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "**驗證失敗**：無效的驗證碼，或者該玩家目前不在線上快取中。";
        return "**Verification Failed**: Invalid token, or player cache has expired.";
    }

    public static String getDiscordVerifySuccess(String username) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "**驗證成功**：您的 Discord 帳號已成功與遊戲 ID **" + username + "** 鎖定綁定！現在可以進服開玩囉！";
        return "**Verification Success**: Your Discord account has been linked to **" + username + "**! You can join the server now.";
    }

    public static String getDiscordAchievementFormat(String username, String achievementName) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "**" + username + "** 獲得了成就：[" + achievementName + "]";
        return "**" + username + "** has just earned the achievement: [" + achievementName + "]";
    }

    public static String getLogModuleDisabled() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "[MCToDC] 模組在設定檔中已關閉，跳過核心載入。";
        return "[MCToDC] Module is disabled in config. Skipping core load.";
    }

    public static String getLogGatewayInitializing() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "[MCToDC] 正在初始化輕量化原生 HTTP REST 閘道...";
        return "[MCToDC] Initializing light-weight native HTTP REST gateway...";
    }

    public static String getLogGatewaySuccess() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "[MCToDC] 本地資料庫與安全代理閘道已成功加載完畢。";
        return "[MCToDC] Server has loaded local database and secure proxy channel successfully.";
    }

    public static String getLogServerShuttingDown() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) return "[MCToDC] 伺服器正在關閉...";
        return "[MCToDC] Server is shutting down...";
    }
}