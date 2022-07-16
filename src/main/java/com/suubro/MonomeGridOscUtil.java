package com.suubro;

import com.bitwig.extension.api.opensoundcontrol.OscConnection;

import java.io.IOException;

public class MonomeGridOscUtil
{
    static void LedLevelMapByOsc(int[][] ledDisplay, OscConnection oscOut) {
        try
        {
            oscOut.startBundle();
            for (int xOffset = 0; xOffset < Grid.WIDTH / 8; xOffset++)
            {
                int xLookup = xOffset * 8;
                int yLookup = 0;
                oscOut.sendMessage("/monome/grid/led/level/map", xOffset * 8, 0,
                        ledDisplay[xLookup][yLookup],
                        ledDisplay[xLookup+1][yLookup],
                        ledDisplay[xLookup+2][yLookup],
                        ledDisplay[xLookup+3][yLookup],
                        ledDisplay[xLookup+4][yLookup],
                        ledDisplay[xLookup+5][yLookup],
                        ledDisplay[xLookup+6][yLookup],
                        ledDisplay[xLookup+7][yLookup],
                        ledDisplay[xLookup][yLookup +1],
                        ledDisplay[xLookup+1][yLookup +1],
                        ledDisplay[xLookup+2][yLookup +1],
                        ledDisplay[xLookup+3][yLookup +1],
                        ledDisplay[xLookup+4][yLookup +1],
                        ledDisplay[xLookup+5][yLookup +1],
                        ledDisplay[xLookup+6][yLookup +1],
                        ledDisplay[xLookup+7][yLookup +1],
                        ledDisplay[xLookup][yLookup +2],
                        ledDisplay[xLookup+1][yLookup +2],
                        ledDisplay[xLookup+2][yLookup +2],
                        ledDisplay[xLookup+3][yLookup +2],
                        ledDisplay[xLookup+4][yLookup +2],
                        ledDisplay[xLookup+5][yLookup +2],
                        ledDisplay[xLookup+6][yLookup +2],
                        ledDisplay[xLookup+7][yLookup +2],
                        ledDisplay[xLookup][yLookup +3],
                        ledDisplay[xLookup+1][yLookup +3],
                        ledDisplay[xLookup+2][yLookup +3],
                        ledDisplay[xLookup+3][yLookup +3],
                        ledDisplay[xLookup+4][yLookup +3],
                        ledDisplay[xLookup+5][yLookup +3],
                        ledDisplay[xLookup+6][yLookup +3],
                        ledDisplay[xLookup+7][yLookup +3],
                        ledDisplay[xLookup][yLookup +4],
                        ledDisplay[xLookup+1][yLookup +4],
                        ledDisplay[xLookup+2][yLookup +4],
                        ledDisplay[xLookup+3][yLookup +4],
                        ledDisplay[xLookup+4][yLookup +4],
                        ledDisplay[xLookup+5][yLookup +4],
                        ledDisplay[xLookup+6][yLookup +4],
                        ledDisplay[xLookup+7][yLookup +4],
                        ledDisplay[xLookup][yLookup +5],
                        ledDisplay[xLookup+1][yLookup +5],
                        ledDisplay[xLookup+2][yLookup +5],
                        ledDisplay[xLookup+3][yLookup +5],
                        ledDisplay[xLookup+4][yLookup +5],
                        ledDisplay[xLookup+5][yLookup +5],
                        ledDisplay[xLookup+6][yLookup +5],
                        ledDisplay[xLookup+7][yLookup +5],
                        ledDisplay[xLookup][yLookup +6],
                        ledDisplay[xLookup+1][yLookup +6],
                        ledDisplay[xLookup+2][yLookup +6],
                        ledDisplay[xLookup+3][yLookup +6],
                        ledDisplay[xLookup+4][yLookup +6],
                        ledDisplay[xLookup+5][yLookup +6],
                        ledDisplay[xLookup+6][yLookup +6],
                        ledDisplay[xLookup+7][yLookup +6],
                        ledDisplay[xLookup][yLookup +7],
                        ledDisplay[xLookup+1][yLookup +7],
                        ledDisplay[xLookup+2][yLookup +7],
                        ledDisplay[xLookup+3][yLookup +7],
                        ledDisplay[xLookup+4][yLookup +7],
                        ledDisplay[xLookup+5][yLookup +7],
                        ledDisplay[xLookup+6][yLookup +7],
                        ledDisplay[xLookup+7][yLookup +7]
                );
            }
            oscOut.endBundle();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
