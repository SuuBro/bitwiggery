package com.suubro;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.callback.DoubleValueChangedCallback;
import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.controller.ControllerExtension;

import java.time.Instant;
import java.util.function.BooleanSupplier;

public class D400GridExtension extends ControllerExtension
{
   private ControllerHost _host;
   private MidiIn _midiIn;
   private MidiOut _midiOut;
   private HardwareSurface _hardwareSurface;
   private TrackBank _trackBank;
   private DeviceBank _deviceBank;
   private Application _application;
   private final long[] _vuMeterLastSend = new long[8];
   private int _selectedTrack = -1;

   protected D400GridExtension(final D400GridExtensionDefinition definition, final ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      _host = getHost();
      _application = _host.createApplication();
      _midiIn = _host.getMidiInPort(0);
      _midiIn.setMidiCallback(this::handleMidi);
      _midiOut = _host.getMidiOutPort(0);
      Transport transport = _host.createTransport();

      _hardwareSurface = _host.createHardwareSurface();

      createButtonWithLight("PLAY", D400.BTN_PLAY,
              transport.playAction(), transport.isPlaying());
      createButtonWithLight("STOP", D400.BTN_STOP,
              transport.stopAction(), transport.isPlaying(), () -> !transport.isPlaying().get());
      createButtonWithLight("RECORD", D400.BTN_REC,
              transport.recordAction(), transport.isArrangerRecordEnabled());
      createButtonWithLight("LOOP", D400.BTN_LOOP,
              transport.isArrangerLoopEnabled().toggleAction(), transport.isArrangerLoopEnabled());
      createButtonWithLight("METRONOME", D400.BTN_METRONOME,
              transport.isMetronomeEnabled().toggleAction(), transport.isMetronomeEnabled());

      final RelativeHardwareKnob jogWheel = _hardwareSurface.createRelativeHardwareKnob("JOG_WHEEL");
      final RelativeHardwareValueMatcher stepDownMatcher = _midiIn.createRelativeValueMatcher(
              "(status == 144 && data1 == " + D400.JOG_WHEEL + " && data2 > 64)", -0.25);
      final RelativeHardwareValueMatcher stepUpMatcher = _midiIn.createRelativeValueMatcher(
              "(status == 144 && data1 == " + D400.JOG_WHEEL + " && data2 < 65)", 0.25);
      final RelativeHardwareValueMatcher relativeMatcher = _host.createOrRelativeHardwareValueMatcher(stepDownMatcher, stepUpMatcher);
      jogWheel.setAdjustValueMatcher(relativeMatcher);
      transport.playStartPosition().addBinding(jogWheel);

      CursorTrack _cursorTrack = _host.createCursorTrack("D400_CURSOR_TRACK", "Cursor Track", 0, 0, true);
      _trackBank = _host.createMainTrackBank (8, 0, 0);
      _trackBank.followCursorTrack (_cursorTrack);
      _deviceBank = _cursorTrack.createDeviceBank(8);

      for (int i = 0; i < _trackBank.getSizeOfBank (); i++)
      {
         final int channel = i;
         final Track track = _trackBank.getItemAt(i);
         track.volume().markInterested();

         setUpFader(_trackBank, channel, track);

         _vuMeterLastSend[channel] = Instant.now().toEpochMilli();

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

   private void setUpFader(TrackBank trackBank, int channel, Track track)
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
            if(!seenInitZero) // This seems to be called initially with value==0, which resets the sliders...
            {
               seenInitZero = true;
               return;
            }
            if (!fader.isUpdatingTargetValue().get())
            {
               final int faderValue = Math.max(0, Math.min(16383, (int)(value * 16384.0)));

               if (_lastSentValue != faderValue)
               {
                  _midiOut.sendMidi(0xE0 | channel, faderValue & 0x7f, faderValue >> 7);
                  _lastSentValue = faderValue;
               }
            }
         }

         private int _lastSentValue = -1;
         private boolean seenInitZero = false;
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
      button.pressedAction().setActionMatcher(_midiIn.createNoteOnActionMatcher(D400.CHANNEL, midi));
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

      if(statusByte == Midi.NOTE_ON && data2 > 0)
      {
         switch (data1){
            case D400.BTN_SELECT_1: selectTrack(0); break;
            case D400.BTN_SELECT_2: selectTrack(1); break;
            case D400.BTN_SELECT_3: selectTrack(2); break;
            case D400.BTN_SELECT_4: selectTrack(3); break;
            case D400.BTN_SELECT_5: selectTrack(4); break;
            case D400.BTN_SELECT_6: selectTrack(5); break;
            case D400.BTN_SELECT_7: selectTrack(6); break;
            case D400.BTN_SELECT_8: selectTrack(7); break;
            case D400.BUTTON_1: selectFx(0); break;
            case D400.BUTTON_2: selectFx(1); break;
            case D400.BUTTON_3: selectFx(2); break;
            case D400.BUTTON_4: selectFx(3); break;
            case D400.BUTTON_5: selectFx(4); break;
            case D400.BUTTON_6: selectFx(5); break;
            case D400.BUTTON_7: selectFx(6); break;
            case D400.BUTTON_8: selectFx(7); break;
         }
      }
   }

   private void selectTrack(int i)
   {
      for (int f = 0; f < 8; f++)
      {
         Device device = _deviceBank.getDevice(f);
         device.isWindowOpen().set(false);
      }
      _trackBank.getItemAt(i).selectInEditor();
      for (int t = 0; t < _trackBank.getSizeOfBank(); t++) {
         _midiOut.sendMidi(Midi.NOTE_ON, D400.BTN_SELECT_1+t, t == i ? 127 : 0);
      }
      _selectedTrack = i;
   }

   private void selectFx(int i)
   {
      for (int f = 0; f < 8; f++)
      {
         Device device = _deviceBank.getDevice(f);
         if(f != i)
         {
            device.isWindowOpen().set(false);
         }
      }
      _deviceBank.scrollIntoView(i);
      Device device = _deviceBank.getDevice(i);
      device.selectInEditor();
      device.isWindowOpen().toggle();
      _application.toggleNoteEditor();
      _application.toggleDevices();
   }
}
