//    Copyright (C) 2015, Mike Rieker, Beverly, MA USA
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

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Get waypoints within a lat/lon area.
 */
public class WaypointsWithin {
    private double westLon, eastLon, northLat, southLat;
    private LinkedList<Waypoint> found = new LinkedList<> ();
    private WairToNow wairToNow;

    public WaypointsWithin (WairToNow wtn)
    {
        wairToNow = wtn;
        clear ();
    }

    /**
     * Set up to (re-)read waypoints within boundaries (GetWaypointsWithin()).
     */
    public void clear ()
    {
        found.clear ();
        southLat =  1000.0;
        northLat = -1000.0;
        westLon  =  1000.0;
        eastLon  = -1000.0;
    }

    /**
     * Return list of all waypoints within the given latitude & longitude.
     * May include waypoints outside that range as well.
     */
    public Collection<Waypoint> Get (double sLat, double nLat, double wLon, double eLon)
    {
        wLon = Lib.NormalLon (wLon);
        eLon = Lib.NormalLon (eLon);

        // if new box is completely separate from old box, wipe out everything and start over
        boolean alloutside = (sLat > northLat) || (nLat < southLat);
        if (! alloutside) {
            boolean oldlonwraps = westLon > eastLon;
            boolean newlonwraps = wLon > eLon;
            if (! oldlonwraps && ! newlonwraps) {
                alloutside = (wLon > eastLon) || (eLon < westLon);
            } else if (! oldlonwraps || ! newlonwraps) {
                alloutside = (westLon > eLon) && (eastLon < wLon);
            }
        }
        if (alloutside) {
            clear ();
        }

        // if new box goes outside old box, re-scan database
        boolean goesoutside = alloutside || (sLat < southLat) || (nLat > northLat);
        if (! goesoutside) {
            boolean oldlonwraps = westLon > eastLon;
            boolean newlonwraps = wLon > eLon;
            if (oldlonwraps == newlonwraps) {
                goesoutside = (wLon < westLon) || (eLon > eastLon);
            } else if (newlonwraps) {
                // oldlonwraps = false; newlonwraps = true
                goesoutside = true;
            } else {
                // oldlonwraps = true; newlonwraps = false
                goesoutside = (eLon > eastLon) && (wLon < westLon);
            }
        }
        if (goesoutside) {

            // expand borders a little so we don't read each time for small changes
            sLat -= 3.0 / 64.0;
            nLat += 3.0 / 64.0;
            wLon = Lib.NormalLon (wLon - 3.0 / 64.0);
            eLon = Lib.NormalLon (eLon + 3.0 / 64.0);

            // re-scanning database, get rid of all previous points
            found.clear ();

            // save exact limits of what we are scanning for
            southLat = sLat;
            northLat = nLat;
            westLon  = wLon;
            eastLon  = eLon;

            for (SQLiteDBs sqldb : wairToNow.maintView.getWaypointDBs ()) {
                try {
                    for (Class<? extends Waypoint> wpclass : Waypoint.wpclasses) {
                        String dbtable = (String) wpclass.getField ("dbtable").get (null);
                        String keyid = (String) wpclass.getField ("dbkeyid").get (null);
                        String[] dbcols = (String[]) wpclass.getField ("dbcols").get (null);
                        Constructor<? extends Waypoint> ctor = wpclass.getConstructor (Cursor.class, DBase.class, WairToNow.class);

                        assert keyid != null;
                        String prefix = keyid.substring (0, 4);

                        String where =
                                prefix + "lat>=" + sLat + " AND " +
                                prefix + "lat<=" + nLat + " AND (" +
                                prefix + "lon>=" + wLon +
                                ((wLon < eLon) ? " AND " : " OR ") +
                                prefix + "lon<=" + eLon + ')';

                        Cursor result = sqldb.query (
                                dbtable, dbcols, where,
                                null, null, null, null, null);
                        try {
                            if (result.moveToFirst ()) do {
                                Waypoint wp = ctor.newInstance (result, (DBase) sqldb.dbaux, wairToNow);
                                found.addLast (wp);
                            } while (result.moveToNext ());
                        } finally {
                            result.close ();
                        }
                    }
                } catch (Exception e) {
                    Log.e (Waypoint.TAG, "error reading " + sqldb.mydbname, e);
                }
            }
        }
        return found;
    }
}
