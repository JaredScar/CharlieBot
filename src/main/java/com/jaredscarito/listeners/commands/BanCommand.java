package com.jaredscarito.listeners.commands;

import java.awt.Color;

import com.jaredscarito.managers.ManagerUtils;
import com.jaredscarito.models.PunishmentType;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class BanCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        String subcommand = evt.getSubcommandName();
        if (subcommand == null) {
            evt.reply("❌ Error: Please specify a subcommand (add or remove).").setEphemeral(true).queue();
            return;
        }
        
        switch (subcommand.toLowerCase()) {
            case "remove":
                // They want to remove the ban from the user
                ManagerUtils.handleRemovePunishment(evt, PunishmentType.BAN);
                break;
            case "add":
                ManagerUtils.handleCommandInteraction(evt, "banUser", "Ban", Color.RED);
                break;
            default:
                evt.reply("❌ Error: Unknown subcommand. Please use 'add' or 'remove'.").setEphemeral(true).queue();
        }
    }
}
