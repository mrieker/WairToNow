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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Display the IAP plate image file.
 */
@SuppressLint("ViewConstructor")
public class IAPSynthPlateImage extends IAPPlateImage implements DisplayableChart.Invalidatable {
    public final static String prefix = "IAP-Synth ILS/DME RWY ";

    // pixel size of real plate files
    private final static int platewidth  = 1614;
    private final static int plateheight = 2475;

    private final static int diagtop  =  511;
    private final static int diagbot  = 1740;
    private final static int diagleft =   75;
    private final static int diagrite = 1537;

    private final static int sideleft = diagleft;
    private final static int siderite = diagrite;
    private final static int sidetop  = diagbot;
    private final static int sidebot  = 2128;

    private final static double sidedist = 15.0;
    private final static int maltrad = 20;

    // 300dpi on plate
    // same scale as sectional chart
    // 300dp(500,000in) in real world
    // = 42.33333 metres/pixel
    private final static double mperbmpix = 500000.0 / 300.0 / 12.0 / Lib.FtPerM;

    private static Path largeobst;
    private static Path maltpath;
    private static Path smallobst;

    private Bitmap   bitmap;
    private Bitmap   invbitmap;
    private boolean  firstDraw = true;
    private double   centerlat;     // lat,lon at center of needle
    private double   centerlon;     // = initially at center of canvas
    private double   gsintdmenm;    // glideslope distance from synth loc/dme antenna
    private double   locmcbin;      // runway magnetic course
    private int      gsaltbin;      // glideslope intercept altitude feet MSL
    private int      wptexpdate;
    private Waypoint dirwaypt;
    private Waypoint fafwaypt;
    private Waypoint.Runway runway;

    @SuppressWarnings({ "PointlessArithmeticExpression", "StringConcatenationInLoop" })
    public IAPSynthPlateImage (WairToNow wtn, Waypoint.Airport apt, String pid, int exp, boolean ful)
    {
        super (wtn, apt, pid, exp, ful);
        runway = airport.GetRunways ().nnget (plateid.substring (prefix.length ()));
        Waypoint.Localizer synthloc = runway.GetSynthLoc ();

        /*
         * Set up lat/lon <-> bitmap pixel mapping.
         *   actual plates are 5.38 x 8.25 inches
         *                     1614 x 2475 pixels
         *                     same scale as section charts (500,000 : 1)
         *                    36.86 x 56.57 nm
         *   At 300dpi, actual plates are 1614 x 2475 pix = 5.38 x 8.25
         *   And actual plates are same scale as sectional charts
         * Position such that center of needle is in center of plate.
         */
        double loctc = synthloc.thdg;
        double rwynm = Lib.LatLonDist (runway.lat, runway.lon, runway.endLat, runway.endLon);

        double loclat = synthloc.lat;
        double loclon = synthloc.lon;
        centerlat = Lib.LatHdgDist2Lat (loclat, loctc + 180.0, 7.5);
        centerlon = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 180.0, 7.5);

        /*
         * Glideslope calculations.
         */
        // glideslope intercept altitude (feet), approx 2000ft AGL
        gsaltbin = ((int) Math.round (runway.elev / 100.0)) * 100 + 2000;

        double gslat = synthloc.gs_lat;
        double gslon = synthloc.gs_lon;
        double gsintaltagl = gsaltbin - runway.elev;
        double gsintdistft = gsintaltagl / Math.tan (Math.toRadians (synthloc.gs_tilt));
        double gsintdistnm = gsintdistft / Lib.FtPerNM;
        double gsintlat = Lib.LatHdgDist2Lat (gslat, loctc + 180.0, gsintdistnm);
        double gsintlon = Lib.LatLonHdgDist2Lon (gslat, gslon, loctc + 180.0, gsintdistnm);

        // DME antenna is located at far end of runway
        double dmelat = synthloc.GetDMELat ();
        double dmelon = synthloc.GetDMELon ();
        gsintdmenm = Lib.LatLonDist (gsintlat, gsintlon, dmelat, dmelon);

        /*
         * Informational text lines.
         */
        String dhtext = "Decision Height:  " + Math.round (runway.elev + 250.0) + "' (250)  or  " +
                Math.round (runway.elev + 200.0) + "' (200)";
        String maptxt = "Missed Approach:  Climb into hold at " + gsaltbin + "' in " +
                (runway.ritraf ? "right" : "left") + "-hand traffic pattern";

        /*
         * Set up frequencies to display on plate.
         */
        String ctaf   = "";
        String atis   = "";
        String awos   = "";
        String appcon = "";
        String tower  = "";
        String gndcon = "";
        String unicom = "";
        String center = "";
        for (String det : airport.GetArptDetails ().split ("\n")) {
            if (det.startsWith ("UNICOM:")) unicom += appVHFFreq (det);
            if (det.startsWith ("CTAF:"))   ctaf   += appVHFFreq (det);
            if (det.startsWith ("ATIS:"))   atis   += appVHFFreq (det);
            if (det.startsWith ("GND/"))    gndcon += appVHFFreq (det);
            if (det.startsWith ("TWR/"))    tower  += appVHFFreq (det);
            if (det.startsWith ("APCH/"))   appcon += appVHFFreq (det);
            if (det.startsWith ("AWOS:"))   awos   += appVHFFreq (det);
            if (det.startsWith ("CENTER:")) center += appVHFFreq (det);
        }

        if (! atis.equals ("")) awos = "";

