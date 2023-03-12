package com.jaredscarito.threads;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import java.util.List;
import java.util.stream.Stream;

public class TicketManager extends ListenerAdapter {
    private long TICKET_CHANNEL_ID = -1;
    private long TICKET_CATEGORY_ID = -1;

    private long TICKET_CREATION_MESSAGE_ID = -1;

    public TicketManager() {
        JDA jda = Main.getInstance().getJDA();
        try {
            Guild guild = jda.getGuildById(getConfigValue("Bot.Guild"));
            if (guild == null) return;
            TextChannel chan = guild.getChannelById(TextChannel.class, getConfigValue("Bot.Tickets.Channel"));
            if (chan == null) return;
            MessageHistory history = chan.getHistory();
            List<Message> msgs = history.getRetrievedHistory();
            Stream<Message> myMessages = msgs.stream().filter((msg) -> {
                if (msg.getMember() == null) return false;
                if (msg.getId().equals(getConfigValue("Bot.Tickets.MessageID"))) return false;
                return msg.getMember().getIdLong() == jda.getSelfUser().getIdLong();
            });
            for (Message msg : myMessages.toList()) {
                msg.delete().queue();
            }
            Message msg = null;
            String msgID = getConfigValue("Bot.Tickets.MessageID");
            if (!msgID.isEmpty())
                msg = history.getMessageById(msgID);
            if (msg != null) {
                // We need to delete it and set up the new one
                msg.delete().queue();
            }
            API.getInstance().createMainTicketMessage(chan).queue((msgRes) -> {});
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    public String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        List<SelectOption> selectedOpts = evt.getSelectedOptions();
        for (SelectOption opt : selectedOpts) {
            // Check if value exists
            String optVal = opt.getValue();
            String title = getConfigValue("Bot.Tickets.Create_Ticket_Options." + optVal + ".Open_Ticket_Confirm_Title");
            String desc = getConfigValue("Bot.Tickets.Create_Ticket_Options." + optVal + ".Open_Ticket_Confirm_Desc");
            API.getInstance().askConfirmDenyMessage(evt, evt.getMember(), title, desc);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        Button button = evt.getButton();
    }
}
