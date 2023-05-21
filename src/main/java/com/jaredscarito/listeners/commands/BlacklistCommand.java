package com.jaredscarito.listeners.commands;

import com.jaredscarito.managers.ManagerUtils;
import com.jaredscarito.models.PunishmentType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;

public class BlacklistCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        if (evt.getSubcommandName() == null) return;
        if (evt.getSubcommandName().equalsIgnoreCase("remove")) {
            // They want to remove the blacklist from the user
            ManagerUtils.handleRemovePunishment(evt, PunishmentType.BLACKLIST);
        }
        if (!evt.getSubcommandName().equalsIgnoreCase("add")) return;
        ManagerUtils.handleCommandInteraction(evt, "blacklistUser", "Blacklist", Color.BLACK);
    }
}
