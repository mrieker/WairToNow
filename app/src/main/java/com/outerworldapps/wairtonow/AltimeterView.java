//    Copyright (C) 2018, Mike Rieker, Beverly, MA USA
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
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

import static android.content.Context.SENSOR_SERVICE;

public class AltimeterView extends View implements WairToNow.CanBeMainView {
    private final static double DIALRATIO  =  5;  // number of times drag finger to drag obs once

    private double baroaltft;   // current altitude (feet)
    private double gpsaltft;
    private double kolsman;     // altimeter setting (inches)
    private double touchDownKol;
    private double touchDownX;
    private double touchDownY;
    private int digitheight;
    private Paint baroAltPaint;
    private Paint gpsAltPaint;
    private Paint kolsmanPaint;
    private Paint needGpsPaint;
    private Paint needBaroPaint;
    private Paint tickMarkPaint;
    private Path needle10kPath;
    private Path needle1kPath;
    private Path needle100Path;
    private Sensor pressureSensor;
    private SensorEventListener sensorEventListener;
    private SensorManager sensorManager;
    private String barostring;
    private String gpsstring;
    private String kolstring;

    private final static String[] digits = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
    };

    private static class AltEntry {
        public double mbar, altmet, altft, tempc;
        public AltEntry (double mbar, double altmet, double altft, double tempc)
        {
            this.mbar   = mbar;
            this.altmet = altmet;
            this.altft  = altft;
            this.tempc  = tempc;
        }
    }

    // http://aviationknowledge.wikidot.com/aviation:sensitive-altimeters
    private final static AltEntry[] alttable = {
        new AltEntry (  10, 31055, 101885, -45.4),
        new AltEntry (  20, 26481,  86881, -50.0),
        new AltEntry (  30, 23849,  78244, -52.7),
        new AltEntry (  40, 22000,  72177, -54.5),
        new AltEntry (  50, 20576,  67507, -55.9),
        new AltEntry (  70, 18442,  60504, -56.5),
        new AltEntry ( 100, 16180,  53083, -56.5),
        new AltEntry ( 150, 13608,  44647, -56.5),
        new AltEntry ( 200, 11784,  38662, -56.5),
        new AltEntry ( 226, 11000,  36091, -56.5),
        new AltEntry ( 250, 10363,  33999, -52.3),
        new AltEntry ( 300,  9164,  30065, -44.5),
        new AltEntry ( 400,  7185,  23574, -31.7),
        new AltEntry ( 500,  5574,  18289, -21.2),
        new AltEntry ( 600,  4206,  13801, -12.3),
        new AltEntry ( 700,  3012,   9882,  -4.6),
        new AltEntry ( 800,  1949,   6394,   2.3),
        new AltEntry ( 850,  1457,   4781,   5.5),
        new AltEntry ( 900,   988,   3243,   8.6),
        new AltEntry ( 950,   540,   1773,  11.5),
        new AltEntry (1000,   111,    364,  14.3),
        new AltEntry (1013.25,  0,      0,  15.0),
        new AltEntry (1050,  -302,   -989,  17.0) };

    public AltimeterView (Context ctx, AttributeSet attrs)
    {
        super (ctx, attrs);
        constructor ();
    }

    public AltimeterView (Context ctx)
    {
        super (ctx);
        constructor ();
    }

    private void constructor ()
    {
        needle10kPath = new Path ();
        needle10kPath.moveTo (   0, -900);
        needle10kPath.lineTo (-200, -900);
        needle10kPath.lineTo (   0, -700);
        needle10kPath.lineTo ( 200, -900);
        needle10kPath.lineTo (   0, -900);

        needle100Path = new Path ();
        needle100Path.moveTo (   0, -100);
        needle100Path.lineTo ( -30, -100);
        needle100Path.lineTo ( -30, -700);
        needle100Path.lineTo (   0, -900);
        needle100Path.lineTo (  30, -700);
        needle100Path.lineTo (  30, -100);
        needle100Path.lineTo (   0, -100);

        needle1kPath = new Path ();
        needle1kPath.moveTo (   0, -100);
        needle1kPath.lineTo ( -40, -100);
        needle1kPath.lineTo ( -70, -500);
        needle1kPath.lineTo (   0, -700);
        needle1kPath.lineTo (  70, -500);
        needle1kPath.lineTo (  40, -100);
        needle1kPath.lineTo (   0, -100);

        tickMarkPaint = new Paint ();
        tickMarkPaint.setColor (Color.GREEN);
        tickMarkPaint.setStyle (Paint.Style.FILL);
        tickMarkPaint.setTextAlign (Paint.Align.CENTER);
        tickMarkPaint.setTextSize (200);

        kolsmanPaint = new Paint ();
        kolsmanPaint.setColor (Color.WHITE);
        kolsmanPaint.setStrokeWidth (10);
        kolsmanPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        kolsmanPaint.setTextSize (150);

        baroAltPaint = new Paint ();
        baroAltPaint.setColor (Color.WHITE);
        baroAltPaint.setStrokeWidth (10);
        baroAltPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        baroAltPaint.setTextAlign (Paint.Align.RIGHT);
        baroAltPaint.setTextSize (150);

        gpsAltPaint = new Paint ();
        gpsAltPaint.setColor (Color.YELLOW);
        gpsAltPaint.setStrokeWidth (10);
        gpsAltPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        gpsAltPaint.setTextAlign (Paint.Align.RIGHT);
        gpsAltPaint.setTextSize (150);

        needBaroPaint = new Paint ();
        needBaroPaint.setColor (Color.WHITE);
        needBaroPaint.setStrokeJoin (Paint.Join.ROUND);
        needBaroPaint.setStrokeWidth (30);
        needBaroPaint.setStyle (Paint.Style.STROKE);

        needGpsPaint = new Paint ();
        needGpsPaint.setColor (Color.YELLOW);
        needGpsPaint.setStrokeJoin (Paint.Join.ROUND);
        needGpsPaint.setStrokeWidth (15);
        needGpsPaint.setStyle (Paint.Style.STROKE);

        Rect bounds = new Rect ();
        tickMarkPaint.getTextBounds ("0", 0, 1, bounds);
        digitheight = bounds.height ();

        setGPSAltitude (0.0);
        setKolsman (29.92);
        setPressure (1013.25);
    }

    // BEGIN CanBeMainView //

    public String GetTabName () { return "Altim"; }
    public int GetOrientation () { return ActivityInfo.SCREEN_ORIENTATION_USER; }
    public boolean IsPowerLocked () { return true; }
    public void OpenDisplay ()
    {
        sensorManager = (SensorManager) getContext ().getSystemService (SENSOR_SERVICE);
        if (sensorManager == null) return;
        pressureSensor = sensorManager.getDefaultSensor (Sensor.TYPE_PRESSURE);
        if (pressureSensor == null) return;
        sensorEventListener = new SensorEventListener () {
            @Override
            public void onSensorChanged (SensorEvent sensorEvent) {
                setPressure (sensorEvent.values[0]);
            }
            @Override
            public void onAccuracyChanged (Sensor sensor, int i) { }
        };
        sensorManager.registerListener (sensorEventListener, pressureSensor, SensorManager.SENSOR_DELAY_UI);
    }
    public void CloseDisplay ()
    {
        sensorManager.unregisterListener (sensorEventListener);
    }
    public void ReClicked () { }
    public View GetBackPage () { return this; }

    // END CanBeMainView //

    public void setKolsman (double inches)
    {
        this.kolsman = inches;
        this.kolstring = String.format (Locale.US, "%.2f\"", inches);
        invalidate ();
    }

    public void setPressure (double mbars)
    {
        mbars += (29.92 - kolsman) / 0.02953;

        for (int i = alttable.length - 1; i > 0;) {
            AltEntry altentryb = alttable[i];
            AltEntry altentrya = alttable[--i];
            if (mbars >= altentrya.mbar) {
                this.baroaltft = (mbars - altentrya.mbar) / (altentryb.mbar - altentrya.mbar) *
                        (altentryb.altft - altentrya.altft) + altentrya.altft;
                this.barostring = String.format (Locale.US, "%,d'", Math.round (baroaltft));
                break;
            }
        }

        invalidate ();
    }

    public void setGPSAltitude (double altitude)
    {
        gpsaltft  = altitude * Lib.FtPerM;
        gpsstring = String.format (Locale.US, "%,d'", Math.round (gpsaltft));
        invalidate ();
    }

    /**
     * Touch for turning the dial.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (@NonNull MotionEvent event)
    {
        switch (event.getActionMasked ()) {
            case MotionEvent.ACTION_DOWN: {
                touchDownX = event.getX ();
                touchDownY = event.getY ();
                touchDownKol = kolsman;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                double moveX    = event.getX ();
                double moveY    = event.getY ();
                double centerX  = getWidth ()  / 2.0;
                double centerY  = getHeight () / 2.0;
                double startHdg = Math.atan2 (touchDownX - centerX, touchDownY - centerY);
                double moveHdg  = Math.atan2 (moveX - centerX, moveY - centerY);

                // compute how many degrees finger moved since ACTION_DOWN
                double degsMoved = Math.toDegrees (moveHdg - startHdg);
                while (degsMoved < -180) degsMoved += 360;
                while (degsMoved >= 180) degsMoved -= 360;

                // compute how many degrees to turn dial based on that
                degsMoved /= DIALRATIO;

                // update the dial to the new heading
                setKolsman (touchDownKol - degsMoved / 360.0);

                // if turned a long way, pretend we just did a new finger down
                // ...this lets us go round and round
                if (Math.abs (degsMoved) > 90 / DIALRATIO) {
                    touchDownX = moveX;
                    touchDownY = moveY;
                    touchDownKol = kolsman;
                }
                break;
            }
        }
        return true;
    }

    /**
     * Draw the nav widget.
     */
    @Override
    public void onDraw (Canvas canvas)
    {
        float lastWidth  = getWidth ();
        float lastHeight = getHeight ();
        float lastScale  = Math.min (lastWidth, lastHeight) / (1000 * 2);

        canvas.save ();

        // set up translation/scaling so that outer ring is radius 1000 centered at 0,0
        canvas.translate (lastWidth / 2, lastHeight / 2);
        canvas.scale (lastScale, lastScale);

        // draw tick marks around the edge
        for (int ft = 0; ft < 1000; ft += 20) {
            double angrad = ft * Math.PI / 500.0;
            double angcos = Math.cos (angrad);
            double angsin = Math.sin (angrad);
            if (ft % 100 == 0) {
                tickMarkPaint.setStrokeWidth (20);
                canvas.drawLine ((float) (angsin * 800), (float) (angcos * -800),
                        (float) (angsin * 1000), (float) (angcos * -1000), tickMarkPaint);
                canvas.drawText (digits[ft/100], (float) (angsin * 675),
                        (float) (angcos * -675) + digitheight / 2, tickMarkPaint);
            } else {
                tickMarkPaint.setStrokeWidth (10);
                canvas.drawLine ((float) (angsin * 850), (float) (angcos * -850),
                        (float) (angsin * 1000), (float) (angcos * -1000), tickMarkPaint);
            }
        }

        // draw kolsman setting
        if (pressureSensor != null) canvas.drawText (kolstring, 100, 400, kolsmanPaint);

        // draw altitude strings
        if (pressureSensor != null) canvas.drawText (barostring, -100, 400, baroAltPaint);
        canvas.drawText (gpsstring, -100, 250, gpsAltPaint);

        // draw altitude needles
        if (pressureSensor != null) drawNeedles (canvas, baroaltft, needBaroPaint);
        drawNeedles (canvas, gpsaltft, needGpsPaint);

        canvas.restore ();
    }

    private void drawNeedles (Canvas canvas, double altft, Paint needlePaint)
    {
        double rot10k = altft * (36.0 / 10000.0);
        double rot1k  = altft * (36.0 /  1000.0);
        double rot100 = altft * (36.0 /   100.0);

        canvas.rotate ((float) rot10k);
        canvas.drawPath (needle10kPath, needlePaint);

        canvas.rotate ((float) (rot1k - rot10k));
        canvas.drawPath (needle1kPath, needlePaint);

        canvas.rotate ((float) (rot100 - rot1k));
        canvas.drawPath (needle100Path, needlePaint);

        canvas.rotate ((float) (0.0 - rot100));
    }
}
