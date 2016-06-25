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

import com.outerworldapps.wairtonow.MyGpsSatellite;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * http://www.gpsinformation.org/dale/nmea.htm#GSV
 * Gps Satellites in View
 */
public class GSVMessage {
    private LinkedList<MyGpsSatellite> satellites = new LinkedList<> ();
    private NMEADecoder nmeaDecoder;

    public GSVMessage (NMEADecoder nd)
    {
        nmeaDecoder = nd;
    }

    public void parse (String[] tokens)
    {
        String pfx = tokens[0].substring (0, 3);
        int lastMessage = Integer.parseInt (tokens[1]);
        int thisMessage = Integer.parseInt (tokens[2]);
        if (thisMessage == 1) {
            for (Iterator<MyGpsSatellite> it = satellites.iterator (); it.hasNext ();) {
                if (it.next ().pfx.equals (pfx)) it.remove ();
            }
        }
        for (int i = 4; i + 4 <= tokens.length;) {
            MyGpsSatellite sat = new MyGpsSatellite ();
            sat.pfx  = pfx;
            sat.prn  = Integer.parseInt (tokens[i++]);
            sat.elev = NMEADecoder.parseFloat (tokens[i++], Float.NaN, 1.0F);
            sat.azim = NMEADecoder.parseFloat (tokens[i++], Float.NaN, 1.0F);
            String snrstr = tokens[i++];
            if (snrstr.length () > 0) {
                sat.snr  = Float.parseFloat (snrstr);
                sat.used = true;
            }
            satellites.addLast (sat);
        }
        if (thisMessage == lastMessage) {
            nmeaDecoder.topDecoder.mReporter.adsbGpsSatellites (satellites);
        }
    }
}
