//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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

import android.database.Cursor;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Check for obstacle and terrain collisions.
 * Scans the database every 10 seconds based on current aircraft position, heading and climb rate.
 * Chart2D then reads the collision points whenever it wants to build the display.
 */
public class CollDetThread extends Thread {
    public final static String TAG = "WairToNow";

    private final static int minsecswarn = 10;
    private final static int maxsecswarn = 90;
    private final static int sweepdegs   = 30;
    private final static int runwaydegs  = 15;
    private final static int padding_met = 10;
    private final static int nearbyaptnm = 2;
    private final static int minrwylenft = 1500;

    private static class Cache {
        int cycle;      // cycle last used
        int elevmet;    // elevation metres
        boolean rwy;    // has a runway
    }

    private int nearbyaptilatmin;
    private int nearbyaptilonmin;
    private int[] badllmins = new int[0];
    private LinkedList<LatLon> nearbyaptlatlons;
    private SQLiteDBs obssqldb;
    private WairToNow wairToNow;

    private final static String[] ob_cols = new String[] { "ob_msl" };
    private final static String[] rwy_cols = new String[] { "rwy_beglat", "rwy_beglon", "rwy_endlat", "rwy_endlon" };

    public CollDetThread (WairToNow wtn)
    {
        setName ("CollDetThread");
        setPriority (MIN_PRIORITY);
        nearbyaptlatlons = new LinkedList<> ();
        wairToNow = wtn;
        start ();
    }

    @Override  // Thread
    public void run ()
    {
        try {
            double oldalt = 0.0;
            double oldlat = 0.0;
            double oldlon = 0.0;
            long oldtime  = 0;

            // latlonmin -> max(obstrs,topo) metres
            HashMap<Integer,Cache> cache = new HashMap<> ();
            int cycle = 0;

            // latlonmin found to be collided
            HashSet<Integer> newbadlls = new HashSet<> ();

            //noinspection InfiniteLoopStatement
            while (true) {
                Thread.sleep (4096 - System.currentTimeMillis () % 4096);

                // build new hash set
                newbadlls.clear ();

                // get current position
                double curlat  = wairToNow.currentGPSLat;
                double curlon  = wairToNow.currentGPSLon;
                double curalt  = wairToNow.currentGPSAlt;
                long   curtime = wairToNow.currentGPSTime;
                double spdmps  = wairToNow.currentGPSSpd;
                double hdgdeg  = wairToNow.currentGPSHdg;

                // we must be going a particular minimum speed
                // and be enabled on the options page
                if ((oldtime > 0) && (spdmps > WairToNow.gpsMinSpeedMPS) &&
                        wairToNow.optionsView.collDetOption.checkBox.isChecked ()) {

                    // new cycle for cache entries
                    cycle ++;

                    // try to open obstructions if not open
                    // ie, maybe just downloaded for first time
                    if (obssqldb == null) {
                        int expdate = wairToNow.maintView.GetCurentObstructionExpDate ();
                        if (expdate > 0) {
                            obssqldb = SQLiteDBs.open ("nobudb/obstructions_" + expdate + ".db");
                        }
                    }

                    if (! nearbyairport (curlat, curlon)) {

                        // calculate how far we can fly in the maxsecswarn period from now
                        double maxnm = spdmps * Lib.KtPerMPS * maxsecswarn / 3600.0;

                        // get current climb rate (metres per nm)
                        double climbrt = (curalt - oldalt) / Lib.LatLonDist (curlat, curlon, oldlat, oldlon);

                        // sweep through each arc at 0.5nm steps
                        double minnm = spdmps * Lib.KtPerMPS * minsecswarn / 3600.0;
                    nmloop:
                        for (double nm = minnm - 0.25; nm < maxnm + 0.25; nm += 0.5) {
                            double alt_met = curalt + climbrt * nm;     // altitude (metres) for this arc

                            // sweep nm-radius arc from left to right
                            double step_deg = Math.toDegrees (0.5 / nm);  // degrees to step 0.5nm
                            int nsteps = (int) Math.ceil (sweepdegs / step_deg);
                            for (int step = -nsteps; step <= nsteps; step++) {

                                // round lat,lon to nearest minute
                                double deltadeg = step_deg * step;
                                double deg = hdgdeg + deltadeg;
                                double lat = Lib.LatHdgDist2Lat (curlat, deg, nm);
                                double lon = Lib.LatLonHdgDist2Lon (curlat, curlon, deg, nm);
                                int llmin = (((int) Math.round (lat * 60.0)) << 16) | (((int) Math.round (lon * 60.0)) & 0xFFFF);

                                // make sure there is a cache entry for that lat,lon
                                Cache ce = cache.get (llmin);
                                if (ce == null) {
                                    ce = readCacheEntry (llmin);
                                    cache.put (llmin, ce);
                                }
                                ce.cycle = cycle;

                                // see if there is an obstruction in the way
                                if (alt_met < ce.elevmet + padding_met) {
                                    newbadlls.add (llmin);

                                    // if colliding with a runway, assume it is a normal landing
                                    if (ce.rwy && (Math.abs (deltadeg) < runwaydegs)) {
                                        newbadlls.clear ();
                                        break nmloop;
                                    }
                                }
                            }
                        }
                    }
                }

                // make new points available
                int newsize = newbadlls.size ();
                if (newsize == badllmins.length) {
                    int i = 0;
                    for (Integer llmin : newbadlls) {
                        badllmins[i++] = llmin;
                    }
                } else {
                    int[] newarr = new int[newsize];
                    int i = 0;
                    for (Integer llmin : newbadlls) {
                        newarr[i++] = llmin;
                    }
                    badllmins = newarr;
                }

                // save old position so we can measure climb rate
                oldtime = curtime;
                oldlat  = curlat;
                oldlon  = curlon;
                oldalt  = curalt;

                // remove old unused cache entries
                for (Iterator<Cache> it = cache.values ().iterator (); it.hasNext ();) {
                    Cache ce = it.next ();
                    if (ce.cycle < cycle - 5) it.remove ();
                }
            }
        } catch (Exception e) {
            Log.e (TAG, "exception in ColDetThread", e);
        } finally {
            SQLiteDBs.CloseAll ();
            badllmins = new int[0];
        }
    }

