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
 * http://www.gpsinformation.org/dale/nmea.htm#GGA
 *
 * GGA - essential fix data which provide 3D location and accuracy data.
 *
 * $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
 *
 * Where:
 * GGA          Global Positioning System Fix Data
 * 123519       Fix taken at 12:35:19 UTC
 * 4807.038,N   Latitude 48 deg 07.038' N
 * 01131.000,E  Longitude 11 deg 31.000' E
 * 1            Fix quality: 0 = invalid
 *                           1 = GPS fix (SPS)
 *                           2 = DGPS fix
 *                           3 = PPS fix
 *                           4 = Real Time Kinematic
 *                           5 = Float RTK
 *                           6 = estimated (dead reckoning) (2.3 feature)
 *                           7 = Manual input mode
 *                           8 = Simulation mode
 * 08           Number of satellites being tracked
 * 0.9          Horizontal dilution of position
 * 545.4,M      Altitude, Meters, above mean sea level
 * 46.9,M       Height of geoid (mean sea level) above WGS84
 *                  ellipsoid
 * (empty field) time in seconds since last DGPS update
 * (empty field) DGPS station ID number
 * *47          the checksum data, always begins with *
 *
 * @author zkhan
 * mods mrieker
 *
 */
public class GGAMessage {
    public static void parse (String[] tokens, NMEADecoder mf)
    {
        boolean valid = Integer.parseInt (tokens[6]) > 0;
        if (valid) {
            int  msec = NMEADecoder.milliseconds (tokens[1]);
            long time = TopDecoder.GetFullTime (msec);

            mf.ownLatitude  = NMEADecoder.degrees (tokens[2], tokens[3], "N", "S");
            mf.ownLongitude = NMEADecoder.degrees (tokens[4], tokens[5], "E", "W");

            float alt = NMEADecoder.parseFloat (tokens[9], Float.NaN, 3.28084F);
            if (!tokens[10].equals ("M")) {
                throw new NumberFormatException ("bad altitude units " + tokens[10]);
            }

            mf.maybeReportOwnshipInfo (time, Float.NaN, Float.NaN, alt);
        }
    }
}
