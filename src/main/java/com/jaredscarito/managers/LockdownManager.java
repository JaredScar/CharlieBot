package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import com.jaredscarito.models.ActionType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LockdownManager extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String id = evt.getModalId();
        String[] modelIdArgs = id.split("\\|");
        if (evt.getMember() == null) return;
        if (modelIdArgs.length <= 1) return;
        String isLockdown = modelIdArgs[0];
        if (!isLockdown.equals("lockdownEnable") && !isLockdown.equals("lockdownDisable")) return;
        ModalMapping modMap = evt.getValue("reason");
        if (modMap == null) return;
        String reason = modMap.getAsString();
        String channelId = modelIdArgs[1];
        TextChannel chan = evt.getJDA().getTextChannelById(channelId);
        if (chan == null) return;
        switch (isLockdown) {
            case "lockdownEnable":
                this.lockChannel(evt, chan, evt.getMember(), reason);
                break;
            case "lockdownDisable":
                this.unlockChannel(evt, chan, evt.getMember(), reason);
                break;
        }
    }
    private void lockChannel(ModalInteractionEvent evt, TextChannel chan, Member mem, String reason) {
        if (ManagerUtils.isChannelLockedDown(chan)) {
            evt.reply("❌ This channel is already locked down. Use `/lockdown disable` to lift it.").setEphemeral(true).queue();
            return;
        }
        List<PermissionOverride> permissionOverrides = chan.getPermissionOverrides();
        boolean completed = ManagerUtils.handleRolePermissionsOnLockdown(chan, permissionOverrides);
        if (completed) {
            Logger.log(ActionType.LOCKDOWN_START, mem, chan, reason);
            evt.reply("**\uD83D\uDD12 LOCKED CHANNEL** -- This channel has been locked by " + mem.getAsMention() + " for reason: `" + reason + "`...").queue();
        } else {
            evt.reply("❌ Could not lock down this channel: it has no role permission overrides to restrict, or something went wrong (see `logs/error.txt`).").setEphemeral(true).queue();
        }
    }
    private void unlockChannel(ModalInteractionEvent evt, TextChannel chan, Member mem, String reason) {
        if (!ManagerUtils.isChannelLockedDown(chan)) {
            evt.reply("❌ This channel is not locked down.").setEphemeral(true).queue();
            return;
        }
        boolean completed = ManagerUtils.handleRolePermissionsAfterLockdown(chan);
        if (completed) {
            Logger.log(ActionType.LOCKDOWN_END, mem, chan, reason);
            evt.reply("**\uD83D\uDD13 UNLOCKED CHANNEL** -- This channel has been unlocked by " + mem.getAsMention() + " for reason: `" + reason + "`...").queue();
        } else {
            evt.reply("❌ Failed to lift the lockdown. Check `logs/error.txt` for details.").setEphemeral(true).queue();
        }
    }
}
