package com.jaredscarito.listeners.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class BlacklistCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        if (evt.getSubcommandName() == null) return;
        if (evt.getSubcommandName().equalsIgnoreCase("remove")) {
            // They want to remove the warn from the user
            // TODO
        }
        if (!evt.getSubcommandName().equalsIgnoreCase("add")) return;
    }
}
