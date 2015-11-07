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

import java.util.Arrays;
import java.util.Comparator;

import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Display a glass cockpit panel.
 */
public class GlassView
        extends View
        implements WairToNow.CanBeMainView {
    private static final float APPMODEFS = 5;         // approach mode full scale deflection degrees
    private static final float APPMODEGS = 3.25F;     // approach mode glide slope degrees
    private static final float FtPerM    = 3.28084F;  // feet per metre
    private static final float GRAVACCEL = 9.80665F;  // metres per second squared
    private static final float GSFTABVTH = 25;        // glideslope feet above threshold
    private static final float NMPerDeg  = 60.0F;     // naut.mi per degree (lat)
    private static final float HSISCALE  = 2.0F;      // HSI scale full deflection = 2.0nm
    private static final float KtPerMPS  = 1.94384F;
    private static final float SMPerNM   = 1.15078F;

    private static final CompareRWNumbers compareRWNumbers = new CompareRWNumbers ();

    private float magvariation;
    private float[][] inv_bank_rot = new float[][] { new float[3], new float[3], new float[3] };
    private float[][] inv_pich_rot = new float[][] { new float[3], new float[3], new float[3] };
    private float[][] composite    = new float[][] { new float[3], new float[3], new float[3] };
    private float[][] canvas_pt    = new float[][] { new float[1], new float[1], new float[1] };
    private float[][] sphere_pt    = new float[][] { new float[1], new float[1], new float[1] };
    private int posIndex = 0;
    private int gsalt, tdze;
    private Paint ahPaintAir     = new Paint ();
    private Paint ahPaintGnd     = new Paint ();
    private Paint ahPaintMrk     = new Paint ();
    private Paint ahPaintSky     = new Paint ();
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
    private RWNumber appRunway;
    private RWNumber[] rwNumbers;
    private WairToNow wairToNow;
    private Waypoint.Airport dstAirport;

    public GlassView (WairToNow na)
    {
        super (na);

        wairToNow = na;

        // artificial horizon paints
        ahPaintAir.setColor (Color.RED);
        ahPaintAir.setStyle (Paint.Style.STROKE);
        ahPaintAir.setStrokeWidth (7);
        ahPaintGnd.setColor (Color.argb (255, 100, 75,  25));
        ahPaintGnd.setStyle (Paint.Style.FILL);
        ahPaintMrk.setColor (Color.YELLOW);
        ahPaintMrk.setStyle (Paint.Style.FILL);
        ahPaintSky.setColor (Color.argb (255, 160, 160, 255));
        ahPaintSky.setStyle (Paint.Style.FILL);

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
        nsPaintNum.setTextSize (wairToNow.textSize);
        nsPaintVal.setColor (Color.RED);
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

    /**
     * The GPS has new position information for us.
     * Save it and update screen.
     */
    public void SetGPSLocation (Location loc)
    {
        if (++ posIndex == positions.length) posIndex = 0;
        Position p  = positions[posIndex];
        p.altitude  = (float) loc.getAltitude ();
        p.heading   = loc.getBearing ();
        p.latitude  = (float) loc.getLatitude ();
        p.longitude = (float) loc.getLongitude ();
        p.speed     = loc.getSpeed ();
        p.time      = loc.getTime ();
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
     * The screen is about to be made current so force portrait mode.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (wairToNow.optionsView.powerLockOption.checkBox.isChecked ()) {
            wairToNow.getWindow ().addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * This screen is no longer current.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        wairToNow.getWindow ().clearFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

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
        Position prevPos = positions[(posIndex+positions.length-1)%positions.length];
        magvariation = Lib.MagVariation (currPos.latitude, currPos.longitude, currPos.altitude);

        int canvasWidth  = canvas.getWidth ();   // eg (600,1024)
        int canvasHeight = canvas.getHeight ();
        canvas.drawColor (Color.GRAY);

        /*
         * Heading comes directly from most recent GPS position report.
         * Units are degrees.
         */
        float truehdg = currPos.heading;

        /*
         * Get current altitude in metres.
         */
        float altitude = currPos.altitude;

        /*
         * Rate of turn can be computed from two most recent GPS reports.
         * Units are radians per second.
         */
        float prevHdg = prevPos.heading;
        if (prevHdg - truehdg >  180) prevHdg -= 360;
        if (prevHdg - truehdg < -180) prevHdg += 360;
        float rateOfTurn = (truehdg - prevHdg) / (currPos.time - prevPos.time) * 1000.0F / 180.0F * Mathf.PI;

        /*
         * Calculate turn radius (metres).
         */
        //float turnRadius = currPos.speed / rateOfTurn;

        /*
         * Calculate centripetal acceleration (metres per second squared).
         */
        float centripAccel = currPos.speed * rateOfTurn;

        /*
         * Bank angle is arctan (centripAccel / gravity).
         */
        //float bankAngle = Math.atan2 (centripAccel, GRAVACCEL);

        /*
         * Pitch-up angle is arctan (climb distance / travel distance).
         */
        //float travelDistance = Math.sqrt (Sq (currPos.latitude - prevPos.latitude) +
        //      Sq ((currPos.longitude - prevPos.longitude) / Math.cos (currPos.latitude / 180.0 * Math.PI)));
        //travelDistance *= NMPerDeg * MPerNM;
        //float climbDistance = altitude - prevPos.altitude;
        //float pitchAngle = Math.atan2 (climbDistance, travelDistance);

        /*
         * Calculate magnetic heading.
         */
        float maghdg = truehdg + magvariation;

        /*
         * Fit the instruments to the page.
         */
        int ahRadius  = canvasHeight *  5 / 32;
        int dgRadius  = canvasHeight *  5 / 32;
        int aspHeight = canvasHeight * 24 / 32;
        int altHeight = canvasHeight * 24 / 32;

        int ahCentY   = canvasHeight *  8 / 32;
        int aspCentY  = canvasHeight * 14 / 32;
        int altCentY  = canvasHeight * 14 / 32;
        int dstCentY  = canvasHeight * 29 / 64;
        int dgCentY   = canvasHeight * 22 / 32;
        int appRwayY  = canvasHeight * 29 / 32;

        int aspCentX  = canvasWidth  *  4 / 32;
        int aspWidth  = canvasWidth  * 11 / 64;
        int dstCentX  = canvasWidth  * 16 / 32;
        int altWidth  = canvasWidth  * 11 / 64;
        int ahCentX   = canvasWidth  * 16 / 32;
        int dgCentX   = canvasWidth  * 16 / 32;
        int altCentX  = canvasWidth  * 28 / 32;

        //DrawArtificialHorizon (canvas, ahCentX, ahCentY, ahRadius, pitchAngle, bankAngle);
        DrawChart (canvas, ahCentX, ahCentY, ahRadius);

        DrawDestinationInfo (canvas, dstCentX, dstCentY, currPos);

        DrawDirectionalGyro (canvas, dgCentX, dgCentY, dgRadius, maghdg);

        float spdflt = currPos.speed * KtPerMPS;
        if (wairToNow.optionsView.ktsMphOption.getAlt ()) spdflt *= SMPerNM;
        int spdint = Math.round (spdflt);
        DrawNumericStrip (canvas, aspCentX, aspCentY, aspHeight, aspWidth, spdint, 10, 8, 0, -1, -1);

        int feet = (int) Math.round (altitude * 3.28084);
        DrawNumericStrip (canvas, altCentX, altCentY, altHeight, altWidth, feet, -100, 8, -999998, gsalt, tdze);

        /*
         * Display destination approach selector boxes.
         */
        String clName = wairToNow.chartView.clName;
        if (clName != null) {
            float dstLat = wairToNow.chartView.dstLat;
            float dstLon = wairToNow.chartView.dstLon;
            Waypoint wp = Waypoint.FindWaypoint (clName, dstLat, dstLon);
            if ((wp != null) && !(wp instanceof Waypoint.Airport)) wp = null;
            if (dstAirport != wp) {
                dstAirport = (Waypoint.Airport)wp;
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
                            rwNumbers[i].x = (int)(canvasWidth * (i + 0.5f) / n + 0.5F);
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
     * Draw the artificial horizon instrument.
     * @param canvas    = what to draw it on
     * @param centX,Y   = center of the circle on the canvas
     * @param radius    = radius of the circle to draw
     * @param pichAngle = how much pitched up (radians)
     * @param bankAngle = how much banked right (radians)
     */
    private void DrawArtificialHorizon (Canvas canvas, int centX, int centY, int radius, float pichAngle, float bankAngle)
    {
        /*
         * Pretend we have a right-handed 3D coordinate system with:
         *   X axis pointing left
         *   Y axis pointing down
         *   Z axis pointing into screen
         * We have a sphere that is sky colored on top and ground colored on the bottom.
         * First we pitch up by rotating it clockwise around the X axis.
         * Then we bank right by rotating it clockwise around the Z axis.
         * Finally simply display the pixels with canvas DZ > 0 because pilot is facing the +Z axis.
         *
         *    [ bank rot ] * [ pitch rot ] * [ sphere x,y,z ] = [ canvas dx,dy,dz ]
         *
         * But we want to start with canvas pixels and get the corresponding sphere pixel.
         * So we will reverse the equation:
         *
         *    [ sphere x,y,z ] = inv [ pitch rot ] * inv [ bank rot ] * [ canvas dx,dy,dz ]
         */
        float bankCos = Mathf.cos (bankAngle);
        float bankSin = Mathf.sin (bankAngle);
        inv_bank_rot[0][0] =  bankCos;
        inv_bank_rot[0][1] = -bankSin;
        inv_bank_rot[1][0] =  bankSin;
        inv_bank_rot[1][1] =  bankCos;
        inv_bank_rot[2][2] =        1;

        float pichCos = Mathf.cos (pichAngle);
        float pichSin = Mathf.sin (pichAngle);
        inv_pich_rot[0][0] =        1;
        inv_pich_rot[1][1] =  pichCos;
        inv_pich_rot[1][2] = -pichSin;
        inv_pich_rot[2][1] =  pichSin;
        inv_pich_rot[2][2] =  pichCos;

        Lib.MatMul (composite, inv_pich_rot, inv_bank_rot);

        /*
         * Scan through all canvas points in the circle that make up the instrument face.
         * Then 'ray trace' them back to where they are on the gyro's sphere.
         * See what color is at that spot on the sphere and paint that color on the canvas' pixel.
         */
        for (int dy = -radius; dy <= radius; dy ++) {
            int w = (int)Math.sqrt (Sq (radius) - Sq (dy));
            float sy = (float)dy / (float)radius;
            canvas_pt[1][0] = sy;
            for (int dx = -w; dx <= w; dx ++) {
                float sx = (float)dx / (float)radius;
                canvas_pt[0][0] = sx;
                canvas_pt[2][0] = Mathf.sqrt (1.0F - sx * sx - sy * sy);
                Lib.MatMul (sphere_pt, composite, canvas_pt);
                Paint p = AHSphereColor (sphere_pt[0][0], sphere_pt[1][0]);
                canvas.drawPoint (centX + dx, centY + dy, p);
            }
        }

        /*
         * Draw the airplane symbol right in the center.
         */
        canvas.drawLine (centX - radius * 5 / 16, centY, centX + radius * 5 / 16, centY, ahPaintAir);
        canvas.drawLine (centX, centY - radius * 7 / 32, centX, centY, ahPaintAir);

        /*
         * Draw the bank angle markings along the top.
         */
        bankAngle *= 180.0F / Mathf.PI;
        canvas.save ();
        canvas.translate (centX, centY);
        for (int i = -60; i <= 60; i += 10) {
            if ((i == 40) || (i == 50) || (i == -40) || (i == -50)) continue;
            canvas.save ();
            canvas.rotate (360 - bankAngle + i);
            if (i % 30 == 0) {
                canvas.drawLine (0, -radius, 0, -radius * 13 / 16, dgPaintMid);
            } else {
                canvas.drawLine (0, -radius, 0, -radius * 14 / 16, dgPaintMin);
            }
            canvas.restore ();
        }
        canvas.restore ();

        /*
         * Draw text box containing pitch angle.
         */
        pichAngle *= 180.0 / Math.PI;
        while (pichAngle <= -180.0) pichAngle += 360.0;
        while (pichAngle >   180.0) pichAngle -= 360.0;
        String paDir = "UP";
        int paYPix = centY - radius;
        if (pichAngle < 0) {
            paDir = "DN";
            paYPix = centY + radius * 18 / 16;
            pichAngle = -pichAngle;
        }
        int paInt = (int)(pichAngle + 0.5);
        if (paInt != 0) {
            String paStr = Integer.toString (paInt) + (char)0xB0 + paDir;
            DrawTextWithBG (canvas, paStr, centX - radius * 3 / 4, paYPix, dgPaintVal, dgPaintBack);
        }

        /*
         * Draw a little yellow triangle at the top to reference the bank angle markings.
         */
        for (int d = 0; d < radius * 3 / 16; d ++) {
            canvas.drawLine (centX - d, centY - radius - d, centX + d, centY - radius - d, dgPaintTri);
        }
        while (bankAngle <= -180.0) bankAngle += 360.0;
        while (bankAngle >   180.0) bankAngle -= 360.0;
        char baDir = 'R';
        if (bankAngle < 0) {
            baDir = 'L';
            bankAngle = -bankAngle;
        }
        int baInt = (int)(bankAngle + 0.5);
        if (baInt != 0) {
            String baStr = Integer.toString (baInt) + (char)0xB0 + baDir;
            DrawTextWithBG (canvas, baStr, centX, centY - radius * 20 / 16, dgPaintVal, dgPaintBack);
        }
    }

    /**
     * Determine what color is at a given artificial horizon sphere's x,y,z.
     * @param x,y = coordinate on sphere's surface (unit sized)
     */
    private Paint AHSphereColor (float x, float y)
    {
        /*
         * See if we should draw one of the yellow markings spaced at 5deg intervals.
         */
        float xabs = Math.abs (x);
        float yabs = Math.abs (y);
        for (int deg = 0; deg <= 20; deg += 5) {
            float sin = Mathf.sin (deg / 180.0F * Mathf.PI);
            if (yabs > sin-1.0/64 && yabs < sin+1.0/64) {
                if (deg == 0) return ahPaintMrk;
                if (xabs * 50 <= 10 - deg % 10) return ahPaintMrk;
                break;
            }
        }

        /*
         * Not a yellow mark, sky is -y (up) and ground is +y (down).
         */
        return (y < 0) ? ahPaintSky : ahPaintGnd;
    }

    /**
     * Draw chart at the given center.
     * @param canvas = canvas to draw it on
     * @param centX  = center of where to put chart on canvas
     * @param centY  = center of where to put chart on canvas
     * @param radius = half width & height to put on canvas
     */
    private void DrawChart (Canvas canvas, int centX, int centY, int radius)
    {
        canvas.save ();
        if (canvas.clipRect (centX - radius, centY - radius, centX + radius, centY + radius)) {
            int canCentX = getWidth  () / 2;
            int canCentY = getHeight () / 2;
            canvas.translate (centX - canCentX, centY - canCentY);
            wairToNow.chartView.ReCenter ();
            wairToNow.chartView.OnDraw (canvas);  // avoid asinine 'suspicious method call' error by using OnDraw()
        }
        canvas.restore ();
    }

    /**
     * Draw string giving destination data: waypoint  distance  timeremaining
     */
    private void DrawDestinationInfo (Canvas canvas, int centX, int centY, Position currPos)
    {
        if (wairToNow.chartView.clName != null) {
            float distnm   = Lib.LatLonDist (currPos.latitude, currPos.longitude, wairToNow.chartView.dstLat, wairToNow.chartView.dstLon);
            int dist10ths  = (int)(distnm * 10 + 0.5);
            String diststr = (dist10ths >= 10000) ? Integer.toString (dist10ths / 10) :
                    Integer.toString (dist10ths / 10) + "." + Integer.toString (dist10ths % 10);
            String hdgstr = wairToNow.chartView.clName + "  " + diststr;
            if (currPos.speed > 1.0) {
                int    timesec = (int)(distnm / currPos.speed / KtPerMPS * 3600.0 + 0.5);
                String timestr = ((timesec >= 3600) ?
                        Integer.toString (timesec / 3600) + ":" + Integer.toString (timesec / 60 % 60 + 100).substring (1) :
                        Integer.toString (timesec / 60)) + ":" +
                        Integer.toString (timesec % 60 + 100).substring (1);
                hdgstr += "  " + timestr;
            }
            DrawTextWithBG (canvas, hdgstr, centX, centY, dgPaintHSV, dgPaintBack);
        }
    }

    /**
     * Draw the directional gyro instrument.
     * @param canvas  = what to draw it on
     * @param centX,Y = center of the circle on the canvas
     * @param radius  = radius of the circle to draw
     * @param heading = numerical heading to draw gyro with (degrees)
     */
    private void DrawDirectionalGyro (Canvas canvas, int centX, int centY, int radius, float heading)
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
            canvas.rotate (360 - heading + i);
            if (i % 30 == 0) {
                canvas.drawText (Integer.toString (i / 10), 0, -radius * 10 / 16, dgPaintNum);
                canvas.drawLine (0, -radius, 0, -radius * 13 / 16, dgPaintMaj);
            } else if (i % 10 == 0) {
                canvas.drawLine (0, -radius, 0, -radius * 13 / 16, dgPaintMid);
            } else {
                canvas.drawLine (0, -radius, 0, -radius * 14 / 16, dgPaintMin);
            }
            canvas.restore ();
        }

        /*
         * Maybe draw HSI needle for selected course.
         */
        String clName = wairToNow.chartView.clName;
        if (clName != null) {

            /*
             * Current course origination and destination points.
             */
            float orgLat = wairToNow.chartView.orgLat;
            float orgLon = wairToNow.chartView.orgLon;
            float dstLat = wairToNow.chartView.dstLat;
            float dstLon = wairToNow.chartView.dstLon;

            /*
             * Our current position.
             */
            Position currPos = positions[posIndex];
            float curLat = currPos.latitude;
            float curLon = currPos.longitude;

            /*
             * Use approach mode if runway selected.
             */
            Waypoint.Runway rw;
            if ((appRunway != null) && ((rw = appRunway.rw) != null)) {

                // true course degrees from current position to runway end
                float tcDegCurToRWEnd = Lib.LatLonTC (curLat,    curLon,    rw.endLat, rw.endLon);

                // true course degrees from runway beginning to runway end
                float tcDegRWBegToEnd = Lib.LatLonTC (rw.begLat, rw.begLon, rw.endLat, rw.endLon);

                // degrees of needle deflection to the right
                float degDeflectRite = tcDegRWBegToEnd - tcDegCurToRWEnd;
                while (degDeflectRite < -180.0) degDeflectRite += 360.0;
                while (degDeflectRite >= 180.0) degDeflectRite -= 360.0;

                // draw the HSI needles in approach mode for this runway
                DrawHSINeedles (canvas,                          // canvas to draw HSI needles on
                                radius,                          // radius of dg circle on canvas
                                heading,                         // current heading (actually current track)
                                tcDegRWBegToEnd + magvariation,  // course of runway centerline
                                tcDegCurToRWEnd + magvariation,  // bearing from current position to runway
                                degDeflectRite,                  // how much HSI needle is to be deflected right
                                APPMODEFS);                      // full scale right deflection

                // nautical miles from current position to runway beginning
                float nmCurToRWBeg = Lib.LatLonDist (curLat, curLon, rw.begLat, rw.begLon);

                // project that distance onto runway centerline
                // also makes it negative if beyond runway threshold
                nmCurToRWBeg *= Math.cos (degDeflectRite / 180.0 * Math.PI);

                // for this far from the runway, compute altitude above runway where glideslope center is
                float nmGSAboveRWBeg = Mathf.tan (APPMODEGS / 180.0F * Mathf.PI) * nmCurToRWBeg;

                // compute feet MSL where glideslope center is
                gsalt = (int)(nmGSAboveRWBeg * Lib.MPerNM * FtPerM + rw.elev + GSFTABVTH);
                tdze  = (int)rw.elev;
            }

            /*
             * If not approaching a runway at destination airport, use enroute mode.
             */
            if (gsalt == -999999) {
                // find out how far off course we are
                float offCourseDist = Lib.GCOffCourseDist (orgLat, orgLon, dstLat, dstLon, curLat, curLon);

                // find heading along course line adjacent to wherever we currently are
                float onCourseHdg = Lib.GCOnCourseHdg (orgLat, orgLon, dstLat, dstLon, curLat, curLon);

                // this is heading to take to get great circle from wherever we currently are
                float bearing = Lib.LatLonTC (curLat, curLon, dstLat, dstLon);

                DrawHSINeedles (canvas,                      // canvas to draw HSI needles on
                                radius,                      // radius of dg circle on canvas
                                heading,                     // current heading (actually current track)
                                onCourseHdg + magvariation,  // desired course line
                                bearing + magvariation,      // actual bearing from current position to destination point
                                offCourseDist,               // how far off course we are
                                HSISCALE);                   // full-scale deflection distance
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
                                 int    radius,      float heading,
                                 float  course,      float bearing,
                                 float  deflectRite, float fullScaleRite)
    {
        // draw HSI needle in the gyro circle
        canvas.save ();
        canvas.rotate (course - heading);
        canvas.drawLine ( 0.0F * radius, -0.5F * radius, 0, -0.2F * radius, dgPaintHSI);
        canvas.drawLine ( 0.0F * radius,  0.2F * radius, 0,  0.5F * radius, dgPaintHSI);
        canvas.drawLine (-0.1F * radius, -0.4F * radius, 0, -0.5F * radius, dgPaintHSI);
        canvas.drawLine ( 0.1F * radius, -0.4F * radius, 0, -0.5F * radius, dgPaintHSI);

        // compute x-axis displacement of right/left needle
        float offset = deflectRite / fullScaleRite;
        if (offset < -1) offset = -1;
        if (offset >  1) offset =  1;
        float xdisp = offset * -0.5F * radius;

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
            canvas.drawLine (xdisp, -0.2F * radius, xdisp, 0.2F * radius, dgPaintHSI);
        } else {
            if (xdisp >= 0) {
                canvas.rotate (90);
                xdisp = -xdisp;
            } else {
                canvas.rotate (-90);
            }
            canvas.drawText ("< " + deflectRiteStr + " >", 0, (int)xdisp, dgPaintHSV);
        }

        canvas.restore ();

        // draw a little arrowhead on the bearing directly to the destination
        canvas.save ();
        canvas.rotate (bearing - heading);
        canvas.drawLine (-0.1F * radius, -0.9F * radius, 0, -radius, dgPaintHSI);
        canvas.drawLine ( 0.1F * radius, -0.9F * radius, 0, -radius, dgPaintHSI);

        // draw another that is an intercept
        canvas.rotate (bearing - course);
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
    private static void DrawTextWithBG (Canvas canvas, String text, int x, int y, Paint fgPaint, Paint bgPaint)
    {
        Rect bounds = new Rect ();
        fgPaint.getTextBounds (text, 0, text.length (), bounds);
        int htw = bounds.right - bounds.left;
        int hth = bounds.bottom - bounds.top;
        canvas.drawRect (x - htw / 2 - 3, y - hth - 3, x + htw / 2 + 3, y + 3, bgPaint);
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
    private void DrawNumericStrip (Canvas canvas, int centX, int centY, int height, int width, int val, int perstep, int numsteps, int minallowed, int marker, int target)
    {
        canvas.save ();
        canvas.translate (centX, centY);
        canvas.drawRect (-width/2, -height/2, width/2, height/2, dgPaintBack);

        // comments use example of the airspeed indicator
        // with val=87 (current speed), perstep=10 (show markings every 10kts), numsteps=8 (show 80kts from top to bottom)
        // note that the 87 will be drawn in the exact middle of the box

        // how many Y pixels are there between the 60,70,80,...
        float yPerStep = (float)height / (float)numsteps;

        // how many Y pixels are there between a 61,62,63,...
        float yPerUnit = yPerStep / (float)perstep;

        // given val=87 and perstep is 10, we want the 7, ie, how much after the 80 we are
        int extraUnits = val % perstep;

        // now calculate how much above the center the first regular marking is,
        // eg, where does the 80 go?
        int yAboveCenter = (int)(yPerUnit * (float)extraUnits);

        int valAboveCtr = val - extraUnits;

        // draw regular interval markings
        for (int n = -(numsteps + 1) / 2; n <= (numsteps + 1) / 2; n ++) {
            int y = -yAboveCenter + (int)(yPerStep * n + 0.5);
            int yabs = Math.abs (y);
            if (yabs < 20) continue;               // skip if near center that where given value goes
            if (yabs > height / 2 - 20) continue;  // skip if off the ends
            int valAtY = valAboveCtr + perstep * n;
            if (valAtY < minallowed) continue;
            canvas.drawText (Integer.toString (valAtY), 0, y, nsPaintNum);
        }

        // draw the given value in the center
        canvas.drawText (Integer.toString (val), 0, 0, nsPaintVal);

        // maybe draw glideslope marker on altimeter indicating where glideslope is
        if (marker >= minallowed) {
            float markerStepsBelowCenter = (float)(marker - val) / perstep;
            if (markerStepsBelowCenter < -(float)numsteps / 2) markerStepsBelowCenter = -(float)numsteps / 2;
            if (markerStepsBelowCenter >  (float)numsteps / 2) markerStepsBelowCenter =  (float)numsteps / 2;
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
            float targetStepsBelowCenter = (float)(target - val) / perstep;
            int targetPixBelowCenter = (int)(targetStepsBelowCenter / numsteps * height + 0.5);
            if ((targetPixBelowCenter >= -height / 2) && (targetPixBelowCenter <= height / 2)) {
                canvas.drawLine (-width * 3 / 8, targetPixBelowCenter, width * 3 / 8, targetPixBelowCenter, dgPaintHSI);
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

    private static float Sq (float x) { return x*x; }

    private static String DegString (float deg)
    {
        int hdgnum = ((int)(deg + 1439.5F)) % 360 + 1;
        String st = Integer.toString (hdgnum + 1000).substring (1);
        return st + (char)0xB0;
    }

    public static class Position {
        float altitude;    // metres
        float heading;     // degrees
        float latitude;    // degrees
        float longitude;   // degrees
        float speed;       // metres per second
        long  time;        // milliseconds
    }
}
