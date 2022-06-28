package com.suubro;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.api.graphics.Bitmap;
import com.bitwig.extension.api.graphics.BitmapFormat;
import com.bitwig.extension.api.graphics.GraphicsOutput;
import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.callback.DoubleValueChangedCallback;
import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.controller.ControllerExtension;
import com.suubro.handler.*;

import java.time.Instant;
import java.util.Date;
import java.util.function.BooleanSupplier;

public class D400GridExtension extends ControllerExtension
{
   private ControllerHost _host;
   private MidiIn _midiIn;
   private MidiOut _midiOut;
   private HardwareSurface _hardwareSurface;
   private ModeHandler _mode;
   private final long[] _vuMeterLastSend = new long[8];
   private final long[] _faderLastSend = new long[8];

   protected D400GridExtension(final D400GridExtensionDefinition definition, final ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      _host = getHost();
      _midiIn = _host.getMidiInPort(0);
      _midiIn.setMidiCallback(this::handleMidi);
      _midiOut = _host.getMidiOutPort(0);
      Transport transport = _host.createTransport();

      _hardwareSurface = _host.createHardwareSurface();

      createButtonWithLight("PLAY", D400Hardware.BTN_PLAY,
              transport.playAction(), transport.isPlaying());
      createButtonWithLight("STOP", D400Hardware.BTN_STOP,
              transport.stopAction(), transport.isPlaying(), () -> !transport.isPlaying().get());
      createButtonWithLight("RECORD", D400Hardware.BTN_REC,
              transport.recordAction(), transport.isArrangerRecordEnabled());
      createButtonWithLight("LOOP", D400Hardware.BTN_LOOP,
              transport.isArrangerLoopEnabled().toggleAction(), transport.isArrangerLoopEnabled());
      createButtonWithLight("METRONOME", D400Hardware.BTN_METRONOME,
              transport.isMetronomeEnabled().toggleAction(), transport.isMetronomeEnabled());

      final RelativeHardwareKnob jogWheel = _hardwareSurface.createRelativeHardwareKnob("JOG_WHEEL");
      final RelativeHardwareValueMatcher stepDownMatcher = _midiIn.createRelativeValueMatcher(
              "(status == 144 && data1 == " + D400Hardware.JOG_WHEEL + " && data2 > 64)", -0.25);
      final RelativeHardwareValueMatcher stepUpMatcher = _midiIn.createRelativeValueMatcher(
              "(status == 144 && data1 == " + D400Hardware.JOG_WHEEL + " && data2 < 65)", 0.25);
      final RelativeHardwareValueMatcher relativeMatcher = _host.createOrRelativeHardwareValueMatcher(stepDownMatcher, stepUpMatcher);
      jogWheel.setAdjustValueMatcher(relativeMatcher);
      transport.playStartPosition().addBinding(jogWheel);

      final CursorTrack cursorTrack = _host.createCursorTrack("D400_CURSOR_TRACK", "Cursor Track", 0, 0, true);
      final TrackBank trackBank = _host.createMainTrackBank (8, 0, 0);
      trackBank.followCursorTrack (cursorTrack);

      for (int i = 0; i < trackBank.getSizeOfBank (); i++)
      {
         final int channel = i;
         setUpFader(trackBank, channel);

         _vuMeterLastSend[channel] = Instant.now().toEpochMilli();
         final Track track = trackBank.getItemAt(i);
         track.addVuMeterObserver(14, -1, true, level -> {
            long now = Instant.now().toEpochMilli();
            if (_vuMeterLastSend[channel] < now - 250)
            {
               _midiOut.sendMidi(Midi.CHANNEL_PRESSURE, level + (channel << 4), 0);
               _vuMeterLastSend[channel] = now;
            }
         } );
      }

      _host.showPopupNotification("D400Grid Initialized");
   }

   private void setUpFader(TrackBank trackBank, int channel)
   {
      final HardwareSlider fader = _hardwareSurface.createHardwareSlider ("SLIDER_" + channel);
      fader.setAdjustValueMatcher(_midiIn.createAbsolutePitchBendValueMatcher(channel));
      fader.setBinding(trackBank.getItemAt(channel).volume());
      fader.beginTouchAction().setActionMatcher(
              _midiIn.createActionMatcher("status == 0x90 && data1 == " + (0x68 + channel) + " && data2 > 0"));
      fader.endTouchAction().setActionMatcher(
              _midiIn.createActionMatcher("status == 0x90 && data1 == " + (0x68 + channel) + " && data2 == 0"));
      fader.disableTakeOver();

      fader.isBeingTouched().markInterested();
      fader.targetValue().markInterested();
      fader.isUpdatingTargetValue().markInterested();
      fader.hasTargetValue().markInterested();

      final DoubleValueChangedCallback moveFader = new DoubleValueChangedCallback()
      {
         @Override
         public void valueChanged(final double value)
         {
            if (!fader.isUpdatingTargetValue().get())
            {
               final int faderValue = Math.max(0, Math.min(16383, (int)(value * 16384.0)));

               if (_lastSentValue != value)
               {
                  _midiOut.sendMidi(0xE0 | channel, faderValue & 0x7f, faderValue >> 7);
                  _lastSentValue = faderValue;
               }
            }
         }

         private int _lastSentValue = -1;
      };
      fader.targetValue().addValueObserver(moveFader);
   }

   public void createButtonWithLight(final String name, final int midi, HardwareBindable binding,
                                     SettableBooleanValue value)
   {
      createButtonWithLight(name, midi, binding, value, value::get);
   }

   public void createButtonWithLight(final String name, final int midi, HardwareBindable binding,
                                     final SettableBooleanValue value, final BooleanSupplier supplier)
   {
      final HardwareButton button = _hardwareSurface.createHardwareButton (name + "_BUTTON");
      button.pressedAction().setActionMatcher(_midiIn.createNoteOnActionMatcher(D400Hardware.CHANNEL, midi));
      button.pressedAction().setBinding(binding);
      final OnOffHardwareLight light = _hardwareSurface.createOnOffHardwareLight(name + "LIGHT");
      value.markInterested();
      light.isOn().setValueSupplier(supplier);
      light.isOn().onUpdateHardware(isOn -> _midiOut.sendMidi(Midi.NOTE_ON, midi, isOn ? 127 : 0));
      button.setBackgroundLight(light);
   }

   @Override
   public void exit()
   {
            getHost().showPopupNotification("D400Grid Exited");
   }

   @Override
   public void flush()
   {
      _hardwareSurface.updateHardware();
      //_transport.updateLEDs();
   }

   public void handleMidi (final int statusByte, final int data1, final int data2)
   {
      final ShortMidiMessage msg = new ShortMidiMessage (statusByte, data1, data2);
      _host.println(msg.toString());
   }
}
