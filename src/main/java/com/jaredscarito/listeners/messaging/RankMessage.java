package com.jaredscarito.listeners.messaging;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RankMessage {
    /** member id -> earliest moment they can earn exp again */
    private static final Map<Long, Instant> nextExpAt = new ConcurrentHashMap<>();

    public static void invoke(MessageReceivedEvent evt) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        Instant now = Instant.now();
        Instant allowedAt = nextExpAt.get(mem.getIdLong());
        if (allowedAt != null && now.isBefore(allowedAt)) return; // still on cooldown

        // Config key is "DebounceTime" (seconds); the old code read a non-existent "DelayTime"
        // (so there was no cooldown at all) and used hours for the first message.
        long debounce = Math.max(0, Main.getInstance().getConfig().getInt("Bot.Messaging.Rank.DebounceTime"));
        int lastRank = API.getInstance().getRank(mem);
        API.getInstance().addRankExp(mem, Main.getInstance().getConfig().getInt("Bot.Messaging.Rank.ExpPerMessage"));
        nextExpAt.put(mem.getIdLong(), now.plusSeconds(debounce));
        checkRankExp(evt.getChannel().asTextChannel(), mem, lastRank);
    }

    private static void checkRankExp(TextChannel chan, Member mem, int lastRank) {
        int currentRank = API.getInstance().getRank(mem);
        if (lastRank == currentRank) return;
        API.getInstance().sendSuccessMessage(chan, mem, "Success", "You have ranked up to rank level `" + currentRank + "`!").queue((msg) -> msg.delete().queueAfter(30, TimeUnit.SECONDS));
    }
}
