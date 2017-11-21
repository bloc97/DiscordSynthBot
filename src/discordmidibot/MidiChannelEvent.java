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
public class MidiChannelEvent {
    
    private final long time;
    private final int channel;
    
    private final int note;
    private final int velocity;
    private final boolean isPress;
    
    private final String instrumentName;

    public MidiChannelEvent(long time, int channel, int note, int velocity, boolean isPress) {
        this.time = time;
        this.channel = channel;
        this.note = note;
        this.velocity = velocity;
        this.isPress = isPress;
        this.instrumentName = null;
    }
    
    public MidiChannelEvent(long time, int channel, String instrumentName) {
        this.time = time;
        this.channel = channel;
        this.note = 0;
        this.velocity = 0;
        this.isPress = false;
        this.instrumentName = instrumentName;
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

    public String getInstrumentName() {
        return (instrumentName == null) ? "" : instrumentName;
    }
    
    public boolean isChangeInstrument() {
        return instrumentName != null;
    }
    
    
    
}
