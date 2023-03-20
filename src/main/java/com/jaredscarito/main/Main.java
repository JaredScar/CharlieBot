package com.jaredscarito.main;

import com.jaredscarito.listeners.commands.general.GeneralCommandEventListener;
import com.jaredscarito.listeners.messaging.general.GeneralMessageEventListener;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.sql.SQLHelper;
import com.jaredscarito.managers.MuteManager;
import com.jaredscarito.managers.TicketManager;
import com.timvisee.yamlwrapper.YamlConfiguration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

public class Main {
    private static JDA JDA_INSTANCE = null;
    private static final Main main = new Main();
    private static TicketManager ticketManager = null;
    public static Main getInstance() {
        return main;
    }
    public JDA getJDA() {
        return JDA_INSTANCE;
    }

    private final YamlConfiguration config = YamlConfiguration.loadFromFile(new File("config/config.yml"));
    public YamlConfiguration getConfig() {
        return this.config;
    }
    public boolean saveConfig() {
        File f = new File("config/config.yml");
        try {
            this.config.save(f);
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.log(ex);
            return false;
        }
        return true;
    }
    public TicketManager getTicketManager() {
        return ticketManager;
    }
    public SQLHelper getSqlHelper() {
        String host = getConfig().getString("Database.Host");
        int port = getConfig().getInt("Database.Port");
        String username = getConfig().getString("Database.Username");
        String password = getConfig().getString("Database.Password");
        String db = getConfig().getString("Database.DB");
        SQLHelper helper = null;
        try {
            helper = new SQLHelper(host, port, db, username, password);
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return helper;
    }
    public static void main(String[] args) throws InterruptedException {
        String token = main.getConfig().getString("Bot.Token");
        JDA jdaInstance = JDABuilder.createDefault(token).enableIntents(
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_MEMBERS
        ).setMemberCachePolicy(MemberCachePolicy.ALL).setChunkingFilter(ChunkingFilter.ALL).build();
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
        ticketManager = new TicketManager();
        jdaInstance.addEventListener(
                new GeneralCommandEventListener(),
                new GeneralMessageEventListener(),
                new MuteManager(),
                ticketManager
        );
    }
}