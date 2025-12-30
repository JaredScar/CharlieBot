package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

public class RuleCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null || mem.getUser().isBot()) return;
        
        String subcommand = evt.getSubcommandName();
        if (subcommand == null) {
            // Show all rules
            showRules(evt, mem);
            return;
        }
        
        switch (subcommand.toLowerCase()) {
            case "add" -> addRule(evt, mem);
            case "edit" -> editRule(evt, mem);
            case "remove" -> removeRule(evt, mem);
        }
    }
    
    private static void showRules(SlashCommandInteractionEvent evt, Member mem) {
        TreeMap<String, String> rules = API.getInstance().getRules();
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("📋 Server Rules");
        eb.setDescription("Here are the current server rules:");
        eb.setColor(Color.BLUE);
        eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
        
        StringBuilder rulesText = new StringBuilder();
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            rulesText.append("**").append(entry.getKey()).append("** - ").append(entry.getValue()).append("\n\n");
        }
        
        if (rulesText.length() > 0) {
            eb.setDescription(rulesText.toString());
        } else {
            eb.setDescription("No rules have been set yet. Use `/rules add` to add the first rule!");
        }
        
        evt.replyEmbeds(eb.build()).queue();
    }
    
    private static void addRule(SlashCommandInteractionEvent evt, Member mem) {
        OptionMapping sectionOpt = evt.getOption("section");
        OptionMapping ruleOpt = evt.getOption("rule");
        
        if (sectionOpt == null || ruleOpt == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Missing required parameters!");
            return;
        }
        
        String section = sectionOpt.getAsString();
        String rule = ruleOpt.getAsString();
        
        // Add rule to config
        String configPath = "Rules.Sections." + section + "." + getNextRuleId(section);
        Main.getInstance().getConfig().set(configPath, rule);
        
        if (Main.getInstance().saveConfig()) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("✅ Rule Added");
            eb.setDescription("Successfully added rule to section **" + section + "**:\n" + rule);
            eb.setColor(Color.GREEN);
            eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
            
            evt.replyEmbeds(eb.build()).queue();
        } else {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Failed to save rule to configuration!");
        }
    }
    
    private static void editRule(SlashCommandInteractionEvent evt, Member mem) {
        OptionMapping ruleIdOpt = evt.getOption("rule_id");
        OptionMapping newRuleOpt = evt.getOption("new_rule");
        
        if (ruleIdOpt == null || newRuleOpt == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Missing required parameters!");
            return;
        }
        
        String ruleId = ruleIdOpt.getAsString();
        String newRule = newRuleOpt.getAsString();
        
        // Find and update the rule
        String rulePath = findRulePath(ruleId);
        if (rulePath != null) {
            Main.getInstance().getConfig().set(rulePath, newRule);
            
            if (Main.getInstance().saveConfig()) {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("✅ Rule Updated");
                eb.setDescription("Successfully updated rule **" + ruleId + "**:\n" + newRule);
                eb.setColor(Color.GREEN);
                eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
                
                evt.replyEmbeds(eb.build()).queue();
            } else {
                API.getInstance().sendErrorMessage(evt, mem, "Error", "Failed to save rule to configuration!");
            }
        } else {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Rule ID not found!");
        }
    }
    
    private static void removeRule(SlashCommandInteractionEvent evt, Member mem) {
        OptionMapping ruleIdOpt = evt.getOption("rule_id");
        
        if (ruleIdOpt == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Missing required parameters!");
            return;
        }
        
        String ruleId = ruleIdOpt.getAsString();
        
        // Find and remove the rule
        String rulePath = findRulePath(ruleId);
        if (rulePath != null) {
            Main.getInstance().getConfig().set(rulePath, null);
            
            if (Main.getInstance().saveConfig()) {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("✅ Rule Removed");
                eb.setDescription("Successfully removed rule **" + ruleId + "**");
                eb.setColor(Color.GREEN);
                eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
                
                evt.replyEmbeds(eb.build()).queue();
            } else {
                API.getInstance().sendErrorMessage(evt, mem, "Error", "Failed to save configuration!");
            }
        } else {
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Rule ID not found!");
        }
    }
    
    private static String getNextRuleId(String section) {
        // Get the next available rule ID for the section
        int maxId = 0;
        try {
            var sectionConfig = Main.getInstance().getConfig().getConfigurationSection("Rules.Sections." + section);
            if (sectionConfig != null) {
                for (String key : sectionConfig.getKeys()) {
                    try {
                        int id = Integer.parseInt(key);
                        maxId = Math.max(maxId, id);
                    } catch (NumberFormatException ignored) {
                        // Skip non-numeric keys
                    }
                }
            }
        } catch (Exception ignored) {
            // If there's an error, start from 1
        }
        return String.valueOf(maxId + 1);
    }
    
    private static String findRulePath(String ruleId) {
        // Search through all sections to find the rule
        try {
            var sectionsConfig = Main.getInstance().getConfig().getConfigurationSection("Rules.Sections");
            if (sectionsConfig != null) {
                for (String section : sectionsConfig.getKeys()) {
                    var sectionConfig = sectionsConfig.getConfigurationSection(section);
                    if (sectionConfig != null) {
                        for (String key : sectionConfig.getKeys()) {
                            if (key.equals(ruleId)) {
                                return "Rules.Sections." + section + "." + key;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Return null if there's an error
        }
        return null;
    }
}
