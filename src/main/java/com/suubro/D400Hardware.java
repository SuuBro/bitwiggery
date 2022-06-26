package com.suubro;

import com.bitwig.extension.callback.ShortMidiDataReceivedCallback;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;

import java.util.Arrays;

public class D400Hardware
{
    public static final int JOG_WHEEL       = 118;
    public static final int SHUTTLE         = 119;

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

    public static final int TOUCH_FADER_1   = 104;
    public static final int TOUCH_FADER_2   = 105;
    public static final int TOUCH_FADER_3   = 106;
    public static final int TOUCH_FADER_4   = 107;
    public static final int TOUCH_FADER_5   = 108;
    public static final int TOUCH_FADER_6   = 109;
    public static final int TOUCH_FADER_7   = 110;
    public static final int TOUCH_FADER_8   = 111;

    public static final int BTN_RECORD_1    = 0;
    public static final int BTN_RECORD_2    = 1;
    public static final int BTN_RECORD_3    = 2;
    public static final int BTN_RECORD_4    = 3;
    public static final int BTN_RECORD_5    = 4;
    public static final int BTN_RECORD_6    = 5;
    public static final int BTN_RECORD_7    = 6;
    public static final int BTN_RECORD_8    = 7;
    public static final int BTN_SOLO_1      = 8;
    public static final int BTN_SOLO_2      = 9;
    public static final int BTN_SOLO_3      = 10;
    public static final int BTN_SOLO_4      = 11;
    public static final int BTN_SOLO_5      = 12;
    public static final int BTN_SOLO_6      = 13;
    public static final int BTN_SOLO_7      = 14;
    public static final int BTN_SOLO_8      = 15;
    public static final int BTN_MUTE_1      = 16;
    public static final int BTN_MUTE_2      = 17;
    public static final int BTN_MUTE_3      = 18;
    public static final int BTN_MUTE_4      = 19;
    public static final int BTN_MUTE_5      = 20;
    public static final int BTN_MUTE_6      = 21;
    public static final int BTN_MUTE_7      = 22;
    public static final int BTN_MUTE_8      = 23;
    public static final int BTN_SELECT_1    = 24;
    public static final int BTN_SELECT_2    = 25;
    public static final int BTN_SELECT_3    = 26;
    public static final int BTN_SELECT_4    = 27;
    public static final int BTN_SELECT_5    = 28;
    public static final int BTN_SELECT_6    = 29;
    public static final int BTN_SELECT_7    = 30;
    public static final int BTN_SELECT_8    = 31;

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