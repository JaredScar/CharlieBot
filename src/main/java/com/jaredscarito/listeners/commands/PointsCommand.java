package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PointsCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null || mem.getUser().isBot()) return;
        
        String subcommand = evt.getSubcommandName();
        // Discord requires a subcommand when subcommands are registered
        // If somehow null, default to showing points
        if (subcommand == null) {
            showUserPoints(evt, mem);
            return;
        }
        
        switch (subcommand.toLowerCase()) {
            case "view" -> showUserPoints(evt, mem);
            case "give" -> givePoints(evt, mem);
            case "leaderboard" -> showLeaderboard(evt, mem);
            case "store" -> showStore(evt, mem);
            case "redeem" -> redeemItem(evt, mem);
            default -> showUserPoints(evt, mem); // Fallback to showing points
        }
    }
    
    private static void showUserPoints(SlashCommandInteractionEvent evt, Member mem) {
        int points = API.getInstance().getPoints(mem);
        String coinName = Main.getInstance().getConfig().getString("Bot.Messaging.Points.Name", "Charlie Coins");
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("💰 " + mem.getEffectiveName() + "'s Points");
        eb.setDescription("You have **" + points + "** " + coinName + "!");
        eb.setColor(new Color(255, 215, 0)); // Gold color
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }
    
    private static void givePoints(SlashCommandInteractionEvent evt, Member mem) {
        OptionMapping amountOpt = evt.getOption("amount");
        OptionMapping memberOpt = evt.getOption("member");
        
        if (amountOpt == null || memberOpt == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Missing required parameters!");
            return;
        }
        
        int amount = amountOpt.getAsInt();
        Member target = memberOpt.getAsMember();
        
        if (target == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Target member not found!");
            return;
        }
        
        if (amount <= 0) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Amount must be positive!");
            return;
        }
        
        // Check if user has enough points to give
        int userPoints = API.getInstance().getPoints(mem);
        if (userPoints < amount) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You don't have enough points to give!");
            return;
        }
        
        // Transfer points
        API.getInstance().removePoints(mem, amount);
        API.getInstance().addPoints(target, amount);
        
        String coinName = Main.getInstance().getConfig().getString("Bot.Messaging.Points.Name", "Charlie Coins");
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("✅ Points Transferred");
        eb.setDescription("You gave **" + amount + "** " + coinName + " to " + target.getAsMention() + "!");
        eb.setColor(Color.GREEN);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static void showLeaderboard(SlashCommandInteractionEvent evt, Member mem) {
        List<LeaderboardEntry> leaderboard = getLeaderboard();
        String coinName = Main.getInstance().getConfig().getString("Bot.Messaging.Points.Name", "Charlie Coins");
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🏆 " + coinName + " Leaderboard");
        eb.setColor(new Color(255, 215, 0)); // Gold color
        
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < Math.min(10, leaderboard.size()); i++) {
            LeaderboardEntry entry = leaderboard.get(i);
            String medal = getMedalEmoji(i + 1);
            description.append(medal).append(" **").append(entry.name).append("** - ").append(entry.points).append(" ").append(coinName).append("\n");
        }
        
        eb.setDescription(description.toString());
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static void showStore(SlashCommandInteractionEvent evt, Member mem) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🛒 Charlie's Store");
        eb.setDescription("Welcome to Charlie's store! Here you can redeem your points for special items.");
        eb.setColor(Color.CYAN);
        
        // Add store items from config
        eb.addField("🎨 Custom Role", "1000 points - Get a custom role with your name!", false);
        eb.addField("🎭 Custom Nickname", "500 points - Change your nickname!", false);
        eb.addField("🎁 Special Badge", "2000 points - Get a special Charlie badge!", false);
        eb.addField("💎 VIP Status", "5000 points - Get VIP perks for a week!", false);
        
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static void redeemItem(SlashCommandInteractionEvent evt, Member mem) {
        OptionMapping itemOpt = evt.getOption("item");
        OptionMapping amountOpt = evt.getOption("amount");
        
        if (itemOpt == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Please specify an item to redeem!");
            return;
        }
        
        String item = itemOpt.getAsString();
        int amount = amountOpt != null ? amountOpt.getAsInt() : 1;
        
        if (amount <= 0) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Amount must be positive!");
            return;
        }
        
        // Handle different items
        switch (item.toLowerCase()) {
            case "role", "custom role" -> redeemCustomRole(evt, mem, amount);
            case "nickname", "custom nickname" -> redeemCustomNickname(evt, mem, amount);
            case "badge", "special badge" -> redeemSpecialBadge(evt, mem, amount);
            case "vip", "vip status" -> redeemVipStatus(evt, mem, amount);
            default -> API.getInstance().sendErrorMessage(evt, mem, "Error", "Unknown item! Use `/points store` to see available items.");
        }
    }
    
    private static void redeemCustomRole(SlashCommandInteractionEvent evt, Member mem, int amount) {
        int cost = 1000 * amount;
        int userPoints = API.getInstance().getPoints(mem);
        
        if (userPoints < cost) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You need " + cost + " points for this item!");
            return;
        }
        
        API.getInstance().removePoints(mem, cost);
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("✅ Custom Role Redeemed!");
        eb.setDescription("You redeemed " + amount + " custom role(s) for " + cost + " points!");
        eb.setColor(Color.GREEN);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static void redeemCustomNickname(SlashCommandInteractionEvent evt, Member mem, int amount) {
        int cost = 500 * amount;
        int userPoints = API.getInstance().getPoints(mem);
        
        if (userPoints < cost) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You need " + cost + " points for this item!");
            return;
        }
        
        API.getInstance().removePoints(mem, cost);
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("✅ Custom Nickname Redeemed!");
        eb.setDescription("You redeemed " + amount + " custom nickname(s) for " + cost + " points!");
        eb.setColor(Color.GREEN);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static void redeemSpecialBadge(SlashCommandInteractionEvent evt, Member mem, int amount) {
        int cost = 2000 * amount;
        int userPoints = API.getInstance().getPoints(mem);
        
        if (userPoints < cost) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You need " + cost + " points for this item!");
            return;
        }
        
        API.getInstance().removePoints(mem, cost);
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("✅ Special Badge Redeemed!");
        eb.setDescription("You redeemed " + amount + " special Charlie badge(s) for " + cost + " points!");
        eb.setColor(Color.GREEN);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static void redeemVipStatus(SlashCommandInteractionEvent evt, Member mem, int amount) {
        int cost = 5000 * amount;
        int userPoints = API.getInstance().getPoints(mem);
        
        if (userPoints < cost) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You need " + cost + " points for this item!");
            return;
        }
        
        API.getInstance().removePoints(mem, cost);
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("✅ VIP Status Redeemed!");
        eb.setDescription("You redeemed " + amount + " week(s) of VIP status for " + cost + " points!");
        eb.setColor(Color.GREEN);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static List<LeaderboardEntry> getLeaderboard() {
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT `lastKnownName`, `points` FROM `points` ORDER BY `points` DESC LIMIT 10");
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String name = rs.getString("lastKnownName");
                int points = rs.getInt("points");
                leaderboard.add(new LeaderboardEntry(name, points));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return leaderboard;
    }
    
    private static String getMedalEmoji(int position) {
        return switch (position) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "🔸";
        };
    }
    
    private static class LeaderboardEntry {
        final String name;
        final int points;
        
        LeaderboardEntry(String name, int points) {
            this.name = name;
            this.points = points;
        }
    }
}
