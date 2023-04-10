package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

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
        TimeUnit unit = this.getTimeUnitFromString(timeUnit);
        if (evt.getGuild() == null) return;
        Member banUser = evt.getGuild().getMemberById(userId);
        if (banUser == null) return;
        String fullUserName = banUser.getUser().getName() + "#" + banUser.getUser().getDiscriminator();
        evt.getGuild().ban(banUser, Integer.parseInt(durationFilter.getAsString()), unit).reason(reason).queue((v) -> {
            Logger.log(ActionType.BAN_CREATE, evt.getMember(), banUser, reason);
            evt.replyEmbeds(API.getInstance().sendSuccessMessage(evt.getMember(), "Success", "User `" + fullUserName + "` has been banned...").build()).setEphemeral(true).queue();
        });
    }
    private TimeUnit getTimeUnitFromString(String str) {
        switch (str.toLowerCase()) {
            case "second":
                return TimeUnit.SECONDS;
            case "minute":
                return TimeUnit.MINUTES;
            case "hour":
                return TimeUnit.HOURS;
            case "day":
                return TimeUnit.DAYS;
        }
        return TimeUnit.DAYS;
    }
}
