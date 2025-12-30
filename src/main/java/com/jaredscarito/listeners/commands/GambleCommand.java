package com.jaredscarito.listeners.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.jaredscarito.listeners.api.API;
import com.jaredscarito.main.Main;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class GambleCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null || mem.getUser().isBot()) {
            evt.reply("❌ Error: Unable to process command.").setEphemeral(true).queue();
            return;
        }
        
        OptionMapping opt = evt.getOption("amount");
        if (opt == null) {
            evt.reply("❌ Error: Please specify an amount to gamble.").setEphemeral(true).queue();
            return;
        }
        
        if (evt.getGuild() == null) {
            evt.reply("❌ Error: This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        
        int amt = opt.getAsInt();
        if (amt <= 0) {
            evt.reply("❌ Error: Amount must be greater than 0.").setEphemeral(true).queue();
            return;
        }
        
        int userPoints = API.getInstance().getPoints(mem);
        if (userPoints < amt) {
            String coinName = Main.getInstance().getConfig().getString("Bot.Messaging.Points.Name", "Charlie Coins");
            evt.reply("❌ Error: You don't have enough " + coinName + "! You have `" + userPoints + "` but need `" + amt + "`.").setEphemeral(true).queue();
            return;
        }
        
        try {
            List<String> slotPngs = getSlotPngs();
            String slotsGif = getSlotsGif();
            
            if (slotPngs == null || slotPngs.isEmpty()) {
                evt.reply("❌ Error: Slot machine configuration not found. Please contact an administrator.").setEphemeral(true).queue();
                return;
            }
            
            if (slotsGif == null || slotsGif.isEmpty()) {
                evt.reply("❌ Error: Slot machine GIF configuration not found. Please contact an administrator.").setEphemeral(true).queue();
                return;
            }
            
            // Search for the GIF emoji - try exact name, name with underscores, and name without underscores
            List<RichCustomEmoji> slotsGifEmoji = evt.getGuild().getEmojisByName(slotsGif, false);
            if (slotsGifEmoji.isEmpty()) {
                // Try alternative name formats
                String altName1 = slotsGif.replace("_", "");
                String altName2 = slotsGif.replace("_", " ");
                slotsGifEmoji = evt.getGuild().getEmojisByName(altName1, false);
                if (slotsGifEmoji.isEmpty()) {
                    slotsGifEmoji = evt.getGuild().getEmojisByName(altName2, false);
                }
            }
            
            // If still not found, try searching all animated emojis for one that contains the name
            if (slotsGifEmoji.isEmpty()) {
                List<RichCustomEmoji> allEmojis = evt.getGuild().getEmojiCache().asList();
                if (allEmojis != null) {
                    for (RichCustomEmoji emoji : allEmojis) {
                        if (emoji != null && emoji.isAnimated()) {
                            String emojiName = emoji.getName();
                            if (emojiName != null && (emojiName.toLowerCase().contains(slotsGif.toLowerCase()) || 
                                slotsGif.toLowerCase().contains(emojiName.toLowerCase()))) {
                                slotsGifEmoji.add(emoji);
                                break;
                            }
                        }
                    }
                }
            }
            
            List<RichCustomEmoji> slotPngEmojis = new ArrayList<>();
            for (String slotPng : slotPngs) {
                List<RichCustomEmoji> emojis = evt.getGuild().getEmojisByName(slotPng, false);
                if (emojis.isEmpty()) {
                    // Try alternative name formats
                    String altName1 = slotPng.replace("_", "");
                    String altName2 = slotPng.replace("_", " ");
                    emojis = evt.getGuild().getEmojisByName(altName1, false);
                    if (emojis.isEmpty()) {
                        emojis = evt.getGuild().getEmojisByName(altName2, false);
                    }
                }
                slotPngEmojis.addAll(emojis);
            }
            
            if (slotsGifEmoji.isEmpty()) {
                // Try to find any animated emoji as a fallback
                List<RichCustomEmoji> allEmojis = evt.getGuild().getEmojiCache().asList();
                if (allEmojis != null) {
                    for (RichCustomEmoji emoji : allEmojis) {
                        if (emoji != null && emoji.isAnimated()) {
                            slotsGifEmoji.add(emoji);
                            break; // Use the first animated emoji found
                        }
                    }
                }
                
                if (slotsGifEmoji.isEmpty()) {
                    StringBuilder errorMsg = new StringBuilder();
                    errorMsg.append("❌ Error: Slot machine GIF emoji (`").append(slotsGif).append("`) not found in this server.\n");
                    errorMsg.append("Looking for emoji name: `").append(slotsGif).append("`\n\n");
                    errorMsg.append("**Available animated emojis:**\n");
                    if (allEmojis != null) {
                        int count = 0;
                        for (RichCustomEmoji emoji : allEmojis) {
                            if (emoji != null && emoji.isAnimated() && count < 10) {
                                errorMsg.append("- `").append(emoji.getName()).append("`\n");
                                count++;
                            }
                        }
                        if (count == 0) {
                            errorMsg.append("No animated emojis found in this server.\n");
                        }
                    }
                    errorMsg.append("\nPlease update the config with the correct emoji name or contact an administrator.");
                    evt.reply(errorMsg.toString()).setEphemeral(true).queue();
                    return;
                }
            }
            
            if (slotPngEmojis.isEmpty()) {
                evt.reply("❌ Error: Slot machine emojis not found in this server. Please contact an administrator.").setEphemeral(true).queue();
                return;
            }
            
            // Acknowledge the interaction immediately
            evt.deferReply().queue();
            
            String slotGif = slotsGifEmoji.get(0).getAsMention();
            String coinName = Main.getInstance().getConfig().getString("Bot.Messaging.Points.Name", "Charlie Coins");
            
            // Send initial spinning message
            evt.getHook().sendMessage(slotGif + slotGif + slotGif).queue((msg) -> {
                int rand1 = getRandomIndex(0, (slotPngs.size() - 1));
                int rand2 = getRandomIndex(0, (slotPngs.size() - 1));
                int rand3 = getRandomIndex(0, (slotPngs.size() - 1));
                
                // Make sure we don't go out of bounds
                rand1 = Math.min(rand1, slotPngEmojis.size() - 1);
                rand2 = Math.min(rand2, slotPngEmojis.size() - 1);
                rand3 = Math.min(rand3, slotPngEmojis.size() - 1);
                
                String emoji1 = slotPngEmojis.get(rand1).getAsMention();
                String emoji2 = slotPngEmojis.get(rand2).getAsMention();
                String emoji3 = slotPngEmojis.get(rand3).getAsMention();
                
                msg.editMessage(emoji1 + slotGif + slotGif).queueAfter(2, TimeUnit.SECONDS);
                msg.editMessage(emoji1 + emoji2 + slotGif).queueAfter(4, TimeUnit.SECONDS);
                msg.editMessage(emoji1 + emoji2 + emoji3).queueAfter(6, TimeUnit.SECONDS, (editedMsg) -> {
                    if (emoji1.equals(emoji2) && emoji2.equals(emoji3)) {
                        API.getInstance().addPoints(mem, amt);
                        editedMsg.reply("**Congrats** " + mem.getAsMention() + " -- You won `" + amt + "` " + coinName + "!").queueAfter(1, TimeUnit.SECONDS);
                    } else {
                        API.getInstance().removePoints(mem, amt);
                        editedMsg.reply("**Sorry** " + mem.getAsMention() + " -- You lost `" + amt + "` " + coinName +
                                ".... Better luck next time!").queueAfter(1, TimeUnit.SECONDS);
                    }
                });
            });
        } catch (Exception e) {
            evt.reply("❌ An error occurred while processing the gamble: " + e.getMessage()).setEphemeral(true).queue();
            e.printStackTrace();
        }
    }

    private static int getRandomIndex(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }

    private static List<String> getSlotPngs() {
        return Main.getInstance().getConfig().getStringList("Bot.Gamble.Slot_PNGs");
    }
    private static String getSlotsGif() {
        return Main.getInstance().getConfig().getString("Bot.Gamble.Slots_GIF");
    }
}
