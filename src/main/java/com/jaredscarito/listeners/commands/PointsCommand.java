package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.timvisee.yamlwrapper.ConfigurationSection;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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
    private static final String STORE_PATH = "Bot.Messaging.Points.Store.Items";

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
        if (target.getIdLong() == mem.getIdLong()) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You can't give points to yourself!");
            return;
        }
        if (target.getUser().isBot()) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You can't give points to a bot!");
            return;
        }
        if (amount <= 0) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Amount must be positive!");
            return;
        }
        
        // Take the points atomically (fails if the balance is too low, even with concurrent commands)
        if (!API.getInstance().tryRemovePoints(mem, amount)) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You don't have enough points to give!");
            return;
        }
        if (!API.getInstance().addPoints(target, amount)) {
            // Could not credit the receiver: give the points back
            API.getInstance().addPoints(mem, amount);
            API.getInstance().sendErrorMessage(evt, mem, "Error", "The transfer failed, you were not charged.");
            return;
        }
        
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
        if (description.length() == 0) {
            // an embed with an empty description is rejected by Discord
            description.append("Nobody has earned any ").append(coinName).append(" yet.");
        }
        
        eb.setDescription(description.toString());
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        evt.replyEmbeds(eb.build()).queue();
    }

    // ------------------------------------------------------------------------------------------
    // Store (configured in config.yml under Bot.Messaging.Points.Store.Items)
    // ------------------------------------------------------------------------------------------

    /** A purchasable reward: pay points, receive a role. */
    private static final class StoreItem {
        final String key;
        final String name;
        final String description;
        final int cost;
        final Role role;

        StoreItem(String key, String name, String description, int cost, Role role) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.cost = cost;
            this.role = role;
        }
    }

    /**
     * Reads the store from the config. An item only counts when it has a positive Cost and a Role that exists in
     * the server, so nothing is ever offered that can't actually be delivered.
     */
    private static List<StoreItem> loadStoreItems(Guild guild) {
        List<StoreItem> items = new ArrayList<>();
        ConfigurationSection section = Main.getInstance().getConfig().getConfigurationSection(STORE_PATH);
        if (section == null) return items;
        List<String> keys = section.getKeys();
        if (keys == null) return items;
        for (String key : keys) {
            String base = STORE_PATH + "." + key;
            int cost = Main.getInstance().getConfig().getInt(base + ".Cost");
            Object rawRole = Main.getInstance().getConfig().get(base + ".Role");
            if (cost <= 0 || rawRole == null) continue;
            Role role;
            try {
                role = guild.getRoleById(String.valueOf(rawRole).trim());
            } catch (NumberFormatException ex) {
                continue; // not a valid role id
            }
            if (role == null) continue;
            String name = Main.getInstance().getConfig().getString(base + ".Name", key);
            String description = Main.getInstance().getConfig().getString(base + ".Description", "");
            items.add(new StoreItem(key, name, description == null ? "" : description, cost, role));
        }
        return items;
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private static void showStore(SlashCommandInteractionEvent evt, Member mem) {
        Guild guild = evt.getGuild();
        if (guild == null) return;
        String coinName = Main.getInstance().getConfig().getString("Bot.Messaging.Points.Name", "Charlie Coins");
        List<StoreItem> items = loadStoreItems(guild);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🛒 Charlie's Store");
        eb.setColor(Color.CYAN);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        if (items.isEmpty()) {
            eb.setDescription("The store is empty right now. Check back later!");
        } else {
            eb.setDescription("Redeem your " + coinName + " for these rewards with `/points redeem item:<name>`.");
            // an embed holds at most 25 fields
            for (int i = 0; i < Math.min(items.size(), 25); i++) {
                StoreItem item = items.get(i);
                String value = (item.description.isEmpty() ? "" : item.description + "\n")
                        + "Reward: " + item.role.getAsMention() + "\nRedeem with: `" + item.key + "`";
                eb.addField(shorten(item.name + " - " + item.cost + " " + coinName, 256), shorten(value, 1024), false);
            }
        }
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static void redeemItem(SlashCommandInteractionEvent evt, Member mem) {
        OptionMapping itemOpt = evt.getOption("item");
        OptionMapping amountOpt = evt.getOption("amount");
        Guild guild = evt.getGuild();
        if (guild == null) return;
        
        if (itemOpt == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Please specify an item to redeem!");
            return;
        }
        if (amountOpt != null && amountOpt.getAsInt() != 1) {
            // every reward is a role, which you can only own once
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Store items can only be redeemed one at a time!");
            return;
        }

        String wanted = itemOpt.getAsString().trim();
        StoreItem item = null;
        for (StoreItem candidate : loadStoreItems(guild)) {
            if (candidate.key.equalsIgnoreCase(wanted) || candidate.name.equalsIgnoreCase(wanted)) {
                item = candidate;
                break;
            }
        }
        if (item == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Unknown item! Use `/points store` to see available items.");
            return;
        }
        if (mem.getRoles().contains(item.role)) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You already own this reward!");
            return;
        }
        if (!guild.getSelfMember().canInteract(item.role)) {
            // the bot's role sits below the reward role: say so BEFORE taking any points
            API.getInstance().sendErrorMessage(evt, mem, "Error", "I can't hand out this reward right now. Please tell a staff member.");
            return;
        }

        final StoreItem bought = item;
        // atomic: fails if the balance is too low, even with concurrent commands
        if (!API.getInstance().tryRemovePoints(mem, bought.cost)) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "You need " + bought.cost + " points for this item!");
            return;
        }

        // Granting the role is a Discord request: acknowledge first, then report the outcome
        evt.deferReply().queue();
        guild.addRoleToMember(mem, bought.role).queue((v) -> {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("✅ " + bought.name + " Redeemed!");
            eb.setDescription("You redeemed **" + bought.name + "** for " + bought.cost + " points and received " + bought.role.getAsMention() + "!");
            eb.setColor(Color.GREEN);
            eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
            evt.getHook().sendMessageEmbeds(eb.build()).queue();
        }, (error) -> {
            // The reward could not be delivered: give the points back
            Logger.log(error);
            API.getInstance().addPoints(mem, bought.cost);
            evt.getHook().sendMessage("❌ I couldn't give you the reward, so you were not charged: " + error.getMessage()).queue();
        });
    }
    
    private static List<LeaderboardEntry> getLeaderboard() {
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return leaderboard;
        
        try (PreparedStatement stmt = conn.prepareStatement("SELECT `lastKnownName`, `points` FROM `points` ORDER BY `points` DESC LIMIT 10")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("lastKnownName");
                    int points = rs.getInt("points");
                    leaderboard.add(new LeaderboardEntry(name, points));
                }
            }
        } catch (SQLException e) {
            Logger.log(e);
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
