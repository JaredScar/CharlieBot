package com.jaredscarito.listeners.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class HistoryCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member member = evt.getMember();
        if (member == null || member.getUser().isBot()) return;
        if (evt.getSubcommandName() == null) return;

        switch (evt.getSubcommandName().toLowerCase()) {
            case "view":
                OptionMapping option = evt.getOption("member");
                if (option == null) return;
                Member target = option.getAsMember();
                if (target == null) return;
                // TODO We want to view the member's history
                break;
        }
    }
}
