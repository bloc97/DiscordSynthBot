/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package discordmidibot;

import helpers.Levenshtein;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.Synthesizer;

/**
 *
 * @author bowen
 */
public class ScorePlayer implements Runnable {

    private final Synthesizer synthesizer;
    private final Queue<MidiChannelEvent> queue;
    
    private boolean requestTermination = false;
    
    public ScorePlayer(Synthesizer synthesizer, Queue<MidiChannelEvent> queue) {
        this.synthesizer = synthesizer;
        this.queue = queue;
    }

    public boolean isRequestTermination() {
        return requestTermination;
    }
    
    public void requestTermination() {
        requestTermination = true;
        for (MidiChannel channel : synthesizer.getChannels()) {
            channel.allSoundOff();
        }
    }
    
    
    @Override
    public void run() {
        
        long startingTime = System.currentTimeMillis();
        
        MidiChannel[] channels = synthesizer.getChannels();
        while (!requestTermination && !queue.isEmpty()) {
            
            long currentTime = System.currentTimeMillis() - startingTime;
            
            if (queue.peek().getTime() <= currentTime) {
                MidiChannelEvent event = queue.poll();
                
                if (event.isChangeInstrument()) {
                    changeInstrument(event.getChannel(), event.getInstrumentName(), synthesizer);
                } else if (event.isPress()) {
                    channels[event.getChannel()].noteOn(event.getNote(), event.getVelocity());
                } else {
                    channels[event.getChannel()].noteOff(event.getNote(), event.getVelocity());
                }
                
            }
            
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Logger.getLogger(ScorePlayer.class.getName()).log(Level.SEVERE, null, ex);
        }
        requestTermination();
    }
    
    public static void changeInstrument(int channel, String instrumentName, Synthesizer synthesizer) {
        
        Instrument[] instruments = synthesizer.getLoadedInstruments();


        Instrument closestInstrument = instruments[0];
        int closestScore = Integer.MAX_VALUE;
        if (!instrumentName.isEmpty()) {
            for (Instrument instrument : instruments) {
                int thisScore = Levenshtein.substringDistance(instrument.getName(), instrumentName);
                if (closestScore > thisScore) {
                    closestScore = thisScore;
                    closestInstrument = instrument;
                }
            }
        }

        synthesizer.getChannels()[channel].programChange(closestInstrument.getPatch().getBank(), closestInstrument.getPatch().getProgram());
    }
    
    public static void changeDefaultInstruments(int channels, String instrumentName, Synthesizer synthesizer) {
        
        Instrument[] instruments = synthesizer.getLoadedInstruments();

        Instrument closestInstrument = instruments[0];
        int closestScore = Integer.MAX_VALUE;
        if (!instrumentName.isEmpty()) {
            for (Instrument instrument : instruments) {
                int thisScore = Levenshtein.substringDistance(instrument.getName(), instrumentName);
                if (closestScore > thisScore) {
                    closestScore = thisScore;
                    closestInstrument = instrument;
                }
            }
        }
        int maxChannels = Math.min(channels, synthesizer.getChannels().length);
        for (int i=0; i<maxChannels; i++) {
            synthesizer.getChannels()[i].programChange(closestInstrument.getPatch().getBank(), closestInstrument.getPatch().getProgram());
        }
        
        
    }
    public static void resetDefaultInstruments(int channels, Synthesizer synthesizer) {
        
        int maxChannels = Math.min(channels, synthesizer.getChannels().length);
        for (int i=0; i<maxChannels; i++) {
            synthesizer.getChannels()[i].programChange(0, 0);
        }
        
        
    }
    
}
