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
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Display a real IAP plate image file.
 */
@SuppressLint("ViewConstructor")
public class IAPRealPlateImage extends IAPPlateImage {

    // dme arcs added via dmearcs.txt (see PlateCIFP.java)
    private class DMEArc {
        public Waypoint nav;
        public double beg, end, nm;

        public void drawIt (Canvas bmcan)
        {
            // wrap beg to be in range (0..360] as that is the one that gets a label
            while (beg <=  0.0) beg += 360.0;
            while (beg > 360.0) beg -= 360.0;

            // wrap end to be within 180 of beg
            // it might end up negative, it might end up more than 360
            while (end - beg < -180.0) end += 360.0;
            while (end - beg >= 180.0) end += 360.0;

            // calculate true endpoints
            double magvar = nav.GetMagVar (nav.elev);
            double trubeg = beg - magvar;
            double truend = end - magvar;

            // get lat,lon of arc endpoints
            double beglat = Lib.LatHdgDist2Lat (nav.lat, trubeg, nm);
            double beglon = Lib.LatLonHdgDist2Lon (nav.lat, nav.lon, trubeg, nm);
            double endlat = Lib.LatHdgDist2Lat (nav.lat, truend, nm);
            double endlon = Lib.LatLonHdgDist2Lon (nav.lat, nav.lon, truend, nm);

            // convert all the lat,lon to bitmap x,y
            double begbmx = LatLon2BitmapX (beglat, beglon);
            double begbmy = LatLon2BitmapY (beglat, beglon);
            double endbmx = LatLon2BitmapX (endlat, endlon);
            double endbmy = LatLon2BitmapY (endlat, endlon);
            double navbmx = LatLon2BitmapX (nav.lat, nav.lon);
            double navbmy = LatLon2BitmapY (nav.lat, nav.lon);

            double radius = Math.sqrt (Math.hypot (begbmx - navbmx, begbmy - navbmy) *
                    Math.hypot (endbmx - navbmx, endbmy - navbmy));

            // make rectangle the arc circle fits in
            RectF rect = new RectF ((float) (navbmx - radius), (float) (navbmy - radius),
                    (float) (navbmx + radius), (float) (navbmy + radius));

            // draw arc
            Paint paint = new Paint ();
            paint.setColor (invcolors ? Color.WHITE : Color.BLACK);
            paint.setFlags (Paint.ANTI_ALIAS_FLAG);
            paint.setStrokeWidth (6.0F);
            paint.setStyle (Paint.Style.STROKE);
            double start = Math.min (trubeg, truend) - 90.0;
            double sweep = Math.abs (truend - trubeg);
            bmcan.drawArc (rect, (float) start, (float) sweep, false, paint);

            // draw radial line segment at beginning of arc
            double trubegsin = Math.sin (Math.toRadians (trubeg));
            double trubegcos = Math.cos (Math.toRadians (trubeg));
            double pixpernm = radius / nm;
            double innerx = navbmx + (nm - 0.6) * pixpernm * trubegsin;
            double innery = navbmy - (nm - 0.6) * pixpernm * trubegcos;
            double outerx = navbmx + (nm + 0.6) * pixpernm * trubegsin;
            double outery = navbmy - (nm + 0.6) * pixpernm * trubegcos;
            paint.setStrokeWidth (3.0F);
            bmcan.drawLine ((float) innerx, (float) innery, (float) outerx, (float) outery, paint);

            // draw legend
            String legend1 = nav.ident + " " + Math.round (beg) + "\u00B0";
            String legend2 = String.format (Locale.US, "%.1f", nm) + " nm";
            paint.setStrokeWidth (1.0F);
            paint.setStyle (Paint.Style.FILL_AND_STROKE);
            paint.setTextSize (25.0F);
            bmcan.save ();
            if (trubeg <= 180.0) {
                bmcan.rotate ((float) (trubeg - 90.0), (float) outerx, (float) outery);
            } else {
                Rect bounds1 = new Rect ();
                Rect bounds2 = new Rect ();
                paint.getTextBounds (legend1, 0, legend1.length (), bounds1);
                paint.getTextBounds (legend2, 0, legend2.length (), bounds2);
                int width = Math.max (bounds1.width (), bounds2.width ());
                bmcan.rotate ((float) (trubeg - 270.0), (float) outerx, (float) outery);
                bmcan.translate (- width, 0);
            }
            bmcan.drawText (legend1, (float) outerx, (float) outery, paint);
            outery += paint.getTextSize ();
            bmcan.drawText (legend2, (float) outerx, (float) outery, paint);
            bmcan.restore ();
        }
    }

