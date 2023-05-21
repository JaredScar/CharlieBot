package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.PunishmentType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
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

    @AllArgsConstructor
    private static class PunishmentData {
        @Getter
        @Setter
        private int pid;
        @Getter
        @Setter
        private String datetime;
        @Getter
        @Setter
        private String rulesBroken;
        @Getter
        @Setter
        private String punishmentLength;
        @Getter
        @Setter
        private String punished_by_lastKnownName;
        @Getter
        @Setter
        private String reason;
    }
    /**
     * @param evt
     * @param punishmentType
     */
    public static void handleRemovePunishment(SlashCommandInteractionEvent evt, PunishmentType punishmentType) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        OptionMapping opt = evt.getOption("member");
        if (opt == null) return;
        if (opt.getAsMember() == null) return;
        User user = opt.getAsMember().getUser();
        String pName = punishmentType.name().toLowerCase();
        String pNameAdjusted = pName.toUpperCase().charAt(0) + pName.substring(1);
        HashMap<Member, List<PunishmentData>> punishmentData = new HashMap<>();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        try {
            PreparedStatement prep = conn.prepareStatement("SELECT `pid`, `datetime`, `ruleIds_broken`, `reason`, `punishment_length`, `punished_by_lastKnownName` FROM `punishments` WHERE `punishment_type` = ? AND `discord_id` = ?");
            prep.setString(1, pName.toUpperCase());
            prep.setLong(2, opt.getAsMember().getIdLong());
            prep.execute();
            ResultSet res = prep.getResultSet();
            while (res.next()) {
                int pid = res.getInt("pid");
                String datetime = res.getString("datetime");
                String rulesBroken = res.getString("ruleIds_broken");
                String punishmentLength = res.getString("punishment_length");
                String punished_by_lastKnownName = res.getString("punished_by_lastKnownName");
                String reason = res.getString("reason");
                PunishmentData pData = new PunishmentData(pid, datetime, rulesBroken, punishmentLength, punished_by_lastKnownName, reason);
                punishmentData.computeIfAbsent(opt.getAsMember(), v -> new ArrayList<>()).add(pData);
            }
        } catch (SQLException e) {
            Logger.log(e);
            e.printStackTrace();
        }
        List<PunishmentData> pDatas = punishmentData.get(opt.getAsMember());
        if (pDatas.size() > 0) {
            // It has actual PunishmentData
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(pName + "Remove" + "|" + opt.getAsMember().getId());
            for (PunishmentData pd : pDatas) {
                menuBuilder.addOption(pd.getPid() + " - " + pd.getReason(), pd.getPid() + "");
            }
            StringSelectMenu menu = menuBuilder.build();
            evt.reply("Remove a " + pNameAdjusted + " punishment from "
                    + opt.getAsMember().getAsMention() + " history").setEphemeral(true).addActionRow(menu).queue();
            punishmentData.remove(opt.getAsMember());
        } else {
            // No PunishmentData, notify them...
            API.getInstance().sendErrorMessage(evt, opt.getAsMember(), "Error: No punishments found", "This member does not have any of these types of punishments...");
        }
    }
    public static void openModalWithPunishmentData(StringSelectInteractionEvent evt, int pid, Member beingPunished, PunishmentType punishmentType) {
        String pName = punishmentType.name().toLowerCase();
        String pNameAdjusted = pName.toUpperCase().charAt(0) + pName.substring(1);
        Modal.Builder builder = Modal.create(pName + "Remove" + "|" + beingPunished.getId(), "Remove a " + pNameAdjusted + " punishment from history");
        HashMap<Member, List<PunishmentData>> punishmentData = new HashMap<>();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        try {
            PreparedStatement prep = conn.prepareStatement("SELECT `pid`, `datetime`, `ruleIds_broken`, `reason`, `punishment_length`, `punished_by_lastKnownName` FROM `punishments` WHERE `punishment_type` = ? AND `pid` = ?");
            prep.setString(1, pName.toUpperCase());
            prep.setInt(2, pid);
            prep.execute();
            ResultSet res = prep.getResultSet();
            while (res.next()) {
                String datetime = res.getString("datetime");
                String rulesBroken = res.getString("ruleIds_broken");
                String punishmentLength = res.getString("punishment_length");
                String punished_by_lastKnownName = res.getString("punished_by_lastKnownName");
                String reason = res.getString("reason");
                PunishmentData pData = new PunishmentData(pid, datetime, rulesBroken, punishmentLength, punished_by_lastKnownName, reason);
                punishmentData.computeIfAbsent(beingPunished, v -> new ArrayList<>()).add(pData);
            }
        } catch (SQLException e) {
            Logger.log(e);
            e.printStackTrace();
        }
        List<PunishmentData> pDatas = punishmentData.get(beingPunished);
        Optional<PunishmentData> optionalPd = pDatas.stream().filter((pd) -> pd.getPid() == pid).findFirst();
        if (optionalPd.isPresent()) {
            // It has actual PunishmentData
            PunishmentData pData = optionalPd.get();
            TextInput datetime = TextInput.create("datetime", "Datetime", TextInputStyle.SHORT).setValue(pData.getDatetime()).build();
            TextInput rulesBroken = TextInput.create("rulesBroken", "Rules Broke", TextInputStyle.PARAGRAPH).setValue(pData.getRulesBroken()).build();
            TextInput punishmentLength = TextInput.create("punishmentLength", "Punishment Length", TextInputStyle.SHORT).setValue(pData.getPunishmentLength()).build();
            TextInput punished_by_lastKnownName = TextInput.create("punishedBy", "Punished By", TextInputStyle.SHORT).setValue(pData.getPunished_by_lastKnownName()).build();
            TextInput reason = TextInput.create("", "", TextInputStyle.PARAGRAPH).build();
            builder.addActionRow(datetime.asDisabled(), rulesBroken.asDisabled(), punishmentLength.asDisabled(), punished_by_lastKnownName.asDisabled(), reason.asDisabled());
            evt.replyModal(builder.build()).queue();
        } else {
            // No PunishmentData, notify them...
            API.getInstance().sendErrorMessage(evt, beingPunished, "Error: No punishments found", "This member does not have any of these types of punishments...");
        }
    }

    /**
     * TODO When reverting punishments, we need to make sure we also remove the actions associated to the punishment if they exist...
     * @param evt
     */
    public static void handleModalPunishmentRemoval(ModalInteractionEvent evt, int pid) {}

    /**
     * TODO When reverting punishments, we need to make sure we also remove the actions associated to the punishment if they exist...
     * If they provide punishmentType as null, we should assume all punishments will be removed from the user's history
     * @param evt
     * @param punishmentType
     */
    public static void clearPunishments(SlashCommandInteractionEvent evt, PunishmentType punishmentType) {}

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
