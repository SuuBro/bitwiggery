package com.suubro;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.controller.ControllerExtension;
import com.suubro.handler.*;

public class D400GridExtension extends ControllerExtension
{
   private ControllerHost _host;
   private TransportHandler _transport;
   private ModeHandler _mode;

   protected D400GridExtension(final D400GridExtensionDefinition definition, final ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      _host = getHost();

      MidiIn midiIn = _host.getMidiInPort(0);
      MidiOut midiOut = _host.getMidiOutPort(0);
      
      final D400Hardware hardware = new D400Hardware(midiOut, midiIn, this::handleMidi);
      _transport = new TransportHandler(_host.createTransport(), hardware);

      final CursorTrack cursorTrack = _host.createCursorTrack("D400_CURSOR_TRACK", "Cursor Track", 0, 0, true);
      final TrackHandler trackHandler = new TrackHandler(_host.createMainTrackBank (8, 0, 0), cursorTrack);

      final PinnableCursorDevice cursorDevice = cursorTrack.createCursorDevice("D400_CURSOR_DEVICE", "Cursor Device", 0, CursorDeviceFollowMode.FOLLOW_SELECTION);
      final RemoteControlHandler remoteControlHandler = new RemoteControlHandler(cursorDevice, cursorDevice.createCursorRemoteControlsPage (8));

      final Mode [] modes = new Mode[]
      {
          trackHandler,
          remoteControlHandler
      };
      _mode = new ModeHandler(modes, _host);

      _host.showPopupNotification("D400Grid Initialized");
   }

   @Override
   public void exit()
   {
      getHost().showPopupNotification("D400Grid Exited");
   }

   @Override
   public void flush()
   {
      _transport.updateLEDs ();
   }

   public void handleMidi (final int statusByte, final int data1, final int data2)
   {
      final ShortMidiMessage msg = new ShortMidiMessage (statusByte, data1, data2);

      _host.println(msg.toString());

      if (_transport.handleMidi (msg))
         return;

      if (_mode.handleMidi (msg))
         return;

      this.getHost ().errorln ("Midi command not processed: " + msg.getStatusByte () + " : " + msg.getData1 ());
   }
}
