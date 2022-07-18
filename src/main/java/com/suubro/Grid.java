package com.suubro;

import com.bitwig.extension.api.opensoundcontrol.OscAddressSpace;
import com.bitwig.extension.api.opensoundcontrol.OscConnection;
import com.bitwig.extension.api.opensoundcontrol.OscMessage;
import com.bitwig.extension.api.opensoundcontrol.OscModule;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extension.controller.api.PinnableCursorClip;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

class Key {

    private final int x;
    private final int y;

    public Key(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Key)) return false;
        Key key = (Key) o;
        return x == key.x && y == key.y;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        return result;
    }

}

public class Grid
{
    public static final int PORT = 19762;
    public static final int HEIGHT = 8;
    public static final int WIDTH = 16;
    public static final int VIRTUAL_HEIGHT = 128;
    public static final int VIRTUAL_WIDTH = WIDTH * 256;
    private static final String[] SCALES = new String[]{ "Chromatic", "Major", "Minor" };


    private final ControllerHost _host;
    private final PinnableCursorClip _clip;
    private CursorTrack _cursorTrack;
    private final OscConnection _oscOut;

    private final int[] _lastDownpressByRow = {-1, -1, -1, -1, -1, -1, -1, -1};
    private int _currentStep = -1;
    private double _zoomLevel = 0.25;
    private int _earliestDisplayedNote = 0;
    private int _lowestDisplayedPitch = 60;
    private int _scaleIndex = 0;
    private int _scaleRoot = 0;
    private int _heldNotePitch = -1;
    private final Map<Key,NoteStep> _notes = new HashMap<>();

    int[][] _ledDisplay = new int[WIDTH][HEIGHT];

