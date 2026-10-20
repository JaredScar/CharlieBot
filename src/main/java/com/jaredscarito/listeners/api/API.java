package com.jaredscarito.listeners.api;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.PunishmentData;
import com.jaredscarito.models.PunishmentType;
import com.timvisee.yamlwrapper.ConfigurationSection;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

public class API {
    private static final API api = new API();
    public static API getInstance() {
        return api;
    }

    public String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }

    public PunishmentType getPunishmentTypeByString(String s) {
        for (PunishmentType pt : PunishmentType.values()) {
            if (pt.name().equalsIgnoreCase(s))
                return pt;
        }
        return null;
    }

    public void notifyPunishment(Member punishedMember, Member punisher, PunishmentType punishmentType, String punishmentLength, List<String> ruleIds_broken, String reason) {
        String channelId = Main.getInstance().getConfig().getString("Bot.Punishment_Announce_Channel");
        if (channelId == null || channelId.trim().isEmpty()) {
            // No punishment channel configured, skip notification
            return;
        }
        TextChannel punishmentChannel = punisher.getGuild().getTextChannelById(channelId);
        if (punishmentChannel == null) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(punishmentType.name());
        eb.addField("Member Punished:", punishedMember.getAsMention(), true);
        eb.addField("Punished By:", punisher.getAsMention(), true);
        eb.addField("Rules Broken:", String.join(", ", ruleIds_broken), false);
        eb.addField("Reason:", reason, false);
        eb.setAuthor(punisher.getUser().getName() + "#" + punisher.getUser().getDiscriminator(), punisher.getUser().getAvatarUrl());
        eb.setThumbnail(punishedMember.getAvatarUrl());
        punishmentChannel.sendMessageEmbeds(eb.build()).queue();
    }

    public void logPunishment(Member punishedMember, Member punisher, PunishmentType punishmentType, String punishmentLength, List<String> ruleIds_broken, String reason) {
        if (ruleIds_broken == null) ruleIds_broken = new ArrayList<>();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return;
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO `punishments` (`discord_id`, `datetime`, `ruleIds_broken`, `lastKnownName`, `lastKnownAvatar`, `punishment_type`, `punishment_length`, `reason`, `punished_by`, `punished_by_lastKnownName`, `punished_by_lastKnownAvatar`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, punishedMember.getIdLong());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime now = LocalDateTime.now(ZoneId.of("America/New_York"));
            String datetimeStr = now.format(formatter);
            stmt.setString(2, datetimeStr);
            stmt.setString(3, String.join(", ", ruleIds_broken));
            stmt.setString(4, punishedMember.getUser().getName() + "#" + punishedMember.getUser().getDiscriminator());
            stmt.setString(5, punishedMember.getUser().getEffectiveAvatarUrl());
            stmt.setString(6, punishmentType.name());
            stmt.setString(7, punishmentLength);
            stmt.setString(8, reason);
            stmt.setLong(9, punisher.getIdLong());
            stmt.setString(10, punisher.getUser().getName() + "#" + punisher.getUser().getDiscriminator());
            stmt.setString(11, punisher.getUser().getEffectiveAvatarUrl());
            stmt.execute();
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    public List<PunishmentData> getAllPunishmentData(long discordId) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return new ArrayList<>();
        List<PunishmentData> punishments = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM `punishments` WHERE `discord_id` = ? ORDER BY `pid` DESC")) {
            stmt.setLong(1, discordId);
            try (ResultSet res = stmt.executeQuery()) {
                while (res.next()) {
                    int pid = res.getInt("pid");
                    Date datetime = res.getTimestamp("datetime");
                    String ruleIds = res.getString("ruleIds_broken");
                    String lastKnownName = res.getString("lastKnownName");
                    String lastKnownAvatar = res.getString("lastKnownAvatar");
                    String punishmentTypeStr = res.getString("punishment_type");
                    PunishmentType punishmentType = PunishmentType.valueOf(punishmentTypeStr);
                    String punishmentLength = res.getString("punishment_length");
                    String reason = res.getString("reason");
                    long punishedBy = res.getLong("punished_by");
                    String punishedByLastKnownName = res.getString("punished_by_lastKnownName");
                    String punishedByLastKnownAvatar = res.getString("punished_by_lastKnownAvatar");
                    boolean deleted = res.getBoolean("deleted");
                    PunishmentData pd = new PunishmentData(
                            pid,
                            discordId,
                            datetime,
                            ruleIds,
                            lastKnownName,
                            lastKnownAvatar,
                            punishmentType,
                            punishmentLength,
                            reason,
                            punishedBy,
                            punishedByLastKnownName,
                            punishedByLastKnownAvatar,
                            deleted
                    );
                    punishments.add(pd);
                }
            }
        } catch (SQLException e) {
            Logger.log(e);
        }
        return punishments;
    }

    public TreeMap<String, String> getRules() {
        TreeMap<String, String> rulesSorted = new TreeMap<>((s1, s2) -> {
            String firstRuleIdentifierArg0 = s1.split("\\.")[0];
            String firstRuleIdentifierArg1 = s1.split("\\.")[1];
            String secondRuleIdentifierArg0 = s2.split("\\.")[0];
            String secondRuleIdentifierArg1 = s2.split("\\.")[1];
            int firstRuleArg0 = Integer.parseInt(firstRuleIdentifierArg0);
            int firstRuleArg1 = Integer.parseInt(firstRuleIdentifierArg1);
            int secondRuleArg0 = Integer.parseInt(secondRuleIdentifierArg0);
            int secondRuleArg1 = Integer.parseInt(secondRuleIdentifierArg1);
            if (firstRuleArg0 > secondRuleArg0) return 1;
            if (secondRuleArg0 > firstRuleArg0) return -1;
            return Integer.compare(firstRuleArg1, secondRuleArg1);
        });
        
        ConfigurationSection sectionsConfig = Main.getInstance().getConfig().getConfigurationSection("Rules.Sections");
        if (sectionsConfig == null) {
            // No rules section exists, return empty map
            return rulesSorted;
        }
        
        List<String> titles = sectionsConfig.getKeys();
        if (titles == null || titles.isEmpty()) {
            return rulesSorted;
        }
        
        HashMap<String, String> ruleList = new HashMap<>();
        String ruleIdentifier = "1.1";
        for (String title : titles) {
            ConfigurationSection sectionConfig = Main.getInstance().getConfig().getConfigurationSection("Rules.Sections." + title);
            if (sectionConfig == null) continue;
            
            List<String> sectRuleIds = sectionConfig.getKeys();
            if (sectRuleIds == null) continue;
            
            for (String sectRuleId : sectRuleIds) {
                String ruleIdentifierArg0 = ruleIdentifier.split("\\.")[0];
                String ruleIdentifierArg1 = ruleIdentifier.split("\\.")[1];
                String sectRule = Main.getInstance().getConfig().getString("Rules.Sections." + title + "." + sectRuleId);
                if (sectRule != null) {
                    ruleList.put(ruleIdentifier, sectRule);
                    ruleIdentifier = ruleIdentifierArg0 + "." + (Integer.parseInt(ruleIdentifierArg1) + 1);
                }
            }
            String ruleIdentifierArg0 = ruleIdentifier.split("\\.")[0];
            ruleIdentifier = (Integer.parseInt(ruleIdentifierArg0) + 1) + ".1";
        }
        
        rulesSorted.putAll(ruleList);
        return rulesSorted;
    }

    public String getStickyMessage(TextChannel chan) {
        long id = chan.getIdLong();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return "";
        try (PreparedStatement stmt = conn.prepareStatement("SELECT `message` FROM `stickies` WHERE `channel_id` = ?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return rs.getString("message");
            }
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return "";
    }

    public HashMap<Long, String> getStickyMessages() {
        HashMap<Long, String> stickies = new HashMap<>();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return stickies;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM `stickies`")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long channelId = rs.getLong("channel_id");
                    String msg = rs.getString("message");
                    stickies.put(channelId, msg);
                }
            }
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return stickies;
    }

    public boolean addPoints(Member mem, int points) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO `points` (`discord_id`, `lastKnownName`, " +
                "`lastKnownAvatar`, `points`) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "`lastKnownName` = ?, `lastKnownAvatar` = ?, `points` = `points` + ?")) {
            long discordId = mem.getIdLong();
            String name = mem.getUser().getName() + "#" + mem.getUser().getDiscriminator();
            String avatarUrl = mem.getUser().getEffectiveAvatarUrl();
            stmt.setLong(1, discordId);
            stmt.setString(2, name);
            stmt.setString(3, avatarUrl);
            stmt.setInt(4, points);
            stmt.setString(5, name);
            stmt.setString(6, avatarUrl);
            stmt.setInt(7, points);
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logger.log(e);
            e.printStackTrace();
        }
        return false;
    }
    public boolean removePoints(Member mem, int points) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE `points` SET " +
                "`lastKnownName` = ?, `lastKnownAvatar` = ?, `points` = `points` - ? WHERE `discord_id` = ?")) {
            long discordId = mem.getIdLong();
            String name = mem.getUser().getName() + "#" + mem.getUser().getDiscriminator();
            String avatarUrl = mem.getUser().getEffectiveAvatarUrl();
            stmt.setString(1, name);
            stmt.setString(2, avatarUrl);
            stmt.setInt(3, points);
            stmt.setLong(4, discordId);
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logger.log(e);
            e.printStackTrace();
        }
        return false;
    }
    /**
     * Atomically deducts points only if the member can afford it.
     * Prevents negative balances when several bets/purchases race each other.
     */
    public boolean tryRemovePoints(Member mem, int points) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE `points` SET `points` = `points` - ? " +
                "WHERE `discord_id` = ? AND `points` >= ?")) {
            stmt.setInt(1, points);
            stmt.setLong(2, mem.getIdLong());
            stmt.setInt(3, points);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logger.log(e);
        }
        return false;
    }
    public int getPoints(Member mem) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return 0;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT `points` FROM `points` WHERE `discord_id` = ?")) {
            stmt.setLong(1, mem.getIdLong());
            try (ResultSet res = stmt.executeQuery()) {
                if (res.next()) {
                    return res.getInt("points");
                }
            }
            // User doesn't have a points entry yet, return 0
            return 0;
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return 0;
    }
    public boolean addRankExp(Member mem, int exp) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO `ranking` (`discord_id`, `lastKnownName`, " +
                "`lastKnownAvatar`, `exp`) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "`lastKnownName` = ?, `lastKnownAvatar` = ?, `exp` = `exp` + ?")) {
            long discordId = mem.getIdLong();
            String name = mem.getUser().getName() + "#" + mem.getUser().getDiscriminator();
            String avatarUrl = mem.getUser().getEffectiveAvatarUrl();
            stmt.setLong(1, discordId);
            stmt.setString(2, name);
            stmt.setString(3, avatarUrl);
            stmt.setInt(4, exp);
            stmt.setString(5, name);
            stmt.setString(6, avatarUrl);
            stmt.setInt(7, exp);
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logger.log(e);
            e.printStackTrace();
        }
        return false;
    }
    public int getRank(Member mem) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return 1;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT `exp` FROM `ranking` WHERE `discord_id` = ?")) {
            stmt.setLong(1, mem.getIdLong());
            try (ResultSet res = stmt.executeQuery()) {
                if (!res.next()) return 1;
                int exp = res.getInt("exp");
                int rank = 1;
                for (int ranking = 1; ranking < 9999; ranking++) {
                    if (ranking * ranking * ranking > exp)
                        break;
                    rank++;
                }
                return rank;
            }
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return 1;
    }
    public boolean addSticky(TextChannel chan, String msg) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO `stickies` (`channel_id`, `message`) " +
                "VALUES (?, ?) ON DUPLICATE KEY UPDATE `message` = ?")) {
            stmt.setLong(1, chan.getIdLong());
            stmt.setString(2, msg);
            stmt.setString(3, msg);
            stmt.execute();
            return true;
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return false;
    }
    public boolean removeSticky(TextChannel chan) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM `stickies` WHERE `channel_id` = ?")) {
            stmt.setLong(1, chan.getIdLong());
            stmt.execute();
            return true;
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return false;
    }

    public EmbedBuilder sendSuccessMessage(Member mem, String title, String desc) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(desc);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        eb.setColor(Color.GREEN);
        return eb;
    }
    public MessageCreateAction sendSuccessMessage(TextChannel chan, Member mem, String title, String desc) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(desc);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        eb.setColor(Color.GREEN);
        return chan.sendMessageEmbeds(eb.build());
    }
    public MessageCreateAction sendErrorMessage(TextChannel chan, Member mem, String title, String desc) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(desc);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        eb.setColor(Color.RED);
        return chan.sendMessageEmbeds(eb.build());
    }
    public void sendErrorMessage(StringSelectInteractionEvent evt, Member mem, String title, String desc) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(desc);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        evt.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }
    public void sendErrorMessage(SlashCommandInteractionEvent evt, Member mem, String title, String desc) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(desc);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        evt.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    public MessageEmbed getEmbedMessage(Member mem, String title, String desc) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(desc);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        return eb.build();
    }

    public MessageCreateAction createMainTicketMessage(TextChannel chan) {
        EmbedBuilder eb = new EmbedBuilder();
        StringSelectMenu.Builder selectMenuBuilder = StringSelectMenu.create("ticketSelectMenu");
        selectMenuBuilder.setPlaceholder(getConfigValue("Bot.Tickets.Title"));
        List<String> options = Main.getInstance().getConfig().getKeys("Bot.Tickets.Ticket_Options");
        for (String opt : options) {
            String optLabel = getConfigValue("Bot.Tickets.Ticket_Options." + opt + ".Label");
            boolean optEnabled = Main.getInstance().getConfig().getBoolean("Bot.Tickets.Ticket_Options." + opt + ".Enabled");
            if (optEnabled)
                selectMenuBuilder.addOption(optLabel, opt);
        }
        StringSelectMenu selectMenu = selectMenuBuilder.build();
        eb.setTitle(getConfigValue("Bot.Tickets.Title"));
        eb.setDescription(getConfigValue("Bot.Tickets.Body"));
        MessageEmbed embed = eb.build();
        MessageCreateAction msg = chan.sendMessageEmbeds(embed).addActionRow(selectMenu);
        return msg;
    }
    public MessageCreateAction createTicketCloseMessage(TextChannel chan, Member ticketCreator) {
        EmbedBuilder eb = new EmbedBuilder();
        String closeTitle = getConfigValue("Bot.Tickets.Ticket_Close_Title");
        String closeBody = getConfigValue("Bot.Tickets.Ticket_Close_Body");
        eb.setTitle((closeTitle == null ? "Welcome to your ticket, {NAME}" : closeTitle).replace("{NAME}", ticketCreator.getEffectiveName()));
        eb.setDescription((closeBody == null ? "A staff member will be with you shortly." : closeBody).replace("{NAME}", ticketCreator.getEffectiveName()));
        Button closeAndSaveButton = Button.danger("closeAndSaveTicket", getConfigValue("Bot.Tickets.Close_Ticket_Button"));
        Button lockButton = Button.secondary("lockTicket", getConfigValue("Bot.Tickets.Lock_Ticket_Button"));
        Button unlockButton = Button.secondary("unlockTicket", getConfigValue("Bot.Tickets.Unlock_Ticket_Button")).asDisabled();
        MessageCreateAction msg = chan.sendMessageEmbeds(eb.build()).addActionRow(unlockButton, lockButton, closeAndSaveButton);
        return msg;
    }

    public void askConfirmDenyMessage(StringSelectInteractionEvent evt, Member mem, String msgTitle, String msgBody) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(msgTitle);
        eb.setDescription(msgBody);
        eb.setAuthor(mem.getEffectiveName());
        List<SelectOption> selectedOpts = evt.getSelectedOptions();
        String optVal = null;
        for (SelectOption opt : selectedOpts) {
            // Check if value exists
            optVal = opt.getValue();
        }
        Button confirmBtn = Button.success("confirm|" + optVal, getConfigValue("Bot.Buttons.Confirm_Button"));
        Button denyBtn = Button.danger("deny|" + optVal, getConfigValue("Bot.Buttons.Deny_Button"));
        evt.replyEmbeds(eb.build()).addActionRow(confirmBtn, denyBtn).setEphemeral(true).queue();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final String TICKET_LOG_STYLE = """
            body { background-color: #36393F; }
            .container-fluid { background-color: #36393F; color: white; }
            #header { background-color: #303339; }
            #hashtag { color: #6d7279; font-weight: bold; font-size: 32px; vertical-align: middle; margin-right: 30px; }
            span#channelName { font-weight: bold; font-size: 22px; vertical-align: middle; }
            .rounded-circle { width: 50px; height: 50px; }
            .profilename { margin-left: 15px; margin-right: 15px; vertical-align: top; }
            .message-content { margin-left: 15px; }
            .message-block:hover { background-color: #2e3239; }
            .timestamp { color: #707070; font-size: 13px; vertical-align: top; }
            .message-block { padding: 20px; }
            a { color: #00b0f4; }
            """;

    /**
     * Saves a transcript of the channel to ticket_logs/ and uploads it to the configured log channel.
     * The history is fetched asynchronously, so the file is only written once every message has arrived.
     * All user-supplied text is HTML-escaped so a transcript can never run script when opened.
     */
    public void saveTicketToFile(TextChannel chan, Member closer, String closeReason, Consumer<Boolean> result) {
        File ticketSaveDir = new File("ticket_logs");
        if (!ticketSaveDir.exists()) ticketSaveDir.mkdirs();
        File logFile = new File(ticketSaveDir, chan.getId() + "-log.html");
        String channelName = chan.getName();
        long channelId = chan.getIdLong();
        ZoneId zone = ZoneId.of("America/New_York");
        DateTimeFormatter stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        chan.getIterableHistory().takeAsync(5000).thenAccept(list -> {
            try {
                List<Message> messages = new ArrayList<>(list);
                Collections.reverse(messages); // history comes back newest-first

                StringBuilder html = new StringBuilder();
                html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n")
                        .append("<title>#").append(channelId).append(' ').append(escapeHtml(channelName)).append("</title>\n")
                        .append("<link rel=\"stylesheet\" href=\"https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css\" ")
                        .append("integrity=\"sha384-JcKb8q3iqJ61gNV9KGb8thSsNjpSL0n8PARn9HuZOnIxN0hoP+VmmDGMN5t9UJ0Z\" crossorigin=\"anonymous\">\n")
                        .append("<style>\n").append(TICKET_LOG_STYLE).append("</style>\n</head>\n<body>\n")
                        .append("<div class=\"container-fluid\">\n<div class=\"row sticky-top\" id=\"header\">\n<div class=\"col-lg-12\">\n")
                        .append("<span id=\"hashtag\">#</span><span id=\"channelName\">").append(escapeHtml(channelName)).append("</span>\n")
                        .append("</div>\n</div>\n");

                for (Message msg : messages) {
                    String author = escapeHtml(msg.getAuthor().getName());
                    String authorImg = escapeHtml(msg.getAuthor().getEffectiveAvatarUrl());
                    String time = msg.getTimeCreated().atZoneSameInstant(zone).format(stamp);
                    String content = escapeHtml(msg.getContentDisplay()).replace("\n", "<br>\n");
                    html.append("<div class=\"message-block\">\n<div class=\"row\">\n<div class=\"col-auto\">")
                            .append("<img src=\"").append(authorImg).append("\" alt=\"").append(author).append("\" class=\"img-fluid rounded-circle\" /></div>\n")
                            .append("<div class=\"col-auto\"><span class=\"profilename\">").append(author).append("</span>")
                            .append("<span class=\"timestamp\">").append(time).append("</span></div>\n</div>\n")
                            .append("<div class=\"row\"><div class=\"col-auto\">")
                            .append("<img src=\"").append(authorImg).append("\" class=\"img-fluid rounded-circle\" style=\"visibility: hidden\" alt=\"\" /></div>\n")
                            .append("<div class=\"col-auto message-content\">").append(content);
                    for (Message.Attachment attachment : msg.getAttachments()) {
                        html.append("<br><a href=\"").append(escapeHtml(attachment.getUrl())).append("\">")
                                .append(escapeHtml(attachment.getFileName())).append("</a>");
                    }
                    html.append("</div>\n</div>\n</div>\n");
                }
                html.append("</div>\n</body>\n</html>\n");

                try (Writer writer = new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8)) {
                    writer.write(html.toString());
                }

                String logChannelId = Main.getInstance().getConfig().getString("Bot.Tickets.Log_Channel");
                if (logChannelId == null || logChannelId.isEmpty()) {
                    Logger.log(new IllegalStateException("Bot.Tickets.Log_Channel is not configured; transcript kept at " + logFile.getPath()));
                    result.accept(true);
                    return;
                }
                TextChannel logChannel = Main.getInstance().getJDA().getTextChannelById(logChannelId);
                if (logChannel == null) {
                    Logger.log(new IllegalStateException("Ticket log channel " + logChannelId + " not found; transcript kept at " + logFile.getPath()));
                    result.accept(true);
                    return;
                }
                String closedBy = closer != null ? closer.getEffectiveName() : "unknown";
                String reason = (closeReason == null || closeReason.isEmpty()) ? "N/A" : closeReason;
                if (reason.length() > 900) reason = reason.substring(0, 900) + "...";
                logChannel.sendMessage("A ticket has been logged. **TITLE:** `" + channelName + "` -- closed by `" + closedBy
                                + "` -- reason: " + reason)
                        .setAllowedMentions(Collections.emptyList())
                        .addFiles(FileUpload.fromData(logFile))
                        .queue(sent -> logFile.delete(), error -> Logger.log(error));
                result.accept(true);
            } catch (Exception ex) {
                Logger.log(ex);
                result.accept(false);
            }
        }).exceptionally(ex -> {
            Logger.log(ex);
            result.accept(false);
            return null;
        });
    }
}
