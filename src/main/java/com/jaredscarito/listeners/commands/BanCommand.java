package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.TreeMap;

public class BanCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        OptionMapping opt = evt.getOption("user");
        if (opt == null) return;
        if (opt.getAsMember() == null) return;
        User user = opt.getAsMember().getUser();
        TextInput inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Reason for ban")
                .setMinLength(0)
                .setMaxLength(1024)
                .setRequired(true)
                .build();
        TextInput numInput = TextInput.create("duration", "Duration", TextInputStyle.SHORT)
                .setPlaceholder("1")
                .setMinLength(0)
                .setMaxLength(999).setRequired(true).build();
        StringSelectMenu selectionMenu = StringSelectMenu.create("timeUnit")
                .addOption("Forever", "Forever")
                .addOption("Seconds", "second")
                .addOption("Minutes", "minute")
                .addOption("Hours", "hour")
                .addOption("Days", "day")
                .build();
        Modal modal = Modal.create("banUser"
                        + "|" + user.getId(), "Ban User " + user.getName() + "#" + user.getDiscriminator())
                .addComponents(ActionRow.of(selectionMenu), ActionRow.of(numInput), ActionRow.of(inp))
                .build();
        evt.replyModal(modal).queue();
        StringSelectMenu.Builder builder = StringSelectMenu.create("banUserRuleSelect|" + user.getId()).setMinValues(1);
        TreeMap<String, String> ruleList = API.getInstance().getRules();
        for (String ruleId : ruleList.keySet()) {
            String rule = ruleList.get(ruleId);
            if (rule.length() > 100) {
                rule = rule.substring(0, 100);
            }
            builder.addOption(ruleId, ruleId, rule);
        }
        StringSelectMenu ruleSelect = builder.build();
        evt.reply("").addActionRow(ruleSelect).setEphemeral(true).queue();
    }
}
