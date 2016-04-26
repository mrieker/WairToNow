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

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.view.View;

/**
 * Display a GPS status panel.
 */
public class GPSStatusView
        extends View
        implements WairToNow.CanBeMainView,
                SensorEventListener {
    private static final float SMPerNM = 1.15078F;

    private final static String[] compDirs = new String[] { "N", "E", "S", "W" };

    private DecimalFormat formatter = new DecimalFormat ("#.#");
    private float compRotDeg;  // compass rotation
    private float[] geomag;
    private float[] gravity;
    private float[] orient = new float[3];
    private float[] rotmat = new float[9];
    private Iterable<GpsSatellite> satellites;
    private WairToNow wairToNow;
    private Paint ignoredSpotsPaint = new Paint ();
    private Paint ringsPaint        = new Paint ();
    private Paint textPaint         = new Paint ();
    private Paint usedSpotsPaint    = new Paint ();
    private SensorManager instrSM;
    private SimpleDateFormat utcfmt;

    public GPSStatusView (WairToNow na)
    {
        super (na);

        wairToNow = na;

        ringsPaint.setColor (Color.YELLOW);
        ringsPaint.setStyle (Paint.Style.STROKE);
        ringsPaint.setStrokeWidth (2);

        usedSpotsPaint.setColor (Color.GREEN);
        usedSpotsPaint.setStyle (Paint.Style.FILL);

        ignoredSpotsPaint.setColor (Color.CYAN);
        ignoredSpotsPaint.setStyle (Paint.Style.STROKE);

        textPaint.setColor (Color.WHITE);
        textPaint.setStrokeWidth (3);
        textPaint.setTextAlign (Paint.Align.CENTER);
        textPaint.setTextSize (wairToNow.textSize);

        utcfmt = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS 'UTC'''", Locale.US);
        utcfmt.setTimeZone (TimeZone.getTimeZone ("UTC"));
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "GPS";
    }

    public void SetGPSStatus (GpsStatus sts)
    {
        satellites = sts.getSatellites ();
    }

    /**
     * What to show when the back button is pressed
     */
    @Override
    public View GetBackPage ()
    {
        return wairToNow.chartView;
    }

    /**
     * The screen is about to be made current so force portrait mode and maybe turn compass on.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        geomag     = null;
        gravity    = null;
        compRotDeg = 99999;
        instrSM    = (SensorManager) wairToNow.getSystemService (Context.SENSOR_SERVICE);
        if (wairToNow.optionsView.gpsCompassOption.checkBox.isChecked ()) {
            Sensor smf = instrSM.getDefaultSensor (Sensor.TYPE_MAGNETIC_FIELD);
            Sensor sac = instrSM.getDefaultSensor (Sensor.TYPE_ACCELEROMETER);
            instrSM.registerListener (this, smf, SensorManager.SENSOR_DELAY_UI);
            instrSM.registerListener (this, sac, SensorManager.SENSOR_DELAY_UI);
        }
    }

    /**
     * This screen is no longer current.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        instrSM.unregisterListener (this);
    }

    /**
     * Tab is being re-clicked when already active.
     */
    @Override  // WairToNow.CanBeMainView
    public void ReClicked ()
    { }

    /**
     * Got a compass reading.
     */
    @Override  // SensorEventListener
    public void onSensorChanged (SensorEvent event)
    {
        switch (event.sensor.getType ()) {
            case Sensor.TYPE_MAGNETIC_FIELD: {
                geomag = event.values;
                break;
            }
            case Sensor.TYPE_ACCELEROMETER: {
                gravity = event.values;
                break;
            }
        }

        if ((geomag != null) && (gravity != null)) {
            SensorManager.getRotationMatrix (rotmat, null, gravity, geomag);
            SensorManager.getOrientation (rotmat, orient);
            compRotDeg = - Mathf.toDegrees (orient[0]);
            geomag  = null;
            gravity = null;
            postInvalidate ();
        }
    }

    @Override  // SensorEventListener
    public void onAccuracyChanged (Sensor sensor, int accuracy)
    { }

    /**
     * Callback to draw the instruments on the screen.
     */
    @Override
    protected void onDraw (Canvas canvas)
    {
        int canvasWidth   = getWidth ();
        int circleCenterX = canvasWidth / 2;
        int circleCenterY = canvasWidth / 2;
        int circleRadius  = canvasWidth * 3 / 8;

        if (compRotDeg != 99999) {
            String cmphdgstr = Integer.toString (1360 - Math.round (compRotDeg + 360.0F) % 360).substring (1) + '\u00B0';
            canvas.drawText (cmphdgstr, circleCenterX, circleCenterY / 8, textPaint);
        }

        canvas.save ();
        try {
            if (compRotDeg != 99999) canvas.rotate (compRotDeg, circleCenterX, circleCenterY);

            for (String compDir : compDirs) {
                canvas.drawText (compDir, circleCenterX, circleCenterY - circleRadius, textPaint);
                canvas.rotate (90.0F, circleCenterX, circleCenterY);
            }

            canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 30 / 90, ringsPaint);
            canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 60 / 90, ringsPaint);
            canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 90 / 90, ringsPaint);

            if (satellites != null) for (GpsSatellite sat : satellites) {
                // hasAlmanac() and hasEphemeris() seem to always return false
                // getSnr() in range 0..30 approx
                float size = sat.getSnr () / 3;
                float radius = (90 - sat.getElevation ()) * circleRadius / 90;
                float azideg = sat.getAzimuth ();
                float deltax = radius * Mathf.sindeg (azideg);
                float deltay = radius * Mathf.cosdeg (azideg);
                Paint paint = sat.usedInFix () ? usedSpotsPaint : ignoredSpotsPaint;
                canvas.drawCircle (circleCenterX + deltax, circleCenterY - deltay, size, paint);
            }
        } finally {
            canvas.restore ();
        }

        float yy = canvasWidth;
        float dy = textPaint.getTextSize () * 1.25F;

        String timestr = utcfmt.format (wairToNow.currentGPSTime);
        canvas.drawText (timestr, circleCenterX, yy, textPaint);
        yy += dy;

        float latitude  = wairToNow.currentGPSLat;
        float longitude = wairToNow.currentGPSLon;
        String locstr = wairToNow.optionsView.LatLonString (latitude,  'N', 'S') + "    " +
                        wairToNow.optionsView.LatLonString (longitude, 'E', 'W');
        canvas.drawText (locstr, circleCenterX, yy, textPaint);
        yy += dy;

        float altitude = wairToNow.currentGPSAlt;
        float heading  = wairToNow.currentGPSHdg;
        float speed    = wairToNow.currentGPSSpd;
        String althdgstr = Integer.toString ((int) (altitude / 3.28084)) + " ft MSL    " +
                           wairToNow.optionsView.HdgString (heading, latitude, longitude, altitude) + "    ";
        if (wairToNow.optionsView.ktsMphOption.getAlt ()) {
            althdgstr += formatter.format (speed * 3600 / Lib.MPerNM * SMPerNM) + " mph";
        } else {
            althdgstr += formatter.format (speed * 3600 / Lib.MPerNM) + " kts";
        }

        canvas.drawText (althdgstr, circleCenterX, yy, textPaint);

        wairToNow.drawGPSAvailable (canvas, this);
    }
}
