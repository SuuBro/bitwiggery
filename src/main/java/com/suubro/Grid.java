package com.suubro;

import com.bitwig.extension.api.opensoundcontrol.OscAddressSpace;
import com.bitwig.extension.api.opensoundcontrol.OscConnection;
import com.bitwig.extension.api.opensoundcontrol.OscMessage;
import com.bitwig.extension.api.opensoundcontrol.OscModule;
import com.bitwig.extension.controller.api.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Key {

    public final int x;
    public final int y;

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
    public static final String[] SCALES = new String[]{ "Chromatic", "Major", "Minor" };

    private static final String GRID_SETTINGS = "Grid Settings";
    private static final double RETRIGGER_GAP = 0.001;

    private final ControllerHost _host;
    private final PinnableCursorClip _clip;
    private final NoteInput _noteInput;
    private final Transport _transport;
    private final OscConnection _oscOut;

    SettableEnumValue _scaleSetting;
    SettableEnumValue _scaleRootSetting;

    private final int[] _lastDownpressByRow = {-1, -1, -1, -1, -1, -1, -1, -1};
    private int _currentStep = -1;
    private double _zoomLevel = 0.25;
    private int _earliestDisplayedNote = 0;
    private int _lowestDisplayedPitchIndex = 60;
    private int _scaleIndex = 0;
    private int _scaleRoot = 0;
    private int[] _availablePitches = IntStream.range(0, 127).toArray();
    private int _heldNotePitch = -1;
    private final Map<Key,NoteStep> _notes = new HashMap<>();
    private final Map<Key,Integer> _noteStarts = new HashMap<>();

    int[][] _ledDisplay = new int[WIDTH][HEIGHT];

    public Grid(ControllerHost host, PinnableCursorClip clip, NoteInput noteInput, Transport transport)
    {
        _host = host;
        _clip = clip;
        _noteInput = noteInput;
        _transport = transport;

        _transport.isClipLauncherOverdubEnabled().markInterested();

        _clip.setStepSize(_zoomLevel);
        _clip.getLoopStart().addValueObserver(d -> Render());
        _clip.getLoopLength().addValueObserver(d -> Render());
        _clip.playingStep().addValueObserver(this::UpdatePlayingStep);
        _clip.addNoteStepObserver(this::OnStepChange);
        _clip.addStepDataObserver(this::OnStepData);

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

        DocumentState documentState = _host.getDocumentState();


        _scaleSetting = documentState.getEnumSetting("Scale", GRID_SETTINGS,
                Grid.SCALES, "Chromatic");
        _scaleSetting.addValueObserver(this::ChangeScaleSetting);

        _scaleRootSetting = documentState.getEnumSetting("Scale Root", GRID_SETTINGS,
                Scales.NoteNameToIndex.keySet().stream().sorted().toArray(String[]::new), "C");
        _scaleRootSetting.addValueObserver(this::ChangeScaleRootSetting);
    }

    private void ChangeScaleSetting(String scaleName)
    {
        SetScaleIndex(Arrays.asList(SCALES).indexOf(scaleName));
    }

    private void ChangeScaleRootSetting(String scaleRoot)
    {
        SetScaleRoot(Scales.NoteNameToIndex.get(scaleRoot));
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
        return _availablePitches[y + _lowestDisplayedPitchIndex];
    }

    public int yToGridIndex(int y)
    {
        return HEIGHT - y - 1;
    }

    private long lastRender = 0;
    public void Render()
    {
        boolean isRecording = _transport.isClipLauncherOverdubEnabled().get();
        long now = Instant.now().toEpochMilli();
        if(isRecording && lastRender > now - 60)
        {
            return; // Don't render too often while recording, missed MIDI notes
        }
        lastRender = now;

        Clear();
        for (int y = 0; y < HEIGHT; y++)
        {
            final int pitch = yToPitch(y);

            List<Map.Entry<Key,Integer>> notesAtPitch = _noteStarts.entrySet().stream()
                    .filter(n -> n.getValue() > 0 && n.getKey().y == pitch)
                    .sorted(Comparator.comparingInt(value -> value.getKey().x))
                    .collect(Collectors.toList());

            for (Map.Entry<Key,Integer> note:notesAtPitch)
            {
                int x = note.getKey().x - _earliestDisplayedNote;
                if(x < 0 || x >= WIDTH)
                {
                    continue; // Nothing to display
                }
                _ledDisplay[x][yToGridIndex(y)] = (note.getValue() == 1) ? 6 : 12;
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

    private void OnStepData(int x, int y, int t)
    {
        _noteStarts.put(new Key(x, y), t);
        Render();
    }

    private void onKey(OscConnection s, OscMessage msg) {
        int x = msg.getInt(0);
        int y = msg.getInt(1);
        boolean downPress = msg.getInt(2) > 0;

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
        if(x == WIDTH-1)
        {
            _noteInput.sendRawMidiEvent(downPress ? Midi.NOTE_ON : Midi.NOTE_OFF, pitch, 64);
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
            addNoteStep(pitch, start, duration);
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
                    addNoteStep(pitch, position, _zoomLevel);
                }
            }
            _lastDownpressByRow[y] = -1;
        }
        else if (downPress)
        {
            _lastDownpressByRow[y] = x;
        }
    }

    private void addNoteStep(int pitch, int start, double duration)
    {
        _clip.setStep(start, pitch, 64, duration - RETRIGGER_GAP);
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
        _lowestDisplayedPitchIndex += amount;
        _lowestDisplayedPitchIndex = Math.min(Math.max(_lowestDisplayedPitchIndex, 0), _availablePitches.length - HEIGHT);
        _host.showPopupNotification("Lowest Note: " + Scales.PitchToNoteName(_availablePitches[_lowestDisplayedPitchIndex]));
        Render();
    }

    public void ChangeScale(int amount)
    {
        int newIndex = _scaleIndex + (amount > 0 ? 1 : -1);
        SetScaleIndex(Math.max(Math.min(newIndex, SCALES.length - 1), 0));
    }

    private void SetScaleIndex(int index)
    {
        _scaleIndex = index;
        _host.showPopupNotification("Scale: " + Scales.IntervalToNoteName(_scaleRoot)
                + " " + SCALES[_scaleIndex]);
        _scaleSetting.set(SCALES[_scaleIndex]);
        UpdateAvailablePitches();
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
        if (_heldNotePitch < 0) {
            return;
        }
        SetScaleRoot(_heldNotePitch % 12);
    }

    private void SetScaleRoot(int noteIndex) {
        _scaleRoot = noteIndex;
        _host.showPopupNotification("Scale: " + Scales.IntervalToNoteName(_scaleRoot)
                    + " " + SCALES[_scaleIndex]);
        _scaleRootSetting.set(Scales.NoteIndexToName.get(_scaleRoot));
        UpdateAvailablePitches();
        Render();
    }

    private void UpdateAvailablePitches()
    {
        int trackingNote = _heldNotePitch > 0 ? _heldNotePitch : yToPitch(4);
        int originalPosition = ClosestIndex(trackingNote, _availablePitches) - _lowestDisplayedPitchIndex;

        _availablePitches = Scales.GeneratePitches(_scaleRoot, SCALES[_scaleIndex]);

        // re-position the view so that changing scale is less disorienting
        int newPosition = ClosestIndex(trackingNote, _availablePitches) - _lowestDisplayedPitchIndex;
        int diff = originalPosition - newPosition;
        _lowestDisplayedPitchIndex -= diff;
    }

    private static int ClosestIndex(int target, int[] values)
    {
        int min = Integer.MAX_VALUE;
        int closest = -1;

        for (int i = 0; i < values.length; i++) {
            final int diff = Math.abs(values[i] - target);
            if (diff < min) {
                min = diff;
                closest = i;
            }
        }

        return closest;
    }
}
