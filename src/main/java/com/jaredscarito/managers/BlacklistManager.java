package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BlacklistManager extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String id = evt.getModalId();
        String[] modelIdArgs = id.split("\\|");
        if (evt.getMember() == null) return;
        if (modelIdArgs.length <= 1) return;
        String isblacklistUser = modelIdArgs[0];
        if (!isblacklistUser.equals("blacklistUser")) return;
        String userId = modelIdArgs[1];
        ModalMapping modMap = evt.getValue("reason");
        ModalMapping durationFilter = evt.getValue("duration");
        ModalMapping durationType = evt.getValue("timeUnit");
        if (modMap == null || durationType == null || durationFilter == null) return;
        String reason = modMap.getAsString();
        String timeUnit = durationType.getAsString();
        TimeUnit unit = ManagerUtils.getTimeUnitFromString(timeUnit);
        if (evt.getGuild() == null) return;
        Member blacklistUser = evt.getGuild().getMemberById(userId);
        if (blacklistUser == null) return;
        String fullUserName = blacklistUser.getUser().getName() + "#" + blacklistUser.getUser().getDiscriminator();
        // TODO Actually blacklist them
        evt.getGuild().ban(blacklistUser, Integer.parseInt(durationFilter.getAsString()), unit).reason(reason).queue((v) -> {
            Logger.log(ActionType.MUTE_CREATE, evt.getMember(), blacklistUser, reason);
            evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "User `" + fullUserName + "` has been muted...").build()).setEphemeral(true).queue();
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getComponentId().contains("blacklistUserRuleSelect")) return;
        String[] args = evt.getComponentId().split("\\|");
        String userId = args[1];
        if (evt.getGuild() == null) return;
        Member mem = evt.getGuild().getMemberById(userId);
        if (mem == null) return;
        ManagerUtils.handleStringSelectMenu(evt, "blacklistUserRuleSelect", "blacklistUser");
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getComponentId().contains("blacklistUserRuleSelectConfirm")) return;
        String[] args = evt.getComponentId().split("\\|");
        String userId = args[1];
        String punisherId = args[2];
        if (evt.getGuild() == null) return;
        Member punisher = evt.getGuild().getMemberById(punisherId);
        Member mem = evt.getGuild().getMemberById(userId);
        if (punisher == null) return;
        if (mem == null) return;
        User user = mem.getUser();
        HashMap<String, List<String>> rulesSelected = ManagerUtils.getRulesSelected();
        List<String> rulesBroken = rulesSelected.get(evt.getComponentId().replace("blacklistUserRuleSelectConfirm", "blacklistUser"));
        if (rulesBroken == null) return;
        TextInput inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Reason for blacklist")
                .setMinLength(0)
                .setMaxLength(1024)
                .setRequired(true)
                .build();
        Modal modal = Modal.create("blacklistUser"
                        + "|" + user.getId(), "Blacklist User " + user.getName() + "#" + user.getDiscriminator())
                .addComponents(ActionRow.of(inp))
                .build();
        evt.replyModal(modal).queue();
    }
}
