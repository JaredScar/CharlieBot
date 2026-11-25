package com.jaredscarito.listeners.commands;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class PurgeCommand {
    /** Discord only bulk-deletes messages from the last 14 days, and at most 100 at a time. */
    private static final int MAX_MESSAGES = 100;

    public static void invoke(SlashCommandInteractionEvent evt) {
        Member staff = evt.getMember();
        if (staff == null || evt.getGuild() == null) {
            evt.reply("❌ Use this command in a server channel.").setEphemeral(true).queue();
            return;
        }
        if (!(evt.getMessageChannel() instanceof GuildMessageChannel channel)) {
            evt.reply("❌ Use this command in a server channel.").setEphemeral(true).queue();
            return;
        }
        OptionMapping amountOpt = evt.getOption("amount");
        if (amountOpt == null) {
            evt.reply("❌ Say how many messages to delete (1-100).").setEphemeral(true).queue();
            return;
        }
        long requested = amountOpt.getAsLong();
        if (requested < 1 || requested > MAX_MESSAGES) {
            evt.reply("❌ Amount must be between 1 and 100.").setEphemeral(true).queue();
            return;
        }
        int amount = (int) requested;
        User filter = evt.getOption("member") == null ? null : evt.getOption("member").getAsUser();

        Member self = evt.getGuild().getSelfMember();
        if (!self.hasPermission(channel, Permission.MESSAGE_MANAGE) || !self.hasPermission(channel, Permission.MESSAGE_HISTORY)) {
            evt.reply("❌ I need Manage Messages and Read Message History in this channel.").setEphemeral(true).queue();
            return;
        }
        if (!staff.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            evt.reply("❌ You need the Manage Messages permission in this channel.").setEphemeral(true).queue();
            return;
        }

        evt.deferReply(true).queue(hook -> channel.getHistory().retrievePast(MAX_MESSAGES).queue(messages -> {
            List<Message> deletable = new ArrayList<>();
            int skippedPinned = 0;
            boolean hitAgeLimit = false;
            OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14);
            for (Message message : messages) {
                if (deletable.size() >= amount) break;
                if (filter != null && message.getAuthor().getIdLong() != filter.getIdLong()) continue;
                if (message.getTimeCreated().isBefore(cutoff)) {
                    hitAgeLimit = true;
                    break;
                }
                if (message.isPinned()) {
                    skippedPinned++;
                    continue;
                }
                deletable.add(message);
            }
            if (deletable.isEmpty()) {
                hook.editOriginal(emptyReason(filter, skippedPinned, hitAgeLimit)).queue();
                return;
            }
            String summary = summary(deletable.size(), filter, skippedPinned, hitAgeLimit, deletable.size() < amount);
            Runnable after = () -> {
                if (channel instanceof TextChannel text) {
                    Logger.log(ActionType.PURGE, staff, text, summary);
                } else {
                    Logger.log(ActionType.PURGE, staff, "#" + channel.getName(), summary);
                }
                hook.editOriginal("✅ " + summary).queue();
            };
            if (deletable.size() == 1) {
                channel.deleteMessageById(deletable.get(0).getIdLong()).queue(v -> after.run(), error -> {
                    Logger.log(error);
                    hook.editOriginal("❌ Failed to delete that message: " + error.getMessage()).queue();
                });
            } else {
                channel.deleteMessages(deletable).queue(v -> after.run(), error -> {
                    Logger.log(error);
                    hook.editOriginal("❌ Failed to delete those messages: " + error.getMessage()).queue();
                });
            }
        }, error -> {
            Logger.log(error);
            hook.editOriginal("❌ Could not read this channel's messages: " + error.getMessage()).queue();
        }), Logger::log);
    }

    private static String emptyReason(User filter, int skippedPinned, boolean hitAgeLimit) {
        if (filter != null && skippedPinned == 0 && !hitAgeLimit) {
            return "No messages from " + filter.getName() + " in the newest " + MAX_MESSAGES + " messages.";
        }
        if (hitAgeLimit && skippedPinned == 0) {
            return "Those messages are older than 14 days. Discord will not bulk-delete them.";
        }
        if (skippedPinned > 0 && !hitAgeLimit) {
            return "Those messages are pinned, so they were left in place.";
        }
        return "Nothing eligible to delete. Pinned messages and messages older than 14 days were left in place.";
    }

    private static String summary(int deleted, User filter, int skippedPinned, boolean hitAgeLimit, boolean shortfall) {
        StringBuilder text = new StringBuilder("Deleted ");
        text.append(deleted).append(deleted == 1 ? " message" : " messages");
        if (filter != null) text.append(" from ").append(filter.getName());
        text.append(".");
        if (skippedPinned > 0) {
            text.append(" Left ").append(skippedPinned).append(skippedPinned == 1 ? " pinned message" : " pinned messages").append(".");
        }
        if (shortfall && hitAgeLimit) {
            text.append(" Stopped at messages older than 14 days.");
        } else if (shortfall && filter != null) {
            text.append(" That is all of theirs in the newest ").append(MAX_MESSAGES).append(" messages.");
        }
        return text.toString();
    }
}
