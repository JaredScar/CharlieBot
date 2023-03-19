package com.jaredscarito.listeners.commands;

import com.jaredscarito.main.Main;
import com.jaredscarito.managers.TicketManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class TicketCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        OptionMapping user = evt.getOption("user");
        OptionMapping userId = evt.getOption("userId");
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
                        boolean added = false;
                        if (user != null) {
                            long uid = user.getAsUser().getIdLong();
                            added = tm.addMember(chan, uid);
                        }
                        if (userId != null) {
                            long uid = userId.getAsLong();
                            added = tm.addMember(chan, uid);
                        }
                        if (added) {
                            // TODO Member was added, send message
                        } else {
                            // TODO Member was not added, failure...
                        }
                    } else {
                        // TODO No permission to manage ticket
                    }
                    break;
                case "remove":
                    if (tm.canManageTicket(mem, ticket_type)) {
                        boolean removed = false;
                        if (user != null) {
                            long uid = user.getAsUser().getIdLong();
                            removed = tm.removeMember(chan, uid);
                        }
                        if (userId != null) {
                            long uid = userId.getAsLong();
                            removed = tm.removeMember(chan, uid);
                        }
                        if (removed) {
                            // TODO Member was removed, send message
                        } else {
                            // TODO Member was not removed, failure...
                        }
                    }
                    break;
                case "members":
                    // TODO Print out the members of the channel
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
            }
        } else {
            // Not a valid ticket, let them know these commands can only be ran inside ticket channels...
            // TODO
        }
    }
}