    public Grid(ControllerHost host, PinnableCursorClip clip, CursorTrack cursorTrack) {
        _host = host;
        _clip = clip;
        _cursorTrack = cursorTrack;

        _clip.setStepSize(_zoomLevel);
        _clip.getLoopStart().addValueObserver(d -> Render());
        _clip.getLoopLength().addValueObserver(d -> Render());
        _clip.playingStep().addValueObserver(this::UpdatePlayingStep);
        _clip.addNoteStepObserver(this::OnStepChange);

        OscModule osc = _host.getOscModule();

        try {
            int listenPort = (int) Math.floor(Math.random() * 64512) + 1024;

            SetUpOsc(osc, listenPort);

            _oscOut = osc.connectToUdpServer("127.0.0.1", PORT, osc.createAddressSpace());

            _oscOut.sendMessage("/sys/port", listenPort);
            _oscOut.sendMessage("/sys/host", "127.0.0.1");
            _oscOut.sendMessage("/sys/prefix", "/monome");
            _oscOut.sendMessage("/sys/info");
            _oscOut.sendMessage("/monome/grid/led/all", 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void UpdatePlayingStep(int step)
    {
        _currentStep = step;
        Render();
    }

    private void Clear()
    {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                _ledDisplay[x][y] = 0;
            }
        }
    }

    public int yToPitch(int y)
    {
        return y + _lowestDisplayedPitch;
    }

    public int yToGridIndex(int y)
    {
        return HEIGHT - y - 1;
    }

    public void Render()
    {
        Clear();
        for (int y = 0; y < HEIGHT; y++)
        {
            final int pitch = yToPitch(y);

            List<NoteStep> notesAtPitch = _notes.values().stream()
                    .filter(n -> n.velocity() > 0 && n.y() == pitch)
                    .sorted(Comparator.comparingInt(NoteStep::x))
                    .collect(Collectors.toList());

            HashSet<Integer> seenNotes = new HashSet<>();
            for (NoteStep note:notesAtPitch)
            {
                if(seenNotes.contains(note.x()))
                {
                    continue;
                }
                seenNotes.add(note.x());

                int actualStart = note.x() - _earliestDisplayedNote;
                int actualEnd = note.x() + (int)(note.duration() / _zoomLevel) - _earliestDisplayedNote;

                for (int i = 1; i < actualEnd-actualStart; i++) {
                    seenNotes.add(note.x()+i);
                }

                if(actualEnd <= 0 || actualStart >= WIDTH)
                {
                    continue; // Nothing to display
                }
                int start = Math.max(actualStart, 0);
                int end = Math.min(actualEnd, WIDTH);

                _ledDisplay[start][yToGridIndex(y)] = actualStart == start ? 12 : 6;
                for (int i = 1; i < end-start; i++) {
                    _ledDisplay[start+i][yToGridIndex(y)] = 6;
                }
            }

            double loopStart = _clip.getLoopStart().get() / _zoomLevel;
            double loopEnd = (_clip.getLoopStart().get() + _clip.getLoopLength().get()) / _zoomLevel;

            for (int x = 0; x < WIDTH; x++)
            {
                int oldValue = _ledDisplay[x][yToGridIndex(y)];
                if (_currentStep == _earliestDisplayedNote + x)
                {
                    _ledDisplay[x][yToGridIndex(y)] = oldValue + 3;
                }
                else if (_earliestDisplayedNote + x < loopStart)
                {
                    _ledDisplay[x][yToGridIndex(y)] = oldValue == 0 ? 2 : oldValue;
                }
                else if (_earliestDisplayedNote + x >= loopEnd)
                {
                    _ledDisplay[x][yToGridIndex(y)] = oldValue == 0 ? 2 : oldValue;
                }
            }
        }
        MonomeGridOscUtil.LedLevelMapByOsc(_ledDisplay, _oscOut);
    }

    public void OnStepChange(NoteStep step)
    {
        _notes.put(new Key(step.x(), step.y()), step);
        Render();
    }

    private void onKey(OscConnection s, OscMessage msg) {
        int x = msg.getInt(0);
        int y = msg.getInt(1);
        boolean downPress = msg.getInt(2) > 0;

        _host.println(msg.getAddressPattern()
                + " x: " + x
                + " y: " + y
                + " downPress: " + downPress);

        int position = x + _earliestDisplayedNote;
        int pitch = yToPitch(yToGridIndex(y));
        int lastDownPress = _lastDownpressByRow[y];

        if(downPress)
        {
            _heldNotePitch = pitch;
        }
        else
        {
            _heldNotePitch = -1;
        }
        if(downPress && x == WIDTH-1)
        {
            _cursorTrack.playNote(pitch, 127);
            return;
        }

        if(downPress && lastDownPress >= 0 && lastDownPress != x) {
            int start = _earliestDisplayedNote + Math.min(x, lastDownPress);
            int end = _earliestDisplayedNote + Math.max(x, lastDownPress);
            for (int i = start; i < end; i++)
            {
                NoteStep existing = _notes.getOrDefault(new Key(i, pitch), null);
                if (existing != null && existing.velocity() > 0)
                {
                    return; // clash with existing note
                }
            }
            double duration = (end + 1 - start) * _zoomLevel;
            _clip.setStep(start, pitch, 127, duration);
            _lastDownpressByRow[y] = -1;
        }
        else if (!downPress && lastDownPress >= 0)
        {
            if (lastDownPress == x) // Same key released
            {
                NoteStep existing = _notes.getOrDefault(new Key(_earliestDisplayedNote + x, pitch), null);
                if (existing != null && existing.velocity() > 0)
                {
                    _clip.clearStep(position, pitch);
                }
                else
                {
                    _clip.setStep(position, pitch, 127, 1 * _zoomLevel);
                }
            }
            _lastDownpressByRow[y] = -1;
        }
        else if (downPress)
        {
            _lastDownpressByRow[y] = x;
        }
    }

    private void SetUpOsc(OscModule osc, int listenPort) throws IOException
    {
        OscConnection oscOut = osc.connectToUdpServer("127.0.0.1", 12002, osc.createAddressSpace());
        oscOut.sendMessage("/serialosc/list", "127.0.0.1", listenPort);

        OscAddressSpace addressSpace = osc.createAddressSpace();
        osc.createUdpServer(listenPort, addressSpace);

        addressSpace.registerDefaultMethod(this::handleUnknownMsg);

        addressSpace.registerMethod("/monome/grid/key", "*", "Grid key pressed", this::onKey);

        addressSpace.registerMethod("/serialosc/device", "*", "Device List", (source, message) ->
        {
            _host.println("Received: " + message.getAddressPattern()
                    + " " + message.getTypeTag()
                    + " " + message.getString(0)
                    + " " + message.getString(1)
                    + " " + message.getInt(2)
            );

            if(PORT != message.getInt(2)){
                String error = "DETECTED DIFFERENT GRID PORT TO THE ONE CONFIGURED." +
                        " Configured: " + PORT + " Detected: " + message.getInt(2);
                _host.errorln(error);
                _host.showPopupNotification(error);
            }
        });
    }

    private void handleUnknownMsg(OscConnection source, OscMessage message)
    {
        _host.println("Received unknown: " + message.getAddressPattern());
    }

    public void HorizontalScroll(int amount)
    {
        _earliestDisplayedNote += amount;
        _earliestDisplayedNote = Math.min(Math.max(_earliestDisplayedNote, 0), VIRTUAL_WIDTH-WIDTH);
        Render();
    }

    public void VerticalScroll(int amount)
    {
        _lowestDisplayedPitch += amount;
        _lowestDisplayedPitch = Math.min(Math.max(_lowestDisplayedPitch, 0), VIRTUAL_HEIGHT-HEIGHT);
        _host.showPopupNotification("Lowest Note: " + Scales.PitchToNoteName(_lowestDisplayedPitch));
        Render();
    }

    public void ChangeScale(int amount)
    {
        _scaleIndex += amount > 0 ? 1 : -1;
        _scaleIndex = Math.max(Math.min(_scaleIndex, SCALES.length - 1), 0);
        _host.showPopupNotification("Scale: " + Scales.IntervalToNoteName(_scaleRoot)
                + " " + SCALES[_scaleIndex]);
        Render();
    }

    public void Zoom(int relative)
    {
        if(relative > 0)
        {
            _zoomLevel = _zoomLevel / 2.0;
        }
        else
        {
            _zoomLevel = _zoomLevel * 2.0;
        }
        _host.println("zoom: " + _zoomLevel);
        _clip.setStepSize(_zoomLevel);
        _host.showPopupNotification("Zoom level: " + _zoomLevel);
        Render();
    }

    public void ChangeScaleRoot()
    {
        if(_heldNotePitch > 0){
            _scaleRoot = _heldNotePitch % 12;
            _host.showPopupNotification("Scale: " + Scales.IntervalToNoteName(_scaleRoot)
                    + " " + SCALES[_scaleIndex]);
        }
    }
}
