package com.jaredscarito.listeners.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class HelpCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member member = evt.getMember();
        if (member == null || member.getUser().isBot()) return;
    }
}
