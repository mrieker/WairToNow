/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.apps4av.avarehelper.gdl90;

import com.apps4av.avarehelper.connections.TopDecoder;

/**
 * 
 * @author zkhan
 * mods mrieker
 *
 */
public class HeartbeatMessage {
    public static void parse (GDL90Decoder mf, byte[] msg, int len)
    {
        if (len < 5) throw new IllegalArgumentException ();

        /*
         * Some useful fields
         */
        int d = msg[1] & 0xFF;
        boolean gpsPositionValid = (d & 0x80) != 0;
        //boolean mBatteryLow = (d & 0x40) != 0;
        //boolean mDeviceRunning = (d & 0x01) != 0;
       
        /*
         * Get time
         */
        int d1 = msg[2] & 0xFF;  // d1<7> = ts<16>
        int d2 = msg[3] & 0xFF;  // d2<7:0> = ts<07:00>
        int d3 = msg[4] & 0xFF;  // d3<7:0> = ts<15:08>

        // milliseconds since 0000Z
        int msecs = (((d1 & 0x80) << 9) | (d3 << 8) | d2) * 1000;

        mf.heartbeatPosVal = gpsPositionValid;
        mf.heartbeatTime   = TopDecoder.GetFullTime (msecs);
        mf.ownSpeed        = Double.NaN;
        mf.ownLatitude     = Double.NaN;
        mf.ownLongitude    = Double.NaN;
        mf.ownTrueAlt      = Double.NaN;
    }
}
