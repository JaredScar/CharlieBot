package com.jaredscarito.main;

import com.jaredscarito.listeners.commands.general.GeneralCommandEventListener;
import com.jaredscarito.listeners.messaging.general.GeneralMessageEventListener;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.managers.*;
import com.jaredscarito.sql.SQLHelper;
import com.timvisee.yamlwrapper.ConfigurationSection;
import com.timvisee.yamlwrapper.YamlConfiguration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

public class Main {
    private static JDA JDA_INSTANCE = null;
    private static final Main main = new Main();
    private static TicketManager ticketManager = null;
    private static KickManager kickManager = null;
    private static MuteManager muteManager = null;
    private static LockdownManager lockdownManager = null;
    private static BanManager banManager = null;
    private static BlacklistManager blacklistManager;
    private static StickyManager stickyManager;

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

    private static void addOption(CommandCreateAction commandCreateAction, SubcommandData subcommandData, String optionType, String optionLabel, String optionDesc, boolean required) {
            if (subcommandData != null)
                subcommandData.addOption(OptionType.valueOf(optionType), optionLabel, optionDesc, required);
            if (commandCreateAction != null)
                commandCreateAction.addOption(OptionType.valueOf(optionType), optionLabel, optionDesc, required);
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
            if (enabled) {
                CommandCreateAction act = jdaInstance.upsertCommand(commandLabel, desc);
                ConfigurationSection optSection = getInstance().getConfig().getConfigurationSection("Bot.Commands." + command
                        + ".Options");
                if (optSection != null) {
                    List<String> opts = optSection.getKeys();
                    for (String optionKey : opts) {
                        String optionLabel = optionKey.toLowerCase();
                        String optionType = optSection.getString(optionKey + ".Type");
                        String optionDesc = optSection.getString(optionKey + ".Description");
                        boolean required = optSection.get(optionKey + ".Required") != null &&
                                optSection.getBoolean(optionKey + ".Required");
                        addOption(act, null, optionType, optionLabel, optionDesc, required);
                    }
                }
                ConfigurationSection subCommandSection = getInstance().getConfig().getConfigurationSection("Bot.Commands." + command
                        + ".Sub-Commands");
                if (subCommandSection != null) {
                    List<String> subCommands = subCommandSection.getKeys();
                    // They have subcommands, add them to act
                    for (String subcommandKey : subCommands) {
                        String subcommandLabel = subcommandKey.toLowerCase();
                        String subcommandDesc = subCommandSection.getString(subcommandKey + ".Description");
                        SubcommandData data = new SubcommandData(subcommandLabel, subcommandDesc);
                        ConfigurationSection optionSection = getInstance().getConfig().getConfigurationSection("Bot.Commands." + command
                                + ".Sub-Commands." + subcommandKey + ".Options");
                        if (optionSection != null) {
                            List<String> options = optionSection.getKeys();
                            for (String optionKey : options) {
                                String optionLabel = optionKey.toLowerCase();
                                String optionType = optionSection.getString(optionKey + ".Type");
                                String optionDesc = optionSection.getString(optionKey + ".Description");
                                boolean required = optionSection.get(optionKey + ".Required") != null &&
                                        optionSection.getBoolean(optionKey + ".Required");
                                addOption(null, data, optionType, optionLabel, optionDesc, required);
                            }
                        }
                        act = act.addSubcommands(data);
                    }
                }
                act.queue();
            }
        }
        JDA_INSTANCE = jdaInstance;
        ticketManager = new TicketManager();
        muteManager = new MuteManager();
        kickManager = new KickManager();
        lockdownManager = new LockdownManager();
        banManager = new BanManager();
        blacklistManager = new BlacklistManager();
        stickyManager = new StickyManager();
        jdaInstance.addEventListener(
                new GeneralCommandEventListener(),
                new GeneralMessageEventListener(),
                muteManager,
                kickManager,
                ticketManager,
                lockdownManager,
                banManager,
                blacklistManager,
                stickyManager,
                new WarnManager()
        );
        StickyManager.initializeStickyMessages();
    }
}