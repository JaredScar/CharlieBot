package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.PunishmentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ManagerUtils {
    @Getter
    private static HashMap<String, List<String>> rulesSelected = new HashMap<>();

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

    public static boolean handleRolePermissionsOnLockdown(TextChannel chan, List<PermissionOverride> permissionOverrides) {
        try {
            Connection conn = Main.getInstance().getSqlHelper().getConn();
            PreparedStatement prep = conn.prepareStatement("INSERT INTO `lockdown_roles` (`channel_id`, `role_id`, `permission`, `default`) VALUES (?, ?, ?, ?)");
            Collection<Permission> allows = new ArrayList<>();
            Collection<Permission> denies = new ArrayList<>();
            denies.add(Permission.MESSAGE_SEND);
            denies.add(Permission.MESSAGE_ADD_REACTION);
            denies.add(Permission.MESSAGE_SEND_IN_THREADS);
            List<String> disregardRoles = Main.getInstance().getConfig().getStringList("Bot.Commands.Lockdown.Requires_Roles");
            TextChannelManager manager = chan.getManager();
            for (PermissionOverride permO : permissionOverrides) {
                if (permO.getRole() == null) continue;
                long roleId = permO.getRole().getIdLong();
                if (disregardRoles.contains(permO.getRole().getId())) continue;
                prep.setLong(1, chan.getIdLong());
                prep.setLong(2, roleId);
                prep.setString(3, "MESSAGE_SEND");
                prep.setBoolean(4, permO.getAllowed().contains(Permission.MESSAGE_SEND));
                prep.execute();
                prep.setString(3, "MESSAGE_ADD_REACTION");
                prep.setBoolean(4, permO.getAllowed().contains(Permission.MESSAGE_ADD_REACTION));
                prep.execute();
                prep.setString(3, "MESSAGE_SEND_IN_THREADS");
                prep.setBoolean(4, permO.getAllowed().contains(Permission.MESSAGE_SEND_IN_THREADS));
                prep.execute();
                manager = manager.putRolePermissionOverride(roleId, allows, denies);
            }
            manager.queue();
        } catch (Exception ex) {
            Logger.log(ex);
            ex.printStackTrace();
            return false;
        }
        return true;
    }
    public static boolean handleRolePermissionsAfterLockdown(TextChannel chan) {
        @Data
        class LockdownRole {
            private long channelId;
            private long roleId;
            private String permission;
            private boolean defaultValue;
        }
        try {
            Connection conn = Main.getInstance().getSqlHelper().getConn();
            PreparedStatement prep = conn.prepareStatement("SELECT * FROM `lockdown_roles` WHERE `channel_id` = ?;");
            prep.setLong(1, chan.getIdLong());
            ResultSet rs = prep.executeQuery();
            List<LockdownRole> lockdownRoles = new ArrayList<>();
            while (rs.next()) {
                LockdownRole lr = new LockdownRole();
                long channelId = rs.getLong("channel_id");
                long roleId = rs.getLong("role_id");
                String permission = rs.getString("permission");
                boolean defaultValue = rs.getBoolean("default");
                lr.setChannelId(channelId);
                lr.setRoleId(roleId);
                lr.setPermission(permission);
                lr.setDefaultValue(defaultValue);
                lockdownRoles.add(lr);
            }
            TextChannelManager manager = chan.getManager();
            Map<Long, List<LockdownRole>> lockdownRolesByRoleId = lockdownRoles.stream()
                    .collect(Collectors.groupingBy(LockdownRole::getRoleId));
            for (long roleId : lockdownRolesByRoleId.keySet()) {
                List<LockdownRole> lrs = lockdownRolesByRoleId.get(roleId);
                Collection<Permission> allows = new ArrayList<>();
                Collection<Permission> denies = new ArrayList<>();
                for (LockdownRole lr : lrs) {
                    String perm = lr.getPermission();
                    boolean defaultValue = lr.isDefaultValue();
                    if (defaultValue)
                        allows.add(Permission.valueOf(perm));
                    else
                        denies.add(Permission.valueOf(perm));
                }
                manager = manager.putRolePermissionOverride(roleId, allows, denies);
            }
            manager.queue();
        } catch (Exception ex) {
            Logger.log(ex);
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean handleMuteMember(Member mem) {
        String muteRole = Main.getInstance().getConfig().getString("Bot.Commands.Mute_Role");
        Guild guild = mem.getGuild();
        Role roleMuted = guild.getRoleById(muteRole);
        if (roleMuted == null) return false;

        guild.addRoleToMember(mem, roleMuted).queue();
        return true;
    }
    public static boolean handleUnmuteMember(Member mem) {
        String muteRole = Main.getInstance().getConfig().getString("Bot.Commands.Mute_Role");
        Guild guild = mem.getGuild();
        Role roleMuted = guild.getRoleById(muteRole);
        if (roleMuted == null) return false;
        guild.removeRoleFromMember(mem, roleMuted).queue();
        return true;
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
        if (!pDatas.isEmpty()) {
            // It has actual PunishmentData
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(pName + "Remove" + "|" + opt.getAsMember().getId());
            for (PunishmentData pd : pDatas) {
                menuBuilder.addOption(pd.getPid() + " - " + pd.getReason(), String.valueOf(pd.getPid()));
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
        Modal.Builder builder = Modal.create(pName + "Remove" + "|" + beingPunished.getId() + "|" + pid, "Remove a " + pNameAdjusted + " punishment from history");
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
     * @param pid
     */
    public static void handleModalPunishmentRemoval(ModalInteractionEvent evt, int pid) {
        String modalId = evt.getModalId();
        String[] modalArgs = modalId.split("\\|");
        String removalType = modalArgs[0].replace("Remove", "");
        String punishedId = modalArgs[1];
        boolean failed = false;
        try {
            removePunishment(pid);
        } catch (SQLException e) {
            Logger.log(e);
            failed = true;
        }
        if (!failed) {
            // It was successful, we need to respond that it was good
            // TODO We also need to remove the actions associated to the punishment...
        }
    }

    public static void removePunishment(int pid) throws SQLException {
        String sql = "DELETE FROM `punishments` WHERE `pid` = ?";
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        PreparedStatement prep = conn.prepareStatement(sql);
        prep.setInt(1, pid);
        prep.execute();
    }

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
                builder.addOption(selectOption.getLabel(), selectOption.getValue(), selectOption.getDescription(), Emoji.fromUnicode("✅"));
                removals.add(selectOption);
            }
        }
        currentOptList.removeAll(removals);
        for (SelectOption selectOption : currentOptList) {
            builder.addOption(selectOption.getLabel(), selectOption.getValue(), selectOption.getDescription() == null ? "" : selectOption.getDescription());
        }
        EmbedBuilder eb = new EmbedBuilder();
        MessageEmbed embed = evt.getMessage().getEmbeds().get(0);
        eb.setTitle(embed.getTitle());
        if (embed.getFooter() != null)
            eb.setFooter(embed.getFooter().getText(), embed.getFooter().getIconUrl());
        if (embed.getThumbnail() != null)
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
