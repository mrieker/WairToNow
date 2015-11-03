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
import java.util.Date;

import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.view.View;

/**
 * Display a GPS status panel.
 */
public class GPSStatusView
        extends View
        implements WairToNow.CanBeMainView {
    private static final float SMPerNM = 1.15078F;

    private DecimalFormat formatter = new DecimalFormat ("#.#");
    private float altitude;    // metres MSL
    private float latitude;    // degrees
    private float longitude;   // degrees
    private float heading;     // degrees true
    private float speed;       // metres per second
    private Iterable<GpsSatellite> satellites;
    private long  time;        // milliseconds since Jan 1, 1970 UTC
    private WairToNow wairToNow;
    private Paint ignoredSpotsPaint = new Paint ();
    private Paint ringsPaint        = new Paint ();
    private Paint textPaint         = new Paint ();
    private Paint usedSpotsPaint    = new Paint ();

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
        textPaint.setTextSize (wairToNow.textSize);
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "GPS";
    }

    /**
     * The GPS has new position information for us.
     * Save it and update screen.
     */
    public void SetGPSLocation (Location loc)
    {
        altitude  = (float) loc.getAltitude ();
        heading   = loc.getBearing ();
        latitude  = (float) loc.getLatitude ();
        longitude = (float) loc.getLongitude ();
        speed     = loc.getSpeed ();
        time      = loc.getTime ();
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
     * The screen is about to be made current so force portrait mode.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

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
     * Callback to draw the instruments on the screen.
     */
    @Override
    protected void onDraw (Canvas canvas)
    {
        if (satellites == null) {
            canvas.drawText ("No satellite status yet", 10, 40, textPaint);
            return;
        }

        int canvasWidth   = canvas.getWidth ();
        int circleCenterX = canvasWidth / 2;
        int circleCenterY = canvasWidth / 2;
        int circleRadius  = canvasWidth * 3 / 8;

        canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 30 / 90, ringsPaint);
        canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 60 / 90, ringsPaint);
        canvas.drawCircle (circleCenterX, circleCenterY, circleRadius * 90 / 90, ringsPaint);

        for (GpsSatellite sat : satellites) {
            // hasAlmanac() and hasEphemeris() seem to always return false
            // getSnr() in range 0..30 approx
            float size   = sat.getSnr () / 3;
            float radius = (90 - sat.getElevation()) * circleRadius / 90;
            float azideg = sat.getAzimuth ();
            float deltax = radius * Mathf.sindeg (azideg);
            float deltay = radius * Mathf.cosdeg (azideg);
            Paint paint  = sat.usedInFix () ? usedSpotsPaint : ignoredSpotsPaint;
            canvas.drawCircle (circleCenterX + deltax, circleCenterY - deltay, size, paint);
        }

        float x  = 10;
        float y  = canvasWidth;
        float dy = textPaint.getTextSize () * 1.25F;

        Date date = new Date (time);
        int tzoff = date.getTimezoneOffset ();
        date.setTime (time + tzoff * 60000);
        int year  = date.getYear ();
        int month = date.getMonth ();
        int day   = date.getDate ();
        int hour  = date.getHours ();
        int min   = date.getMinutes ();
        int sec   = date.getSeconds ();
        int mill  = (int)(time % 1000);

        String timestr = Integer.toString (year  + 11900).substring (1) + "-" +
                         Integer.toString (month +   101).substring (1) + "-" +
                         Integer.toString (day   +   100).substring (1) + " " +
                         Integer.toString (hour  +   100).substring (1) + ":" +
                         Integer.toString (min   +   100).substring (1) + ":" +
                         Integer.toString (sec   +   100).substring (1) + "." +
                         Integer.toString (mill  +  1000).substring (1) + " UTC";
        canvas.drawText (timestr, x, y, textPaint);
        y += dy;

        String locstr = wairToNow.optionsView.LatLonString (latitude,  'N', 'S') + "    " +
                        wairToNow.optionsView.LatLonString (longitude, 'E', 'W');
        canvas.drawText (locstr, x, y, textPaint);
        y += dy;

        String althdgstr = Integer.toString ((int) (altitude / 3.28084)) + " ft MSL    " +
                           wairToNow.optionsView.HdgString (heading, latitude, longitude, altitude) + "    ";
        if (wairToNow.optionsView.ktsMphOption.getAlt ()) {
            althdgstr += formatter.format (speed * 3600 / Lib.MPerNM * SMPerNM) + " mph";
        } else {
            althdgstr += formatter.format (speed * 3600 / Lib.MPerNM) + " kts";
        }

        canvas.drawText (althdgstr, x, y, textPaint);

        wairToNow.drawGPSAvailable (canvas, this);
    }
}
