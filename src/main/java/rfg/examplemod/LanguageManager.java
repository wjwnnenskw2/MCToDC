package rfg.examplemod;

public class LanguageManager {

    public static String getMcKickReason(String code) {
<<<<<<< HEAD
        String notice = MessageConfigHandler.messages.minecraftLoginPanelNotice;
        String instruction = MessageConfigHandler.messages.minecraftLoginPanelInstruction;
        String footer = MessageConfigHandler.messages.minecraftLoginPanelFooter;

        if (notice.isEmpty()) notice = "Verification Required: You are currently isolated.";
        if (instruction.isEmpty()) instruction = "Please check your verification code and enter the command in the Discord channel:";
        if (footer.isEmpty()) footer = "Action will be automatically un-restricted upon successful linking.";

        // 使用 Minecraft 原生顏色代碼 § 進行排版
        return "§c" + notice + "\n\n" +
               "§e" + instruction + "\n" +
               "§b!verify " + code + "\n\n" +
               "§7" + footer;
=======
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "§c 您尚未通過 Discord 綁定驗證！\n\n" +
                   "§e請前往伺服器 Discord 聯動頻道\n" +
                   "§e輸入驗證指令：§b!verify " + code + "\n\n" +
                   "";
        }
        return "§c You have not verified your account on Discord yet!\n\n" +
               "§ePlease go to our Discord link channel\n" +
               "§eand type the verify command: §b!verify " + code + "\n\n" +
               "";
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
    }

    public static String getDiscordPlayerJoined(String username, String discordName) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "**" + username + "** 進入了伺服器 ";
        }
        return "**" + username + "** joined the server! ";
    }

    public static String getDiscordPlayerLeft(String username) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "**" + username + "** 離開了伺服器";
        }
        return "**" + username + "** left the server";
    }

    public static String getDiscordChatFormat(String username, String message) {
        return "**" + username + "**: " + message;
    }

    public static String getDiscordDeathFormat(String username, String deathMessage) {
        return "**" + username + "** " + deathMessage;
    }

    public static String getDiscordServerStarted() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "**伺服器已開啟。**";
        }
        return "**Server has started!** Bridge gateway online.";
    }

    public static String getDiscordServerStopped() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "**伺服器已關閉。**";
        }
        return "**Server has been shut down.**";
    }

    public static String getDiscordVerifyFormatError() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "**驗證失敗**：格式錯誤！請使用 `!verify <四位數驗證碼>`。";
        }
        return "**Verification Failed**: Invalid format! Please use `!verify <4-digit code>`.";
    }

    public static String getDiscordVerifyInvalidError() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "**驗證失敗**：無效的驗證碼，或者該玩家目前不在線上快取中。";
        }
        return "**Verification Failed**: Invalid token, or player cache has expired.";
    }

    public static String getDiscordVerifySuccess(String username) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "**驗證成功**：您的 Discord 帳號已成功與遊戲 ID **" + username + "** 鎖定綁定！現在可以進服開玩囉！";
        }
        return "**Verification Success**: Your Discord account has been linked to **" + username + "**! You can join the server now.";
    }

<<<<<<< HEAD
=======
    // 🎯【TODO: 新增成就多語系支援】
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
    public static String getDiscordAchievementFormat(String username, String achievementName) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "**" + username + "** 獲得了成就：[" + achievementName + "]";
        }
        return "**" + username + "** has just earned the achievement: [" + achievementName + "]";
    }
<<<<<<< HEAD
    
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
=======
>>>>>>> parent of 7c4c64d (Delete src/main/java/rfg/examplemod directory)
}