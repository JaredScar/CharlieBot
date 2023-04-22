package com.jaredscarito.listeners.commands;

import com.jaredscarito.managers.ManagerUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;

public class KickCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        ManagerUtils.handleCommandInteraction(evt, "kickUser", "Kick", Color.ORANGE);
    }
}
