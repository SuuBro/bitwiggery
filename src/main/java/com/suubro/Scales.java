package com.suubro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Scales
{
    public static int[] GeneratePitches(int scaleRoot, String scale)
    {
        int[] intervals = ScaleIntervals.get(scale);
        int lastNote = scaleRoot;
        int intervalIndex = 0;
        ArrayList<Integer> result = new ArrayList<>();
        while (lastNote < 127)
        {
            result.add(lastNote);
            lastNote += intervals[intervalIndex];
            intervalIndex = (intervalIndex + 1) % intervals.length;
        }
        return result.stream().mapToInt(i -> i).toArray();
    }

    public static String IntervalToNoteName(int interval)
    {
        return NoteIndexToName.get(interval % 12);
    }

    public static String PitchToNoteName(int pitch)
    {
        return IntervalToNoteName(pitch) + ((pitch / 12) - 2);
    }

    private static final Map<String, int[]> ScaleIntervals;
    static {
        Map<String, int[]> builder = new HashMap<>();
        builder.put("Chromatic", new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
        builder.put("Blues", new int[] {3, 2, 1, 1, 3, 2});
        builder.put("Dorian", new int[] {2, 1, 2, 2, 2, 1, 2});
        builder.put("Major", new int[] {2, 2, 1, 2, 2, 2, 1});
        builder.put("Minor", new int[] {2, 1, 2, 2, 1, 2, 2});
        builder.put("Locrian", new int[] {1, 2, 2, 1, 2, 2, 2});
        builder.put("Mixolydian", new int[] {2, 2, 1, 2, 2, 1, 2});
        builder.put("Phrygian", new int[] {1, 2, 2, 2, 1, 2, 2});
        ScaleIntervals = Collections.unmodifiableMap(builder);
    }

    public static final Map<Integer, String> NoteIndexToName;
    static {
        Map<Integer, String> builder = new HashMap<>();
        builder.put(0, "C");
        builder.put(1, "C#");
        builder.put(2, "D");
        builder.put(3, "D#");
        builder.put(4, "E");
        builder.put(5, "F");
        builder.put(6, "F#");
        builder.put(7, "G");
        builder.put(8, "G#");
        builder.put(9, "A");
        builder.put(10, "A#");
        builder.put(11, "B");
        NoteIndexToName = Collections.unmodifiableMap(builder);
    }

    public static final Map<String, Integer> NoteNameToIndex;
    static {
        Map<String, Integer> builder = new HashMap<>();
        NoteIndexToName.forEach((key, value) -> builder.put(value, key));
        NoteNameToIndex = Collections.unmodifiableMap(builder);
    }
}
