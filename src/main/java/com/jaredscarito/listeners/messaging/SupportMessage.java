package com.jaredscarito.listeners.messaging;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SupportMessage {
    public static void invoke(MessageReceivedEvent evt) {
        if (evt.getMember() == null) return;
        if (evt.getMember().getUser().isBot()) return;
        TextChannel chan = evt.getChannel().asTextChannel();
        List<String> wordList = Main.getInstance().getConfig().getStringList("Bot.Messaging.Support.Triggers");
        List<String> bypassCategories = Main.getInstance().getConfig().getStringList("Bot.Messaging.Support.Bypass_Categories");
        for (String categoryId : bypassCategories) {
            if (chan.getParentCategoryId().equals(categoryId))
                return; // Do not continue, it's a bypass category
        }
        boolean sendMessage = false;
        for (String word : wordList) {
            if (evt.getMessage().getContentDisplay().toLowerCase().contains(word)) {
                sendMessage = true;
            }
        }
        int bypass_rank = Main.getInstance().getConfig().getInt("Bot.Messaging.Support.Bypass_Level");
        if (sendMessage && !(API.getInstance().getRank(evt.getMember()) >= bypass_rank)
                && !(hasBypassRole(evt.getMember().getRoles()))) {
            evt.getChannel().sendMessage(Main.getInstance().getConfig().getString("Bot.Messaging.Support.Message")).queue((msg) -> {
                evt.getMessage().delete().queueAfter(30, TimeUnit.SECONDS);
                msg.delete().queueAfter(30, TimeUnit.SECONDS);
            });
        }
    }

    private static boolean hasBypassRole(List<Role> roles) {
        List<String> bypass_roles = Main.getInstance().getConfig().getStringList("Bot.Messaging.Support.Bypass_Roles");
        for (String bypass_role : bypass_roles) {
            for (Role r : roles) {
                if (r.getId().equals(bypass_role))
                    return true;
            }
        }
        return false;
    }
}
