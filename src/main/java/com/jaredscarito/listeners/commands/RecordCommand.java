package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.models.PunishmentData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * A member's own punishment record. Unlike /history, this never names the staff member who issued it.
 */
public class RecordCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member member = evt.getMember();
        if (member == null || member.getUser().isBot()) return;

        List<PunishmentData> punishments = API.getInstance().getAllPunishmentData(member.getIdLong());
        List<PunishmentData> visible = new ArrayList<>();
        for (PunishmentData punishment : punishments) {
            if (!punishment.isDeleted()) visible.add(punishment);
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("📋 Your record");
        eb.setColor(Color.ORANGE);
        eb.setAuthor(member.getEffectiveName(), member.getAvatarUrl());

        if (visible.isEmpty()) {
            eb.setDescription("You have no punishments on record.");
        } else {
            StringBuilder historyText = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            int shown = 0;
            for (PunishmentData punishment : visible) {
                if (shown >= 10) break;
                String dateStr = punishment.getDatetime() != null
                        ? dateFormat.format(punishment.getDatetime()) : "Unknown date";
                StringBuilder entry = new StringBuilder();
                entry.append("**").append(punishment.getPunishmentType() == null ? "UNKNOWN" : punishment.getPunishmentType().name()).append("**");
                String length = punishment.getPunishmentLength();
                if (length != null && !length.isBlank()) entry.append(" (").append(truncate(length, 20)).append(")");
                entry.append(" — ").append(dateStr).append("\n");
                entry.append("Rules: ").append(blank(punishment.getRuleIdsBroken())).append("\n");
                entry.append("Reason: ").append(truncate(punishment.getReason(), 200)).append("\n\n");
                if (historyText.length() + entry.length() > 3800) break;
                historyText.append(entry);
                shown++;
            }
            if (visible.size() > shown) {
                historyText.append("*... and ").append(visible.size() - shown).append(" more entries*");
            }
            eb.setDescription(historyText.toString());
        }

        evt.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private static String blank(String text) {
        return (text == null || text.isBlank()) ? "None" : text;
    }

    private static String truncate(String text, int max) {
        if (text == null || text.isBlank()) return "None";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
