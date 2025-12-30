package com.jaredscarito.listeners.commands.general;

import java.util.List;

import com.jaredscarito.listeners.commands.BanCommand;
import com.jaredscarito.listeners.commands.BarkCommand;
import com.jaredscarito.listeners.commands.BlacklistCommand;
import com.jaredscarito.listeners.commands.GambleCommand;
import com.jaredscarito.listeners.commands.HelpCommand;
import com.jaredscarito.listeners.commands.HistoryCommand;
import com.jaredscarito.listeners.commands.KickCommand;
import com.jaredscarito.listeners.commands.LockdownCommand;
import com.jaredscarito.listeners.commands.MuteCommand;
import com.jaredscarito.listeners.commands.PointsCommand;
import com.jaredscarito.listeners.commands.RuleCommand;
import com.jaredscarito.listeners.commands.StickyCommand;
import com.jaredscarito.listeners.commands.TicketCommand;
import com.jaredscarito.listeners.commands.WarnCommand;
import com.jaredscarito.main.Main;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GeneralCommandEventListener extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evt) {
        String commandName = evt.getFullCommandName().split(" ")[0];
        Member mem = evt.getMember();
        if (mem == null) {
            evt.reply("❌ Error: Unable to identify member.").setEphemeral(true).queue();
            return;
        }
        
        if (!isEnabled(commandName)) {
            evt.reply("❌ This command is currently disabled.").setEphemeral(true).queue();
            return;
        }
        
        List<Role> roles = mem.getRoles();
        if (!hasValidRole(commandName, roles)) {
            evt.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        try {
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
                default -> evt.reply("❌ Unknown command: " + commandName).setEphemeral(true).queue();
            }
        } catch (Exception e) {
            evt.reply("❌ An error occurred while processing the command.").setEphemeral(true).queue();
            e.printStackTrace();
        }
    }
    public boolean isEnabled(String command) {
        String name = command.substring(0,1).toUpperCase() + command.substring(1).toLowerCase();
        return (Main.getInstance().getConfig().getBoolean("Bot.Commands." + name + ".Enabled"));
    }
    public boolean hasValidRole(String command, List<Role> roles) {
        String name = command.substring(0,1).toUpperCase() + command.substring(1).toLowerCase();
        List<String> required_roles = Main.getInstance().getConfig().getStringList("Bot.Commands." + name + ".Requires_Roles");
        
        // If no roles are required, allow access
        if (required_roles == null || required_roles.isEmpty()) {
            return true;
        }
        
        // Check if user has any of the required roles
        for (Role role : roles) {
            if (required_roles.contains(role.getId())) return true;
        }
        return false;
    }
}
