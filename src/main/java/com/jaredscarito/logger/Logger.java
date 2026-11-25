package com.jaredscarito.logger;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
    /**
     * The channel action logs are posted to, or null when channel logging is switched off
     * (Bot.Logger.Enabled: false) or the guild/channel is not configured. The file logs
     * (logs/actions.txt and logs/error.txt) are written regardless of this setting.
     */
    private static TextChannel getLogChannel() {
        return findLogChannel(true);
    }

    /**
     * @param respectEnabled when true, Bot.Logger.Enabled: false hides the channel.
     *        Reports ignore that switch, because a member is waiting on staff to see it.
     */
    private static TextChannel findLogChannel(boolean respectEnabled) {
        try {
            // a missing key keeps the old behaviour (logging on)
            if (respectEnabled && !Main.getInstance().getConfig().getBoolean("Bot.Logger.Enabled", true)) return null;
            String logChannel = Main.getInstance().getConfig().getString("Bot.Logger.Channel");
            String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
            if (logChannel == null || logChannel.trim().isEmpty() || guildId == null || guildId.trim().isEmpty()) return null;
            Guild guild = Main.getInstance().getJDA().getGuildById(guildId);
            if (guild == null) return null;
            return guild.getTextChannelById(logChannel);
        } catch (Exception ex) {
            // a bad id in the config must never break the action that is being logged
            log(ex);
            return null;
        }
    }

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
        TextChannel logChan = getLogChannel();
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
        TextChannel logChan = getLogChannel();
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
        TextChannel logChan = getLogChannel();
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
        TextChannel logChan = getLogChannel();
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
        TextChannel logChan = getLogChannel();
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
        TextChannel logChan = getLogChannel();
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
        TextChannel logChan = getLogChannel();
        if (logChan == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(performedBy.getEffectiveName(), performedBy.getAvatarUrl());
        eb.setTitle(actionType.name());
        eb.addField("Performed By", performedBy.getAsMention(), true);
        if (reason.length() > 0)
            eb.addField("Reason", reason, true);
        logChan.sendMessageEmbeds(eb.build()).queue();
    }
    public static void log(Throwable ex) {
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
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                FileWriter writer = new FileWriter(errors, true);
                writer.write("[" + getCurrentDatetimeString() + "] "
                        + ex.getClass().getName() + ": " + ex.getMessage() + "\n");
                writer.write(sw.toString());
                writer.write("----------------------\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes a member report to the log file and the mod channel.
     * The channel post is sent even when Bot.Logger.Enabled is false.
     *
     * @return true when a mod channel is configured
     */
    public static boolean logReport(Member reporter, User target, String details) {
        File logsDir = new File("logs");
        if (!logsDir.exists()) logsDir.mkdirs();
        File actionLog = new File("logs/actions.txt");
        try {
            if (!actionLog.exists()) actionLog.createNewFile();
            FileWriter writer = new FileWriter(actionLog, true);
            writer.write("[" + getCurrentDatetimeString() + "] REPORT:\n");
            writer.write("Reported by: " + reporter.getUser().getName() + "\n");
            if (target != null) writer.write("Reported user: " + target.getName() + " (" + target.getId() + ")\n");
            if (details != null && !details.isEmpty()) writer.write(details + "\n");
            writer.write("----------------------\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            log(e);
        }
        TextChannel logChan = findLogChannel(false);
        if (logChan == null) return false;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.RED);
        eb.setTitle("Report");
        eb.setAuthor(reporter.getEffectiveName(), reporter.getAvatarUrl());
        eb.addField("Reported by", reporter.getAsMention(), true);
        if (target != null) {
            eb.setThumbnail(target.getAvatarUrl());
            eb.addField("Reported user", target.getAsMention(), true);
        }
        if (details != null && !details.isEmpty()) {
            eb.addField("Details", details.length() <= 1024 ? details : details.substring(0, 1021) + "...", false);
        }
        logChan.sendMessageEmbeds(eb.build()).queue(null, Logger::log);
        return true;
    }
}
