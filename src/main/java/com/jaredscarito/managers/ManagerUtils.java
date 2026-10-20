package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.ActionType;
import com.jaredscarito.models.PunishmentType;
import lombok.Getter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ManagerUtils {
    @Getter
    private static final HashMap<String, List<String>> rulesSelected = new HashMap<>();

    /** Select menu allows at most 25 options. */
    private static final int MAX_SELECT_OPTIONS = 25;

    /** Permissions a lockdown takes away from every (non-staff) role that has an override in the channel. */
    private static final List<Permission> LOCKDOWN_PERMISSIONS = List.of(
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_SEND_IN_THREADS
    );

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private static String orNone(String text) {
        return (text == null || text.isBlank()) ? "N/A" : text;
    }

    // ------------------------------------------------------------------------------------------
    // Lockdown
    // ------------------------------------------------------------------------------------------

    /** True when a lockdown has been applied to this channel and not yet lifted. */
    public static boolean isChannelLockedDown(TextChannel chan) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try (PreparedStatement prep = conn.prepareStatement("SELECT 1 FROM `lockdown_roles` WHERE `channel_id` = ? LIMIT 1")) {
            prep.setLong(1, chan.getIdLong());
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            Logger.log(ex);
        }
        return false;
    }

    /**
     * Denies the lockdown permissions for every role override in the channel (except staff roles) while
     * keeping each role's other permissions intact. The previous state of each permission is stored so
     * {@link #handleRolePermissionsAfterLockdown(TextChannel)} can put things back exactly as they were.
     */
    public static boolean handleRolePermissionsOnLockdown(TextChannel chan, List<PermissionOverride> permissionOverrides) {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        if (isChannelLockedDown(chan)) return false; // never overwrite the saved original state
        try (PreparedStatement prep = conn.prepareStatement("INSERT INTO `lockdown_roles` (`channel_id`, `role_id`, `permission`, `default`) VALUES (?, ?, ?, ?)")) {
            List<String> disregardRoles = Main.getInstance().getConfig().getStringList("Bot.Commands.Lockdown.Requires_Roles");
            if (disregardRoles == null) disregardRoles = Collections.emptyList();
            TextChannelManager manager = chan.getManager();
            boolean changed = false;
            for (PermissionOverride permO : permissionOverrides) {
                Role role = permO.getRole();
                if (role == null) continue;
                if (disregardRoles.contains(role.getId())) continue;
                EnumSet<Permission> allowed = permO.getAllowed();
                EnumSet<Permission> denied = permO.getDenied();
                Collection<Permission> newAllows = EnumSet.copyOf(allowed);
                Collection<Permission> newDenies = EnumSet.copyOf(denied);
                boolean overrideChanged = false;
                for (Permission managed : LOCKDOWN_PERMISSIONS) {
                    if (denied.contains(managed)) continue; // already denied, leave it alone
                    prep.setLong(1, chan.getIdLong());
                    prep.setLong(2, role.getIdLong());
                    prep.setString(3, managed.name());
                    // "default" = the permission was explicitly allowed before lockdown
                    prep.setBoolean(4, allowed.contains(managed));
                    prep.executeUpdate();
                    newAllows.remove(managed);
                    newDenies.add(managed);
                    overrideChanged = true;
                }
                if (overrideChanged) {
                    manager = manager.putRolePermissionOverride(role.getIdLong(), newAllows, newDenies);
                    changed = true;
                }
            }
            if (!changed) return false; // no applicable role overrides in this channel, nothing was locked
            manager.queue(null, Logger::log);
        } catch (Exception ex) {
            Logger.log(ex);
            return false;
        }
        return true;
    }

    /** Restores the permissions saved by {@link #handleRolePermissionsOnLockdown}, then forgets them. */
    public static boolean handleRolePermissionsAfterLockdown(TextChannel chan) {
        record LockdownRow(long roleId, String permission, boolean defaultValue) {}
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) return false;
        try {
            List<LockdownRow> rows = new ArrayList<>();
            try (PreparedStatement prep = conn.prepareStatement("SELECT `role_id`, `permission`, `default` FROM `lockdown_roles` WHERE `channel_id` = ?")) {
                prep.setLong(1, chan.getIdLong());
                try (ResultSet rs = prep.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new LockdownRow(rs.getLong("role_id"), rs.getString("permission"), rs.getBoolean("default")));
                    }
                }
            }
            if (rows.isEmpty()) return false; // channel was not locked down

            Map<Long, List<LockdownRow>> byRole = new LinkedHashMap<>();
            for (LockdownRow row : rows) {
                byRole.computeIfAbsent(row.roleId(), k -> new ArrayList<>()).add(row);
            }
            TextChannelManager manager = chan.getManager();
            boolean changed = false;
            for (Map.Entry<Long, List<LockdownRow>> entry : byRole.entrySet()) {
                Role role = chan.getGuild().getRoleById(entry.getKey());
                if (role == null) continue; // role was deleted while locked
                PermissionOverride current = chan.getPermissionOverride(role);
                EnumSet<Permission> allows = current != null ? EnumSet.copyOf(current.getAllowed()) : EnumSet.noneOf(Permission.class);
                EnumSet<Permission> denies = current != null ? EnumSet.copyOf(current.getDenied()) : EnumSet.noneOf(Permission.class);
                for (LockdownRow row : entry.getValue()) {
                    Permission perm = Permission.valueOf(row.permission());
                    allows.remove(perm);
                    denies.remove(perm);
                    if (row.defaultValue()) allows.add(perm); // otherwise it goes back to "inherit"
                }
                manager = manager.putRolePermissionOverride(role.getIdLong(), allows, denies);
                changed = true;
            }
            Runnable forget = () -> {
                try (PreparedStatement del = Main.getInstance().getSqlHelper().getConn().prepareStatement("DELETE FROM `lockdown_roles` WHERE `channel_id` = ?")) {
                    del.setLong(1, chan.getIdLong());
                    del.executeUpdate();
                } catch (Exception ex) {
                    Logger.log(ex);
                }
            };
            if (changed) {
                manager.queue(v -> forget.run(), Logger::log);
            } else {
                forget.run();
            }
        } catch (Exception ex) {
            Logger.log(ex);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Mute / blacklist roles
    // ------------------------------------------------------------------------------------------

    private static Role findConfiguredRole(Guild guild, String... paths) {
        for (String path : paths) {
            Object raw = Main.getInstance().getConfig().get(path);
            if (raw == null) continue;
            String id = String.valueOf(raw).trim();
            if (id.isEmpty()) continue;
            try {
                Role role = guild.getRoleById(id);
                if (role != null) return role;
            } catch (NumberFormatException ignored) {
                // not a valid snowflake, try next path
            }
        }
        return null;
    }

    /** The configured mute role, or null when it is missing/invalid. */
    public static Role getMuteRole(Guild guild) {
        return findConfiguredRole(guild, "Bot.Commands.Mute_Role", "Bot.Commands.Mute.Mute_Role");
    }

    /** The configured blacklist role, or null when it is missing/invalid. */
    public static Role getBlacklistRole(Guild guild) {
        return findConfiguredRole(guild, "Bot.Commands.Blacklist.Blacklist_Role", "Bot.Commands.Blacklist_Role");
    }

    public static boolean handleMuteMember(Member mem) {
        Guild guild = mem.getGuild();
        Role roleMuted = getMuteRole(guild);
        if (roleMuted == null) return false;
        guild.addRoleToMember(mem, roleMuted).queue(null, Logger::log);
        return true;
    }
    public static boolean handleUnmuteMember(Member mem) {
        Guild guild = mem.getGuild();
        Role roleMuted = getMuteRole(guild);
        if (roleMuted == null) return false;
        guild.removeRoleFromMember(mem, roleMuted).queue(null, Logger::log);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Removing a punishment from a member's history
    // ------------------------------------------------------------------------------------------

    /** Step 1 of /<punishment> remove: lists the member's punishments of this type in a select menu. */
    public static void handleRemovePunishment(SlashCommandInteractionEvent evt, PunishmentType punishmentType) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        OptionMapping opt = evt.getOption("member");
        if (opt == null) return;
        // The user, not the member: a banned user is no longer in the server, but their ban must still be removable
        User target = opt.getAsUser();
        String pName = punishmentType.name().toLowerCase();
        String pNameAdjusted = Character.toUpperCase(pName.charAt(0)) + pName.substring(1);
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error: Database connection unavailable", "Unable to connect to database...");
            return;
        }
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(pName + "Remove" + "|" + target.getId());
        int count = 0;
        try (PreparedStatement prep = conn.prepareStatement("SELECT `pid`, `reason` FROM `punishments` WHERE `punishment_type` = ? AND `discord_id` = ? " +
                "AND (`deleted` = 0 OR `deleted` IS NULL) ORDER BY `pid` DESC LIMIT " + MAX_SELECT_OPTIONS)) {
            prep.setString(1, punishmentType.name());
            prep.setLong(2, target.getIdLong());
            try (ResultSet res = prep.executeQuery()) {
                while (res.next()) {
                    int pid = res.getInt("pid");
                    String reason = res.getString("reason");
                    // select option labels may be at most 100 characters
                    menuBuilder.addOption(truncate(pid + " - " + orNone(reason), 100), String.valueOf(pid));
                    count++;
                }
            }
        } catch (SQLException e) {
            Logger.log(e);
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Something went wrong while loading the punishments...");
            return;
        }
        if (count == 0) {
            API.getInstance().sendErrorMessage(evt, mem, "Error: No punishments found", "This member does not have any of these types of punishments...");
            return;
        }
        evt.reply("Remove a " + pNameAdjusted + " punishment from " + target.getAsMention() + " history")
                .setEphemeral(true).addActionRow(menuBuilder.build()).queue();
    }

    /** Step 2: after the moderator picks a punishment, show its details with Remove / Cancel buttons. */
    public static void openModalWithPunishmentData(StringSelectInteractionEvent evt, int pid, long beingPunishedId, PunishmentType punishmentType) {
        String pName = punishmentType.name().toLowerCase();
        String pNameAdjusted = Character.toUpperCase(pName.charAt(0)) + pName.substring(1);
        Member moderator = evt.getMember();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) {
            API.getInstance().sendErrorMessage(evt, moderator, "Error: Database connection unavailable", "Unable to connect to database...");
            return;
        }
        EmbedBuilder eb = null;
        try (PreparedStatement prep = conn.prepareStatement("SELECT `datetime`, `ruleIds_broken`, `reason`, `punishment_length`, `punished_by_lastKnownName` " +
                "FROM `punishments` WHERE `punishment_type` = ? AND `pid` = ? AND `discord_id` = ?")) {
            prep.setString(1, punishmentType.name());
            prep.setInt(2, pid);
            prep.setLong(3, beingPunishedId);
            try (ResultSet res = prep.executeQuery()) {
                if (res.next()) {
                    eb = new EmbedBuilder();
                    eb.setTitle("Remove " + pNameAdjusted + " #" + pid + " from history?");
                    eb.setColor(Color.ORANGE);
                    eb.addField("Member", "<@" + beingPunishedId + ">", true);
                    eb.addField("Punished by", truncate(orNone(res.getString("punished_by_lastKnownName")), 1000), true);
                    eb.addField("Date", orNone(res.getString("datetime")), true);
                    eb.addField("Rules broken", truncate(orNone(res.getString("ruleIds_broken")), 1000), false);
                    eb.addField("Length", orNone(res.getString("punishment_length")), true);
                    eb.addField("Reason", truncate(orNone(res.getString("reason")), 1000), false);
                }
            }
        } catch (SQLException e) {
            Logger.log(e);
        }
        if (eb == null) {
            API.getInstance().sendErrorMessage(evt, moderator, "Error: No punishments found", "This punishment no longer exists...");
            return;
        }
        // tell the moderator up front when removing the entry also lifts the punishment
        String effect = switch (punishmentType) {
            case BAN -> "Removing this entry will also **unban** the member (unless a newer ban is still on record).";
            case MUTE -> "Removing this entry will also **unmute** the member (unless a newer mute is still on record).";
            case BLACKLIST -> "Removing this entry will also **lift the blacklist** and give the member their roles back (unless a newer blacklist is still on record).";
            default -> null;
        };
        if (effect != null) eb.setDescription(effect);
        Button confirm = Button.danger("punishRemoveConfirm|" + pid + "|" + beingPunishedId + "|" + punishmentType.name(), "Remove");
        Button cancel = Button.secondary("punishRemoveCancel", "Cancel");
        evt.replyEmbeds(eb.build()).addActionRow(cancel, confirm).setEphemeral(true).queue();
    }

    /**
     * Step 3: the moderator pressed "Remove". The entry is deleted from the history and, when it was the
     * member's latest one, the punishment itself is lifted too (unban / unmute / restore blacklisted roles).
     */
    public static void handleButtonPunishmentRemoval(ButtonInteractionEvent evt) {
        String[] args = evt.getComponentId().split("\\|");
        if (args.length < 4) return;
        int pid;
        long discordId;
        PunishmentType type;
        try {
            pid = Integer.parseInt(args[1]);
            discordId = Long.parseLong(args[2]);
            type = PunishmentType.valueOf(args[3]);
        } catch (IllegalArgumentException ex) {
            return;
        }
        boolean removed;
        boolean liftEffect = false;
        try {
            removed = removePunishment(pid, discordId, type);
            // Only lift the punishment itself when this was the member's latest entry of that type;
            // removing an old entry must not free someone who has a newer ban/mute/blacklist on record.
            if (removed) liftEffect = !hasNewerPunishment(pid, discordId, type);
        } catch (SQLException e) {
            Logger.log(e);
            evt.editMessage("❌ Failed to remove punishment #" + pid + " from history.")
                    .setEmbeds(Collections.emptyList()).setComponents(Collections.emptyList()).queue();
            return;
        }
        if (!removed) {
            evt.editMessage("❌ Punishment #" + pid + " was not found (it may already have been removed).")
                    .setEmbeds(Collections.emptyList()).setComponents(Collections.emptyList()).queue();
            return;
        }

        logRemoval(evt, pid, discordId, type);

        String message = "✅ Punishment #" + pid + " has been removed from history.";
        Guild guild = evt.getGuild();
        if (!liftEffect || guild == null || type == PunishmentType.KICK || type == PunishmentType.WARN) {
            evt.editMessage(message).setEmbeds(Collections.emptyList()).setComponents(Collections.emptyList()).queue();
            return;
        }
        // Undoing the effect is a Discord request that can take a moment: acknowledge first, then fill in the result
        evt.editMessage("⏳ Removing punishment #" + pid + "...").setEmbeds(Collections.emptyList()).setComponents(Collections.emptyList())
                .queue((hook) -> PunishmentEffects.undo(guild, discordId, type,
                        (note) -> hook.editOriginal(message + note).queue(null, Logger::log)));
    }

    /** True when the member still has a newer, non-deleted punishment of this type than {@code pid}. */
    private static boolean hasNewerPunishment(int pid, long discordId, PunishmentType type) throws SQLException {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) {
            throw new SQLException("Database connection unavailable");
        }
        try (PreparedStatement prep = conn.prepareStatement("SELECT 1 FROM `punishments` WHERE `discord_id` = ? AND `punishment_type` = ? " +
                "AND `pid` > ? AND (`deleted` = 0 OR `deleted` IS NULL) LIMIT 1")) {
            prep.setLong(1, discordId);
            prep.setString(2, type.name());
            prep.setInt(3, pid);
            try (ResultSet rs = prep.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void logRemoval(ButtonInteractionEvent evt, int pid, long discordId, PunishmentType type) {
        ActionType actionType = switch (type) {
            case BAN -> ActionType.BAN_REMOVE;
            case MUTE -> ActionType.MUTE_DELETE;
            case BLACKLIST -> ActionType.BLACKLIST_REMOVE;
            case WARN -> ActionType.WARN_REMOVE;
            default -> null; // kicks have no log type
        };
        Member moderator = evt.getMember();
        if (actionType == null || moderator == null) return;
        // the target may no longer be in the server (banned), so look the user up instead of the member
        evt.getJDA().retrieveUserById(discordId).queue(
                (user) -> Logger.log(actionType, moderator, user, "History entry #" + pid + " removed"),
                (error) -> {});
    }

    public static boolean removePunishment(int pid, long discordId, PunishmentType type) throws SQLException {
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) {
            throw new SQLException("Database connection unavailable");
        }
        try (PreparedStatement prep = conn.prepareStatement("DELETE FROM `punishments` WHERE `pid` = ? AND `discord_id` = ? AND `punishment_type` = ?")) {
            prep.setInt(1, pid);
            prep.setLong(2, discordId);
            prep.setString(3, type.name());
            return prep.executeUpdate() > 0;
        }
    }

    /**
     * TODO When reverting punishments, we need to make sure we also remove the actions associated to the punishment if they exist...
     * If they provide punishmentType as null, we should assume all punishments will be removed from the user's history
     * @param evt
     * @param punishmentType
     */
    public static void clearPunishments(SlashCommandInteractionEvent evt, PunishmentType punishmentType) {
        Member mem = evt.getMember();
        if (mem == null || mem.getUser().isBot()) return;
        // Note: most commands in this codebase expect the target member under option name "member".
        OptionMapping opt = evt.getOption("member");
        if (opt == null || opt.getAsMember() == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error: Missing required parameter", "Please specify a `member` option.");
            return;
        }

        Member target = opt.getAsMember();
        Connection conn = Main.getInstance().getSqlHelper().getConn();
        if (conn == null) {
            API.getInstance().sendErrorMessage(evt, mem, "Error: Database connection unavailable", "Unable to connect to database...");
            return;
        }

        try {
            if (punishmentType == null) {
                try (PreparedStatement prep = conn.prepareStatement("DELETE FROM `punishments` WHERE `discord_id` = ?")) {
                    prep.setLong(1, target.getIdLong());
                    prep.execute();
                }
            } else {
                try (PreparedStatement prep = conn.prepareStatement(
                        "DELETE FROM `punishments` WHERE `punishment_type` = ? AND `discord_id` = ?")) {
                    prep.setString(1, punishmentType.name().toUpperCase());
                    prep.setLong(2, target.getIdLong());
                    prep.execute();
                }
            }
        } catch (SQLException e) {
            Logger.log(e);
            API.getInstance().sendErrorMessage(evt, mem, "Error", "Failed to clear punishments.");
            return;
        }

        String typeLabel = punishmentType == null ? "all punishments" : (punishmentType.name().toLowerCase() + " punishments");
        evt.reply("✅ Cleared " + typeLabel + " for " + target.getAsMention() + ".").setEphemeral(true).queue();
    }

    // ------------------------------------------------------------------------------------------
    // Rule selection flow shared by warn / mute / kick / blacklist / ban
    // ------------------------------------------------------------------------------------------

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
        List<String> existing = rulesSelected.get(ruleSelectId);
        if (existing != null) {
            for (String rule : rulesBroken) {
                if (existing.contains(rule)) {
                    existing.remove(rule);
                } else {
                    existing.add(rule);
                }
            }
        } else {
            rulesSelected.put(ruleSelectId, new ArrayList<>(rulesBroken));
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
        if (embed.getColor() != null)
            eb.setColor(embed.getColor());
        if (embed.getFooter() != null)
            eb.setFooter(embed.getFooter().getText(), embed.getFooter().getIconUrl());
        if (embed.getThumbnail() != null)
            eb.setThumbnail(embed.getThumbnail().getUrl());
        // an embed field can't be empty (all rules were toggled off again)
        eb.addField("Rules broken:", rulesBrokenList.isEmpty() ? "None selected" : String.join(", ", rulesBrokenList), false);
        evt.editSelectMenu(builder.build()).and(evt.getHook().editOriginalEmbeds((eb.build()))).queue();
    }

    public static void handleCommandInteraction(SlashCommandInteractionEvent evt, String punishmentType, String punishName, Color color) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        OptionMapping opt = evt.getOption("member");
        if (opt == null) return;
        Member target = opt.getAsMember();
        if (target == null) {
            evt.reply("❌ That user is not a member of this server.").setEphemeral(true).queue();
            return;
        }
        if (target.getIdLong() == mem.getIdLong()) {
            evt.reply("❌ You cannot do that to yourself.").setEphemeral(true).queue();
            return;
        }
        if (target.getUser().isBot()) {
            evt.reply("❌ You cannot do that to a bot.").setEphemeral(true).queue();
            return;
        }
        if (!mem.canInteract(target)) {
            evt.reply("❌ You cannot do that to a member whose highest role is equal to or above yours.").setEphemeral(true).queue();
            return;
        }
        User user = target.getUser();
        TreeMap<String, String> ruleList = API.getInstance().getRules();
        if (ruleList.isEmpty()) {
            evt.reply("❌ No rules are configured yet. Add some with `/rules add` first.").setEphemeral(true).queue();
            return;
        }
        // start every flow with a clean slate so an old, abandoned selection can't leak into this one
        rulesSelected.remove(punishmentType + "|" + user.getId() + "|" + mem.getUser().getId());

        StringSelectMenu.Builder builder = StringSelectMenu.create(punishmentType + "RuleSelect|" + user.getId() + "|"
                + mem.getUser().getId()).setMinValues(1);
        int added = 0;
        for (String ruleId : ruleList.keySet()) {
            if (added >= MAX_SELECT_OPTIONS) break; // Discord select menus hold at most 25 options
            builder.addOption(ruleId, ruleId, truncate(ruleList.get(ruleId), 100));
            added++;
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
        if (ruleList.size() > MAX_SELECT_OPTIONS) {
            eb.setDescription("Only the first " + MAX_SELECT_OPTIONS + " rules can be selected.");
        }
        evt.replyEmbeds(eb.build()).addActionRow(ruleSelect).addActionRow(cancelButton, submitButton).setEphemeral(true).queue();
    }
}
