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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * http://www.gpsinformation.org/dale/nmea.htm#GSV
 * Gps Satellites in View
 *
 * http://www.gpsinformation.org/dale/nmea.htm#GSA
 * Gps Satellites Active
 */
public class GSVMessage {
    private HashMap<String,HashSet<Integer>> activesatss = new HashMap<> ();
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

        // reset list for this prefix if first message
        if (thisMessage == 1) {
            for (Iterator<MyGpsSatellite> it = satellites.iterator (); it.hasNext ();) {
                if (it.next ().pfx.equals (pfx)) it.remove ();
            }
        }

        // see if a GSA message was received and what satellites it said were active
        HashSet<Integer> activesats = activesatss.get (pfx);

        // go through all the satellites mentioned in this GSV message
        // if we have never seen a GSA message, assume the satellite is used
        for (int i = 4; i + 4 <= tokens.length;) {
            MyGpsSatellite sat = new MyGpsSatellite ();
            sat.pfx  = pfx;
            sat.prn  = Integer.parseInt (tokens[i++]);
            sat.elev = NMEADecoder.parseDouble (tokens[i++], Double.NaN, 1.0);
            sat.azim = NMEADecoder.parseDouble (tokens[i++], Double.NaN, 1.0);
            sat.used = (activesats == null) || activesats.contains (sat.prn);
            String snrstr = tokens[i++];
            if (snrstr.length () > 0) {
                sat.snr = Double.parseDouble (snrstr);
            }
            satellites.addLast (sat);
        }

        // if we have all messages, update the on-screen status graphic
        if (thisMessage == lastMessage) {
            nmeaDecoder.topDecoder.mReporter.adsbGpsSatellites (satellites);
        }
    }

    // $GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39
    public void parsegsa (String[] tokens)
    {
        // make sure we have an activesats entry for this prefix
        String pfx = tokens[0].substring (0, 3);
        HashSet<Integer> activesats = activesatss.get (pfx);
        if (activesats == null) {
            activesats = new HashSet<> ();
            activesatss.put (pfx, activesats);
        }

        // get list of active PRNs for the prefix
        activesats.clear ();
        int ntoks = tokens.length;
        if (ntoks > 15) ntoks = 15;
        for (int i = 3; i < ntoks; i ++) {
            String tok = tokens[i];
            if (! tok.equals ("")) {
                int prn = Integer.parseInt (tok);
                activesats.add (prn);
            }
        }

        // mark all the known satellites as used or not used
        // note that we will only mark a max of 12 as used cuz that's all the GSA message can send
        boolean changed = false;
        for (MyGpsSatellite sat : satellites) {
            activesats = activesatss.get (sat.pfx);
            boolean used = (activesats != null) && activesats.contains (sat.prn);
            changed |= sat.used ^ used;
            sat.used = used;
        }

        // if any change, update satellite status graphic
        if (changed) {
            nmeaDecoder.topDecoder.mReporter.adsbGpsSatellites (satellites);
        }
    }
}
