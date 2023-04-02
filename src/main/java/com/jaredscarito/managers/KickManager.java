package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

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
        String fullUserName = kickUser.getUser().getName() + "#" + kickUser.getUser().getDiscriminator();
        evt.getGuild().kick(kickUser).reason(reason).queue((v) -> {
            Logger.log(ActionType.KICK_CREATE, evt.getMember(), kickUser, reason);
            evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "User `" + fullUserName + "` has been kicked...").build()).setEphemeral(true).queue();
        });
    }
}
