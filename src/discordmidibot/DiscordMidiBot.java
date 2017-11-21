/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package discordmidibot;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import jdautils.BotBuilder;

/**
 *
 * @author bowen
 */
public class DiscordMidiBot {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        
        Path path = FileSystems.getDefault().getPath("dapi.key");
        String token = Files.readAllLines(path).get(0);
            
        BotBuilder.buildBot(new MidiEventListener(), token);
    }
    
}
