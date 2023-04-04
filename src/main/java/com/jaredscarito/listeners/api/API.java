package com.jaredscarito.listeners.api;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.mysql.cj.log.Log;
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

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class API {
    private static final API api = new API();
    public static API getInstance() {
        return api;
    }

    public String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }

    public boolean addPoints(Member mem, int points) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO `points` (`discord_id`, `lastKnownName`, " +
                    "`lastKnownAvatar`, `points`) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                    "`lastKnownName` = ?, `lastKnownAvatar` = ?, `points` = `points` + ?");
            long discordId = mem.getIdLong();
            String name = mem.getUser().getName() + "#" + mem.getUser().getDiscriminator();
            String avatarUrl = mem.getUser().getAvatarUrl();
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
        try {
            PreparedStatement stmt = conn.prepareStatement("UPDATE `points` SET " +
                    "`lastKnownName` = ?, `lastKnownAvatar` = ?, `points` = `points` - ? WHERE `discord_id` = ?");
            long discordId = mem.getIdLong();
            String name = mem.getUser().getName() + "#" + mem.getUser().getDiscriminator();
            String avatarUrl = mem.getUser().getAvatarUrl();
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
    public int getPoints(Member mem) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT `points` FROM `points` WHERE `discord_id` = ?");
            stmt.setLong(1, mem.getIdLong());
            stmt.execute();
            ResultSet res = stmt.getResultSet();
            res.next();
            return res.getInt("points");
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return 0;
    }
    public boolean addRankExp(Member mem, int exp) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO `ranking` (`discord_id`, `lastKnownName`, " +
                    "`lastKnownAvatar`, `exp`) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                    "`lastKnownName` = ?, `lastKnownAvatar` = ?, `exp` = `exp` + ?");
            long discordId = mem.getIdLong();
            String name = mem.getUser().getName() + "#" + mem.getUser().getDiscriminator();
            String avatarUrl = mem.getUser().getAvatarUrl();
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
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT `exp` FROM `ranking` WHERE `discord_id` = ?");
            stmt.setLong(1, mem.getIdLong());
            stmt.execute();
            ResultSet res = stmt.getResultSet();
            if (!res.next()) return 1;
            int exp = res.getInt("exp");
            int rank = 1;
            for (int ranking = 1; ranking < 9999; ranking++) {
                if (ranking * ranking * ranking > exp)
                    break;
                rank++;
            }
            return rank;
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return 1;
    }
    public void gamble(SlashCommandInteractionEvent evt, Member mem, int points) {}
    public void lockdownEnable(TextChannel chan) {}
    public void lockdownDisable(TextChannel chan) {}
    public boolean addSticky(TextChannel chan, String msg) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO `stickies` (`channel_id`, `message`) " +
                    "VALUES (?, ?) ON DUPLICATE KEY UPDATE `message` = ?");
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
        try {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM `stickies` WHERE `channel_id` = ?");
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
        StringSelectMenu.Builder selectMenuBuilder = StringSelectMenu.create("ticket");
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
        eb.setTitle(getConfigValue("Bot.Tickets.Ticket_Close_Title").replace("{NAME}", ticketCreator.getEffectiveName()));
        eb.setDescription(getConfigValue("Bot.Ticket.Ticket_Close_Body").replace("{NAME}", ticketCreator.getEffectiveName()));
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

    public void saveTicketToFile(TextChannel chan, Member closer, String closeReason, Consumer<Boolean> result) {
        File ticketSaveDir = new File("ticket_logs");
        if (!ticketSaveDir.exists()) ticketSaveDir.mkdir();
        File logFile = new File("ticket_logs/" + chan.getId() + "-log.html");
        try {
            logFile.createNewFile();
            FileWriter writer = new FileWriter(logFile);
            writer.write("<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <title>#" + chan.getIdLong() + " " + chan.getName() + "</title>\n" +
                    "    <link rel=\"stylesheet\" href=\"https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css\" integrity=\"sha384-JcKb8q3iqJ61gNV9KGb8thSsNjpSL0n8PARn9HuZOnIxN0hoP+VmmDGMN5t9UJ0Z\" crossorigin=\"anonymous\">\n" +
                    "    <style>\n" +
                    "        body {\n" +
                    "            background-color: #36393F;\n" +
                    "        }\n" +
                    "        .container-fluid {\n" +
                    "            background-color: #36393F;\n" +
                    "            color: white;\n" +
                    "        }\n" +
                    "        #header {\n" +
                    "            background-color: #303339;\n" +
                    "        }\n" +
                    "        #hashtag {\n" +
                    "            color: #6d7279;\n" +
                    "            font-weight: bold;\n" +
                    "            font-size: 32px;\n" +
                    "            vertical-align: middle;\n" +
                    "            margin-right: 30px;\n" +
                    "        }\n" +
                    "        span#channelName {\n" +
                    "            font-weight: bold;\n" +
                    "            font-size: 22px;\n" +
                    "            vertical-align: middle;\n" +
                    "        }\n" +
                    "        .rounded-circle {\n" +
                    "            width: 50px;\n" +
                    "            height: 50px;\n" +
                    "        }\n" +
                    "        .profilename {\n" +
                    "            margin-left: 15px;\n" +
                    "            margin-right: 15px;\n" +
                    "            vertical-align: top;\n" +
                    "        }\n" +
                    "        .message-content {\n" +
                    "            margin-left: 15px;\n" +
                    "        }\n" +
                    "        .message-block:hover {\n" +
                    "            background-color: #2e3239;\n" +
                    "        }\n" +
                    "        .timestamp {\n" +
                    "            color: #707070;\n" +
                    "            font-size: 13px;\n" +
                    "            vertical-align: top;\n" +
                    "        }\n" +
                    "        .message-block {\n" +
                    "            padding: 20px;\n" +
                    "        }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<div class=\"container-fluid\">\n" +
                    "    <div class=\"row sticky-top\" id=\"header\">\n" +
                    "        <div class=\"col-lg-12\">\n" +
                    "            <span id=\"hashtag\">#</span><span id=\"channelName\">" + chan.getName() + "</span>\n" +
                    "        </div>\n" +
                    "    </div>");
            chan.getIterableHistory().takeAsync(5000).thenApply(list -> {
                Stream<Message> messages = list.stream();
                for (Message msg : messages.toArray(Message[]::new)) {
                    String author = msg.getAuthor().getAsTag();
                    String message = msg.getContentDisplay();
                    String authorImg = msg.getAuthor().getAvatarUrl();
                    List<Message.Attachment> attachments = msg.getAttachments();
                    List<File> files = new ArrayList<>();
                    for (Message.Attachment attach : attachments) {
                        CompletableFuture<File> file = attach.downloadToFile();
                        File attachment = new File("ticket_log/attachments/" + chan.getId() + "-" + attach.getFileName()
                                + "." + attach.getFileExtension());
                        file.complete(attachment);
                        if (attachment.exists())
                            files.add(attachment);
                    }
                    // TODO Add files to message block
                    String messageBlock = "<div class=\"message-block\">\n" +
                            "        <div class=\"row\">\n" +
                            "            <div class=\"col-auto\">\n" +
                            "                <img src=\"" + authorImg + "\" alt=\"" + author + "\" class=\"img-fluid rounded-circle\" />\n" +
                            "            </div>\n" +
                            "            <div class=\"col-auto\">\n" +
                            "                <span class=\"profilename\">" + author + "</span>\n" +
                            "                <span class=\"timestamp\">" + msg.getTimeCreated().toLocalDateTime()
                            .minusHours(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "</span>\n" +
                            "            </div>\n" +
                            "        </div>";
                    messageBlock += "<div class=\"row\">\n" +
                            "            <div class=\"col-auto\">\n" +
                            "                <img src=\"" + authorImg + "\" alt=\"" + author + "\" class=\"img-fluid rounded-circle\" style=\"visibility: hidden\" />\n" +
                            "            </div>\n" +
                            "            <div class=\"col-auto message-content\">\n" +
                            "                <!-- Message Content -->\n" +
                            message +
                            "            </div>\n" +
                            "        </div></div>";
                    try {
                        writer.write(messageBlock);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }).thenApply(bool -> {
                if (bool) {
                    // It completed, send to Discord
                    try {
                        writer.write("</div></body></html>");
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    TextChannel LOG_CHANNEL = Main.getInstance().getJDA().getTextChannelById(Main.getInstance().getConfig().getString("Bot.Tickets.Log_Channel"));
                    if (LOG_CHANNEL != null) {
                        LOG_CHANNEL.sendMessage("A ticket has been logged. **TITLE:** `" + chan.getName() + "`")
                                .addFiles(FileUpload.fromData(logFile)).queue((msg) -> {
                                    logFile.delete();
                                });
                    }
                    result.accept(true);
                    return true;
                }
                result.accept(false);
                return false;
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
