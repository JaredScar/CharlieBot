package com.jaredscarito.main;

import com.jaredscarito.listeners.commands.general.GeneralCommandEventListener;
import com.jaredscarito.listeners.messaging.general.GeneralMessageEventListener;
import com.jaredscarito.threads.MuteManager;
import com.jaredscarito.threads.TicketManager;
import com.timvisee.yamlwrapper.YamlConfiguration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.File;
import java.net.URL;
import java.util.List;

public class Main {
    private static JDA JDA_INSTANCE = null;
    public static Main main = new Main();
    public static Main getInstance() {
        return main;
    }
    public JDA getJDA() {
        return JDA_INSTANCE;
    }
    public YamlConfiguration getConfig() {
        File f = new File("config.yml");
        if (!f.exists()) {
            ClassLoader classLoader = getClass().getClassLoader();
            URL url = classLoader.getResource("config.yml");
            if (url != null)
                f = new File(url.getFile());
        }
        return YamlConfiguration.loadFromFile(f);
    }
    public static void main(String[] args) throws InterruptedException {
        String token = main.getConfig().getString("Bot.Token");
        JDA jdaInstance = JDABuilder.createDefault(token).enableIntents(
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MESSAGES
        ).build();
        jdaInstance.awaitReady();
        List<String> commands = getInstance().getConfig().getConfigurationSection("Bot.Commands").getKeys();
        for (String command : commands) {
            String commandLabel = command.toLowerCase();
            String desc = getInstance().getConfig().getString("Bot.Commands." + command + ".Description");
            boolean enabled = getInstance().getConfig().getBoolean("Bot.Commands." + command + ".Enabled");
            if (enabled)
                jdaInstance.upsertCommand(commandLabel, desc).queue();
        }
        JDA_INSTANCE = jdaInstance;
        jdaInstance.addEventListener(
                new GeneralCommandEventListener(),
                new GeneralMessageEventListener(),
                new MuteManager(),
                new TicketManager()
        );
    }
}