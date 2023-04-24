package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class ManagerUtils {
    private static HashMap<String, List<String>> rulesSelected = new HashMap<>();

    public static HashMap<String, List<String>> getRulesSelected() {
        return rulesSelected;
    }

    public static TimeUnit getTimeUnitFromString(String str) {
        switch (str.toLowerCase()) {
            case "second":
                return TimeUnit.SECONDS;
            case "minute":
                return TimeUnit.MINUTES;
            case "hour":
                return TimeUnit.HOURS;
            case "day":
                return TimeUnit.DAYS;
        }
        return TimeUnit.DAYS;
    }

    public static void handleStringSelectMenu(StringSelectInteractionEvent evt, String componentId, String punishmentType) {
        List<SelectOption> optionsSelected = evt.getSelectedOptions();
        StringSelectMenu.Builder builder = StringSelectMenu.create(evt.getComponentId());
        List<String> rulesBroken = new ArrayList<>();
        for (SelectOption opt : optionsSelected) {
            rulesBroken.add(opt.getValue());
        }
        String ruleSelectId = evt.getComponentId().replace(componentId, punishmentType);
        List<SelectOption> currentOptList = new ArrayList<>(evt.getSelectMenu().getOptions());
        List<SelectOption> removals = new ArrayList<>();
        if (rulesSelected.containsKey(ruleSelectId)) {
            List<String> rulesBrokenList = rulesSelected.get(ruleSelectId);
            for (String rule : rulesBroken) {
                if (rulesBrokenList.contains(rule)) {
                    rulesBrokenList.remove(rule);
                } else {
                    rulesBrokenList.add(rule);
                }
            }
            rulesSelected.put(ruleSelectId, rulesBrokenList);
        } else {
            rulesSelected.put(ruleSelectId, rulesBroken);
        }

        List<String> rulesBrokenList = rulesSelected.get(ruleSelectId);
        currentOptList.sort((s1, s2) -> {
            if (s1.getEmoji() != null && !optionsSelected.contains(s1)) return 1;
            if (s2.getEmoji() != null && !optionsSelected.contains(s2)) return -1;
            String firstRuleIdentifierArg0 = s1.getValue().split("\\.")[0];
            String firstRuleIdentifierArg1 = s1.getValue().split("\\.")[1];
            String secondRuleIdentifierArg0 = s2.getValue().split("\\.")[0];
            String secondRuleIdentifierArg1 = s2.getValue().split("\\.")[1];
            int firstRuleArg0 = Integer.parseInt(firstRuleIdentifierArg0);
            int firstRuleArg1 = Integer.parseInt(firstRuleIdentifierArg1);
            int secondRuleArg0 = Integer.parseInt(secondRuleIdentifierArg0);
            int secondRuleArg1 = Integer.parseInt(secondRuleIdentifierArg1);
            if (firstRuleArg0 > secondRuleArg0) return 1;
            if (secondRuleArg0 > firstRuleArg0) return -1;
            if (firstRuleArg1 > secondRuleArg1) return 1;
            if (secondRuleArg1 > firstRuleArg1) return -1;
            return 0;
        });
        for (SelectOption selectOption : currentOptList) {
            if (rulesBrokenList.contains(selectOption.getValue())) {
                builder.addOption(selectOption.getLabel(), selectOption.getValue(), Emoji.fromUnicode("✅"));
                removals.add(selectOption);
            }
        }
        currentOptList.removeAll(removals);
        for (SelectOption selectOption : currentOptList) {
            builder.addOption(selectOption.getLabel(), selectOption.getValue());
        }
        EmbedBuilder eb = new EmbedBuilder();
        MessageEmbed embed = evt.getMessage().getEmbeds().get(0);
        eb.setTitle(embed.getTitle());
        eb.setFooter(embed.getFooter().getText(), embed.getFooter().getIconUrl());
        eb.setThumbnail(embed.getThumbnail().getUrl());
        eb.addField("Rules broken:", String.join(", ", rulesBrokenList), false);
        evt.editSelectMenu(builder.build()).and(evt.getHook().editOriginalEmbeds((eb.build()))).queue();
    }

    public static void handleCommandInteraction(SlashCommandInteractionEvent evt, String punishmentType, String punishName, Color color) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        OptionMapping opt = evt.getOption("member");
        if (opt == null) return;
        if (opt.getAsMember() == null) return;
        User user = opt.getAsMember().getUser();
        StringSelectMenu.Builder builder = StringSelectMenu.create(punishmentType + "RuleSelect|" + user.getId() + "|"
                + mem.getUser().getId()).setMinValues(1);
        TreeMap<String, String> ruleList = API.getInstance().getRules();
        for (String ruleId : ruleList.keySet()) {
            String rule = ruleList.get(ruleId);
            if (rule.length() > 100) {
                rule = rule.substring(0, 100);
            }
            builder.addOption(ruleId, ruleId, rule);
        }
        StringSelectMenu ruleSelect = builder.build();
        Button submitButton = Button.success(punishmentType + "RuleSelectConfirm|" + user.getId() + "|"
                + mem.getUser().getId(), "Submit");
        Button cancelButton = Button.danger(punishmentType + "RuleSelectDeny|" + user.getId() + "|"
                + mem.getUser().getId(), "Cancel");
        EmbedBuilder eb = new EmbedBuilder();
        eb.setFooter(mem.getUser().getName() + "#" + mem.getUser().getDiscriminator(), mem.getUser().getAvatarUrl());
        eb.setThumbnail(user.getAvatarUrl());
        eb.setTitle(punishName + " " + user.getName() + "#" + user.getDiscriminator());
        eb.setColor(color);
        evt.replyEmbeds(eb.build()).addActionRow(ruleSelect).addActionRow(cancelButton, submitButton).setEphemeral(true).queue();
    }
}