    public int[] getBadLLMins ()
    {
        return badllmins;
    }

    // see if there is an airport nearby
    private boolean nearbyairport (double curlat, double curlon)
    {
        int ilatmin = (int) Math.round (curlat * 60.0);
        int ilonmin = (int) Math.round (curlon * 60.0);

        if ((nearbyaptilatmin != ilatmin) || (nearbyaptilonmin != ilonmin)) {
            nearbyaptilatmin = ilatmin;
            nearbyaptilonmin = ilonmin;
            nearbyaptlatlons.clear ();
            double lolat = ilatmin / 60.0 - (nearbyaptnm + 2) / Lib.NMPerDeg;
            double hilat = ilatmin / 60.0 + (nearbyaptnm + 2) / Lib.NMPerDeg;
            double dlon  = (nearbyaptnm + 2) / Lib.NMPerDeg / Math.cos (Math.toRadians (curlat));
            double lolon = ilonmin / 60.0 - dlon;
            double hilon = ilonmin / 60.0 + dlon;
            for (SQLiteDBs wptsqldb : wairToNow.maintView.getWaypointDBs ()) {
                Cursor result = wptsqldb.query ("runways", rwy_cols,
                        "rwy_beglat>=" + lolat + " AND rwy_beglat<=" + hilat + " AND rwy_beglon>=" +
                                lolon + " AND rwy_beglon<=" + hilon, null, null, null, null, null);
                try {
                    if (result.moveToFirst ()) do {
                        double beglat = result.getDouble (0);
                        double beglon = result.getDouble (1);
                        double endlat = result.getDouble (2);
                        double endlon = result.getDouble (3);
                        double lennm  = Lib.LatLonTC (beglat, beglon, endlat, endlon);
                        if (lennm >= minrwylenft / Lib.FtPerNM) {
                            LatLon ll = new LatLon ();
                            ll.lat = beglat;
                            ll.lon = beglon;
                            nearbyaptlatlons.add (ll);
                        }
                    } while (result.moveToNext ());
                } finally {
                    result.close ();
                }
            }
        }

        // see if there is a good sized runway within nearbyaptnm of here
        for (LatLon ll : nearbyaptlatlons) {
            double distnm = Lib.LatLonDist (ll.lat, ll.lon, curlat, curlon);
            if (distnm <= nearbyaptnm) {
                return true;
            }
        }
        return false;
    }

