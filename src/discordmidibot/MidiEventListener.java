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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.apache.commons.lang3.StringUtils;
import parse.CommandParser;
import parse.Delimiter;
import parse.Nested;
import parse.Separator;
import parse.converter.ConverterUtils;


/**
 *
 * @author bowen
 */
public class MidiEventListener extends ListenerAdapter {
    
    public static final int OCTAVE_OFFSET = 1;

    public static final List<Character> NOTECHARLIST = Arrays.asList(new Character[] {'d', 0, 'r', 0, 'm', 'f', 0, 's', 0, 'l', 0, 't', 'D', 0, 'R', 0, 'M', 'F', 0, 'S', 0, 'L', 0, 'T'});
    public static final List<Character> DURATIONCHARLIST = Arrays.asList(new Character[] {'z', 'Z', 'y', 'Y', 'x', 'X', 'e', 'E', 'q', 'Q', 'h', 'H', 'w', 'W', 'v', 'V', 'u', 'U'});
    public static final List<Character> VALIDFRACTIONCHARLIST = Arrays.asList(new Character[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '/'});
    
    public static boolean isBPM(String string) {
        try {
            int bpm = Integer.parseInt(string);
            return bpm != 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
    
    public static boolean isTimeSignature(String string) {
        return (string = string.trim()).startsWith(":") && string.endsWith(":") && string.contains("/");
    }
    
    public static boolean isInstrument(String string) {
        return (string = string.trim()).startsWith("{") && string.endsWith("}");
    }
    
    public static boolean isAssignment(String string) {
        return string.contains("=");
    }
    public String replaceEach(String s, Map<String, String> replacements)
    {
        int size = replacements.size();
        String[] keys = replacements.keySet().toArray(new String[size]);
        String[] values = replacements.values().toArray(new String[size]);
        return StringUtils.replaceEach(s, keys, values);
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        
        String rawString = event.getMessage().getRawContent();
        Guild guild = event.getGuild();
            
        try {
            if (rawString.startsWith("!drm")) {

                List<VoiceChannel> voiceChannels = event.getGuild().getVoiceChannelsByName("compo", true);

                if (!voiceChannels.isEmpty() && !guild.getAudioManager().isConnected()) {
                    VoiceChannel voiceChannel = voiceChannels.get(0);
                    guild.getAudioManager().openAudioConnection(voiceChannel);
                }

                MidiSendHandler handler = getGuildHandler(guild);
                handler.stop();

                String[] stringLines = rawString.split("\n");

                String[] firstLineArgs = stringLines[0].split(" ");

                boolean foundBPM, foundTime, foundInstrument;
                foundBPM = false;
                foundTime = false;
                foundInstrument = false;

                int defaultBPM = 120;
                float defaultNumerator = 4f;
                float defaultDenominator = 4f;

                int subStringIndex = firstLineArgs[0].length() + 1;

                ScorePlayer.resetDefaultInstruments(stringLines.length, handler.getSynthesizer());

                for (int i=1; i<firstLineArgs.length && i<4; i++) { //read the first three properties after the initial command
                    String rawPropertyString = firstLineArgs[i];
                    String potentialProperty = rawPropertyString.trim();

                    if (!foundBPM && isBPM(potentialProperty)) {
                        defaultBPM = Integer.parseInt(potentialProperty);
                        foundBPM = true;
                        subStringIndex += rawPropertyString.length() + 1;

                    } else if (!foundTime && isTimeSignature(potentialProperty)) {
                        Map.Entry<Float, Float> timeFraction = parseTimeSignature(potentialProperty.substring(1, potentialProperty.length() - 1));
                        defaultNumerator = timeFraction.getKey();
                        defaultDenominator = timeFraction.getValue();
                        foundTime = true;
                        subStringIndex += rawPropertyString.length() + 1;

                    } else if (!foundInstrument && isInstrument(potentialProperty)) {
                        String instrument = potentialProperty.substring(1, potentialProperty.length() - 1);
                        ScorePlayer.changeDefaultInstruments(stringLines.length, instrument, handler.getSynthesizer());
                        foundInstrument = true;
                        subStringIndex += rawPropertyString.length() + 1;

                    } else {
                        //This is not a property, which means we are at the end, start reading data
                        break;
                    }
                }

                subStringIndex = Math.min(subStringIndex, stringLines[0].length());
                stringLines[0] = stringLines[0].substring(subStringIndex);

                Queue<MidiChannelEvent> queue = new PriorityQueue<>(new Comparator<MidiChannelEvent>() {
                    @Override
                    public int compare(MidiChannelEvent o1, MidiChannelEvent o2) {
                        return (int)(o1.getTime() - o2.getTime());
                    }
                });

                for (int i=0; i<stringLines.length; i++) {
                    queueChannel(queue, stringLines[i], i, tempoMs(defaultBPM), defaultNumerator, defaultDenominator);
                }

                handler.play(queue);


            } else if (rawString.startsWith("drmex") || rawString.startsWith("!drmex")) {
                
                List<VoiceChannel> voiceChannels = event.getGuild().getVoiceChannelsByName("compo", true);

                if (!voiceChannels.isEmpty() && !guild.getAudioManager().isConnected()) {
                    VoiceChannel voiceChannel = voiceChannels.get(0);
                    guild.getAudioManager().openAudioConnection(voiceChannel);
                }

                MidiSendHandler handler = getGuildHandler(guild);
                handler.stop();

                String[] stringLines = rawString.split("\n");

                boolean foundBPM, foundTime, foundInstrument;
                foundBPM = false;
                foundTime = false;
                foundInstrument = false;

                int defaultBPM = 120;
                float defaultNumerator = 4f;
                float defaultDenominator = 4f;
                int defaultAssignmentDepth = 1;
                float biggestAssignmentRatio = 1;
                
                Map<String, String> assignmentMap = new LinkedHashMap<>();

                int dataStartIndex = 1;

                ScorePlayer.resetDefaultInstruments(stringLines.length, handler.getSynthesizer());

                for (int i=1; i<stringLines.length; i++) { //read the first three properties after the initial command
                    String rawPropertyString = stringLines[i];
                    String potentialProperty = rawPropertyString.trim();

                    if (!foundBPM && isBPM(potentialProperty)) {
                        defaultBPM = Integer.parseInt(potentialProperty);
                        foundBPM = true;
                        dataStartIndex += 1;

                    } else if (!foundTime && isTimeSignature(potentialProperty)) {
                        Map.Entry<Float, Float> timeFraction = parseTimeSignature(potentialProperty.substring(1, potentialProperty.length() - 1));
                        defaultNumerator = timeFraction.getKey();
                        defaultDenominator = timeFraction.getValue();
                        foundTime = true;
                        dataStartIndex += 1;

                    } else if (!foundInstrument && isInstrument(potentialProperty)) {
                        String instrument = potentialProperty.substring(1, potentialProperty.length() - 1);
                        ScorePlayer.changeDefaultInstruments(stringLines.length, instrument, handler.getSynthesizer());
                        foundInstrument = true;
                        dataStartIndex += 1;

                    } else if (isAssignment(potentialProperty)) {
                        try {
                            String lefthand = potentialProperty.substring(0, potentialProperty.indexOf('=')).trim();
                            String righthand = potentialProperty.substring(potentialProperty.indexOf('=') + 1, potentialProperty.length()).trim();
                            
                            float assignmentRatio = (float)righthand.length() / lefthand.length();
                            
                            if (lefthand.toLowerCase().equals("k")) {
                                defaultAssignmentDepth = Integer.parseInt(righthand);
                            } else {
                                assignmentMap.put(lefthand, righthand);
                                if (biggestAssignmentRatio < assignmentRatio) {
                                    biggestAssignmentRatio = assignmentRatio;
                                }
                            }
                        } catch (Exception ex) {
                        }
                        dataStartIndex += 1;
                    } else {
                        //This is not a property, which means we are at the end, start reading data
                        break;
                    }
                }

                Queue<MidiChannelEvent> queue = new PriorityQueue<>(new Comparator<MidiChannelEvent>() {
                    @Override
                    public int compare(MidiChannelEvent o1, MidiChannelEvent o2) {
                        return (int)(o1.getTime() - o2.getTime());
                    }
                });

                for (int i=dataStartIndex; i<stringLines.length; i++) {
                    //System.out.println(stringLines[i]);
                    for (int n=0; n<defaultAssignmentDepth; n++) {
                        if (stringLines[i].length() * biggestAssignmentRatio > 20000) {
                            break;
                        }
                        stringLines[i] = replaceEach(stringLines[i], assignmentMap);
                    }
                    //System.out.println(stringLines[i]);
                    queueChannel(queue, stringLines[i], i, tempoMs(defaultBPM), defaultNumerator, defaultDenominator);
                }

                handler.play(queue);


            } else if (rawString.startsWith("!synth")) { //Displays help

            }
        } catch (Exception ex) {
            
        }
        
        
        
        /**
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
                            queueChannel(queue, channelStringArray[i], i, quarTimeStep, 4, 4);
                        }
                        
                        handler.play(queue);

                        break;
                }
                
            }
            
            
        }
        
        if (!attachments.isEmpty()) {
            event.getMessage().delete().submit();
        }*/
        
    }
    
    
    public int tempoMs(int bpm) {
        return (int)((60d / bpm) * 1000);
    }
    
    public static void queueChannel(Queue<MidiChannelEvent> queue, String data, int channel, int beatTimeStep, float numerator, float denominator) {
        
        String[] chars = data.split("");
        
        boolean isInComment = false;
        boolean isInInstrumentName = false;
        String instrumentName = "";
        
        boolean isInChord = false;
        boolean isInKeySignature = false;
        boolean isInTimeSignature = false;
        
        boolean nextStaccato = false;
        
        boolean isInFractionalNumber = false;
        
        int note;
        
        long time = 0;
        
        long syncTime = 0;
        
        int octave = 4 + OCTAVE_OFFSET;
        int tempOctave = 0;
        int tempAccidental = 0;
        boolean forceAccidental = false;
        
        int[] accidental = new int[12];
        
        int dynamic = 48 - 1;
        float multiplier = 1;
        String fractionalMultiplier = "";
        String timeSignature = "";
        float periodTimeMultiplier = 1;
        float periodTimeHalfCounter = 1;
        
        LinkedList<Integer> noteList = new LinkedList<>();
        LinkedList<Integer> durationList = new LinkedList<>();
        LinkedList<Boolean> staccatoList = new LinkedList<>();
        boolean doAdvanceTime = true;
        
        for (String schar : chars) {
            char c = schar.charAt(0);
            
            
            if (isInComment) {
                if (c == '\\') {
                    isInComment = false;
                }
                continue;
            } else {
                if (c == '\\') {
                    isInComment = true;
                    continue;
                }
            }
            
            if (isInInstrumentName) {
                if (c == '}') {
                    queue.add(new MidiChannelEvent(time, channel, instrumentName));
                    instrumentName = "";
                    isInInstrumentName = false;
                } else {
                    instrumentName += "" + c;
                }
                continue;
            } else {
                if (c == '{') {
                    isInInstrumentName = true;
                    continue;
                }
            }
            
            
            if (isInFractionalNumber) {
                if (VALIDFRACTIONCHARLIST.indexOf(c) < 0) {
                    isInFractionalNumber = false; //Leave fractionalNumber
                    if (c != '@' && c != ';') {
                        multiplier = parseStringMultiplier(fractionalMultiplier);
                        periodTimeMultiplier = 1;
                        periodTimeHalfCounter = 1;
                        fractionalMultiplier = "";
                    }
                }
            }
            
            if (c == ':') {
                
                if (isInTimeSignature) {
                    Map.Entry<Float, Float> signature = parseTimeSignature(timeSignature);
                    numerator = signature.getKey();
                    denominator = signature.getValue();
                    timeSignature = "";
                }
                
                isInTimeSignature = !isInTimeSignature;
                
            } else if (c == '|') {
                syncTime += beatTimeStep * numerator;
                time = syncTime;
                
            } else if (c == '?') {
                syncTime = time;
                
            } else if (c == '[') {
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
                    
                    int dt = getDt(multiplier, periodTimeMultiplier, beatTimeStep);
                    noteList.add(realNote);
                    durationList.add(dt);
                    staccatoList.add(nextStaccato);
                    
                } else { //Play note
                    
                    int dt = getDt(multiplier, periodTimeMultiplier, beatTimeStep);
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
                multiplier = (float)Math.pow(2, realDurationMultiplierPow) * (denominator / 4f); //divided by 4 since we take the quarter as a beat initially
                periodTimeMultiplier = 1;
                periodTimeHalfCounter = 1;
                
                
            } else if (VALIDFRACTIONCHARLIST.indexOf(c) >= 0) {
                
                if (isInTimeSignature) {
                    timeSignature += "" + c;
                } else {
                    fractionalMultiplier += "" + c;
                    isInFractionalNumber = true;
                }
                
            } else if (c == '.') {
                periodTimeHalfCounter /= 2;
                periodTimeMultiplier += periodTimeHalfCounter;
            } else if (c == '!') {
                nextStaccato = true;
            } else if (c == '~') {
                
                int dt = getDt(multiplier, periodTimeMultiplier, beatTimeStep);
                if (isInChord) {
                    noteList.add(-1);
                    durationList.add(dt);
                } else {
                    if (doAdvanceTime) { //If advance time
                        time += dt;
                    }
                    doAdvanceTime = true;
                }
                
            } else if (c == '&') {
                
                int dt = -getDt(multiplier, periodTimeMultiplier, beatTimeStep);
                if (isInChord) {
                    noteList.add(-1);
                    durationList.add(dt);
                } else {
                    if (doAdvanceTime) { //If advance time
                        time += dt;
                    }
                    doAdvanceTime = true;
                }
                
            } else if (c == '@') {
                
                long absoluteTimeBeats = 0;
                
                if (!fractionalMultiplier.isEmpty()) {
                    absoluteTimeBeats = parseIntAbsoluteTime(fractionalMultiplier);
                    fractionalMultiplier = ""; //Clear temp value
                    isInFractionalNumber = false;
                }
                
                time = absoluteTimeBeats * beatTimeStep;
                
            } else if (c == ';') {
                
                long absoluteTimeBeats = 0;
                
                if (!fractionalMultiplier.isEmpty()) {
                    absoluteTimeBeats = parseIntAbsoluteTime(fractionalMultiplier);
                    fractionalMultiplier = ""; //Clear temp value
                    isInFractionalNumber = false;
                }
                
                time = (long)(absoluteTimeBeats * beatTimeStep * numerator);
                
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
            } else if (c == '>') {
                dynamic -= 16;
                if (dynamic < 15) {
                    dynamic = 15;
                }
            } else if (c == '<') {
                dynamic += 16;
                if (dynamic > 127) {
                    dynamic = 127;
                }
            } else if (c == '%') {
                dynamic = 48 - 1;
            }
            
        }
        
        
        
        
    }
    
    public static int getDt(float multiplier, float periodTimeMultiplier, int beatTimeStep) {
        
        return Math.max(Math.round(beatTimeStep * multiplier * periodTimeMultiplier), 2); //dt has to be at least 2

    }
    
    public static Map.Entry<Float, Float> parseTimeSignature(String timeSignature) {
        
        if (timeSignature.isEmpty()) {
            return new AbstractMap.SimpleEntry<>(4f, 4f);
        } else {
            String[] fractionParts = timeSignature.split("/");
            
            if (fractionParts.length == 1) {
                float numerator = parseNumber(fractionParts[0]);
                return new AbstractMap.SimpleEntry<>(numerator, 4f);
                
            } else if (fractionParts.length >= 2) {
                
                float numerator = parseNumber(fractionParts[0]);
                float denom = parseNumber(fractionParts[fractionParts.length - 1]);
                
                if (denom == 0) {
                    denom = 4f;
                }
                
                if (numerator == 0) {
                    numerator = 4f;
                }
                
                return new AbstractMap.SimpleEntry<>(numerator, denom);
                
            } else { //If fractionalDuration equals "/"
                return new AbstractMap.SimpleEntry<>(4f, 4f);
            }
        }
    }
    public static float parseStringMultiplier(String fractionalDuration) {
        
        if (fractionalDuration.isEmpty()) {
            return 1f;
        } else {
            String[] fractionParts = fractionalDuration.split("/");
            
            if (fractionParts.length == 1) {
                return parseNumber(fractionParts[0]);
                
            } else if (fractionParts.length >= 2) {
                
                float numerator = parseNumber(fractionParts[0]);
                float denom = parseNumber(fractionParts[fractionParts.length - 1]);
                
                if (denom == 0) {
                    denom = 1f;
                }
                
                if (numerator == 0) {
                    numerator = 1f/512f;
                }
                
                return numerator / denom;
                
            } else { //If fractionalDuration equals "/"
                return 0.5f;
            }
            
        }
    }
    private static long parseIntAbsoluteTime(String numberString) {
        try {
            return Long.parseLong(numberString);
        } catch (NumberFormatException ex) {
            return 0;
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
            Logger.getLogger(MidiEventListener.class.getName()).log(Level.SEVERE, null, ex);
            return new MidiSendHandler(midiSynth, null);
        }
        
    }

    private static class HashMapImpl extends HashMap<Character, Integer> {

        public HashMapImpl() {
        }
        {put('d', 0);}
    }
    
}
