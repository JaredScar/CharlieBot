package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StickyManager extends ListenerAdapter {
    private static final String PREFIX = "**__Stickied Message:__** ";
    /** channel id -> id of the sticky message the bot currently has posted there */
    private static final Map<Long, Long> lastStickyMessageId = new ConcurrentHashMap<>();

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String id = evt.getModalId();
        String[] modelIdArgs = id.split("\\|");
        if (evt.getMember() == null) return;
        if (modelIdArgs.length <= 1) return;
        String isLockdown = modelIdArgs[0];
        if (!isLockdown.equals("stickyAdd") && !isLockdown.equals("stickyEdit")) return;
        ModalMapping modMap = evt.getValue("stickyMessage");
        if (modMap == null) return;
        String stickyMessage = modMap.getAsString();
        String channelId = modelIdArgs[1];
        TextChannel chan = evt.getJDA().getTextChannelById(channelId);
        if (chan == null) {
            evt.reply("❌ I can't find that channel anymore.").setEphemeral(true).queue();
            return;
        }
        if (!API.getInstance().addSticky(chan, stickyMessage)) {
            evt.reply("❌ The sticky message could not be saved. Check `logs/error.txt` for details.").setEphemeral(true).queue();
            return;
        }
        switch (isLockdown) {
            case "stickyAdd":
                evt.reply("Success: The sticky message has been added...").setEphemeral(true).queue();
                Logger.log(ActionType.STICKY_CREATE, evt.getMember(), chan.getName(), stickyMessage);
                break;
            case "stickyEdit":
                evt.reply("Success: The sticky message has been edited...").setEphemeral(true).queue();
                Logger.log(ActionType.STICKY_EDIT, evt.getMember(), chan.getName(), stickyMessage);
                break;
        }
        initializeStickyMessage(chan.getIdLong());
    }

    /** Called after the sticky was removed from the database: take the bot's sticky message down again. */
    public static void removeStickyMessage(long channelId) {
        JDA jda = Main.getInstance().getJDA();
        TextChannel chan = jda.getTextChannelById(channelId);
        if (chan == null) return;
        Long tracked = lastStickyMessageId.remove(channelId);
        if (tracked != null) {
            chan.deleteMessageById(tracked).queue(null, error -> {});
            return;
        }
        // Not tracked (e.g. after a restart): only delete the newest message if it is really our sticky.
        // The old code deleted the newest message unconditionally, which could be a member's message.
        long selfId = jda.getSelfUser().getIdLong();
        chan.getIterableHistory().takeAsync(1).thenAccept((msgList) -> {
            if (msgList.isEmpty()) return;
            Message last = msgList.get(0);
            if (last.getAuthor().getIdLong() == selfId && last.getContentRaw().startsWith(PREFIX)) {
                last.delete().queue(null, error -> {});
            }
        });
    }

    /** Posts a fresh sticky and takes the previous one down. */
    private static void postSticky(TextChannel chan, String stickyText) {
        Long old = lastStickyMessageId.get(chan.getIdLong());
        if (old != null) {
            chan.deleteMessageById(old).queue(null, error -> {});
        }
        chan.sendMessage(PREFIX + stickyText).queue((msg) -> lastStickyMessageId.put(chan.getIdLong(), msg.getIdLong()));
    }

    /** Makes sure the channel's sticky is the newest message, without duplicating it. */
    private static void ensureSticky(TextChannel chan, String stickyText) {
        String expected = PREFIX + stickyText;
        long selfId = chan.getJDA().getSelfUser().getIdLong();
        chan.getIterableHistory().takeAsync(1).thenAccept((msgList) -> {
            if (!msgList.isEmpty()) {
                Message last = msgList.get(0);
                boolean ours = last.getAuthor().getIdLong() == selfId && last.getContentRaw().startsWith(PREFIX);
                if (ours && last.getContentRaw().equals(expected)) {
                    // already sitting at the bottom, just remember it so it can be replaced later
                    lastStickyMessageId.put(chan.getIdLong(), last.getIdLong());
                    return;
                }
                if (ours) {
                    // an outdated version of our sticky (it was edited or removed): replace it
                    lastStickyMessageId.put(chan.getIdLong(), last.getIdLong());
                }
            }
            postSticky(chan, stickyText);
        });
    }

    public static void initializeStickyMessage(long channelId) {
        JDA jda = Main.getInstance().getJDA();
        TextChannel chan = jda.getTextChannelById(channelId);
        if (chan == null) return;
        String stickyText = API.getInstance().getStickyMessage(chan);
        if (stickyText.isEmpty()) return;
        ensureSticky(chan, stickyText);
    }

    public static void initializeStickyMessages() {
        HashMap<Long, String> stickyMessages = API.getInstance().getStickyMessages();
        JDA jda = Main.getInstance().getJDA();
        for (Map.Entry<Long, String> entry : stickyMessages.entrySet()) {
            TextChannel chan = jda.getTextChannelById(entry.getKey());
            if (chan == null) continue; // channel was deleted, keep going with the others
            ensureSticky(chan, entry.getValue());
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent evt) {
        // asTextChannel() throws for threads / voice chats / DMs
        if (!evt.isFromType(ChannelType.TEXT)) return;
        TextChannel chan = evt.getChannel().asTextChannel();
        String stickyMessage = API.getInstance().getStickyMessage(chan);
        if (stickyMessage.isEmpty()) return;
        // never react to our own sticky, otherwise it would repost itself forever
        if (evt.getAuthor().getIdLong() == evt.getJDA().getSelfUser().getIdLong()
                && evt.getMessage().getContentRaw().startsWith(PREFIX)) return;
        postSticky(chan, stickyMessage);
    }
}
