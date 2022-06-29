package com.suubro;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.controller.ControllerExtension;

import java.time.Instant;
import java.util.function.BooleanSupplier;

public class D400GridExtension extends ControllerExtension
{
   private static final int NUM_PARAMS_IN_PAGE = 8;

   private ControllerHost _host;
   private MidiIn _midiIn;
   private MidiOut _midiOut;
   private HardwareSurface _hardwareSurface;
   private TrackBank _trackBank;
   private DeviceBank _deviceBank;
   private final CursorRemoteControlsPage[] _parameterBanks = new CursorRemoteControlsPage[8];
   private Application _application;

   private final Fader[] faders = new Fader[8];
   private final long[] _vuMeterLastSend = new long[8];

   private int _selectedDevice = -1;

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
      for (int i = 0; i < _deviceBank.getSizeOfBank (); i++)
      {
         _parameterBanks[i] = _deviceBank.getDevice(i).createCursorRemoteControlsPage(8);
      }

      for (int i = 0; i < _trackBank.getSizeOfBank (); i++)
      {
         final int channel = i;
         final Track track = _trackBank.getItemAt(i);
         track.volume().markInterested();

         Fader fader = setUpFader(channel);
         fader.setBinding(_trackBank.getItemAt(channel).volume());

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

   private Fader setUpFader(int channel)
   {
      HardwareSlider slider = _hardwareSurface.createHardwareSlider ("SLIDER_" + channel);
      slider.setAdjustValueMatcher(_midiIn.createAbsolutePitchBendValueMatcher(channel));

      slider.beginTouchAction().setActionMatcher(
              _midiIn.createActionMatcher("status == 0x90 && data1 == " + (0x68 + channel) + " && data2 > 0"));
      slider.endTouchAction().setActionMatcher(
              _midiIn.createActionMatcher("status == 0x90 && data1 == " + (0x68 + channel) + " && data2 == 0"));
      slider.disableTakeOver();

      slider.isBeingTouched().markInterested();
      slider.targetValue().markInterested();
      slider.isUpdatingTargetValue().markInterested();
      slider.hasTargetValue().markInterested();

      final Fader fader = new Fader(_host, _midiOut, channel, slider);
      slider.targetValue().addValueObserver(fader);
      faders[channel] = fader;
      return fader;
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
   }

   public void handleMidi (final int statusByte, final int data1, final int data2)
   {
      final ShortMidiMessage msg = new ShortMidiMessage (statusByte, data1, data2);
      _host.println(msg.toString());

      if(statusByte == Midi.NOTE_ON)
      {
         if (data1 == D400.SHUTTLE)
         {
            handleShuttle(data2);
         }
         else if(data2 > 0)
         {
            switch (data1)
            {
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
   }

   private boolean _shuttleSettled = true;
   private void handleShuttle(int amount)
   {
      _host.println("_selectedDevice: " + _selectedDevice);
      if(amount == 0)
      {
         _host.println("Settled");
         _shuttleSettled = true;
         return;
      }
      if(_selectedDevice < 0 || !_shuttleSettled)
      {
         _host.println("Skip");
         return;
      }

      if(amount == 8)
      {
         _host.println("Next");
         _parameterBanks[_selectedDevice].selectNextPage(true);
      }
      else if( amount == 120)
      {
         _host.println("Previous");
         _parameterBanks[_selectedDevice].selectPreviousPage(true);
      }
      _shuttleSettled = false;
   }

   private void selectTrack(int i)
   {
      clearFx();
      _application.toggleDevices();
      _application.toggleNoteEditor();
      for (int t = 0; t < _trackBank.getSizeOfBank(); t++) {
         Track track = _trackBank.getItemAt(t);
         if(t == i)
         {
            track.selectInEditor();
         }
         _midiOut.sendMidi(Midi.NOTE_ON, D400.BTN_SELECT_1+t, t == i ? 127 : 0);
         faders[t].setBinding(track.volume());
      }
   }

   private void clearFx()
   {
      clearFx(-1);
   }

   private void clearFx(int exceptThisChannel)
   {
      for (int f = 0; f < _trackBank.getSizeOfBank(); f++)
      {
         Device device = _deviceBank.getDevice(f);
         if(f != exceptThisChannel)
         {
            device.isWindowOpen().set(false);
            device.isRemoteControlsSectionVisible().set(false);
         }
         _midiOut.sendMidi(Midi.NOTE_ON, D400.BUTTON_1+f, f == exceptThisChannel ? 127 : 0);
      }
   }

   private void selectFx(int i)
   {
      clearFx(i);
      _deviceBank.scrollIntoView(i);
      Device device = _deviceBank.getDevice(i);
      device.selectInEditor();
      device.isWindowOpen().toggle();
      device.isRemoteControlsSectionVisible().set(true);
      _application.toggleNoteEditor();
      _application.toggleDevices();

      for (int t = 0; t < NUM_PARAMS_IN_PAGE; t++)
      {
         Parameter parameter = _parameterBanks[i].getParameter(t);
         faders[t].setBinding(parameter);
      }
      _selectedDevice = i;
   }
}
