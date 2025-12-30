package com.jaredscarito.listeners.commands;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.managers.TicketManager;
import com.jaredscarito.models.ActionType;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class TicketCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        User user = null;
        if (evt.getOption("member") != null) {
            user = Objects.requireNonNull(evt.getOption("member")).getAsUser();
        }
        String subCommand = evt.getSubcommandName();
        TextChannel chan = evt.getChannel().asTextChannel();
        Member mem = evt.getMember();
        TicketManager tm = Main.getInstance().getTicketManager();
        if (mem == null) {
            evt.reply("❌ Error: Unable to identify member.").setEphemeral(true).queue();
            return;
        }
        if (subCommand == null) {
            evt.reply("❌ Error: Please specify a subcommand.").setEphemeral(true).queue();
            return;
        }
        
        if (tm.isValidTicket(chan)) {
            // Defer reply for subcommands that use getHook()
            boolean deferred = false;
            if (subCommand.equalsIgnoreCase("members") || subCommand.equalsIgnoreCase("close")) {
                evt.deferReply(true).queue();
                deferred = true;
            }
            String ticket_type = tm.getTicketTypeById(chan.getIdLong());
            if (ticket_type == null) {
                if (deferred) {
                    evt.getHook().sendMessage("❌ Error: Unable to determine ticket type.").setEphemeral(true).queue();
                } else {
                    evt.reply("❌ Error: Unable to determine ticket type.").setEphemeral(true).queue();
                }
                return;
            }
            switch (subCommand.toLowerCase()) {
                case "add":
                    if (user == null) {
                        evt.reply("❌ Error: Please specify a member to add.").setEphemeral(true).queue();
                        return;
                    }
                    if (tm.canManageTicket(mem, ticket_type)) {
                        long uid = user.getIdLong();
                        boolean added = tm.addMember(chan, uid);
                        if (added && uid != -1) {
                            if (evt.getGuild() == null) return;
                            Member memberById = evt.getGuild().getMemberById(uid);
                            if (memberById == null) return;
                            evt.reply("✅ Success: User " + memberById.getEffectiveName() + " has been added to the ticket.").queue();
                            Logger.log(ActionType.TICKET_ADD_MEMBER, evt.getMember(), memberById, chan, "");
                        } else {
                            API.getInstance().sendErrorMessage(evt, mem, "Error: Something went wrong...", "Something went wrong when this user was being added to the ticket...");
                        }
                    } else {
                        API.getInstance().sendErrorMessage(evt, mem, "Error: Permission denied.", "You lack permissions to manage this ticket...");
                    }
                    break;
                case "remove":
                    if (user == null) {
                        evt.reply("❌ Error: Please specify a member to remove.").setEphemeral(true).queue();
                        return;
                    }
                    if (tm.canManageTicket(mem, ticket_type)) {
                        long uid = user.getIdLong();
                        boolean removed = tm.removeMember(chan, uid);
                        if (removed && uid != -1) {
                            // Member was removed, send message
                            if (evt.getGuild() == null) return;
                            Member memberById = evt.getGuild().getMemberById(uid);
                            if (memberById == null) return;
                            evt.reply("✅ Success: User " + memberById.getEffectiveName() + " has been removed from the ticket.").queue();
                            Logger.log(ActionType.TICKET_REMOVE_MEMBER, evt.getMember(), memberById, chan, "");
                        } else {
                            API.getInstance().sendErrorMessage(evt, mem, "Error: Something went wrong...", "Something went wrong when this user was being removed from the ticket...");
                        }
                    } else {
                        API.getInstance().sendErrorMessage(evt, mem, "Error: Permission denied.", "You lack permissions to manage this ticket...");
                    }
                    break;
                case "members":
                    List<Member> chanMembers = chan.getMembers();
                    String memberList = "The members of this ticket are:\n";
                    for (Member m : chanMembers) {
                        if (!m.getUser().isBot())
                            memberList += m.getAsMention() + " ";
                    }
                    if (memberList.trim().equals("The members of this ticket are:")) {
                        memberList = "No members found in this ticket.";
                    }
                    evt.getHook().sendMessage(memberList).setEphemeral(true).queue();
                    break;
                case "lock":
                    if (tm.canManageTicket(mem, ticket_type)) {
                        boolean wasLocked = tm.lockTicket(chan, mem);
                        if (wasLocked) {
                            evt.reply("✅ Ticket has been locked.").queue();
                        } else {
                            evt.reply("❌ Failed to lock the ticket.").setEphemeral(true).queue();
                        }
                        Logger.log(ActionType.LOCK_TICKET, mem, chan, "");
                    } else {
                        API.getInstance().sendErrorMessage(evt, mem, "Error: Permission denied.", "You lack permissions to manage this ticket...");
                    }
                    break;
                case "unlock":
                    if (tm.canManageTicket(mem, ticket_type)) {
                        boolean wasUnlocked = tm.unlockTicket(chan, mem);
                        if (wasUnlocked) {
                            evt.reply("✅ Ticket has been unlocked.").queue();
                        } else {
                            evt.reply("❌ Failed to unlock the ticket.").setEphemeral(true).queue();
                        }
                        Logger.log(ActionType.UNLOCK_TICKET, mem, chan, "");
                    } else {
                        API.getInstance().sendErrorMessage(evt, mem, "Error: Permission denied.", "You lack permissions to manage this ticket...");
                    }
                    break;
                case "close":
                    String reason = "";
                    if (evt.getOption("reason") != null) {
                        reason = Objects.requireNonNull(evt.getOption("reason")).getAsString();
                    }
                    if (tm.canManageTicket(mem, ticket_type)) {
                        if (tm.deleteTicketFromDB(chan)) {
                            tm.saveTicket(chan, evt.getMember(), reason);
                            // it was saved, delete it
                            evt.getHook().sendMessage("This ticket will be deleted in `30` seconds...").queue();
                            chan.sendMessage("Deleting in `10` seconds...").queueAfter(20, TimeUnit.SECONDS);
                            chan.sendMessage("Deleting in `5` seconds...").queueAfter(25, TimeUnit.SECONDS);
                            chan.delete().queueAfter(30, TimeUnit.SECONDS);
                            Logger.log(ActionType.CLOSE_TICKET, evt.getMember(), reason);
                        }
                    }
                    break;
            }
        } else {
            // Not a valid ticket, let them know these commands can only be run inside ticket channels...
            evt.reply("❌ Error: This is not a valid ticket. These commands can only be used inside of a valid ticket channel.").setEphemeral(true).queue();
        }
    }
}
