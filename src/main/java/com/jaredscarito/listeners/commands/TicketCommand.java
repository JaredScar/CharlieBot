package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import com.jaredscarito.managers.TicketManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.List;

public class TicketCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        User user = null;
        if (evt.getOption("member") != null) {
            user = evt.getOption("member").getAsUser();
        }
        String subCommand = evt.getSubcommandName();
        TextChannel chan = evt.getChannel().asTextChannel();
        Member mem = evt.getMember();
        TicketManager tm = Main.getInstance().getTicketManager();
        if (mem == null) return;
        if (subCommand == null) return;
        if (tm.isValidTicket(chan)) {
            String ticket_type = tm.getTicketTypeById(chan.getIdLong());
            if (ticket_type == null) return;
            switch (subCommand.toLowerCase()) {
                case "add":
                    if (tm.canManageTicket(mem, ticket_type)) {
                        long uid = user.getIdLong();
                        boolean added = tm.addMember(chan, uid);
                        if (added && uid != -1) {
                            if (evt.getGuild() == null) return;
                            Member memberById = evt.getGuild().getMemberById(uid);
                            if (memberById == null) return;
                            chan.sendMessage("Success: User " + memberById.getEffectiveName() + " has been added to the ticket...").queue();
                        } else {
                            API.getInstance().sendErrorMessage(evt, mem, "Error: Something went wrong...", "Something went wrong when this user was being added to the ticket...");
                        }
                    } else {
                        API.getInstance().sendErrorMessage(evt, mem, "Error: Permission denied.", "You lack permissions to manage this ticket...");
                    }
                    break;
                case "remove":
                    if (tm.canManageTicket(mem, ticket_type)) {
                        long uid = user.getIdLong();
                        boolean removed = tm.removeMember(chan, uid);
                        if (removed && uid != -1) {
                            // Member was removed, send message
                            if (evt.getGuild() == null) return;
                            Member memberById = evt.getGuild().getMemberById(uid);
                            if (memberById == null) return;
                            chan.sendMessage("Success: User " + memberById.getEffectiveName() + " has been removed from the ticket...").queue();
                        } else {
                            API.getInstance().sendErrorMessage(evt, mem, "Error: Something went wrong...", "Something went wrong when this user was being removed from the ticket...");
                        }
                    }
                    break;
                case "members":
                    List<Member> chanMembers = chan.getMembers();
                    String memberList = "The members of this ticket are:\n";
                    for (Member m : chanMembers) {
                        if (!m.getUser().isBot())
                            memberList += m.getAsMention();
                    }
                    evt.getHook().sendMessage(memberList).setEphemeral(true).queue();
                    break;
                case "lock":
                    if (tm.canManageTicket(mem, ticket_type)) {
                        boolean wasLocked = tm.lockTicket(chan, mem);
                    }
                    break;
                case "unlock":
                    if (tm.canManageTicket(mem, ticket_type)) {
                        boolean wasUnlocked = tm.unlockTicket(chan, mem);
                    }
                    break;
                case "close":
                    String reason = "";
                    if (evt.getOption("reason") != null) {
                        reason = evt.getOption("reason").getAsString();
                    }
                    if (tm.canManageTicket(mem, ticket_type)) {
                        tm.saveAndCloseTicket(chan, mem, reason);
                    }
                    break;
            }
        } else {
            // Not a valid ticket, let them know these commands can only be run inside ticket channels...
            API.getInstance().sendErrorMessage(evt, mem, "Error: This is not a valid ticket.", "These commands can only be ran inside of a valid ticket...");
        }
    }
}
