package com.jaredscarito.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.ActionType;
import com.timvisee.yamlwrapper.YamlConfiguration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;

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
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return null;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT `ticket_type` FROM `tickets` WHERE `channel_id` = ?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return rs.getString("ticket_type");
            }
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
        if (evt.getMember() == null) {
            evt.reply("❌ Error: Unable to identify member.").setEphemeral(true).queue();
            return;
        }
        
        if (!evt.getComponentId().equals("ticketSelectMenu")) return;
        
        List<SelectOption> selectedOpts = evt.getSelectedOptions();
        if (selectedOpts.isEmpty()) {
            evt.reply("❌ Error: No ticket category selected.").setEphemeral(true).queue();
            return;
        }
        
        try {
            for (SelectOption opt : selectedOpts) {
                // Check if value exists
                String optVal = opt.getValue();
                if (optVal == null || optVal.isEmpty()) {
                    evt.reply("❌ Error: Invalid ticket category selected.").setEphemeral(true).queue();
                    return;
                }
                
                String title = getConfigValue("Bot.Tickets.Ticket_Options." + optVal + ".Open_Ticket_Confirm_Title");
                String desc = getConfigValue("Bot.Tickets.Ticket_Options." + optVal + ".Open_Ticket_Confirm_Desc");
                List<String> roles_required = Main.getInstance().getConfig().getStringList("Bot.Tickets.Ticket_Options." + optVal + ".Roles_Required");
                
                if (canOpenTicketType(evt.getMember(), roles_required)) {
                    API.getInstance().askConfirmDenyMessage(evt, evt.getMember(), title, desc);
                } else {
                    API.getInstance().sendErrorMessage(evt, evt.getMember(), "Error: Permission denied.", "You do not have permissions to open a ticket of this type...");
                }
            }
        } catch (Exception e) {
            evt.reply("❌ An error occurred while processing your ticket selection: " + e.getMessage()).setEphemeral(true).queue();
            e.printStackTrace();
        }
    }

    private String getCurrentDatetimeString() {
        Date date = new Date();

        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        formatter.setTimeZone(TimeZone.getTimeZone("America/New_York"));

        return (formatter.format(date));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null || mem.getUser().isBot()) {
            return; // Let other handlers process this
        }
        
        String buttonId = evt.getButton().getId();
        if (buttonId == null) {
            return; // Let other handlers process this
        }
        
        // Check if this is a ticket-related button, if not, let other handlers process it
        // Punishment buttons contain "RuleSelectConfirm" or "RuleSelectDeny", so skip those
        if (buttonId.contains("RuleSelectConfirm") || buttonId.contains("RuleSelectDeny")) {
            // This is a punishment button, let other handlers process it
            return;
        }
        
        // Only process ticket-related buttons
        if (!buttonId.equals("closeAndSaveTicket") && 
            !buttonId.equals("lockTicket") && 
            !buttonId.equals("unlockTicket") &&
            !buttonId.startsWith("confirm|") &&
            !buttonId.startsWith("deny|") &&
            !buttonId.startsWith("closeAndSaveTicket|")) {
            // Not a ticket button, let other handlers process it
            return;
        }
        
        String[] params = buttonId.split("\\|");
        if (params.length < 2) {
            switch (params[0]) {
                case "closeAndSaveTicket" -> {
                    // Bring up modal to get reason
                    TextInput inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                            .setPlaceholder("Reason for closure")
                            .setMinLength(0)
                            .setMaxLength(1024)
                            .build();
                    Modal modal = Modal.create("closeTicketAndSaveModal"
                                    + "|" + evt.getChannel().getId(), "Close Ticket")
                            .addComponents(ActionRow.of(inp))
                            .build();
                    evt.replyModal(modal).queue();
                }
                case "lockTicket" -> {
                    if (evt.getChannel() instanceof TextChannel) {
                        boolean ticketWasLocked = this.lockTicket(evt.getChannel().asTextChannel(), evt.getMember());
                        if (ticketWasLocked) {
                            evt.reply("✅ Ticket has been locked.").setEphemeral(true).queue();
                        } else {
                            evt.reply("❌ Failed to lock ticket.").setEphemeral(true).queue();
                        }
                        Logger.log(ActionType.LOCK_TICKET, mem, evt.getChannel().asTextChannel(), "");
                    }
                }
                case "unlockTicket" -> {
                    if (evt.getChannel() instanceof TextChannel) {
                        boolean ticketWasUnlocked = this.unlockTicket(evt.getChannel().asTextChannel(), evt.getMember());
                        if (ticketWasUnlocked) {
                            evt.reply("✅ Ticket has been unlocked.").setEphemeral(true).queue();
                        } else {
                            evt.reply("❌ Failed to unlock ticket.").setEphemeral(true).queue();
                        }
                        Logger.log(ActionType.UNLOCK_TICKET, mem, evt.getChannel().asTextChannel(), "");
                    }
                }
                default -> evt.reply("❌ Unknown button action.").setEphemeral(true).queue();
            }
            return; // Not enough arguments for a create_ticket option response
        }
        
        try {
            String discCategory = getConfigValue("Bot.Tickets.Ticket_Options." + params[1] + ".Category");
            String categoryLabel = getConfigValue("Bot.Tickets.Ticket_Options." + params[1] + ".Label");
            String categoryIcon = getConfigValue("Bot.Tickets.Ticket_Options." + params[1] + ".Icon");
            String startMessage = getConfigValue("Bot.Tickets.Ticket_Options." + params[1] + ".Start_Message");
            
            if (discCategory == null || categoryLabel == null) {
                evt.reply("❌ Error: Ticket configuration not found.").setEphemeral(true).queue();
                return;
            }
            
            switch (params[0].toLowerCase()) {
                case "confirm":
                    // Acknowledge the interaction immediately with ephemeral reply
                    evt.deferReply(true).queue();
                    
                    Guild guild = evt.getJDA().getGuildById(getConfigValue("Bot.Guild"));
                    if (guild == null) {
                        evt.getHook().editOriginal("❌ Error: Guild not found.").queue();
                        return;
                    }
                    
                    Category category = guild.getCategoryById(discCategory);
                    if (category == null) {
                        evt.getHook().editOriginal("❌ Error: Ticket category not found. Please contact an administrator.").queue();
                        return;
                    }
                    
                    Connection conn = Main.getInstance().getSqlHelper().getConn();
                    if (conn == null) {
                        evt.getHook().editOriginal("❌ Error: Database connection unavailable.").queue();
                        return;
                    }
                    try (PreparedStatement prep = conn.prepareStatement("INSERT INTO `tickets` (`ticket_owner`, `ticket_type`, `creation_date`, `locked`) VALUES (?, ?, ?, ?)",
                            new String[] {"ticket_id"})) {
                        prep.setLong(1, mem.getIdLong());
                        prep.setString(2, params[1]);
                        prep.setString(3, getCurrentDatetimeString());
                        prep.setInt(4, 0);
                        prep.execute();
                        try (ResultSet rs = prep.getGeneratedKeys()) {
                            if (rs.next()) {
                                int ticketId = rs.getInt(1);
                                String channelName = (categoryIcon != null ? categoryIcon : "🎫") + "--" + evt.getMember().getUser().getName() + "--" + ticketId;
                                
                                // Prepare permissions for the ticket creator
                                List<Permission> allows = new ArrayList<>();
                                List<Permission> denies = new ArrayList<>();
                                allows.add(Permission.MESSAGE_SEND);
                                allows.add(Permission.MESSAGE_SEND_IN_THREADS);
                                allows.add(Permission.MESSAGE_ADD_REACTION);
                                allows.add(Permission.MESSAGE_HISTORY);
                                allows.add(Permission.VIEW_CHANNEL);
                                
                                // Create channel with member permissions set from the start
                                category.createTextChannel(channelName)
                                    .addMemberPermissionOverride(mem.getIdLong(), allows, denies)
                                    .queue((textChan) -> {
                                        // Add managers/roles with access to the ticket
                                        this.addManagersToTicket(textChan, params[1]);
                                        // Edit the ephemeral deferred reply (only visible to the user)
                                        evt.getHook().editOriginal("✅ Your " + categoryLabel + " ticket has been created: " + textChan.getAsMention()).queue();
                                        API.getInstance().createTicketCloseMessage(textChan, evt.getMember()).queue((msg) -> {
                                            Connection conn2 = Main.getInstance().getSqlHelper().getConn();
                                            if (conn2 != null) {
                                                try (PreparedStatement prepared = conn2.prepareStatement("UPDATE `tickets` SET `message_id` = ?, `channel_id` = ? WHERE `ticket_id` = ?")) {
                                                    prepared.setLong(1, msg.getIdLong());
                                                    prepared.setLong(2, textChan.getIdLong());
                                                    prepared.setInt(3, ticketId);
                                                    prepared.execute();
                                                } catch (SQLException ex) {
                                                    Logger.log(ex);
                                                    ex.printStackTrace();
                                                }
                                            }
                                        });
                                        if (startMessage != null && startMessage.length() > 0) {
                                            String newLine = System.getProperty("line.separator");
                                            String newMsg = startMessage.replace("\\n", newLine);
                                            textChan.sendMessage(newMsg).queue();
                                            Logger.log(ActionType.CREATE_TICKET, evt.getMember(), textChan, "");
                                        }
                                    }, (error) -> {
                                        evt.getHook().editOriginal("❌ Error: Failed to create ticket channel: " + error.getMessage()).queue();
                                        error.printStackTrace();
                                    });
                            } else {
                                evt.getHook().editOriginal("❌ Error: Failed to create ticket in database.").queue();
                            }
                        }
                    } catch (SQLException ex) {
                        Logger.log(ex);
                        ex.printStackTrace();
                        evt.getHook().editOriginal("❌ Error: Database error occurred while creating ticket.").queue();
                    }
                    break;
                case "deny":
                    evt.editMessage("❌ You have cancelled your ticket creation for category: " + categoryLabel).setReplace(true).queue();
                    break;
                default:
                    evt.reply("❌ Unknown button action.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            evt.reply("❌ An error occurred: " + e.getMessage()).setEphemeral(true).queue();
            e.printStackTrace();
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String modalId = evt.getModalId();
        String[] params = modalId.split("\\|");
        if (params.length > 1) {
            if (params[0].equals("closeTicketAndSaveModal")) {
                String channelId = params[1];
                String reason = Objects.requireNonNull(evt.getValue("reason")).getAsString();
                if (evt.getGuild() != null) {
                    TextChannel channel = evt.getGuild().getTextChannelById(channelId);
                    if (channel != null) {
                        if (this.deleteTicketFromDB(channel)) {
                            this.saveTicket(channel, evt.getMember(), reason);
                            // it was saved, delete it
                            evt.reply("This ticket will be deleted in `30` seconds...").queue();
                            channel.sendMessage("Deleting in `10` seconds...").queueAfter(20, TimeUnit.SECONDS);
                            channel.sendMessage("Deleting in `5` seconds...").queueAfter(25, TimeUnit.SECONDS);
                            channel.delete().queueAfter(30, TimeUnit.SECONDS);
                            String ticketTitle = channel.getName();
                            Logger.log(ActionType.CLOSE_TICKET, evt.getMember(), ticketTitle, reason);
                            return;
                        }
                    }
                }
                evt.reply("The ticket could not be closed due to an error...").setEphemeral(true).queue();
            }
        }
    }

    public boolean deleteTicketFromDB(TextChannel chan) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM `tickets` WHERE `channel_id` = ?")) {
            stmt.setLong(1, chan.getIdLong());
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logger.log(e);
            e.printStackTrace();
        }
        return false;
    }

    public void addManagersToTicket(TextChannel chan, String ticket_type) {
        List<Permission> allows = new ArrayList<>();
        List<Permission> denies = new ArrayList<>();
        allows.add(Permission.MESSAGE_SEND);
        allows.add(Permission.MESSAGE_SEND_IN_THREADS);
        allows.add(Permission.MESSAGE_ADD_REACTION);
        allows.add(Permission.MESSAGE_HISTORY);
        allows.add(Permission.VIEW_CHANNEL);
        List<String> manage_roles = Main.getInstance().getConfig().getStringList("Bot.Tickets.Ticket_Options." + ticket_type + ".Manage_Roles");
        for (String roleId : manage_roles) {
            Role r = chan.getGuild().getRoleById(roleId);
            if (r != null)
                chan.getPermissionContainer().getManager().putRolePermissionOverride(r.getIdLong(), allows, denies).queue();
        }
    }

    public boolean isValidTicket(TextChannel textChannel) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM `tickets` WHERE `channel_id` = ?")) {
            stmt.setLong(1, textChannel.getIdLong());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (rs.getInt("count") > 0) return true;
                }
            }
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return false;
    }
    public boolean lockTicket(TextChannel chan, Member locker) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE `tickets` SET `locked` = 1 WHERE `channel_id` = ?")) {
            stmt.setLong(1, chan.getIdLong());
            stmt.execute();
            // It executed, we want to lock the ticket now
            List<Member> chanMembers = chan.getMembers();
            List<Permission> allows = new ArrayList<>();
            List<Permission> denies = new ArrayList<>();
            denies.add(Permission.MESSAGE_SEND);
            denies.add(Permission.MESSAGE_SEND_IN_THREADS);
            denies.add(Permission.MESSAGE_ADD_REACTION);
            TextChannelManager manager = chan.getManager();
            for (Member mem : chanMembers) {
                if (!mem.getUser().isBot())
                    manager.putMemberPermissionOverride(mem.getIdLong(), allows, denies);
            }
            manager.queue();
            try (PreparedStatement stmt2 = conn.prepareStatement("SELECT `message_id` FROM `tickets` WHERE `channel_id` = ?")) {
                stmt2.setLong(1, chan.getIdLong());
                try (ResultSet rs = stmt2.executeQuery()) {
                    if (rs.next()) {
                        long messageId = rs.getLong("message_id");
                        chan.getHistoryFromBeginning(1).queue((hist) -> {
                            Message msg = hist.getMessageById(messageId);
                            Button closeAndSaveButton = Button.danger("closeAndSaveTicket", getConfigValue("Bot.Tickets.Close_Ticket_Button"));
                            Button lockButton = Button.secondary("lockTicket", getConfigValue("Bot.Tickets.Lock_Ticket_Button")).asDisabled();
                            Button unlockButton = Button.secondary("unlockTicket", getConfigValue("Bot.Tickets.Unlock_Ticket_Button"));
                            if (msg != null) {
                                // It's not null, we need to edit it
                                msg.editMessageEmbeds(msg.getEmbeds()).setActionRow(unlockButton, lockButton, closeAndSaveButton).queue();
                                chan.sendMessage("The ticket has been \uD83D\uDD12 locked by " + locker.getAsMention()).queue();
                            }
                        });
                    }
                }
            }
            return true;
        } catch (SQLException ex) {
            Logger.log(ex);
            ex.printStackTrace();
        }
        return false;
    }
    public boolean unlockTicket(TextChannel chan, Member unlocker) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE `tickets` SET `locked` = 0 WHERE `channel_id` = ?")) {
            stmt.setLong(1, chan.getIdLong());
            stmt.execute();
            // It executed and updates 1 row, we want to unlock the ticket now
            List<Member> chanMembers = chan.getMembers();
            List<Permission> allows = new ArrayList<>();
            List<Permission> denies = new ArrayList<>();
            allows.add(Permission.MESSAGE_SEND);
            allows.add(Permission.MESSAGE_SEND_IN_THREADS);
            allows.add(Permission.MESSAGE_ADD_REACTION);
            TextChannelManager manager = chan.getManager();
            for (Member mem : chanMembers) {
                if (!mem.getUser().isBot())
                    manager.putMemberPermissionOverride(mem.getIdLong(), allows, denies);
            }
            manager.queue();
            try (PreparedStatement stmt2 = conn.prepareStatement("SELECT `message_id` FROM `tickets` WHERE `channel_id` = ?")) {
                stmt2.setLong(1, chan.getIdLong());
                try (ResultSet rs = stmt2.executeQuery()) {
                    if (rs.next()) {
                        long messageId = rs.getLong("message_id");
                        chan.getHistoryFromBeginning(1).queue((hist) -> {
                            Message msg = hist.getMessageById(messageId);
                            Button closeAndSaveButton = Button.danger("closeAndSaveTicket", getConfigValue("Bot.Tickets.Close_Ticket_Button"));
                            Button lockButton = Button.secondary("lockTicket", getConfigValue("Bot.Tickets.Lock_Ticket_Button"));
                            Button unlockButton = Button.secondary("unlockTicket", getConfigValue("Bot.Tickets.Unlock_Ticket_Button")).asDisabled();
                            if (msg != null) {
                                // It's not null, we need to edit it
                                msg.editMessageEmbeds(msg.getEmbeds()).setActionRow(unlockButton, lockButton, closeAndSaveButton).queue();
                                chan.sendMessage("The ticket has been \uD83D\uDD13 unlocked by " + unlocker.getAsMention()).queue();
                            }
                        });
                    }
                }
            }
            return true;
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
    public void saveTicket(TextChannel textChannel, Member closer, String reason) {
        API.getInstance().saveTicketToFile(textChannel, closer, reason, (result) -> {});
    }
}
