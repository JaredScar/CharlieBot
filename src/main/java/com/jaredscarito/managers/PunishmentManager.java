package com.jaredscarito.managers;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class PunishmentManager extends ListenerAdapter {
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {}

    @Override
    public void onModalInteraction(ModalInteractionEvent evt) {}
}
