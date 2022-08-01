package com.suubro;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;

import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
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
   private final CursorRemoteControlsPage[] _parameterBanks = new CursorRemoteControlsPage[8];
   private Application _application;

   private final Fader[] _faders = new Fader[8];
   private final long[] _vuMeterLastSend = new long[8];
   private final Timer _displayUpdateTimer = new Timer();

   private boolean _deviceMode = false;
   private int _selectedTrack = -1;
   private int _selectedDevice = -1;
   private boolean _shift = false;

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
      NoteInput noteInput = _midiIn.createNoteInput("Grid Note Input", "00000");
      _midiOut = _host.getMidiOutPort(0);

      _cursorTrack = _host.createCursorTrack("D400_CURSOR_TRACK", "Cursor Track", 0, 0, true);
      _clip = _cursorTrack.createLauncherCursorClip(Grid.VIRTUAL_WIDTH, Grid.VIRTUAL_HEIGHT);

      _clip.clipLauncherSlot().sceneIndex().markInterested();
      _transport = _host.createTransport();
      _trackBank = _host.createMainTrackBank(8, 0, 0);
      _trackBank.followCursorTrack(_cursorTrack);

      _grid = new Grid(_host, _clip, noteInput, _transport, _trackBank);

      _hardwareSurface = _host.createHardwareSurface();

      createButtonWithLight("PLAY", D400.BTN_PLAY, _transport.playAction(), _transport.isPlaying());
      createButtonWithLight("STOP", D400.BTN_STOP, _transport.stopAction(), _transport.isPlaying(),
              () -> !_transport.isPlaying().get());

      createButtonWithLight("RECORD", D400.BTN_REC, _transport.isClipLauncherOverdubEnabled());
      createButtonWithLight("LOOP", D400.BTN_LOOP, _transport.isArrangerLoopEnabled());
      createButtonWithLight("METRONOME", D400.BTN_METRONOME, _transport.isMetronomeEnabled());


      _deviceBank = _cursorTrack.createDeviceBank(8);

      for (int i = 0; i < _deviceBank.getSizeOfBank(); i++) {
         _parameterBanks[i] = _deviceBank.getDevice(i).createCursorRemoteControlsPage(8);
         for (int p = 0; p < NUM_PARAMS_IN_PAGE; p++)
         {
            _parameterBanks[i].getParameter(p).name().markInterested();
            _parameterBanks[i].getParameter(p).value().markInterested();
            _parameterBanks[i].getParameter(p).displayedValue().markInterested();
         }
      }

      for (int i = 0; i < _trackBank.getSizeOfBank(); i++) {
         final int channel = i;
         final Track track = _trackBank.getItemAt(i);
         track.name().markInterested();
         track.volume().markInterested();
         track.volume().displayedValue().markInterested();

         _faders[channel] = new Fader(_host, _midiIn, _midiOut, channel, _hardwareSurface);
         _faders[channel].setBinding(_trackBank.getItemAt(channel).volume());
         createButtonWithLight("MUTE" + i, D400.BTN_MUTE_1+i, track.mute());
         createButtonWithLight("SOLO" + i, D400.BTN_RECORD_1+i, track.solo());

         _vuMeterLastSend[channel] = Instant.now().toEpochMilli();

         track.addVuMeterObserver(14, -1, true, level -> {
            long now = Instant.now().toEpochMilli();
            if (_vuMeterLastSend[channel] < now - 120) {
               _midiOut.sendMidi(Midi.CHANNEL_PRESSURE, level + (channel << 4), 0);
               _vuMeterLastSend[channel] = now;
            }
         });
      }

      _displayUpdateTimer.scheduleAtFixedRate(new TimerTask() {
         @Override
         public void run() {
            buildDisplay();
         }
      }, 0, 100);

      _host.showPopupNotification("D400Grid Initialized");
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

   public void createButtonWithLight(final String name, final int midi, HardwareBindable binding,
                                     SettableBooleanValue value)
   {
      createButtonWithLight(name, midi, binding, value, value::get);
   }

   public void createButtonWithLight(final String name, final int midi, SettableBooleanValue value)
   {
      createButtonWithLight(name, midi, value.toggleAction(), value, value::get);
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
      _grid.Render();
   }

   public void handleMidi (final int statusByte, final int data1, final int data2)
   {
      final ShortMidiMessage msg = new ShortMidiMessage (statusByte, data1, data2);
      // _host.println(msg.toString());

      if(statusByte == Midi.NOTE_ON)
      {
         if (data1 == D400.SHUTTLE)
         {
            handleShuttle(data2);
         }
         if (data1 == D400.BTN_SHIFT)
         {
            _shift = data2 > 0;
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
               case D400.BTN_MUTE_1: muteTrack(0); break;
               case D400.BTN_MUTE_2: muteTrack(1); break;
               case D400.BTN_MUTE_3: muteTrack(2); break;
               case D400.BTN_MUTE_4: muteTrack(3); break;
               case D400.BTN_MUTE_5: muteTrack(4); break;
               case D400.BTN_MUTE_6: muteTrack(5); break;
               case D400.BTN_MUTE_7: muteTrack(6); break;
               case D400.BTN_MUTE_8: muteTrack(7); break;
               case D400.BTN_RECORD_1: soloTrack(0); break;
               case D400.BTN_RECORD_2: soloTrack(1); break;
               case D400.BTN_RECORD_3: soloTrack(2); break;
               case D400.BTN_RECORD_4: soloTrack(3); break;
               case D400.BTN_RECORD_5: soloTrack(4); break;
               case D400.BTN_RECORD_6: soloTrack(5); break;
               case D400.BTN_RECORD_7: soloTrack(6); break;
               case D400.BTN_RECORD_8: soloTrack(7); break;
               case D400.BUTTON_1: selectFx(0, true); break;
               case D400.BUTTON_2: selectFx(1, true); break;
               case D400.BUTTON_3: selectFx(2, true); break;
               case D400.BUTTON_4: selectFx(3, true); break;
               case D400.BUTTON_5: selectFx(4, true); break;
               case D400.BUTTON_6: selectFx(5, true); break;
               case D400.BUTTON_7: selectFx(6, true); break;
               case D400.BUTTON_8: selectFx(7, true); break;
               case D400.BTN_ASSIGN1: changeView(1); break;
               case D400.BTN_ASSIGN2: changeView(2); break;
               case D400.JOG_WHEEL: _grid.HorizontalScroll(relative(data2)); break;
               case D400.EQ_KNOB_1: _grid.ChangeScale(relative(data2)); break;
               case D400.EQ_KNOB_BTN_1: _grid.ChangeScaleRoot(); break;
               case D400.EQ_KNOB_3: _grid.Zoom(relative(data2)); break;
               case D400.EQ_KNOB_4: _grid.VerticalScroll(relative(data2)); break;
            }
         }
      }
   }

   private void changeView(int view)
   {
      if(_shift)
      {
         if(view == 1)
         {
            _application.toggleNoteEditor();
         }
         else if(view == 2)
         {
            _application.toggleDevices();
         }
      }
      else
      {
         _application.setPanelLayout(view == 1
                 ? _application.PANEL_LAYOUT_ARRANGE
                 : _application.PANEL_LAYOUT_EDIT);
      }
   }

   private void muteTrack(int i)
   {
      _trackBank.getItemAt(i).mute().toggle();
      _midiOut.sendMidi(Midi.NOTE_ON, D400.BTN_MUTE_1+i, !_trackBank.getItemAt(i).mute().get() ? 0 : 127);
   }

   private void soloTrack(int i)
   {
      _trackBank.getItemAt(i).solo().toggle();
      _midiOut.sendMidi(Midi.NOTE_ON, D400.BTN_RECORD_1+i, !_trackBank.getItemAt(i).solo().get() ? 0 : 127);
   }

   private int relative(int midiData)
   {
      return midiData > 64
              ? (128 - midiData) * -1
              : midiData;
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
      boolean toggleDeviceMode = _selectedTrack == i;

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
            _selectedTrack = i;
         }
         _midiOut.sendMidi(Midi.NOTE_ON, D400.BTN_SELECT_1+t, t == i ? 127 : 0);
         _faders[t].setBinding(track.volume());
      }

      if(toggleDeviceMode)
      {
         _deviceMode = !_deviceMode;
      }
      else
      {
         selectFx(0, false);
      }

      buildDisplay();
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
      _selectedDevice = -1;
   }

   private void selectFx(int i, boolean toggleFxWindow)
   {
      clearFx(i);
      _deviceBank.scrollIntoView(i);
      Device device = _deviceBank.getDevice(i);
      device.selectInEditor();
      if(toggleFxWindow)
      {
         device.isWindowOpen().toggle();
      }
      device.isRemoteControlsSectionVisible().set(true);

      for (int t = 0; t < NUM_PARAMS_IN_PAGE; t++)
      {
         Parameter parameter = _parameterBanks[i].getParameter(t);
         _faders[t].setBinding(parameter);
      }
      _selectedDevice = i;
      _deviceMode = true;
      buildDisplay();
   }

   String _displayCache = null;
   private void buildDisplay()
   {
      StringBuilder line0 = new StringBuilder();
      StringBuilder line1 = new StringBuilder();
      if(_deviceMode && _selectedDevice >= 0)
      {
         for (int t = 0; t < NUM_PARAMS_IN_PAGE; t++)
         {
            Parameter parameter = _parameterBanks[_selectedDevice].getParameter(t);
            AddDisplayText(line0, parameter.name().get());
            AddDisplayText(line1, parameter.displayedValue().getLimited(8));
         }
      }
      else {
         for (int t = 0; t < _trackBank.getSizeOfBank(); t++) {
            Track track = _trackBank.getItemAt(t);
            AddDisplayText(line0, track.name().get());
            AddDisplayText(line1, track.volume().displayedValue().get());
         }
      }
      final String updateMessage = Midi.SYSEX_HDR + "18" + "00"
              + stringToHex(line0.toString())
              + stringToHex(line1.toString())
              + Midi.SYSEX_END;

      if(!updateMessage.equals(_displayCache))
      {
         _midiOut.sendSysex(updateMessage);
         _displayCache = updateMessage;
      }
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
