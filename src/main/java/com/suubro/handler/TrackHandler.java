package com.suubro.handler;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;

public class TrackHandler implements Mode
{
    private final TrackBank   trackbank;
    private final CursorTrack cursorTrack;

    public TrackHandler (final TrackBank trackbank, final CursorTrack cursorTrack)
    {
        this.trackbank = trackbank;
        this.cursorTrack = cursorTrack;

        this.trackbank.followCursorTrack (this.cursorTrack);

        for (int i = 0; i < this.trackbank.getSizeOfBank (); i++)
        {
            final Track track = this.trackbank.getItemAt (i);
            track.pan ().markInterested ();
            track.volume ().markInterested ();
        }

        this.cursorTrack.solo ().markInterested ();
        this.cursorTrack.mute ().markInterested ();
    }

    @Override
    public String getName ()
    {
        return "Track Mode";
    }

    @Override
    public void setIndication (final boolean enable)
    {
        for (int i = 0; i < this.trackbank.getSizeOfBank (); i++)
        {
            final Track track = this.trackbank.getItemAt (i);
            track.pan ().setIndication (enable);
            track.volume ().setIndication (enable);
        }
    }

    @Override
    public boolean handleMidi (final ShortMidiMessage message)
    {
        if (message.isNoteOn ())
        {
        }

        if (message.isControlChange ())
        {
            final int data2 = message.getData2 ();
            final Integer value = data2 > 64 ? 64 - data2 : data2;
        }

        return false;
    }
}