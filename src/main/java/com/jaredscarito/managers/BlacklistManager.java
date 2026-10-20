package com.jaredscarito.managers;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.logger.Logger;
import com.jaredscarito.models.ActionType;
import com.jaredscarito.models.PunishmentType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        if (modMap == null) return;
        String reason = modMap.getAsString();
        if (evt.getGuild() == null) return;
        Member blacklistUser = evt.getGuild().getMemberById(userId);
        if (blacklistUser == null) return;
        String fullUserName = blacklistUser.getUser().getName() + "#" + blacklistUser.getUser().getDiscriminator();
        // Validate the role BEFORE touching the member: previously every role was stripped first and
        // the blacklist role lookup afterwards, so a missing/invalid config left the member with nothing.
        Role blacklistRole = ManagerUtils.getBlacklistRole(evt.getGuild());
        if (blacklistRole == null) {
            evt.reply("❌ The blacklist role is not configured (or no longer exists). Set `Bot.Commands.Blacklist.Blacklist_Role` in config.yml to a valid role ID.")
                    .setEphemeral(true).queue();
            return;
        }
        List<String> ruleIds = ManagerUtils.getRulesSelected().remove(evt.getModalId());
        if (ruleIds == null) {
            evt.reply("❌ This prompt has expired. Please run the command again.").setEphemeral(true).queue();
            return;
        }
        Member moderator = evt.getMember();
        // Swap every removable role for the blacklist role in a single request
        List<Role> rolesToRemove = new ArrayList<>();
        Member self = evt.getGuild().getSelfMember();
        for (Role r : blacklistUser.getRoles()) {
            if (r.isManaged() || r.equals(blacklistRole) || !self.canInteract(r)) continue;
            rolesToRemove.add(r);
        }
        // Keep the removed roles so /blacklist remove can give them back. A member who is ALREADY blacklisted
        // only has the blacklist role left, so saving again would overwrite the original roles with nothing.
        boolean alreadyBlacklisted = blacklistUser.getRoles().contains(blacklistRole);
        evt.getGuild().modifyMemberRoles(blacklistUser, List.of(blacklistRole), rolesToRemove).queue((v) -> {
            if (!alreadyBlacklisted) {
                PunishmentEffects.saveBlacklistedRoles(blacklistUser.getIdLong(), rolesToRemove);
            }
            Logger.log(ActionType.BLACKLIST_CREATE, moderator, blacklistUser, reason);
            API.getInstance().logPunishment(blacklistUser, moderator, PunishmentType.BLACKLIST, "", ruleIds, reason);
            evt.replyEmbeds(API.getInstance().sendSuccessMessage(moderator, "Success", "User `" + fullUserName + "` has been blacklisted...").build()).setEphemeral(true).queue();
        }, (error) -> {
            Logger.log(error);
            evt.reply("❌ Failed to blacklist `" + fullUserName + "`: " + error.getMessage()).setEphemeral(true).queue();
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
                        + "|" + user.getId() + "|" + punisher.getId(), "Blacklist User " + user.getName() + "#" + user.getDiscriminator())
                .addComponents(ActionRow.of(inp))
                .build();
        evt.replyModal(modal).queue();
    }
}
