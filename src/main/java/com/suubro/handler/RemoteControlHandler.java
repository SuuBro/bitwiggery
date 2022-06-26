package com.suubro.handler;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;

public class RemoteControlHandler implements Mode
{
    private final CursorRemoteControlsPage remoteControlsBank;

    public RemoteControlHandler (final CursorDevice cursorDevice, final CursorRemoteControlsPage remoteControlsBank)
    {
        this.remoteControlsBank = remoteControlsBank;

        for (int i = 0; i < this.remoteControlsBank.getParameterCount (); i++)
            this.remoteControlsBank.getParameter (i).markInterested ();

        cursorDevice.isEnabled ().markInterested ();
        cursorDevice.isWindowOpen ().markInterested ();
    }

    @Override
    public String getName ()
    {
        return "Device Mode";
    }


    /** {@inheritDoc} */
    @Override
    public void setIndication (final boolean enable)
    {
        for (int i = 0; i < this.remoteControlsBank.getParameterCount (); i++)
            this.remoteControlsBank.getParameter (i).setIndication (enable);
    }


    /** {@inheritDoc} */
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