    // https://www.nbaa.org/ops/airspace/issues/20130418-faa-expands-size-of-protected-airspace-for-circling-approaches.php
    // https://aviation.stackexchange.com/questions/973/when-conducting-a-circling-approach-how-close-do-you-have-to-stay-to-the-airpor
    private final static int[][] newcircrad10ths = new int[][] {
            new int[] { 13, 17, 27, 36, 45 },   //    0-1000 ft msl
            new int[] { 13, 18, 28, 37, 46 },   // 1001-3000 ft msl
            new int[] { 13, 18, 29, 38, 48 },   // 3001-5000 ft msl
            new int[] { 13, 19, 30, 40, 50 },   // 5001-7000 ft msl
            new int[] { 14, 20, 32, 42, 53 },   // 7001-9000 ft msl
            new int[] { 14, 21, 33, 44, 55 }    // 9001+     ft msl
    };
    private final static int[] oldcircrad10ths = new int[] {
            13, 15, 17, 23, 45
    };

    private final static byte CRT_UNKN = 0;  // circling radius type unknown
    private final static byte CRT_NEW  = 1;  // circling radius type new style (newcircrad10ths)
    private final static byte CRT_OLD  = 2;  // circling radius type old style (oldcircrad10ths)

    private final static Object shadeCirclingAreaLock = new Object ();
    private static ShadeCirclingAreaThread shadeCirclingAreaThread;

    private Bitmap fgbitmap;              // single plate page
    private boolean      hasDMEArcs;
    private byte         circradtype;
    private byte[]       circrwyepts;
    private double       bitmapLat, bitmapLon;
    private double       bmxy2llx = Double.NaN;
    private double       bmxy2lly = Double.NaN;
    private int          circradopt;
    private Lambert      lccmap;                // non-null: FAA-provided georef data
    private LatLon       bmxy2latlon = new LatLon ();
    private LinkedList<DMEArc> dmeArcs;
    private PointD       bitmapPt = new PointD ();
    private String       filename;              // name of gifs on flash, without ".p<pageno>" suffix

    private final static String[] columns_cp_misc = new String[] { "cp_appid", "cp_segid", "cp_legs" };

    private final static String[] columns_georefs21 = new String[] { "gr_clat", "gr_clon", "gr_stp1", "gr_stp2", "gr_rada", "gr_radb",
            "gr_tfwa", "gr_tfwb", "gr_tfwc", "gr_tfwd", "gr_tfwe", "gr_tfwf", "gr_circtype", "gr_circrweps" };

    public IAPRealPlateImage (WairToNow wtn, Waypoint.Airport apt, String pid, int exp, String fnm, boolean ful)
    {
        super (wtn, apt, pid, exp, ful);
        filename = fnm;

        /*
         * Get any georeference points in database.
         */
        GetIAPGeoRefPoints ();

        /*
         * Init CIFP class.
         * Constructor uses lat/lon <=> bitmap pixel mapping.
         */
        plateCIFP = new PlateCIFP (wairToNow, this, airport, plateid);
    }

    /**
     * Maybe CIFP knows what navaid the approach uses.
     */
    @Override  // IAPPlateImage
    public Waypoint GetRefNavWaypt ()
    {
        return plateCIFP.getRefNavWaypt ();
    }

