package com.jaredscarito.listeners.api;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jaredscarito.logger.Logger;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;

/**
 * Writes a ticket channel out as a single HTML file that follows Discord's dark theme:
 * server column, channel sidebar, message pane and member list, with grouped messages,
 * replies, embeds, attachments, reactions and buttons.
 */
public final class TicketTranscript {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", Locale.US);
    private static final long GROUP_WINDOW_MS = 7L * 60L * 1000L;
    private static final int HISTORY_LIMIT = 5000;
    /** Keep the HTML small enough to upload back to Discord after images are inlined. */
    private static final int MAX_IMAGE_BYTES = 800_000;
    private static final int MAX_IMAGE_TOTAL = 4_000_000;

    private static final Pattern SPECIAL = Pattern.compile(
            "<@!?(\\d+)>|<@&(\\d+)>|<#(\\d+)>|<a:([\\w]+):(\\d+)>|<:([\\w]+):(\\d+)>");

    private static final char SLOT_START = '\uE000';
    private static final char SLOT_END = '\uE001';

    private TicketTranscript() {}

    public static CompletableFuture<Boolean> render(TextChannel chan, Member closer, String closeReason, File logFile) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        String closedBy = closer != null ? closer.getEffectiveName() : "unknown";
        chan.getIterableHistory().takeAsync(HISTORY_LIMIT).thenAccept(list -> {
            try {
                List<Message> messages = new ArrayList<>(list);
                Collections.reverse(messages);
                Map<String, String> images = inlineImages(messages);
                String html = buildDocument(chan, messages, images, closedBy, closeReason == null ? "" : closeReason);
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8)) {
                    writer.write(html);
                }
                done.complete(true);
            } catch (Exception ex) {
                Logger.log(ex);
                done.complete(false);
            }
        }).exceptionally(ex -> {
            Logger.log(ex);
            done.complete(false);
            return null;
        });
        return done;
    }

    /** Downloads small images so the file still shows them after the ticket channel is deleted. */
    private static Map<String, String> inlineImages(List<Message> messages) {
        Map<String, String> dataUris = new ConcurrentHashMap<>();
        List<CompletableFuture<?>> jobs = new ArrayList<>();
        int[] used = new int[1];
        for (Message msg : messages) {
            for (Message.Attachment attachment : msg.getAttachments()) {
                if (!attachment.isImage() || attachment.getSize() <= 0 || attachment.getSize() > MAX_IMAGE_BYTES) continue;
                String mime = imageMime(attachment);
                if (mime == null) continue;
                synchronized (used) {
                    if (used[0] + attachment.getSize() > MAX_IMAGE_TOTAL) continue;
                    used[0] += attachment.getSize();
                }
                String id = attachment.getId();
                jobs.add(attachment.getProxy().download().orTimeout(10, TimeUnit.SECONDS).thenAccept(stream -> {
                    try (InputStream in = stream) {
                        byte[] bytes = in.readNBytes(MAX_IMAGE_BYTES + 1);
                        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) return;
                        dataUris.put(id, "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes));
                    } catch (Exception ex) {
                        Logger.log(ex);
                    }
                }).exceptionally(ex -> null));
            }
        }
        if (jobs.isEmpty()) return dataUris;
        try {
            CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Logger.log(ex);
        } catch (Exception ex) {
            Logger.log(ex);
        }
        return dataUris;
    }

    private static String imageMime(Message.Attachment attachment) {
        String name = attachment.getFileName() == null ? "" : attachment.getFileName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".svg")) return null;
        String type = attachment.getContentType();
        if (type == null) return "image/png";
        int semi = type.indexOf(';');
        if (semi >= 0) type = type.substring(0, semi);
        type = type.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "image/png", "image/jpeg", "image/gif", "image/webp" -> type;
            default -> null;
        };
    }

    private static String buildDocument(TextChannel chan, List<Message> messages, Map<String, String> images,
                                        String closedBy, String closeReason) {
        Map<Long, Message> byId = new LinkedHashMap<>();
        for (Message msg : messages) byId.put(msg.getIdLong(), msg);

        Map<Long, Person> people = new LinkedHashMap<>();
        StringBuilder body = new StringBuilder();
        if (messages.size() >= HISTORY_LIMIT) {
            body.append("<div class=\"system\"><span class=\"sys-text\">Only the latest 5,000 messages are included.</span></div>\n");
        }
        String previousDay = null;
        Message previous = null;
        for (Message msg : messages) {
            remember(msg, people);
            String day = msg.getTimeCreated().atZoneSameInstant(ZONE).format(DAY);
            if (!day.equals(previousDay)) {
                body.append("<div class=\"day-divider\"><span>").append(escape(day)).append("</span></div>\n");
                previousDay = day;
                previous = null;
            }
            boolean grouped = groupsWith(previous, msg);
            if (isChat(msg.getType())) {
                body.append(renderMessage(msg, byId, images, grouped));
            } else {
                body.append(renderSystem(msg));
            }
            previous = msg;
        }
        String reason = closeReason.isBlank() ? "No reason given" : closeReason;
        body.append("<div class=\"system\"><span class=\"sys-text\">Ticket closed by <span class=\"sys-name\">")
                .append(escape(closedBy)).append("</span> — ").append(escape(reason)).append("</span></div>\n");

        String guildName = chan.getGuild().getName();
        String channelName = chan.getName();
        String topic = "Closed by " + closedBy + " — " + reason.replace('\n', ' ');
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="dark">
                <title>#%s</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Sans:wght@400;500;600;700&display=swap">
                <style>%s</style>
                </head>
                <body>
                <div class="app">
                  <nav class="servers" aria-hidden="true">%s</nav>
                  <aside class="channels">
                    <div class="guild-header"><span>%s</span></div>
                    <div class="channel-scroll">
                      <div class="category">Ticket</div>
                      <div class="channel active"><span class="hash">#</span><span class="cname">%s</span></div>
                    </div>
                  </aside>
                  <main class="chat">
                    <header class="chat-header">
                      <span class="hash big">#</span>
                      <span class="chat-name">%s</span>
                      <span class="topic" title="%s">%s</span>
                    </header>
                    <div class="messages">%s</div>
                    <div class="composer" aria-hidden="true"><div class="composer-box"><span class="plus">+</span><span>Message #%s</span></div></div>
                  </main>
                  <aside class="members">%s</aside>
                </div>
                <script>
                document.addEventListener('click', function (e) {
                  var s = e.target.closest('.spoiler');
                  if (!s) return;
                  if (!s.classList.contains('revealed')) { e.preventDefault(); s.classList.add('revealed'); }
                });
                </script>
                </body>
                </html>
                """.formatted(
                escape(channelName),
                CSS,
                serverIcon(chan),
                escape(guildName),
                escape(channelName),
                escape(channelName),
                escape(topic),
                escape(topic),
                body,
                escape(channelName),
                memberList(people));
    }

    private static String serverIcon(TextChannel chan) {
        String icon = chan.getGuild().getIconUrl();
        String safe = safeUrl(icon);
        if (safe != null) {
            return "<div class=\"server\"><span class=\"pill\"></span><div class=\"server-icon\"><img alt=\"\" src=\"" + safe + "\"></div></div>";
        }
        return "<div class=\"server\"><span class=\"pill\"></span><div class=\"server-icon\">" + escape(acronym(chan.getGuild().getName())) + "</div></div>";
    }

    private static String acronym(String name) {
        if (name == null || name.isBlank()) return "?";
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (sb.length() == 3) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private static String memberList(Map<Long, Person> people) {
        StringBuilder online = new StringBuilder();
        StringBuilder bots = new StringBuilder();
        int humans = 0;
        int botCount = 0;
        for (Person person : people.values()) {
            String row = "<div class=\"member\"><img class=\"member-avatar\" alt=\"\" src=\"" + escape(person.avatar)
                    + "\"><span class=\"member-name\"" + (person.color == null ? "" : " style=\"color:" + person.color + "\"")
                    + ">" + escape(person.name) + "</span>"
                    + (person.badge == null ? "" : "<span class=\"bot-tag\">" + person.badge + "</span>") + "</div>\n";
            if (person.badge != null) {
                bots.append(row);
                botCount++;
            } else {
                online.append(row);
                humans++;
            }
        }
        StringBuilder html = new StringBuilder();
        if (humans > 0) {
            html.append("<div class=\"member-group\">Members — ").append(humans).append("</div>\n").append(online);
        }
        if (botCount > 0) {
            html.append("<div class=\"member-group\">Bots — ").append(botCount).append("</div>\n").append(bots);
        }
        return html.toString();
    }

    private static void remember(Message msg, Map<Long, Person> people) {
        User author = msg.getAuthor();
        if (author == null || people.containsKey(author.getIdLong())) return;
        Member member = msg.getMember();
        String color = member != null && member.getColor() != null ? hex(member.getColor()) : null;
        String badge = msg.isWebhookMessage() ? "BOT" : author.isBot() ? "APP" : null;
        people.put(author.getIdLong(), new Person(displayName(msg), author.getEffectiveAvatarUrl(), color, badge));
    }

    private static boolean isChat(MessageType type) {
        return type == MessageType.DEFAULT || type == MessageType.INLINE_REPLY
                || type == MessageType.SLASH_COMMAND || type == MessageType.CONTEXT_COMMAND
                || type == MessageType.THREAD_STARTER_MESSAGE;
    }

    private static boolean groupsWith(Message previous, Message current) {
        if (previous == null || !isChat(previous.getType()) || !isChat(current.getType())) return false;
        if (current.getType() == MessageType.INLINE_REPLY || current.getMessageReference() != null) return false;
        if (previous.getAuthor().getIdLong() != current.getAuthor().getIdLong()) return false;
        long gap = current.getTimeCreated().toInstant().toEpochMilli() - previous.getTimeCreated().toInstant().toEpochMilli();
        return gap >= 0 && gap < GROUP_WINDOW_MS;
    }

    private static String renderMessage(Message msg, Map<Long, Message> byId, Map<String, String> images, boolean grouped) {
        User author = msg.getAuthor();
        String name = displayName(msg);
        Member member = msg.getMember();
        String color = member != null && member.getColor() != null ? hex(member.getColor()) : null;
        String when = msg.getTimeCreated().atZoneSameInstant(ZONE).format(CLOCK);
        String full = msg.getTimeCreated().atZoneSameInstant(ZONE).format(FULL);
        String avatar = author.getEffectiveAvatarUrl();

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"message").append(grouped ? "" : " cozy").append("\" id=\"m-").append(msg.getId()).append("\">\n");
        if (grouped) {
            html.append("<time class=\"hover-time\" datetime=\"").append(escape(full)).append("\">").append(escape(when)).append("</time>\n");
        } else {
            html.append("<img class=\"avatar\" alt=\"\" src=\"").append(escape(avatar)).append("\">\n");
        }
        html.append("<div class=\"message-body\">\n");
        if (!grouped) {
            if (msg.getMessageReference() != null && msg.getMessageReference().getMessageIdLong() != 0) {
                html.append(renderReply(msg, byId));
            }
            html.append("<div class=\"header-line\"><span class=\"username\"")
                    .append(color == null ? "" : " style=\"color:" + color + "\"")
                    .append(">").append(escape(name)).append("</span>");
            if (msg.isWebhookMessage()) html.append("<span class=\"bot-tag\">BOT</span>");
            else if (author.isBot()) html.append("<span class=\"bot-tag\">APP</span>");
            html.append("<time class=\"timestamp\" datetime=\"").append(escape(full)).append("\" title=\"")
                    .append(escape(full)).append("\">").append(escape(full)).append("</time></div>\n");
        }
        String content = formatContent(msg.getContentRaw(), msg);
        boolean jumbo = isJumbo(msg.getContentRaw());
        if (!content.isEmpty() || msg.getTimeEdited() != null) {
            html.append("<div class=\"content").append(jumbo ? " jumbo" : "").append("\">").append(content);
            if (msg.getTimeEdited() != null) {
                String edited = msg.getTimeEdited().atZoneSameInstant(ZONE).format(FULL);
                html.append(" <span class=\"edited\" title=\"Edited ").append(escape(edited)).append("\">(edited)</span>");
            }
            html.append("</div>\n");
        }
        for (MessageEmbed embed : msg.getEmbeds()) {
            if (!embed.isEmpty()) html.append(renderEmbed(embed));
        }
        for (Message.Attachment attachment : msg.getAttachments()) {
            html.append(renderAttachment(attachment, images));
        }
        for (Sticker sticker : msg.getStickers()) {
            html.append(renderSticker(sticker));
        }
        if (!msg.getReactions().isEmpty()) {
            html.append("<div class=\"reactions\">");
            for (MessageReaction reaction : msg.getReactions()) html.append(renderReaction(reaction));
            html.append("</div>\n");
        }
        for (LayoutComponent row : msg.getComponents()) {
            if (row instanceof ActionRow actionRow) html.append(renderActionRow(actionRow));
        }
        html.append("</div></div>\n");
        return html.toString();
    }

    private static String renderReply(Message msg, Map<Long, Message> byId) {
        long refId = msg.getMessageReference().getMessageIdLong();
        Message original = byId.get(refId);
        if (original == null) original = msg.getReferencedMessage();
        if (original == null && msg.getMessageReference().getMessage() != null) {
            original = msg.getMessageReference().getMessage();
        }
        String snippet;
        String name = "Unknown";
        String avatar = "";
        String href = "";
        if (original == null || original.getAuthor() == null) {
            snippet = "Original message was deleted or is not in this transcript";
        } else {
            name = displayName(original);
            avatar = original.getAuthor().getEffectiveAvatarUrl();
            href = "#m-" + original.getId();
            String text = original.getContentDisplay().replace('\n', ' ').trim();
            if (text.isEmpty() && !original.getAttachments().isEmpty()) text = "Attachment";
            else if (text.isEmpty() && !original.getEmbeds().isEmpty()) text = "Embed";
            else if (text.isEmpty() && !original.getStickers().isEmpty()) text = "Sticker";
            if (text.length() > 90) text = text.substring(0, 87) + "...";
            snippet = text.isEmpty() ? "Click to see message" : text;
        }
        String inner = (avatar.isEmpty() ? "" : "<img class=\"reply-avatar\" alt=\"\" src=\"" + escape(avatar) + "\">")
                + "<span class=\"reply-name\">" + escape(name) + "</span><span class=\"reply-text\">" + escape(snippet) + "</span>";
        if (href.isEmpty()) return "<div class=\"reply\">" + inner + "</div>\n";
        return "<div class=\"reply\"><a href=\"" + href + "\">" + inner + "</a></div>\n";
    }

    private static String renderSystem(Message msg) {
        String name = displayName(msg);
        String text = switch (msg.getType()) {
            case CHANNEL_PINNED_ADD -> name + " pinned a message to this channel.";
            case RECIPIENT_ADD -> name + " added a recipient.";
            case RECIPIENT_REMOVE -> name + " removed a recipient.";
            case CHANNEL_NAME_CHANGE -> name + " changed the channel name.";
            case THREAD_CREATED -> name + " started a thread.";
            case AUTO_MODERATION_ACTION -> name + " triggered AutoMod.";
            default -> {
                String shown = msg.getContentDisplay();
                yield shown == null || shown.isBlank()
                        ? name + " — " + msg.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        : shown;
            }
        };
        return "<div class=\"system\" id=\"m-" + msg.getId() + "\"><span class=\"sys-mark\">•</span><span class=\"sys-text\">"
                + escape(text) + "</span></div>\n";
    }

    private static String renderEmbed(MessageEmbed embed) {
        Color color = embed.getColor();
        String accent = color == null ? "#4e5058" : hex(color);
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"embed\" style=\"border-color:").append(accent).append("\"><div class=\"embed-grid\"><div class=\"embed-main\">");
        MessageEmbed.AuthorInfo author = embed.getAuthor();
        if (author != null && author.getName() != null) {
            html.append("<div class=\"embed-author\">");
            String icon = firstSafe(author.getProxyIconUrl(), author.getIconUrl());
            if (icon != null) html.append("<img alt=\"\" src=\"").append(icon).append("\">");
            String authorUrl = safeUrl(author.getUrl());
            if (authorUrl != null) html.append("<a href=\"").append(authorUrl).append("\" target=\"_blank\" rel=\"noopener noreferrer\">");
            html.append(escape(author.getName()));
            if (authorUrl != null) html.append("</a>");
            html.append("</div>");
        }
        if (embed.getTitle() != null && !embed.getTitle().isEmpty()) {
            String titleUrl = safeUrl(embed.getUrl());
            html.append("<div class=\"embed-title\">");
            if (titleUrl != null) html.append("<a href=\"").append(titleUrl).append("\" target=\"_blank\" rel=\"noopener noreferrer\">");
            html.append(escape(embed.getTitle()));
            if (titleUrl != null) html.append("</a>");
            html.append("</div>");
        }
        if (embed.getDescription() != null && !embed.getDescription().isEmpty()) {
            html.append("<div class=\"embed-description\">").append(formatContent(embed.getDescription(), null)).append("</div>");
        }
        if (!embed.getFields().isEmpty()) {
            html.append("<div class=\"fields\">");
            for (MessageEmbed.Field field : embed.getFields()) {
                html.append("<div class=\"field").append(field.isInline() ? " inline" : "").append("\">")
                        .append("<div class=\"field-name\">").append(escape(field.getName())).append("</div>")
                        .append("<div class=\"field-value\">").append(formatContent(field.getValue(), null)).append("</div></div>");
            }
            html.append("</div>");
        }
        String footerText = null;
        String footerIcon = null;
        if (embed.getFooter() != null) {
            footerText = embed.getFooter().getText();
            footerIcon = firstSafe(embed.getFooter().getProxyIconUrl(), embed.getFooter().getIconUrl());
        } else if (embed.getSiteProvider() != null && embed.getSiteProvider().getName() != null) {
            footerText = embed.getSiteProvider().getName();
        }
        if (embed.getTimestamp() != null) {
            String stamp = embed.getTimestamp().atZoneSameInstant(ZONE).format(FULL);
            footerText = footerText == null || footerText.isBlank() ? stamp : footerText + " • " + stamp;
        }
        if (footerText != null && !footerText.isBlank()) {
            html.append("<div class=\"embed-footer\">");
            if (footerIcon != null) html.append("<img alt=\"\" src=\"").append(footerIcon).append("\">");
            html.append("<span>").append(escape(footerText)).append("</span></div>");
        }
        html.append("</div>");
        if (embed.getThumbnail() != null) {
            String thumb = firstSafe(embed.getThumbnail().getProxyUrl(), embed.getThumbnail().getUrl());
            if (thumb != null) html.append("<img class=\"embed-thumb\" alt=\"\" src=\"").append(thumb).append("\">");
        }
        html.append("</div>");
        if (embed.getImage() != null) {
            String image = firstSafe(embed.getImage().getProxyUrl(), embed.getImage().getUrl());
            if (image != null) {
                html.append("<a class=\"embed-image\" href=\"").append(image).append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                        .append("<img alt=\"\" src=\"").append(image).append("\"></a>");
            }
        }
        html.append("</div>\n");
        return html.toString();
    }

    private static String renderAttachment(Message.Attachment attachment, Map<String, String> images) {
        String name = attachment.getFileName() == null ? "file" : attachment.getFileName();
        String href = safeUrl(attachment.getUrl());
        String src = images.get(attachment.getId());
        if (src == null) src = firstSafe(attachment.getProxyUrl(), attachment.getUrl());
        boolean spoiler = attachment.isSpoiler();
        if (attachment.isImage() && src != null && imageMime(attachment) != null) {
            String open = href == null ? src : href;
            return "<div class=\"attach" + (spoiler ? " spoiler" : "") + "\"><a href=\"" + open
                    + "\" target=\"_blank\" rel=\"noopener noreferrer\"><img alt=\"" + escape(name) + "\" src=\"" + src + "\"></a></div>\n";
        }
        if (attachment.isVideo() && href != null) {
            return "<video class=\"attach-video\" controls src=\"" + href + "\"></video>\n" + fileCard(name, attachment.getSize(), href);
        }
        String type = attachment.getContentType() == null ? "" : attachment.getContentType();
        if (type.startsWith("audio/") && href != null) {
            return "<audio class=\"attach-audio\" controls src=\"" + href + "\"></audio>\n" + fileCard(name, attachment.getSize(), href);
        }
        return fileCard(name, attachment.getSize(), href);
    }

    private static String fileCard(String name, int size, String href) {
        String label = escape(name);
        String linked = href == null ? label : "<a href=\"" + href + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + label + "</a>";
        return "<div class=\"file\"><div class=\"file-icon\">↓</div><div><div class=\"file-name\">" + linked
                + "</div><div class=\"file-size\">" + fileSize(size) + "</div></div></div>\n";
    }

    private static String fileSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private static String renderSticker(Sticker sticker) {
        if (sticker.getFormatType() == Sticker.StickerFormat.LOTTIE) {
            return "<div class=\"sticker-fallback\">Sticker: " + escape(sticker.getName()) + "</div>\n";
        }
        String src = safeUrl(sticker.getIconUrl());
        if (src == null) return "<div class=\"sticker-fallback\">Sticker: " + escape(sticker.getName()) + "</div>\n";
        return "<img class=\"sticker\" alt=\"" + escape(sticker.getName()) + "\" title=\"" + escape(sticker.getName()) + "\" src=\"" + src + "\">\n";
    }

    private static String renderReaction(MessageReaction reaction) {
        EmojiUnion emoji = reaction.getEmoji();
        String glyph;
        if (emoji.getType() == Emoji.Type.CUSTOM) {
            CustomEmoji custom = emoji.asCustom();
            String src = safeUrl(custom.getImageUrl());
            glyph = src == null ? escape(custom.getName())
                    : "<img class=\"emoji\" alt=\":" + escape(custom.getName()) + ":\" src=\"" + src + "\">";
        } else {
            glyph = escape(emoji.getName());
        }
        return "<span class=\"reaction" + (reaction.isSelf() ? " mine" : "") + "\">" + glyph
                + "<span class=\"count\">" + reaction.getCount() + "</span></span>";
    }

    private static String renderActionRow(ActionRow row) {
        StringBuilder html = new StringBuilder("<div class=\"action-row\">");
        for (ItemComponent component : row.getComponents()) {
            if (component instanceof Button button) {
                String label = buttonEmoji(button.getEmoji()) + escape(button.getLabel() == null ? "" : button.getLabel());
                String style = button.getStyle() == null ? "secondary" : button.getStyle().name().toLowerCase(Locale.ROOT);
                String disabled = button.isDisabled() ? " disabled" : "";
                if (button.getStyle() == ButtonStyle.LINK) {
                    String href = safeUrl(button.getUrl());
                    if (href != null) {
                        html.append("<a class=\"btn link\" href=\"").append(href).append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                                .append(label).append("<span class=\"external\">↗</span></a>");
                        continue;
                    }
                }
                html.append("<span class=\"btn ").append(style).append(disabled).append("\">").append(label).append("</span>");
            } else if (component instanceof SelectMenu menu) {
                String placeholder = menu.getPlaceholder() == null || menu.getPlaceholder().isBlank()
                        ? "Make a selection" : menu.getPlaceholder();
                html.append("<div class=\"select").append(menu.isDisabled() ? " disabled" : "")
                        .append("\"><span>").append(escape(placeholder)).append("</span><span class=\"chevron\">▾</span></div>");
            }
        }
        html.append("</div>\n");
        return html.toString();
    }

    private static String buttonEmoji(EmojiUnion emoji) {
        if (emoji == null) return "";
        if (emoji.getType() == Emoji.Type.CUSTOM) {
            String src = safeUrl(emoji.asCustom().getImageUrl());
            if (src == null) return "";
            return "<img class=\"emoji\" alt=\"\" src=\"" + src + "\"> ";
        }
        return escape(emoji.getName()) + " ";
    }

    private static String displayName(Message msg) {
        if (msg.getAuthor() == null) return "Unknown";
        Member member = msg.getMember();
        if (member != null) return member.getEffectiveName();
        if (msg.getAuthor().getGlobalName() != null && !msg.getAuthor().getGlobalName().isBlank()) {
            return msg.getAuthor().getGlobalName();
        }
        return msg.getAuthor().getName();
    }

    private static boolean isJumbo(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return raw.trim().matches("(?s)(<a?:[\\w]+:\\d+>\\s*)+");
    }

    // ------------------------------------------------------------------------------------------
    // Discord markdown. User text is escaped; only tags this class builds are inserted.
    // ------------------------------------------------------------------------------------------

    private static String formatContent(String raw, Message msg) {
        if (raw == null || raw.isEmpty()) return "";
        raw = raw.replace("\r\n", "\n").replace('\r', '\n');
        List<String> slots = new ArrayList<>();
        String text = extractCode(raw, slots);
        text = replaceMentions(text, msg, slots);
        text = applyQuotes(text);
        return restore(text, slots);
    }

    private static String extractCode(String raw, List<String> slots) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            if (raw.startsWith("```", i)) {
                int close = raw.indexOf("```", i + 3);
                String body = close < 0 ? raw.substring(i + 3) : raw.substring(i + 3, close);
                String lang = "";
                int nl = body.indexOf('\n');
                if (nl >= 0) {
                    String first = body.substring(0, nl).trim();
                    if (first.matches("[A-Za-z0-9_+#.\\-]{1,32}")) {
                        lang = first;
                        body = body.substring(nl + 1);
                    }
                }
                if (body.endsWith("\n")) body = body.substring(0, body.length() - 1);
                String html = "<div class=\"codeblock\">"
                        + (lang.isEmpty() ? "" : "<div class=\"code-lang\">" + escape(lang) + "</div>")
                        + "<pre><code>" + escape(body) + "</code></pre></div>";
                out.append(slot(slots, html));
                if (close < 0) break;
                i = close + 3;
                continue;
            }
            if (raw.charAt(i) == '`') {
                int close = raw.indexOf('`', i + 1);
                if (close > i + 1 && raw.indexOf('\n', i + 1) != close && !raw.substring(i + 1, close).contains("\n")) {
                    out.append(slot(slots, "<code>" + escape(raw.substring(i + 1, close)) + "</code>"));
                    i = close + 1;
                    continue;
                }
            }
            out.append(raw.charAt(i));
            i++;
        }
        return out.toString();
    }

    private static String replaceMentions(String text, Message msg, List<String> slots) {
        Matcher matcher = SPECIAL.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(text, last, matcher.start());
            sb.append(mentionSlot(matcher, msg, slots));
            last = matcher.end();
        }
        sb.append(text, last, text.length());
        String out = sb.toString();
        if (msg != null && msg.getMentions().mentionsEveryone()) {
            out = out.replace("@everyone", slot(slots, "<span class=\"mention\">@everyone</span>"));
            out = out.replace("@here", slot(slots, "<span class=\"mention\">@here</span>"));
        }
        return out;
    }

    private static String mentionSlot(Matcher matcher, Message msg, List<String> slots) {
        if (matcher.group(1) != null) {
            long id = Long.parseLong(matcher.group(1));
            return slot(slots, "<span class=\"mention\">@" + escape(userLabel(msg, id)) + "</span>");
        }
        if (matcher.group(2) != null) {
            long id = Long.parseLong(matcher.group(2));
            if (msg != null) {
                for (Role role : msg.getMentions().getRoles()) {
                    if (role.getIdLong() != id) continue;
                    String style = "";
                    if (role.getColor() != null) {
                        style = " style=\"color:" + hex(role.getColor()) + ";background:" + rgba(role.getColor(), 0.3) + "\"";
                    }
                    return slot(slots, "<span class=\"mention\"" + style + ">@" + escape(role.getName()) + "</span>");
                }
            }
            return slot(slots, "<span class=\"mention\">@deleted-role</span>");
        }
        if (matcher.group(3) != null) {
            long id = Long.parseLong(matcher.group(3));
            String name = "deleted-channel";
            if (msg != null) {
                for (GuildChannel channel : msg.getMentions().getChannels()) {
                    if (channel.getIdLong() == id) {
                        name = channel.getName();
                        break;
                    }
                }
            }
            return slot(slots, "<span class=\"mention\">#" + escape(name) + "</span>");
        }
        boolean animated = matcher.group(4) != null;
        String emojiName = animated ? matcher.group(4) : matcher.group(6);
        String emojiId = animated ? matcher.group(5) : matcher.group(7);
        String src = "https://cdn.discordapp.com/emojis/" + emojiId + (animated ? ".gif" : ".png");
        return slot(slots, "<img class=\"emoji\" alt=\":" + escape(emojiName) + ":\" title=\":" + escape(emojiName)
                + ":\" src=\"" + src + "\">");
    }

    private static String userLabel(Message msg, long id) {
        if (msg == null) return "user";
        for (Member member : msg.getMentions().getMembers()) {
            if (member.getIdLong() == id) return member.getEffectiveName();
        }
        for (User user : msg.getMentions().getUsers()) {
            if (user.getIdLong() != id) continue;
            return user.getGlobalName() != null ? user.getGlobalName() : user.getName();
        }
        return "user";
    }

    private static String applyQuotes(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            if (lines[i].startsWith(">>>")) {
                if (sb.length() > 0) sb.append("<br>");
                String first = lines[i].length() > 3 && lines[i].charAt(3) == ' ' ? lines[i].substring(4) : lines[i].substring(3);
                StringBuilder inner = new StringBuilder(formatLine(first));
                for (int j = i + 1; j < lines.length; j++) inner.append("<br>").append(formatLine(lines[j]));
                sb.append("<blockquote>").append(inner).append("</blockquote>");
                break;
            }
            if (lines[i].startsWith("> ") || lines[i].equals(">")) {
                if (sb.length() > 0) sb.append("<br>");
                StringBuilder inner = new StringBuilder();
                boolean any = false;
                while (i < lines.length && (lines[i].startsWith("> ") || lines[i].equals(">"))) {
                    if (any) inner.append("<br>");
                    any = true;
                    inner.append(formatLine(lines[i].equals(">") ? "" : lines[i].substring(2)));
                    i++;
                }
                sb.append("<blockquote>").append(inner).append("</blockquote>");
                continue;
            }
            if (sb.length() > 0) sb.append("<br>");
            sb.append(formatLine(lines[i]));
            i++;
        }
        return sb.toString();
    }

    private static String formatLine(String line) {
        return formatSegment(escapeKeepingSlots(line), 0);
    }

    private static String formatSegment(String text, int depth) {
        if (depth > 12) return text;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == SLOT_START) {
                int end = text.indexOf(SLOT_END, i);
                if (end < 0) {
                    out.append(text.substring(i));
                    break;
                }
                out.append(text, i, end + 1);
                i = end + 1;
                continue;
            }
            int consumed = tryWrap(out, text, i, depth, "***", "<strong><em>", "</em></strong>");
            if (consumed < 0) consumed = tryWrap(out, text, i, depth, "**", "<strong>", "</strong>");
            if (consumed < 0) consumed = tryWrap(out, text, i, depth, "__", "<u>", "</u>");
            if (consumed < 0) consumed = tryWrap(out, text, i, depth, "~~", "<s>", "</s>");
            if (consumed < 0) consumed = tryWrap(out, text, i, depth, "||", "<span class=\"spoiler\"><span class=\"spoiler-text\">", "</span></span>");
            if (consumed >= 0) {
                i += consumed;
                continue;
            }
            if ((text.charAt(i) == '*' || text.charAt(i) == '_') && (i + 1 >= text.length() || text.charAt(i + 1) != text.charAt(i))) {
                char mark = text.charAt(i);
                int close = findSingle(text, i + 1, mark);
                boolean boundary = mark != '_' || (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))
                        && (close < 0 || close + 1 >= text.length() || !Character.isLetterOrDigit(text.charAt(close + 1)));
                if (close > i + 1 && boundary) {
                    out.append("<em>").append(formatSegment(text.substring(i + 1, close), depth + 1)).append("</em>");
                    i = close + 1;
                    continue;
                }
            }
            if (text.charAt(i) == '[') {
                int labelEnd = text.indexOf(']', i + 1);
                if (labelEnd > i + 1 && labelEnd + 1 < text.length() && text.charAt(labelEnd + 1) == '(') {
                    int urlEnd = text.indexOf(')', labelEnd + 2);
                    if (urlEnd > labelEnd + 2) {
                        String href = text.substring(labelEnd + 2, urlEnd);
                        if (href.startsWith("https://") || href.startsWith("http://")) {
                            out.append("<a href=\"").append(href).append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                                    .append(formatSegment(text.substring(i + 1, labelEnd), depth + 1)).append("</a>");
                            i = urlEnd + 1;
                            continue;
                        }
                    }
                }
            }
            if (text.startsWith("https://", i) || text.startsWith("http://", i)) {
                int end = i;
                while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != SLOT_START) end++;
                while (end > i && ".,);:".indexOf(text.charAt(end - 1)) >= 0) end--;
                String href = text.substring(i, end);
                out.append("<a href=\"").append(href).append("\" target=\"_blank\" rel=\"noopener noreferrer\">").append(href).append("</a>");
                i = end;
                continue;
            }
            out.append(text.charAt(i));
            i++;
        }
        return out.toString();
    }

    /** @return characters consumed, or -1 when this marker is not a real pair */
    private static int tryWrap(StringBuilder out, String text, int i, int depth, String marker, String openTag, String closeTag) {
        if (!text.startsWith(marker, i)) return -1;
        int close = text.indexOf(marker, i + marker.length());
        if (close < 0 || close == i + marker.length()) return -1;
        out.append(openTag).append(formatSegment(text.substring(i + marker.length(), close), depth + 1)).append(closeTag);
        return close + marker.length() - i;
    }

    private static int findSingle(String text, int from, char mark) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == SLOT_START) {
                int end = text.indexOf(SLOT_END, i);
                if (end < 0) return -1;
                i = end;
                continue;
            }
            if (text.charAt(i) != mark) continue;
            boolean doubled = (i > 0 && text.charAt(i - 1) == mark) || (i + 1 < text.length() && text.charAt(i + 1) == mark);
            if (!doubled) return i;
        }
        return -1;
    }

    private static String escapeKeepingSlots(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == SLOT_START) {
                int end = text.indexOf(SLOT_END, i);
                if (end < 0) {
                    sb.append(escape(text.substring(i)));
                    break;
                }
                sb.append(text, i, end + 1);
                i = end;
                continue;
            }
            appendEscaped(sb, text.charAt(i));
        }
        return sb.toString();
    }

    private static String slot(List<String> slots, String html) {
        int id = slots.size();
        slots.add(html);
        return SLOT_START + Integer.toString(id) + SLOT_END;
    }

    private static String restore(String html, List<String> slots) {
        StringBuilder sb = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            int start = html.indexOf(SLOT_START, i);
            if (start < 0) {
                sb.append(html, i, html.length());
                break;
            }
            sb.append(html, i, start);
            int end = html.indexOf(SLOT_END, start);
            if (end < 0) {
                sb.append(html.substring(start));
                break;
            }
            try {
                sb.append(slots.get(Integer.parseInt(html.substring(start + 1, end))));
            } catch (RuntimeException ex) {
                sb.append(html, start, end + 1);
            }
            i = end + 1;
        }
        return sb.toString();
    }

    private static String firstSafe(String preferred, String fallback) {
        String safe = safeUrl(preferred);
        return safe != null ? safe : safeUrl(fallback);
    }

    private static String safeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.regionMatches(true, 0, "https://", 0, 8) || trimmed.regionMatches(true, 0, "http://", 0, 7)) {
            return escape(trimmed);
        }
        return null;
    }

    private static String escape(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) appendEscaped(sb, text.charAt(i));
        return sb.toString();
    }

    private static void appendEscaped(StringBuilder sb, char c) {
        switch (c) {
            case '&' -> sb.append("&amp;");
            case '<' -> sb.append("&lt;");
            case '>' -> sb.append("&gt;");
            case '"' -> sb.append("&quot;");
            case '\'' -> sb.append("&#39;");
            default -> sb.append(c);
        }
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String rgba(Color color, double alpha) {
        return "rgba(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + alpha + ")";
    }

    private static final class Person {
        final String name;
        final String avatar;
        final String color;
        /** "APP", "BOT", or null for a normal member. */
        final String badge;

        Person(String name, String avatar, String color, String badge) {
            this.name = name;
            this.avatar = avatar;
            this.color = color;
            this.badge = badge;
        }
    }

    private static final String CSS = """
            :root { color-scheme: dark; }
            * { box-sizing: border-box; }
            html, body { margin: 0; height: 100%; background: #1e1f22; }
            body { font-family: "Noto Sans", "Helvetica Neue", Helvetica, Arial, sans-serif; font-size: 16px; color: #dbdee1; }
            a { color: #00a8fc; text-decoration: none; }
            a:hover { text-decoration: underline; }
            .app { display: flex; height: 100vh; overflow: hidden; }
            .servers { width: 72px; background: #1e1f22; flex: none; padding-top: 12px; }
            .server { position: relative; }
            .pill { position: absolute; left: 0; top: 4px; width: 4px; height: 40px; border-radius: 0 4px 4px 0; background: #fff; }
            .server-icon { width: 48px; height: 48px; margin: 0 auto; border-radius: 16px; background: #5865f2; color: #fff;
              display: flex; align-items: center; justify-content: center; font-weight: 600; overflow: hidden; }
            .server-icon img { width: 100%; height: 100%; object-fit: cover; }
            .channels { width: 240px; background: #2b2d31; display: flex; flex-direction: column; flex: none; }
            .guild-header { height: 48px; display: flex; align-items: center; padding: 0 16px; font-weight: 600; color: #f2f3f5;
              box-shadow: 0 1px 0 #1f2023; flex: none; }
            .guild-header span, .cname, .chat-name, .topic { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .channel-scroll, .members { overflow-y: auto; }
            .category { margin: 16px 8px 4px 18px; font-size: 12px; font-weight: 700; letter-spacing: .02em; text-transform: uppercase; color: #949ba4; }
            .channel { display: flex; align-items: center; margin: 1px 8px; padding: 6px 8px; border-radius: 4px; color: #949ba4; }
            .channel.active { background: #404249; color: #f2f3f5; }
            .hash { width: 20px; color: #80848e; font-weight: 500; flex: none; }
            .channel.active .hash { color: #f2f3f5; }
            .chat { flex: 1; min-width: 0; background: #313338; display: flex; flex-direction: column; }
            .chat-header { height: 48px; display: flex; align-items: center; padding: 0 16px; box-shadow: 0 1px 0 #1f2023; gap: 8px; flex: none; }
            .hash.big { color: #80848e; font-size: 22px; font-weight: 600; width: auto; }
            .chat-name { font-weight: 600; color: #f2f3f5; }
            .topic { color: #949ba4; font-size: 14px; border-left: 1px solid #3f4147; padding-left: 8px; min-width: 0; }
            .messages { flex: 1; overflow-y: auto; padding: 16px 0 24px; }
            .day-divider { display: flex; align-items: center; margin: 8px 16px 8px 16px; color: #949ba4; font-size: 12px; font-weight: 600; }
            .day-divider::before, .day-divider::after { content: ""; height: 1px; background: #3f4147; flex: 1; }
            .day-divider span { padding: 0 8px; }
            .message { position: relative; padding: 2px 48px 2px 72px; }
            .message.cozy { margin-top: 1.0625rem; min-height: 2.75rem; }
            .message:hover { background: #2e3035; }
            .avatar { position: absolute; left: 16px; top: 2px; width: 40px; height: 40px; border-radius: 50%; }
            .hover-time { position: absolute; left: 0; width: 64px; text-align: right; top: 2px; font-size: 11px; line-height: 1.375rem;
              color: #949ba4; opacity: 0; }
            .message:hover .hover-time { opacity: 1; }
            .header-line { display: flex; align-items: baseline; line-height: 1.375rem; min-width: 0; }
            .username { font-weight: 500; color: #f2f3f5; }
            .timestamp, .edited { color: #949ba4; font-size: 12px; margin-left: 6px; font-weight: 400; }
            .edited { font-size: 10px; }
            .bot-tag { background: #5865f2; color: #fff; font-size: 10px; font-weight: 600; line-height: 15px; border-radius: 3px;
              padding: 0 4px; margin-left: 6px; position: relative; top: -1px; }
            .content { line-height: 1.375rem; white-space: normal; overflow-wrap: anywhere; color: #dbdee1; }
            .content.jumbo .emoji { width: 48px; height: 48px; }
            .emoji { width: 22px; height: 22px; vertical-align: bottom; object-fit: contain; }
            .mention { background: rgba(88, 101, 242, .3); color: #c9cdfb; border-radius: 3px; padding: 0 2px; font-weight: 500; }
            .mention:hover { background: #5865f2; color: #fff; }
            blockquote { margin: 4px 0; padding: 0 8px 0 12px; border-left: 4px solid #4e5058; }
            .content code { font-family: Consolas, "Courier New", monospace; font-size: .875em; background: #2b2d31; padding: 2px 4px; border-radius: 3px; }
            .codeblock { background: #2b2d31; border: 1px solid #1e1f22; border-radius: 4px; margin: 6px 0; max-width: 100%; }
            .code-lang { color: #b5bac1; font-size: 12px; padding: 6px 10px 0; }
            .codeblock pre { margin: 0; padding: 8px 10px 10px; overflow-x: auto; }
            .codeblock code { font-family: Consolas, "Courier New", monospace; font-size: 14px; white-space: pre; background: none; padding: 0; }
            .spoiler { background: #80848e; color: transparent; border-radius: 4px; cursor: pointer; }
            .spoiler .spoiler-text { opacity: 0; }
            .spoiler.revealed { background: rgba(255, 255, 255, .1); color: inherit; }
            .spoiler.revealed .spoiler-text { opacity: 1; }
            .reply { margin-bottom: 2px; font-size: 14px; color: #949ba4; min-width: 0; }
            .reply a { display: flex; align-items: center; gap: 4px; color: inherit; min-width: 0; }
            .reply-avatar { width: 16px; height: 16px; border-radius: 50%; flex: none; }
            .reply-name { font-weight: 600; color: #dbdee1; flex: none; }
            .reply a:hover .reply-name { text-decoration: underline; }
            .reply-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .embed { background: #2b2d31; border-left: 4px solid #4e5058; border-radius: 4px; max-width: 432px; margin-top: 8px; padding: 8px 16px 16px 12px; }
            .embed-grid { display: flex; gap: 16px; }
            .embed-main { min-width: 0; flex: 1; }
            .embed-author { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; margin-top: 8px; }
            .embed-author img, .embed-footer img { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; }
            .embed-title { font-weight: 600; margin-top: 8px; color: #f2f3f5; }
            .embed-description, .field-value { font-size: 14px; line-height: 1.3; margin-top: 8px; }
            .fields { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
            .field { flex: 1 1 100%; min-width: 0; }
            .field.inline { flex: 1 1 140px; }
            .field-name { font-size: 14px; font-weight: 600; }
            .field-value { margin-top: 2px; }
            .embed-thumb { width: 80px; height: 80px; border-radius: 4px; object-fit: cover; margin-top: 8px; flex: none; }
            .embed-image img, .attach img { display: block; max-width: min(400px, 100%); max-height: 300px; border-radius: 8px; margin-top: 8px; }
            .attach.spoiler img { filter: blur(44px); }
            .attach.spoiler.revealed img { filter: none; }
            .attach-video, .attach-audio { display: block; max-width: min(400px, 100%); margin-top: 8px; border-radius: 8px; }
            .file { display: flex; gap: 12px; align-items: center; background: #2b2d31; border: 1px solid #1e1f22; border-radius: 8px;
              padding: 10px 12px; margin-top: 8px; max-width: 400px; }
            .file-icon { width: 30px; height: 40px; border-radius: 4px; background: #5865f2; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; }
            .file-size, .file-name { font-size: 14px; }
            .file-size { color: #949ba4; }
            .sticker { width: 160px; height: 160px; object-fit: contain; margin-top: 4px; }
            .sticker-fallback { margin-top: 6px; color: #b5bac1; font-size: 14px; }
            .reactions { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
            .reaction { display: inline-flex; align-items: center; gap: 6px; background: #2b2d31; border: 1px solid transparent;
              border-radius: 8px; padding: 2px 6px; font-size: 14px; }
            .reaction.mine { background: rgba(88, 101, 242, .15); border-color: #5865f2; }
            .count { color: #dbdee1; font-size: 14px; }
            .action-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 6px; }
            .btn { display: inline-flex; align-items: center; gap: 6px; border-radius: 3px; padding: 2px 16px; min-height: 32px;
              font-size: 14px; font-weight: 500; color: #fff; cursor: default; }
            .btn.primary { background: #5865f2; }
            .btn.secondary, .btn.link { background: #4e5058; color: #fff; }
            .btn.success { background: #248046; }
            .btn.danger { background: #da373c; }
            .btn.unknown { background: #4e5058; }
            .btn.disabled { opacity: .5; }
            .external { font-size: 12px; }
            .select { display: flex; justify-content: space-between; align-items: center; width: 100%; max-width: 400px; background: #1e1f22;
              color: #949ba4; border-radius: 4px; padding: 8px 12px; font-size: 14px; }
            .select.disabled { opacity: .5; }
            .system { position: relative; padding: 8px 48px 8px 72px; color: #949ba4; }
            .sys-mark { position: absolute; left: 16px; width: 40px; text-align: center; color: #80848e; }
            .sys-name { color: #dbdee1; font-weight: 600; }
            .composer { padding: 0 16px 24px; flex: none; }
            .composer-box { background: #383a40; border-radius: 8px; min-height: 44px; display: flex; align-items: center; padding: 0 16px; color: #6d6f78; }
            .plus { width: 24px; height: 24px; border-radius: 50%; background: #b5bac1; color: #383a40; display: inline-flex; align-items: center;
              justify-content: center; font-weight: 700; margin-right: 12px; }
            .members { width: 240px; background: #2b2d31; flex: none; padding-top: 16px; }
            .member-group { margin: 8px 8px 4px 16px; font-size: 12px; font-weight: 700; letter-spacing: .02em; text-transform: uppercase; color: #949ba4; }
            .member { display: flex; align-items: center; gap: 10px; margin: 1px 8px; padding: 6px 8px; border-radius: 4px; min-width: 0; }
            .member:hover { background: #35373c; }
            .member-avatar { width: 32px; height: 32px; border-radius: 50%; flex: none; }
            .member-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 15px; font-weight: 500; color: #dbdee1; }
            .messages::-webkit-scrollbar, .channel-scroll::-webkit-scrollbar, .members::-webkit-scrollbar { width: 8px; }
            .messages::-webkit-scrollbar-thumb, .channel-scroll::-webkit-scrollbar-thumb, .members::-webkit-scrollbar-thumb { background: #1a1b1e; border-radius: 4px; }
            @media (max-width: 1100px) { .members { display: none; } }
            @media (max-width: 800px) { .servers, .channels { display: none; } .message, .system { padding-right: 16px; } }
            """;
}
