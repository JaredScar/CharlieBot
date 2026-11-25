package com.jaredscarito.listeners.commands;

import com.jaredscarito.logger.Logger;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ReportCommand {
    private static final long COOLDOWN_MS = 30_000L;
    private static final Pattern MESSAGE_LINK = Pattern.compile(
            "https://(?:[a-z0-9-]+\\.)?discord(?:app)?\\.com/channels/\\d+/\\d+/\\d+");
    private static final ConcurrentHashMap<Long, Long> nextReportAt = new ConcurrentHashMap<>();

    public static void invoke(SlashCommandInteractionEvent evt) {
        Member reporter = evt.getMember();
        if (reporter == null || evt.getGuild() == null) {
            evt.reply("❌ Use this command in the server.").setEphemeral(true).queue();
            return;
        }
        OptionMapping memberOpt = evt.getOption("member");
        OptionMapping reasonOpt = evt.getOption("reason");
        if (memberOpt == null || reasonOpt == null) {
            evt.reply("❌ Choose a member and a reason.").setEphemeral(true).queue();
            return;
        }
        User target = memberOpt.getAsUser();
        if (target.getIdLong() == reporter.getIdLong()) {
            evt.reply("❌ You can't report yourself.").setEphemeral(true).queue();
            return;
        }
        String reason = reasonOpt.getAsString().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            evt.reply("❌ The reason has to be between 1 and 500 characters.").setEphemeral(true).queue();
            return;
        }
        String extra = "";
        OptionMapping linkOpt = evt.getOption("link");
        if (linkOpt != null && !linkOpt.getAsString().isBlank()) {
            String link = linkOpt.getAsString().trim();
            if (!MESSAGE_LINK.matcher(link).matches()) {
                evt.reply("❌ That link doesn't look like a Discord message link.").setEphemeral(true).queue();
                return;
            }
            extra = "Message: " + link;
        }
        if (!tryConsume(reporter.getIdLong())) {
            evt.reply("❌ Wait a few seconds before sending another report.").setEphemeral(true).queue();
            return;
        }
        evt.reply(deliver(reporter, target, reason, extra)).setEphemeral(true).queue();
    }

    public static void openMessageReport(MessageContextInteractionEvent evt) {
        if (!enabled()) {
            evt.reply("❌ Reports are turned off.").setEphemeral(true).queue();
            return;
        }
        Member reporter = evt.getMember();
        Message message = evt.getTarget();
        if (reporter == null || evt.getGuild() == null || message == null) {
            evt.reply("❌ Use this on a message in the server.").setEphemeral(true).queue();
            return;
        }
        if (message.getAuthor().getIdLong() == reporter.getIdLong()) {
            evt.reply("❌ You can't report your own message.").setEphemeral(true).queue();
            return;
        }
        if (coolingDown(reporter.getIdLong())) {
            evt.reply("❌ Wait a few seconds before sending another report.").setEphemeral(true).queue();
            return;
        }
        TextInput reason = TextInput.create("reason", "What should staff know?", TextInputStyle.PARAGRAPH)
                .setMinLength(1)
                .setMaxLength(500)
                .setRequired(true)
                .build();
        String modalId = "reportMessage|" + message.getChannel().getId() + "|" + message.getId() + "|" + message.getAuthor().getId();
        evt.replyModal(Modal.create(modalId, "Report message").addComponents(ActionRow.of(reason)).build()).queue();
    }

    public static void finishMessageReport(ModalInteractionEvent evt) {
        Member reporter = evt.getMember();
        if (reporter == null || evt.getGuild() == null) {
            evt.reply("❌ Use this in the server.").setEphemeral(true).queue();
            return;
        }
        String[] args = evt.getModalId().split("\\|");
        var reasonMap = evt.getValue("reason");
        if (args.length < 4 || reasonMap == null) {
            evt.reply("❌ That report could not be read. Try again.").setEphemeral(true).queue();
            return;
        }
        String reason = reasonMap.getAsString().trim();
        if (reason.isEmpty()) {
            evt.reply("❌ A reason is required.").setEphemeral(true).queue();
            return;
        }
        if (!tryConsume(reporter.getIdLong())) {
            evt.reply("❌ Wait a few seconds before sending another report.").setEphemeral(true).queue();
            return;
        }
        long channelId;
        long messageId;
        long authorId;
        try {
            channelId = Long.parseLong(args[1]);
            messageId = Long.parseLong(args[2]);
            authorId = Long.parseLong(args[3]);
        } catch (NumberFormatException ex) {
            evt.reply("❌ That report could not be read. Try again.").setEphemeral(true).queue();
            return;
        }
        Guild guild = evt.getGuild();
        String jump = "https://discord.com/channels/" + guild.getId() + "/" + channelId + "/" + messageId;
        evt.deferReply(true).queue(hook -> {
            GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelId);
            if (channel == null) {
                guild.getJDA().retrieveUserById(authorId).queue(
                        author -> hook.editOriginal(deliver(reporter, author, reason, "Message: " + jump + "\n(message could not be loaded)")).queue(),
                        error -> hook.editOriginal(deliver(reporter, null, reason, "Message: " + jump)).queue());
                return;
            }
            channel.retrieveMessageById(messageId).queue(message -> {
                String content = message.getContentDisplay();
                String extra = "Message: " + message.getJumpUrl();
                if (content != null && !content.isBlank()) {
                    String shown = content.length() <= 300 ? content : content.substring(0, 297) + "...";
                    extra += "\n" + shown;
                }
                hook.editOriginal(deliver(reporter, message.getAuthor(), reason, extra)).queue();
            }, error -> guild.getJDA().retrieveUserById(authorId).queue(
                    author -> hook.editOriginal(deliver(reporter, author, reason, "Message: " + jump + "\n(message already deleted)")).queue(),
                    missing -> hook.editOriginal(deliver(reporter, null, reason, "Message: " + jump)).queue()));
        }, Logger::log);
    }

    private static String deliver(Member reporter, User target, String reason, String extra) {
        StringBuilder details = new StringBuilder(reason);
        if (extra != null && !extra.isBlank()) details.append("\n").append(extra);
        boolean posted = Logger.logReport(reporter, target, details.toString());
        return posted
                ? "✅ Report sent to staff."
                : "✅ Report saved. The staff log channel is not set up, so please also tell a moderator.";
    }

    private static boolean enabled() {
        return Main.getInstance().getConfig().getBoolean("Bot.Commands.Report.Enabled");
    }

    private static boolean coolingDown(long userId) {
        Long next = nextReportAt.get(userId);
        return next != null && System.currentTimeMillis() < next;
    }

    private static boolean tryConsume(long userId) {
        long now = System.currentTimeMillis();
        boolean[] allowed = {true};
        nextReportAt.compute(userId, (id, prev) -> {
            if (prev != null && now < prev) {
                allowed[0] = false;
                return prev;
            }
            return now + COOLDOWN_MS;
        });
        return allowed[0];
    }
}
