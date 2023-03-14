package com.jaredscarito.listeners.messaging.general;

import com.jaredscarito.listeners.messaging.PointsMessage;
import com.jaredscarito.listeners.messaging.RankMessage;
import com.jaredscarito.listeners.messaging.SelfPromotionMessage;
import com.jaredscarito.listeners.messaging.SupportMessage;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GeneralMessageEventListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent evt) {
        if (this.isEnabled("Bot.Messaging.Support"))
            SupportMessage.invoke(evt);
        if (this.isEnabled("Bot.Messaging.Points"))
            PointsMessage.invoke(evt);
        if (this.isEnabled("Bot.Messaging.Rank"))
            RankMessage.invoke(evt);
        if (this.isEnabled("Bot.Messaging.SelfPromotion"))
            SelfPromotionMessage.invoke(evt);

    }
    public boolean isEnabled(String path) {
        return (Main.getInstance().getConfig().getBoolean( path + ".Enabled"));
    }
}
