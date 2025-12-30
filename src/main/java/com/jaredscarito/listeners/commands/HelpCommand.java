package com.jaredscarito.listeners.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;

public class HelpCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member member = evt.getMember();
        if (member == null || member.getUser().isBot()) return;
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🐕 CharlieBot Help");
        eb.setDescription("Welcome to CharlieBot! Here are all the available commands:");
        eb.setColor(Color.CYAN);
        eb.setAuthor(member.getEffectiveName(), member.getAvatarUrl());
        
        // Add command categories
        addModerationCommands(eb);
        addFunCommands(eb);
        addUtilityCommands(eb);
        addPointsCommands(eb);
        
        eb.setFooter("CharlieBot - Created in memory of Charlie (March 09, 2023)");
        
        evt.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }
    
    private static void addModerationCommands(EmbedBuilder eb) {
        StringBuilder modCommands = new StringBuilder();
        modCommands.append("`/warn add [user]` - Warn a user\n");
        modCommands.append("`/warn remove [user]` - Remove a warning\n");
        modCommands.append("`/kick add [user]` - Kick a user\n");
        modCommands.append("`/ban add [user]` - Ban a user\n");
        modCommands.append("`/ban remove [user]` - Unban a user\n");
        modCommands.append("`/mute add [user]` - Mute a user\n");
        modCommands.append("`/mute remove [user]` - Unmute a user\n");
        modCommands.append("`/blacklist add [user]` - Blacklist a user\n");
        modCommands.append("`/blacklist remove [user]` - Remove from blacklist\n");
        modCommands.append("`/lockdown enable` - Enable server lockdown\n");
        modCommands.append("`/lockdown disable` - Disable server lockdown\n");
        modCommands.append("`/history view [user]` - View user's punishment history\n");
        modCommands.append("`/history clear [user]` - Clear user's punishment history\n");
        
        eb.addField("🛡️ Moderation Commands", modCommands.toString(), false);
    }
    
    private static void addFunCommands(EmbedBuilder eb) {
        StringBuilder funCommands = new StringBuilder();
        funCommands.append("`/bark` - Get a random picture of Charlie! 🐕\n");
        funCommands.append("`/gamble [points]` - Gamble your points in a slot machine\n");
        
        eb.addField("🎮 Fun Commands", funCommands.toString(), false);
    }
    
    private static void addUtilityCommands(EmbedBuilder eb) {
        StringBuilder utilCommands = new StringBuilder();
        utilCommands.append("`/rules` - View server rules\n");
        utilCommands.append("`/rules add [section] [rule]` - Add a new rule\n");
        utilCommands.append("`/rules edit [rule_id] [new_rule]` - Edit an existing rule\n");
        utilCommands.append("`/rules remove [rule_id]` - Remove a rule\n");
        utilCommands.append("`/ticket add [user]` - Add user to ticket\n");
        utilCommands.append("`/ticket remove [user]` - Remove user from ticket\n");
        utilCommands.append("`/ticket members` - List ticket members\n");
        utilCommands.append("`/ticket lock` - Lock the ticket\n");
        utilCommands.append("`/ticket unlock` - Unlock the ticket\n");
        utilCommands.append("`/sticky add [message]` - Add sticky message to channel\n");
        utilCommands.append("`/sticky remove` - Remove sticky message from channel\n");
        
        eb.addField("🔧 Utility Commands", utilCommands.toString(), false);
    }
    
    private static void addPointsCommands(EmbedBuilder eb) {
        StringBuilder pointsCommands = new StringBuilder();
        pointsCommands.append("`/points` - View your points\n");
        pointsCommands.append("`/points give [amount] [member]` - Give points to another user\n");
        pointsCommands.append("`/points leaderboard` - View the points leaderboard\n");
        pointsCommands.append("`/points store` - View the points store\n");
        pointsCommands.append("`/points redeem [item] [amount]` - Redeem items from the store\n");
        
        eb.addField("💰 Points System", pointsCommands.toString(), false);
    }
}
