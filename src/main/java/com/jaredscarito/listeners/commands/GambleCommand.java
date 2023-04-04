package com.jaredscarito.listeners.commands;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class GambleCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null) return;
        if (mem.getUser().isBot()) return;
        OptionMapping opt = evt.getOption("amount");
        if (opt == null) return;
        if (evt.getGuild() == null) return;
        int amt = opt.getAsInt();
        if (API.getInstance().getPoints(mem) < amt) {
            // They cannot gamble this amount as they do not have this amount to gamble...
            // TODO Error
            return;
        }
        List<String> slotPngs = getSlotPngs();
        String slotsGif = getSlotsGif();
        List<RichCustomEmoji> slotsGifEmoji = evt.getGuild().getEmojisByName(slotsGif, false);
        List<RichCustomEmoji> slotPngEmojis = new ArrayList<>();
        for (String slotPng : slotPngs) {
            List<RichCustomEmoji> emojis = evt.getGuild().getEmojisByName(slotPng, false);
            slotPngEmojis.addAll(emojis);
        }
        if (slotsGifEmoji.size() == 0) return;
        if (slotPngEmojis.size() == 0) return;
        String slotGif = slotsGifEmoji.get(0).getAsMention();
        String coinName = Main.getInstance().getConfig().getString("Bot.Messaging.Points.Name");
        evt.getChannel().asTextChannel().sendMessage(slotGif + "" + slotGif + "" + slotGif).queue((msg) -> {
            int rand1 = getRandomIndex(0, (getSlotPngs().size() - 1));
            int rand2 = getRandomIndex(0, (getSlotPngs().size() - 1));
            int rand3 = getRandomIndex(0, (getSlotPngs().size() - 1));
            String emoji1 = slotPngEmojis.get(rand1).getAsMention();
            String emoji2 = slotPngEmojis.get(rand2).getAsMention();
            String emoji3 = slotPngEmojis.get(rand3).getAsMention();
            msg.editMessage(emoji1 + slotGif + slotGif).queueAfter(2, TimeUnit.SECONDS);
            msg.editMessage(emoji1 + emoji2 + slotGif).queueAfter(4, TimeUnit.SECONDS);
            msg.editMessage(emoji1 + emoji2 + emoji3).queueAfter(6, TimeUnit.SECONDS);
            if (emoji1.equals(emoji2) && emoji2.equals(emoji3)) {
                msg.reply("**Congrats** " + mem.getAsMention() + " -- You won `" + amt + "` " + coinName + "!").queueAfter(7, TimeUnit.SECONDS);
                API.getInstance().addPoints(mem, amt);
            } else {
                msg.reply("**Sorry** " + mem.getAsMention() + " -- You lost `" + amt + "` " + coinName +
                        ".... Better luck next time!").queueAfter(7, TimeUnit.SECONDS);
                API.getInstance().removePoints(mem, amt);
            }
        });
    }

    private static int getRandomIndex(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }

    private static List<String> getSlotPngs() {
        return Main.getInstance().getConfig().getStringList("Bot.Commands.Gamble.Slot_PNGs");
    }
    private static String getSlotsGif() {
        return Main.getInstance().getConfig().getString("Bot.Commands.Gamble.Slots_GIF");
    }
}
