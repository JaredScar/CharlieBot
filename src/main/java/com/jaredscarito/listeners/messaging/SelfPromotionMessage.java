package com.jaredscarito.listeners.messaging;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SelfPromotionMessage {
    public static void invoke(MessageReceivedEvent evt) {
        TextChannel chan = evt.getChannel().asTextChannel();
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        // Valid member, we want to continue
    }
}
