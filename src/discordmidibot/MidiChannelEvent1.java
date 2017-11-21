/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package discordmidibot;

/**
 *
 * @author bowen
 */
public class MidiChannelEvent1 {
    
    private final long time;
    private final int channel;
    
    private final int note;
    private final int velocity;
    private final boolean isPress;

    public MidiChannelEvent1(long time, int channel, int note, int velocity, boolean isPress) {
        this.time = time;
        this.channel = channel;
        this.note = note;
        this.velocity = velocity;
        this.isPress = isPress;
    }

    public long getTime() {
        return time;
    }
    
    public int getChannel() {
        return channel;
    }

    public int getNote() {
        return note;
    }

    public int getVelocity() {
        return velocity;
    }

    public boolean isPress() {
        return isPress;
    }
    
    
    
    
}
