package com.jaredscarito.managers;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.ActionType;
import com.jaredscarito.sql.SQLHelper;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdatePendingEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves a member's roles when they leave and gives them back when they rejoin.
 * The mute role and the blacklist role are not saved. A blacklisted member gets the blacklist
 * role again instead of the roles the blacklist took away (those stay in the blacklist tables).
 * A muted member gets their other roles back and the mute role on top.
 */
public class MemberRoleManager extends ListenerAdapter {
    public static void ensureSchema() {
        Connection conn = conn();
        if (conn == null) return;
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS `member_roles` (" +
                    "`discord_id` BIGINT NOT NULL, " +
                    "`role_id` BIGINT NOT NULL, " +
                    "PRIMARY KEY (`discord_id`, `role_id`))");
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    public static boolean isBlacklisted(long discordId) {
        Connection conn = conn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM `blacklists` WHERE `discord_id` = ? LIMIT 1")) {
            stmt.setLong(1, discordId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Logger.log(e);
            return false;
        }
    }

    /** True when a mute is still active. An expired row that the scheduler has not cleared yet does not count. */
    public static boolean isMuted(long discordId) {
        Connection conn = conn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT `expires_at` FROM `mutes` WHERE `discord_id` = ?")) {
            stmt.setLong(1, discordId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                long expires = rs.getLong("expires_at");
                boolean permanent = rs.wasNull();
                return permanent || expires > System.currentTimeMillis();
            }
        } catch (SQLException e) {
            Logger.log(e);
            return false;
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent evt) {
        if (!ourGuild(evt.getGuild())) return;
        Member member = evt.getMember();
        if (member == null || member.getUser().isBot()) return;
        saveRoles(member);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent evt) {
        if (!ourGuild(evt.getGuild())) return;
        if (evt.getUser().isBot()) return;
        // Membership screening rejects role edits until the member finishes it.
        if (evt.getMember().isPending()) return;
        restore(evt.getMember());
    }

    @Override
    public void onGuildMemberUpdatePending(GuildMemberUpdatePendingEvent evt) {
        if (!ourGuild(evt.getGuild())) return;
        if (evt.getNewPending() || evt.getUser().isBot()) return;
        restore(evt.getMember());
    }

    private static void saveRoles(Member member) {
        Connection conn = conn();
        if (conn == null) return;
        long discordId = member.getIdLong();
        Guild guild = member.getGuild();
        List<Long> roleIds = new ArrayList<>();
        for (Role role : member.getRoles()) {
            if (role.isManaged() || role.isPublicRole() || isMuteOrBlacklist(guild, role)) continue;
            roleIds.add(role.getIdLong());
        }
        try {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM `member_roles` WHERE `discord_id` = ?")) {
                del.setLong(1, discordId);
                del.executeUpdate();
            }
            if (roleIds.isEmpty()) return;
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO `member_roles` (`discord_id`, `role_id`) VALUES (?, ?)")) {
                for (long roleId : roleIds) {
                    ins.setLong(1, discordId);
                    ins.setLong(2, roleId);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    private static void restore(Member member) {
        Guild guild = member.getGuild();
        Member self = guild.getSelfMember();
        if (!self.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_ROLES)) {
            Logger.log(new IllegalStateException("Cannot restore roles for " + member.getId() + ": the bot lacks Manage Roles"));
            return;
        }
        long discordId = member.getIdLong();
        boolean blacklisted = isBlacklisted(discordId);
        boolean muted = isMuted(discordId);
        List<Role> toAdd = new ArrayList<>();
        if (blacklisted) {
            // The roles a blacklist removed live in the blacklist tables and come back when staff lift it.
            deleteSaved(discordId);
            Role blacklist = ManagerUtils.getBlacklistRole(guild);
            if (blacklist != null && self.canInteract(blacklist)) toAdd.add(blacklist);
            else if (blacklist == null) Logger.log(new IllegalStateException("Blacklisted member " + discordId + " rejoined, but no blacklist role is configured"));
        } else {
            for (long roleId : loadSaved(discordId)) {
                Role role = guild.getRoleById(roleId);
                if (role == null || role.isManaged() || role.isPublicRole() || isMuteOrBlacklist(guild, role)) continue;
                if (!self.canInteract(role) || member.getRoles().contains(role)) continue;
                toAdd.add(role);
            }
        }
        if (muted) {
            Role mute = ManagerUtils.getMuteRole(guild);
            if (mute != null && self.canInteract(mute) && !member.getRoles().contains(mute) && !toAdd.contains(mute)) toAdd.add(mute);
            else if (mute == null) Logger.log(new IllegalStateException("Muted member " + discordId + " rejoined, but no mute role is configured"));
        }
        if (toAdd.isEmpty()) {
            if (!blacklisted) deleteSaved(discordId);
            return;
        }
        String reason = blacklisted
                ? "Reapplied the blacklist role on rejoin"
                : (muted ? "Restored roles and the mute role on rejoin" : "Restored roles on rejoin");
        guild.modifyMemberRoles(member, toAdd, List.of()).queue(v -> {
            if (!blacklisted) deleteSaved(discordId);
            Logger.log(ActionType.ROLE_RESTORE, self, member, reason);
        }, error -> Logger.log(error));
    }

    private static List<Long> loadSaved(long discordId) {
        List<Long> ids = new ArrayList<>();
        Connection conn = conn();
        if (conn == null) return ids;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT `role_id` FROM `member_roles` WHERE `discord_id` = ?")) {
            stmt.setLong(1, discordId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong("role_id"));
            }
        } catch (SQLException e) {
            Logger.log(e);
        }
        return ids;
    }

    private static void deleteSaved(long discordId) {
        Connection conn = conn();
        if (conn == null) return;
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM `member_roles` WHERE `discord_id` = ?")) {
            stmt.setLong(1, discordId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    private static boolean isMuteOrBlacklist(Guild guild, Role role) {
        Role mute = ManagerUtils.getMuteRole(guild);
        Role blacklist = ManagerUtils.getBlacklistRole(guild);
        if (mute != null && mute.getIdLong() == role.getIdLong()) return true;
        return blacklist != null && blacklist.getIdLong() == role.getIdLong();
    }

    private static boolean ourGuild(Guild guild) {
        String id = Main.getInstance().getConfig().getString("Bot.Guild");
        return id != null && guild.getId().equals(id.trim());
    }

    private static Connection conn() {
        SQLHelper helper = Main.getInstance().getSqlHelper();
        return helper == null ? null : helper.getConn();
    }
}
