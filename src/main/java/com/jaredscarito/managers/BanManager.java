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
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BanManager extends ListenerAdapter {
    /** Duration chosen on /tempban, kept until the reason modal is submitted or the prompt is cancelled. */
    private static final ConcurrentHashMap<String, PendingTempban> PENDING_TEMPBANS = new ConcurrentHashMap<>();

    public static void rememberTempban(String key, String raw, long millis) {
        PENDING_TEMPBANS.put(key, new PendingTempban(raw, millis));
    }

    public static void forgetTempban(String key) {
        if (key != null) PENDING_TEMPBANS.remove(key);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent evt) {
        String id = evt.getModalId();
        String[] modelIdArgs = id.split("\\|");
        if (evt.getMember() == null) return;
        if (modelIdArgs.length <= 1) return;
        String flow = modelIdArgs[0];
        boolean temporary = flow.equals("tempbanUser");
        if (!flow.equals("banUser") && !temporary) return;
        String userId = modelIdArgs[1];
        ModalMapping modMap = evt.getValue("reason");
        if (modMap == null) return;
        String reason = modMap.getAsString();
        if (evt.getGuild() == null) return;
        Member banUser = evt.getGuild().getMemberById(userId);
        if (banUser == null) return;
        PendingTempban pending = temporary ? PENDING_TEMPBANS.remove(id) : null;
        if (temporary && pending == null) {
            evt.reply("❌ This prompt has expired. Please run the command again.").setEphemeral(true).queue();
            return;
        }
        String fullUserName = banUser.getUser().getName() + "#" + banUser.getUser().getDiscriminator();
        List<String> ruleIds = ManagerUtils.getRulesSelected().remove(evt.getModalId());
        if (ruleIds == null) {
            evt.reply("❌ This prompt has expired. Please run the command again.").setEphemeral(true).queue();
            return;
        }
        Member moderator = evt.getMember();
        // Only record the punishment once Discord has actually accepted the ban.
        // A temporary ban does not wipe a week of messages; a permanent one still does.
        int deleteFor = temporary ? 0 : 168;
        TimeUnit deleteUnit = temporary ? TimeUnit.SECONDS : TimeUnit.HOURS;
        evt.getGuild().ban(banUser, deleteFor, deleteUnit).reason(reason).queue((v) -> {
            String length = temporary ? pending.raw : "";
            API.getInstance().logPunishment(banUser, moderator, PunishmentType.BAN, length, ruleIds, reason);
            if (temporary) {
                PunishmentEffects.trackBan(banUser.getIdLong(), pending.millis);
            } else {
                // a permanent ban must not be lifted by an older temporary one
                PunishmentEffects.clearBan(banUser.getIdLong());
            }
            String note = temporary ? reason + " (" + PunishmentEffects.describeDuration(pending.millis) + ")" : reason;
            Logger.log(ActionType.BAN_CREATE, moderator, banUser, note);
            String success = temporary
                    ? "User `" + fullUserName + "` has been banned " + PunishmentEffects.describeDuration(pending.millis) + "."
                    : "User `" + fullUserName + "` has been banned...";
            evt.replyEmbeds(API.getInstance().sendSuccessMessage(moderator, "Success", success).build()).setEphemeral(true).queue();
        }, (error) -> {
            Logger.log(error);
            evt.reply("❌ Failed to ban `" + fullUserName + "`: " + error.getMessage()).setEphemeral(true).queue();
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        if (evt.getMember() == null) return;
        String componentId = evt.getComponentId();
        String selectPrefix;
        String flow;
        if (componentId.contains("tempbanUserRuleSelect")) {
            selectPrefix = "tempbanUserRuleSelect";
            flow = "tempbanUser";
        } else if (componentId.contains("banUserRuleSelect")) {
            selectPrefix = "banUserRuleSelect";
            flow = "banUser";
        } else {
            return;
        }
        String[] args = componentId.split("\\|");
        if (args.length < 2) return;
        String userId = args[1];
        if (evt.getGuild() == null) return;
        Member mem = evt.getGuild().getMemberById(userId);
        if (mem == null) return;
        ManagerUtils.handleStringSelectMenu(evt, selectPrefix, flow);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        if (evt.getMember() == null) return;
        String componentId = evt.getComponentId();
        boolean temporary = componentId.contains("tempbanUserRuleSelectConfirm");
        if (!temporary && !componentId.contains("banUserRuleSelectConfirm")) return;
        String flow = temporary ? "tempbanUser" : "banUser";
        String[] args = componentId.split("\\|");
        if (args.length < 3) return;
        String userId = args[1];
        String punisherId = args[2];
        if (evt.getGuild() == null) return;
        Member punisher = evt.getGuild().getMemberById(punisherId);
        Member mem = evt.getGuild().getMemberById(userId);
        if (punisher == null) return;
        if (mem == null) return;
        User user = mem.getUser();
        String flowKey = flow + "|" + user.getId() + "|" + punisher.getId();
        HashMap<String, List<String>> rulesSelected = ManagerUtils.getRulesSelected();
        List<String> rulesBroken = rulesSelected.get(flowKey);
        if (rulesBroken == null) return;
        if (temporary && !PENDING_TEMPBANS.containsKey(flowKey)) {
            evt.reply("❌ This prompt has expired. Please run the command again.").setEphemeral(true).queue();
            return;
        }
        TextInput inp = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder(temporary ? "Reason for the temporary ban" : "Reason for ban")
                .setMinLength(0)
                .setMaxLength(1024)
                .setRequired(true)
                .build();
        String title = (temporary ? "Tempban " : "Ban User ") + user.getName();
        if (title.length() > 45) title = title.substring(0, 45);
        Modal modal = Modal.create(flowKey, title)
                .addComponents(ActionRow.of(inp))
                .build();
        evt.replyModal(modal).queue();
    }

    private static final class PendingTempban {
        final String raw;
        final long millis;

        PendingTempban(String raw, long millis) {
            this.raw = raw;
            this.millis = millis;
        }
    }
}
