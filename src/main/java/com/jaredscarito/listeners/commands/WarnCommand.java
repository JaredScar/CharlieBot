package com.jaredscarito.listeners.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class WarnCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        OptionMapping opt = evt.getOption("user");
        if (opt == null) return;
        if (opt.getAsMember() == null) return;
        User user = opt.getAsMember().getUser();
        TextInput inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Reason for warning")
                .setMinLength(0)
                .setMaxLength(1024)
                .setRequired(true)
                .build();
        Modal modal = Modal.create("warnUser"
                        + "|" + user.getId(), "Warn User " + user.getName() + "#" + user.getDiscriminator())
                .addComponents(ActionRow.of(inp))
                .build();
        evt.replyModal(modal).queue();
    }
}