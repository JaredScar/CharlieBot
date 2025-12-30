package com.jaredscarito.listeners.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class LockdownCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Guild guild = evt.getGuild();
        Member mem = evt.getMember();
        if (guild == null) {
            evt.reply("❌ Error: This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        if (mem == null) {
            evt.reply("❌ Error: Unable to identify member.").setEphemeral(true).queue();
            return;
        }
        if (mem.getUser().isBot()) {
            return;
        }
        
        String subCommand = evt.getSubcommandName();
        if (subCommand == null) {
            evt.reply("❌ Error: Please specify a subcommand (enable or disable).").setEphemeral(true).queue();
            return;
        }
        
        TextInput inp;
        Modal modal = null;
        switch (subCommand.toLowerCase()) {
            case "enable":
                inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Reason for enabling lockdown")
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
                        .setPlaceholder("Reason for disabling lockdown")
                        .setMinLength(0)
                        .setMaxLength(1024)
                        .setRequired(true)
                        .build();
                modal = Modal.create("lockdownDisable|" + evt.getChannel().getId(), "Disable Lockdown")
                        .addComponents(ActionRow.of(inp))
                        .build();
                break;
            default:
                evt.reply("❌ Error: Unknown subcommand. Please use 'enable' or 'disable'.").setEphemeral(true).queue();
                return;
        }
        
        if (modal != null) {
            evt.replyModal(modal).queue();
        } else {
            evt.reply("❌ Error: Failed to create lockdown modal. Please try again.").setEphemeral(true).queue();
        }
    }
}
