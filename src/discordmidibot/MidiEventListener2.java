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
public class MidiEventListener2 extends ListenerAdapter {
    
    public static final int OCTAVE_OFFSET = 1;

    public static final List<Character> NOTELIST = Arrays.asList(new Character[] {'d', 0, 'r', 0, 'm', 'f', 0, 's', 0, 'l', 0, 't', 'D', 0, 'R', 0, 'M', 'F', 0, 'S', 0, 'L', 0, 'T'});
    public static final List<Character> QUICKDURATIONLIST = Arrays.asList(new Character[] {'z', 'Z', 'y', 'Y', 'x', 'X', 'e', 'E', 'q', 'Q', 'h', 'H', 'w', 'W', 'v', 'V', 'u', 'U'});
    
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
                        int timeStep = 500 * 4;
                        
                        String bpm = secondCommand;
                        if (ConverterUtils.isNumber(bpm)) {
                            timeStep = tempoMs(ConverterUtils.parseNumber(bpm).intValue());
                            sub += bpm.length() + 1;
                        }
                        
                        String channelsString = rawString.substring(Math.min(sub, rawString.length() - 1));

                        //boolean useCDEF = ParserUtils.containsCharacter(rawString, new char[] {'a','A','b','B','c','C','e','E','g','G'});
                        
                        String[] channelStringArray = channelsString.split("\n");
                        String[] channelInstrumentString = new String[channelStringArray.length];
                        
                        for (int i=0; i<channelStringArray.length; i++) {
                            if (!channelStringArray[i].isEmpty()) {
                                if (channelStringArray[i].charAt(0) == '[') {
                                    
                                    int endIndex = channelStringArray[i].indexOf(']');
                                    
                                    if (endIndex > 0) {
                                        channelInstrumentString[i] = channelStringArray[i].substring(1, endIndex);
                                        channelStringArray[i] = channelStringArray[i].substring(Math.min(endIndex + 1, channelStringArray[i].length() - 1));
                                    }
                                    
                                } else {
                                    channelInstrumentString[i] = "";
                                }
                            }
                        }
                        
                        LinkedList<List<Long>> syncTimesList = new LinkedList<>();
                        
                        for (int i=0; i<channelStringArray.length; i++) {
                            syncTimesList.add(getSyncTimes(channelStringArray[i].split(""), timeStep));
                        }
                        
