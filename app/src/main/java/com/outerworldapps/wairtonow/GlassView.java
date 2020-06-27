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
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Display a glass cockpit panel.
 */
@SuppressLint("ViewConstructor")
public class GlassView
        extends View
        implements WairToNow.CanBeMainView {
    private static final double APPMODEGS   = 3.25;   // approach mode glide slope degrees
    private static final double GSFTABVTH   = 25.0;   // glideslope feet above threshold
    private static final double HSISCALEAPP = 0.5;    // HSI scale full deflection = 0.5nm
    private static final double HSISCALEENR = 2.0;    // HSI scale full deflection = 2.0nm
    public  static final int   STDRATETURN = 3;       // degrees per second

    private static final CompareRWNumbers compareRWNumbers = new CompareRWNumbers ();

    private double magvariation;
    private int posIndex = 0;
    private int gsalt, tdze;
    private Paint dgPaintBack    = new Paint ();
    private Paint dgPaintHSI     = new Paint ();
    private Paint dgPaintHSV     = new Paint ();
    private Paint dgPaintMaj     = new Paint ();
    private Paint dgPaintMid     = new Paint ();
    private Paint dgPaintMin     = new Paint ();
    private Paint dgPaintNum     = new Paint ();
    private Paint dgPaintTri     = new Paint ();
    private Paint dgPaintVal     = new Paint ();
    private Paint nsPaintNum     = new Paint ();
    private Paint nsPaintVal     = new Paint ();
    private Paint rwayPaint      = new Paint ();
    private Paint rwSelPaint     = new Paint ();
    private Position[] positions = new Position[] { new Position (), new Position () };
    private Rect dtwbBounds      = new Rect ();
    private RWNumber appRunway;
    private RWNumber[] rwNumbers;
    private WairToNow wairToNow;
    private Waypoint.Airport dstAirport;

    public GlassView (WairToNow na)
    {
        super (na);

        wairToNow = na;

        // directional gyro paints
        dgPaintBack.setColor (Color.BLACK);
        dgPaintBack.setStyle (Paint.Style.FILL);
        dgPaintHSI.setColor (Color.MAGENTA);
        dgPaintHSI.setStyle (Paint.Style.FILL);
        dgPaintHSI.setStrokeWidth (5);
        dgPaintHSV.setColor (Color.MAGENTA);
        dgPaintHSV.setStyle (Paint.Style.FILL);
        dgPaintHSV.setStrokeWidth (3);
        dgPaintHSV.setTextAlign (Paint.Align.CENTER);
        dgPaintHSV.setTextSize (wairToNow.textSize);
        dgPaintMaj.setColor (Color.GREEN);
        dgPaintMaj.setStyle (Paint.Style.STROKE);
        dgPaintMaj.setStrokeWidth (5);
        dgPaintMid.setColor (Color.WHITE);
        dgPaintMid.setStyle (Paint.Style.STROKE);
        dgPaintMid.setStrokeWidth (5);
        dgPaintMin.setColor (Color.WHITE);
        dgPaintMin.setStyle (Paint.Style.STROKE);
        dgPaintMin.setStrokeWidth (3);
        dgPaintNum.setColor (Color.GREEN);
        dgPaintNum.setStyle (Paint.Style.FILL);
        dgPaintNum.setStrokeWidth (2);
        dgPaintNum.setTextAlign (Paint.Align.CENTER);
        dgPaintNum.setTextSize (wairToNow.textSize);
        dgPaintTri.setColor (Color.YELLOW);
        dgPaintTri.setStyle (Paint.Style.STROKE);
        dgPaintTri.setStrokeWidth (1);
        dgPaintVal.setColor (Color.GREEN);
        dgPaintVal.setStyle (Paint.Style.FILL);
        dgPaintVal.setStrokeWidth (2);
        dgPaintVal.setTextAlign (Paint.Align.CENTER);
        dgPaintVal.setTextSize (wairToNow.textSize);

        // numeric strip paints
        nsPaintNum.setColor (Color.WHITE);
        nsPaintNum.setStyle (Paint.Style.FILL);
        nsPaintNum.setStrokeWidth (2);
        nsPaintNum.setTextAlign (Paint.Align.CENTER);
        nsPaintNum.setTextSize (wairToNow.textSize * 0.875F);
        nsPaintVal.setColor (Color.GREEN);
        nsPaintVal.setStyle (Paint.Style.FILL);
        nsPaintVal.setStrokeWidth (2);
        nsPaintVal.setTextAlign (Paint.Align.CENTER);
        nsPaintVal.setTextSize (wairToNow.textSize);

        // approach runway selector paints
        rwayPaint.setColor (Color.RED);
        rwayPaint.setStyle (Paint.Style.FILL);
        rwayPaint.setStrokeWidth (2);
        rwayPaint.setTextAlign (Paint.Align.CENTER);
        rwayPaint.setTextSize (wairToNow.textSize);
        rwSelPaint.setColor (Color.GREEN);
        rwSelPaint.setStyle (Paint.Style.FILL);
        rwSelPaint.setStrokeWidth (2);
        rwSelPaint.setTextAlign (Paint.Align.CENTER);
        rwSelPaint.setTextSize (wairToNow.textSize);
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Glass";
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return true;
    }

    /**
     * The GPS has new position information for us.
     * Save it and update screen.
     */
    public void SetGPSLocation ()
    {
        if (++ posIndex == positions.length) posIndex = 0;
        Position p  = positions[posIndex];
        p.altitude  = wairToNow.currentGPSAlt;
        p.heading   = wairToNow.currentGPSHdg;
        p.latitude  = wairToNow.currentGPSLat;
        p.longitude = wairToNow.currentGPSLon;
        p.magvar    = wairToNow.currentMagVar;
        p.speed     = wairToNow.currentGPSSpd;
        p.time      = wairToNow.currentGPSTime;
        invalidate ();
    }

    /**
     * What to show when the back button is pressed
     */
    @Override  // WairToNow.CanBeMainView
    public View GetBackPage ()
    {
        return wairToNow.chartView;
    }

    /**
     * The screen is about to be made current.
     */
    @SuppressLint("SourceLockedOrientationActivity")
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    { }

    /**
     * This screen is no longer current.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    { }

    /**
     * Tab is being re-clicked when already active.
     */
    @Override  // WairToNow.CanBeMainView
    public void ReClicked ()
    { }

    /**
     * Callback for mouse events on the image.
     * We use this for scrolling the map around.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (@NonNull MotionEvent event)
    {
        switch (event.getAction ()) {
            case MotionEvent.ACTION_DOWN: {
                if (rwNumbers != null) {
                    int x = (int)event.getX ();
                    int y = (int)event.getY ();
                    for (RWNumber rwn : rwNumbers) {
                        if (rwn.box.contains (x, y)) {
                            appRunway = rwn;
                            invalidate ();
                            break;
                        }
                    }
                }
                break;
            }
        }
        return true;
    }

    /**
     * Callback to draw the instruments on the screen.
     */
    @Override
    protected void onDraw (Canvas canvas)
    {
        Position currPos = positions[posIndex];
        magvariation = currPos.magvar;

        int canvasWidth  = getWidth ();   // eg (600,1024)
        int canvasHeight = getHeight ();
        canvas.drawColor (Color.GRAY);

        /*
         * Heading comes directly from most recent GPS position report.
         * Units are degrees.
         */
        double truehdg = currPos.heading;

        /*
         * Get current altitude in metres.
         */
        double altitude = currPos.altitude;

        /*
         * Calculate magnetic heading.
         */
        double maghdg = truehdg + magvariation;

        /*
         * Fit the instruments to the page.
         */
        int ahRadius  = canvasHeight *  7 / 32;
        int dgRadius  = canvasHeight *  5 / 32;
        int aspHeight = canvasHeight * 24 / 32;
        int altHeight = canvasHeight * 24 / 32;

        int ahCentY   = canvasHeight *  8 / 32;
        int aspCentY  = canvasHeight * 16 / 32;
        int altCentY  = canvasHeight * 16 / 32;
        int dstCentY  = canvasHeight * 33 / 64;
        int dgCentY   = canvasHeight * 24 / 32;
        int appRwayY  = canvasHeight * 31 / 32;

        int aspCentX  = canvasWidth  *  3 / 32;
        int aspWidth  = canvasWidth  *  9 / 64;
        int dstCentX  = canvasWidth  * 16 / 32;
        int altWidth  = canvasWidth  *  9 / 64;
        int ahCentX   = canvasWidth  * 16 / 32;
        int dgCentX   = canvasWidth  * 16 / 32;
        int altCentX  = canvasWidth  * 29 / 32;

        DrawChart (canvas, ahCentX, ahCentY, ahRadius, truehdg);

        double spdflt = currPos.speed * Lib.KtPerMPS;
        if (wairToNow.optionsView.ktsMphOption.getAlt ()) spdflt *= Lib.SMPerNM;
        int spdint = (int) Math.round (spdflt);
        DrawNumericStrip (canvas, aspCentX, aspCentY, aspHeight, aspWidth, spdint, 10, 8, 0, -1, -1);

        int feet = (int) Math.round (altitude * Lib.FtPerM);
        DrawNumericStrip (canvas, altCentX, altCentY, altHeight, altWidth, feet, -100, 8, -999998, gsalt, tdze);

        Waypoint stepahead = DrawDestinationInfo (canvas, dstCentX, dstCentY, currPos);

        DrawDirectionalGyro (canvas, dgCentX, dgCentY, dgRadius, maghdg, stepahead);

        /*
         * Display destination approach selector boxes.
         */
        Waypoint clDest = wairToNow.chartView.clDest;
        if (clDest != null) {
            if (!(clDest instanceof Waypoint.Airport)) clDest = null;
            if (dstAirport != clDest) {
                dstAirport = (Waypoint.Airport) clDest;
                rwNumbers  = null;
                if (dstAirport != null) {
                    int n = dstAirport.GetRunways ().values ().size ();
                    if (n > 0) {
                        rwNumbers = new RWNumber[n+1];
                        n = 0;
                        rwNumbers[n++] = new RWNumber ();
                        for (Waypoint.Runway rw : dstAirport.GetRunways ().values ()) {
                            rwNumbers[n] = new RWNumber ();
                            rwNumbers[n].rw = rw;
                            n ++;
                        }
                        Arrays.sort (rwNumbers, 0, n, compareRWNumbers);
                        for (int i = 0; i < n; i ++) {
                            rwNumbers[i].x = (int)(canvasWidth * (i + 0.5f) / n + 0.5);
                            rwNumbers[i].y = appRwayY;
                        }
                        appRunway = rwNumbers[0];  // default to enroute mode
                    }
                }
            }
            if (rwNumbers != null) {
                for (RWNumber rwn : rwNumbers) {
                    rwn.Draw (canvas);
                }
            }
        }

        wairToNow.drawGPSAvailable (canvas, this);
    }

    /**
     * Draw chart at the given center.
     * @param canvas = canvas to draw it on
     * @param centX  = center of where to put chart on canvas
     * @param centY  = center of where to put chart on canvas
     * @param radius = half width & height to put on canvas
     * @param course = course line degrees ("UP" direction of chart)
     */
    private void DrawChart (Canvas canvas, int centX, int centY, int radius, double course)
    {
        if (wairToNow.chartView.backing instanceof Chart2DView) {
            Chart2DView chart2DView = (Chart2DView) wairToNow.chartView.backing;
            canvas.save ();
            if (canvas.clipRect (centX - radius, centY - radius, centX + radius, centY + radius)) {
                int canWidth = getWidth ();
                int canHeight = getHeight ();
                int canCentX = canWidth / 2;
                int canCentY = canHeight / 2;
                canvas.translate (centX - canCentX, centY + radius / 2.0F - canCentY);
                wairToNow.chartView.ReCenter ();
                chart2DView.SetCanvasHdgRad (Math.toRadians (course));
                chart2DView.DrawChart (canvas, canWidth, canHeight);
                chart2DView.UnSetCanvasHdgRad ();
            }
            canvas.restore ();
        }
    }

    /**
     * Draw string giving destination data: waypoint  distance  timeremaining
     * @return null: still on course for the destination
     *         else: in turn on way to next segment of the route
     */
    private Waypoint DrawDestinationInfo (Canvas canvas, int centX, int centY, Position currPos)
    {
        Waypoint stepahead = null;

        Waypoint destwp = wairToNow.chartView.clDest;
        if (destwp != null) {

            // distance to destination waypoint
            StringBuilder sb = new StringBuilder ();
            sb.append (wairToNow.chartView.clDest.ident);
            sb.append (' ');
            double distnm  = Lib.LatLonDist (currPos.latitude, currPos.longitude, destwp.lat, destwp.lon);
            int dist10ths = (int) Math.round (distnm * 10.0);
            sb.append (dist10ths / 10);
            if (dist10ths < 10000) {
                sb.append ('.');
                sb.append (dist10ths % 10);
            }

            // make sure we are going some minimal speed so we can calculate time
            if (currPos.speed > WairToNow.gpsMinSpeedMPS) {

                // time to destination. either HH:MM or MM:SS
                sb.append (' ');
                int timesec = (int)(distnm / currPos.speed / Lib.KtPerMPS * 3600.0 + 0.5);
                if (timesec >= 3600) {
                    sb.append (timesec / 3600);
                    sb.append (':');
                    sb.append (Integer.toString (timesec / 60 % 60 + 100).substring (1));
                } else {
                    sb.append (timesec / 60);
                    sb.append (':');
                    sb.append (Integer.toString (timesec % 60 + 100).substring (1));
                }

                // see if route view active and there is another waypoint after current destination
                RouteView routeView = wairToNow.routeView;
                Waypoint[] ara = routeView.analyzedRouteArray;
                int pai = routeView.pointAhead;
                if (routeView.trackingOn && (++ pai < ara.length)) {

                    // compute change in heading required from current to oncourse to next destination
                    Waypoint nextwp = ara[pai];
                    double currhdg = currPos.heading;
                    double nexthdg = Lib.LatLonTC (destwp.lat, destwp.lon, nextwp.lat, nextwp.lon);
                    double hdgdiff = nexthdg - currhdg;
                    if (hdgdiff < -180.0) hdgdiff += 360.0;
                    if (hdgdiff >= 180.0) hdgdiff -= 360.0;

                    double hdgdiffabs = Math.abs (hdgdiff);
                    if ((hdgdiffabs > 1.0) && (hdgdiffabs < 179.0)) {

                        // find lat/lon where current heading would intersect the next course
                        LatLon intersect = new LatLon ();
                        Lib.GCIntersect (destwp.lat, destwp.lon, nextwp.lat, nextwp.lon,
                                currPos.latitude, currPos.longitude, currPos.heading, intersect);

                        // how many seconds from current position to intersection point
                        // if we kept going straight
                        double secfromcurrenttopoint = Lib.LatLonDist (currPos.latitude,
                                currPos.longitude, intersect.lat, intersect.lon) * Lib.MPerNM /
                                currPos.speed;

                        // how many seconds from start or end of turn to intersection point
                        // if we kept going straight

                        //   arclength[met] = speed[met/sec] / turnrate[rad/sec] * hdgdiff[rad]
                        //   arclength[met] = radius[met] * hdgdiffrad[rad]
                        //   => radius[met] = speed[met/sec] / turnrate[rad/sec]
                        //
                        //   2 * radius**2 - 2 * radius * radius * cos hdgdiff = linelen**2
                        //   2 * radius**2 * (1 - cos hdgdiff) = linelen**2
                        //
                        //   2 * disttopoint**2 * (1 - cos (pi - hdgdiff)) = linelen**2
                        //
                        //   2 * radius**2 * (1 - cos hdgdiff) = 2 * disttopoint**2 * (1 - cos (pi - hdgdiff))
                        //   radius**2 * (1 - cos hdgdiff) = disttopoint**2 * (1 + cos hdgdiff)
                        //   radius**2 * (1 - cos hdgdiff) / (1 + cos hdgdiff) = disttopoint**2
                        //   radius**2 * (tan (hdgdiff / 2))**2 = disttopoint**2
                        //   radius * tan (hdgdiff / 2) = disttopoint
                        //
                        //   disttopoint[met] = radius[met] * tan (hdgdiff / 2)
                        //   timetopoint[sec] = radius[met] / speed[met/sec] * tan (hdgdiff / 2)
                        //   timetopoint[sec] = tan (hdgdiff / 2) / turnrate[rad/sec]
                        double secfromturntopoint = Math.tan (Math.toRadians (hdgdiffabs) / 2.0) /
                                Math.toRadians (STDRATETURN);

                        // see how many seconds from now the turn must begin
                        int tostartsec = (int) Math.round (secfromcurrenttopoint - secfromturntopoint);
                        if (tostartsec <= 10) {

                            // 10 or less, display arrow and new heading
                            // hollow if before turn should start, solid if at or past
                            int maghdgbin = (int) Math.round (nexthdg + magvariation + 359.0) % 360 + 1;
                            String maghdgstr = Integer.toString (maghdgbin + 1000).substring (1);
                            boolean on = (tostartsec < 0) || ((tostartsec & 1) == 0);
                            if (hdgdiff > 0) {
                                sb.append (on ? " \u25B6" : " \u25B7");
                                sb.append (maghdgstr);
                                sb.append ('\u00B0');
                            } else {
                                sb.insert (0, on ? "\u00B0\u25C0 " : "\u00B0\u25C1 ");
                                sb.insert (0, maghdgstr);
                            }

                            // if should be in the turn, flip hsi needle to the next heading on the route
                            if (tostartsec <= 0) stepahead = nextwp;
                        }
                    }
                }
            }

            // draw the string with background
            DrawTextWithBG (canvas, sb.toString (), centX, centY, dgPaintHSV, dgPaintBack);
        }

        return stepahead;
    }

    /**
     * Draw the directional gyro instrument possibly with HSI needles.
     * @param canvas  = what to draw it on
     * @param centX,Y = center of the circle on the canvas
     * @param radius  = radius of the circle to draw
     * @param heading = numerical heading to draw gyro with (degrees)
     */
    private void DrawDirectionalGyro (Canvas canvas, int centX, int centY, int radius, double heading, Waypoint stepahead)
    {
        gsalt = -999999;
        tdze  = -999999;

        canvas.save ();
        canvas.translate (centX, centY);

        /*
         * Draw the black circle background.
         */
        canvas.drawCircle (0, 0, radius, dgPaintBack);

        /*
         * Draw the markings around the perimeter of the circle.
         */
        for (int i = 0; i < 360; i += 5) {
            canvas.save ();
            canvas.rotate ((float) (360 - heading + i));
            if (i % 30 == 0) {
                canvas.drawText (Integer.toString (i / 10), 0, -radius * 10.0F / 16, dgPaintNum);
                canvas.drawLine (0, -radius, 0, -radius * 13.0F / 16, dgPaintMaj);
            } else if (i % 10 == 0) {
                canvas.drawLine (0, -radius, 0, -radius * 13.0F / 16, dgPaintMid);
            } else {
                canvas.drawLine (0, -radius, 0, -radius * 14.0F / 16, dgPaintMin);
            }
            canvas.restore ();
        }

        /*
         * Maybe draw HSI needle for selected course.
         */
        Waypoint clDest = wairToNow.chartView.clDest;
        if (clDest != null) {

            /*
             * Our current position.
             */
            Position currPos = positions[posIndex];
            double curLat = currPos.latitude;
            double curLon = currPos.longitude;

            /*
             * Use approach mode if runway selected.
             */
            Waypoint.Runway rw;
            if ((appRunway != null) && ((rw = appRunway.rw) != null)) {

                // true course degrees from current position to runway end
                double tcDegCurToRWEnd = Lib.LatLonTC (curLat, curLon, rw.endLat, rw.endLon);

                // true course degrees from runway beginning to runway end
                double tcDegRWBegToEnd = rw.trueHdg;

                // nautical miles of needle deflection to the right
                double nmDeflectRite = Lib.GCOffCourseDist (rw.lat, rw.lon, rw.endLat, rw.endLon, curLat, curLon);

                // draw the HSI needles in approach mode for this runway
                DrawHSINeedles (canvas,                  // canvas to draw HSI needles on
                        radius,                          // radius of dg circle on canvas
                        heading,                         // current heading (actually current track)
                        tcDegRWBegToEnd + magvariation,  // course of runway centerline
                        tcDegCurToRWEnd + magvariation,  // bearing from current position to runway
                        nmDeflectRite,                   // how much HSI needle is to be deflected right
                        HSISCALEAPP);                    // full scale right deflection

                // nautical miles from current position to runway beginning
                double nmCurToRWBeg = Lib.LatLonDist (curLat, curLon, rw.lat, rw.lon);

                // for this far from the runway, compute altitude above runway where glideslope center is
                double nmGSAboveRWBeg = Math.tan (APPMODEGS / 180.0 * Math.PI) * nmCurToRWBeg;

                // compute feet MSL where glideslope center is
                gsalt = (int)(nmGSAboveRWBeg * Lib.MPerNM * Lib.FtPerM + rw.elev + GSFTABVTH);
                tdze  = (int)rw.elev;
            }

            /*
             * If not approaching a runway at destination airport, use enroute mode.
             */
            if (gsalt == -999999) {

                // current course origination and destination points.
                double orgLat = wairToNow.chartView.orgLat;
                double orgLon = wairToNow.chartView.orgLon;
                double dstLat = clDest.lat;
                double dstLon = clDest.lon;

                if (stepahead != null) {
                    orgLat = dstLat;
                    orgLon = dstLon;
                    dstLat = stepahead.lat;
                    dstLon = stepahead.lon;
                }

                // find out how far off course we are
                double offCourseDist = Lib.GCOffCourseDist (orgLat, orgLon, dstLat, dstLon, curLat, curLon);

                // find heading along course line adjacent to wherever we currently are
                double onCourseHdg = Lib.GCOnCourseHdg (orgLat, orgLon, dstLat, dstLon, curLat, curLon);

                // this is heading to take to get great circle from wherever we currently are
                double bearing = Lib.LatLonTC (curLat, curLon, dstLat, dstLon);

                DrawHSINeedles (canvas,                      // canvas to draw HSI needles on
                                radius,                      // radius of dg circle on canvas
                                heading,                     // current heading (actually current track)
                                onCourseHdg + magvariation,  // desired course line
                                bearing + magvariation,      // actual bearing from current position to destination point
                                offCourseDist,               // how far off course we are
                                HSISCALEENR);                // full-scale deflection distance
            }
        }

        /*
         * Draw triangle at the top along with numeric heading.
         */
        for (int d = 0; d < radius * 3 / 16; d ++) {
            canvas.drawLine (-d, -radius - d, + d, -radius - d, dgPaintTri);
        }
        String hdgstr = DegString (heading);
        DrawTextWithBG (canvas, hdgstr, 0, -radius * 20 / 16, dgPaintVal, dgPaintBack);

        canvas.restore ();
    }

    /**
     * Draw HSI needles
     * @param canvas        = canvas to draw the needles on, translated so DG center is at (0,0)
     * @param radius        = radius of the DG circle the HSI needles are drawn on
     * @param heading       = heading at top of DG circle
     * @param course        = course the aircraft should ideally be on (needle)
     * @param bearing       = actual bearing to the destination (tickmark)
     * @param deflectRite   = how far off to the right the ideal course line is
     * @param fullScaleRite = how far off to the right full scale deflection is
     */
    private void DrawHSINeedles (Canvas canvas,
                                 int    radius,      double heading,
                                 double course,      double bearing,
                                 double deflectRite, double fullScaleRite)
    {
        // draw HSI needle in the gyro circle
        canvas.save ();
        canvas.rotate ((float) (course - heading));
        canvas.drawLine ( 0.0F * radius, -0.5F * radius, 0, -0.2F * radius, dgPaintHSI);
        canvas.drawLine ( 0.0F * radius,  0.2F * radius, 0,  0.5F * radius, dgPaintHSI);
        canvas.drawLine (-0.1F * radius, -0.4F * radius, 0, -0.5F * radius, dgPaintHSI);
        canvas.drawLine ( 0.1F * radius, -0.4F * radius, 0, -0.5F * radius, dgPaintHSI);

        // compute x-axis displacement of right/left needle
        double offset = deflectRite / fullScaleRite;
        if (offset < -1) offset = -1;
        if (offset >  1) offset =  1;
        double xdisp = offset * -0.5 * radius;

        // draw off course distance string along HSI needle
        String deflectRiteStr;
        if (deflectRite < 0) deflectRite = -deflectRite;
        if (deflectRite >= 9.949) {
            // 10 or more, no decimal point, just round
            deflectRite += 0.5;
            deflectRiteStr = Integer.toString ((int)deflectRite);
        } else {
            // lt 10, use decimal point and round to one decimal digit
            deflectRite += 0.05;
            int deflectRiteInt = (int)deflectRite;
            deflectRite -= deflectRiteInt;
            deflectRite *= 10;
            int deflectRiteDec = (int)deflectRite;
            deflectRiteStr = Integer.toString (deflectRiteInt);
            if (deflectRiteDec > 0) {
                deflectRiteStr += '.' + Integer.toString (deflectRiteDec);
            }
        }

        // use a line if lt 0.1 off course, otherwise use text
        if (deflectRiteStr.equals ("0")) {
            canvas.drawLine ((float) xdisp, -0.2F * radius, (float) xdisp, 0.2F * radius, dgPaintHSI);
        } else {
            if (xdisp >= 0) {
                canvas.rotate (90);
                xdisp = -xdisp;
            } else {
                canvas.rotate (-90);
            }
            canvas.drawText ("\u25C1" + deflectRiteStr + "\u25B7", 0, (int)xdisp, dgPaintHSV);
        }

        canvas.restore ();

        // draw a little arrowhead on the bearing directly to the destination
        canvas.save ();
        canvas.rotate ((float) (bearing - heading));
        canvas.drawLine (-0.1F * radius, -0.9F * radius, 0, -radius, dgPaintHSI);
        canvas.drawLine ( 0.1F * radius, -0.9F * radius, 0, -radius, dgPaintHSI);

        // draw another that is an intercept
        canvas.rotate ((float) (bearing - course));
        canvas.drawLine (-0.1F * radius, -0.9F * radius, 0, -radius, dgPaintHSI);
        canvas.drawLine ( 0.1F * radius, -0.9F * radius, 0, -radius, dgPaintHSI);
        canvas.restore ();
    }


    /**
     * Draw text with a background rectangle.
     * @param canvas  = canvas to draw text on
     * @param text    = string to draw
     * @param x,y     = where to put string on canvas
     * @param fgPaint = paints the foreground text
     * @param bgPaint = paints the background rectangle
     */
    private void DrawTextWithBG (Canvas canvas, String text, int x, int y, Paint fgPaint, Paint bgPaint)
    {
        fgPaint.getTextBounds (text, 0, text.length (), dtwbBounds);
        int htw = dtwbBounds.width  ();
        int hth = dtwbBounds.height ();
        canvas.drawRect (x - htw / 2.0F - 3.0F, y - hth - 3, x + htw / 2.0F + 3.0F, y + 3, bgPaint);
        canvas.drawText (text, x, y, fgPaint);
    }

    /**
     * Draw a vertical numeric strip box.
     * @param canvas     = canvas to draw it on
     * @param centX,Y    = center of box on canvas
     * @param height     = overall height of the box
     * @param width      = overall width of the box
     * @param val        = current value to be highlighted
     * @param perstep    = regular interval marking step
     * @param numsteps   = number of persteps to display top-to-bottom
     * @param minallowed = don't display regular interval markings below this value
     */
    @SuppressWarnings("SameParameterValue")
    private void DrawNumericStrip (Canvas canvas, int centX, int centY, int height, int width, int val, int perstep, int numsteps, int minallowed, int marker, int target)
    {
        canvas.save ();
        canvas.translate (centX, centY);
        canvas.drawRect (-width/2.0F, -height/2.0F, width/2.0F, height/2.0F, dgPaintBack);

        // comments use example of the airspeed indicator
        // with val=87 (current speed), perstep=10 (show markings every 10kts), numsteps=8 (show 80kts from top to bottom)
        // note that the 87 will be drawn in the exact middle of the box

        // how many Y pixels are there between the 60,70,80,...
        double yPerStep = (double)height / (double)numsteps;

        // how many Y pixels are there between a 61,62,63,...
        double yPerUnit = yPerStep / (double)perstep;

        // given val=87 and perstep is 10, we want the 7, ie, how much after the 80 we are
        int extraUnits = val % perstep;

        // now calculate how much above the center the first regular marking is,
        // eg, where does the 80 go?
        int yAboveCenter = (int)(yPerUnit * (double)extraUnits);

        int valAboveCtr = val - extraUnits;

        // draw regular interval markings
        float tsnum = nsPaintNum.getTextSize ();
        float tsval = nsPaintVal.getTextSize ();
        for (int n = -(numsteps + 1) / 2; n <= (numsteps + 1) / 2; n ++) {
            int y = -yAboveCenter + (int)(yPerStep * n + 0.5);
            if ((y < 0) && (y > -tsnum)) continue;
            if ((y >= 0) && (y < tsval)) continue;  // skip if near center where given value goes
            int yabs = Math.abs (y);
            if (yabs > height / 2 - 20) continue;      // skip if off the ends
            int valAtY = valAboveCtr + perstep * n;
            if (valAtY < minallowed) continue;
            canvas.drawText (Integer.toString (valAtY), 0, y, nsPaintNum);
        }

        // draw the given value in the center
        canvas.drawText (Integer.toString (val), 0, 0, nsPaintVal);

        // maybe draw glideslope marker on altimeter indicating where glideslope is
        if (marker >= minallowed) {
            double markerStepsBelowCenter = (double)(marker - val) / perstep;
            if (markerStepsBelowCenter < -(double)numsteps / 2) markerStepsBelowCenter = -(double)numsteps / 2;
            if (markerStepsBelowCenter >  (double)numsteps / 2) markerStepsBelowCenter =  (double)numsteps / 2;
            int markerPixBelowCenter = (int)(markerStepsBelowCenter / numsteps * height + 0.5);

            int d = width / 8;

            for (int x = 0; x <= d * 3 / 2; x ++) {
                int o = width / 2 - d + x;
                canvas.drawLine (-o, markerPixBelowCenter - x, -o, markerPixBelowCenter + x, dgPaintHSI);
                canvas.drawLine ( o, markerPixBelowCenter - x,  o, markerPixBelowCenter + x, dgPaintHSI);
            }
        }

        // maybe draw line at altitude target
        if (target >= minallowed) {
            double targetStepsBelowCenter = (double)(target - val) / perstep;
            int targetPixBelowCenter = (int)(targetStepsBelowCenter / numsteps * height + 0.5);
            if ((targetPixBelowCenter >= -height / 2) && (targetPixBelowCenter <= height / 2)) {
                canvas.drawLine (-width * 3.0F / 8, targetPixBelowCenter, width * 3.0F / 8, targetPixBelowCenter, dgPaintHSI);
            }
        }

        canvas.restore ();
    }

    /**
     * Selects active approach.
     */
    private class RWNumber {
        public int x;     // center of text
        public int y;     // top of text
        public Rect box;  // a little larger than text
        public Waypoint.Runway rw;

        public void Draw (Canvas canvas)
        {
            // get string to be displayed
            String rwn = "ENR";
            if (rw != null) rwn = rw.number;

            // get paint to be used
            Paint p = (this == appRunway) ? rwSelPaint : rwayPaint;

            // maybe need to compute where string is on canvas
            // box is used to detect touch
            if (box == null) {
                box = new Rect ();
                p.getTextBounds (rwn, 0, rwn.length (), box);
                box.bottom += 8;
                box.right  += 8;
                box.offset (x - box.right / 2, y - 4);
            }

            canvas.drawRect (box, dgPaintBack);

            // draw string on canvas
            canvas.drawText (rwn, x, y, p);
        }
    }

    private static class CompareRWNumbers implements Comparator<RWNumber> {
        @Override
        public int compare (RWNumber arg0, RWNumber arg1)
        {
            String num0 = (arg0.rw == null) ? "" : arg0.rw.number;
            String num1 = (arg1.rw == null) ? "" : arg1.rw.number;
            return num0.compareTo (num1);
        }
    }

    private static String DegString (double deg)
    {
        int hdgnum = ((int)(deg + 1439.5)) % 360 + 1;
        String st = Integer.toString (hdgnum + 1000).substring (1);
        return st + (char)0xB0;
    }

}
