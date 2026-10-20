package com.jaredscarito.managers;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.ActionType;
import com.jaredscarito.models.PunishmentType;
import com.jaredscarito.sql.SQLHelper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The real-world effects behind the punishment history:
 * <ul>
 *   <li>timed mutes (tracked in the {@code mutes} table and lifted automatically when they run out),</li>
 *   <li>the roles a blacklist takes away (kept in {@code blacklists}/{@code roles} so they can be given back),</li>
 *   <li>undoing a punishment (unban / unmute / restore roles) when it is removed from a member's history.</li>
 * </ul>
 */
public class PunishmentEffects {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final Pattern DURATION = Pattern.compile("^(\\d{1,9})\\s*([a-z]*)$");

    private static ScheduledExecutorService scheduler;

    private PunishmentEffects() {}

    // ------------------------------------------------------------------------------------------
    // Database helpers
    // ------------------------------------------------------------------------------------------

    private static Connection conn() {
        SQLHelper helper = Main.getInstance().getSqlHelper();
        return helper == null ? null : helper.getConn();
    }

    /** Creates the table that tracks active mutes if it isn't there yet (so existing databases keep working). */
    public static void ensureSchema() {
        Connection conn = conn();
        if (conn == null) return;
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS `mutes` (" +
                    "`discord_id` BIGINT PRIMARY KEY, " +
                    "`expires_at` BIGINT DEFAULT NULL)");
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Mute durations
    // ------------------------------------------------------------------------------------------

    /**
     * Understands "30m", "2h", "7d", "1w" (a bare number means hours). "0", "perm", "permanent" and "forever" mean no end.
     *
     * @return the length in milliseconds, or null for a permanent mute
     * @throws IllegalArgumentException when the text can't be understood
     */
    public static Long parseDuration(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty duration");
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) throw new IllegalArgumentException("empty duration");
        if (s.equals("perm") || s.equals("permanent") || s.equals("permanently") || s.equals("forever")) return null;
        Matcher m = DURATION.matcher(s);
        if (!m.matches()) throw new IllegalArgumentException("unreadable duration: " + raw);
        long amount = Long.parseLong(m.group(1));
        if (amount == 0) return null;
        long unit = switch (m.group(2)) {
            case "", "h", "hr", "hrs", "hour", "hours" -> HOUR;
            case "m", "min", "mins", "minute", "minutes" -> MINUTE;
            case "d", "day", "days" -> DAY;
            case "w", "wk", "wks", "week", "weeks" -> WEEK;
            default -> throw new IllegalArgumentException("unknown unit: " + m.group(2));
        };
        return amount * unit; // at most 999,999,999 weeks, far below Long.MAX_VALUE
    }

    /** Human readable form of a parsed duration, e.g. "2 hour(s)". */
    public static String describeDuration(Long millis) {
        if (millis == null) return "permanently";
        if (millis % WEEK == 0) return "for " + (millis / WEEK) + " week(s)";
        if (millis % DAY == 0) return "for " + (millis / DAY) + " day(s)";
        if (millis % HOUR == 0) return "for " + (millis / HOUR) + " hour(s)";
        return "for " + (millis / MINUTE) + " minute(s)";
    }

    // ------------------------------------------------------------------------------------------
    // Active mutes + automatic unmute
    // ------------------------------------------------------------------------------------------

    /** Remember that this member is muted. A null duration means permanent (never expires). */
    public static void trackMute(long discordId, Long durationMillis) {
        Connection conn = conn();
        if (conn == null) return;
        Long expiresAt = durationMillis == null ? null : System.currentTimeMillis() + durationMillis;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO `mutes` (`discord_id`, `expires_at`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `expires_at` = ?")) {
            stmt.setLong(1, discordId);
            if (expiresAt == null) {
                stmt.setNull(2, Types.BIGINT);
                stmt.setNull(3, Types.BIGINT);
            } else {
                stmt.setLong(2, expiresAt);
                stmt.setLong(3, expiresAt);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    public static void clearMute(long discordId) {
        Connection conn = conn();
        if (conn == null) return;
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM `mutes` WHERE `discord_id` = ?")) {
            stmt.setLong(1, discordId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    /**
     * Starts the background check that lifts expired mutes. It runs once shortly after startup, which also
     * catches up on mutes that ran out while the bot was offline.
     */
    public static synchronized void startMuteScheduler() {
        if (scheduler != null) return;
        ensureSchema();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CharlieBot-mute-expiry");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(PunishmentEffects::checkExpiredMutes, 5, 30, TimeUnit.SECONDS);
    }

    private static void checkExpiredMutes() {
        // an uncaught exception would silently cancel every future run of the schedule
        try {
            Connection conn = conn();
            if (conn == null) return;
            List<Long> due = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT `discord_id` FROM `mutes` WHERE `expires_at` IS NOT NULL AND `expires_at` <= ?")) {
                stmt.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) due.add(rs.getLong("discord_id"));
                }
            }
            if (due.isEmpty()) return;

            JDA jda = Main.getInstance().getJDA();
            String guildId = Main.getInstance().getConfig().getString("Bot.Guild");
            if (jda == null || guildId == null || guildId.trim().isEmpty()) return;
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) return;
            Role muteRole = ManagerUtils.getMuteRole(guild);

            for (long id : due) {
                Member member = guild.getMemberById(id);
                if (member == null || muteRole == null || !member.getRoles().contains(muteRole)) {
                    // left the server, or the role is already gone: nothing left to lift
                    clearMute(id);
                    continue;
                }
                guild.removeRoleFromMember(member, muteRole).queue((v) -> {
                    clearMute(id);
                    Logger.log(ActionType.MUTE_DELETE, guild.getSelfMember(), member, "Mute expired");
                }, Logger::log);
            }
        } catch (Throwable t) {
            Logger.log(t);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Roles taken away by a blacklist
    // ------------------------------------------------------------------------------------------

    /** Keeps the roles a blacklist takes away, so they can be given back when the blacklist is removed. */
    public static void saveBlacklistedRoles(long discordId, List<Role> roles) {
        Connection conn = conn();
        if (conn == null) return;
        try {
            try (PreparedStatement ins = conn.prepareStatement("INSERT IGNORE INTO `blacklists` (`discord_id`) VALUES (?)")) {
                ins.setLong(1, discordId);
                ins.executeUpdate();
            }
            int blacklistId = -1;
            try (PreparedStatement sel = conn.prepareStatement("SELECT `blacklist_id` FROM `blacklists` WHERE `discord_id` = ?")) {
                sel.setLong(1, discordId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) blacklistId = rs.getInt("blacklist_id");
                }
            }
            if (blacklistId < 0) return;
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM `roles` WHERE `blacklist_id` = ?")) {
                del.setInt(1, blacklistId);
                del.executeUpdate();
            }
            try (PreparedStatement add = conn.prepareStatement("INSERT INTO `roles` (`blacklist_id`, `role_id`) VALUES (?, ?)")) {
                for (Role r : roles) {
                    add.setInt(1, blacklistId);
                    add.setLong(2, r.getIdLong());
                    add.addBatch();
                }
                add.executeBatch();
            }
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    private static List<Long> loadSavedRoles(long discordId) {
        List<Long> ids = new ArrayList<>();
        Connection conn = conn();
        if (conn == null) return ids;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT r.`role_id` FROM `roles` r " +
                "JOIN `blacklists` b ON b.`blacklist_id` = r.`blacklist_id` WHERE b.`discord_id` = ?")) {
            stmt.setLong(1, discordId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong("role_id"));
            }
        } catch (SQLException e) {
            Logger.log(e);
        }
        return ids;
    }

