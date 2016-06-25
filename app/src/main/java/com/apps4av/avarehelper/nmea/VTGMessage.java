//    Copyright (C) 2016, Mike Rieker, Beverly, MA USA
//    www.outerworldapps.com
//
//    This program is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; version 2 of the License.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    EXPECT it to FAIL when someone's HeALTh or PROpeRTy is at RISk.
//
//    You should have received a copy of the GNU General Public License
//    along with this program; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//    http://www.gnu.org/licenses/gpl-2.0.html

package com.apps4av.avarehelper.nmea;

/**
 * http://aprs.gids.nl/nmea/#vtg
 */
public class VTGMessage {
    public static void parse (String[] tokens, NMEADecoder mf)
    {
        float track = Float.NaN;
        float speed = Float.NaN;
        if (tokens[2].equals ("T") && (tokens[1].length () > 0)) {
            track = Float.parseFloat (tokens[1]);  // true track degrees
        }
        if (tokens[6].equals ("M") && (tokens[5].length () > 0)) {
            speed = Float.parseFloat (tokens[5]);  // ground speed knots
        }
        mf.maybeReportOwnshipInfo (mf.reportTime, track, speed, Float.NaN);
    }
}
