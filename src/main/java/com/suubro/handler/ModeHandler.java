package com.suubro.handler;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.api.ControllerHost;
import com.suubro.D400Hardware;

public class ModeHandler
{
    public static final String []   MODE_OPTIONS =
    {
        "Track",
        "Device"
    };

    private final Mode []           modes;
    private final ControllerHost    host;

    private Mode                    activeMode;

    public ModeHandler (final Mode [] modes, final ControllerHost host)
    {
        this.modes = modes;
        this.host = host;

        this.setActiveMode (modes[0]);
    }

    public void setActiveMode (final Mode newMode)
    {
        this.activeMode = newMode;
        this.host.showPopupNotification (this.activeMode.getName ());
        this.updateIndication ();
    }

    public void updateIndication ()
    {
        for (final Mode mode: this.modes)
            mode.setIndication (false);
        this.activeMode.setIndication (true);
    }

    public boolean handleMidi (final ShortMidiMessage message)
    {
        if (message.isNoteOn ())
        {
            switch (message.getData1 ())
            {
                case D400Hardware.BTN_ASSIGN1:
                    this.setActiveMode (this.modes[0]);
                    return true;

                case D400Hardware.BTN_ASSIGN2:
                    this.setActiveMode (this.modes[1]);
                    return true;
            }
        }

        return this.activeMode.handleMidi(message);
    }
}