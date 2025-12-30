package com.jaredscarito.listeners.commands;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;

public class BarkCommand {
    public static void invoke(SlashCommandInteractionEvent evt) {
        Member mem = evt.getMember();
        if (mem == null || mem.getUser().isBot()) {
            evt.reply("❌ Error: Unable to process command.").setEphemeral(true).queue();
            return;
        }
        
        try {
            // Get random Charlie image
            String charlieImage = getRandomCharlieImage();
            
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("🐕 Charlie says: WOOF! 🐕");
            eb.setColor(Color.ORANGE);
            eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
            
            // Try to load the image file
            File imageFile = new File("assets/" + charlieImage);
            if (imageFile.exists() && imageFile.isFile()) {
                try {
                    eb.setDescription("Here's a picture of Charlie to brighten your day!");
                    eb.setImage("attachment://" + charlieImage);
                    evt.replyEmbeds(eb.build()).addFiles(FileUpload.fromData(imageFile, charlieImage)).queue();
                } catch (Exception e) {
                    // If file upload fails, use fallback
                    eb.setDescription("🐕 Charlie says: WOOF! 🐕\n\n*Charlie's memory lives on in this bot. He was a good boy who brought joy to everyone around him.*");
                    evt.replyEmbeds(eb.build()).queue();
                }
            } else {
                // Fallback if image doesn't exist
                eb.setDescription("🐕 Charlie says: WOOF! 🐕\n\n*Charlie's memory lives on in this bot. He was a good boy who brought joy to everyone around him.*");
                evt.replyEmbeds(eb.build()).queue();
            }
        } catch (Exception e) {
            // Always respond, even on error
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("🐕 Charlie says: WOOF! 🐕");
            eb.setDescription("🐕 Charlie says: WOOF! 🐕\n\n*Charlie's memory lives on in this bot. He was a good boy who brought joy to everyone around him.*");
            eb.setColor(Color.ORANGE);
            if (mem != null) {
                eb.setAuthor(mem.getEffectiveName(), mem.getAvatarUrl());
            }
            evt.replyEmbeds(eb.build()).queue();
            e.printStackTrace();
        }
    }
    
    private static String getRandomCharlieImage() {
        List<String> charlieImages = new ArrayList<>();
        charlieImages.add("charlie_1.jpg");
        charlieImages.add("charlie_2.jpg");
        charlieImages.add("charlie_3.jpg");
        charlieImages.add("charlie_4.jpg");
        charlieImages.add("charlie_5.jpg");
        
        Random random = new Random();
        return charlieImages.get(random.nextInt(charlieImages.size()));
    }
}
