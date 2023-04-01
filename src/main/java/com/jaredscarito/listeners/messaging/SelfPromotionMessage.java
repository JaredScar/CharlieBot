package com.jaredscarito.listeners.messaging;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

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
        Instant currentDatetime = Instant.now(Clock.system(ZoneId.of("America/New_York")));
        if (currentUserLevel >= bypass_level) {
            // They are high enough level
            if (advertisedAlready.containsKey(mem.getIdLong())) {
                // We want to check to make sure their time limit is not up and see if they can do it again
                Instant lastAdvertised = advertisedAlready.get(mem.getIdLong());
                Instant allowedAdvertise = lastAdvertised.plus(getConfigValueInt("Bot.Messaging.Self_Promotion.DelayTime"), ChronoUnit.HOURS);
                if (allowedAdvertise.isBefore(currentDatetime)) {
                    // They can advertise again
                    advertisedAlready.put(mem.getIdLong(), currentDatetime.plus(getConfigValueInt("Bot.Messaging.Self_Promotion.DelayTime"), ChronoUnit.HOURS));
                    return;
                }
                // They have already advertised, they need to wait until they can again
                evt.getMessage().delete().queue();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy hh:mm a")
                        .withZone(ZoneId.of("America/New_York"));
                API.getInstance().sendErrorMessage(chan, mem, "Error", "You have already advertised... You cannot advertise until `" + formatter.format(allowedAdvertise) + "`...").queue((msg) -> msg.delete().queueAfter(30, TimeUnit.SECONDS));
            } else {
                advertisedAlready.put(mem.getIdLong(), currentDatetime.plus(getConfigValueInt("Bot.Messaging.Self_Promotion.DelayTime"), ChronoUnit.HOURS));
            }
        } else {
            // They are not high enough level to post here...
            evt.getMessage().delete().queue();
            API.getInstance().sendErrorMessage(chan, mem, "Error", "You are not of high enough rank to post in here... You must be rank equivalent or better of `" + bypass_level + "`...").queue((msg) -> msg.delete().queueAfter(30, TimeUnit.SECONDS));
        }
    }
    private static String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }
    private static int getConfigValueInt(String path) {
        return Main.getInstance().getConfig().getInt(path);
    }
}
