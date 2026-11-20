package com.jaredscarito.listeners.commands;

import com.jaredscarito.managers.BanManager;
import com.jaredscarito.managers.ManagerUtils;
import com.jaredscarito.managers.PunishmentEffects;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.Color;

public class TempbanCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member staff = evt.getMember();
        if (staff == null) return;
        OptionMapping memberOpt = evt.getOption("member");
        OptionMapping durationOpt = evt.getOption("duration");
        if (memberOpt == null || durationOpt == null) {
            evt.reply("❌ Please choose a member and a duration (30m, 2h, 7d, or 1w).").setEphemeral(true).queue();
            return;
        }
        Member target = memberOpt.getAsMember();
        if (target == null) {
            evt.reply("❌ That user is not a member of this server.").setEphemeral(true).queue();
            return;
        }
        String raw = durationOpt.getAsString();
        Long millis;
        try {
            millis = PunishmentEffects.parseDuration(raw);
        } catch (IllegalArgumentException ex) {
            evt.reply("❌ Could not read that duration. Use 30m, 2h, 7d, or 1w. A plain number means hours.").setEphemeral(true).queue();
            return;
        }
        if (millis == null) {
            evt.reply("❌ A temporary ban needs an end time. Use `/ban add` for a permanent ban.").setEphemeral(true).queue();
            return;
        }
        if (!ManagerUtils.handleCommandInteraction(evt, "tempbanUser", "Tempban", Color.RED)) return;
        BanManager.rememberTempban("tempbanUser|" + target.getId() + "|" + staff.getId(), raw.trim(), millis);
    }
}
