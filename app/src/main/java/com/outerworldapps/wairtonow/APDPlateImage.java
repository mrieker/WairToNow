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

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.Log;

/**
 * Display the APD plate image file.
 */
@SuppressLint("ViewConstructor")
public class APDPlateImage extends GRPlateImage {
    private Bitmap  bitmap;    // single plate page
    private boolean full;
    private long    plateLoadedUptime;
    private String  filename;  // name of gif on flash, without ".p<pageno>" suffix

    private final static String[] columns_apdgeorefs1 = new String[] { "gr_wfta", "gr_wftb", "gr_wftc", "gr_wftd", "gr_wfte", "gr_wftf" };

    public APDPlateImage (WairToNow wtn, Waypoint.Airport apt, String pid, int exp, String fnm, boolean ful)
    {
        super (wtn, apt, pid, exp);
        filename = fnm;
        full     = ful;
        plateLoadedUptime = SystemClock.uptimeMillis ();

        /*
         * Get georeference points downloaded from server.
         */
        String dbname = "nobudb/plates_" + expdate + ".db";
        try {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            Cursor result = sqldb.query (
                    "apdgeorefs", columns_apdgeorefs1,
                    "gr_icaoid=?", new String[] { airport.ident },
                    null, null, null, null);
            try {
                if (result.moveToFirst ()) {
                    xfmwfta = result.getDouble (0);
                    xfmwftb = result.getDouble (1);
                    xfmwftc = result.getDouble (2);
                    xfmwftd = result.getDouble (3);
                    xfmwfte = result.getDouble (4);
                    xfmwftf = result.getDouble (5);
                }
            } finally {
                result.close ();
            }
        } catch (Exception e) {
            Log.e (TAG, "error reading " + dbname, e);
        }
    }

    /**
     * Close any open bitmaps so we don't take up too much memory.
     */
    public void CloseBitmaps ()
    {
        if (bitmap != null) {
            bitmap.recycle ();
            bitmap = null;
        }
    }

    protected void GotMouseDown (double x, double y) { }
    protected void GotMouseUp (double x, double y) { }

    /**
     * Draw the plate image and any other marks on it we want.
     */
    @Override  // View
    public void onDraw (Canvas canvas)
    {
        /*
         * Get single page's bitmap.
         */
        if (bitmap == null) {
            bitmap = OpenFirstPage (filename);
            if (bitmap == null) return;
        }

        /*
         * Display bitmap to canvas.
         */
        ShowSinglePage (canvas, bitmap);

        /*
         * Draw airplane if we have enough georeference info.
         * Also draw runway centerlines on airport diagrams
         * so user can easily verify georeference info.
         */
        if (DrawLocationArrow (canvas, 0.0, true) && (SystemClock.uptimeMillis () - plateLoadedUptime < 2000)) {
            DrawRunwayCenterlines (canvas);
        }

        if (full) wairToNow.drawGPSAvailable (canvas, this);
    }
}
