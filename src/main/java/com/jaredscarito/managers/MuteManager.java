package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import com.jaredscarito.models.PunishmentType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

public class MuteManager extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent evt) {}

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String id = evt.getModalId();
        String[] modelIdArgs = id.split("\\|");
        if (evt.getMember() == null) return;
        if (modelIdArgs.length <= 1) return;
        String ismuteUser = modelIdArgs[0];
        if (!ismuteUser.equals("muteUser")) return;
        String userId = modelIdArgs[1];
        ModalMapping modMap = evt.getValue("reason");
        ModalMapping durationFilter = evt.getValue("duration");
        if (modMap == null || durationFilter == null) return;
        String reason = modMap.getAsString();
        if (evt.getGuild() == null) return;
        Member muteUser = evt.getGuild().getMemberById(userId);
        if (muteUser == null) return;
        String fullUserName = muteUser.getUser().getName() + "#" + muteUser.getUser().getDiscriminator();
        Logger.log(ActionType.MUTE_CREATE, evt.getMember(), muteUser, reason);
        HashMap<String, List<String>> rulesSelected = ManagerUtils.getRulesSelected();
        List<String> ruleIds = rulesSelected.get(evt.getModalId());
        API.getInstance().logPunishment(muteUser, evt.getMember(), PunishmentType.MUTE, durationFilter.getAsString(), ruleIds, reason);
        if (ManagerUtils.handleMuteMember(muteUser))
            evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "User `" + fullUserName + "` has been muted...").build()).setEphemeral(true).queue();
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getComponentId().contains("muteUserRuleSelect")) return;
        String[] args = evt.getComponentId().split("\\|");
        String userId = args[1];
        if (evt.getGuild() == null) return;
        Member mem = evt.getGuild().getMemberById(userId);
        if (mem == null) return;
        ManagerUtils.handleStringSelectMenu(evt, "muteUserRuleSelect", "muteUser");
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getComponentId().contains("muteUserRuleSelectConfirm")) return;
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
        List<String> rulesBroken = rulesSelected.get(evt.getComponentId().replace("muteUserRuleSelectConfirm", "muteUser"));
        if (rulesBroken == null) return;
        TextInput inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Reason for mute")
                .setMinLength(0)
                .setMaxLength(1024)
                .setRequired(true)
                .build();
        TextInput numInput = TextInput.create("duration", "Duration", TextInputStyle.SHORT)
                .setPlaceholder("1")
                .setMinLength(0)
                .setMaxLength(999).setRequired(true).build();
        Modal modal = Modal.create("muteUser"
                        + "|" + user.getId(), "Mute User " + user.getName() + "#" + user.getDiscriminator())
                .addComponents(ActionRow.of(numInput), ActionRow.of(inp))
                .build();
        evt.replyModal(modal).queue();
    }
}
