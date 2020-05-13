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
import android.util.SparseArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Contains topography info (ground elevation at a given lat/lon).
 */
public class Topography {
    public final static String TAG = "WairToNow";
    public final static short INVALID_ELEV = (short) -0x8000;

    private final static SparseArray<short[]> loadedTopos = new SparseArray<> ();

    private static String topoZipName;
    private static TopoZipFile topoZipFile;

    public static void purge () {
        synchronized (loadedTopos) {
            loadedTopos.clear ();
            if (topoZipFile != null) {
                try { topoZipFile.close (); } catch (IOException ioe) { Lib.Ignored (); }
                topoZipFile = null;
                topoZipName = null;
            }
        }
    }

    /**
     * Get elevation for a given lat/lon.
     * Synchronous, may take a moment to complete.
     *
     * @param lat = latitude, rounded to nearest minute
     * @param lon = longitude, rounded to nearest minute
     * @return INVALID_ELEV: elevation unknown; else: elevation in metres MSL
     */
    public static short getElevMetres (double lat, double lon) {
        /*
         * Split given lat,lon into degrees and minutes.
         */
        int ilatmin = (int) Math.round (lat * 60.0) + 60000;
        int ilonmin = (int) Math.round (lon * 60.0) + 60000;
        int ilatdeg = ilatmin / 60 - 1000;
        int ilondeg = ilonmin / 60 - 1000;

        ilatmin %= 60;
        ilonmin %= 60;

        if (ilatmin < 0) { ilatmin += 60; -- ilatdeg; }
        if (ilonmin < 0) { ilonmin += 60; -- ilondeg; }

        if (ilatdeg >= 90) return INVALID_ELEV;
        if (ilatdeg < -90) return INVALID_ELEV;

        if (ilondeg < -180) ilondeg += 360;
        if (ilondeg >= 180) ilondeg -= 360;

        /*
         * See if corresponding file is already loaded in memory.
         * If not, read it in and save on list.
         */
        int key = (ilatdeg << 16) + (ilondeg & 0xFFFF);
        short[] topos;
        synchronized (loadedTopos) {
            int i = loadedTopos.indexOfKey (key);
            if (i >= 0) {
                topos = loadedTopos.valueAt (i);
            } else {
                topos = ReadFile (ilatdeg, ilondeg);
                loadedTopos.put (key, topos);
            }
        }

        /*
         * File corrupt.
         */
        if (topos == null) return INVALID_ELEV;

        /*
         * Loaded in memory, return value.
         */
        return topos[ilatmin*60+ilonmin];
    }

    /**
     * Read topo data from Zip file.
     * @param ilatdeg = latitude degree of topo data
     * @param ilondeg = longitude degree of topo data
     * @return array of shorts giving elevation (metres MSL)
     *         ...indexed by minutelatitude*60+minutelongitude
     */
    private static short[] ReadFile (int ilatdeg, int ilondeg)
    {
        /*
         * Open corresponding topo/ilatdeg.zip file if not already.
         */
        String name = WairToNow.dbdir + "/datums/topo/" + ilatdeg + ".zip";
        if (! name.equals (topoZipName)) {

            // close currently open zip file
            if (topoZipFile != null) {
                try {
                    topoZipFile.close ();
                } catch (IOException ioe) {
                    Lib.Ignored ();
                }
                topoZipFile = null;
                topoZipName = null;
            }

            // null files are for areas not covered by air charts
            // so just return INVALID_ELEV
            File file = new File (name);
            if (! file.exists () || (file.length () == 0)) return null;

            // otherwise, open the zip file
            try {
                RandomAccessFile raf = new RandomAccessFile (name, "r");
                topoZipFile = new TopoZipFile (raf, ilatdeg);
                topoZipName = name;
            } catch (IOException ioe) {
                Log.e (TAG, "error opening " + name, ioe);
                topoZipFile = null;
                topoZipName = null;
                return null;
            }
            Log.i (TAG, "opened " + name);
        }

        /*
         * Zip file opened, read requested entry into memory.
         */
        try {
            byte[] bytes = topoZipFile.getBytes (ilondeg);
            if (bytes == null) throw new FileNotFoundException ();
            int rc = bytes.length;
            if (rc != 7200) {
                throw new IOException ("only read " + rc + " of 7200 bytes");
            }
            short[] shorts = new short[3600];
            int j = 0;
            for (int i = 0; i < 3600; i ++) {
                int lo = bytes[j++] & 0xFF;
                int hi = bytes[j++] & 0xFF;
                shorts[i] = (short) ((hi << 8) + lo);
            }
            return shorts;
        } catch (Exception e) {
            Log.e (TAG, "error reading topo " + ilatdeg + "/" + ilondeg, e);
            return null;
        }
    }
}
