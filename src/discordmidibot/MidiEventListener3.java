/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package discordmidibot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sun.media.sound.AudioSynthesizer;
import com.sun.media.sound.SoftSynthesizer;
import helpers.Levenshtein;
import helpers.ParserUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import numberutils.IntegerUtils;
import parse.CommandParser;
import parse.Delimiter;
import parse.Nested;
import parse.Separator;
import parse.converter.ConverterUtils;


/**
 *
 * @author bowen
 */
public class MidiEventListener3 extends ListenerAdapter {
    
    public static final int OCTAVE_OFFSET = 1;

    public static final List<Character> NOTECHARLIST = Arrays.asList(new Character[] {'d', 0, 'r', 0, 'm', 'f', 0, 's', 0, 'l', 0, 't', 'D', 0, 'R', 0, 'M', 'F', 0, 'S', 0, 'L', 0, 'T'});
    public static final List<Character> DURATIONCHARLIST = Arrays.asList(new Character[] {'z', 'Z', 'y', 'Y', 'x', 'X', 'e', 'E', 'q', 'Q', 'h', 'H', 'w', 'W', 'v', 'V', 'u', 'U'});
    public static final List<Character> VALIDFRACTIONCHARLIST = Arrays.asList(new Character[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '/'});
    
    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        
        String rawString = event.getMessage().getRawContent();
        
        List<Message.Attachment> attachments = event.getMessage().getAttachments();
        
        //System.out.println(attachments.size());
        
        Queue<String> commands = new LinkedList<>(CommandParser.separatorParse(rawString, " "));
        
        if (commands.poll().equalsIgnoreCase("midi") || !attachments.isEmpty()) {
            
            Guild guild = event.getGuild();
            MidiSendHandler handler = getGuildHandler(guild);
            MidiChannel channel = handler.getSynthesizer().getChannels()[0];
            
            String secondCommand = commands.poll();
            
            if (!attachments.isEmpty()) {
                secondCommand = "blah";
            }
            
            switch (secondCommand.toLowerCase()) {
                
                case "start" :
                    
                    List<VoiceChannel> voiceChannels = event.getGuild().getVoiceChannelsByName("compo", true);
                    
                    if (!voiceChannels.isEmpty()) {
                        VoiceChannel voiceChannel = voiceChannels.get(0);
                        guild.getAudioManager().openAudioConnection(voiceChannel);
                    }
                    
                    
                    break;
                    
                case "test" :
                    
                    Queue<MidiChannelEvent> queue2 = new LinkedList<>();
                    
                    
                    for (int i=0; i<12; i++) {
                        queue2.add(new MidiChannelEvent((i * 1000), 0, 60, 50, true));
                        queue2.add(new MidiChannelEvent(((i + 1) * 1000), 0, 60, 50, false));
                    }
                    
                    handler.play(new ScorePlayer(handler.getSynthesizer(), queue2));
        
                    break;
                    
                    
                case "stop" :
                    
                    handler.stop();
                    
                default :
                    
                    if (!attachments.isEmpty()) {
                        File file = new File("tempMidi.smid");
                        attachments.get(0).download(file);
                        try {
                            List<String> strList = Files.readAllLines(FileSystems.getDefault().getPath("tempMidi.smid"));
                            String rectString = ParserUtils.join(strList, '\n');
                            int nextSpace = rectString.indexOf(" ");
                            if (nextSpace != -1 && nextSpace < rectString.length()) {
                                secondCommand = rectString.substring(0, rectString.indexOf(" "));
                            }
                            rawString = "midi " + rectString;
                            //System.out.println(rawString);
                        } catch (FileNotFoundException ex) {
                            rawString = "";
                        } catch (IOException ex) {
                            rawString = "";
                        }
                        file.delete();
                    }
                    
                    
                    if (rawString.length() > 5) {
                    
                        Queue<MidiChannelEvent> queue = new PriorityQueue<>(new Comparator<MidiChannelEvent>() {

                            @Override
                            public int compare(MidiChannelEvent o1, MidiChannelEvent o2) {
                                return (int)(o1.getTime() - o2.getTime());
                            }

                        });
                        
                        int sub = 5;
                        int quarTimeStep = 500;
                        
                        String bpm = secondCommand;
                        if (ConverterUtils.isNumber(bpm)) {
                            quarTimeStep = tempoMs(ConverterUtils.parseNumber(bpm).intValue());
                            sub += bpm.length() + 1;
                        }
                        
                        String channelsString = rawString.substring(Math.min(sub, rawString.length() - 1));

                        //boolean useCDEF = ParserUtils.containsCharacter(rawString, new char[] {'a','A','b','B','c','C','e','E','g','G'});
                        
                        String[] channelStringArray = channelsString.split("\n");
                        String[] channelInstrumentString = new String[channelStringArray.length];
                        
                        for (int i=0; i<channelStringArray.length; i++) {
                            if (!channelStringArray[i].isEmpty()) {
                                if (channelStringArray[i].charAt(0) == '{') {
                                    
                                    int endIndex = channelStringArray[i].indexOf('}');
                                    
                                    if (endIndex > 0) {
                                        channelInstrumentString[i] = channelStringArray[i].substring(1, endIndex);
                                        channelStringArray[i] = channelStringArray[i].substring(Math.min(endIndex + 1, channelStringArray[i].length() - 1));
                                    }
                                    
                                } else {
                                    channelInstrumentString[i] = "";
                                }
                            }
                        }
                        
                        for (int i=0; i<channelInstrumentString.length; i++) {
                            
                            if (channelInstrumentString.length != 0) {
                                
                                Instrument[] instruments = handler.getSynthesizer().getLoadedInstruments();
                                
                                
                                Instrument closestInstrument = instruments[0];
                                int closestScore = Integer.MAX_VALUE;

                                for (Instrument instrument : instruments) {
                                    int thisScore = Levenshtein.subwordDistance(instrument.getName(), channelInstrumentString[i]);
                                    if (closestScore > thisScore) {
                                        closestScore = thisScore;
                                        closestInstrument = instrument;
                                    }
                                }
                                
                                handler.getSynthesizer().getChannels()[i].programChange(closestInstrument.getPatch().getBank(), closestInstrument.getPatch().getProgram());
                                
                            } else {
                                handler.getSynthesizer().getChannels()[i].programChange(0, 0);
                            }
                            
                        }
                        
                        for (int i=0; i<channelStringArray.length; i++) {
                            queueChannel(queue, channelStringArray[i], i, quarTimeStep, 4);
                        }
                        
                        handler.play(new ScorePlayer(handler.getSynthesizer(), queue));

                        break;
                }
                
            }
            
            
        }
        
