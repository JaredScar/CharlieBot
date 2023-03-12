package com.jaredscarito.listeners.messaging.general;

import com.jaredscarito.listeners.messaging.SupportMessage;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GeneralMessageEventListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent evt) {
        SupportMessage.invoke(evt);
    }
}
