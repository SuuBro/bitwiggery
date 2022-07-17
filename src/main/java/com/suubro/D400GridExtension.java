package com.suubro;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;

import java.time.Instant;
import java.util.function.BooleanSupplier;

public class D400GridExtension extends ControllerExtension
{
   private static final int NUM_PARAMS_IN_PAGE = 8;

   private Grid _grid;

   private ControllerHost _host;
   private MidiIn _midiIn;
   private MidiOut _midiOut;
   private HardwareSurface _hardwareSurface;
   private Transport _transport;
   private TrackBank _trackBank;
   private CursorTrack _cursorTrack;
   private PinnableCursorClip _clip;
   private DeviceBank _deviceBank;
   private SceneBank _sceneBank;
   private final CursorRemoteControlsPage[] _parameterBanks = new CursorRemoteControlsPage[8];
   private Application _application;

   private final Fader[] faders = new Fader[8];
   private final long[] _vuMeterLastSend = new long[8];

   private boolean _deviceMode = false;
   private int _selectedDevice = -1;

   protected D400GridExtension(final D400GridExtensionDefinition definition, final ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init() {
      _host = getHost();
      _application = _host.createApplication();

      _midiIn = _host.getMidiInPort(0);
      _midiIn.setMidiCallback(this::handleMidi);
      _midiOut = _host.getMidiOutPort(0);

      _cursorTrack = _host.createCursorTrack("D400_CURSOR_TRACK", "Cursor Track", 0, 0, true);
      _clip = _cursorTrack.createLauncherCursorClip(Grid.VIRTUAL_WIDTH, Grid.VIRTUAL_HEIGHT);
      _clip.addNoteStepObserver(this::onNoteStepChanged);
      _clip.clipLauncherSlot().sceneIndex().markInterested();
      _clip.playingStep().addValueObserver(stepIndex -> _grid.UpdatePlayingStep(stepIndex));

      _grid = new Grid(_host, _clip);

      _transport = _host.createTransport();
      _hardwareSurface = _host.createHardwareSurface();

      createButtonWithLight("PLAY", D400.BTN_PLAY,
              _transport.playAction(), _transport.isPlaying());
      createButtonWithLight("STOP", D400.BTN_STOP,
              _transport.stopAction(), _transport.isPlaying(), () -> !_transport.isPlaying().get());
      createButtonWithLight("RECORD", D400.BTN_REC,
              _transport.recordAction(), _transport.isArrangerRecordEnabled());
      createButtonWithLight("LOOP", D400.BTN_LOOP,
              _transport.isArrangerLoopEnabled().toggleAction(), _transport.isArrangerLoopEnabled());
      createButtonWithLight("METRONOME", D400.BTN_METRONOME,
              _transport.isMetronomeEnabled().toggleAction(), _transport.isMetronomeEnabled());

      _trackBank = _host.createMainTrackBank(8, 0, 0);
      _trackBank.followCursorTrack(_cursorTrack);
      _deviceBank = _cursorTrack.createDeviceBank(8);
      _sceneBank = _host.createSceneBank(8);

      for (int i = 0; i < _deviceBank.getSizeOfBank(); i++) {
         _parameterBanks[i] = _deviceBank.getDevice(i).createCursorRemoteControlsPage(8);
         for (int p = 0; p < NUM_PARAMS_IN_PAGE; p++) {
            _parameterBanks[i].getParameter(p).name().addValueObserver(this::buildDisplay);
            _parameterBanks[i].getParameter(p).displayedValue().addValueObserver(this::buildDisplay);
         }
      }

      for (int i = 0; i < _trackBank.getSizeOfBank(); i++) {
         final int channel = i;
         final Track track = _trackBank.getItemAt(i);
         track.volume().markInterested();
         track.name().markInterested();

         Fader fader = setUpFader(channel);
         fader.setBinding(_trackBank.getItemAt(channel).volume());

         track.name().addValueObserver(this::buildDisplay);
         track.volume().displayedValue().addValueObserver(this::buildDisplay);

         _vuMeterLastSend[channel] = Instant.now().toEpochMilli();

         track.addVuMeterObserver(14, -1, true, level -> {
            long now = Instant.now().toEpochMilli();
            if (_vuMeterLastSend[channel] < now - 250) {
               _midiOut.sendMidi(Midi.CHANNEL_PRESSURE, level + (channel << 4), 0);
               _vuMeterLastSend[channel] = now;
            }
         });
      }


      _host.showPopupNotification("D400Grid Initialized");
   }

   private void onNoteStepChanged(NoteStep step) {
      _host.println("Step:  "
              + " x: " + step.x()
              + " y: " + step.y()
              + " d: " + step.duration()
              + " v: " + step.velocity()
      );
      _grid.OnStepChange(step);
   }

   static String stringToHex(String string) {
      StringBuilder buf = new StringBuilder(200);
      for (char ch: string.toCharArray()) {
         if (buf.length() > 0)
            buf.append(' ');
         buf.append(String.format("%02x", (int) ch));
      }
      return buf.toString();
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
         if(data1 == D400.JOG_WHEEL)
         {
            handleJogWheel(data2);
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
               case D400.BTN_ASSIGN1: _application.setPanelLayout(_application.PANEL_LAYOUT_ARRANGE); break;
               case D400.BTN_ASSIGN2: _application.setPanelLayout(_application.PANEL_LAYOUT_EDIT); break;
            }
         }
      }
   }

   private void handleJogWheel(int value)
   {
      int amount = value > 64
              ? (128 - value) * -1
              : value;
      _grid.HorizontalScroll(amount);
   }

   private boolean _shuttleSettled = true;
   private void handleShuttle(int amount)
   {
      if(amount == 0)
      {
         _host.println("Settled");
         _shuttleSettled = true;
         return;
      }
      if(!_shuttleSettled)
      {
         _host.println("Skip");
         return;
      }

      if(_deviceMode)
      {
         if(amount == 8)
         {
            _host.println("Next Device");
            _parameterBanks[_selectedDevice].selectNextPage(true);
         }
         else if( amount == 120)
         {
            _host.println("Previous Device");
            _parameterBanks[_selectedDevice].selectPreviousPage(true);
         }
      }
      else
      {
         if(amount == 8)
         {
            _host.println("Next Scene");
            _clip.selectNext();
         }
         else if( amount == 120)
         {
            _host.println("Previous Scene");
            _clip.selectPrevious();
         }
      }

      _shuttleSettled = false;
   }

   private void selectTrack(int i)
   {
      clearFx();

      for (int t = 0; t < _trackBank.getSizeOfBank(); t++) {
         Track track = _trackBank.getItemAt(t);
         if(t == i)
         {
            track.selectInEditor();
            track.makeVisibleInArranger();
            _cursorTrack.selectSlot(_clip.clipLauncherSlot().sceneIndex().get());
            _clip.selectClip(_clip);
            _clip.clipLauncherSlot().select();
            _clip.clipLauncherSlot().showInEditor();
         }
         _midiOut.sendMidi(Midi.NOTE_ON, D400.BTN_SELECT_1+t, t == i ? 127 : 0);
         faders[t].setBinding(track.volume());
      }
      _selectedDevice = -1;
      _deviceMode = false;
      buildDisplay("");
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

      for (int t = 0; t < NUM_PARAMS_IN_PAGE; t++)
      {
         Parameter parameter = _parameterBanks[i].getParameter(t);
         faders[t].setBinding(parameter);
      }
      _selectedDevice = i;
      _deviceMode = true;
      buildDisplay("");
   }

   private void buildDisplay(Object unused) {
      StringBuilder line0 = new StringBuilder();
      StringBuilder line1 = new StringBuilder();
      if(_deviceMode)
      {
         for (int t = 0; t < NUM_PARAMS_IN_PAGE; t++)
         {
            Parameter parameter = _parameterBanks[_selectedDevice].getParameter(t);
            AddDisplayText(line0, parameter.name().get());
            AddDisplayText(line1, parameter.displayedValue().get());
         }
      }
      else {
         for (int t = 0; t < _trackBank.getSizeOfBank(); t++) {
            Track track = _trackBank.getItemAt(t);
            AddDisplayText(line0, track.name().get());
            AddDisplayText(line1, track.volume().displayedValue().get());
         }
      }
      _midiOut.sendSysex(Midi.SYSEX_HDR + "18" + "00"
              + stringToHex(line0.toString())
              + stringToHex(line1.toString())
              + Midi.SYSEX_END);
   }

   private void AddDisplayText(StringBuilder line, String text) {
      line.append(text, 0, Math.min(text.length(), 8));
      if (text.length() < 8) {
         for (int s = 0; s < 8 - text.length(); s++) {
            line.append(' ');
         }
      }
   }
}
