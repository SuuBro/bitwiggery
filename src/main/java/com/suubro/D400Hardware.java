package com.suubro;

import com.bitwig.extension.callback.ShortMidiDataReceivedCallback;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;

import java.util.Arrays;

public class D400Hardware
{
    public static final int JOG_WHEEL       = 118;
    public static final int SHUTTLE         = 121;

    public static final int BTN_UP          = 46;
    public static final int BTN_DOWN        = 47;
    public static final int BTN_STAR        = 52;
    public static final int BTN_SHIFT       = 70;
    public static final int BTN_LOOP        = 86;
    public static final int BTN_METRONOME   = 89;
    public static final int BTN_BACK        = 91;
    public static final int BTN_FWD         = 92;
    public static final int BTN_STOP        = 93;
    public static final int BTN_PLAY        = 94;
    public static final int BTN_REC         = 95;
    public static final int BTN_ASSIGN1     = 116;
    public static final int BTN_ASSIGN2     = 117;

    public static final int EQ_KNOB_BTN_1   = 64;
    public static final int EQ_KNOB_1       = 96;
    public static final int EQ_KNOB_BTN_2   = 65;
    public static final int EQ_KNOB_2       = 98;
    public static final int EQ_KNOB_BTN_3   = 66;
    public static final int EQ_KNOB_3       = 100;
    public static final int EQ_KNOB_BTN_4   = 67;
    public static final int EQ_KNOB_4       = 102;

    public static final int BUTTON_1        = 120;
    public static final int BUTTON_2        = 121;
    public static final int BUTTON_3        = 122;
    public static final int BUTTON_4        = 123;
    public static final int BUTTON_5        = 124;
    public static final int BUTTON_6        = 125;
    public static final int BUTTON_7        = 126;
    public static final int BUTTON_8        = 127;

    public static final int KNOB_BTN_1      = 44;
    public static final int KNOB_1          = 112;
    public static final int KNOB_BTN_2      = 56;
    public static final int KNOB_2          = 80;
    public static final int KNOB_BTN_3      = 45;
    public static final int KNOB_3          = 114;
    public static final int KNOB_BTN_4      = 60;
    public static final int KNOB_4          = 34;

    private final MidiOut         portOut;
    private final int []          ledCache             = new int [128];


    public D400Hardware(final MidiOut outputPort, final MidiIn inputPort, final ShortMidiDataReceivedCallback inputCallback)
    {
        this.portOut = outputPort;

        Arrays.fill (this.ledCache, -1);

        inputPort.setMidiCallback (inputCallback);
    }

    public void updateLED (final int note, final boolean isOn)
    {
        final int value = isOn ? 127 : 0;
        if (this.ledCache[note] == value)
            return;
        this.ledCache[note] = value;
        this.portOut.sendMidi (0x90, note, value);
    }
}