package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.PunishmentData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
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
        
        if (punishments.isEmpty()) {
            eb.setDescription("This user has no punishment history.");
        } else {
            StringBuilder historyText = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            
            for (int i = 0; i < Math.min(punishments.size(), 10); i++) {
                PunishmentData punishment = punishments.get(i);
                if (punishment.isDeleted()) continue;
                
                String dateStr = punishment.getDatetime() != null ? 
                    dateFormat.format(punishment.getDatetime()) : "Unknown Date";
                
                historyText.append("**").append(punishment.getPunishmentType().name()).append("**")
                    .append(" - ").append(dateStr).append("\n");
                historyText.append("Rules: ").append(punishment.getRuleIdsBroken()).append("\n");
                historyText.append("Reason: ").append(punishment.getReason()).append("\n");
                historyText.append("By: ").append(punishment.getPunishedByLastKnownName()).append("\n\n");
            }
            
            if (punishments.size() > 10) {
                historyText.append("*... and ").append(punishments.size() - 10).append(" more entries*");
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
        boolean success = markPunishmentsAsDeleted(target.getIdLong());
        
        if (success) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("✅ History Cleared");
            eb.setDescription("Successfully cleared punishment history for " + target.getAsMention());
            eb.setColor(Color.GREEN);
            eb.setAuthor(member.getEffectiveName(), member.getAvatarUrl());
            
            evt.replyEmbeds(eb.build()).queue();
        } else {
            API.getInstance().sendErrorMessage(evt, member, "Error", "Failed to clear punishment history!");
        }
    }
    
    private static boolean markPunishmentsAsDeleted(long discordId) {
        var conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (var stmt = conn.prepareStatement("UPDATE `punishments` SET `deleted` = 1 WHERE `discord_id` = ?")) {
            stmt.setLong(1, discordId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
