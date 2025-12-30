package com.jaredscarito.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import com.jaredscarito.main.Main;
import com.jaredscarito.models.ActionType;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class Logger {
    private static String getCurrentDatetimeString() {
        Date date = new Date();

        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        formatter.setTimeZone(TimeZone.getTimeZone("America/New_York"));

        return (formatter.format(date));
    }
    public static void log(ActionType actionType, Member performedBy, Member performedOn, List<String> brokenRuleIds, String reason) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File actionLog = new File("logs/actions.txt");
        if (!actionLog.exists()) {
            try {
                actionLog.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (actionLog.exists()) {
            try {
                FileWriter writer = new FileWriter(actionLog, true);
                writer.write("[" + getCurrentDatetimeString() + "] " + actionType.name() + ":");
                writer.write("\nPerformed by: " + performedBy.getUser().getName() + "#" +
                        performedBy.getUser().getDiscriminator() + "");
                writer.write("\nPerformed on: " + performedOn.getUser().getName() + "#" + performedOn.getUser().getDiscriminator());
                writer.write("\nBroken Rules: " + String.join(", ", brokenRuleIds));
                if (reason.length() > 0)
                    writer.write("\nReason: " + reason + "");
                writer.write("\n----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Logger.log(e);
                e.printStackTrace();
            }
        }
        String logChannel = Main.getInstance().getConfig().getString("Bot.Logger.Channel");
        String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
        Guild guild = Main.getInstance().getJDA().getGuildById(guildId);
        if (guild == null) return;
        TextChannel logChan = guild.getTextChannelById(logChannel);
        if (logChan == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(performedBy.getEffectiveName(), performedBy.getAvatarUrl());
        eb.setTitle(actionType.name());
        eb.addField("Performed By", performedBy.getAsMention(), true);
        eb.addField("Performed On", performedOn.getAsMention(), true);
        eb.addField("Rules Broken", String.join(", ", brokenRuleIds), false);
        if (reason.length() > 0)
            eb.addField("Reason", reason, false);
        logChan.sendMessageEmbeds(eb.build()).queue();
    }
    public static void log(ActionType actionType, Member performedBy, String channelName, String reason) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File actionLog = new File("logs/actions.txt");
        if (!actionLog.exists()) {
            try {
                actionLog.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (actionLog.exists()) {
            try {
                FileWriter writer = new FileWriter(actionLog, true);
                writer.write("[" + getCurrentDatetimeString() + "] " + actionType.name() + ":");
                writer.write("\nPerformed by: " + performedBy.getUser().getName() + "#" +
                        performedBy.getUser().getDiscriminator() + "");
                if (reason.length() > 0)
                    writer.write("\nReason: " + reason + "");
                writer.write("\n----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Logger.log(e);
                e.printStackTrace();
            }
        }
        String logChannel = Main.getInstance().getConfig().getString("Bot.Logger.Channel");
        String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
        Guild guild = Main.getInstance().getJDA().getGuildById(guildId);
        if (guild == null) return;
        TextChannel logChan = guild.getTextChannelById(logChannel);
        if (logChan == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(performedBy.getEffectiveName(), performedBy.getAvatarUrl());
        eb.setTitle(actionType.name());
        eb.addField("Performed By", performedBy.getAsMention(), true);
        eb.addField("TextChannel", channelName, false);
        if (reason.length() > 0)
            eb.addField("Reason", reason, false);
        logChan.sendMessageEmbeds(eb.build()).queue();
    }
    public static void log(ActionType actionType, Member performedBy, TextChannel channel, String reason) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File actionLog = new File("logs/actions.txt");
        if (!actionLog.exists()) {
            try {
                actionLog.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (actionLog.exists()) {
            try {
                FileWriter writer = new FileWriter(actionLog, true);
                writer.write("[" + getCurrentDatetimeString() + "] " + actionType.name() + ":");
                writer.write("\nPerformed by: " + performedBy.getUser().getName() + "#" +
                        performedBy.getUser().getDiscriminator() + "");
                if (reason.length() > 0)
                    writer.write("\nReason: " + reason + "");
                writer.write("\n----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Logger.log(e);
                e.printStackTrace();
            }
        }
        String logChannel = Main.getInstance().getConfig().getString("Bot.Logger.Channel");
        String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
        Guild guild = Main.getInstance().getJDA().getGuildById(guildId);
        if (guild == null) return;
        TextChannel logChan = guild.getTextChannelById(logChannel);
        if (logChan == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(performedBy.getEffectiveName(), performedBy.getAvatarUrl());
        eb.setTitle(actionType.name());
        eb.addField("Performed By", performedBy.getAsMention(), true);
        eb.addField("TextChannel", channel.getAsMention(), false);
        if (reason.length() > 0)
            eb.addField("Reason", reason, false);
        logChan.sendMessageEmbeds(eb.build()).queue();
    }
    public static void log(ActionType actionType, Member performedBy, Member performedOn, TextChannel channel, String reason) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File actionLog = new File("logs/actions.txt");
        if (!actionLog.exists()) {
            try {
                actionLog.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (actionLog.exists()) {
            try {
                FileWriter writer = new FileWriter(actionLog, true);
                writer.write("[" + getCurrentDatetimeString() + "] " + actionType.name() + ":");
                writer.write("\nPerformed by: " + performedBy.getUser().getName() + "#" +
                        performedBy.getUser().getDiscriminator() + "");
                writer.write("\nPerformed on: " + performedOn.getUser().getName() + "#" +
                        performedOn.getUser().getDiscriminator() + "");
                if (reason.length() > 0)
                    writer.write("\nReason: " + reason + "");
                writer.write("\n----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Logger.log(e);
                e.printStackTrace();
            }
        }
        String logChannel = Main.getInstance().getConfig().getString("Bot.Logger.Channel");
        String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
        Guild guild = Main.getInstance().getJDA().getGuildById(guildId);
        if (guild == null) return;
        TextChannel logChan = guild.getTextChannelById(logChannel);
        if (logChan == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(performedBy.getEffectiveName(), performedBy.getAvatarUrl());
        eb.setThumbnail(performedOn.getAvatarUrl());
        eb.setTitle(actionType.name());
        eb.addField("Performed By", performedBy.getAsMention(), true);
        eb.addField("Performed On", performedOn.getAsMention(), true);
        eb.addField("TextChannel", channel.getAsMention(), false);
        if (reason.length() > 0)
            eb.addField("Reason", reason, false);
        logChan.sendMessageEmbeds(eb.build()).queue();
    }
    public static void log(ActionType actionType, Member performedBy, Member performedOn, String reason) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File actionLog = new File("logs/actions.txt");
        if (!actionLog.exists()) {
            try {
                actionLog.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (actionLog.exists()) {
            try {
                FileWriter writer = new FileWriter(actionLog, true);
                writer.write("[" + getCurrentDatetimeString() + "] " + actionType.name() + ":");
                writer.write("\nPerformed by: " + performedBy.getUser().getName() + "#" +
                        performedBy.getUser().getDiscriminator() + "");
                writer.write("\nPerformed on: " + performedOn.getUser().getName() + "#" +
                        performedOn.getUser().getDiscriminator() + "");
                if (reason.length() > 0)
                    writer.write("\nReason: " + reason + "");
                writer.write("\n----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Logger.log(e);
                e.printStackTrace();
            }
        }
        String logChannel = Main.getInstance().getConfig().getString("Bot.Logger.Channel");
        String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
        Guild guild = Main.getInstance().getJDA().getGuildById(guildId);
        if (guild == null) return;
        TextChannel logChan = guild.getTextChannelById(logChannel);
        if (logChan == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(performedBy.getEffectiveName(), performedBy.getAvatarUrl());
        eb.setThumbnail(performedOn.getAvatarUrl());
        eb.setTitle(actionType.name());
        eb.addField("Performed By", performedBy.getAsMention(), true);
        eb.addField("Performed On", performedOn.getAsMention(), true);
        if (reason.length() > 0)
            eb.addField("Reason", reason, false);
        logChan.sendMessageEmbeds(eb.build()).queue();
    }
    public static void log(ActionType actionType, Member performedBy, User performedOn, String reason) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File actionLog = new File("logs/actions.txt");
        if (!actionLog.exists()) {
            try {
                actionLog.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (actionLog.exists()) {
            try {
                FileWriter writer = new FileWriter(actionLog, true);
                writer.write("[" + getCurrentDatetimeString() + "] " + actionType.name() + ":");
                writer.write("\nPerformed by: " + performedBy.getUser().getName() + "#" +
                        performedBy.getUser().getDiscriminator() + "");
                writer.write("\nPerformed on: " + performedOn.getName() + "#" +
                        performedOn.getDiscriminator() + "");
                if (reason.length() > 0)
                    writer.write("\nReason: " + reason + "");
                writer.write("\n----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Logger.log(e);
                e.printStackTrace();
            }
        }
        String logChannel = Main.getInstance().getConfig().getString("Bot.Logger.Channel");
        String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
        Guild guild = Main.getInstance().getJDA().getGuildById(guildId);
        if (guild == null) return;
        TextChannel logChan = guild.getTextChannelById(logChannel);
        if (logChan == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(performedBy.getEffectiveName(), performedBy.getAvatarUrl());
        eb.setThumbnail(performedOn.getAvatarUrl());
        eb.setTitle(actionType.name());
        eb.addField("Performed By", performedBy.getAsMention(), true);
        eb.addField("Performed On", performedOn.getAsMention(), true);
        if (reason.length() > 0)
            eb.addField("Reason", reason, false);
        logChan.sendMessageEmbeds(eb.build()).queue();
    }
    public static void log(ActionType actionType, Member performedBy, String reason) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File actionLog = new File("logs/actions.txt");
        if (!actionLog.exists()) {
            try {
                actionLog.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (actionLog.exists()) {
            try {
                FileWriter writer = new FileWriter(actionLog, true);
                writer.write("[" + getCurrentDatetimeString() + "] " + actionType.name() + ":");
                writer.write("\nPerformed by: " + performedBy.getUser().getName() + "#" +
                        performedBy.getUser().getDiscriminator() + "\n");
                if (reason.length() > 0)
                    writer.write("\nReason: " + reason);
                writer.write("\n----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Logger.log(e);
                e.printStackTrace();
            }
        }
        String logChannel = Main.getInstance().getConfig().getString("Bot.Logger.Channel");
        String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
        Guild guild = Main.getInstance().getJDA().getGuildById(guildId);
        if (guild == null) return;
        TextChannel logChan = guild.getTextChannelById(logChannel);
        if (logChan == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(performedBy.getEffectiveName(), performedBy.getAvatarUrl());
        eb.setTitle(actionType.name());
        eb.addField("Performed By", performedBy.getAsMention(), true);
        if (reason.length() > 0)
            eb.addField("Reason", reason, true);
        logChan.sendMessageEmbeds(eb.build()).queue();
    }
    public static void log(Exception ex) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File errors = new File("logs/error.txt");
        if (!errors.exists()) {
            try {
                errors.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (errors.exists()) {
            try {
                FileWriter writer = new FileWriter(errors, true);
                writer.write("[" + getCurrentDatetimeString() + "] Error Encountered:\n");
                writer.write(ex.getMessage());
                writer.write("\n----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
