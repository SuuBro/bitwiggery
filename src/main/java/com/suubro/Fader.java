package com.suubro;

import com.bitwig.extension.callback.DoubleValueChangedCallback;
import com.bitwig.extension.controller.api.*;

public class Fader implements DoubleValueChangedCallback {

    private final ControllerHost _host;
    private final MidiOut _midiOut;
    private final int _channel;
    private final HardwareSlider _fader;
    private int _lastSentValue = -1;
    private boolean _seenInitZero = false;

    public Fader(ControllerHost host, MidiOut midiOut, int channel, HardwareSlider fader)
    {
        _host = host;
        _midiOut = midiOut;
        _channel = channel;
        _fader = fader;
    }

    @Override
    public void valueChanged(final double value)
    {
        if(!_seenInitZero) // This seems to be called initially with value==0, which resets the sliders...
        {
            _seenInitZero = true;
            return;
        }
        if (!_fader.isUpdatingTargetValue().get())
        {
            final int faderValue = Math.max(0, Math.min(16383, (int)(value * 16384.0)));

            if (_lastSentValue != faderValue)
            {
                _host.println("Setting fader " + _channel + " to " + faderValue);
                _midiOut.sendMidi(0xE0 | _channel, faderValue & 0x7f, faderValue >> 7);
                _lastSentValue = faderValue;
            }
        }
    }

    public void setBinding(HardwareBindable bindable)
    {
        _fader.setBinding(bindable);
        _seenInitZero = false;
    }
}
