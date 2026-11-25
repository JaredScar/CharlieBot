package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.managers.MemberRoleManager;
import com.jaredscarito.managers.StaffNotes;
import com.jaredscarito.models.PunishmentData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class WhoisCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member staff = evt.getMember();
        if (staff == null) return;
        if (evt.getOption("member") == null || evt.getOption("member").getAsMember() == null) {
            evt.reply("❌ That user is not in this server.").setEphemeral(true).queue();
            return;
        }
        Member target = evt.getOption("member").getAsMember();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(88, 101, 242));
        eb.setAuthor(staff.getEffectiveName(), staff.getAvatarUrl());
        eb.setThumbnail(target.getEffectiveAvatarUrl());
        eb.setTitle(target.getEffectiveName());
        eb.setDescription(target.getAsMention());

        long created = target.getUser().getTimeCreated().toEpochSecond();
        eb.addField("Account created", "<t:" + created + ":D> (<t:" + created + ":R>)", true);
        if (target.hasTimeJoined()) {
            long joined = target.getTimeJoined().toEpochSecond();
            eb.addField("Joined", "<t:" + joined + ":D> (<t:" + joined + ":R>)", true);
        } else {
            eb.addField("Joined", "Unknown", true);
        }
        eb.addField("Rank", "Level " + API.getInstance().getRank(target) + " (" + API.getInstance().getRankExp(target) + " exp)", true);
        eb.addField("Points", String.valueOf(API.getInstance().getPoints(target)), true);

        List<String> status = new ArrayList<>();
        if (MemberRoleManager.isMuted(target.getIdLong())) status.add("Muted");
        if (MemberRoleManager.isBlacklisted(target.getIdLong())) status.add("Blacklisted");
        eb.addField("Status", status.isEmpty() ? "None" : String.join(", ", status), true);

        eb.addField("Roles", roles(target), false);
        eb.addField("Recent punishments", punishments(target.getIdLong()), false);
        eb.addField("Staff notes", StaffNotes.formatRecent(target.getIdLong(), 5, 900), false);

        evt.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private static String roles(Member target) {
        if (target.getRoles().isEmpty()) return "None";
        StringBuilder text = new StringBuilder();
        for (Role role : target.getRoles()) {
            if (role.isPublicRole()) continue;
            String name = role.getName();
            if (text.length() + name.length() + 2 > 900) {
                text.append("…");
                break;
            }
            if (text.length() > 0) text.append(", ");
            text.append(name);
        }
        return text.length() == 0 ? "None" : text.toString();
    }

    private static String punishments(long discordId) {
        List<PunishmentData> visible = new ArrayList<>();
        for (PunishmentData punishment : API.getInstance().getAllPunishmentData(discordId)) {
            if (!punishment.isDeleted()) visible.add(punishment);
        }
        if (visible.isEmpty()) return "None";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (PunishmentData punishment : visible) {
            if (shown >= 5) break;
            String date = punishment.getDatetime() == null ? "Unknown date" : dateFormat.format(punishment.getDatetime());
            String type = punishment.getPunishmentType() == null ? "UNKNOWN" : punishment.getPunishmentType().name();
            String length = punishment.getPunishmentLength();
            StringBuilder entry = new StringBuilder();
            entry.append("**").append(type).append("**");
            if (length != null && !length.isBlank()) entry.append(" (").append(truncate(length, 20)).append(")");
            entry.append(" — ").append(date).append("\n");
            entry.append(truncate(punishment.getReason(), 80)).append("\n\n");
            if (text.length() + entry.length() > 900) break;
            text.append(entry);
            shown++;
        }
        if (visible.size() > shown) text.append("*... and ").append(visible.size() - shown).append(" more*");
        String result = text.toString().trim();
        return result.isEmpty() ? "None" : result;
    }

    private static String truncate(String text, int max) {
        if (text == null || text.isBlank()) return "None";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
