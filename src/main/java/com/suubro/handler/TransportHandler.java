package com.suubro.handler;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.api.Transport;
import com.suubro.D400Hardware;

public class TransportHandler
{
    private final Transport    transport;
    private final D400Hardware hardware;

    public TransportHandler (final Transport transport, final D400Hardware hardware)
    {
        this.transport = transport;
        this.hardware = hardware;

        this.transport.isPlaying().markInterested();
        this.transport.isArrangerRecordEnabled().markInterested();
        this.transport.isArrangerLoopEnabled().markInterested();
        this.transport.isMetronomeEnabled().markInterested();
    }

    public boolean handleMidi (final ShortMidiMessage message)
    {
        if (!message.isNoteOn())
            return false;

        if (message.getData2() == 0)
            return true;

        switch (message.getData1())
        {
            case D400Hardware.JOG_WHEEL:
                final int data2 = message.getData2 ();
                final double increment = (data2 > 64 ? -1.0 * (128 - data2) : data2) / 4.0;
                this.transport.incPosition(increment, false);
                return true;

            case D400Hardware.BTN_PLAY:
                this.transport.play();
                return true;

            case D400Hardware.BTN_STOP:
                this.transport.stop();
                return true;

            case D400Hardware.BTN_REC:
                this.transport.record();
                return true;

            case D400Hardware.BTN_FWD:
                this.transport.fastForward();
                return true;

            case D400Hardware.BTN_BACK:
                this.transport.rewind();
                return true;

            case D400Hardware.BTN_LOOP:
                this.transport.isArrangerLoopEnabled().toggle();
                return true;

            case D400Hardware.BTN_METRONOME:
                this.transport.isMetronomeEnabled().toggle();
                return true;

            default:
                return false;
        }
    }

    public void updateLEDs ()
    {
        this.hardware.updateLED (D400Hardware.BTN_STOP, !this.transport.isPlaying().get());
        this.hardware.updateLED (D400Hardware.BTN_PLAY, this.transport.isPlaying().get());
        this.hardware.updateLED (D400Hardware.BTN_REC, this.transport.isArrangerRecordEnabled().get());
        this.hardware.updateLED (D400Hardware.BTN_LOOP, this.transport.isArrangerLoopEnabled().get());
        this.hardware.updateLED (D400Hardware.BTN_METRONOME, this.transport.isMetronomeEnabled().get());
    }
}