        NNTreeMap<String,String> frequencies = new NNTreeMap<> ();
        if (! ctaf.equals ("")) frequencies.put ("CTAF", ctaf.trim ());
        if (! atis.equals ("")) frequencies.put ("ATIS", atis.trim ());
        if (! awos.equals ("")) frequencies.put ("AWOS", awos.trim ());
        if (! appcon.equals ("")) frequencies.put ("APP CON", appcon.trim ());
        if (! tower.equals ("")) frequencies.put ("TOWER", tower.trim ());
        if (! gndcon.equals ("")) frequencies.put ("GND CON", gndcon.trim ());
        if (! unicom.equals ("")) frequencies.put ("UNICOM", unicom.trim ());
        if (! center.equals ("")) frequencies.put ("CENTER", center.trim ());

        if (! ctaf.equals ("") && ctaf.equals (tower)) {
            frequencies.remove ("CTAF");
            frequencies.remove ("TOWER");
            frequencies.put ("TOWER/CTAF", tower.trim ());
            ctaf = "";
        }

        if (! ctaf.equals ("") && ctaf.equals (unicom)) {
            frequencies.remove ("CTAF");
            frequencies.remove ("UNICOM");
            frequencies.put ("CTAF/UNICOM", unicom.trim ());
        }

        /*
         * Compute localizer magnetic course.
         */
        locmcbin = loctc + synthloc.magvar;
        String locmcstr = Lib.Hdg2Str (locmcbin);

