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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * Holds the current ADS-B derived Metars etc for an airport from all sources.
 */
public class MetarRepo {
    private final static int MAXMETARS = 20;

    private double altSetting;
    public  double latitude;
    public  double longitude;
    public  int ceilingft;   // feet or -1 if unknown
    private int lastSeqno = Integer.MIN_VALUE;
    public  long ceilingms;  // timestamp (ms) or 0 if unknown
    private NNTreeMap<Integer,Metar> metarAges = new NNTreeMap<> ();
    public  NNTreeMap<String,TreeMap<Long,Metar>> metarTypes = new NNTreeMap<> ();

    public MetarRepo (double lat, double lon)
    {
        ceilingft = -1;
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
            oldmet = metarAges.nnremove (oldseqno);
            metarTypes.nnget (oldmet.type).remove (oldmet.time);
        }

        // if it contains altimeter setting, save it
        // likewise for ceiling
        if (newmet.type.equals ("METAR") || newmet.type.equals ("SPECI")) {
            long whents = 0;
            String[] parts = newmet.data.split (" ");
            for (String part : parts) {
                if (part.equals ("RMK")) break;

                if ((part.length () == 7) && part.endsWith ("Z")) {
                    try {
                        int ddhhmm = Integer.parseInt (part.substring (0, 6));
                        int metday = ddhhmm / 10000;
                        int methr  = (ddhhmm / 100) % 100;
                        int metmin = ddhhmm % 100;
                        GregorianCalendar gc = new GregorianCalendar (TimeZone.getTimeZone ("GMT"));
                        int metyear = gc.get (Calendar.YEAR);
                        int metmon  = gc.get (Calendar.MONTH);
                        int nowday  = gc.get (Calendar.DAY_OF_MONTH);
                        if (metday > nowday) {
                            if (-- metmon < Calendar.JANUARY) {
                                gc.set (Calendar.YEAR, -- metyear);
                                metmon = Calendar.DECEMBER;
                            }
                            gc.set (Calendar.MONTH, metmon);
                        }
                        gc.set (Calendar.DAY_OF_MONTH, metday);
                        gc.set (Calendar.HOUR, methr);
                        gc.set (Calendar.MINUTE, metmin);
                        gc.set (Calendar.SECOND, 0);
                        whents = gc.getTimeInMillis () / 1000 * 1000;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                }

                // altimeter setting
                if ((part.length () == 5) && (part.charAt (0) == 'A')) {
                    try {
                        int setting = Integer.parseInt (part.substring (1));
                        if ((setting > 2500) && (setting < 3500)) {
                            altSetting = setting / 100.0;
                        }
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                }

                // ceiling
                if ((part.length () == 6) && (part.startsWith ("BKN") || part.startsWith ("OVC"))) {
                    try {
                        ceilingft = Integer.parseInt (part.substring (3)) * 100;
                        ceilingms = whents;
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
    public static double getAltSetting (HashMap<String,MetarRepo> metarRepos, double lat, double lon)
    {
        double closestdistance = 100.0;
        double closestsetting  = 0.0;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (metarRepos) {
            for (MetarRepo repo : metarRepos.values ()) {
                if (repo.altSetting > 0.0) {
                    double dist = Lib.LatLonDist (repo.latitude, repo.longitude, lat, lon);
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