    /**
     * Read CIFPs for this plate from database.
     */
    @Override  // IAPPlateImage
    public void ReadCIFPs ()
    {
        SQLiteDBs sqldb = openPlateDB ();
        try {
            Cursor result1 = sqldb.query (
                    "iapcifps", columns_cp_misc,
                    "cp_icaoid=?", new String[] { airport.ident },
                    null, null, null, null);
            try {
                if (result1.moveToFirst ()) do {
                    String appid = result1.getString (0);
                    String segid = result1.getString (1);
                    String legs  = result1.getString (2);
                    plateCIFP.ParseCIFPSegment (appid, segid, legs);
                } while (result1.moveToNext ());
            } finally {
                result1.close ();
            }
        } catch (Exception e) {
            plateCIFP.errorMessage ("error reading " + sqldb.mydbname, e);
        }
    }

    /**
     * Set up to draw a DME arc on the plate.
     * These are arcs as specified in dmearcs.txt.
     * (called by PlateCIFP constructor)
     * @param nav = navaid the arc is based on
     * @param beg = beginning of arc (magnetic), this gets a tick mark and legend
     * @param end = end of arc (magnetic), should be recip of final approach course
     * @param nm  = arc radius, usually 10.0
     */
    @Override  // IAPPlateImage
    public void DrawDMEArc (Waypoint nav, double beg, double end, double nm)
    {
        // add it to list of DME arcs to be drawn on plate
        if (dmeArcs == null) dmeArcs = new LinkedList<> ();
        DMEArc dmeArc = new DMEArc ();
        dmeArc.nav = nav;
        dmeArc.beg = beg;
        dmeArc.end = end;
        dmeArc.nm  = nm;
        dmeArcs.addLast (dmeArc);
        hasDMEArcs = true;
    }

    /**
     * Check for FAA-supplied georeference information.
     */
    private void GetIAPGeoRefPoints ()
    {
        String[] grkey = new String[] { airport.ident, plateid };
        SQLiteDBs machinedb = openPlateDB ();
        if (machinedb.tableExists ("iapgeorefs2")) {
            if (!machinedb.columnExists ("iapgeorefs2", "gr_circtype")) {
                machinedb.execSQL ("ALTER TABLE iapgeorefs2 ADD COLUMN gr_circtype INTEGER NOT NULL DEFAULT " + CRT_UNKN);
            }
            if (!machinedb.columnExists ("iapgeorefs2", "gr_circrweps")) {
                machinedb.execSQL ("ALTER TABLE iapgeorefs2 ADD COLUMN gr_circrweps BLOB");
            }
            Cursor result = machinedb.query ("iapgeorefs2", columns_georefs21,
                    "gr_icaoid=? AND gr_plate=?", grkey,
                    null, null, null, null);
            try {
                if (result.moveToFirst ()) {
                    lccmap = new Lambert (
                            result.getDouble (0),
                            result.getDouble (1),
                            result.getDouble (2),
                            result.getDouble (3),
                            result.getDouble (4),
                            result.getDouble (5),
                            new double[] {
                                result.getDouble (6),
                                result.getDouble (7),
                                result.getDouble (8),
                                result.getDouble (9),
                                result.getDouble (10),
                                result.getDouble (11)
                            }
                    );
                    circradtype = (byte) result.getInt (12);
                    circrwyepts = result.getBlob (13);
                }
            } finally {
                result.close ();
            }
        }
    }

    /**
     * Close any open bitmaps so we don't take up too much memory.
     */
    public void CloseBitmaps ()
    {
        if (fgbitmap != null) {
            fgbitmap.recycle ();
            fgbitmap = null;
        }
    }

