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
        if (!isLockdown.equals("enableLockdown") && !isLockdown.equals("disableLockdown")) return;
        ModalMapping modMap = evt.getValue("reason");
        if (modMap == null) return;
        String reason = modMap.getAsString();
        String channelId = modelIdArgs[1];
        TextChannel chan = evt.getJDA().getTextChannelById(channelId);
        if (chan == null) return;
        switch (isLockdown) {
            case "enableLockdown":
                this.lockChannel(chan, evt.getMember(), reason);
                break;
            case "disableLockdown":
                this.unlockChannel(chan, evt.getMember(), reason);
                break;
        }
    }
    private void lockChannel(TextChannel chan, Member mem, String reason) {
        List<PermissionOverride> permissionOverrides = chan.getPermissionOverrides();
        Collection<Permission> allows = new ArrayList<>();
        Collection<Permission> denies = new ArrayList<>();
        denies.add(Permission.MESSAGE_SEND);
        denies.add(Permission.MESSAGE_ADD_REACTION);
        denies.add(Permission.MESSAGE_SEND_IN_THREADS);
        List<String> disregardRoles = Main.getInstance().getConfig().getStringList("Bot.Commands.Lockdown.Requires_Roles");
        for (PermissionOverride permO : permissionOverrides) {
            if (permO.getRole() == null) continue;
            long roleId = permO.getRole().getIdLong();
            if (disregardRoles.contains(permO.getRole().getId())) continue;

            if (permO.getRole().getPermissions().contains(Permission.CREATE_PUBLIC_THREADS))
                denies.add(Permission.CREATE_PUBLIC_THREADS);
            if (permO.getRole().getPermissions().contains(Permission.CREATE_PRIVATE_THREADS))
                denies.add(Permission.CREATE_PRIVATE_THREADS);
            chan.getManager().putRolePermissionOverride(roleId, allows, denies).queue();
        }
        Logger.log(ActionType.LOCKDOWN_START, mem, chan, reason);
        chan.sendMessage("**\uD83D\uDD12 LOCKED CHANNEL** -- This channel has been locked by " + mem.getAsMention() + " for reason: `" + reason + "`...").queue();
    }
    private void unlockChannel(TextChannel chan, Member mem, String reason) {
        List<PermissionOverride> permissionOverrides = chan.getPermissionOverrides();
        Collection<Permission> allows = new ArrayList<>();
        Collection<Permission> denies = new ArrayList<>();
        allows.add(Permission.MESSAGE_SEND);
        allows.add(Permission.MESSAGE_ADD_REACTION);
        allows.add(Permission.MESSAGE_SEND_IN_THREADS);
        List<String> disregardRoles = Main.getInstance().getConfig().getStringList("Bot.Commands.Lockdown.Requires_Roles");
        for (PermissionOverride permO : permissionOverrides) {
            if (permO.getRole() == null) continue;
            long roleId = permO.getRole().getIdLong();
            if (disregardRoles.contains(permO.getRole().getId())) continue;

            if (permO.getRole().getPermissions().contains(Permission.CREATE_PUBLIC_THREADS))
                allows.add(Permission.CREATE_PUBLIC_THREADS);
            if (permO.getRole().getPermissions().contains(Permission.CREATE_PRIVATE_THREADS))
                allows.add(Permission.CREATE_PRIVATE_THREADS);
            chan.getManager().putRolePermissionOverride(roleId, allows, denies).queue();
        }
        Logger.log(ActionType.LOCKDOWN_END, mem, chan, reason);
        chan.sendMessage("**\uD83D\uDD13 UNLOCKED CHANNEL** -- This channel has been unlocked by " + mem.getAsMention() + " for reason: `" + reason + "`...").queue();
    }
}
