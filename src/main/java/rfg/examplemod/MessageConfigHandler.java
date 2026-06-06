package rfg.examplemod;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MessageConfigHandler {
    private static File configFile;
    public static MessagesContainer messages = new MessagesContainer();

    public static void init(File configDir, Logger logger) {
        configFile = new File(configDir, "rfg-discord-messages.toml");
        if (!configFile.exists()) {
            try {
                writeDefaultConfig();
                logger.info(" Generated default localized messages configuration.");
            } catch (IOException e) {
                logger.error(" Failed to generate message config: " + e.getMessage());
            }
        }
        loadConfig();
    }

    private static void writeDefaultConfig() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {
            writer.write("# MCToDC Localized Messages Configuration\n");
            writer.write("# All entries support custom text adjustments\n\n");
            
            writer.write("[discord_announcements]\n");
            writer.write("serverStarted = \"Server has loaded local database and secure proxy channel successfully.\"\n");
            writer.write("serverStopped = \"Server is shutting down...\"\n");
            writer.write("playerJoin = \"**%user%** joined the server.\"\n");
            writer.write("playerLeave = \"**%user%** left the server.\"\n");
            writer.write("playerDeath = \"**%message%**\"\n");
            writer.write("playerAchievement = \"🏆 **%user% has earned the achievement [%achievement%]!**\"\n\n"); // 👈 預設加入無 Emoji 純淨粗體成就文本

            writer.write("[discord_responses]\n");
            writer.write("verifyFormatError = \"Verify failed: Invalid format. Usage: !verify 4-digit-code\"\n");
            writer.write("verifyInvalidError = \"Verify failed: Invalid token or player is offline.\"\n");
            writer.write("verifySuccess = \"Verification Success! Account %user% linked with local storage database successfully.\"\n\n");

            writer.write("[minecraft_login_panel]\n");
            writer.write("notice = \"Verification Required: You are currently isolated.\"\n");
            writer.write("instruction = \"Please check your verification code and enter the command in the Discord channel:\"\n");
            writer.write("footer = \"Action will be automatically un-restricted upon successful linking.\"\n\n");

            writer.write("[minecraft_game_notices]\n");
            writer.write("verifySuccessNotice = \"Verification complete. Account fully activated!\"\n\n");

            writer.write("[chat_formatting]\n");
            writer.write("discordToMinecraftChat = \"<Discord> %user%: %message%\"\n");
            writer.write("minecraftToDiscordChat = \"<%user%> %message%\"\n");
            writer.write("minecraftCommand = \"[Command] %user%: %message%\"\n");
        }
    }

    private static void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue;
                if (line.contains("=")) {
                    String key = line.substring(0, line.indexOf("=")).trim();
                    String val = line.substring(line.indexOf("=") + 1).trim();
                    if (val.startsWith("\"") && val.endsWith("\"")) {
                        val = val.substring(1, val.length() - 1);
                    }
                    val = StringEscapeUtils.unescapeJava(val);
                    assignKey(key, val);
                }
            }
        } catch (Exception e) {}
    }

    private static void assignKey(String key, String val) {
        switch (key) {
            case "serverStarted": messages.discordServerStarted = val; break;
            case "serverStopped": messages.discordServerStopped = val; break;
            case "playerJoin": messages.discordPlayerJoin = val; break;
            case "playerLeave": messages.discordPlayerLeave = val; break;
            case "playerDeath": messages.discordPlayerDeath = val; break;
            case "playerAchievement": messages.discordPlayerAchievement = val; break;
            case "verifyFormatError": messages.discordVerifyFormatError = val; break;
            case "verifyInvalidError": messages.discordVerifyInvalidError = val; break;
            case "verifySuccess": messages.discordVerifySuccess = val; break;
            case "notice": messages.minecraftLoginPanelNotice = val; break;
            case "instruction": messages.minecraftLoginPanelInstruction = val; break;
            case "footer": messages.minecraftLoginPanelFooter = val; break;
            case "verifySuccessNotice": messages.minecraftVerifySuccessNotice = val; break;
            case "discordToMinecraftChat": messages.discordToMinecraftChat = val; break;
            case "minecraftToDiscordChat": messages.minecraftToDiscordChat = val; break;
            case "minecraftCommand": messages.minecraftCommand = val; break;
        }
    }

    public static class MessagesContainer {
        public String discordServerStarted = "";
        public String discordServerStopped = "";
        public String discordPlayerJoin = "";
        public String discordPlayerLeave = "";
        public String discordPlayerDeath = "";
        public String discordPlayerAchievement = ""; 
        public String discordVerifyFormatError = "";
        public String discordVerifyInvalidError = "";
        public String discordVerifySuccess = "";
        public String minecraftLoginPanelNotice = "";
        public String minecraftLoginPanelInstruction = "";
        public String minecraftLoginPanelFooter = "";
        public String minecraftVerifySuccessNotice = "";
        public String discordToMinecraftChat = "";
        public String minecraftToDiscordChat = "";
        public String minecraftCommand = "";
    }
}