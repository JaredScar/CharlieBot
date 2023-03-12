package com.jaredscarito.listeners.messaging;

import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class SupportMessage {
    public static void invoke(MessageReceivedEvent evt) {
        if (evt.getMember() == null) return;
        if (evt.getMember().getUser().isBot()) return;
        List<String> wordList = Main.getInstance().getConfig().getStringList("Bot.Messaging.Support.Triggers");
        boolean sendMessage = false;
        for (String word : wordList) {
            if (evt.getMessage().getContentDisplay().toLowerCase().contains(word)) {
                sendMessage = true;
            }
        }
        if (sendMessage) {
            evt.getChannel().sendMessage(Main.getInstance().getConfig().getString("Bot.Messaging.Support.Message")).queue((msg) -> {
                Timer timer = new Timer();
                evt.getMessage().delete().queue();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        msg.delete().queue();
                    }
                }, 30000L);
            });
        }
    }
}
