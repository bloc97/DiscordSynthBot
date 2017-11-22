/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package discordmidibot;

import com.sun.media.sound.AudioSynthesizer;
import com.sun.media.sound.SoftSynthesizer;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import net.dv8tion.jda.core.audio.AudioSendHandler;

/**
 *
 * @author bowen
 */
public class MidiSendHandler implements AudioSendHandler {
    
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    
    private final AudioSynthesizer synthesizer;
    private final AudioInputStream stream;
    
    private ScorePlayer currentPlayer;
    
    public MidiSendHandler(AudioSynthesizer synthesizer, AudioInputStream stream) {
        this.stream = stream;
        this.synthesizer = synthesizer;
    }
    
    public void play(Queue<MidiChannelEvent> queue) {
        stop();
        currentPlayer = new ScorePlayer(synthesizer, queue);
        executor.submit(currentPlayer);
    }
    
    public void stop() {
        if (currentPlayer != null) {
            currentPlayer.requestTermination();
        }
        currentPlayer = null;
    }
    
    @Override
    public boolean canProvide() {
        try {
            return stream.available() >= 0 && !currentPlayer.isRequestTermination();
        } catch (IOException | NullPointerException ex) {
            return false;
        }
    }

    public AudioSynthesizer getSynthesizer() {
        return synthesizer;
    }
    
    @Override
    public byte[] provide20MsAudio() {
        int totalSize = ((int)stream.getFormat().getSampleRate() * stream.getFormat().getFrameSize() / 50);
        if (totalSize % 4 != 0) totalSize = totalSize - (totalSize%4);

        byte[] b = new byte[totalSize];
        try {
            int maxLength = stream.read(b);
            if (maxLength < 0) {
                stop();
            }
        } catch (IOException | NullPointerException ex) {
            return b;
        }
        return b;
    }

    @Override
    public boolean isOpus() {
        return false;
    }
}