                        List<Long> syncTimeList = getSyncTimes(syncTimesList);
                        
                        
                        for (int i=0; i<channelStringArray.length; i++) {
                            queueChannel(channelStringArray[i], timeStep, i, queue, syncTimeList);
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
                        
                        handler.play(new ScorePlayer(handler.getSynthesizer(), queue));

                        break;
                }
                
            }
            
            
        }
        
        if (!attachments.isEmpty()) {
            event.getMessage().delete().submit();
        }
        
    }
    
    
    public static void queueChannel(Queue<MidiChannelEvent> queue, String data, int channel, int beatTimeStep, float denominator) {
        
        String[] chars = data.split("");
        
        float defaultBeatMultiplier = denominator/4f;
        
        boolean isInChord = false;
        boolean isInKeySignature = false;
        
        int note;
        
        long time = 0;
        
        int octave = 4 + OCTAVE_OFFSET;
        int tempOctave = 0;
        int tempAccidental = 0;
        boolean forceAccidental = false;
        
        int[] accidental = new int[12];
        
        int dynamic = 48 - 1;
        float multiplier = 1;
        float periodTimeMultiplier = 1;
        float periodTimeHalfCounter = 1;
        
        List<Integer> noteList = new LinkedList<>();
        List<Integer> durationList = new LinkedList<>();
        boolean doAdvanceChord = true;
        
        for (String schar : chars) {
            char c = schar.charAt(0);

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
                
                if (doAdvanceChord) { //If advance time
                    
                }
                
                doAdvanceChord = true;
                
            } else if (c == '*') {
                doAdvanceChord = false;
            } else if ((note = noteList.indexOf(c) % 12) >= 0) {
                
                int realNote = IntegerUtils.bound(note + (forceAccidental ? tempAccidental : accidental[note]) + (octave * 12), 0, 128);
                
                if (isInKeySignature) {
                    accidental[note] = tempAccidental;
                } else if (isInChord) {
                    //Add to noteList and durationList
                } else { //Play note
                    int dt = Math.round(beatTimeStep * multiplier * periodTimeMultiplier);
                    queue.add(new MidiChannelEvent(time, channel, note, dynamic, true));
                    queue.add(new MidiChannelEvent(time + dt - 1, channel, note, dynamic, false));
                    time += dt;
                }
                
                //Reset temporary values, octave and accidental
                tempOctave = 0;
                if (!isInKeySignature) {
                    tempAccidental = 0;
                }
                forceAccidental = false;
                
            } else if (durationList.indexOf(c) >= 0) {
                
                int realDurationMultiplierPow = (durationList.indexOf(c) / 2) - 4;
                multiplier = (float)Math.pow(2, realDurationMultiplierPow) * defaultBeatMultiplier;
                periodTimeMultiplier = 1;
                periodTimeHalfCounter = 1;
                
            } else if (c == '.') {
                periodTimeHalfCounter /= 2;
                periodTimeMultiplier += periodTimeHalfCounter;
            } else if (c == 'z' || c == 'Z') {
                int dt = Math.round(beatTimeStep * multiplier);
                time += dt;
                
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
    
    
    
    public void queueChannel(String data, int timeStep, int channel, Queue<MidiChannelEvent> queue, List<Long> syncTimeList) {
        
        String[] chars = data.split("");
        
        int syncIndex = 0;
        long time = 0;

        int dynamic = 48 - 1;
        int shift = 0;
        
        double multiplier = 0.25d;
        
        //double periodTimeChange = 1;
        //double periodTimeShift = 1;
        
        LinkedList<Integer> noteList = new LinkedList<>();

        //boolean keepList = true;
        
        boolean isInChord = false;

        for (int i=0; i<chars.length; i++) {

            char c = chars[i].charAt(0);


            if (c == '+') {
                shift += 12;
                if (shift > 60) {
                    shift = 60;
                }
            } else if (c == '-') {
                shift -= 12;
                if (shift < -60) {
                    shift = -60;
                }
            } else if (c == '=') {
                shift = 0;
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
            } else if (c == '_') {
                dynamic -= 48 - 1;
            } else if (c == '|') {
                if (syncIndex < syncTimeList.size()) {
                    time = syncTimeList.get(syncIndex);
                    syncIndex++;
                }
            } else if (c == '.') {
                //periodTimeShift /= 2;
                //periodTimeChange += periodTimeShift;
            } else if (c == '(') {
                
                if (!isInChord) {
                    int dt = (int)(multiplier * timeStep);
                    sendNotes(queue, noteList, dt, time, channel, dynamic);
                    time = time + dt;
                    noteList.clear();
                }
                
                isInChord = true;
            } else if (c == ')') {
                isInChord = false;
            } else {

                int tempNote = getNoteName(c);

                if (tempNote > 0) {
                    
                    if (isInChord) {
                        noteList.add(tempNote + shift);
                    } else {
                        int dt = (int)(multiplier * timeStep);
                        sendNotes(queue, noteList, dt, time, channel, dynamic);
                        time = time + dt;
                        noteList.clear();
                        noteList.add(tempNote + shift);
                    }
                    
                } else if (tempNote == -2) {
                    
                    if (isInChord) {
                        
                    } else {
                        int dt = (int)(multiplier * timeStep);
                        sendNotes(queue, noteList, dt, time, channel, dynamic);
                        time = time + dt;
                        noteList.clear();
                        noteList.add(tempNote);
                    }
                    
                } else { //Not a note nor a space

                    double newMult = getMultiplier(c);

                    if (newMult == -1) {
                        //Doesn't exist...
                    } else { //Play the notes
                        
                        //int dt = (int)(multiplier * periodTimeChange * timeStep);
                        
                        if (!isInChord) {
                            int dt = (int)(multiplier * timeStep);
                            sendNotes(queue, noteList, dt, time, channel, dynamic);
                            time = time + dt;
                            noteList.clear();
                        }
                        
                        
                        multiplier = newMult;
                        
                        /*
                        periodTimeShift = 1d;
                        periodTimeChange = 1d;
                        for (int note : noteList) {
                            queue.add(new MidiChannelEvent(time, channel, note, dynamic, true));
                            queue.add(new MidiChannelEvent(time + dt - 1, channel, note, dynamic, false));
                        }
                        time = time + dt;
                        keepList = false;
                        */

                    }
                }

            }

        }
        int dt = (int)(multiplier * timeStep);
        sendNotes(queue, noteList, dt, time, channel, dynamic);
    }
    
    
    public void sendNotes(Queue queue, List<Integer> noteList, int dt, long time, int channel, int dynamic) {
        for (int note : noteList) {
            if (note >= 0) {
                queue.add(new MidiChannelEvent(time, channel, note, dynamic, true));
                queue.add(new MidiChannelEvent(time + dt - 1, channel, note, dynamic, false));
            }
        }
        noteList.clear();
    }
    
    
    public List<Long> getSyncTimes(String[] chars, int timeStep) {
        
        LinkedList<Long> syncTimes = new LinkedList<>();
        
        long time = 0;
        for (int i=0; i<chars.length; i++) {
            char c = chars[i].charAt(0);
            
            if (c == '|') {
                syncTimes.add(time);
            }
            
            double multiplier = getMultiplier(c);
            
            if (multiplier != -1) {
                time += (int)(multiplier * timeStep);
            }
            
        }
        
        return new ArrayList<>(syncTimes);
    }
    
    public List<Long> getSyncTimes(List<List<Long>> syncTimes) {
        
        int minSize = Integer.MAX_VALUE;
        
        LinkedList<Long> finalSyncTimes = new LinkedList<>();
        
        for (List<Long> syncTime : syncTimes) {
            if (minSize > syncTime.size()) {
                minSize = syncTime.size();
            }
        }
        
        for (int i=0; i<minSize; i++) {
            
            long maxValue = 0;
            
            for (int n=0; n<syncTimes.size(); n++) {
                long value = syncTimes.get(n).get(i);
                if (maxValue < value) {
                    maxValue = value;
                }
            }
            
            finalSyncTimes.add(maxValue);
        }
        
        return new ArrayList<>(finalSyncTimes);
    }
    
    public int tempoMs(int bpm) {
        return (int)((60d / bpm) * 1000 * 4);
    }
    
    public static int getScaleShift(char c) {
        switch (c) {
            case '-':
                return -60;
            case '0':
                return -48;
            case '1':
                return -36;
            case '2':
                return -24;
            case '3':
                return -12;
            case '4':
                return 0;
            case '5':
                return 12;
            case '6':
                return 24;
            case '7':
                return 36;
            case '8':
                return 48;
            case '9':
                return 60;
            default:
                return -1;
        }
    }
    
    public static double getMultiplier(char c) {
        switch (c) {
            case '8':
                return 8d;
            case '6':
                return 6d;
            case '4':
                return 4d;
            case '3':
                return 3d;
            case '2':
                return 2d;
            case 'w':
                return 1d;
            case 'W':
                return 1d + 0.5d;
            case 'h':
                return 0.5d;
            case 'H':
                return 0.5d + 0.25d;
            case 'q':
                return 0.25d;
            case 'Q':
                return 0.25d + 0.125d;
            case '"':
                return 0.125d;
            case '\'':
                return 0.0625d;
            default:
                return -1;
        }
    }
    public static int getNoteName(char c) {
        switch (c) {
            case 'd':
                return 60;
            case 'D':
                return 61;
            case 'r':
                return 62;
            case 'R':
                return 63;
            case 'm':
                return 64;
            case 'M':
                return 64;
            case 'f':
                return 65;
            case 'F':
                return 66;
            case 's':
                return 67;
            case 'S':
                return 68;
            case 'l':
                return 69;
            case 'L':
                return 70;
            case 'x':
                return 71;
            case 'X':
                return 71;
            case 'z':
                return -2;
            default:
                return -1;
        }
    }
    public static int getNote(char c) {
        switch (c) {
            case 'c':
                return 60;
            case 'C':
                return 61;
            case 'd':
                return 62;
            case 'D':
                return 63;
            case 'e':
                return 64;
            case 'E':
                return 64;
            case 'f':
                return 65;
            case 'F':
                return 66;
            case 'g':
                return 67;
            case 'G':
                return 68;
            case 'a':
                return 69;
            case 'A':
                return 70;
            case 'b':
                return 71;
            case 'B':
                return 71;
            case ' ':
                return -2;
            default:
                return -1;
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
            Logger.getLogger(MidiEventListener2.class.getName()).log(Level.SEVERE, null, ex);
            return new MidiSendHandler(midiSynth, null);
        }
        
    }

    private static class HashMapImpl extends HashMap<Character, Integer> {

        public HashMapImpl() {
        }
        {put('d', 0);}
    }
    
}
