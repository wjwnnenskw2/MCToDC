package rfg.examplemod;

public class LanguageManager {

    public static String getDiscordServerStarted() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "伺服器聊天橋接器已成功初始化。";
        }
        return "Server chat bridge has been initialized successfully.";
    }

    public static String getDiscordServerStopped() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "伺服器已安全關閉。";
        }
        return "Server has been stopped safely.";
    }

    public static String getMcKickReason(String code) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "[MCToDC] 您的帳號尚未通過 Discord 進服驗證。\n\n請加入我們的 Discord 伺服器並在指定頻道輸入以下指令：\n!verify " + code;
        }
        return "[MCToDC] You are not verified via Discord yet.\n\nPlease join our Discord server and use the command in the channel:\n!verify " + code;
    }

    public static String getDiscordPlayerJoined(String player, String discordName) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "玩家 " + player + " (Discord: " + discordName + ") 已加入伺服器。";
        }
        return "Player " + player + " (Discord: " + discordName + ") joined the server.";
    }

    public static String getDiscordPlayerLeft(String player) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "玩家 " + player + " 已離開伺服器。";
        }
        return "Player " + player + " left the server.";
    }

    public static String getDiscordChatFormat(String player, String message) {
        return "[" + player + "]: " + message;
    }

    public static String getDiscordDeathFormat(String player, String message) {
        return "Player " + player + " " + message;
    }

    public static String getDiscordAchievementFormat(String player, String achievement) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "玩家 " + player + " 獲得了成就: [" + achievement + "]";
        }
        return "Player " + player + " earned the achievement: [" + achievement + "]";
    }

    public static String getDiscordVerifyFormatError() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "驗證錯誤: 指令格式無效。請使用 `!verify [4位數驗證碼]`";
        }
        return "Verification error: Invalid command format. Use `!verify [4-digit code]`";
    }

    public static String getDiscordVerifyInvalidError() {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "驗證錯誤: 驗證碼無效或已過期。";
        }
        return "Verification error: Code is invalid or has expired.";
    }

    public static String getDiscordVerifySuccess(String player) {
        if ("zh_tw".equalsIgnoreCase(ConfigHandler.generalConfig.language)) {
            return "驗證成功: 帳號已成功綁定玩家 " + player + "。白名單已更新。";
        }
        return "Verification success: Account bound to player " + player + ". Whitelist updated.";
    }

    public static String getLogGatewayInit() {
        return "[MCToDC] Initializing light-weight native HTTP REST gateway...";
    }

    public static String getLogGatewaySuccess() {
        return "[MCToDC] Local storage and network proxy channel loaded successfully.";
    }

    public static String getLogShuttingDown() {
        return "[MCToDC] Stopping server connection channels...";
    }
}