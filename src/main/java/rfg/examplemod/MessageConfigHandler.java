package rfg.examplemod;

import com.moandjiezana.toml.Toml;
import org.apache.logging.log4j.Logger;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;

public class MessageConfigHandler {
    private static Logger logger;
    private static Toml config;
    private static File configFile;

    public static class MessagesContainer {
        // 🔓 加上 public 關鍵字，允許 MinecraftListener 跨類別高效直接存取
        public String playerJoined = "";
        public String playerLeft = "";
        public String chat = "";
        public String death = "";
        public String achievement = "";
        public String minecraftCommand = "";

        public String minecraftLoginPanelNotice = "";
        public String minecraftLoginPanelInstruction = "";
        public String minecraftLoginPanelFooter = "";

        public String discordServerStarted = "";
        public String discordServerStopped = "";
        public String discordVerifyFormatError = "";
        public String discordVerifyInvalidError = "";
        public String discordVerifySuccess = "";
        public String discordToMinecraftChat = "";
    }

    public static MessagesContainer messages = new MessagesContainer();

    public static void init(File configDirectory, Logger log) {
        logger = log;
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }
        configFile = new File(configDirectory, "rfg-discord-messages.toml");
        if (!configFile.exists()) {
            try {
                writeDefaultConfig();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        loadConfig();
    }

    private static void writeDefaultConfig() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {
            writer.write("# MCToDC - Custom Multilingual & Display Template Profile\n\n");
            
            writer.write("[minecraft_to_discord]\n");
            writer.write("playerJoined = \"**%player%** Joined the game from Discord Link (%discord%)\"\n");
            writer.write("playerLeft = \"**%player%** Left the server.\"\n");
            writer.write("chat = \"<%player%> %message%\"\n");
            writer.write("death = \"☠️ **%player%** %message%\"\n");
            writer.write("achievement = \"🏆 **%player%** just earned the achievement [%achievement%]\"\n");
            writer.write("minecraftCommand = \"💻 [Console Auditing] %user%: %message%\"\n\n");

            writer.write("[minecraft_login_panel]\n");
            writer.write("minecraftLoginPanelNotice = \"Discord Account Binding Verification Required\"\n");
            writer.write("minecraftLoginPanelInstruction = \"Please type the command below in our Discord server integration gateway channel:\"\n");
            writer.write("minecraftLoginPanelFooter = \"Your account state access permission will recover automatically upon token authorization sync.\"\n\n");

            writer.write("[discord_to_minecraft]\n");
            writer.write("discordServerStarted = \"🟢 **Minecraft Server Infrastructure Online**\"\n");
            writer.write("discordServerStopped = \"🔴 **Minecraft Server Infrastructure Offline**\"\n");
            writer.write("discordVerifyFormatError = \"❌ **Verification Failed**: Wrong argument structure, use: `!verify <4-digit-code>`\"\n");
            writer.write("discordVerifyInvalidError = \"❌ **Verification Failed**: Verification token expired or identity context does not exist.\"\n");
            writer.write("discordVerifySuccess = \"✅ **Verification Completed**: Discord account bound with player id **%user%** successfully!\"\n");
            writer.write("discordToMinecraftChat = \"§9[Discord] §f%user%: %message%\"\n");
        }
    }

    public static void loadConfig() {
        try {
            config = new Toml().read(configFile);

            messages.playerJoined = config.getString("minecraft_to_discord.playerJoined", "");
            messages.playerLeft = config.getString("minecraft_to_discord.playerLeft", "");
            messages.chat = config.getString("minecraft_to_discord.chat", "");
            messages.death = config.getString("minecraft_to_discord.death", "");
            messages.achievement = config.getString("minecraft_to_discord.achievement", "");
            messages.minecraftCommand = config.getString("minecraft_to_discord.minecraftCommand", "");

            messages.minecraftLoginPanelNotice = config.getString("minecraft_login_panel.minecraftLoginPanelNotice", "");
            messages.minecraftLoginPanelInstruction = config.getString("minecraft_login_panel.minecraftLoginPanelInstruction", "");
            messages.minecraftLoginPanelFooter = config.getString("minecraft_login_panel.minecraftLoginPanelFooter", "");

            messages.discordServerStarted = config.getString("discord_to_minecraft.discordServerStarted", "");
            messages.discordServerStopped = config.getString("discord_to_minecraft.discordServerStopped", "");
            messages.discordVerifyFormatError = config.getString("discord_to_minecraft.discordVerifyFormatError", "");
            messages.discordVerifyInvalidError = config.getString("discord_to_minecraft.discordVerifyInvalidError", "");
            messages.discordVerifySuccess = config.getString("discord_to_minecraft.discordVerifySuccess", "");
            messages.discordToMinecraftChat = config.getString("discord_to_minecraft.discordToMinecraftChat", "");

        } catch (Exception e) {
            logger.error("[MCToDC] Message Config file loading error: " + e.getMessage());
        }
    }
}