package com.jaredscarito.listeners.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class PointsMessage {
    /** member id -> earliest moment they can earn points again */
    private static final Map<Long, Instant> nextPointsAt = new ConcurrentHashMap<>();

    public static void invoke(MessageReceivedEvent evt) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        Instant now = Instant.now();
        Instant allowedAt = nextPointsAt.get(mem.getIdLong());
        if (allowedAt != null && now.isBefore(allowedAt)) return; // still on cooldown

        long debounce = Math.max(0, Main.getInstance().getConfig().getInt("Bot.Messaging.Points.DebounceTime"));
        API.getInstance().addPoints(mem, Main.getInstance().getConfig().getInt("Bot.Messaging.Points.PerMessage"));
        nextPointsAt.put(mem.getIdLong(), now.plusSeconds(debounce));
    }
}
