package com.jaredscarito.managers;

import com.jaredscarito.models.PunishmentType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import java.util.List;

public class PunishmentManager extends ListenerAdapter {
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evt) {
        List<SelectOption> selOptions = evt.getSelectedOptions();
        SelectOption selectOption = null;
        for (SelectOption opt : selOptions) {
            selectOption = opt;
        }
        if (selectOption == null) return;
        if (!validInt(selectOption.getValue())) return;
        if (evt.getSelectMenu().getId() == null) return;
        if (evt.getSelectMenu().getId().split("\\|").length <= 1) return;
        String pName = evt.getSelectMenu().getId().split("\\|")[0].replace("Remove", "").toUpperCase();
        PunishmentType punishmentType = null;
        for (PunishmentType pt : PunishmentType.values()) {
            if (pName.equals(pt.name()))
                punishmentType = pt;
        }
        String punishedId = evt.getSelectMenu().getId().split("\\|")[1];
        if (evt.getGuild() == null) return;
        Member mem = evt.getGuild().getMemberById(punishedId);
        if (mem == null) return;

        if (punishmentType == null) {
            // TODO Error encountered... Send error msg
            return;
        }

        ManagerUtils.openModalWithPunishmentData(evt, Integer.parseInt(selectOption.getValue()), mem, punishmentType);
    }

    private boolean validInt(String i) {
        try {
            Integer.parseInt(i);
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent evt) {
        ManagerUtils.handleModalPunishmentRemoval(evt, 0);
    }
}
