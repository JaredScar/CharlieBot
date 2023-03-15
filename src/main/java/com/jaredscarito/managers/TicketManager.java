package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.timvisee.yamlwrapper.YamlConfiguration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class TicketManager extends ListenerAdapter {

    public TicketManager() {
        JDA jda = Main.getInstance().getJDA();
        try {
            Guild guild = jda.getGuildById(getConfigValue("Bot.Guild"));
            if (guild == null) return;
            TextChannel chan = guild.getChannelById(TextChannel.class, getConfigValue("Bot.Tickets.Channel"));
            if (chan == null) return;
            String msgID = getConfigValue("Bot.Tickets.MessageID");
            if (!msgID.isEmpty()) {
                // We need to delete it and set up the new one
                chan.purgeMessagesById(msgID);
            }
            API.getInstance().createMainTicketMessage(chan).queue((msgRes) -> {
                YamlConfiguration config = Main.getInstance().getConfig();
                config.set("Bot.Tickets.MessageID", msgRes.getId());
                Main.getInstance().saveConfig();
            });
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    public String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }

    public boolean canOpenTicketType(Member mem, List<String> roles_required) {
        List<Role> memRoles = mem.getRoles();
        for (Role r : memRoles) {
            if (roles_required.contains(r.getId()))
                return true;
        }
        return false;
    }

    public boolean canManageTicket(TextChannel ticketChan, Member mem) {
        return false;
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
            List<String> roles_required = Main.getInstance().getConfig().getStringList("Bot.Tickets.Create_Ticket_Options." + optVal + ".Roles_Required");
            if (canOpenTicketType(evt.getMember(), roles_required)) {
                API.getInstance().askConfirmDenyMessage(evt, evt.getMember(), title, desc);
            } else {
                // TODO Error message, they do not have permissions to open this ticket type
            }
        }
        evt.editSelectMenu(evt.getSelectMenu().createCopy().build()).queue();
    }

    private String getCurrentDatetimeString() {
        Date date = new Date();

        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
        if (params.length < 2) {
            switch (params[0]) {
                case "closeTicketAndSave":
                    break;
                case "lockTicket":
                    break;
                case "unlockTicket":
                    break;
            }
            return; // Not enough arguments for a create_ticket option response
        }
        String discCategory = getConfigValue("Bot.Tickets.Create_Ticket_Options." + params[1] + ".Category");
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
                            prep.execute();
                            ResultSet rs = prep.getGeneratedKeys();
                            if (rs.next()) {
                                int ticketId = rs.getInt(1);
                                category.createTextChannel(categoryIcon + "--" + evt.getMember().getUser().getName()
                                        + "--" + ticketId).queue((textChan) -> {
                                    evt.editMessage("Your " + categoryLabel + " ticket has been created: " + textChan.getAsMention()).setReplace(true).queue();
                                    API.getInstance().createTicketCloseMessage(textChan, evt.getMember()).queue((msg) -> {
                                        try {
                                            PreparedStatement prepared = Main.getInstance().getSqlHelper().getConn()
                                                    .prepareStatement("UPDATE `tickets` SET `message_id` = ?, `channel_id` = ? WHERE `ticket_id` = ?");
                                            prepared.setLong(1, msg.getIdLong());
                                            prepared.setLong(2, textChan.getIdLong());
                                            prepared.setInt(3, ticketId);
                                            prepared.execute();
                                        } catch (SQLException ex) {
                                            Logger.log(ex);
                                            ex.printStackTrace();
                                        }
                                    });
                                    if (startMessage.length() > 0)
                                        textChan.sendMessage(startMessage).queue();
                                });
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