    /**
     * Draw the plate image and any other marks on it we want.
     */
    @Override  // View
    public void onDraw (Canvas canvas)
    {
        /*
         * Get single page's bitmap.
         */
        if (fgbitmap == null) {

            // get original page
            Bitmap bm = OpenFirstPage (filename);
            if (bm == null) return;
            if (bm.isMutable ()) {
                fgbitmap = bm;
            } else {
                fgbitmap = bm.copy (Bitmap.Config.ARGB_8888, true);
                bm.recycle ();
            }

            // add DME arcs if any defined
            if (hasDMEArcs) {
                DrawDMEArcsOnBitmap ();
            }

            // shade circling area
            if (lccmap != null) {
                ShadeCirclingArea ();
            }
        }

        /*
         * Display bitmap to canvas.
         */
        ShowSinglePage (canvas, fgbitmap);

        /*
         * Draw runways if FAA-provided georef present.
         */
        if ((lccmap != null) && (SystemClock.uptimeMillis () - plateLoadedUptime < 10000)) {
            DrawRunwayCenterlines (canvas);
        }

        /*
         * Draw a bunch of stuff on the plate.
         */
        DrawCommon (canvas);
    }

    /**
     * Convert lat,lon to bitmap X,Y.
     * Use LCC conversion if FAA-supplied georef data given.
     */
    @Override  // GRPPlateImage
    protected boolean LatLon2BitmapOK ()
    {
        return lccmap != null;
    }

    @Override  // GRPPlateImage
    public double LatLon2BitmapX (double lat, double lon)
    {
        if (lccmap == null) return Double.NaN;
        bitmapLat = lat;
        bitmapLon = lon;
        lccmap.LatLon2ChartPixelExact (lat, lon, bitmapPt);
        return bitmapPt.x;
    }

    @Override  // GRPPlateImage
    public double LatLon2BitmapY (double lat, double lon)
    {
        if (lccmap == null) return Double.NaN;
        if ((bitmapLat != lat) || (bitmapLon != lon)) {
            bitmapLat = lat;
            bitmapLon = lon;
            lccmap.LatLon2ChartPixelExact (lat, lon, bitmapPt);
        }
        return bitmapPt.y;
    }

    @Override  // GRPPlateImage
    public double BitmapXY2Lat (double bmx, double bmy)
    {
        if (lccmap == null) return Double.NaN;
        if ((bmx != bmxy2llx) || (bmy != bmxy2lly)) {
            bmxy2llx = bmx;
            bmxy2lly = bmy;
            lccmap.ChartPixel2LatLonExact (bmx, bmy, bmxy2latlon);
        }
        return bmxy2latlon.lat;
    }

    @Override  // GRPPlateImage
    public double BitmapXY2Lon (double bmx, double bmy)
    {
        if (lccmap == null) return Double.NaN;
        if ((bmx != bmxy2llx) || (bmy != bmxy2lly)) {
            bmxy2llx = bmx;
            bmxy2lly = bmy;
            lccmap.ChartPixel2LatLonExact (bmx, bmy, bmxy2latlon);
        }
        return bmxy2latlon.lon;
    }

    /**
     * Adds DME arcs to copy of original plate bitmap image.
     * Write to file so user can print it.
     */
    private void DrawDMEArcsOnBitmap ()
    {
        // draw DME arc(s) on bitmap
        Canvas bmcan = new Canvas (fgbitmap);
        for (DMEArc dmeArc : dmeArcs) dmeArc.drawIt (bmcan);

        // if we don't already have an image file, create one
        File csvfile = new File (WairToNow.dbdir + "/dmearcs.txt");
        File imgfile = new File (WairToNow.dbdir + "/dmearcs-" + airport.ident + "-" +
                plateid.replace (" ", "_").replace ("/", "_") + "-" + expdate + ".gif");
        if (imgfile.lastModified () < csvfile.lastModified ()) {
            synchronized (WriteGifThread.list) {
                if (!WriteGifThread.list.containsKey (imgfile)) {
                    WriteGifThread.Node node = new WriteGifThread.Node (fgbitmap);
                    WriteGifThread.list.put (imgfile, node);
                    if (WriteGifThread.thread == null) {
                        WriteGifThread.thread = new WriteGifThread ();
                        WriteGifThread.thread.start ();
                    }
                }
            }
        }
    }

