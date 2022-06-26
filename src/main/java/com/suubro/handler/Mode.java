package com.suubro.handler;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;

public interface Mode
{
    String getName ();

    void setIndication (boolean enable);

    boolean handleMidi (ShortMidiMessage message);
}