    private static void deleteSavedRoles(long discordId) {
        Connection conn = conn();
        if (conn == null) return;
        try {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM `roles` WHERE `blacklist_id` IN " +
                    "(SELECT `blacklist_id` FROM `blacklists` WHERE `discord_id` = ?)")) {
                del.setLong(1, discordId);
                del.executeUpdate();
            }
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM `blacklists` WHERE `discord_id` = ?")) {
                del.setLong(1, discordId);
                del.executeUpdate();
            }
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Undoing a punishment
    // ------------------------------------------------------------------------------------------

    /**
     * Reverses what a punishment did (unban, unmute, give back blacklisted roles). Kicks and warnings have no
     * lasting effect, so there is nothing to undo for them.
     *
     * @param done called exactly once, with a short note for the moderator (empty when there was nothing to undo)
     */
    public static void undo(Guild guild, long discordId, PunishmentType type, Consumer<String> done) {
        switch (type) {
            case BAN -> guild.unban(UserSnowflake.fromId(discordId)).queue(
                    (v) -> done.accept("\n🔓 The member has been unbanned."),
                    (error) -> {
                        if (error instanceof ErrorResponseException e && e.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                            done.accept(""); // they were not banned (anymore)
                        } else {
                            Logger.log(error);
                            done.accept("\n⚠️ Could not unban the member: " + error.getMessage());
                        }
                    });
            case MUTE -> {
                clearMute(discordId);
                Member member = guild.getMemberById(discordId);
                Role muteRole = ManagerUtils.getMuteRole(guild);
                if (member == null || muteRole == null || !member.getRoles().contains(muteRole)) {
                    done.accept("");
                    return;
                }
                guild.removeRoleFromMember(member, muteRole).queue(
                        (v) -> done.accept("\n🔊 The member has been unmuted."),
                        (error) -> {
                            Logger.log(error);
                            done.accept("\n⚠️ Could not remove the mute role: " + error.getMessage());
                        });
            }
            case BLACKLIST -> {
                Member member = guild.getMemberById(discordId);
                if (member == null) {
                    deleteSavedRoles(discordId); // they left, there is nobody to give the roles back to
                    done.accept("");
                    return;
                }
                Role blacklistRole = ManagerUtils.getBlacklistRole(guild);
                Member self = guild.getSelfMember();
                List<Role> toAdd = new ArrayList<>();
                for (long roleId : loadSavedRoles(discordId)) {
                    Role r = guild.getRoleById(roleId);
                    if (r == null || r.isManaged() || r.isPublicRole() || !self.canInteract(r) || member.getRoles().contains(r)) continue;
                    toAdd.add(r);
                }
                List<Role> toRemove = new ArrayList<>();
                if (blacklistRole != null && member.getRoles().contains(blacklistRole)) toRemove.add(blacklistRole);
                if (toAdd.isEmpty() && toRemove.isEmpty()) {
                    deleteSavedRoles(discordId);
                    done.accept("");
                    return;
                }
                guild.modifyMemberRoles(member, toAdd, toRemove).queue((v) -> {
                    deleteSavedRoles(discordId);
                    done.accept("\n✅ The member is no longer blacklisted and their roles were given back.");
                }, (error) -> {
                    Logger.log(error);
                    done.accept("\n⚠️ Could not restore the member's roles: " + error.getMessage());
                });
            }
            default -> done.accept(""); // KICK / WARN: nothing lasting to undo
        }
    }
}