    // get highest of topography and obstructions within 0.5 mins of given lat,lon
    //  input:
    //   llmin<31:16> = latitude minutes
    //   llmin<15:00> = longitude minutes
    //  output:
    //   returns highest (metres)
    private Cache readCacheEntry (int llmin)
    {
        double lat = (llmin >> 16) / 60.0;
        double lon = ((short) llmin) / 60.0;

        // see if there is topography in the way
        int elev_met = Topography.getElevMetres (lat, lon);

        // see if there are any obstructions higher than that
        if (obssqldb != null) {
            double minlat = lat - 0.5 / 60.0;
            double maxlat = lat + 0.5 / 60.0;
            double minlon = lon - 0.5 / 60.0;
            double maxlon = lon + 0.5 / 60.0;
            int minmsl = (int) Math.round (elev_met * Lib.FtPerM);
            Cursor result = obssqldb.query ("obstrs", ob_cols,
                    "ob_lat>=" + minlat + " AND ob_lat<=" + maxlat + " AND ob_lon>=" +
                            minlon + " AND ob_lon<=" + maxlon + " AND ob_msl>" + minmsl,
                    null, null, null, null, null);
            try {
                if (result.moveToFirst ()) {
                    do {
                        int msl_met = (int) Math.round (result.getInt (0) / Lib.FtPerM);
                        if (elev_met < msl_met) elev_met = msl_met;
                    } while (result.moveToNext ());
                }
            } finally {
                result.close ();
            }
        }

        Cache ce = new Cache ();
        ce.elevmet = elev_met;

        // see if it is a runway
        double minlat = Lib.LatHdgDist2Lat (lat, 180.0, 1.0);
        double maxlat = Lib.LatHdgDist2Lat (lat,   0.0, 1.0);
        double minlon = Lib.LatLonHdgDist2Lon (lat, lon, -90.0, 1.0);
        double maxlon = Lib.LatLonHdgDist2Lon (lat, lon,  90.0, 1.0);
        for (SQLiteDBs wptsqldb : wairToNow.maintView.getWaypointDBs ()) {
            Cursor result = wptsqldb.query ("runways", rwy_cols,
                    "rwy_beglat>" + minlat + " AND rwy_beglat<" + maxlat +
                    " AND rwy_beglon>" + minlon + " AND rwy_beglon<" + maxlon,
                    null, null, null, null, null);
            try {
                if (result.moveToFirst ()) do {
                    double beglat = result.getDouble (0);
                    double beglon = result.getDouble (1);
                    double endlat = result.getDouble (2);
                    double endlon = result.getDouble (3);
                    double lennm  = Lib.LatLonTC (beglat, beglon, endlat, endlon);
                    if (lennm >= minrwylenft / Lib.FtPerNM) {
                        ce.rwy = true;
                        break;
                    }
                } while (result.moveToNext ());
                ce.rwy = result.moveToFirst ();
            } finally {
                result.close ();
            }
        }

        return ce;
    }
}
