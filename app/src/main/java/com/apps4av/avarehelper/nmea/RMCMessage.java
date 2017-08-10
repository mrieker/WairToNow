/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.apps4av.avarehelper.nmea;

import com.apps4av.avarehelper.connections.TopDecoder;

/**
 * 
 * @author zkhan
 * mods mrieker
 *
 */
public class RMCMessage {
    public static void parse (String[] tokens, NMEADecoder mf)
    {
        /*
         * 3,4 = lat
         * 5,6 = lon
         * 7 = speed
         * 8 = bearing
         */
        boolean valid = tokens[2].equals ("A");
        if (valid) {
            int  msec = NMEADecoder.milliseconds (tokens[1]);
            long time = TopDecoder.GetFullTime (msec);
            double lat = NMEADecoder.degrees (tokens[3], tokens[4], "N", "S");
            double lon = NMEADecoder.degrees (tokens[5], tokens[6], "E", "W");
            double kts = NMEADecoder.parseDouble (tokens[7], Double.NaN, 1.0);
            double hdg = NMEADecoder.parseDouble (tokens[8], Double.NaN, 1.0);

            mf.ownLatitude  = lat;
            mf.ownLongitude = lon;

            mf.maybeReportOwnshipInfo (time, hdg, kts, Double.NaN);
        }
    }
}
