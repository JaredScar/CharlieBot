package com.jaredscarito.managers;

import com.jaredscarito.listeners.commands.ReportCommand;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

/** Right-click "Report message", and the reason modal that follows it. */
public class ReportManager extends ListenerAdapter {
    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent evt) {
        if (!evt.getName().equals("Report message")) return;
        ReportCommand.openMessageReport(evt);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        if (!evt.getModalId().startsWith("reportMessage|")) return;
        ReportCommand.finishMessageReport(evt);
    }
}
