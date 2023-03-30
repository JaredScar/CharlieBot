package com.jaredscarito.listeners.messaging;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.Instant;
import java.util.HashMap;

public class SelfPromotionMessage {
    private static HashMap<Long, Instant> advertisedAlready = new HashMap<>();
    public static void invoke(MessageReceivedEvent evt) {
        TextChannel chan = evt.getChannel().asTextChannel();
        if (!chan.getId().equals(getConfigValue("Bot.Messaging.Self_Promotion.Channel"))) return;
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        // Valid member, we want to continue
        int currentUserLevel = API.getInstance().getRank(mem);
        int bypass_level = getConfigValueInt("Bot.Messaging.Self_Promotion.Bypass_Level");
        if (currentUserLevel >= bypass_level) {
            // They are high enough level
            if (advertisedAlready.containsKey(mem.getIdLong())) {
                // TODO We want to check to make sure their time limit is not up and see if they can do it again
                // They have already advertised, they need to wait until they can again
                evt.getMessage().delete().queue();
            }
        } else {
            // They are not high enough level to post here...
            evt.getMessage().delete().queue();
        }
    }
    private static String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }
    private static int getConfigValueInt(String path) {
        return Main.getInstance().getConfig().getInt(path);
    }
}
