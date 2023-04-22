package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import com.jaredscarito.models.PunishmentType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KickManager extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String id = evt.getModalId();
        String[] modelIdArgs = id.split("\\|");
        if (evt.getMember() == null) return;
        if (modelIdArgs.length <= 1) return;
        String isKickUser = modelIdArgs[0];
        if (!isKickUser.equals("kickUser")) return;
        String userId = modelIdArgs[1];
        ModalMapping modMap = evt.getValue("reason");
        if (modMap == null) return;
        String reason = modMap.getAsString();
        if (evt.getGuild() == null) return;
        Member kickUser = evt.getGuild().getMemberById(userId);
        if (kickUser == null) return;
        List<String> ruleIds = new ArrayList<>(Arrays.asList(modelIdArgs).subList(2, modelIdArgs.length));
        String fullUserName = kickUser.getUser().getName() + "#" + kickUser.getUser().getDiscriminator();
        evt.getGuild().kick(kickUser).reason(reason).queue((v) -> {
            API.getInstance().logPunishment(kickUser, evt.getMember(), PunishmentType.WARN, "", ruleIds, reason);
            Logger.log(ActionType.KICK_CREATE, evt.getMember(), kickUser, ruleIds, reason);
            evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "User `" + fullUserName + "` has been kicked...").build()).setEphemeral(true).queue();
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        if (!evt.getId().contains("kickUserRuleSelect")) return;
        String[] args = evt.getId().split("\\|");
        String userId = args[1];
        if (evt.getGuild() == null) return;
        Member mem = evt.getGuild().getMemberById(userId);
        if (mem == null) return;
        ManagerUtils.handleStringSelectMenu(evt, "kickUserRuleSelect", "kickUser");
    }
}
