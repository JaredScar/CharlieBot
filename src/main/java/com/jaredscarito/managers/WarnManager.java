package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import com.jaredscarito.models.PunishmentType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WarnManager extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String id = evt.getModalId();
        String[] modelIdArgs = id.split("\\|");
        if (evt.getMember() == null) return;
        if (modelIdArgs.length <= 1) return;
        String iswarnUser = modelIdArgs[0];
        if (!iswarnUser.equals("warnUser")) return;
        String userId = modelIdArgs[1];
        ModalMapping modMap = evt.getValue("reason");
        if (modMap == null) return;
        String reason = modMap.getAsString();
        if (evt.getGuild() == null) return;
        Member warnUser = evt.getGuild().getMemberById(userId);
        if (warnUser == null) return;
        List<String> ruleIds = rulesSelected.get(evt.getModalId());
        if (ruleIds == null) return;
        String fullUserName = warnUser.getUser().getName() + "#" + warnUser.getUser().getDiscriminator();
        API.getInstance().notifyPunishment(warnUser, evt.getMember(), PunishmentType.WARN, "", ruleIds, reason);
        API.getInstance().logPunishment(warnUser, evt.getMember(), PunishmentType.WARN, "", ruleIds, reason);
        Logger.log(ActionType.WARN_CREATE, evt.getMember(), warnUser, ruleIds, reason);
        evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "User `" + fullUserName + "` has been warned...").build()).setEphemeral(true).queue();
    }

    private static HashMap<String, List<String>> rulesSelected = new HashMap<>();
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getComponentId().contains("warnUserRuleSelect")) return;
        String[] args = evt.getComponentId().split("\\|");
        String userId = args[1];
        if (evt.getGuild() == null) return;
        Member mem = evt.getGuild().getMemberById(userId);
        if (mem == null) return;
        User user = mem.getUser();
        List<SelectOption> optionsSelected = evt.getSelectedOptions();
        List<String> rulesBroken = new ArrayList<>();
        for (SelectOption opt : optionsSelected) {
            rulesBroken.add(opt.getValue());
        }
        String ruleSelectId = evt.getComponentId().replace("warnUserRuleSelect", "warnUser");
        if (rulesSelected.containsKey(ruleSelectId)) {
            List<String> rulesBrokenList = rulesSelected.get(ruleSelectId);
            for (String rule : rulesBroken) {
                if (rulesBrokenList.contains(rule))
                    rulesBrokenList.remove(rule);
                else
                    rulesBrokenList.add(rule);
            }
            rulesSelected.put(ruleSelectId, rulesBrokenList);
        } else {
            rulesSelected.put(ruleSelectId, rulesBroken);
        }
        List<String> rulesBrokenList = rulesSelected.get(ruleSelectId);
        EmbedBuilder eb = new EmbedBuilder();
        MessageEmbed embed = evt.getMessage().getEmbeds().get(0);
        eb.setTitle(embed.getTitle());
        eb.setFooter(embed.getFooter().getText(), embed.getFooter().getIconUrl());
        eb.setThumbnail(embed.getThumbnail().getUrl());
        eb.addField("Rules broken:", String.join(", ", rulesBrokenList), false);
        evt.editMessageEmbeds((eb.build())).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getComponentId().contains("warnUserRuleSelectConfirm")) return;
        String[] args = evt.getComponentId().split("\\|");
        String userId = args[1];
        String punisherId = args[2];
        if (evt.getGuild() == null) return;
        Member punisher = evt.getGuild().getMemberById(punisherId);
        Member mem = evt.getGuild().getMemberById(userId);
        if (punisher == null) return;
        if (mem == null) return;
        User user = mem.getUser();
        List<String> rulesBroken = rulesSelected.get(evt.getComponentId().replace("warnUserRuleSelectConfirm", "warnUser"));
        if (rulesBroken == null) return;
        TextInput inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Reason for warning")
                .setMinLength(0)
                .setMaxLength(1024)
                .setRequired(true)
                .build();
        Modal modal = Modal.create("warnUser"
                        + "|" + user.getId() + "|" + punisher.getId(), "Warn User " + user.getName() + "#" + user.getDiscriminator())
                .addComponents(ActionRow.of(inp))
                .build();
        evt.replyModal(modal).queue();
    }
}
