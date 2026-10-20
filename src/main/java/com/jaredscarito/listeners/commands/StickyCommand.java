package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.managers.StickyManager;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
        if (subCommand == null) {
            evt.reply("❌ Error: Please specify a subcommand (add, edit or remove). " +
                    "If you don't see any, add the `Sub-Commands` for `Sticky` to config.yml (see config.example.yml).").setEphemeral(true).queue();
            return;
        }
        if (evt.getChannelType() != ChannelType.TEXT) {
            evt.reply("❌ Sticky messages can only be used in regular text channels.").setEphemeral(true).queue();
            return;
        }
        TextChannel chan = evt.getChannel().asTextChannel();
        TextInput.Builder inp = TextInput.create("stickyMessage", "Sticky", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Sticky Message")
                .setMinLength(1)
                .setMaxLength(1024)
                .setRequired(true);
        Modal modal = null;
        switch (subCommand.toLowerCase()) {
            case "add":
                modal = Modal.create("stickyAdd|" + chan.getId(), "Add Sticky Message")
                        .addComponents(ActionRow.of(inp.build()))
                        .build();
                break;
            case "edit":
                String current = API.getInstance().getStickyMessage(chan);
                if (current.isEmpty()) {
                    evt.reply("❌ This channel has no sticky message yet. Use `/sticky add` first.").setEphemeral(true).queue();
                    return;
                }
                inp.setValue(current);
                modal = Modal.create("stickyEdit|" + chan.getId(), "Edit Sticky Message")
                        .addComponents(ActionRow.of(inp.build()))
                        .build();
                break;
            case "remove":
                if (!API.getInstance().removeSticky(chan)) {
                    evt.reply("❌ The sticky message could not be removed. Check `logs/error.txt` for details.").setEphemeral(true).queue();
                    return;
                }
                // Remove sticky message from channel if it is at bottom
                StickyManager.removeStickyMessage(chan.getIdLong());
                evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "The stickied message for this channel has been removed...!").build()).setEphemeral(true).queue();
                Logger.log(ActionType.STICKY_REMOVE, mem, chan, "N/A");
                break;
            default:
                evt.reply("❌ Error: Unknown subcommand.").setEphemeral(true).queue();
                return;
        }
        if (modal != null)
            evt.replyModal(modal).queue();
    }
}
