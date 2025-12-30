package com.jaredscarito.listeners.commands;

import java.awt.Color;

import com.jaredscarito.managers.ManagerUtils;
import com.jaredscarito.models.PunishmentType;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class WarnCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        String subcommand = evt.getSubcommandName();
        if (subcommand == null) {
            evt.reply("❌ Error: Please specify a subcommand (add or remove).").setEphemeral(true).queue();
            return;
        }
        
        switch (subcommand.toLowerCase()) {
            case "remove":
                // They want to remove the warn from the user
                ManagerUtils.handleRemovePunishment(evt, PunishmentType.WARN);
                break;
            case "add":
                ManagerUtils.handleCommandInteraction(evt, "warnUser", "Warn", Color.YELLOW);
                break;
            default:
                evt.reply("❌ Error: Unknown subcommand. Please use 'add' or 'remove'.").setEphemeral(true).queue();
        }
    }
}
