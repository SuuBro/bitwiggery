package com.suubro;

import com.bitwig.extension.api.opensoundcontrol.OscAddressSpace;
import com.bitwig.extension.api.opensoundcontrol.OscConnection;
import com.bitwig.extension.api.opensoundcontrol.OscMessage;
import com.bitwig.extension.api.opensoundcontrol.OscModule;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.NoteStep;

import java.io.IOException;

public class Grid
{
    public static final int Port  = 19762;

    private final ControllerHost _host;
    private final OscConnection _oscOut;

    public Grid(ControllerHost _host) {
        this._host = _host;
        OscModule osc = _host.getOscModule();

        try {
            int listenPort = (int) Math.floor(Math.random() * 64512) + 1024;

            SetUpOsc(osc, listenPort);

            _oscOut = osc.connectToUdpServer("127.0.0.1", Port, osc.createAddressSpace());

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
        try {
            _oscOut.sendMessage("/monome/grid/led/all", 0);
            for (int i = 0; i < 8; i++)
            {
                _oscOut.sendMessage("/monome/grid/led/set", step, i, 1);
            }
        } catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        Render();
    }

    public void Render()
    {

    }

    public void OnStepChange(NoteStep step)
    {
        Render();
    }

    private void onKeyPressed(OscConnection s, OscMessage msg) {
        _host.println("Received: " + msg.getAddressPattern()
                + " " + msg.getTypeTag()
                + " " + msg.getInt(0)
                + " " + msg.getInt(1)
                + " " + msg.getInt(2));
    }

    private void SetUpOsc(OscModule osc, int listenPort) throws IOException
    {
        OscConnection oscOut = osc.connectToUdpServer("127.0.0.1", 12002, osc.createAddressSpace());
        oscOut.sendMessage("/serialosc/list", "127.0.0.1", listenPort);

        OscAddressSpace addressSpace = osc.createAddressSpace();
        osc.createUdpServer(listenPort, addressSpace);

        addressSpace.registerDefaultMethod(this::handleUnknownMsg);

        addressSpace.registerMethod("/monome/grid/key", "*", "Grid key pressed", this::onKeyPressed);

        addressSpace.registerMethod("/serialosc/device", "*", "Device List", (source, message) ->
        {
            _host.println("Received: " + message.getAddressPattern()
                    + " " + message.getTypeTag()
                    + " " + message.getString(0)
                    + " " + message.getString(1)
                    + " " + message.getInt(2)
            );

            if(Port != message.getInt(2)){
                String error = "DETECTED DIFFERENT GRID PORT TO THE ONE CONFIGURED." +
                        " Configured: " + Port + " Detected: " + message.getInt(2);
                _host.errorln(error);
                _host.showPopupNotification(error);
            }
        });
    }

    private void handleUnknownMsg(OscConnection source, OscMessage message) {
        _host.println("Received unknown: " + message.getAddressPattern());
    }
}