        if (!attachments.isEmpty()) {
            event.getMessage().delete().submit();
        }
        
    }
    
    
    public int tempoMs(int bpm) {
        return (int)((60d / bpm) * 1000);
    }
    
    public static void queueChannel(Queue<MidiChannelEvent> queue, String data, int channel, int beatTimeStep, float denominator) {
        
        String[] chars = data.split("");
        
        float defaultBeatMultiplier = denominator/4f;
        
        boolean isInChord = false;
        boolean isInKeySignature = false;
        
        boolean nextStaccato = false;
        
        boolean isInFractionalNumber = false;
        
        int note;
        
        long time = 0;
        
        int octave = 4 + OCTAVE_OFFSET;
        int tempOctave = 0;
        int tempAccidental = 0;
        boolean forceAccidental = false;
        
        int[] accidental = new int[12];
        
        int dynamic = 48 - 1;
        float multiplier = 1;
        String fractionalMultiplier = "";
        float periodTimeMultiplier = 1;
        float periodTimeHalfCounter = 1;
        
        LinkedList<Integer> noteList = new LinkedList<>();
        LinkedList<Integer> durationList = new LinkedList<>();
        LinkedList<Boolean> staccatoList = new LinkedList<>();
        boolean doAdvanceTime = true;
        
        for (String schar : chars) {
            char c = schar.charAt(0);
            
            if (isInFractionalNumber) {
                if (VALIDFRACTIONCHARLIST.indexOf(c) < 0) {
                    isInFractionalNumber = false; //Leave fractionalNumber
                }
            }
            
            if (c == '[') {
                isInKeySignature = true;
                tempAccidental = 0;
                forceAccidental = false;
            } else if (c == ']') {
                isInKeySignature = false;
                tempAccidental = 0;
                forceAccidental = false;
            } else if (c == '(') {
                isInChord = true;
            } else if (c == ')') {
                isInChord = false;
                //Play chord from noteList and durationList
                
                int largestDt = 0;
                
                while (!noteList.isEmpty()) {
                    int realNote = noteList.poll();
                    int dt = durationList.poll();
                    boolean useStaccato = staccatoList.poll();
                    if (largestDt < dt) {
                        largestDt = dt;
                    }
                    if (realNote >= 0) {
                        queue.add(new MidiChannelEvent(time, channel, realNote, dynamic, true));
                        if (useStaccato) {
                            queue.add(new MidiChannelEvent(time + (dt / 2), channel, realNote, dynamic, false));
                        } else {
                            queue.add(new MidiChannelEvent(time + dt - 1, channel, realNote, dynamic, false));
                        }
                    }
                }
                
                if (doAdvanceTime) { //If advance time
                    time += largestDt;
                }
                
                doAdvanceTime = true;
                
            } else if (c == '^') {
                doAdvanceTime = false;
            } else if ((note = NOTECHARLIST.indexOf(c) % 12) >= 0) {
                
                int realNote = IntegerUtils.bound(note + (forceAccidental ? tempAccidental : accidental[note]) + ((octave + tempOctave) * 12), 0, 128);
                
                if (isInKeySignature) {
                    accidental[note] = tempAccidental;
                } else if (isInChord) {
                    
                    float realMultiplier = 1;
                    if (fractionalMultiplier.isEmpty()) {
                        realMultiplier = multiplier;
                    } else {
                        realMultiplier = parseStringDuration(fractionalMultiplier);
                    }
                    
                    int dt = Math.round(beatTimeStep * realMultiplier * periodTimeMultiplier);
                    noteList.add(realNote);
                    durationList.add(dt);
                    staccatoList.add(nextStaccato);
                    
                } else { //Play note
                    
                    float realMultiplier = 1;
                    if (fractionalMultiplier.isEmpty()) {
                        realMultiplier = multiplier;
                    } else {
                        realMultiplier = parseStringDuration(fractionalMultiplier);
                    }
                    
                    int dt = Math.round(beatTimeStep * realMultiplier * periodTimeMultiplier);
                    queue.add(new MidiChannelEvent(time, channel, realNote, dynamic, true));
                    
                    if (nextStaccato) {
                        queue.add(new MidiChannelEvent(time + (dt / 2), channel, realNote, dynamic, false));
                    } else {
                        queue.add(new MidiChannelEvent(time + dt - 1, channel, realNote, dynamic, false));
                    }
                    
                    if (doAdvanceTime) { //If advance time
                        time += dt;
                    }

                    doAdvanceTime = true;
                }
                
                //Reset temporary values, octave and accidental
                tempOctave = 0;
                if (!isInKeySignature) {
                    tempAccidental = 0;
                }
                forceAccidental = false;
                nextStaccato = false;
                
            } else if (DURATIONCHARLIST.indexOf(c) >= 0) {
                
                int realDurationMultiplierPow = (DURATIONCHARLIST.indexOf(c) / 2) - 4;
                multiplier = (float)Math.pow(2, realDurationMultiplierPow) * defaultBeatMultiplier;
                periodTimeMultiplier = 1;
                periodTimeHalfCounter = 1;
                
                fractionalMultiplier = ""; //Clear fraction multiplier
                
            } else if (VALIDFRACTIONCHARLIST.indexOf(c) >= 0) {
                
                if (!isInFractionalNumber) {
                    fractionalMultiplier = "";
                }
                fractionalMultiplier += "" + c;
                isInFractionalNumber = true;
                
            } else if (c == '.') {
                periodTimeHalfCounter /= 2;
                periodTimeMultiplier += periodTimeHalfCounter;
            } else if (c == '!') {
                nextStaccato = true;
            } else if (c == '~') {
                
                float realMultiplier = 1;
                if (fractionalMultiplier.isEmpty()) {
                    realMultiplier = multiplier;
                } else {
                    realMultiplier = parseStringDuration(fractionalMultiplier);
                }
                int dt = Math.round(beatTimeStep * realMultiplier * periodTimeMultiplier);
                
                if (isInChord) {
                    
                    noteList.add(-1);
                    durationList.add(dt);
                    
                } else {
                    if (doAdvanceTime) { //If advance time
                        time += dt;
                    }

                    doAdvanceTime = true;
                    
                }
                
            } else if (c == 'g' || c == 'G') {
                octave = 4 + OCTAVE_OFFSET;
            } else if (c == 'b' || c == 'B') {
                octave = 3 + OCTAVE_OFFSET;
            } else if (c == '+') {
                octave++;
                if (octave > 10) {
                    octave = 10;
                }
            } else if (c == '-') {
                octave--;
                if (octave < 0) {
                    octave = 0;
                }
            } else if (c == '\'') {
                tempOctave++;
                if (tempOctave + octave > 10) {
                    tempOctave--;
                }
            } else if (c == ',') {
                tempOctave--;
                if (tempOctave + octave < 0) {
                    tempOctave++;
                }
            } else if (c == '#') {
                if (tempAccidental < 0) {
                    tempAccidental = 0;
                }               
                tempAccidental++;
                forceAccidental = true;
            } else if (c == '$') {
                if (tempAccidental > 0) {
                    tempAccidental = 0;
                }               
                tempAccidental--;
                forceAccidental = true;
            } else if (c == 'n' || c == 'N') {
                tempAccidental = 0;
                forceAccidental = true;
            } else if (c == '<') {
                dynamic -= 16;
                if (dynamic < 15) {
                    dynamic = 15;
                }
            } else if (c == '>') {
                dynamic += 16;
                if (dynamic > 127) {
                    dynamic = 127;
                }
            } else if (c == '%') {
                dynamic = 48 - 1;
            }
            
        }
        
        
        
        
    }
    
    public static int getDt(float multiplier, float periodTimeMultiplier, String fractionalMultiplier, int beatTimeStep) {
        
        float realMultiplier = 1;
        if (fractionalMultiplier.isEmpty()) {
            realMultiplier = multiplier;
        } else {
            realMultiplier = parseStringDuration(fractionalMultiplier);
        }
        return Math.round(beatTimeStep * realMultiplier * periodTimeMultiplier);

    }
    
    
    public static float parseStringDuration(String fractionalDuration) {
        
        if (fractionalDuration.isEmpty()) {
            return 1f;
        } else {
            String[] fractionParts = fractionalDuration.split("/");
            
            if (fractionParts.length == 1) {
                return parseNumber(fractionParts[0]);
                
            } else if (fractionParts.length >= 2) {
                
                float numerator = parseNumber(fractionParts[0]);
                float denom = parseNumber(fractionParts[fractionParts.length - 1]);
                
                return numerator / denom;
                
            } else { //If fractionalDuration equals "/"
                return 0.5f;
            }
            
        }
    }
    
    private static float parseNumber(String numberString) {
        try {
            return Float.parseFloat(numberString);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
    
    private static final ConcurrentMap<Guild, MidiSendHandler> map = new ConcurrentHashMap<>();
    
    public static MidiSendHandler getGuildHandler(Guild guild) {
        if (map.containsKey(guild)) {
            return map.get(guild);
        }
        //AudioSynthesizer midiSynth = new SoftSynthesizer(); 
        AudioSynthesizer midiSynth = new SoftSynthesizer();
        
        AudioFormat format = new AudioFormat(48000, 16, 2, true, true);
        
        try {
            AudioInputStream stream = midiSynth.openStream(format, null);
            MidiSendHandler handler = new MidiSendHandler(midiSynth, stream);
            guild.getAudioManager().setSendingHandler(handler);
            map.put(guild, handler);
            return handler;
        } catch (MidiUnavailableException ex) {
            Logger.getLogger(MidiEventListener3.class.getName()).log(Level.SEVERE, null, ex);
            return new MidiSendHandler(midiSynth, null);
        }
        
    }

    private static class HashMapImpl extends HashMap<Character, Integer> {

        public HashMapImpl() {
        }
        {put('d', 0);}
    }
    
}
