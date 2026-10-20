package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.PunishmentData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class HistoryCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member member = evt.getMember();
        if (member == null || member.getUser().isBot()) return;
        if (evt.getSubcommandName() == null) return;

        switch (evt.getSubcommandName().toLowerCase()) {
            case "view" -> viewHistory(evt, member);
            case "clear" -> clearHistory(evt, member);
            default -> {
                // Handle unknown subcommand
                API.getInstance().sendErrorMessage(evt, member, "Error", "Unknown subcommand!");
            }
        }
    }
    
    private static void viewHistory(SlashCommandInteractionEvent evt, Member member) {
        OptionMapping option = evt.getOption("member");
        if (option == null) {
            API.getInstance().sendErrorMessage(evt, member, "Error", "Please specify a member to view history for!");
            return;
        }
        
        Member target = option.getAsMember();
        if (target == null) {
            API.getInstance().sendErrorMessage(evt, member, "Error", "Target member not found!");
            return;
        }
        
        List<PunishmentData> punishments = API.getInstance().getAllPunishmentData(target.getIdLong());
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("📋 Punishment History - " + target.getEffectiveName());
        eb.setColor(Color.ORANGE);
        eb.setAuthor(member.getEffectiveName(), member.getAvatarUrl());
        eb.setThumbnail(target.getAvatarUrl());
        
        // Filter cleared entries FIRST, otherwise the 10 entry limit could be used up by deleted ones
        List<PunishmentData> visible = new ArrayList<>();
        for (PunishmentData p : punishments) {
            if (!p.isDeleted()) visible.add(p);
        }
        
        if (visible.isEmpty()) {
            eb.setDescription("This user has no punishment history.");
        } else {
            StringBuilder historyText = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            int shown = 0;
            
            for (PunishmentData punishment : visible) {
                if (shown >= 10) break;
                
                String dateStr = punishment.getDatetime() != null ? 
                    dateFormat.format(punishment.getDatetime()) : "Unknown Date";
                
                StringBuilder entry = new StringBuilder();
                entry.append("**").append(punishment.getPunishmentType() == null ? "UNKNOWN" : punishment.getPunishmentType().name()).append("**")
                    .append(" - ").append(dateStr).append("\n");
                entry.append("Rules: ").append(punishment.getRuleIdsBroken()).append("\n");
                // embed descriptions are limited to 4096 characters, so cap each reason
                entry.append("Reason: ").append(truncate(punishment.getReason(), 200)).append("\n");
                entry.append("By: ").append(punishment.getPunishedByLastKnownName()).append("\n\n");
                
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
    
    private static void clearHistory(SlashCommandInteractionEvent evt, Member member) {
        OptionMapping option = evt.getOption("member");
        if (option == null) {
            API.getInstance().sendErrorMessage(evt, member, "Error", "Please specify a member to clear history for!");
            return;
        }
        
        Member target = option.getAsMember();
        if (target == null) {
            API.getInstance().sendErrorMessage(evt, member, "Error", "Target member not found!");
            return;
        }
        
        // Mark all punishments as deleted instead of actually deleting them
        int cleared = markPunishmentsAsDeleted(target.getIdLong());
        
        if (cleared >= 0) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("✅ History Cleared");
            // 0 affected rows is not a failure: the user simply had nothing to clear
            eb.setDescription(cleared == 0
                    ? target.getAsMention() + " has no punishment history to clear."
                    : "Successfully cleared " + cleared + " punishment history entr" + (cleared == 1 ? "y" : "ies") + " for " + target.getAsMention());
            eb.setColor(Color.GREEN);
            eb.setAuthor(member.getEffectiveName(), member.getAvatarUrl());
            
            evt.replyEmbeds(eb.build()).queue();
        } else {
            API.getInstance().sendErrorMessage(evt, member, "Error", "Failed to clear punishment history!");
        }
    }
    
    /** @return number of entries cleared, or -1 on a database error */
    private static int markPunishmentsAsDeleted(long discordId) {
        var conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return -1;
        try (var stmt = conn.prepareStatement("UPDATE `punishments` SET `deleted` = 1 WHERE `discord_id` = ? AND `deleted` = 0")) {
            stmt.setLong(1, discordId);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.log(e);
            e.printStackTrace();
            return -1;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "None";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
