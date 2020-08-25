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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

import java.util.Collection;
import java.util.HashMap;

/**
 * Display a runway diagram made up from runway information,
 * and backed by OpenStreetMap tiles.
 */
@SuppressLint("ViewConstructor")
public class RWYPlateImage extends GRPlateImage implements DisplayableChart.Invalidatable {

    // one of these per runway half
    private class RwyInfo {
        public Waypoint.Runway rwywp;

        public int surfColor;

        public int begLeftBmpX;
        public int begLeftBmpY;
        public int begRiteBmpX;
        public int begRiteBmpY;

        public int midLeftBmpX;
        public int midLeftBmpY;
        public int midRiteBmpX;
        public int midRiteBmpY;

        public int numsTopBmpX;
        public int numsTopBmpY;

        public Rect numBounds = new Rect ();

        public RwyInfo (Waypoint.Runway rwy)
        {
            rwywp = rwy;

            // get runway dimensions in NM
            double lengthNM = (double) rwy.getLengthFt () / Lib.FtPerM / Lib.MPerNM;
            double widthNM  = (double) rwy.getWidthFt  () / Lib.FtPerM / Lib.MPerNM;

            // get runway midpoint and heading from this end to the midpoint
            double midLat = (rwy.lat + rwy.endLat) / 2.0;
            double midLon = Lib.AvgLons (rwy.lon, rwy.endLon);
            double hdgDeg = Lib.LatLonTC (rwy.lat, rwy.lon, midLat, midLon);

            // get lat/lon on centerline of beginning of runway
            double begLat = Lib.LatHdgDist2Lat (midLat, hdgDeg + 180.0, lengthNM / 2.0);
            double begLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg + 180.0, lengthNM / 2.0);

            // get lat/lon of left & right sides of this end of runway
            double begLeftLat = Lib.LatHdgDist2Lat (begLat, hdgDeg - 90.0, widthNM / 2.0);
            double begLeftLon = Lib.LatLonHdgDist2Lon (begLat, begLon, hdgDeg - 90.0, widthNM / 2.0);

            double begRiteLat = Lib.LatHdgDist2Lat (begLat, hdgDeg + 90.0, widthNM / 2.0);
            double begRiteLon = Lib.LatLonHdgDist2Lon (begLat, begLon, hdgDeg + 90.0, widthNM / 2.0);

            // get lat/lon of left & right sides of midpoint on runway
            double midLeftLat = Lib.LatHdgDist2Lat (midLat, hdgDeg - 90.0, widthNM / 2.0);
            double midLeftLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg - 90.0, widthNM / 2.0);

