package com.suubro.handler;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.suubro.D400Hardware;

public class TrackHandler implements Mode
{
    private final TrackBank _trackBank;
    private final CursorTrack _cursorTrack;
    private final ControllerHost _host;
    private final D400Hardware _hardware;

    public TrackHandler (final ControllerHost host, final TrackBank trackbank, final CursorTrack cursorTrack, D400Hardware hardware)
    {
        _host = host;
        _trackBank = trackbank;
        _cursorTrack = cursorTrack;
        _hardware = hardware;

        this._trackBank.followCursorTrack (_cursorTrack);

        for (int i = 0; i < this._trackBank.getSizeOfBank (); i++)
        {
            final Track track = this._trackBank.getItemAt (i);
            track.volume ().markInterested ();
        }

        _cursorTrack.solo ().markInterested ();
        _cursorTrack.mute ().markInterested ();
    }

    @Override
    public String getName ()
    {
        return "Track Mode";
    }

    @Override
    public void setIndication (final boolean enable)
    {
        for (int i = 0; i < this._trackBank.getSizeOfBank(); i++)
        {
            final Track track = this._trackBank.getItemAt(i);
            track.volume().setIndication(enable);
        }
    }

    @Override
    public boolean handleMidi (final ShortMidiMessage message)
    {
        if(message.isPitchBend())
        {
            final Track track = this._trackBank.getItemAt(message.getChannel());
            final Integer value = (message.getData2() * 128) + message.getData1();
            _host.println(value.toString());
            track.volume().set(value, 128*128);
            return true;
        }

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