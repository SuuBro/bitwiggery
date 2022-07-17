package com.suubro;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Scales
{
    public static String pitchToNoteName(int lowestDisplayedPitch)
    {
        int note = (lowestDisplayedPitch % 12);
        int octave = (lowestDisplayedPitch / 12) - 2;
        return NoteNames.get(note) + octave;
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
