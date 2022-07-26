package com.suubro;

import com.bitwig.extension.controller.api.*;

public class Fader
{
    private static final String TouchActionPrefix = "status == 0x90 && data1 == ";

    private ControllerHost _host;
    private final MidiOut _midiOut;
    private final int _channel;
    private final HardwareSlider _slider;

    public Fader(ControllerHost host, MidiIn midiIn, MidiOut midiOut, int channel, HardwareSurface surface)
    {
        _host = host;
        _midiOut = midiOut;
        _channel = channel;
        HardwareSlider slider = surface.createHardwareSlider ("SLIDER_" + channel);
        slider.setAdjustValueMatcher(midiIn.createAbsolutePitchBendValueMatcher(channel));
        slider.hasTargetValue().markInterested();
        slider.isUpdatingTargetValue().markInterested();
        slider.value().addValueObserver(this::valueChanged);
        slider.targetValue().addValueObserver(this::targetValueChanged);

        slider.beginTouchAction().setActionMatcher(
                midiIn.createActionMatcher(TouchActionPrefix + (0x68 + channel) + " && data2 > 0")
        );
        slider.endTouchAction().setActionMatcher(
                midiIn.createActionMatcher(TouchActionPrefix + (0x68 + channel) + " && data2 == 0")
        );
        slider.disableTakeOver();

        _slider = slider;
    }

    public void valueChanged(final double value)
    {
        SendMidiToHardware(value);
    }

    public void targetValueChanged(final double value)
    {
        if(_slider.hasTargetValue().get())
        {
            SendMidiToHardware(value);
        }
    }

    private void SendMidiToHardware(double value) {
        final int faderValue = Math.max(0, Math.min(16383, (int)(value * 16384.0)));
        _midiOut.sendMidi(0xE0 | _channel, faderValue & 0x7f, faderValue >> 7);
    }

    public void setBinding(Parameter bindable)
    {
        _slider.setBinding(bindable);
        SendMidiToHardware(bindable.get());
    }
}