    /**
     * Set up fgbitmap with circling area shaded in.
     */
    private void ShadeCirclingArea ()
    {
        // get speed category selected by user
        // don't do anything more if user has disabled circling area shading
        circradopt = wairToNow.optionsView.circCatOption.getVal ();
        if (circradopt < 0) return;

        // fill in shading in background thread
        // make sure we only have one thread so we don't run out of memory
        synchronized (shadeCirclingAreaLock) {
            ShadeCirclingAreaThread scat = shadeCirclingAreaThread;
            if (scat == null) {
                shadeCirclingAreaThread = scat = new ShadeCirclingAreaThread ();
                scat.start ();
            }
            scat.nextPlateImage = this;
        }
    }

    /**
     * This runs in the ShadeCirclingAreaThread to scan bitmap for the inverse [C] if necessary
     * then shade in the circling area on the bitmap.
     */
    @Override  // IAPPlateImage
    public void ShadeCirclingAreaWork ()
    {
        try {

            // maybe need to compute circling area type
            //  CRT_OLD: old style, independent of circling MDA
            //  CRT_NEW: new style, depends on circling MDA
            if (circradtype == CRT_UNKN) {

                // read bitmap containing prototype white C on black background
                InputStream tagstream = wairToNow.getAssets ().open ("circletag.png");
                Bitmap tagbmp = BitmapFactory.decodeStream (tagstream);
                tagstream.close ();
                int tagwidth  = tagbmp.getWidth  ();
                int tagheight = tagbmp.getHeight ();
                int[] tagpixs = new int[tagwidth*tagheight];
                tagbmp.getPixels (tagpixs, 0, tagwidth, 0, 0, tagwidth, tagheight);
                tagbmp.recycle ();
                boolean[] tagblacks = new boolean[tagwidth*tagheight];
                for (int i = tagwidth * tagheight; -- i >= 0;) {
                    tagblacks[i] = Color.red (tagpixs[i]) < 128;
                }

                // get lower left quadrant of plate image
                int bmpwidth  = fgbitmap.getWidth  () / 2;
                int bmpheight = fgbitmap.getHeight () / 2;
                int[] pixels = new int[bmpwidth*bmpheight];
                fgbitmap.getPixels (pixels, 0, bmpwidth, 0, bmpheight, bmpwidth, bmpheight);
                boolean[] blacks = new boolean[bmpwidth*bmpheight];
                for (int i = bmpwidth * bmpheight; -- i >= 0;) {
                    blacks[i] = Color.red (pixels[i]) < 128;
                }

                // search lower left quadrant for inverse [C]
                circradtype = CRT_OLD;
                for (int y = 0; y < bmpheight - tagheight; y ++) {
                    for (int x = 0; x < bmpwidth - tagwidth; x ++) {
                        int matches = 0;
                        for (int yy = 0; yy < tagheight;) {
                            for (int xx = 0; xx < tagwidth; xx ++) {
                                boolean black = blacks[(y+yy)*bmpwidth+(x+xx)];
                                if (black == tagblacks[yy*tagwidth+xx]) matches ++;
                            }
                            if (matches * 8 < ++ yy * tagwidth * 7) break;
                        }
                        if (matches * 8 > tagheight * tagwidth * 7) {
                            circradtype = CRT_NEW;
                            x = bmpwidth;
                            y = bmpheight;
                        }
                    }
                }

                // write value to database so we don't have to scan this plate again
                ContentValues values = new ContentValues (1);
                values.put ("gr_circtype", circradtype);
                String[] whargs = new String[] { airport.ident, plateid };
                SQLiteDBs machinedb = openPlateDB ();
                machinedb.update ("iapgeorefs2", values, "gr_icaoid=? AND gr_plate=?", whargs);
            }

            // maybe we need to compute circling ring endpoints
            // it is a convex polygon whose vertices are the runway endpoints
            // any interior points are discarded/ignored
            if ((circrwyepts == null) || (circrwyepts.length == 0)) {

                // make array of runway endpoints
                Collection<Waypoint.Runway> runways = airport.GetRunways ().values ();
                int nrwyeps     = runways.size ();
                if (nrwyeps == 0) return;
                PointD[] rwyeps = new PointD[nrwyeps];
                double minrwybmpx = Double.MAX_VALUE;
                double minrwybmpy = Double.MAX_VALUE;
                double maxrwybmpx = Double.MIN_VALUE;
                double maxrwybmpy = Double.MIN_VALUE;
                int i = 0;
                for (Waypoint.Runway runway : runways) {
                    double rwybmpx = LatLon2BitmapX (runway.lat, runway.lon);
                    double rwybmpy = LatLon2BitmapY (runway.lat, runway.lon);
                    rwyeps[i++]    = new PointD (rwybmpx, rwybmpy);
                    if (minrwybmpx > rwybmpx) minrwybmpx = rwybmpx;
                    if (minrwybmpy > rwybmpy) minrwybmpy = rwybmpy;
                    if (maxrwybmpx < rwybmpx) maxrwybmpx = rwybmpx;
                    if (maxrwybmpy < rwybmpy) maxrwybmpy = rwybmpy;
                }

                // make a clockwise convex polygon out of the points,
                // ignoring any on the interior
                if (nrwyeps > 2) {
                    PointD[] temp = new PointD[nrwyeps];
                    i = 0;

                    // find westmost point, guaranteed to be an outie
                    int bestj = 0;
                    for (int j = 0; ++ j < nrwyeps;) {
                        if (rwyeps[bestj].x > rwyeps[j].x) bestj = j;
                    }
                    temp[i++] = rwyeps[bestj];
                    rwyeps[bestj] = rwyeps[--nrwyeps];

                    // find next point of lowest heading from that
                    // due north is 0, east is 90, south is 180, west is 270
                    // repeat until the answer is the starting point
                    // since we are at westmost point to start, next point will be east of it
                    double lasth = 0.0;
                    while (true) {
                        PointD lastep = temp[i-1];
                        double besth = Double.MAX_VALUE;
                        bestj = -1;
                        for (int j = 0; j < nrwyeps; j ++) {
                            PointD rwyep = rwyeps[j];

                            // get heading from last point to this point
                            // range is -PI .. +PI
                            double h = Math.atan2 (rwyep.x - lastep.x, lastep.y - rwyep.y);

                            // lasth is in range 0 .. +2*PI
                            // compute how much we have to turn right
                            // so resultant range of h is -3*PI .. +PI
                            h -= lasth;

                            // wrap to range -PI/10 .. +PI*19/10
                            // the -PI/10 allows for a little turning left for rounding errors
                            if (h < - Math.PI / 10.0) h += Math.PI * 2.0;

                            // save it if it is the least amount of right turn
                            // (this includes saving if it is a little bit of a left turn
                            //  from rounding errors)
                            if (besth > h) {
                                besth = h;
                                bestj = j;
                            }
                        }

                        // if made it all the way back to westmost point, we're done
                        if (rwyeps[bestj] == temp[0]) break;

                        // otherwise save point, remove from list, and continue on
                        temp[i++] = rwyeps[bestj];
                        rwyeps[bestj] = (i == 2) ? temp[0] : rwyeps[--nrwyeps];
                        lasth += besth;
                    }

                    // replace old list with new one
                    // it may have fewer points (some were on interior)
                    rwyeps = temp;
                    nrwyeps = i;
                }

                // write endpoints in order to database blob so we don't have to recompute it next time
                // packed as pairs of shorts, x,y, for each endpoint
                circrwyepts = new byte[nrwyeps*4];
                int j = 0;
                for (i = 0; i < nrwyeps; i ++) {
                    long xx = Math.round (rwyeps[i].x);
                    long yy = Math.round (rwyeps[i].y);
                    if ((xx != (short) xx) || (yy != (short) yy)) {
                        throw new Exception ("bad runway x,y");
                    }
                    circrwyepts[j++] = (byte) xx;
                    circrwyepts[j++] = (byte) (xx >> 8);
                    circrwyepts[j++] = (byte) yy;
                    circrwyepts[j++] = (byte) (yy >> 8);
                }

                ContentValues values = new ContentValues (1);
                values.put ("gr_circrweps", circrwyepts);
                String[] whargs = new String[] { airport.ident, plateid };
                SQLiteDBs machinedb = openPlateDB ();
                machinedb.update ("iapgeorefs2", values, "gr_icaoid=? AND gr_plate=?", whargs);
            }

            // get circling radius in tenths of a nautical mile based on circling MDA MSL and category
            // we don't have circling MDA so use airport elev + 200 which is conservative
            int circrad10ths;
            String type;
            if (circradtype == CRT_NEW) {
                int aptelevidx = (airport.elev == Waypoint.ELEV_UNKNOWN) ? 0 : ((int) airport.elev + 1200) / 2000;
                if (aptelevidx > 5) aptelevidx = 5;
                circrad10ths = newcircrad10ths[aptelevidx][circradopt];
                type = "new";
            } else {
                circrad10ths = oldcircrad10ths[circradopt];
                type = "old";
            }
            Log.i (TAG, airport.ident + " " + plateid + " using " + type + " circling radius of " + (circrad10ths / 10.0));

            // get circling radius in bitmap pixels
            double circradlat = airport.lat + circrad10ths / (Lib.NMPerDeg * 10.0);
            double aptbmx = LatLon2BitmapX (airport.lat, airport.lon);
            double aptbmy = LatLon2BitmapY (airport.lat, airport.lon);
            double radbmx = LatLon2BitmapX (circradlat, airport.lon);
            double radbmy = LatLon2BitmapY (circradlat, airport.lon);
            double circradbmp = Math.hypot (aptbmx - radbmx, aptbmy - radbmy);

            // draw outline circradbmp outside that polygon, rounding the corners
            int nrwyeps = circrwyepts.length / 4;
            Path path = new Path  ();
            RectF oval = new RectF ();
            int qx = circRwyEpX ((nrwyeps * 2 - 2) % nrwyeps);
            int qy = circRwyEpY ((nrwyeps * 2 - 2) % nrwyeps);
            int rx = circRwyEpX (nrwyeps - 1);
            int ry = circRwyEpY (nrwyeps - 1);
            double tcqr = Math.toDegrees (Math.atan2 (rx - qx, qy - ry));
            for (int i = 0; i < nrwyeps; i ++) {

                // three points going clockwise around the perimeter P -> Q -> R
                qx = rx; qy = ry;
                rx = circRwyEpX (i);
                ry = circRwyEpY (i);

                // standing at Q looking at R:
                //  draw arc from Q of displaced PQ to Q of displaced QR
                // we can assume it is a right turn at Q from PQ to QR
                //  the turn amount to the right should be -1..+181 degrees
                //  (the extra degree on each end is for rounding errors)
                // the Path object will fill in a line from the last arc to this one
                oval.bottom  = (float) (qy + circradbmp);
                oval.left    = (float) (qx - circradbmp);
                oval.right   = (float) (qx + circradbmp);
                oval.top     = (float) (qy - circradbmp);
                double tcpq  = tcqr;
                tcqr         = Math.toDegrees (Math.atan2 (rx - qx, qy - ry));
                double start = tcpq + 180.0;
                double sweep = tcqr - tcpq;
                if (sweep < -10.0) sweep += 360.0;
                if (sweep > 0.0) {  // slight left turn from rounding errors gets no curve
                    path.arcTo (oval, (float) start, (float) sweep);
                }
            }

            // close the path by joining the end of the last arc to the beginning of the first arc
            path.close ();

            // draw the path and fill it in
            // use darken mode to leave black stuff fully black
            Canvas canvas = new Canvas (fgbitmap);
            Paint  paint  = new Paint  ();
            paint.setColor (invcolors ? Color.rgb (0, 85, 170) : Color.rgb (255, 170, 85));
            paint.setStrokeWidth (0);
            paint.setStyle (Paint.Style.FILL_AND_STROKE);
            paint.setXfermode (new PorterDuffXfermode (
                    invcolors ? PorterDuff.Mode.LIGHTEN : PorterDuff.Mode.DARKEN));
            canvas.drawPath (path, paint);
        } catch (Exception e) {
            Log.w (TAG, "error shading circling area", e);
        }
    }

