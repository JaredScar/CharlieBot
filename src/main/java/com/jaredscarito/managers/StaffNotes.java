package com.jaredscarito.managers;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.sql.SQLHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Private notes staff attach to a member. They show on /history and /whois, never on /record. */
public final class StaffNotes {
    private static final DateTimeFormatter STORED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    private StaffNotes() {}

    public static void ensureSchema() {
        Connection conn = conn();
        if (conn == null) return;
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS `notes` (" +
                    "`note_id` INT AUTO_INCREMENT PRIMARY KEY, " +
                    "`discord_id` BIGINT NOT NULL, " +
                    "`note` VARCHAR(500) NOT NULL, " +
                    "`author_id` BIGINT NOT NULL, " +
                    "`author_name` VARCHAR(128) NOT NULL, " +
                    "`datetime` DATETIME NOT NULL, " +
                    "`deleted` BIT(1) NOT NULL DEFAULT 0)");
        } catch (SQLException e) {
            Logger.log(e);
        }
    }

    public static boolean add(long discordId, long authorId, String authorName, String note) {
        Connection conn = conn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO `notes` (`discord_id`, `note`, `author_id`, `author_name`, `datetime`) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setLong(1, discordId);
            stmt.setString(2, note);
            stmt.setLong(3, authorId);
            stmt.setString(4, authorName);
            stmt.setString(5, LocalDateTime.now(ZONE).format(STORED));
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logger.log(e);
            return false;
        }
    }

    /** Marks one note deleted. The discord id must match so a menu can't remove someone else's note. */
    public static boolean remove(long noteId, long discordId) {
        Connection conn = conn();
        if (conn == null) return false;
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE `notes` SET `deleted` = 1 WHERE `note_id` = ? AND `discord_id` = ? AND `deleted` = 0")) {
            stmt.setLong(1, noteId);
            stmt.setLong(2, discordId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logger.log(e);
            return false;
        }
    }

    public static List<Note> recent(long discordId, int limit) {
        List<Note> notes = new ArrayList<>();
        Connection conn = conn();
        if (conn == null) return notes;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT `note_id`, `note`, `author_name`, `datetime` FROM `notes` " +
                        "WHERE `discord_id` = ? AND `deleted` = 0 ORDER BY `note_id` DESC LIMIT ?")) {
            stmt.setLong(1, discordId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp datetime = rs.getTimestamp("datetime");
                    notes.add(new Note(rs.getLong("note_id"), rs.getString("note"), rs.getString("author_name"), datetime));
                }
            }
        } catch (SQLException e) {
            Logger.log(e);
        }
        return notes;
    }

    /** Text for an embed field. "None" when this member has no notes. */
    public static String formatRecent(long discordId, int limit, int maxChars) {
        List<Note> notes = recent(discordId, limit);
        if (notes.isEmpty()) return "None";
        SimpleDateFormat shown = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        StringBuilder text = new StringBuilder();
        int shownCount = 0;
        for (Note note : notes) {
            String when = note.datetime == null ? "Unknown date" : shown.format(note.datetime);
            String body = note.text == null ? "" : note.text.replace("\n", " ");
            if (body.length() > 120) body = body.substring(0, 117) + "...";
            String entry = "**#" + note.id + "** — " + when + " — " + note.authorName + "\n" + body + "\n\n";
            if (text.length() + entry.length() > maxChars) break;
            text.append(entry);
            shownCount++;
        }
        if (notes.size() > shownCount) {
            text.append("*... and ").append(notes.size() - shownCount).append(" more*");
        }
        String result = text.toString().trim();
        return result.isEmpty() ? "None" : result;
    }

    private static Connection conn() {
        SQLHelper helper = Main.getInstance().getSqlHelper();
        return helper == null ? null : helper.getConn();
    }

    public static final class Note {
        public final long id;
        public final String text;
        public final String authorName;
        public final Timestamp datetime;

        public Note(long id, String text, String authorName, Timestamp datetime) {
            this.id = id;
            this.text = text;
            this.authorName = authorName;
            this.datetime = datetime;
        }
    }
}
