package com.jaredscarito.listeners.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class LockdownCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Guild guild = evt.getGuild();
        Member mem = evt.getMember();
        if (guild == null) return;
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        String subCommand = evt.getSubcommandName();
        if (subCommand == null) return;
        TextInput inp;
        Modal modal = null;
        switch (subCommand.toLowerCase()) {
            case "enable":
                inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Reason for enabling  lockdown")
                        .setMinLength(0)
                        .setMaxLength(1024)
                        .setRequired(true)
                        .build();
                modal = Modal.create("lockdownEnable|" + evt.getChannel().getId(), "Enable Lockdown")
                        .addComponents(ActionRow.of(inp))
                        .build();
                break;
            case "disable":
                inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Reason for disabling  lockdown")
                        .setMinLength(0)
                        .setMaxLength(1024)
                        .setRequired(true)
                        .build();
                modal = Modal.create("lockdownDisable|" + evt.getChannel().getId(), "Disable Lockdown")
                        .addComponents(ActionRow.of(inp))
                        .build();
                break;
        }
        if (modal != null) {
            evt.replyModal(modal).queue();
        } else {
            // TODO Error, something went wrong in the setup process...
        }
    }
}
