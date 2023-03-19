package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.mysql.cj.log.Log;
import com.timvisee.yamlwrapper.YamlConfiguration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

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

    public String getTicketTypeById(long id) {
        try {
            PreparedStatement stmt = Main.getInstance().getSqlHelper().getConn().prepareStatement("SELECT `ticket_type` FROM `tickets` WHERE `channel_id` = ?");
            stmt.setLong(1, id);
            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            if (rs.next())
                return rs.getString("ticket_type");
        } catch (SQLException e) {
            Logger.log(e);
            e.printStackTrace();
        }
        return null;
    }

    public boolean canManageTicket(Member mem, String ticket_type) {
        List<String> roles_required = Main.getInstance().getConfig().getStringList("Bot.Tickets.Ticket_Options." + ticket_type + ".Roles_Required");
        List<Role> memRoles = mem.getRoles();
        for (Role r : memRoles) {
            if (roles_required.contains(r.getId()))
                return true;
        }
        return false;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        List<SelectOption> selectedOpts = evt.getSelectedOptions();
        for (SelectOption opt : selectedOpts) {
            // Check if value exists
            String optVal = opt.getValue();
            String title = getConfigValue("Bot.Tickets.Ticket_Options." + optVal + ".Open_Ticket_Confirm_Title");
            String desc = getConfigValue("Bot.Tickets.Ticket_Options." + optVal + ".Open_Ticket_Confirm_Desc");
            List<String> roles_required = Main.getInstance().getConfig().getStringList("Bot.Tickets.Ticket_Options." + optVal + ".Roles_Required");
            if (canOpenTicketType(evt.getMember(), roles_required)) {
                API.getInstance().askConfirmDenyMessage(evt, evt.getMember(), title, desc);
            } else {
                API.getInstance().sendErrorMessage(evt, evt.getMember(), "Error: Permission denied.", "You do not have permissions to open a ticket of this type...");
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
                    boolean ticketWasClosed = this.saveAndCloseTicket(evt.getChannel().asTextChannel(), evt.getMember());
                    break;
                case "lockTicket":
                    boolean ticketWasLocked = this.lockTicket(evt.getChannel().asTextChannel(), evt.getMember());
                    break;
                case "unlockTicket":
                    boolean ticketWasUnlocked = this.unlockTicket(evt.getChannel().asTextChannel(), evt.getMember());
                    break;
            }
            return; // Not enough arguments for a create_ticket option response
        }
        String discCategory = getConfigValue("Bot.Tickets.Ticket_Options." + params[1] + ".Category");
        String categoryLabel = getConfigValue("Bot.Tickets.Ticket_Options." + params[1] + ".Label");
        String categoryIcon = getConfigValue("Bot.Tickets.Ticket_Options." + params[1] + ".Icon");
        String startMessage = getConfigValue("Bot.Tickets.Ticket_Options." + params[1] + ".Start_Message");
        switch (params[0].toLowerCase()) {
            case "confirm":
                Guild guild = evt.getJDA().getGuildById(getConfigValue("Bot.Guild"));
                if (guild != null) {
                    Category category = guild.getCategoryById(discCategory);
                    if (category != null) {
                        try {
                            PreparedStatement prep = Main.getInstance().getSqlHelper().getConn()
                                    .prepareStatement("INSERT INTO `tickets` (`ticket_owner`, `ticket_type`, `creation_date`, `locked`) VALUES (?, ?, ?, ?)",
                                            new String[] {"ticket_id"});
                            prep.setLong(1, mem.getIdLong());
                            prep.setString(2, params[1]);
                            prep.setString(3, getCurrentDatetimeString());
                            prep.setInt(4, 0);
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
        try {
            PreparedStatement stmt = Main.getInstance().getSqlHelper().getConn().prepareStatement("SELECT COUNT(*) as count FROM `tickets` WHERE `channel_id` = ?");
            stmt.setLong(1, textChannel.getIdLong());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                if (rs.getInt("count") > 0) return true;
            }
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return false;
    }
    public boolean lockTicket(TextChannel chan, Member locker) {
        try {
            PreparedStatement stmt = Main.getInstance().getSqlHelper().getConn().prepareStatement("UPDATE `tickets` SET `locked` = 1 WHERE `channel_id` = ?");
            stmt.setLong(1, chan.getIdLong());
            if (stmt.execute()) {
                // It executed, we want to lock the ticket now
                List<Member> chanMembers = chan.getMembers();
                List<Permission> allows = new ArrayList<>();
                List<Permission> denies = new ArrayList<>();
                denies.add(Permission.MESSAGE_SEND);
                denies.add(Permission.MESSAGE_SEND_IN_THREADS);
                denies.add(Permission.MESSAGE_ADD_REACTION);
                for (Member mem : chanMembers) {
                    chan.getPermissionContainer().getManager().putMemberPermissionOverride(mem.getIdLong(), allows, denies).queue();
                }
                stmt = Main.getInstance().getSqlHelper().getConn().prepareStatement("SELECT `message_id` FROM `tickets` WHERE `channel_id` = ?");
                stmt.setLong(1, chan.getIdLong());
                stmt.execute();
                ResultSet rs = stmt.getResultSet();
                long messageId = rs.getLong("message_id");
                chan.getHistoryFromBeginning(1).queue((hist) -> {
                    Message msg = hist.getMessageById(messageId);
                    Button closeAndSaveButton = Button.danger("closeAndSaveTicket", getConfigValue("Bot.Tickets.Close_Ticket_Button"));
                    Button lockButton = Button.secondary("lockTicket", getConfigValue("Bot.Tickets.Lock_Ticket_Button")).asDisabled();
                    Button unlockButton = Button.secondary("unlockTicket", getConfigValue("Bot.Tickets.Unlock_Ticket_Button"));
                    if (msg != null) {
                        // It's not null, we need to edit it
                        msg.editMessageEmbeds(msg.getEmbeds()).setActionRow(unlockButton, lockButton, closeAndSaveButton).queue();
                        chan.sendMessage("The ticket has been \uD83D\uDD34 locked by " + locker.getAsMention()).queue();
                    }
                });
                return true;
            }
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return false;
    }
    public boolean unlockTicket(TextChannel chan, Member unlocker) {
        try {
            PreparedStatement stmt = Main.getInstance().getSqlHelper().getConn().prepareStatement("UPDATE `tickets` SET `locked` = 0 WHERE `channel_id` = ?");
            stmt.setLong(1, chan.getIdLong());
            if (stmt.execute()) {
                // It executed and updates 1 row, we want to unlock the ticket now
                List<Member> chanMembers = chan.getMembers();
                List<Permission> allows = new ArrayList<>();
                List<Permission> denies = new ArrayList<>();
                allows.add(Permission.MESSAGE_SEND);
                allows.add(Permission.MESSAGE_SEND_IN_THREADS);
                allows.add(Permission.MESSAGE_ADD_REACTION);
                for (Member mem : chanMembers) {
                    chan.getPermissionContainer().getManager().putMemberPermissionOverride(mem.getIdLong(), allows, denies).queue();
                }
                stmt = Main.getInstance().getSqlHelper().getConn().prepareStatement("SELECT `message_id` FROM `tickets` WHERE `channel_id` = ?");
                stmt.setLong(1, chan.getIdLong());
                stmt.execute();
                ResultSet rs = stmt.getResultSet();
                long messageId = rs.getLong("message_id");
                chan.getHistoryFromBeginning(1).queue((hist) -> {
                    Message msg = hist.getMessageById(messageId);
                    Button closeAndSaveButton = Button.danger("closeAndSaveTicket", getConfigValue("Bot.Tickets.Close_Ticket_Button"));
                    Button lockButton = Button.secondary("lockTicket", getConfigValue("Bot.Tickets.Lock_Ticket_Button"));
                    Button unlockButton = Button.secondary("unlockTicket", getConfigValue("Bot.Tickets.Unlock_Ticket_Button")).asDisabled();
                    if (msg != null) {
                        // It's not null, we need to edit it
                        msg.editMessageEmbeds(msg.getEmbeds()).setActionRow(unlockButton, lockButton, closeAndSaveButton).queue();
                        chan.sendMessage("The ticket has been \uD83D\uDFE2 unlocked by " + unlocker.getAsMention()).queue();
                    }
                });
                return true;
            }
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return false;
    }
    public boolean addMember(TextChannel chan, long uid) {
        Member mem = chan.getGuild().getMemberById(uid);
        if (mem == null) return false;
        List<Permission> allows = new ArrayList<>();
        List<Permission> denies = new ArrayList<>();
        allows.add(Permission.MESSAGE_SEND);
        allows.add(Permission.MESSAGE_SEND_IN_THREADS);
        allows.add(Permission.MESSAGE_ADD_REACTION);
        allows.add(Permission.MESSAGE_HISTORY);
        allows.add(Permission.VIEW_CHANNEL);
        chan.getPermissionContainer().getManager().putMemberPermissionOverride(mem.getIdLong(), allows, denies).queue();
        return true;
    }
    public boolean removeMember(TextChannel chan, long uid) {
        Member mem = chan.getGuild().getMemberById(uid);
        if (mem == null) return false;
        List<Permission> allows = new ArrayList<>();
        List<Permission> denies = new ArrayList<>();
        denies.add(Permission.MESSAGE_SEND);
        denies.add(Permission.MESSAGE_SEND_IN_THREADS);
        denies.add(Permission.MESSAGE_ADD_REACTION);
        denies.add(Permission.MESSAGE_HISTORY);
        denies.add(Permission.VIEW_CHANNEL);
        chan.getPermissionContainer().getManager().putMemberPermissionOverride(mem.getIdLong(), allows, denies).queue();
        return true;
    }
    public boolean saveAndCloseTicket(TextChannel textChannel, Member closer) {
        return false;
    }
}
