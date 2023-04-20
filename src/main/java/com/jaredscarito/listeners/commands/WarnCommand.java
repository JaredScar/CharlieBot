package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.awt.*;
import java.util.TreeMap;

public class WarnCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        OptionMapping opt = evt.getOption("member");
        if (opt == null) return;
        if (opt.getAsMember() == null) return;
        User user = opt.getAsMember().getUser();
        StringSelectMenu.Builder builder = StringSelectMenu.create("warnUserRuleSelect|" + user.getId() + "|"
                + mem.getUser().getId()).setMinValues(1);
        TreeMap<String, String> ruleList = API.getInstance().getRules();
        for (String ruleId : ruleList.keySet()) {
            String rule = ruleList.get(ruleId);
            if (rule.length() > 100) {
                rule = rule.substring(0, 100);
            }
            builder.addOption(ruleId, ruleId, rule);
        }
        StringSelectMenu ruleSelect = builder.build();
        Button submitButton = Button.success("warnUserRuleSelectConfirm|" + user.getId() + "|"
                + mem.getUser().getId(), "Submit");
        Button cancelButton = Button.danger("warnUserRuleSelectDeny|" + user.getId() + "|"
                + mem.getUser().getId(), "Cancel");
        EmbedBuilder eb = new EmbedBuilder();
        eb.setFooter(mem.getUser().getName() + "#" + mem.getUser().getDiscriminator(), mem.getUser().getAvatarUrl());
        eb.setThumbnail(user.getAvatarUrl());
        eb.setTitle("Warn " + user.getName() + "#" + user.getDiscriminator());
        eb.setColor(Color.YELLOW);
        evt.replyEmbeds(eb.build()).addActionRow(ruleSelect).addActionRow(cancelButton, submitButton).setEphemeral(true).queue();
    }
}
