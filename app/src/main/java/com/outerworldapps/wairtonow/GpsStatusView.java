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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;

/**
 * Display a GPS status panel.
 * Also acts as a compass.
 */
public class GpsStatusView
        extends View
        implements SensorEventListener {

    private final static String[] compDirs = new String[] { "N", "E", "S", "W" };

    private float compRotDeg;  // compass rotation
    private float[] geomag;
    private float[] gravity;
    private float[] orient = new float[3];
    private float[] rotmat = new float[9];
    private Iterable<MyGpsSatellite> satellites;
    private Paint ignoredSpotsPaint = new Paint ();
    private Paint ringsPaint        = new Paint ();
    private Paint textPaint         = new Paint ();
    private Paint usedSpotsPaint    = new Paint ();
    private SensorManager instrSM;

    public GpsStatusView (Context ctx)
    {
        super (ctx);
        construct ();
    }

    public void construct ()
    {
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
    }

    public void SetGPSStatus (Iterable<MyGpsSatellite> sats)
    {
        satellites = sats;
        invalidate ();
    }

    public void Startup (WairToNow wairToNow)
    {
        geomag     = null;
        gravity    = null;
        compRotDeg = 99999;
        textPaint.setTextSize (wairToNow.textSize);
        instrSM    = (SensorManager) wairToNow.getSystemService (Context.SENSOR_SERVICE);
        if (wairToNow.optionsView.gpsCompassOption.checkBox.isChecked ()) {
            Sensor smf = instrSM.getDefaultSensor (Sensor.TYPE_MAGNETIC_FIELD);
            Sensor sac = instrSM.getDefaultSensor (Sensor.TYPE_ACCELEROMETER);
            instrSM.registerListener (this, smf, SensorManager.SENSOR_DELAY_UI);
            instrSM.registerListener (this, sac, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void Shutdown ()
    {
        instrSM.unregisterListener (this);
    }

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
            invalidate ();
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
        float textHeight    = textPaint.getTextSize ();
        float circleCenterX = getWidth ()  / 2.0F;
        float circleCenterY = getHeight () / 2.0F;
        float circleRadius  = Math.min (circleCenterX, circleCenterY) - textHeight * 2;

        canvas.save ();
        try {
            if (compRotDeg != 99999) {
                String cmphdgstr = Integer.toString (1360 - Math.round (compRotDeg + 360.0F) % 360).substring (1) + '\u00B0';
                canvas.drawText (cmphdgstr, circleCenterX, textHeight, textPaint);
                canvas.rotate (compRotDeg, circleCenterX, circleCenterY);
            }

            for (String compDir : compDirs) {
                canvas.drawText (compDir, circleCenterX, circleCenterY - circleRadius, textPaint);
                canvas.rotate (90.0F, circleCenterX, circleCenterY);
            }

            if (satellites != null) {
                canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 30 / 90, ringsPaint);
                canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 60 / 90, ringsPaint);
            }
            canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 90 / 90, ringsPaint);

            if (satellites != null) for (MyGpsSatellite sat : satellites) {
                // hasAlmanac() and hasEphemeris() seem to always return false
                // getSnr() in range 0..30 approx
                float size = sat.snr / 3;
                float radius = (90 - sat.elev) * circleRadius / 90;
                float azideg = sat.azim;
                float deltax = radius * Mathf.sindeg (azideg);
                float deltay = radius * Mathf.cosdeg (azideg);
                Paint paint = sat.used ? usedSpotsPaint : ignoredSpotsPaint;
                canvas.drawCircle (circleCenterX + deltax, circleCenterY - deltay, size, paint);
            }
        } finally {
            canvas.restore ();
        }
    }
}
