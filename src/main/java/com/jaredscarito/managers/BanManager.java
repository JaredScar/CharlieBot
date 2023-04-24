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

public class BanManager extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String id = evt.getModalId();
        String[] modelIdArgs = id.split("\\|");
        if (evt.getMember() == null) return;
        if (modelIdArgs.length <= 1) return;
        String isbanUser = modelIdArgs[0];
        if (!isbanUser.equals("banUser")) return;
        String userId = modelIdArgs[1];
        ModalMapping modMap = evt.getValue("reason");
        ModalMapping durationFilter = evt.getValue("duration");
        ModalMapping durationType = evt.getValue("timeUnit");
        if (modMap == null || durationType == null || durationFilter == null) return;
        String reason = modMap.getAsString();
        String timeUnit = durationType.getAsString();
        TimeUnit unit = ManagerUtils.getTimeUnitFromString(timeUnit);
        if (evt.getGuild() == null) return;
        Member banUser = evt.getGuild().getMemberById(userId);
        if (banUser == null) return;
        String fullUserName = banUser.getUser().getName() + "#" + banUser.getUser().getDiscriminator();
        evt.getGuild().ban(banUser, Integer.parseInt(durationFilter.getAsString()), unit).reason(reason).queue((v) -> {
            Logger.log(ActionType.BAN_CREATE, evt.getMember(), banUser, reason);
            evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "User `" + fullUserName + "` has been banned...").build()).setEphemeral(true).queue();
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getComponentId().contains("banUserRuleSelect")) return;
        String[] args = evt.getComponentId().split("\\|");
        String userId = args[1];
        if (evt.getGuild() == null) return;
        Member mem = evt.getGuild().getMemberById(userId);
        if (mem == null) return;
        ManagerUtils.handleStringSelectMenu(evt, "banUserRuleSelect", "banUser");
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getComponentId().contains("banUserRuleSelectConfirm")) return;
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
        List<String> rulesBroken = rulesSelected.get(evt.getComponentId().replace("banUserRuleSelectConfirm", "warnUser"));
        if (rulesBroken == null) return;
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
    }
}
