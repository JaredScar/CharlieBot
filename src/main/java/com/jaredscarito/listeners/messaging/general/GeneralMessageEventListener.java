package com.jaredscarito.listeners.messaging.general;

import com.jaredscarito.listeners.messaging.PointsMessage;
import com.jaredscarito.listeners.messaging.RankMessage;
import com.jaredscarito.listeners.messaging.SelfPromotionMessage;
import com.jaredscarito.listeners.messaging.SupportMessage;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GeneralMessageEventListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent evt) {
        // All handlers below work with regular guild text channels and real users.
        // Threads, voice-channel chats, forum posts, webhooks and bots would make them throw.
        if (!evt.isFromType(ChannelType.TEXT)) return;
        if (evt.getAuthor().isBot() || evt.isWebhookMessage()) return;

        if (this.isEnabled("Bot.Messaging.Support"))
            run(() -> SupportMessage.invoke(evt));
        if (this.isEnabled("Bot.Messaging.Points"))
            run(() -> PointsMessage.invoke(evt));
        if (this.isEnabled("Bot.Messaging.Rank"))
            run(() -> RankMessage.invoke(evt));
        if (this.isEnabled("Bot.Messaging.Self_Promotion"))
            run(() -> SelfPromotionMessage.invoke(evt));
    }

    /** One failing handler must not stop the others, and its error must end up in the error log. */
    private void run(Runnable handler) {
        try {
            handler.run();
        } catch (Exception ex) {
            Logger.log(ex);
        }
    }

    public boolean isEnabled(String path) {
        return (Main.getInstance().getConfig().getBoolean( path + ".Enabled"));
    }
}