    private int circRwyEpX (int i)
    {
        i *= 4;
        int xx = circrwyepts[i++] & 0xFF;
        xx += circrwyepts[i] << 8;
        return xx;
    }

    private int circRwyEpY (int i)
    {
        i *= 4;
        i += 2;
        int yy = circrwyepts[i++] & 0xFF;
        yy += circrwyepts[i]   << 8;
        return yy;
    }

    /**
     * If we don't know if the plate uses the old or new circling area definitions,
     * scan it and write the tag to the database.  Then draw the circling area.
     * If we erroneously don't find the inverse C, then we assume old-style circling
     * radii which are more conservative than the new ones which is OK.
     */
    private static class ShadeCirclingAreaThread extends Thread {
        public IAPPlateImage nextPlateImage;

        @Override
        public void run ()
        {
            setName ("ShadeCirclingAreaThread");
            setPriority (MIN_PRIORITY);
            while (true) {
                IAPPlateImage iapPlateImage;
                synchronized (shadeCirclingAreaLock) {
                    iapPlateImage = nextPlateImage;
                    if (iapPlateImage == null) {
                        shadeCirclingAreaThread = null;
                        break;
                    }
                    nextPlateImage = null;
                }
                iapPlateImage.ShadeCirclingAreaWork ();
            }
            SQLiteDBs.CloseAll ();
        }
    }

