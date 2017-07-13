//    Copyright (C) 2016, Mike Rieker, Beverly, MA USA
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
import android.graphics.Color;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Plays back log files recorded by AdsbGpsRThread class.
 */
public class LogPlayGpsAdsb extends GpsAdsbReceiver {
    public final static String TAG = "WairToNow";

    private AdsbGpsRThread adsbContext;
    private boolean displayOpen;
    private boolean streamClosed;
    private volatile boolean pktReceivedQueued;
    private byte[] onebyte = new byte[1];
    private byte[] sizebytes = new byte[2];
    private CheckBox btAdsbEnab;
    private InputStream playStream;
    private int sizeint;
    private LinearLayout linearLayout;
    private long startGpsTime;
    private String[] namearray;
    private TextArraySpinner btAdsbName;
    private TextView btAdsbStatus;

    @SuppressLint("SetTextI18n")
    public LogPlayGpsAdsb (SensorsView sv)
    {
        wairToNow = sv.wairToNow;

        btAdsbEnab   = new CheckBox (wairToNow);
        btAdsbName   = new TextArraySpinner (wairToNow);
        btAdsbStatus = new TextView (wairToNow);

        btAdsbEnab.setText ("Log file playback");

        wairToNow.SetTextSize (btAdsbEnab);
        wairToNow.SetTextSize (btAdsbName);
        wairToNow.SetTextSize (btAdsbStatus);

        linearLayout = new LinearLayout (wairToNow);
        linearLayout.setOrientation (LinearLayout.VERTICAL);
        linearLayout.addView (btAdsbEnab);
        linearLayout.addView (btAdsbName);
        linearLayout.addView (btAdsbStatus);

        btAdsbName.setTitle ("Select log");
        btAdsbName.setLabels (new String[0], "Cancel", "(select)", null);
        btAdsbName.setIndex (TextArraySpinner.NOTHING);
        btAdsbName.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                btAdsbNameClicked ();
            }
        });
        btAdsbName.setOnItemSelectedListener (new TextArraySpinner.OnItemSelectedListener () {
            @Override
            public boolean onItemSelected (View view, int index) {
                return true;
            }

            @Override
            public boolean onNegativeClicked (View view) {
                return false;
            }

            @Override
            public boolean onPositiveClicked (View view) {
                return false;
            }
        });

        btAdsbEnab.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view)
            {
                btAdsbEnabClicked ();
            }
        });
    }

    /*****************************************************************************\
     *  GpsAdsbReceiver implementation                                           *
     *  Used by SensorsView to let the user select a log file as a data source.  *
    \*****************************************************************************/

    @Override  // GpsAdsbReceiver
    public String getPrefKey ()
    {
        return "logplay";
    }

    @Override  // GpsAdsbReceiver
    public String getDisplay ()
    {
        return btAdsbEnab.getText ().toString ();
    }

    @Override  // GpsAdsbReceiver
    public View displayOpened ()
    {
        displayOpen = true;
        return linearLayout;
    }

    @Override  // GpsAdsbReceiver
    public void displayClosed ()
    {
        displayOpen = false;
    }

    @SuppressLint("SetTextI18n")
    @Override  // GpsAdsbReceiver
    public void startSensor ()
    { /* don't start anything on app restart */ }

    @Override  // GpsAdsbReceiver
    public void stopSensor ()
    {
        // don't show any error alerts during or after close
        if (adsbContext != null) {
            adsbContext.close ();
            adsbContext = null;
            playStream  = null;
        }
    }

    @Override  // GpsAdsbReceiver
    public void refreshStatus ()
    {
        adsbPacketReceivedUI.run ();
    }

    @Override  // GpsAdsbReceiver
    public boolean isSelected ()
    {
        return adsbContext != null;
    }

    private void btAdsbNameClicked ()
    {
        File[] files = new File (logdir).listFiles ();
        if (files != null) {
            ArrayList<String> namelist = new ArrayList<> (files.length);
            for (File file : files) {
                String name = file.getName ();
                namelist.add (name.substring (0, name.length ()));
            }
            namearray = namelist.toArray (new String[namelist.size()]);
            btAdsbName.setLabels (namearray, "Cancel", "(select)", null);
        }
    }

    /**
     * User just clicked 'log file playback' enable checkbox.
     * Open or close the log file selected in the selection box.
     * If opening file, start a thread to read it and pass packets to the decoder.
     */
    @SuppressLint("SetTextI18n")
    private void btAdsbEnabClicked ()
    {
        int index = btAdsbName.getIndex ();
        if (btAdsbEnab.isChecked () && (index >= 0)) {
            getButton ().setRingColor (Color.GREEN);

            /*
             * Try to open log file.
             */
            String name = logdir + "/" + namearray[index];
            sizeint = 0;
            streamClosed = false;
            try {
                playStream = new FileInputStream (name);
            } catch (IOException ioe) {
                String msg = ioe.getMessage ();
                if (msg == null) msg = ioe.getClass ().getSimpleName ();
                btAdsbStatus.setText ("error opening " + name + ": " + msg);
                btAdsbEnab.setChecked (false);
                getButton ().setRingColor (Color.TRANSPARENT);
                btAdsbName.setEnabled (true);
                return;
            }

            /*
             * Disable changing file name while running.
             */
            btAdsbName.setEnabled (false);

            /*
             * Fork a thread to open and read from the file.
             */
            wairToNow.currentGPSTime = 0;
            startGpsTime = 0;
            adsbContext  = new AdsbGpsRThread (receiverStream, wairToNow);
            adsbContext.setName (getPrefKey ());
            adsbContext.start ();
        } else {
            getButton ().setRingColor (Color.TRANSPARENT);

            /*
             * Shut down whichever GPS receiver we are using now.
             */
            stopSensor ();

            /*
             * Allow changing file name.
             */
            btAdsbEnab.setChecked (false);
            btAdsbName.setEnabled (true);
        }

        /*
         * Update status display saying which sensors are enabled.
         */
        wairToNow.sensorsView.SetGPSLocation ();
    }

    /**
     * A packet was read from the log file and this page is visible.
     * Update the on-screen text to show updated byte count.
     */
    private final Runnable adsbPacketReceivedUI = new Runnable () {
        @Override
        public void run ()
        {
            pktReceivedQueued = false;
            if (playStream != null) {
                String text = adsbContext.getConnectStatusText ("Reading");
                btAdsbStatus.setText (text);
            }
        }
    };

    /***********************************************\
     *  InputStream implementation                 *
     *  Reads NMEA/GDL-90 messages from the file.  *
     *  Also updates on-screen status.             *
    \***********************************************/

    private final InputStream receiverStream = new InputStream () {

        @Override  // InputStream
        public int read () throws IOException
        {
            int rc = read (onebyte, 0, 1);
            if (rc <= 0) return -1;
            return onebyte[0] & 0xFF;
        }

        @Override  // InputStream
        public int read (@NonNull byte[] buffer, int offset, int length) throws IOException
        {
            // may update stats display with everything we have done so far
            if (displayOpen && !pktReceivedQueued) {
                pktReceivedQueued = true;
                wairToNow.runOnUiThread (adsbPacketReceivedUI);
            }

            // all done if stream is closed
            if (streamClosed) return -1;

            // delay a realistic amount based on elapsed time since start
            // as far as our uptime vs the gps time received
            if ((startGpsTime == 0) && (wairToNow.currentGPSTime > 0)) {
                startGpsTime = wairToNow.currentGPSTime - SystemClock.uptimeMillis ();
            }
            if (startGpsTime > 0) {
                long delay = wairToNow.currentGPSTime - SystemClock.uptimeMillis () - startGpsTime;
                if (delay > 0) {
                    if (delay > WairToNow.gpsDownDelay) {
                        delay -= WairToNow.gpsDownDelay;
                        Log.w (TAG, "logplay skipping " + delay + " ms");
                        startGpsTime += delay;
                        delay = WairToNow.gpsDownDelay;
                    }
                    try {
                        Thread.sleep (delay);
                    } catch (InterruptedException ie) {
                        Lib.Ignored ();
                    }
                }
            }

            // maybe we need size of next segment in log file
            if (sizeint == 0) {
                int rc = playStream.read (sizebytes);
                if (rc < 2) return -1;
                sizeint = ((sizebytes[1] & 0xFF) << 8) | (sizebytes[0] & 0xFF);
            }

            // read as much as we can and return to caller
            if (length > sizeint) length = sizeint;
            int rc = playStream.read (buffer, offset, length);
            if (rc > 0) sizeint -= rc;
            return rc;
        }

        @Override  // InputStream
        public void close () throws IOException
        {
            streamClosed = true;
            adsbContext.interrupt ();
        }
    };
}
