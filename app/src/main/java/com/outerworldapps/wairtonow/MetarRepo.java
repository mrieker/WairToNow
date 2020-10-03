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

import java.util.HashMap;

/**
 * Holds the current ADS-B or Web derived Metars etc for an airport.
 */
public class MetarRepo {
    public final static String TAG = "WairToNow";

    private double altSetting;
    public  double latitude;
    public  double longitude;
    public  float  visibsm;     // visibility SM or NaN if unknown
    public  int    ceilingft;   // feet or -1 if unknown
    public  long   ceilvisms;   // timestamp (ms) or 0 if unknown
    public  NNTreeMap<String,NNTreeMap<Long,Metar>> metarTypes = new NNTreeMap<> ();

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
        // treat SPECIs just like METARs so they get sorted with each other
        String type = newmet.type;
        if (type.equals ("SPECI")) type = "METAR";

        // insert on lists
        NNTreeMap<Long,Metar> types = metarTypes.get (type);
        if (types == null) {
            types = new NNTreeMap<> ();
            metarTypes.put (type, types);
        }
        types.put (newmet.time, newmet);

        switch (type) {

            // allow only 3 METARs
            case "METAR": {
                while (types.size () > 3) {
                    Long oldtime = types.keySet ().iterator ().next ();
                    types.remove (oldtime);
                }
                break;
            }

            // allow only 1 TAF
            case "TAF": {
                while (types.size () > 1) {
                    Long oldtime = types.keySet ().iterator ().next ();
                    types.remove (oldtime);
                }
                break;
            }
        }

        // if it contains altimeter setting, save it
        // likewise for ceiling and visibility
        if ((ceilvisms < newmet.time) && (type.equals ("METAR") || type.equals ("TAF"))) {
            float newvisibsm = Float.MAX_VALUE;
            int newceilft = Integer.MAX_VALUE;
            String[] words = newmet.words;
            int nwords = words.length;
            for (int iword = 0; iword < nwords; iword ++) {
                String word = words[iword];
                // don't try to decode remarks
                if (word.equals ("RMK")) break;
                // TAFs begin with a METAR up to the first 'FROM' block
                if ((word.length () == 8) && word.startsWith ("FM")) break;

                // altimeter setting
                if ((word.length () == 5) && (word.charAt (0) == 'A')) {
                    try {
                        int setting = Integer.parseInt (word.substring (1));
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
                if (word.startsWith ("BKN") || word.startsWith ("OVC")) {
                    try {
                        int ceilft = Integer.parseInt (word.substring (3)) * 100;
                        if (newceilft > ceilft) newceilft = ceilft;
                        continue;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                }
                if (word.startsWith ("VV")) {
                    try {
                        int ceilft = Integer.parseInt (word.substring (2)) * 100;
                        if (newceilft > ceilft) newceilft = ceilft;
                        continue;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                }

                // visibility  P6SM  10SM  1 1/2SM  1/4SM
                // 'M' on the front means less than, eg, M1/4SM
                if ((word.length () > 2) && word.endsWith ("SM")) {
                    String number = word.substring (0, word.length () - 2);
                    if (number.startsWith ("M")) number = number.substring (1);
                    if (number.startsWith ("P")) number = number.substring (1);
                    int i = number.indexOf ('/');
                    float val = Float.NaN;
                    try {
                        if (i < 0) val = Float.parseFloat (number);
                        else {
                            val = Float.parseFloat (number.substring (0, i)) / Float.parseFloat (number.substring (i + 1));
                            if (iword > 0) {
                                int whole = Integer.parseInt (words[iword-1]);
                                if ((whole > 0) && (whole < 9)) val += whole;
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    if (! Float.isNaN (val)) newvisibsm = val;
                }
            }

            Log.d (TAG, "MetarRepo: when=" + Lib.TimeStringUTC (newmet.time) + " ceil=" + newceilft + " visib=" + newvisibsm + " data=" + newmet.data);
            ceilvisms = newmet.time;
            ceilingft = newceilft;
            visibsm   = newvisibsm;
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
        double total = 0.0;
        double setting  = 0.0;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (metarRepos) {
            for (MetarRepo repo : metarRepos.values ()) {
                if (repo.altSetting > 0.0) {
                    double dist = Lib.LatLonDist (repo.latitude, repo.longitude, lat, lon);
                    if (dist <= 0.5) return repo.altSetting;
                    if (dist <= 100.0) {
                        total += 1.0 / dist;
                        setting += repo.altSetting / dist;
                    }
                }
            }
        }
        if (setting == 0.0) return 0.0;
        return setting / total;
    }
}
