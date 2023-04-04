package com.jaredscarito.listeners.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class StickyCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Guild guild = evt.getGuild();
        Member mem = evt.getMember();
        if (guild == null) return;
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        String subCommand = evt.getSubcommandName();
        if (subCommand == null) return;
        switch (subCommand.toLowerCase()) {
            case "add":
                break;
            case "edit":
                break;
            case "remove":
                break;
        }
    }
}
