package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.managers.StickyManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class StickyCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Guild guild = evt.getGuild();
        Member mem = evt.getMember();
        if (guild == null) return;
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        String subCommand = evt.getSubcommandName();
        if (subCommand == null) return;
        TextInput.Builder inp = TextInput.create("stickyMessage", "Sticky", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Sticky Message")
                .setMinLength(0)
                .setMaxLength(1024)
                .setRequired(true);
        Modal modal = null;
        switch (subCommand.toLowerCase()) {
            case "add":
                modal = Modal.create("stickyAdd|" + evt.getChannel().getId(), "Add Sticky Message")
                        .addComponents(ActionRow.of(inp.build()))
                        .build();
                break;
            case "edit":
                inp.setValue(API.getInstance().getStickyMessage(evt.getChannel().asTextChannel()));
                modal = Modal.create("stickyEdit|" + evt.getChannel().getId(), "Edit Sticky Message")
                        .addComponents(ActionRow.of(inp.build()))
                        .build();
                break;
            case "remove":
                API.getInstance().removeSticky(evt.getChannel().asTextChannel());
                // Remove sticky message from channel if it is at bottom
                StickyManager.removeStickyMessage(evt.getChannel().getIdLong());
                evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "The stickied message for this channel has been removed...!").build()).setEphemeral(true).queue();
                break;
        }
        if (modal != null)
            evt.replyModal(modal).queue();
    }
}
