package com.suubro;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Scales
{
    public static String IntervalToNoteName(int interval)
    {
        return NoteNames.get(interval % 12);
    }

    public static String PitchToNoteName(int pitch)
    {
        return IntervalToNoteName(pitch) + ((pitch / 12) - 2);
    }

    public static int FindNoteAtInterval(String scale, int baseNote, int position)
    {
        int[] intervals = ScaleIntervals.get(scale);
        int result = baseNote;
        for (int i = 0; i < position; i++)
        {
            int interval = intervals[position % intervals.length];
            result += interval;
        }
        return result;
    }

    private static final Map<String, int[]> ScaleIntervals;
    static {
        Map<String, int[]> builder = new HashMap<>();
        builder.put("chromatic", new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
        builder.put("blues", new int[] {3, 2, 1, 1, 3, 2});
        builder.put("dorian", new int[] {2, 1, 2, 2, 2, 1, 2});
        builder.put("major", new int[] {2, 2, 1, 2, 2, 2, 1});
        builder.put("minor", new int[] {2, 1, 2, 2, 1, 2, 2});
        builder.put("locrian", new int[] {1, 2, 2, 1, 2, 2, 2});
        builder.put("mixolydian", new int[] {2, 2, 1, 2, 2, 1, 2});
        builder.put("phrygian", new int[] {1, 2, 2, 2, 1, 2, 2});
        ScaleIntervals = Collections.unmodifiableMap(builder);
    }

    private static final Map<Integer, String> NoteNames;
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
        NoteNames = Collections.unmodifiableMap(builder);
    }
}
