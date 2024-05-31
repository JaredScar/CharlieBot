package com.jaredscarito.listeners.commands.general;

import com.jaredscarito.listeners.commands.*;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class GeneralCommandEventListener extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evt) {
        String commandName = evt.getFullCommandName().split(" ")[0];
        Member mem = evt.getMember();
        if (mem == null) return;
        if (!isEnabled(commandName)) return;
        List<Role> roles = mem.getRoles();
        if (!hasValidRole(commandName, roles)) return;
        switch (commandName.toLowerCase()) {
            case "bark" -> BarkCommand.invoke(evt);
            case "mute" -> MuteCommand.invoke(evt);
            case "kick" -> KickCommand.invoke(evt);
            case "blacklist" -> BlacklistCommand.invoke(evt);
            case "ban" -> BanCommand.invoke(evt);
            case "lockdown" -> LockdownCommand.invoke(evt);
            case "ticket" -> TicketCommand.invoke(evt);
            case "gamble" -> GambleCommand.invoke(evt);
            case "sticky" -> StickyCommand.invoke(evt);
            case "warn" -> WarnCommand.invoke(evt);
            case "points" -> PointsCommand.invoke(evt);
            case "history" -> HistoryCommand.invoke(evt);
            case "rules" -> RuleCommand.invoke(evt);
            case "help" -> HelpCommand.invoke(evt);
        }
    }
    public boolean isEnabled(String command) {
        String name = command.substring(0,1).toUpperCase() + command.substring(1).toLowerCase();
        return (Main.getInstance().getConfig().getBoolean("Bot.Commands." + name + ".Enabled"));
    }
    public boolean hasValidRole(String command, List<Role> roles) {
        String name = command.substring(0,1).toUpperCase() + command.substring(1).toLowerCase();
        List<String> required_roles = Main.getInstance().getConfig().getStringList("Bot.Commands." + name + ".Requires_Roles");
        for (Role role : roles) {
            if (required_roles.contains(role.getId())) return true;
        }
        return false;
    }
}
