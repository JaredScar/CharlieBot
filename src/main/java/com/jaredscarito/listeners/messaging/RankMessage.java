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
import java.util.concurrent.TimeUnit;

public class RankMessage {
    private static HashMap<Long, Instant> delayExpAdd = new HashMap<>();
    public static void invoke(MessageReceivedEvent evt) {
        TextChannel chan = evt.getChannel().asTextChannel();
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        Instant currentDatetime = Instant.now(Clock.system(ZoneId.of("America/New_York")));
        int lastRank = API.getInstance().getRank(mem);
        if (delayExpAdd.containsKey(mem.getIdLong())) {
            // It already has them, do not add points
            // Check if the time has expired and if so, add points and add them to delay
            Instant lastExpAdded = delayExpAdd.get(mem.getIdLong());
            Instant allowedNewExpAdd = lastExpAdded.plus(getConfigValueInt("Bot.Messaging.Rank.DelayTime"), ChronoUnit.SECONDS);
            if (allowedNewExpAdd.isBefore(currentDatetime)) {
                API.getInstance().addRankExp(mem, Main.getInstance().getConfig().getInt("Bot.Messaging.Rank.ExpPerMessage"));
                delayExpAdd.put(mem.getIdLong(), currentDatetime.plus(getConfigValueInt("Bot.Messaging.Rank.DelayTime"), ChronoUnit.SECONDS));
                checkRankExp(chan, mem, lastRank);
            }
        } else {
            // It does not have them, add points
            API.getInstance().addRankExp(mem, Main.getInstance().getConfig().getInt("Bot.Messaging.Rank.ExpPerMessage"));
            delayExpAdd.put(mem.getIdLong(), currentDatetime.plus(getConfigValueInt("Bot.Messaging.Rank.DelayTime"), ChronoUnit.HOURS));
            checkRankExp(chan, mem, lastRank);
        }
    }

    private static void checkRankExp(TextChannel chan, Member mem, int lastRank) {
        int currentRank = API.getInstance().getRank(mem);
        if (lastRank == currentRank) return;
        API.getInstance().sendSuccessMessage(chan, mem, "Success", "You have ranked up to rank level `" + currentRank + "`!").queue((msg) -> msg.delete().queueAfter(30, TimeUnit.SECONDS));
    }

    private static String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }
    private static int getConfigValueInt(String path) {
        return Main.getInstance().getConfig().getInt(path);
    }
}
