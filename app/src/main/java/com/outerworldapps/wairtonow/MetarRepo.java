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

import android.util.Log;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * Holds the current ADS-B derived Metars etc for an airport from all sources.
 */
public class MetarRepo {
    public final static String TAG = "WairToNow";
    private final static int MAXMETARS = 20;

    private double altSetting;
    public  double latitude;
    public  double longitude;
    public  float visibsm;   // visibility SM or NaN if unknown
    public  int ceilingft;   // feet or -1 if unknown
    private int lastSeqno = Integer.MIN_VALUE;
    public  long ceilvisms;  // timestamp (ms) or 0 if unknown
    private NNTreeMap<Integer,Metar> metarAges = new NNTreeMap<> ();
    public  NNTreeMap<String,TreeMap<Long,Metar>> metarTypes = new NNTreeMap<> ();

    public MetarRepo (double lat, double lon)
    {
        ceilingft = -1;
        latitude  = lat;
        longitude = lon;
        visibsm   = Float.NaN;
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
        if (newmet.type.equals ("METAR") || newmet.type.equals ("SPECI") || newmet.type.equals ("TAF")) {
            float newvisibsm = Float.MAX_VALUE;
            int lowestceilft = Integer.MAX_VALUE;
            long whents = 0;
            String[] parts = newmet.data.split ("\\s+");
            int nparts = parts.length;
            for (int ipart = 0; ipart < nparts; ipart ++) {
                String part = parts[ipart];
                // don't try to decode remarks
                if (part.equals ("RMK")) break;
                // TAFs begin with a METAR up to the first 'FROM' block
                if ((part.length () == 8) && part.startsWith ("FM")) break;

                // decode date/time of METAR observation
                if ((part.length () == 7) && part.endsWith ("Z")) {
                    try {
                        int ddhhmm = Integer.parseInt (part.substring (0, 6));
                        int metday = ddhhmm / 10000;
                        int methr  = (ddhhmm / 100) % 100;
                        int metmin = ddhhmm % 100;
                        GregorianCalendar gc = new GregorianCalendar (TimeZone.getTimeZone ("GMT"));
                        int nowday = gc.get (Calendar.DAY_OF_MONTH);
                        if ((metday > 20) && (nowday < 10)) {
                            gc.add (Calendar.MONTH, -1);
                        }
                        gc.set (Calendar.DAY_OF_MONTH, metday);
                        gc.set (Calendar.HOUR_OF_DAY, methr);
                        gc.set (Calendar.MINUTE, metmin);
                        gc.set (Calendar.SECOND, 0);
                        gc.set (Calendar.MILLISECOND, 0);
                        whents = gc.getTimeInMillis ();
                        continue;
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
                            continue;
                        }
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                    continue;
                }

                // ceiling
                if (part.startsWith ("BKN") || part.startsWith ("OVC")) {
                    try {
                        int ceilft = Integer.parseInt (part.substring (3)) * 100;
                        if (lowestceilft > ceilft) lowestceilft = ceilft;
                        continue;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                }
                if (part.startsWith ("VV")) {
                    try {
                        int ceilft = Integer.parseInt (part.substring (2)) * 100;
                        if (lowestceilft > ceilft) lowestceilft = ceilft;
                        continue;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                }

                // visibility  P6SM  10SM  1 1/2SM  1/4SM
                // 'M' on the front means less than, eg, M1/4SM
                if ((part.length () > 2) && part.endsWith ("SM")) {
                    String number = part.substring (0, part.length () - 2);
                    if (number.startsWith ("M")) number = number.substring (1);
                    if (number.startsWith ("P")) number = number.substring (1);
                    int i = number.indexOf ('/');
                    float val = Float.NaN;
                    try {
                        if (i < 0) val = Float.parseFloat (number);
                        else {
                            val = Float.parseFloat (number.substring (0, i)) / Float.parseFloat (number.substring (i + 1));
                            if (ipart > 0) {
                                int whole = Integer.parseInt (parts[ipart-1]);
                                if ((whole > 0) && (whole < 9)) val += whole;
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    if (! Float.isNaN (val)) newvisibsm = val;
                }
            }
            Log.d (TAG, "MetarRepo: when=" + Lib.TimeStringUTC (whents) + " ceil=" + lowestceilft + " visib=" + newvisibsm + " data=" + newmet.data);

            if (ceilvisms < whents) {
                ceilvisms = whents;
                ceilingft = lowestceilft;
                visibsm   = newvisibsm;
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
