package com.jaredscarito.managers;

import com.jaredscarito.models.PunishmentType;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Collections;

/**
 * Handles the interactions that belong to "/<punishment> remove" (pick one from history, confirm it)
 * and the shared Cancel button of the "/<punishment> add" rule-selection flow.
 */
public class PunishmentManager extends ListenerAdapter {
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        String menuId = evt.getComponentId();
        // Menu ids look like "banRemove|<memberId>"
        String[] idArgs = menuId.split("\\|");
        if (idArgs.length < 2 || !idArgs[0].endsWith("Remove")) return;
        if (evt.getSelectedOptions().isEmpty()) return;
        String selected = evt.getSelectedOptions().get(0).getValue();
        int pid;
        try {
            pid = Integer.parseInt(selected);
        } catch (NumberFormatException ex) {
            return;
        }
        String pName = idArgs[0].substring(0, idArgs[0].length() - "Remove".length()).toUpperCase();
        PunishmentType punishmentType = null;
        for (PunishmentType pt : PunishmentType.values()) {
            if (pName.equals(pt.name())) punishmentType = pt;
        }
        if (punishmentType == null) {
            evt.reply("❌ Unknown punishment type.").setEphemeral(true).queue();
            return;
        }
        if (evt.getGuild() == null) return;
        // a plain id: the user does not have to be in the server (banned users aren't)
        long targetId;
        try {
            targetId = Long.parseLong(idArgs[1]);
        } catch (NumberFormatException ex) {
            return;
        }
        ManagerUtils.openModalWithPunishmentData(evt, pid, targetId, punishmentType);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evt) {
        String id = evt.getComponentId();
        if (id.startsWith("punishRemoveConfirm|")) {
            ManagerUtils.handleButtonPunishmentRemoval(evt);
        } else if (id.equals("punishRemoveCancel")) {
            evt.editMessage("Cancelled.").setEmbeds(Collections.emptyList()).setComponents(Collections.emptyList()).queue();
        } else if (id.contains("RuleSelectDeny")) {
            // "<type>RuleSelectDeny|<memberId>|<punisherId>" -> drop the saved rule selection and close the prompt
            ManagerUtils.getRulesSelected().remove(id.replace("RuleSelectDeny", ""));
            evt.editMessage("Cancelled.").setEmbeds(Collections.emptyList()).setComponents(Collections.emptyList()).queue();
        }
    }
}
