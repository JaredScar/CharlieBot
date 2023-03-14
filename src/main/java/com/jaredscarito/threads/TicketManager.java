package com.jaredscarito.threads;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

public class TicketManager extends ListenerAdapter {
    private long TICKET_CHANNEL_ID = -1;
    private long TICKET_CATEGORY_ID = -1;

    private long TICKET_CREATION_MESSAGE_ID = -1;

    public TicketManager() {
        JDA jda = Main.getInstance().getJDA();
        try {
            Guild guild = jda.getGuildById(getConfigValue("Bot.Guild"));
            if (guild == null) return;
            TextChannel chan = guild.getChannelById(TextChannel.class, getConfigValue("Bot.Tickets.Channel"));
            if (chan == null) return;
            MessageHistory history = chan.getHistory();
            List<Message> msgs = history.getRetrievedHistory();
            Stream<Message> myMessages = msgs.stream().filter((msg) -> {
                if (msg.getMember() == null) return false;
                if (msg.getId().equals(getConfigValue("Bot.Tickets.MessageID"))) return false;
                return msg.getMember().getIdLong() == jda.getSelfUser().getIdLong();
            });
            for (Message msg : myMessages.toList()) {
                msg.delete().queue();
            }
            Message msg = null;
            String msgID = getConfigValue("Bot.Tickets.MessageID");
            if (!msgID.isEmpty())
                msg = history.getMessageById(msgID);
            if (msg != null) {
                // We need to delete it and set up the new one
                msg.delete().queue();
            }
            API.getInstance().createMainTicketMessage(chan).queue((msgRes) -> {});
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    public String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        List<SelectOption> selectedOpts = evt.getSelectedOptions();
        for (SelectOption opt : selectedOpts) {
            // Check if value exists
            String optVal = opt.getValue();
            String title = getConfigValue("Bot.Tickets.Create_Ticket_Options." + optVal + ".Open_Ticket_Confirm_Title");
            String desc = getConfigValue("Bot.Tickets.Create_Ticket_Options." + optVal + ".Open_Ticket_Confirm_Desc");
            API.getInstance().askConfirmDenyMessage(evt, evt.getMember(), title, desc);
        }
        evt.editSelectMenu(evt.getSelectMenu().createCopy().build()).queue();
    }

    private String getCurrentDatetimeString() {
        Date date = new Date();

        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

        formatter.setTimeZone(TimeZone.getTimeZone("EST"));

        return (formatter.format(date));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        String buttonId = evt.getButton().getId();
        String[] params = buttonId.split("\\|");
        String discCategory = getConfigValue("Bot.Tickets.Create_Ticket_Options." + params[1] + ".Category");
        System.out.println("Button ID: " + buttonId);
        String categoryLabel = getConfigValue("Bot.Tickets.Create_Ticket_Options." + params[1] + ".Label");
        String categoryIcon = getConfigValue("Bot.Tickets.Create_Ticket_Options." + params[1] + ".Icon");
        String startMessage = getConfigValue("Bot.Tickets.Create_Ticket_Options." + params[1] + ".Start_Message");
        switch (params[0].toLowerCase()) {
            case "confirm":
                Guild guild = evt.getJDA().getGuildById(getConfigValue("Bot.Guild"));
                if (guild != null) {
                    Category category = guild.getCategoryById(discCategory);
                    if (category != null) {
                        try {
                            PreparedStatement prep = Main.getInstance().getSqlHelper().getConn()
                                    .prepareStatement("INSERT INTO `tickets` (`ticket_owner`, `creation_date`, `locked`) VALUES (?, ?, ?)",
                                            new String[] {"ticket_id"});
                            prep.setLong(1, mem.getIdLong());
                            prep.setString(2, getCurrentDatetimeString());
                            prep.setInt(3, 0);
                            if (prep.execute()) {
                                ResultSet rs = prep.getGeneratedKeys();
                                if (rs.next()) {
                                    int ticketId = rs.getInt(1);
                                    category.createTextChannel(categoryIcon + "--" + evt.getMember().getUser().getName()
                                            + "--" + ticketId).queue((textChan) -> {
                                        evt.editMessage("Your " + categoryLabel + " ticket has been created: " + textChan.getAsMention()).setReplace(true).queue();
                                        // TODO Need to send a message here that they can use to close and lock ticket
                                        if (startMessage.length() > 0)
                                            textChan.sendMessage(startMessage).queue();
                                    });
                                }
                            }
                        } catch (SQLException ex) {
                            Logger.log(ex);
                            ex.printStackTrace();
                        }
                    }
                }
                break;
            case "deny":
                evt.editMessage("You have cancelled your ticket creation for category: " + categoryLabel).setReplace(true).queue();
                break;
        }
    }

    public boolean isValidTicket(TextChannel textChannel) {
        return false;
    }
    public boolean saveAndCloseTicket(TextChannel textChannel) {
        return false;
    }
}
