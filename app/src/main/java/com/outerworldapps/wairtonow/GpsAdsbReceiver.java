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
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * GPS/ADS-B receiver devices (internal or external).
 * They are selectable on the SensorsView screen.
 */
public abstract class GpsAdsbReceiver {
    public final static String logdir = WairToNow.dbdir + "/gpsadsblogs";
    public final static boolean LOGGING_ENABLED = true;//TODO false;

    public abstract String getPrefKey  ();  // get receiver's preferences key name
    public abstract String getDisplay  ();  // get receiver's display string (used for status display)
    public abstract View displayOpened ();  // SensorsView display open, update status display continually
    public abstract void displayClosed ();  // SensorsView display closed, stop updating status display
    public abstract void startSensor   ();  // sensor selected in preferences and app is active, start receiving
    public abstract void stopSensor    ();  // sensor not selected inprefs or app is inactive, stop receiving
    public abstract void refreshStatus ();  // update status display
    public abstract boolean isSelected ();  // true iff no stopSensor() since most recent startSensor()

    protected WairToNow wairToNow;

    private CheckBox logEnable;
    private RingedButton button;
    private TextView logName;

    /**
     * Create GUI elements for log file creation.
     */
    @SuppressLint("SetTextI18n")
    protected void createLogElements (ViewGroup parent)
    {
        if (LOGGING_ENABLED) {
            logEnable = new CheckBox (wairToNow);
            logEnable.setText ("Write log ");
            wairToNow.SetTextSize (logEnable);

            logName = new TextView (wairToNow);
            wairToNow.SetTextSize (logName);

            LinearLayout ll = new LinearLayout (wairToNow);
            ll.setOrientation (LinearLayout.HORIZONTAL);
            ll.setLayoutParams (new ViewGroup.LayoutParams (ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            ll.addView (logEnable);
            ll.addView (logName);

            parent.addView (ll);
        }
    }

    /**
     * Fill in log file GUI elements from saved values in preferences.
     */
    protected void getLogFromPreferences (SharedPreferences prefs)
    {
        if (LOGGING_ENABLED) {
            String prefKey = getPrefKey ();
            boolean logenab = prefs.getBoolean (prefKey + "LogEnable", false);
            logEnable.setChecked (logenab);
        }
    }

    /**
     * Write out log file GUI elements to preferences.
     */
    protected void putLogToPreferences (SharedPreferences.Editor editr)
    {
        if (LOGGING_ENABLED) {
            String prefKey = getPrefKey ();
            editr.putBoolean (prefKey + "LogEnable", logEnable.isChecked ());
        }
    }

    /**
     * Enable/Disable log file GUI elements.
     */
    protected void setLogChangeEnable (boolean enable)
    {
        if (LOGGING_ENABLED) logEnable.setEnabled (enable);
    }

    /**
     * Create log file.
     */
    protected OutputStream createLogFile ()
    {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (LOGGING_ENABLED && logEnable.isChecked ()) {

            // make log file name based on date/time
            SimpleDateFormat sdf = new SimpleDateFormat ("yyyyMMdd-HHmmss", Locale.US);
            sdf.setTimeZone (TimeZone.getTimeZone ("UTC"));
            final String logname = sdf.format (System.currentTimeMillis ()) + "." + getPrefKey ();
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    logName.setText (logname);
                }
            });

            // make sure we have the directory to put the log file in
            Lib.Ignored (new File (logdir).mkdirs ());

            // get the full path name to create
            String logpath = logdir + "/" + logname;

            // try to create it
            try {
                return new BufferedOutputStream (new FileOutputStream (logpath));
            } catch (IOException ioe) {
                String msg = ioe.getMessage ();
                if (msg == null) msg = ioe.getClass ().getSimpleName ();
                errorMessage ("error creating " + logpath + ": " + msg);
            }
        }
        return null;
    }

    /**
     * Close log file.
     */
    protected static void closeLogFile (OutputStream logStream)
    {
        if (LOGGING_ENABLED) {
            try { logStream.close (); } catch (Exception e) { Lib.Ignored (); }
        }
    }

    /**
     * Display an error message dialog box.
     */
    protected void errorMessage (String message)
    {
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle (getDisplay ());
        adb.setMessage (message);
        adb.setPositiveButton ("OK", null);
        adb.show ();
    }

    /**
     * Get selection button.
     */
    public RingedButton getButton ()
    {
        if (button == null) {
            button = new RingedButton (wairToNow);
            button.setTag (this);
            button.setText (getPrefKey ());
            wairToNow.SetTextSize (button);
        }
        return button;
    }
}
