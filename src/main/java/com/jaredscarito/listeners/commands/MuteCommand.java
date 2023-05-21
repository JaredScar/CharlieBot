package com.jaredscarito.listeners.commands;

import com.jaredscarito.managers.ManagerUtils;
import com.jaredscarito.models.PunishmentType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;

public class MuteCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        if (evt.getSubcommandName() == null) return;
        if (evt.getSubcommandName().equalsIgnoreCase("remove")) {
            // They want to remove the mute from the user
            ManagerUtils.handleRemovePunishment(evt, PunishmentType.MUTE);
        }
        if (!evt.getSubcommandName().equalsIgnoreCase("add") && !evt.getSubcommandName().equalsIgnoreCase("edit")) return;
        ManagerUtils.handleCommandInteraction(evt, "muteUser", "Mute", Color.PINK);
    }
}