            double midRiteLat = Lib.LatHdgDist2Lat (midLat, hdgDeg + 90.0, widthNM / 2.0);
            double midRiteLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg + 90.0, widthNM / 2.0);

            // compute corresponding bitmap pixels
            begLeftBmpX = (int) Math.round (LatLon2BitmapX (begLeftLat, begLeftLon));
            begLeftBmpY = (int) Math.round (LatLon2BitmapY (begLeftLat, begLeftLon));
            begRiteBmpX = (int) Math.round (LatLon2BitmapX (begRiteLat, begRiteLon));
            begRiteBmpY = (int) Math.round (LatLon2BitmapY (begRiteLat, begRiteLon));

            midLeftBmpX = (int) Math.round (LatLon2BitmapX (midLeftLat, midLeftLon));
            midLeftBmpY = (int) Math.round (LatLon2BitmapY (midLeftLat, midLeftLon));
            midRiteBmpX = (int) Math.round (LatLon2BitmapX (midRiteLat, midRiteLon));
            midRiteBmpY = (int) Math.round (LatLon2BitmapY (midRiteLat, midRiteLon));

            // compute top center of runway numbers text
            numsTopBmpX = (begLeftBmpX + begRiteBmpX) / 2;
            numsTopBmpY = (begLeftBmpY + begRiteBmpY) / 2;
            rwyPaint.getTextBounds (rwy.number, 0, rwy.number.length (), numBounds);

            // get runway surface color
            String[] infoLine = rwy.getInfoLine ();
            surfColor = Color.LTGRAY;
            for (String infoWord : infoLine) {
                if (infoWord.contains ("TURF")) {
                    surfColor = Color.GREEN;
                    break;
                }
            }
        }
    }

    // one of these per waypoint within the display area
    private class WpInfo {
        public double bmpX, bmpY;
        public String ident;
        public String type;

        public WpInfo (Waypoint wp)
        {
            bmpX  = LatLon2BitmapX (wp.lat, wp.lon);
            bmpY  = LatLon2BitmapY (wp.lat, wp.lon);
            ident = wp.ident;
            type  = wp.GetTypeAbbr ();
        }
    }

    private final static double defnm = 0.75;
    private final static double padnm = 0.25;

    private double longestNM;
    private double qtrTextHeight;
    private HashMap<String,RwyInfo> runways = new HashMap<> ();
    private HashMap<String,WpInfo> waypoints = new HashMap<> ();
    private int mappedCanWidth;
    private int mappedCanHeight;
    private int synthWidth;
    private int synthHeight;
    private Paint rwyPaint;
    private Path rwyPath = new Path ();
    private PixelMapper pmap = new PixelMapper ();

    public RWYPlateImage (WairToNow wtn, Waypoint.Airport apt, String pid, int exp)
    {
        super (wtn, apt, pid, exp);
        rwyPaint = new Paint ();
        rwyPaint.setStrokeWidth (2);
        rwyPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        rwyPaint.setTextAlign (Paint.Align.CENTER);
        rwyPaint.setTextSize (wairToNow.textSize);
        Rect bounds = new Rect ();
        rwyPaint.getTextBounds ("M", 0, 1, bounds);
        qtrTextHeight = bounds.height () / 4.0;

        double avglatcos = Math.cos (Math.toRadians (airport.lat));
        double minlat = airport.lat;
        double maxlat = airport.lat;
        double minlon = airport.lon;
        double maxlon = airport.lon;

        Collection<Waypoint.Runway> rwywps = airport.GetRunways ().values ();
        if (rwywps.isEmpty ()) {
            minlat -= defnm / Lib.NMPerDeg;
            maxlat += defnm / Lib.NMPerDeg;
            minlon -= defnm / Lib.NMPerDeg / avglatcos;
            maxlon += defnm / Lib.NMPerDeg / avglatcos;
            longestNM = (defnm - padnm) * 2.0;
        } else {
            for (Waypoint.Runway rwywp : rwywps) {
                if (minlat > rwywp.lat) minlat = rwywp.lat;
                if (maxlat < rwywp.lat) maxlat = rwywp.lat;
                if (minlon > rwywp.lon) minlon = rwywp.lon;
                if (maxlon < rwywp.lon) maxlon = rwywp.lon;
                double lenNM = rwywp.getLengthFt () / Lib.FtPerM / Lib.MPerNM;
                if (longestNM < lenNM) longestNM = lenNM;
            }
            minlat -= padnm / Lib.NMPerDeg;
            maxlat += padnm / Lib.NMPerDeg;
            minlon -= padnm / Lib.NMPerDeg / avglatcos;
            maxlon += padnm / Lib.NMPerDeg / avglatcos;
        }

        synthWidth  = (int) ((maxlon - minlon) * 32768 * avglatcos + 0.5);
        synthHeight = (int) ((maxlat - minlat) * 32768 + 0.5);

        // set up lat/lon => bitmap transformation
        SetLatLon2Bitmap (
                minlat, minlon, // lower left
                maxlat, maxlon, // upper right
                0, synthHeight, // lower left
                synthWidth, 0); // upper right

        // now that mapping is set up, save runway placement info
        for (Waypoint.Runway rwywp : airport.GetRunways ().values ()) {
            runways.put (rwywp.number, new RwyInfo (rwywp));
        }

        // find waypoints within the area depicted
        // we don't want fixes as there are a lot of them adding clutter
        Collection<Waypoint> wps = new Waypoint.Within (wairToNow).Get (minlat, maxlat, minlon, maxlon);
        for (Waypoint wp : wps) {
            String type = wp.GetType ();
            if (!type.startsWith ("FIX")) waypoints.put (wp.ident, new WpInfo (wp));
        }
    }

    public void CloseBitmaps ()
    {
        wairToNow.openStreetMap.CloseBitmaps ();
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
         * Draw Open Street Map as background.
         */
        int canwidth  = getWidth ();
        int canheight = getHeight ();
        boolean landscape = (canwidth > canheight);

        // get same pixel sizes used during prefetch = whole screen
        int diswidth  = wairToNow.displayWidth;
        int disheight = wairToNow.displayHeight;
        int lower  = Math.min (diswidth, disheight);
        int higher = diswidth ^ disheight ^ lower;
        diswidth  = landscape ? higher : lower;
        disheight = landscape ? lower : higher;

        // draw using whole screen size so it will use prefetched scaling
        // it will be smaller because of button row
        // also accounts for any user panning/zooming
        CalcOSMBackground (diswidth, disheight);

        canvas.save ();
        try {
            // translate to draw centered on canvas (assuming user has not panned/zoomed it)
            canvas.translate ((canwidth - diswidth) / 2.0F, (canheight - disheight) / 2.0F);

            // draw tiles and copyright message
            wairToNow.openStreetMap.Draw (canvas, pmap, this, (canheight + disheight) / 2.0F);

            /*
             * Show debugging lines through airport reference point.
             */
            /*{
                rwyPaint.setColor (Color.WHITE);
                double aptCanX = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon));
                double aptCanY = BitmapY2CanvasY (LatLon2BitmapY (airport.lat, airport.lon));
                canvas.drawLine (0, aptCanY, width, aptCanY, rwyPaint);
                canvas.drawLine (aptCanX, 0, aptCanX, height, rwyPaint);
            }*/

            /*
             * Draw scale.
             */
            /*
            rwyPaint.setColor (Color.CYAN);
            double aptlatcos = Math.cos (Math.toRadians (airport.lat));
            double smFactor  = wairToNow.optionsView.ktsMphOption.getAlt () ? Lib.SMPerNM : 1.0;
            double canx0     = BitmapX2CanvasX (0);
            double cany0     = BitmapY2CanvasY (synthHeight);
            double canx0lim  = canx0 + metrics.xdpi / 4;
            double cany0lim  = cany0 - metrics.ydpi / 4;
            int   nsteps    = (int) Math.ceil (longestNM * smFactor / stepMi / 2.0);
            for (int step = 0; step <= nsteps; step ++) {
                String str = Double.toString (step * stepMi);
                int strlen = str.length ();

                double dlatdeg = step * stepMi / smFactor / Lib.NMPerDeg;
                double dlondeg = dlatdeg / aptlatcos;

                // left column
                rwyPaint.setTextAlign (Paint.Align.LEFT);
                double cany1 = BitmapY2CanvasY (LatLon2BitmapY (airport.lat + dlatdeg, airport.lon)) + qtrTextHeight * 2;
                canvas.drawText (str, 0, strlen, canx0, cany1, rwyPaint);
                double cany2 = BitmapY2CanvasY (LatLon2BitmapY (airport.lat - dlatdeg, airport.lon)) + qtrTextHeight * 2;
                if (cany2 < cany0lim) canvas.drawText (str, 0, strlen, canx0, cany2, rwyPaint);

                // bottom row
                rwyPaint.setTextAlign (Paint.Align.CENTER);
                double canx1 = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon + dlondeg));
                canvas.drawText (str, 0, strlen, canx1, cany0, rwyPaint);
                double canx2 = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon - dlondeg));
                if (canx2 > canx0lim) canvas.drawText (str, 0, strlen, canx2, cany0, rwyPaint);
            }
            */

            /*
             * Draw rectangles for each runway.
             */
            for (RwyInfo rwy : runways.values ()) {

                // compute corresponding canvas pixels
                double begLeftCanX = BitmapX2CanvasX (rwy.begLeftBmpX);
                double begLeftCanY = BitmapY2CanvasY (rwy.begLeftBmpY);
                double begRiteCanX = BitmapX2CanvasX (rwy.begRiteBmpX);
                double begRiteCanY = BitmapY2CanvasY (rwy.begRiteBmpY);

                double midLeftCanX = BitmapX2CanvasX (rwy.midLeftBmpX);
                double midLeftCanY = BitmapY2CanvasY (rwy.midLeftBmpY);
                double midRiteCanX = BitmapX2CanvasX (rwy.midRiteBmpX);
                double midRiteCanY = BitmapY2CanvasY (rwy.midRiteBmpY);

                // draw rectangle for this runway half
                rwyPath.reset ();
                rwyPath.moveTo ((float) begLeftCanX, (float) begLeftCanY);
                rwyPath.lineTo ((float) begRiteCanX, (float) begRiteCanY);
                rwyPath.lineTo ((float) midRiteCanX, (float) midRiteCanY);
                rwyPath.lineTo ((float) midLeftCanX, (float) midLeftCanY);
                rwyPath.lineTo ((float) begLeftCanX, (float) begLeftCanY);

                rwyPaint.setColor (rwy.surfColor);
                canvas.drawPath (rwyPath, rwyPaint);
            }

            /*
             * Draw waypoints.
             */
            rwyPaint.setColor (Color.MAGENTA);
            rwyPaint.setTextAlign (Paint.Align.LEFT);
            for (WpInfo wpInfo : waypoints.values ()) {
                double canX = BitmapX2CanvasX (wpInfo.bmpX);
                double canY = BitmapY2CanvasY (wpInfo.bmpY);
                canvas.drawCircle ((float) canX, (float) canY, (float) qtrTextHeight, rwyPaint);
                canX += qtrTextHeight * 2;
                canY -= qtrTextHeight;
                canvas.drawText (wpInfo.ident, 0, wpInfo.ident.length (), (float) canX, (float) canY, rwyPaint);
                canY += qtrTextHeight * 6;
                canvas.drawText (wpInfo.type, 0, wpInfo.type.length (), (float) canX, (float) canY, rwyPaint);
            }

            /*
             * Draw runway numbers.
             */
            rwyPaint.setColor (Color.RED);
            rwyPaint.setTextAlign (Paint.Align.CENTER);
            for (RwyInfo rwy : runways.values ()) {
                canvas.save ();
                double canX = BitmapX2CanvasX (rwy.numsTopBmpX);
                double canY = BitmapY2CanvasY (rwy.numsTopBmpY);
                double deg  = rwy.rwywp.trueHdg;
                canvas.rotate ((float) deg, (float) canX, (float) canY);
                canvas.drawText (rwy.rwywp.number, 0, rwy.rwywp.number.length (),
                        (float) canX, (float) (canY + rwy.numBounds.height () * 1.5), rwyPaint);
                canvas.restore ();
            }

            /*
             * Draw airplane.
             */
            DrawLocationArrow (canvas, true);
        } finally {
            canvas.restore ();
        }
    }

    /**
     * Calculate OpenStreetMap tile mapping to the canvas.
     * @param canWidth  = width of the canvas being drawn on
     * @param canHeight = height of the canvas being drawn on
     * returns with pmap filled in with pixel mapping
     */
    public void CalcOSMBackground (int canWidth, int canHeight)
    {
        /*
         * If first time calling onDraw() or if screen is flipped landscape<->portrait,
         * set up initial bitmap=>canvas mapping.  User can then pan/zoom after this.
         * This mapping shows airport centered on screen with a little margin around runways.
         */
        if ((mappedCanWidth != canWidth) || (mappedCanHeight != canHeight)) {
            mappedCanWidth  = canWidth;
            mappedCanHeight = canHeight;
            SetBitmapMapping2 (synthWidth, synthHeight, canWidth, canHeight);
        }

        double leftbmx = CanvasX2BitmapX (0);
        double ritebmx = CanvasX2BitmapX (canWidth);
        double topbmy  = CanvasY2BitmapY (0);
        double botbmy  = CanvasY2BitmapY (canHeight);

        double tllat = BitmapXY2Lat (leftbmx, topbmy);
        double tllon = BitmapXY2Lon (leftbmx, topbmy);
        double trlat = BitmapXY2Lat (ritebmx, topbmy);
        double trlon = BitmapXY2Lon (ritebmx, topbmy);
        double bllat = BitmapXY2Lat (leftbmx, botbmy);
        double bllon = BitmapXY2Lon (leftbmx, botbmy);
        double brlat = BitmapXY2Lat (ritebmx, botbmy);
        double brlon = BitmapXY2Lon (ritebmx, botbmy);

        pmap.setup (canWidth, canHeight, tllat, tllon, trlat, trlon, bllat, bllon, brlat, brlon);
    }

    /**
     * Get default (not zoomed or panned) runway diagram mapping of OpenStreetMap tiles.
     */
    public static PixelMapper GetRWYPlateImageDefaultOSMMapping (Waypoint.Airport apt, WairToNow wairToNow, boolean landscape)
    {
        RWYPlateImage rpi = new RWYPlateImage (wairToNow, apt, "RWY-RUNWAY", 99999999);
        int width  = wairToNow.displayWidth;
        int height = wairToNow.displayHeight;
        int lower  = Math.min (width, height);
        int higher = width ^ height ^ lower;
        width  = landscape ? higher : lower;
        height = landscape ? lower : higher;
        rpi.CalcOSMBackground (width, height);
        return rpi.pmap;
    }
}
