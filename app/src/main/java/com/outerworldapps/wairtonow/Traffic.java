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

import android.graphics.PointF;

/**
 * Traffic report received from ADS-B.
 */
public class Traffic {
    public final static float MINTRAFAGLM = 300.0F / Lib.FtPerM;

    private final static char[] hdgarrows = {
            //'\u2B61', '\u2B67', '\u2B62', '\u2B68',
            //'\u2B63', '\u2B69', '\u2B60', '\u2B66'
            //'\u21D1', '\u21D7', '\u21D2', '\u21D8',
            //'\u21D3', '\u21D9', '\u21D0', '\u21D6'
            '\u2191', '\u2197', '\u2192', '\u2198',
            '\u2193', '\u2199', '\u2190', '\u2196'
    };

    public long time;           // ms since 1970-01-01 00:00 UTC'
    public float taltitude;     // TRUE altitude metres (or NaN)
    public float heading;       // heading degrees (or NaN)
    public float latitude;      // latitude degrees
    public float longitude;     // longitude degrees
    public float speed;         // speed metres per second (or NaN)
    public float climb;         // climb feet per minute (or NaN)
    public int address;         // <26:24>=addr type; <23:00>=ICAO address
    public String callsign;     // call sign
    public String squawk;       // transponder code
    public int seqno;           // reception sequence number

    public float distaway;
    public PointF canpix;

    private float lastDistNM = -1.0F;
    private int lastHdgIdx = -1;
    private String[] text;

    /**
     * Just got an update for this same traffic.
     * Save updated values.
     * @param nt = updated values
     */
    public void updateFrom (Traffic nt)
    {
        time = nt.time;
        taltitude = nt.taltitude;
        heading = nt.heading;
        latitude = nt.latitude;
        longitude = nt.longitude;
        speed = nt.speed;
        climb = nt.climb;
        address = nt.address;
        if ((nt.callsign != null) && (nt.callsign.length () > 0)) {
            callsign = nt.callsign;
        }
        if ((nt.squawk != null) && (nt.squawk.length () > 0)) {
            squawk = nt.squawk;
        }
        seqno = nt.seqno;

        text = null;
    }

    /**
     * Get text string to be displayed on the screen.
     */
    public String[] getText (WairToNow wairToNow, float canuphdg)
    {
        // if distance changed, update text
        float distnm = Lib.LatLonDist (latitude, longitude,
                wairToNow.currentGPSLat, wairToNow.currentGPSLon);
        if (Math.abs (lastDistNM - distnm) > 0.01F) {
            lastDistNM = distnm;
            text = null;
        }

        // get which arrow should be used to indicate relative heading
        // if different than last time, rebuild string
        int hdgidx = Float.isNaN (heading) ? -1 :
                Math.round ((heading - canuphdg) / 45.0F) & 7;
        if (lastHdgIdx != hdgidx) {
            lastHdgIdx = hdgidx;
            text = null;
        }

        // rebuild string if necessary
        if (text == null) {
            StringBuilder sb = new StringBuilder ();

            // true altitude / climb
            if (!Float.isNaN (taltitude)) {

                // display altitude
                int talt = Math.round (taltitude * Lib.FtPerM);
                sb.append (talt);
                sb.append (" ft");

                // also indicate climbing or descending
                if (!Float.isNaN (climb)) {
                    int clmb = Math.round (climb / 128.0F);
                    if (clmb < 0) {
                        sb.append (" \u25BC");  // down arrow
                    }
                    if (clmb > 0) {
                        sb.append (" \u25B2");  // up arrow
                    }
                }
                sb.append ('\n');
            }

            // distance and heading arrow
            boolean mphOption = wairToNow.optionsView.ktsMphOption.getAlt ();
            sb.append (Lib.DistString (lastDistNM, mphOption));
            if (lastHdgIdx >= 0) sb.append (hdgarrows[lastHdgIdx]);

            text = sb.toString ().split ("\n");
        }
        return text;
    }
}