        /*
         * Create plate bitmap filled with white background.
         */
        bitmap = Bitmap.createBitmap (platewidth, plateheight, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor (Color.WHITE);
        Canvas canvas = new Canvas (bitmap);

        /*
         * Draw header text.
         */
        Paint paint = new Paint ();
        paint.setColor (Color.BLACK);

        paint.setStrokeWidth (3.0F);
        paint.setStyle (Paint.Style.STROKE);
        canvas.drawRect (284, 160, 404, 270, paint);    // APP CRS
        canvas.drawRect (404, 160, 618, 270, paint);    // Rwy Ldg..Apt Elev
        canvas.drawRect (diagleft, 410, diagrite, diagtop, paint);      // frequencies
        canvas.drawRect (diagleft, diagtop, diagrite, diagbot, paint);  // diagramme
        canvas.drawRect (sideleft, sidetop, siderite, sidebot, paint);  // side view

        paint.setStyle (Paint.Style.FILL_AND_STROKE);

        paint.setTextAlign (Paint.Align.RIGHT);
        paint.setTextSize (47);
        paint.setStrokeWidth (3.0F);
        canvas.drawText (plateid, 1500, 217, paint);
        paint.setTextSize (30);
        paint.setStrokeWidth (2.0F);
        canvas.drawText (airport.name + " (" + airport.faaident + ")", 1500, 257, paint);
        paint.setTextSize (47);
        canvas.drawText (synthloc.ident, 1500, 309, paint);

        paint.setStrokeWidth (1.0F);
        paint.setTextAlign (Paint.Align.CENTER);
        paint.setTextSize (23);
        canvas.drawText ("APP CRS", 345, 211, paint);
        paint.setStrokeWidth (2.0F);
        canvas.drawText (locmcstr, 345, 247, paint);

        paint.setStrokeWidth (1.0F);
        paint.setTextAlign (Paint.Align.LEFT);
        canvas.drawText ("Rwy Ldg", 421, 194, paint);
        canvas.drawText ("TDZE", 421, 225, paint);
        canvas.drawText ("Apt Elev", 421, 256, paint);

        paint.setTextAlign (Paint.Align.RIGHT);
        paint.setStrokeWidth (2.0F);
        canvas.drawText (Integer.toString (runway.getLengthFt ()), 607, 194, paint);
        canvas.drawText (Long.toString (Math.round (runway.elev)), 607, 225, paint);
        canvas.drawText (Long.toString (Math.round (airport.elev)), 607, 256, paint);

        paint.setTextAlign (Paint.Align.CENTER);
        paint.setTextSize (33.0F);
        canvas.drawText (dhtext, platewidth / 2.0F, 341, paint);
        canvas.drawText (maptxt, platewidth / 2.0F, 382, paint);

        paint.setStrokeWidth (3.0F);
        int nfreq = frequencies.size ();
        for (int i = 0; ++ i < nfreq;) {
            int x = Math.round ((diagrite - diagleft) * i / (float) nfreq) + diagleft;
            canvas.drawLine (x, 410, x, diagtop, paint);
        }
        int ifreq = 0;
        for (String key : frequencies.keySet ()) {
            String val = frequencies.nnget (key);
            int x = Math.round ((diagrite - diagleft) * (ifreq + 0.5F) / nfreq) + diagleft;
            paint.setStrokeWidth (1.0F);
            canvas.drawText (key, x, 455, paint);
            paint.setStrokeWidth (2.0F);
            canvas.drawText (val, x, 492, paint);
            ifreq ++;
        }

        /*
         * Draw expiration date strings.
         */
        wptexpdate = wairToNow.maintView.GetCurentWaypointExpDate ();
        int wptexpmonidx = wptexpdate / 100 % 100 * 3;
        String wptexpmonth = "JANFEBMARAPRMAYJUNJULAUGSEPOCTNOVDEC".substring (wptexpmonidx - 3, wptexpmonidx);
        String expstr = "VALID to " + (wptexpdate % 100) + " " + wptexpmonth + " " + (wptexpdate / 10000);
        canvas.save ();
        canvas.rotate (270, 1590, 1200);
        canvas.drawText (expstr, 1590, 1200, paint);
        canvas.restore ();
        canvas.save ();
        canvas.rotate (90, 24, 1200);
        canvas.drawText (expstr, 24, 1200, paint);
        canvas.restore ();

        /*
         * Draw side view diagram.
         */
        // side view runway line
        paint.setColor (Color.BLACK);
        paint.setStrokeWidth (9.0F);
        float rwysidex = sideLatLonToX (runway.lat, runway.lon);
        canvas.drawLine (sideLatLonToX (runway.endLat, runway.endLon), 2100,
                         rwysidex, 2100, paint);

        // level line to intercept glideslope and glideslope line to gs antenna
        float gsantx = sideLatLonToX (synthloc.gs_lat, synthloc.gs_lon);
        float gsintx = sideLatLonToX (gsintlat, gsintlon);
        paint.setStrokeWidth (5.0F);
        canvas.drawLine (gsantx, 2100, gsintx, 1976, paint);
        canvas.drawLine (gsintx, 1976, 228, 1976, paint);

        // DME at runway numbers and glideslope intercept
        paint.setColor (Color.GRAY);
        paint.setStrokeWidth (3.0F);
        canvas.drawLine (rwysidex, 2100, rwysidex, 1900, paint);
        canvas.drawLine (gsintx, 2100, gsintx, 1900, paint);
        paint.setColor (Color.BLACK);
        paint.setTextAlign (Paint.Align.CENTER);
        double rwydmelen = Lib.LatLonDist (runway.lat, runway.lon, synthloc.dme_lat, synthloc.dme_lon);
        paint.setStrokeWidth (2.0F);
        paint.setTextSize (47);
        canvas.drawText (synthloc.ident, rwysidex, 1832, paint);
        canvas.drawText (synthloc.ident, gsintx, 1832, paint);
        paint.setTextSize (40);
        canvas.drawText (String.format (Locale.US, "%3.1f DME", rwydmelen), rwysidex, 1880, paint);
        canvas.drawText (String.format (Locale.US, "%3.1f DME", gsintdmenm), gsintx, 1880, paint);

        // mag course, gs intercept altitude, glideslope descent angle strings
        paint.setTextAlign (Paint.Align.CENTER);
        canvas.drawText ("\u21D0 " + Lib.Hdg2Str (locmcbin + 180.0), (228 + gsintx) / 2, 1890, paint);
        canvas.drawText (locmcstr + " \u21D2", (228 + gsintx) / 2, 1950, paint);
        canvas.drawText (gsaltbin + " ft", (228 + gsintx) / 2, 2032, paint);
        paint.setTextAlign (Paint.Align.LEFT);
        canvas.drawText (String.format (Locale.US, "  %4.2f\u00B0 \u21D8", synthloc.gs_tilt), gsintx, 2080, paint);

        // maltese at gs intercept
        if (maltpath == null) {
            float r30 = (float) Math.sin (Math.toRadians (30.0)) * maltrad;
            float r60 = (float) Math.sin (Math.toRadians (60.0)) * maltrad;
            maltpath = new Path ();
            maltpath.moveTo (0.0F, 0.0F);
            maltpath.lineTo ( r30, -r60);
            maltpath.lineTo ( r60, -r30);
            maltpath.lineTo (0.0F, 0.0F);
            maltpath.lineTo ( r60,  r30);
            maltpath.lineTo ( r30,  r60);
            maltpath.lineTo (0.0F, 0.0F);
            maltpath.lineTo (-r30,  r60);
            maltpath.lineTo (-r60,  r30);
            maltpath.lineTo (0.0F, 0.0F);
            maltpath.lineTo (-r60, -r30);
            maltpath.lineTo (-r30, -r60);
            maltpath.lineTo (0.0F, 0.0F);
        }
        canvas.save ();
        canvas.translate (gsintx, 1976);
        paint.setColor (Color.WHITE);
        canvas.drawRect (- maltrad * 1.2F, - maltrad * 1.2F, maltrad * 1.2F, maltrad * 1.2F, paint);
        paint.setColor (Color.BLACK);
        canvas.drawPath (maltpath, paint);
        canvas.restore ();

        /*
         * Draw VFR ONLY message on bottom.
         */
        paint.setColor (Color.RED);
        paint.setTextSize (50.0F);
        paint.setTextAlign (Paint.Align.CENTER);
        canvas.drawText ("VFR USE ONLY", 807, sidebot + 87, paint);

        /*
         * Set clip to diagram area of bitmap so we don't draw topo background out of the box.
         * Leave a few pixels for the rectangle outline width.
         */
        canvas.clipRect (diagleft + 2, diagtop + 2, diagrite - 2, diagbot - 2);

        /*
         * Set up topo rectangles for chart background.
         */
        double tllat  = BitmapXY2Lat (diagleft, diagtop);
        double trlat  = BitmapXY2Lat (diagrite, diagtop);
        double bllat  = BitmapXY2Lat (diagleft, diagbot);
        double brlat  = BitmapXY2Lat (diagrite, diagbot);
        double tllon  = BitmapXY2Lon (diagleft, diagtop);
        double trlon  = BitmapXY2Lon (diagrite, diagtop);
        double bllon  = BitmapXY2Lon (diagleft, diagbot);
        double brlon  = BitmapXY2Lon (diagrite, diagbot);
        double minlat = Math.min (Math.min (tllat, trlat), Math.min (bllat, brlat));
        double maxlat = Math.max (Math.max (tllat, trlat), Math.max (bllat, brlat));
        double minlon = Math.min (Math.min (tllon, trlon), Math.min (bllon, brlon));
        double maxlon = Math.max (Math.max (tllon, trlon), Math.max (bllon, brlon));

        int minlathalfmin = (int) Math.round (minlat * 60.0) * 2;
        int maxlathalfmin = (int) Math.round (maxlat * 60.0) * 2;
        int minlonhalfmin = (int) Math.round (minlon * 60.0) * 2;
        int maxlonhalfmin = (int) Math.round (maxlon * 60.0) * 2;
        if (maxlonhalfmin < minlonhalfmin) maxlonhalfmin += 360 * 120;

        // loop through all possible lat/lon one minute at a time
        for (int lathalfmin = minlathalfmin; lathalfmin <= maxlathalfmin; lathalfmin += 2) {
            double latdeg = lathalfmin / 120.0;
            for (int lonhalfmin = minlonhalfmin; lonhalfmin <= maxlonhalfmin; lonhalfmin += 2) {
                double londeg = lonhalfmin / 120.0;

                // color if topo not known
                int color = Color.LTGRAY;

                // get ground elevation at that lat/lon position
                // skip if unknown (leaving background black)
                short elevMetres = Topography.getElevMetres (latdeg, londeg);
                if (elevMetres != Topography.INVALID_ELEV) {

                    // compute gap between terrain and altitude we should be flying at for this position
                    double elevft = elevMetres * Lib.FtPerM;  // terrain elevation in feet
                    double flydist = Lib.LatLonDist (runway.lat, runway.lon, latdeg, londeg);
                    if (flydist > gsintdmenm)
                        flydist = gsintdmenm;  // how far away from runway we are
                    double flyarw = flydist / gsintdmenm * (gsaltbin - runway.elev);  // how high above runway we should be flying
                    double gapft = runway.elev + flyarw - elevft;  // actual gap between flying and ground

                    // if flying through dirt, we want full RED color
                    // if gap is at least half distance to runway height, full WHITE color
                    if (gapft < 0.0) {
                        if (gapft < -flyarw) gapft = -flyarw;
                        double hue = gapft / flyarw * Math.PI / 2.0;  // 0(red)..-pi/2(blue)
                        color = Color.rgb ((int) Math.round (Math.cos (hue) * 255),
                                0, (int) Math.round (Math.sin (hue) * -255));
                    } else {
                        if (gapft > flyarw / 2.0) {         // gap more than half way above runway
                            gapft = flyarw / 2.0;           // -- fully WHITE
                        }
                        double hue = gapft / (flyarw / 2.0);  // 0(red)..1(white)
                        color = Color.rgb (255, (int) Math.round (hue * 255),
                                (int) Math.round (hue * 255));
                    }
                }

                // draw topo rectangle
                paint.setColor (color);
                float bmpleft = (float) LatLon2BitmapX (lathalfmin / 120.0, (lonhalfmin - 1) / 120.0);
                float bmpbot  = (float) LatLon2BitmapY ((lathalfmin - 1) / 120.0, lonhalfmin / 120.0);
                float bmprite = (float) LatLon2BitmapX (lathalfmin / 120.0, (lonhalfmin + 1) / 120.0);
                float bmptop  = (float) LatLon2BitmapY ((lathalfmin + 1) / 120.0, lonhalfmin / 120.0);
                canvas.drawRect (bmpleft, bmptop, bmprite, bmpbot, paint);
            }
        }

        /*
         * Get obstructions within area of the chart.
         */
        Rect textbounds = new Rect ();
        int obstructionexpdate = wairToNow.maintView.GetCurentObstructionExpDate ();
        String dbname = "nobudb/obstructions_" + obstructionexpdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
        if (sqldb != null) {
            try {
                Cursor result = sqldb.query (
                        "obstrs", new String[] { "ob_agl", "ob_lat", "ob_lon", "ob_msl" },
                        "ob_lat>" + minlat + " AND ob_lat<" + maxlat + " AND ob_lon>" + minlon +
                                " AND ob_lon<" + maxlon,
                        null, null, null, null, null);
                try {
                    if (result.moveToFirst ()) {
                        paint.setColor (Color.rgb (8, 72, 135));  // dark blue
                        paint.setStrokeWidth (1.0F);
                        paint.setTextAlign (Paint.Align.CENTER);
                        paint.setTextSize (23.0F);
                        if (largeobst == null) {
                            largeobst = new Path ();
                            largeobst.addCircle (0, 0, 4, Path.Direction.CCW);
                            largeobst.moveTo (306-306, 440-460);
                            largeobst.lineTo (295-306, 452-460);
                            largeobst.lineTo (274-306, 464-460);
                            largeobst.lineTo (274-306, 462-460);
                            largeobst.lineTo (292-306, 437-460);
                            largeobst.lineTo (302-306, 404-460);
                            largeobst.lineTo (302-306, 370-460);
                            largeobst.lineTo (306-306, 368-460);
                            largeobst.lineTo (310-306, 370-460);
                            largeobst.lineTo (310-306, 404-460);
                            largeobst.lineTo (320-306, 437-460);
                            largeobst.lineTo (338-306, 462-460);
                            largeobst.lineTo (338-306, 464-460);
                            largeobst.lineTo (317-306, 452-460);
                            largeobst.lineTo (306-306, 440-460);
                            smallobst = new Path ();
                            smallobst.addCircle (0, 0, 4, Path.Direction.CCW);
                            smallobst.moveTo (374-374, 432-463);
                            smallobst.lineTo (361-374, 463-463);
                            smallobst.lineTo (352-374, 463-463);
                            smallobst.lineTo (374-374, 414-463);
                            smallobst.lineTo (396-374, 463-463);
                            smallobst.lineTo (387-374, 463-463);
                            smallobst.lineTo (374-374, 432-463);
                        }
                        canvas.save ();
                        try {
                            float lastcx = 0.0F;
                            float lastcy = 0.0F;
                            do {
                                int    agl = result.getInt (0);
                                double lat = result.getDouble (1);
                                double lon = result.getDouble (2);
                                float  cx  = (float) LatLon2BitmapX (lat, lon);
                                float  cy  = (float) LatLon2BitmapY (lat, lon);
                                canvas.translate (cx - lastcx, cy - lastcy);
                                canvas.drawPath ((agl < 1000) ? smallobst : largeobst, paint);
                                lastcx = cx;
                                lastcy = cy;
                            } while (result.moveToNext ());
                        } finally {
                            canvas.restore ();
                        }
                    }
                } finally {
                    result.close ();
                }
            } catch (Exception e) {
                Log.e (TAG, "error reading " + dbname, e);
            }
        }

        /*
         * Draw runways.
         */
        paint.setColor (Color.BLACK);
        for (Waypoint.Runway rwy : airport.GetRunwayPairs ()) {
            double begBmpX = LatLon2BitmapX (rwy.lat, rwy.lon);
            double begBmpY = LatLon2BitmapY (rwy.lat, rwy.lon);
            double endBmpX = LatLon2BitmapX (rwy.endLat, rwy.endLon);
            double endBmpY = LatLon2BitmapY (rwy.endLat, rwy.endLon);
            float lengthft = rwy.getLengthFt ();
            float widthft  = rwy.getWidthFt ();
            float lengthcp = (float) Math.hypot (endBmpX - begBmpX, endBmpY - begBmpY);
            float widthcp  = lengthcp * widthft / lengthft;
            if (widthcp < 8.0F) widthcp = 8.0F;
            paint.setStrokeWidth (widthcp / 2.0F);
            canvas.drawLine ((float)begBmpX, (float)begBmpY, (float)endBmpX, (float)endBmpY, paint);
        }

        /*
         * Compute needle points.
         *  15nm long
         *  3deg wide each side
         */
        double[] needlells = new double[26];

        // tail of needle on centerline
        needlells[ 6] = Lib.LatHdgDist2Lat    (loclat,         loctc + 180.0, rwynm + 15.0);
        needlells[ 7] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 180.0, rwynm + 15.0);
        // tail of needle left of centerline
        needlells[ 4] = Lib.LatHdgDist2Lat    (loclat,         loctc + 183.0, rwynm + 15.5);
        needlells[ 5] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 183.0, rwynm + 15.5);
        // tail of needle right of centerline
        needlells[ 8] = Lib.LatHdgDist2Lat    (loclat,         loctc + 177.0, rwynm + 15.5);
        needlells[ 9] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 177.0, rwynm + 15.5);

        // tip of needle
        needlells[ 0] = Lib.LatHdgDist2Lat    (loclat,         loctc + 180.0, rwynm + 0.2);
        needlells[ 1] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 180.0, rwynm + 0.2);
        // near tip left of centerline
        needlells[ 2] = Lib.LatHdgDist2Lat    (loclat,         loctc + 183.0, rwynm + 0.5);
        needlells[ 3] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 183.0, rwynm + 0.5);
        // near tip right of centerline
        needlells[10] = Lib.LatHdgDist2Lat    (loclat,         loctc + 177.0, rwynm + 0.5);
        needlells[11] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 177.0, rwynm + 0.5);

        // most way toward tail on centerline
        needlells[12] = Lib.LatHdgDist2Lat    (loclat,         loctc + 180.0, rwynm + 10.0);
        needlells[13] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 180.0, rwynm + 10.0);

        // start of PT barb near numbers
        needlells[18] = Lib.LatHdgDist2Lat    (loclat,         loctc + 180.0, rwynm + 12.0);
        needlells[19] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 180.0, rwynm + 12.0);

        // elbow of PT barb near tail
        needlells[20] = Lib.LatHdgDist2Lat    (loclat,         loctc + 180.0, rwynm + 14.0);
        needlells[21] = Lib.LatLonHdgDist2Lon (loclat, loclon, loctc + 180.0, rwynm + 14.0);

        // end of left 45deg leg of PT barb
        needlells[22] = Lib.LatHdgDist2Lat    (needlells[20],                loctc + 135.0, 2.0);
        needlells[23] = Lib.LatLonHdgDist2Lon (needlells[20], needlells[21], loctc + 135.0, 2.0);

        // end of right 45deg leg of PT barb
        needlells[24] = Lib.LatHdgDist2Lat    (needlells[20],                loctc - 135.0, 2.0);
        needlells[25] = Lib.LatLonHdgDist2Lon (needlells[20], needlells[21], loctc - 135.0, 2.0);

        float[] needlexys = new float[26];
        for (int i = 0; i < 26; i += 2) {
            needlexys[i+0] = (float) LatLon2BitmapX (needlells[i+0], needlells[i+1]);
            needlexys[i+1] = (float) LatLon2BitmapY (needlells[i+0], needlells[i+1]);
        }

        /*
         * Draw localizer needle.
         */
        // needle outline
        paint.setStrokeWidth (3.0F);
        for (int i = 0; i < 12; i += 2) {
            int j = i + 2;
            if (j >= 12) j -= 12;
            canvas.drawLine (needlexys[i+0], needlexys[i+1],
                    needlexys[j+0], needlexys[j+1],
                    paint);
        }

        // line down center
        paint.setStrokeWidth (6.0F);
        canvas.drawLine (needlexys[ 0], needlexys[ 1], needlexys[12], needlexys[13], paint);

        // PT barbs
        canvas.drawLine (needlexys[18], needlexys[19], needlexys[20], needlexys[21], paint);
        canvas.drawLine (needlexys[20], needlexys[21], needlexys[22], needlexys[23], paint);
        canvas.drawLine (needlexys[20], needlexys[21], needlexys[24], needlexys[25], paint);

        // mag course text
        float textsize = (float) Math.hypot (needlexys[6] - needlexys[4], needlexys[7] - needlexys[5]);
        paint.setStrokeWidth (2.0F);
        paint.setTextSize (textsize);
        paint.getTextBounds (locmcstr, 0, locmcstr.length (), textbounds);
        paint.setTextAlign ((locmcbin < 0.0) ? Paint.Align.LEFT : Paint.Align.RIGHT);
        float textheight = textbounds.height ();
        float textrotate = (float) ((loctc < 0.0) ? (loctc - 270.0) : (loctc - 90.0));
        canvas.save ();
        canvas.rotate (textrotate, needlexys[12], needlexys[13]);
        canvas.translate (0.0F, textheight * 0.5F);
        canvas.drawText (locmcstr, needlexys[12], needlexys[13], paint);
        canvas.restore ();

        // glideslope intercept point crosswise line

        // glideslope intercept position
        // glideslope antenna is located 1000' down from near end of runway
        float gsintbmpx = (float) LatLon2BitmapX (gsintlat, gsintlon);
        float gsintbmpy = (float) LatLon2BitmapY (gsintlat, gsintlon);
        float needletailwidth = (float) Math.hypot (needlexys[8] - needlexys[4],
                needlexys[9] - needlexys[5]);
        canvas.save ();
        try {
            if (loctc < 0.0) {
                canvas.rotate ((float) (loctc + 90.0), gsintbmpx, gsintbmpy);
                paint.setTextAlign (Paint.Align.LEFT);
            } else {
                canvas.rotate ((float) (loctc - 90.0), gsintbmpx, gsintbmpy);
                paint.setTextAlign (Paint.Align.RIGHT);
            }
            paint.setStrokeWidth (3.0F);
            canvas.drawLine (gsintbmpx, gsintbmpy - needletailwidth, gsintbmpx, gsintbmpy + needletailwidth, paint);
            paint.setStrokeWidth (2.0F);
            paint.setTextSize (textsize * 0.75F);
            String gsaltstr = " " + gsaltbin + " ft ";
            canvas.drawText (gsaltstr, gsintbmpx, gsintbmpy - needletailwidth + textheight * 0.75F, paint);
            String gsintdmestr = " " + Lib.DoubleNTZ (gsintdmenm) + " DME ";
            canvas.drawText (gsintdmestr, gsintbmpx, gsintbmpy + needletailwidth, paint);
        } finally {
            canvas.restore ();
        }

        // PT in/out heading strings
        String ptlinstr, ptloutstr, ptrinstr, ptroutstr;
        if (loctc < 0.0) {
            ptlinstr  = Lib.Hdg2Str (locmcbin + 135.0) + '\u2192';  // right arrow
            ptloutstr = '\u2190' + Lib.Hdg2Str (locmcbin -  45.0);  // left arrow
            ptrinstr  = Lib.Hdg2Str (locmcbin - 135.0) + '\u2192';  // right arrow
            ptroutstr = '\u2190' + Lib.Hdg2Str (locmcbin +  45.0);  // left arrow
        } else {
            ptlinstr  = Lib.Hdg2Str (locmcbin -  45.0) + '\u2192';  // right arrow
            ptloutstr = '\u2190' + Lib.Hdg2Str (locmcbin + 135.0);  // left arrow
            ptrinstr  = Lib.Hdg2Str (locmcbin +  45.0) + '\u2192';  // right arrow
            ptroutstr = '\u2190' + Lib.Hdg2Str (locmcbin - 135.0);  // left arrow
        }

        canvas.save ();
        canvas.rotate (textrotate - 45.0F, needlexys[22], needlexys[23]);
        canvas.drawText (ptlinstr,  needlexys[22], needlexys[23], paint);
        canvas.translate (0.0F, textsize);
        canvas.drawText (ptloutstr, needlexys[22], needlexys[23], paint);
        canvas.restore ();
        canvas.save ();
        canvas.rotate (textrotate + 45.0F, needlexys[24], needlexys[25]);
        canvas.drawText (ptroutstr, needlexys[24], needlexys[25], paint);
        canvas.translate (0.0F, textsize);
        canvas.drawText (ptrinstr,  needlexys[24], needlexys[25], paint);
        canvas.restore ();

        /*
         * Save in a .GIF so they can print it if they want.
         */
        new SaveGifThread ().start ();

        /*
         * Maybe invert the colors.
         */
        invbitmap = maybeInvertColors (bitmap);

        /*
         * Show DME in lower left corner.
         */
        plateDME.showDMEBox (synthloc);

        /*
         * Init CIFP class.
         * Constructor uses lat/lon <=> bitmap pixel mapping.
         */
        plateCIFP = new PlateCIFP (wairToNow, this, airport, plateid);
    }

    // extract VHF frequencies from given string
    @SuppressWarnings("StringConcatenationInLoop")
    private static String appVHFFreq (String str)
    {
        String ret = "";
        String[] parts = str.split (" ");
        for (String part : parts) {
            try {
                long freq = Math.round (Double.parseDouble (part) * 1000.0);
                if (freq < 118000) continue;
                if (freq > 135999) continue;
                ret += " " + part;
            } catch (NumberFormatException nfe) {
                Lib.Ignored ();
            }
        }
        return ret;
    }

    // compute side-view X given a lat,lon
    private float sideLatLonToX (double lat, double lon)
    {
        double dist = Lib.LatLonDist (lat, lon, runway.endLat, runway.endLon);
        return (float) (dist / sidedist) * (sideleft - siderite) + siderite - 200;
    }

    /**
     * We always reference the synthetic ILS/DME antennae.
     */
    @Override  // IAPPlateImage
    public Waypoint GetRefNavWaypt ()
    {
        return runway.GetSynthLoc ();
    }

    /**
     * Get CIFP segments.
     */
    @Override  // IAPPlateImage
    public void ReadCIFPs ()
    {
        // CIFP-style approach id
        String appid = "I" + runway.number;

        // make a waypoint for the FAF at the glideslope intercept point
        Waypoint.Localizer synthloc = runway.GetSynthLoc ();
        double locmcbinbc = locmcbin + 180.0;
        while (locmcbinbc <=  0.0) locmcbinbc += 360.0;
        while (locmcbinbc > 360.0) locmcbinbc -= 360.0;
        String faf = synthloc.ident + "[" + Lib.DoubleNTZ (locmcbinbc) + "@" + Lib.DoubleNTZ (gsintdmenm);
        fafwaypt = FindWaypoint (faf);

        // make segment for left-turn PT
        plateCIFP.ParseCIFPSegment (appid, "-PT-L",
                "CF,wp=" + faf + ",iaf;" +
                "PI,wp=" + faf + ",toc=" + Math.round (locmcbin * 10.0 - 1350.0) + ",td=L,a=+" + gsaltbin);

        // make segment for right-turn PT
        plateCIFP.ParseCIFPSegment (appid, "-PT-R",
                "CF,wp=" + faf + ",iaf;" +
                "PI,wp=" + faf + ",toc=" + Math.round (locmcbin * 10.0 + 1350.0) + ",td=R,a=+" + gsaltbin);

        // make segment for direct in from elbow of two PTs
        String dir = synthloc.ident + "[" + Lib.DoubleNTZ (locmcbinbc) + "@" + Lib.DoubleNTZ (gsintdmenm * 2.0);
        dirwaypt = FindWaypoint (dir);
        plateCIFP.ParseCIFPSegment (appid, "-DIR-",
                "CF,wp=" + dir + ",iaf;" +
                "CF,wp=" + faf + ",a=+" + gsaltbin);

        // make final approach segment
        double nmfromnumberstogsant = Lib.LatLonDist (runway.lat, runway.lon, synthloc.gs_lat, synthloc.gs_lon);
        double gsfeetabovenumbers = nmfromnumberstogsant * Lib.FtPerNM * Math.tan (Math.toRadians (synthloc.gs_tilt));
        plateCIFP.ParseCIFPSegment (appid, "~f~",
                "CF,wp=" + faf + ",a=G" + gsaltbin + ":" + gsaltbin + ",faf;" +
                "CF,wp=RW" + runway.number + ",mc=" + Math.round (locmcbin * 10.0) +
                        ",a=@" + Math.round (runway.elev + gsfeetabovenumbers));

        // make missed approach segment
        // hold over localizer antenna
        char trafdir = runway.ritraf ? 'R' : 'L';
        plateCIFP.ParseCIFPSegment (appid, "~m~",
                "CF,wp=" + synthloc.ident + ",a=+" + gsaltbin + ";" +
                "HM,wp=" + synthloc.ident + ",rad=" + Math.round (locmcbin * 10.0) +
                        ",td=" + trafdir + ",a=+" + gsaltbin);

        // give navaid name used for the approach
        plateCIFP.ParseCIFPSegment (appid, "~r~", synthloc.ident);
    }

    // used by PlateCIFP,PlateDME
    @Override  // PlateImage
    public Waypoint FindWaypoint (String wayptid)
    {
        switch (wayptid) {
            case "-DIR-": return dirwaypt;
            case "-PT-L":
            case "-PT-R": return fafwaypt;
            default: return super.FindWaypoint (wayptid);
        }
    }

    /**
     * Don't draw any added DME arcs.
     */
    @Override  // IAPPlateImage
    public void DrawDMEArc (Waypoint nav, double beg, double end, double nm)
    { }

    /**
     * Close any open bitmaps so we don't take up too much memory.
     */
    public void CloseBitmaps ()
    {
        // re-center when opened again
        // might be switching between FAAWP1,2 and
        // right side of VirtNav1,2 pages
        firstDraw = true;
    }

    /**
     * Draw the plate image and any other marks on it we want.
     */
    @Override  // View
    public void onDraw (Canvas canvas)
    {
        if (firstDraw) {
            SetBitmapMapping (platewidth, plateheight);
            firstDraw = false;
        }

        /* back display with auto sectional
           - doesn't translate/zoom
           - too messy to read
        double bmpleft = 0;
        double bmprite = platewidth;
        double bmptop  = 0;
        double bmpbot  = plateheight;
        double tlLat = BitmapXY2Lat (bmpleft, bmptop);
        double tlLon = BitmapXY2Lon (bmpleft, bmptop);
        double trLat = BitmapXY2Lat (bmprite, bmptop);
        double trLon = BitmapXY2Lon (bmprite, bmptop);
        double blLat = BitmapXY2Lat (bmpleft, bmpbot);
        double blLon = BitmapXY2Lon (bmpleft, bmpbot);
        double brLat = BitmapXY2Lat (bmprite, bmpbot);
        double brLon = BitmapXY2Lon (bmprite, bmpbot);
        pmap.setup (platewidth, plateheight,
                tlLat, tlLon, trLat, trLon,
                blLat, blLon, brLat, brLon);
        wairToNow.chartView.autoAirChartSEC.DrawOnCanvas (pmap, canvas, this, 0.0);
        */

        /*
         * Display bitmap to canvas.
         */
        ShowSinglePage (canvas, invbitmap);

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
        return true;
    }

    @Override  // GRPPlateImage
    public double LatLon2BitmapX (double lat, double lon)
    {
        double easting = (lon - centerlon) * (Lib.NMPerDeg * Lib.MPerNM) *
                Math.cos (Math.toRadians (lat));
        return (diagrite - diagleft) / 2.0 + easting / mperbmpix + diagleft;
    }

    @Override  // GRPPlateImage
    public double LatLon2BitmapY (double lat, double lon)
    {
        double northing = (lat - centerlat) * (Lib.NMPerDeg * Lib.MPerNM);
        return (diagbot - diagtop) / 2.0 - northing / mperbmpix + diagtop;
    }

    @Override  // GRPPlateImage
    public double BitmapXY2Lat (double bmx, double bmy)
    {
        // bmy = (diagbot - diagtop) / 2.0 - northing / mperbmpix + diagtop
        // bmy - diagtop = (diagbot - diagtop) / 2.0 - northing / mperbmpix
        // northing / mperbmpix = (diagbot - diagtop) / 2.0 - bmy + diagtop
        // northing = ((diagbot - diagtop) / 2.0 - bmy + diagtop) * mperbmpix
        double northing = ((diagbot - diagtop) / 2.0 - bmy + diagtop) * mperbmpix;

        // northing = (lat - centerlat) * (Lib.NMPerDeg * Lib.MPerNM)
        // northing / (Lib.NMPerDeg * Lib.MPerNM) = (lat - centerlat)
        return northing / (Lib.NMPerDeg * Lib.MPerNM) + centerlat;
    }

    @Override  // GRPPlateImage
    public double BitmapXY2Lon (double bmx, double bmy)
    {
        double lat = BitmapXY2Lat (bmx, bmy);

        // bmx = (diagrite - diagleft) / 2.0 + easting / mperbmpix + diagleft
        // bmx - diagleft = (diagrite - diagleft) / 2.0 + easting / mperbmpix
        // bmx - diagleft - (diagrite - diagleft) / 2.0 = easting / mperbmpix
        // (bmx - diagleft - (diagrite - diagleft) / 2.0) * mperbmpix = easting
        double easting = (bmx - diagleft - (diagrite - diagleft) / 2.0) * mperbmpix;

        // easting = (lon - centerlon) * (Lib.NMPerDeg * Lib.MPerNM) * Math.cos (Math.toRadians (lat))
        // easting / (Lib.NMPerDeg * Lib.MPerNM) / Math.cos (Math.toRadians (lat)) = (lon - centerlon)
        return easting / (Lib.NMPerDeg * Lib.MPerNM) / Math.cos (Math.toRadians (lat)) + centerlon;
    }

    /**
     * This runs in the ShadeCirclingAreaThread to scan bitmap for the inverse [C] if necessary
     * then shade in the circling area on the bitmap.
     */
    @Override  // IAPPlateImage
    public void ShadeCirclingAreaWork ()
    { }

    private class SaveGifThread extends Thread {
        @Override
        public void run ()
        {
            File sidir = new File (WairToNow.dbdir, "synth_ilsdme");
            Lib.Ignored (sidir.mkdirs ());
            String base = airport.faaident + "_" + runway.number + "_";
            String name = base + wptexpdate + ".gif";
            File file = new File (sidir, name);
            String path = file.getPath ();
            if (! file.exists ()) {

                // get pixels for the image
                int[] pixels = new int[platewidth*plateheight];
                bitmap.getPixels (pixels, 0, platewidth, 0, 0, platewidth, plateheight);

                // write a .gif to a temp file
                File tmpfile = new File (path + ".tmp");
                Log.i (TAG, "creating " + path);
                try {
                    FileOutputStream fos = new FileOutputStream (tmpfile);
                    try {
                        AnimatedGifEncoder age = new AnimatedGifEncoder ();
                        age.start (fos);
                        age.addFrame (pixels, platewidth, plateheight);
                        age.finish ();
                    } finally {
                        fos.close ();
                    }

                    // successfully written, rename to permanent name
                    if (! tmpfile.renameTo (file)) {
                        throw new IOException ("error renaming from " + tmpfile.getPath ());
                    }

                    // delete any older versions so they don't keep piling up
                    File[] oldfiles = sidir.listFiles ();
                    assert oldfiles != null;
                    for (File oldfile : oldfiles) {
                        String oldname = oldfile.getName ();
                        if (oldname.startsWith (base) && ! oldname.equals (name)) {
                            Lib.Ignored (oldfile.delete ());
                        }
                    }
                } catch (IOException ioe) {
                    Log.w (TAG, "error writing " + name, ioe);
                    Lib.Ignored (tmpfile.delete ());
                    Lib.Ignored (file.delete ());
                }
            }
        }
    }
}
