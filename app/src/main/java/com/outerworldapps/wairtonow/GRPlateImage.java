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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Single-page georeferenced plates.
 */
public abstract class GRPlateImage extends PlateImage implements ExactMapper {
    protected double xfmtfwa, xfmtfwb, xfmtfwc, xfmtfwd, xfmtfwe, xfmtfwf;
    protected double xfmwfta, xfmwftb, xfmwftc, xfmwftd, xfmwfte, xfmwftf;
    protected double mPerBmpPix;

    private Paint rwclPaint;
    private PointD canPoint = new PointD ();
    private RectF canTmpRect = new RectF ();

    public GRPlateImage (WairToNow wtn, Waypoint.Airport apt, String pid, int exp)
    {
        super (wtn, apt, pid, exp);
    }

    /**
     * Since the plate is georeferenced, re-draw it with new GPS location.
     */
    @Override
    public void SetGPSLocation ()
    {
        invalidate ();
    }

    /**
     * Display single-page plate according to current zoom/pan.
     */
    protected void ShowSinglePage (Canvas canvas, Bitmap bitmap)
    {
        /*
         * See where page maps to on canvas.
         */
        canTmpRect.top    = (float) BitmapY2CanvasY (0);
        canTmpRect.bottom = (float) BitmapY2CanvasY (bitmap.getHeight ());
        canTmpRect.left   = (float) BitmapX2CanvasX (0);
        canTmpRect.right  = (float) BitmapX2CanvasX (bitmap.getWidth  ());

        /*
         * Display bitmap to canvas.
         */
        canvas.drawBitmap (bitmap, null, canTmpRect, null);

        /*
         * Draw hash if expired.
         */
        DrawExpiredHash (canvas, canTmpRect);
    }

