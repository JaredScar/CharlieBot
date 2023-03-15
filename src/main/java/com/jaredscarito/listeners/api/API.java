package com.jaredscarito.listeners.api;

import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

public class API {
    public static API api = new API();
    public static API getInstance() {
        return api;
    }

    public String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }

    public MessageCreateAction createMainTicketMessage(TextChannel chan) {
        EmbedBuilder eb = new EmbedBuilder();
        StringSelectMenu.Builder selectMenuBuilder = StringSelectMenu.create("ticket");
        selectMenuBuilder.setPlaceholder(getConfigValue("Bot.Tickets.Title"));
        List<String> options = Main.getInstance().getConfig().getKeys("Bot.Tickets.Create_Ticket_Options");
        for (String opt : options) {
            String optLabel = getConfigValue("Bot.Tickets.Create_Ticket_Options." + opt + ".Label");
            boolean optEnabled = Main.getInstance().getConfig().getBoolean("Bot.Tickets.Create_Ticket_Options." + opt + ".Enabled");
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
    public MessageEmbed createTicketCloseMessage() {
        return null;
    }

    public boolean saveTicketToFile(TextChannel chan, Member closer, String closeReason) {
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
                    /** /
                    if (LOG_CHANNEL != null) {
                        LOG_CHANNEL.sendMessage("A ticket has been logged. **TITLE:** `" + chan.getName() + "`")
                                .addFile(logFile).queue(message -> {
                                    // It completed, we want to delete the file now
                                    logFile.delete();
                                });
                    }
                     /**/
                    return true;
                }
                return false;
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }
}
