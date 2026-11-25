package com.jaredscarito.managers;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Collections;

/** Handles the select menu from /note remove. */
public class NoteManager extends ListenerAdapter {
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        String id = evt.getComponentId();
        if (!id.startsWith("staffNotePick|")) return;
        if (evt.getMember() == null || evt.getGuild() == null) return;
        String[] args = id.split("\\|");
        if (args.length < 2) return;
        long discordId;
        long noteId;
        try {
            discordId = Long.parseLong(args[1]);
            noteId = Long.parseLong(evt.getSelectedOptions().get(0).getValue());
        } catch (NumberFormatException ex) {
            evt.editMessage("❌ That note could not be read.").setComponents(Collections.emptyList()).queue();
            return;
        }
        if (!StaffNotes.remove(noteId, discordId)) {
            evt.editMessage("❌ That note is already gone.").setComponents(Collections.emptyList()).queue();
            return;
        }
        Member target = evt.getGuild().getMemberById(discordId);
        if (target != null) {
            Logger.log(ActionType.NOTE_DELETE, evt.getMember(), target, "Removed note #" + noteId);
        }
        evt.editMessage("✅ Removed note #" + noteId + ".").setComponents(Collections.emptyList()).queue();
    }
}
