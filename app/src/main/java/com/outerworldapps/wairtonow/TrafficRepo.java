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

package com.outerworldapps.wairtonow;

import java.util.HashMap;
import java.util.TreeMap;

/**
 * Holds the current Traffic from all sources.
 */
public class TrafficRepo {
    private final static int MAXIMAGES = 1000;

    public  volatile boolean amEmpty;
    public  HashMap<Integer,Traffic> trafficAddr = new HashMap<> ();
    public  TreeMap<Integer,Traffic> trafficAges = new TreeMap<> ();

    private int lastSeqno = Integer.MIN_VALUE;

    /**
     * Purge the given addresses from the repo.
     * ** CALLER MUST HAVE the repo LOCKED **
     */
    public void deleteAddresses (int naddrs, int[] addrs)
    {
        HashMap<Integer,Traffic> hm = trafficAddr;

        for (int i = 0; i < naddrs; i ++) {
            int addr = addrs[i];
            if (hm.containsKey (addr)) {
                Traffic tr = hm.get (addr);
                hm.remove (addr);
                trafficAges.remove (tr.seqno);
            }
        }

        amEmpty = trafficAges.isEmpty ();
    }

    /**
     * Insert new traffic into the repo.
     * ** CALLER MUST HAVE the repo LOCKED **
     */
    public void insertNewTraffic (Traffic newtr)
    {
        newtr.seqno = ++ lastSeqno;

        // update old traffic for same address
        Traffic oldtr = trafficAddr.get (newtr.address);
        if (oldtr != null) {
            trafficAges.remove (oldtr.seqno);
            trafficAddr.remove (oldtr.address);
            oldtr.updateFrom (newtr);
            newtr = oldtr;
        }

        // delete oldest traffic overall to make room if we have too many
        while (trafficAges.size () >= MAXIMAGES) {
            int oldseqno = trafficAges.keySet ().iterator ().next ();
            oldtr = trafficAges.remove (oldseqno);
            trafficAddr.remove (oldtr.address);
        }

        // insert on lists
        trafficAddr.put (newtr.address, newtr);
        trafficAges.put (newtr.seqno, newtr);

        amEmpty = false;
    }
}
