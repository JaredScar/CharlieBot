package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class StickyManager extends ListenerAdapter {
    private static HashMap<Long, Long> lastStickyMessageId = new HashMap<>();
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
        if (chan == null) return;
        switch (isLockdown) {
            case "stickyAdd":
                evt.reply("Success: The sticky message has been added...").setEphemeral(true).queue();
                API.getInstance().addSticky(chan, stickyMessage);
                initializeStickyMessage(chan.getIdLong());
                Logger.log(ActionType.STICKY_CREATE, evt.getMember(), evt.getChannel().getName(), stickyMessage);
                break;
            case "stickyEdit":
                evt.reply("Success: The sticky message has been edited...").setEphemeral(true).queue();
                API.getInstance().addSticky(chan, stickyMessage);
                initializeStickyMessage(chan.getIdLong());
                Logger.log(ActionType.STICKY_EDIT, evt.getMember(), evt.getChannel().getName(), stickyMessage);
                break;
        }
    }

    public static void removeStickyMessage(long channelId) {
        JDA jda = Main.getInstance().getJDA();
        TextChannel chan = jda.getTextChannelById(channelId);
        if (chan == null) return;
        String msg = "**__Stickied Message:__** " + API.getInstance().getStickyMessage(chan);
        // Send message if it doesn't exist already above me
        chan.getIterableHistory().takeAsync(1).thenAccept((msgList) -> {
            if (msgList.size() == 0) return;
            if (msgList.get(0).getContentRaw().equals(msg)) return;
            msgList.get(0).delete().queue();
        });
    }

    public static void initializeStickyMessage(long channelId) {
        JDA jda = Main.getInstance().getJDA();
        TextChannel chan = jda.getTextChannelById(channelId);
        if (chan == null) return;
        String msg = "**__Stickied Message:__** " + API.getInstance().getStickyMessage(chan);
        // Send message if it doesn't exist already above me
        chan.getIterableHistory().takeAsync(1).thenAccept((msgList) -> {
            if (msgList.size() == 0) return;
            if (msgList.get(0).getContentRaw().equals(msg)) return;
            chan.sendMessage(msg).queue();
        });
    }
    public static void initializeStickyMessages() {
        HashMap<Long, String> stickyMessages = API.getInstance().getStickyMessages();
        for (long channelId : stickyMessages.keySet()) {
            JDA jda = Main.getInstance().getJDA();
            TextChannel chan = jda.getTextChannelById(channelId);
            if (chan == null) return;
            // Send message if it doesn't exist already right above me
            String msg = "**__Stickied Message:__** " + stickyMessages.get(channelId);
            chan.getIterableHistory().takeAsync(1).thenAccept((msgList) -> {
                if (msgList.size() == 0) return;
                if (msgList.get(0).getContentRaw().equals(msg)) return;
                chan.sendMessage(msg).queue();
            });
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent evt) {
        String stickyMessage = API.getInstance().getStickyMessage(evt.getChannel().asTextChannel());
        if (stickyMessage.length() == 0) return;
        if (evt.getMember() == null) return;
        if (evt.getMessage().getContentRaw().equals("**__Stickied Message:__** " + stickyMessage) && evt.getMember().getUser().isBot()) return;
        if (this.lastStickyMessageId.containsKey(evt.getChannel().getIdLong())) {
            evt.getChannel().asTextChannel().purgeMessagesById(this.lastStickyMessageId.get(evt.getChannel().getIdLong()));
        }
        evt.getChannel().asTextChannel().sendMessage("**__Stickied Message:__** " + stickyMessage).queue((msg) -> {
            this.lastStickyMessageId.put(evt.getChannel().getIdLong(), msg.getIdLong());
        });
    }
}