    /**
     * First time displaying a plat with a DME arc on it,
     * write to a .gif file in a background thread.
     */
    private static class WriteGifThread extends Thread {
        public static class Node {
            public int[] data;
            public int width;
            public int height;

            public Node (Bitmap bm)
            {
                width  = bm.getWidth  ();
                height = bm.getHeight ();
                data   = new int[width*height];
                bm.getPixels (data, 0, width, 0, 0, width, height);
            }
        }

        public static final NNHashMap<File,Node> list = new NNHashMap<> ();
        public static WriteGifThread thread;

        @Override
        public void run ()
        {
            setName ("WriteGifThread");
            setPriority (Thread.MIN_PRIORITY);

            File imgfile = null;
            Node node;
            while (true) {

                // see if another gif to write
                // exit thread if not
                synchronized (list) {
                    if (imgfile != null) list.remove (imgfile);
                    Iterator<File> it = list.keySet ().iterator ();
                    if (!it.hasNext ()) {
                        thread = null;
                        break;
                    }
                    imgfile = it.next ();
                    node = list.nnget (imgfile);
                }

                // write pixel data to temp file in gif format
                long ms = SystemClock.uptimeMillis ();
                Log.i (TAG, "creating " + imgfile.getPath ());
                try {
                    File tmpfile = new File (imgfile.getPath () + ".tmp");
                    FileOutputStream tmpouts = new FileOutputStream (tmpfile);
                    try {
                        AnimatedGifEncoder encoder = new AnimatedGifEncoder ();
                        encoder.start (tmpouts);
                        encoder.addFrame (node.data, node.width, node.height);
                        encoder.finish ();
                    } finally {
                        tmpouts.close ();
                    }

                    // temp written successfully, rename to permanent name
                    if (!tmpfile.renameTo (imgfile)) {
                        throw new IOException ("error renaming from " + tmpfile.getPath ());
                    }
                } catch (IOException ioe) {

                    // failed to write temp file
                    Log.w (TAG, "error writing " + imgfile.getPath () + ": " + ioe.getMessage ());
                    Lib.Ignored (imgfile.delete ());
                }

                // finished one way or the other
                ms = SystemClock.uptimeMillis () - ms;
                Log.i (TAG, "finished " + imgfile.getPath () + ", " + ms + " ms");
            }
        }
    }

    private SQLiteDBs openPlateDB ()
    {
        int expdate = MaintView.GetPlatesExpDate (airport.state);
        String dbname = "nobudb/plates_" + expdate + ".db";
        return SQLiteDBs.open (dbname);
    }
}
