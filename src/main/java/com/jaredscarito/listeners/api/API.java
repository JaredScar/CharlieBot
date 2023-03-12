package com.jaredscarito.listeners.api;

import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

import java.util.List;

public class API {
    public static API api = new API();
    public static API getInstance() {
        return api;
    }

    public String getConfigValue(String path) {
        return Main.getInstance().getConfig().getString(path);
    }

    public MessageCreateAction createMainTicketMessage(TextChannel chan) {
        EmbedBuilder eb = new EmbedBuilder();
        StringSelectMenu.Builder selectMenuBuilder = StringSelectMenu.create("ticket");
        selectMenuBuilder.setPlaceholder(getConfigValue("Bot.Tickets.Title"));
        List<String> options = Main.getInstance().getConfig().getKeys("Bot.Tickets.Create_Ticket_Options");
        for (String opt : options) {
            String optLabel = getConfigValue("Bot.Tickets.Create_Ticket_Options." + opt + ".Label");
            boolean optEnabled = Main.getInstance().getConfig().getBoolean("Bot.Tickets.Create_Ticket_Options." + opt + ".Enabled");
            if (optEnabled)
                selectMenuBuilder.addOption(optLabel, opt);
        }
        StringSelectMenu selectMenu = selectMenuBuilder.build();
        eb.setTitle(getConfigValue("Bot.Tickets.Title"));
        eb.setDescription(getConfigValue("Bot.Tickets.Body"));
        MessageEmbed embed = eb.build();
        MessageCreateAction msg = chan.sendMessageEmbeds(embed).addActionRow(selectMenu);
        return msg;
    }
    public void askConfirmDenyMessage(StringSelectInteractionEvent evt, Member mem, String msgTitle, String msgBody) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(msgTitle);
        eb.setDescription(msgBody);
        eb.setAuthor(mem.getEffectiveName());
        List<SelectOption> selectedOpts = evt.getSelectedOptions();
        String optVal = null;
        for (SelectOption opt : selectedOpts) {
            // Check if value exists
            optVal = opt.getValue();
        }
        Button confirmBtn = Button.secondary("confirm|" + optVal, getConfigValue("Bot.Buttons.Confirm_Button"));
        Button denyBtn = Button.secondary("deny|" + optVal, getConfigValue("Bot.Buttons.Deny_Button"));
        evt.replyEmbeds(eb.build()).addActionRow(confirmBtn, denyBtn).setEphemeral(true).queue();
    }
    public MessageEmbed createTicketOpenConfirmationMessage() {
        return null;
    }
    public MessageEmbed createTicketCloseMessage() {
        return null;
    }
}
