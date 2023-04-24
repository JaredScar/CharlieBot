package com.jaredscarito.listeners.commands;

import com.jaredscarito.managers.ManagerUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;

public class BanCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        if (evt.getSubcommandName() == null) return;
        if (evt.getSubcommandName().equalsIgnoreCase("remove")) {
            // They want to remove the mute from the user
            // TODO
        }
        if (!evt.getSubcommandName().equalsIgnoreCase("add")) return;
        ManagerUtils.handleCommandInteraction(evt, "banUser", "Ban", Color.RED);
    }
}
