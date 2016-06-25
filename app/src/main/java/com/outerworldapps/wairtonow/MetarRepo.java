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
 * Holds the current ADS-B derived Metars etc for an airport from all sources.
 */
public class MetarRepo {
    private final static int MAXMETARS = 20;

    private float altSetting;
    private float latitude;
    private float longitude;
    private int lastSeqno = Integer.MIN_VALUE;
    private TreeMap<Integer,Metar> metarAges = new TreeMap<> ();
    public  TreeMap<String,TreeMap<Long,Metar>> metarTypes = new TreeMap<> ();

    public MetarRepo (float lat, float lon)
    {
        latitude  = lat;
        longitude = lon;
    }

    /**
     * Insert new metar into the repo.
     * ** CALLER MUST HAVE the repo LOCKED **
     */
    public void insertNewMetar (Metar newmet)
    {
        newmet.seqno = ++ lastSeqno;

        // insert on lists
        TreeMap<Long,Metar> types = metarTypes.get (newmet.type);
        if (types == null) {
            types = new TreeMap<> ();
            metarTypes.put (newmet.type, types);
        }
        Metar oldmet = types.remove (newmet.time);
        if (oldmet != null) metarAges.remove (oldmet.seqno);
        types.put (newmet.time, newmet);
        metarAges.put (newmet.seqno, newmet);

        // delete oldest metar overall to make room if we now have too many
        while (metarAges.size () > MAXMETARS) {
            int oldseqno = metarAges.keySet ().iterator ().next ();
            oldmet = metarAges.remove (oldseqno);
            metarTypes.get (oldmet.type).remove (oldmet.time);
        }

        // if it contains altimeter setting, save it
        if (newmet.type.equals ("METAR") || newmet.type.equals ("SPECI")) {
            String[] parts = newmet.data.split (" ");
            for (String part : parts) {
                if (part.equals ("RMK")) break;
                if ((part.length () == 5) && (part.charAt (0) == 'A')) {
                    try {
                        int setting = Integer.parseInt (part.substring (1));
                        if ((setting > 2500) && (setting < 3500)) {
                            altSetting = setting / 100.0F;
                        }
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                }
            }
        }
    }

    /**
     * Find closest altimeter setting to the given point.
     * @param metarRepos = wairToNow.metarRepos
     * @param lat = leatitude of aircraft
     * @param lon = longitude of aircraft
     * @return altimeter setting (or 0 if unknown)
     */
    public static float getAltSetting (HashMap<String,MetarRepo> metarRepos, float lat, float lon)
    {
        float closestdistance = 100.0F;
        float closestsetting  = 0.0F;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (metarRepos) {
            for (MetarRepo repo : metarRepos.values ()) {
                if (repo.altSetting > 0.0F) {
                    float dist = Lib.LatLonDist (repo.latitude, repo.longitude, lat, lon);
                    if (closestdistance > dist) {
                        closestdistance = dist;
                        closestsetting  = repo.altSetting;
                    }
                }
            }
        }
        return closestsetting;
    }
}
