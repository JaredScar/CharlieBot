package com.jaredscarito.listeners.commands;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.managers.StaffNotes;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class NoteCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member staff = evt.getMember();
        if (staff == null) return;
        String sub = evt.getSubcommandName();
        if (sub == null) {
            evt.reply("❌ Use add or remove.").setEphemeral(true).queue();
            return;
        }
        switch (sub.toLowerCase()) {
            case "add" -> add(evt, staff);
            case "remove" -> remove(evt, staff);
            default -> evt.reply("❌ Use add or remove.").setEphemeral(true).queue();
        }
    }

    private static void add(SlashCommandInteractionEvent evt, Member staff) {
        Member target = member(evt);
        OptionMapping noteOpt = evt.getOption("note");
        if (target == null || noteOpt == null) {
            evt.reply("❌ Choose a member who is in this server and write a note.").setEphemeral(true).queue();
            return;
        }
        String note = noteOpt.getAsString().trim();
        if (note.isEmpty() || note.length() > 500) {
            evt.reply("❌ The note has to be between 1 and 500 characters.").setEphemeral(true).queue();
            return;
        }
        String author = staff.getUser().getName();
        if (!StaffNotes.add(target.getIdLong(), staff.getIdLong(), author, note)) {
            evt.reply("❌ Could not save that note.").setEphemeral(true).queue();
            return;
        }
        Logger.log(ActionType.NOTE_CREATE, staff, target, note);
        evt.reply("✅ Note added for " + target.getAsMention() + ". It shows on `/history` and `/whois`, not on `/record`.")
                .setEphemeral(true).queue();
    }

    private static void remove(SlashCommandInteractionEvent evt, Member staff) {
        Member target = member(evt);
        if (target == null) {
            evt.reply("❌ That user is not in this server.").setEphemeral(true).queue();
            return;
        }
        var notes = StaffNotes.recent(target.getIdLong(), 25);
        if (notes.isEmpty()) {
            evt.reply(target.getAsMention() + " has no staff notes.").setEphemeral(true).queue();
            return;
        }
        StringSelectMenu.Builder menu = StringSelectMenu.create("staffNotePick|" + target.getId())
                .setPlaceholder("Select a note to remove")
                .setMinValues(1)
                .setMaxValues(1);
        for (StaffNotes.Note note : notes) {
            String label = note.text == null ? "(empty)" : note.text.replace('\n', ' ').trim();
            if (label.isEmpty()) label = "(empty)";
            if (label.length() > 100) label = label.substring(0, 97) + "...";
            if (note.datetime == null) {
                menu.addOption(label, String.valueOf(note.id));
            } else {
                String when = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(note.datetime);
                menu.addOption(label, String.valueOf(note.id), when);
            }
        }
        evt.reply("Pick a note to remove from " + target.getEffectiveName() + ".")
                .addActionRow(menu.build())
                .setEphemeral(true)
                .queue();
    }

    private static Member member(SlashCommandInteractionEvent evt) {
        OptionMapping option = evt.getOption("member");
        if (option == null) return null;
        return option.getAsMember();
    }
}