    /**
     * Draw airplane icon showing current location on plate.
     * Also draw collision circles.
     */
    protected boolean DrawLocationArrow (Canvas canvas, boolean isAptDgm)
    {
        if (!LatLon2BitmapOK ()) return false;

        if (!wairToNow.optionsView.typeBOption.checkBox.isChecked () ||
                (isAptDgm && (wairToNow.currentGPSSpd < 80.0 * Lib.MPerNM / 3600.0))) {
            wairToNow.DrawCollisionPoints (canvas, this);
            double bitmapX = LatLon2BitmapX (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
            double bitmapY = LatLon2BitmapY (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
            canPoint.x = BitmapX2CanvasX (bitmapX);
            canPoint.y = BitmapY2CanvasY (bitmapY);
            wairToNow.DrawLocationArrow (canvas, canPoint, 0.0);
        }
        return true;
    }

    /**
     * Set lat/lon => bitmap transform via two example mappings.
     *   (ilat,ilon) <=> (ipixelx,ipixely)
     *   (jlat,jlon) <=> (jpixelx,jpixely)
     */
    @SuppressWarnings("SameParameterValue")
    protected void SetLatLon2Bitmap (double ilat, double ilon, double jlat, double jlon,
                                     int ipixelx, int ipixely, int jpixelx, int jpixely)
    {
        double avglatcos = Math.cos (Math.toRadians (airport.lat));

        //noinspection UnnecessaryLocalVariable
        double inorthing = ilat;                // 60nm units north of equator
        double ieasting  = ilon * avglatcos;    // 60nm units east of prime meridian
        //noinspection UnnecessaryLocalVariable
        double jnorthing = jlat;                // 60nm units north of equator
        double jeasting  = jlon * avglatcos;    // 60nm units east of prime meridian

        // determine transform to convert northing/easting to pixel

        //                     [ wfta' wftb' 0 ]
        //  [ east north 1 ] * [ wftc' wftd' 0 ] = [ pixx pixy 1 ]
        //                     [ wfte' wftf' 1 ]

        //   wfta * lon + wftc * lat + wfte = pixx  as given in LatLon2BitmapX()
        //   wftb * lon + wftd * lat + wftf = pixy  as given in LatLon2BitmapY()

        //   wfta' * easting + wftc' * northing + wfte' = pixx
        //   wftb' * easting + wftd' * northing + wftf' = pixy

        // we want to allow only scaling, rotation, translation, but we also want to invert pixy axis:
        //    wfta' + wftd' = 0
        //    wftb' - wftc' = 0

        double[][] mat = new double[][] {
                new double[] { 1,  0, 0, 1, 0, 0, 0 },
                new double[] { 0, -1, 1, 0, 0, 0, 0 },
                new double[] { ieasting, 0, inorthing, 0, 1, 0, ipixelx },
                new double[] { 0, ieasting, 0, inorthing, 0, 1, ipixely },
                new double[] { jeasting, 0, jnorthing, 0, 1, 0, jpixelx },
                new double[] { 0, jeasting, 0, jnorthing, 0, 1, jpixely }
        };
        Lib.RowReduce (mat);

        // save transformation values for LatLon2Bitmap{X,Y}()

        //                  [ wfta wftb 0 ]
        //  [ lon lat 1 ] * [ wftc wftd 0 ] = [ pixx pixy 1 ]
        //                  [ wfte wftf 1 ]

        xfmwfta = mat[0][6];
        xfmwftb = mat[1][6];
        xfmwftc = mat[2][6];
        xfmwftd = mat[3][6];
        xfmwfte = mat[4][6];
        xfmwftf = mat[5][6];

        // save real-world metres per bitmap pixel
        // xfmwft{a,b,c,d} are in pixels/degree

        mPerBmpPix = Lib.NMPerDeg * Lib.MPerNM / Math.hypot (xfmwftc, xfmwftd);

        // convert the wfta',wftb' to wfta,wftb include factor to convert longitude => easting
        // wftc',wftd',wfte',wftf' are the same as wftc,wftd,wfte,wftf

        xfmwfta *= avglatcos;
        xfmwftb *= avglatcos;

        // invert for reverse transform

        //                  [ wfta wftb 0 ]
        //  [ lon lat 1 ] * [ wftc wftd 0 ] = [ pixx pixy 1 ]
        //                  [ wfte wftf 1 ]

        //                  [ wfta wftb 0 ]   [ tfwa tfwb 0 ]                     [ tfwa tfwb 0 ]
        //  [ lon lat 1 ] * [ wftc wftd 0 ] * [ tfwc tfwd 0 ] = [ pixx pixy 1 ] * [ tfwc tfwd 0 ]
        //                  [ wfte wftf 1 ]   [ tfwe tfwf 1 ]                     [ tfwe tfwf 1 ]

        //                                    [ tfwa tfwb 0 ]
        //  [ lon lat 1 ] = [ pixx pixy 1 ] * [ tfwc tfwd 0 ]
        //                                    [ tfwe tfwf 1 ]

        //   tfwa * pixx + tfwc * pixy + tfwe = lon
        //   tfwb * pixx + tfwd * pixy + tfwf = lat

        double[][] rev = new double[][] {
            new double[] { xfmwfta, xfmwftb, 0, 1, 0, 0 },
            new double[] { xfmwftc, xfmwftd, 0, 0, 1, 0 },
            new double[] { xfmwfte, xfmwftf, 1, 0, 0, 1 },
        };
        Lib.RowReduce (rev);

        xfmtfwa = rev[0][3];
        xfmtfwb = rev[0][4];
        xfmtfwc = rev[1][3];
        xfmtfwd = rev[1][4];
        xfmtfwe = rev[2][3];
        xfmtfwf = rev[2][4];

        // return rotation angle
        //return Math.atan2 (xfmwftb, xfmwfta);
    }

    // ExactMapper
    @Override  // ExactMapper
    public boolean LatLon2CanPixExact (double lat, double lon, PointD canpix)
    {
        if (! LatLon2BitmapOK ()) return false;
        canpix.x = BitmapX2CanvasX (LatLon2BitmapX (lat, lon));
        canpix.y = BitmapY2CanvasY (LatLon2BitmapY (lat, lon));
        return true;
    }

    @Override  // ExactMapper
    public void CanPix2LatLonExact (double canvasPixX, double canvasPixY, LatLon ll)
    {
        double bmpx = CanvasX2BitmapX (canvasPixX);
        double bmpy = CanvasY2BitmapY (canvasPixY);
        ll.lat = BitmapXY2Lat (bmpx, bmpy);
        ll.lon = BitmapXY2Lat (bmpx, bmpy);
    }

    @Override  // ExactMapper
    public double CanPixPerNMAprox ()
    {
        double curlat  = wairToNow.currentGPSLat;
        double curlon  = wairToNow.currentGPSLon;
        double curhdg  = wairToNow.currentGPSHdg;
        double curcanx = BitmapX2CanvasX (LatLon2BitmapX (curlat, curlon));
        double curcany = BitmapY2CanvasY (LatLon2BitmapY (curlat, curlon));
        double mahlat  = Lib.LatHdgDist2Lat (curlat, curhdg, 1.0);
        double mahlon  = Lib.LatLonHdgDist2Lon (curlat, curlon, curhdg, 1.0);
        double mahcanx = BitmapX2CanvasX (LatLon2BitmapX (mahlat, mahlon));
        double mahcany = BitmapY2CanvasY (LatLon2BitmapY (mahlat, mahlon));
        return Math.hypot (curcanx - mahcanx, curcany - mahcany);
    }

    /**
     * Lat/Lon => gif pixel
     */
    protected boolean LatLon2BitmapOK ()
    {
        return (xfmwfte != 0.0) || (xfmwftf != 0.0);
    }
    protected double LatLon2BitmapX (double lat, double lon)
    {
        return xfmwfta * lon + xfmwftc * lat + xfmwfte;
    }
    protected double LatLon2BitmapY (double lat, double lon)
    {
        return xfmwftb * lon + xfmwftd * lat + xfmwftf;
    }

    /**
     * Gif pixel => Lat/Lon
     */
    protected double BitmapXY2Lat (double bmx, double bmy)
    {
        return xfmtfwb * bmx + xfmtfwd * bmy + xfmtfwf;
    }
    protected double BitmapXY2Lon (double bmx, double bmy)
    {
        return xfmtfwa * bmx + xfmtfwc * bmy + xfmtfwe;
    }

    /**
     * Draw lines down the centerline of runways.
     */
    protected void DrawRunwayCenterlines (Canvas canvas)
    {
        if (rwclPaint == null) {
            rwclPaint = new Paint ();
            rwclPaint.setColor (Color.MAGENTA);
            rwclPaint.setStrokeWidth (wairToNow.thinLine / 2.0F);
            rwclPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        }

        for (Waypoint.Runway rwy : airport.GetRunwayPairs ()) {
            double begBmpX = LatLon2BitmapX (rwy.lat, rwy.lon);
            double begBmpY = LatLon2BitmapY (rwy.lat, rwy.lon);
            double endBmpX = LatLon2BitmapX (rwy.endLat, rwy.endLon);
            double endBmpY = LatLon2BitmapY (rwy.endLat, rwy.endLon);
            double begCanX = BitmapX2CanvasX (begBmpX);
            double begCanY = BitmapY2CanvasY (begBmpY);
            double endCanX = BitmapX2CanvasX (endBmpX);
            double endCanY = BitmapY2CanvasY (endBmpY);
            canvas.drawLine ((float) begCanX, (float) begCanY, (float) endCanX, (float) endCanY, rwclPaint);
        }
    }
}
