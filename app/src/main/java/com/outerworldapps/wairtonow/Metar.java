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

import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * ADS-B derived METARs, etc.
 */
public class Metar {
    public final long time;
    public final String type;
    public final String data;
    public final String[] words;

    public Metar (long ti, String ty, String da)
    {
        words = da.split ("\\s+");

        switch (ty) {
            // set time to ddhhmmZ in the METAR/SPECI/TAF
            case "METAR":
            case "SPECI":
            case "TAF": {
                for (String word : words) {
                    // don't parse the remarks
                    if (word.equals ("RMK")) break;
                    // TAFs begin with a METAR up to the first 'FROM' block FMddhhmm
                    if ((word.length () == 8) && word.startsWith ("FM")) break;
                    // check for ddhhmmZ
                    if ((word.length () == 7) && (word.charAt (6) == 'Z')) {
                        try {
                            ti = parseMetarTime (word);
                            break;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                break;
            }
        }

        time = ti;
        type = ty;
        data = da;
    }

    // convert given ddhhmmZ string to millisecond time
    //  input:
    //   ddhhmmz = time in form ddhhmmZ
    //  output:
    //   returns millisecond time
    private static long parseMetarTime (String ddhhmmz)
    {
        GregorianCalendar gcal = new GregorianCalendar (Lib.tzUtc, Locale.US);
        int dd = Integer.parseInt (ddhhmmz.substring (0, 2));
        int hh = Integer.parseInt (ddhhmmz.substring (2, 4));
        int mm = Integer.parseInt (ddhhmmz.substring (4, 6));
        if (ddhhmmz.charAt (6) != 'Z') throw new NumberFormatException ("bad metar time " + ddhhmmz);
        if ((gcal.get (GregorianCalendar.DAY_OF_MONTH) > 20) && (dd < 10)) {
            gcal.add (GregorianCalendar.MONTH, 1);
        }
        gcal.set (GregorianCalendar.DAY_OF_MONTH, dd);
        gcal.set (GregorianCalendar.HOUR_OF_DAY, hh);
        gcal.set (GregorianCalendar.MINUTE, mm);
        gcal.set (GregorianCalendar.SECOND, 0);
        gcal.set (GregorianCalendar.MILLISECOND, 0);
        return gcal.getTimeInMillis ();
    }
}
