package com.jaredscarito.listeners.messaging;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

public class PointsMessage {
    private static HashMap<Long, Instant> delayPointAdd = new HashMap<>();
    public static void invoke(MessageReceivedEvent evt) {
        TextChannel chan = evt.getChannel().asTextChannel();
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        Instant currentDatetime = Instant.now(Clock.system(ZoneId.of("America/New_York")));
        if (delayPointAdd.containsKey(mem.getIdLong())) {
            // It already has them, do not add points
            // Check if the time has expired and if so, add points and add them to delay
            Instant lastPointAdded = delayPointAdd.get(mem.getIdLong());
            Instant allowedNewPointAdd = lastPointAdded.plus(getConfigValueInt("Bot.Messaging.Points.DelayTime"), ChronoUnit.SECONDS);
            if (allowedNewPointAdd.isBefore(currentDatetime)) {
                API.getInstance().addPoints(mem, Main.getInstance().getConfig().getInt("Bot.Messaging.Points.PointsPerMessage"));
                delayPointAdd.put(mem.getIdLong(), currentDatetime.plus(getConfigValueInt("Bot.Messaging.Points.DelayTime"), ChronoUnit.SECONDS));
            }
        } else {
            // It does not have them, add points
            API.getInstance().addPoints(mem, Main.getInstance().getConfig().getInt("Bot.Messaging.Points.PointsPerMessage"));
            delayPointAdd.put(mem.getIdLong(), currentDatetime.plus(getConfigValueInt("Bot.Messaging.Points.DelayTime"), ChronoUnit.HOURS));
        }
    }

    private static String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }
    private static int getConfigValueInt(String path) {
        return Main.getInstance().getConfig().getInt(path);
    }
}
