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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Manage bread crumbs files.
 */
@SuppressLint("ViewConstructor")
public class CrumbsView extends ScrollView implements WairToNow.CanBeMainView {
    private final static int INTERVALMS   = 10000;  // record a point every 10 seconds
    private final static int TYPE_UNKNOWN = 0;
    private final static int TYPE_CSV_GZ  = 1;
    private final static int TYPE_GPX_GZ  = 2;
    private final static SimpleDateFormat gpxdatefmt = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    private final static String TAG = "WairToNow";
    private final static String crumbsdir = WairToNow.dbdir + "/crumbs";
    private final static String[] typeSuffixes = new String[] { null, ".csv.gz", ".gpx.gz" };

    private LinearLayout buttonList;
    private Position     curGPSPos;
    private TrailButton  activeButton;
    private WairToNow    wairToNow;

    public CrumbsView (WairToNow wtn)
    {
        super (wtn);
        wairToNow = wtn;
        gpxdatefmt.setTimeZone (TimeZone.getTimeZone ("UTC"));
    }

    @Override  // CanBeMainView
    public String GetTabName ()
    {
        return "Crumbs";
    }

    @Override  // CanBeMainView
    public int GetOrientation ()
    {
        return ActivityInfo.SCREEN_ORIENTATION_USER;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    @Override  // CanBeMainView
    public void OpenDisplay ()
    {
        if (buttonList == null) {

            // make sure we have directory to put new files in
            Lib.Ignored (new File (crumbsdir).mkdirs ());

            // create list to put buttons in, one button per file
            buttonList = new LinearLayout (wairToNow);
            buttonList.setOrientation (LinearLayout.VERTICAL);

            // put 'create' button at the top
            buttonList.addView (new CreateNewButton ());

            // put existing file buttons next
            // sort them by descending date
            File[] existingFiles = new File (crumbsdir).listFiles ();
            TreeMap<Long,ShowExistingButton> sortedFiles = new TreeMap<> ();
            for (File f : existingFiles) {
                String name = f.getName ();
                int type = TYPE_UNKNOWN;
                if (name.endsWith (".csv.gz")) type = TYPE_CSV_GZ;
                if (name.endsWith (".gpx.gz")) type = TYPE_GPX_GZ;
                if (type != TYPE_UNKNOWN) {
                    name = name.substring (0, name.length () - 7);
                    name = URLDecoder.decode (name);
                    long date = GetTrailFileDate (name, type);
                    if (date > 0) sortedFiles.put (- date, new ShowExistingButton (name, type, date));
                }
            }
            for (ShowExistingButton seb : sortedFiles.values ()) {
                buttonList.addView (seb);
            }

            addView (buttonList);
        }
    }

    @Override  // CanBeMainView
    public void CloseDisplay ()
    { }

    @Override  // CanBeMainView
    public void ReClicked ()
    { }

    @Override  // CanBeMainView
    public View GetBackPage ()
    {
        return this;
    }

    /**
     * Get locations of the currently selected trail.
     */
    public LinkedList<Position> GetShownTrail ()
    {
        return (activeButton == null) ? null : activeButton.displaytrail;
    }

    /**
     * App is exiting, close any recording file.
     */
    public void CloseFiles ()
    {
        if (activeButton != null) {
            activeButton.onClick (null);
        }
    }

    /**
     * Process an incoming GPS location.
     */
    public void SetGPSLocation ()
    {
        if (activeButton != null) {
            activeButton.GotGPSLocation ();
        }
    }

    /**
     * Clicking one of these buttons attempts to de-activate a previously activated one
     * then tries to activate the clicked one.
     */
    private abstract class TrailButton extends Button implements OnClickListener {
        public int type;
        public LinkedList<Position> displaytrail;
        public LinkedList<Position> playbacktrail;
        public String name;

        public TrailButton ()
        {
            super (wairToNow);
            setOnClickListener (this);
            wairToNow.SetTextSize (this);
        }

        // the button is trying to be activated
        // return whether or not the activation was successful
        public abstract boolean OpenClicked ();

        // the button is being deactivated
        public abstract void CloseClicked ();

        // there is an incoming GPS location to process
        public abstract void GotGPSLocation ();

        @Override  // OnClickListener
        public final void onClick (View v)
        {
            TrailButton ctb = activeButton;

            // if there is an active button, deactivate it
            if (ctb != null) {
                activeButton = null;
                ctb.CloseClicked ();
                ctb.setTextColor (Color.BLACK);
            }

            // if they clicked something other than the active button,
            // activate it.  then flip back to the chart page.
            if ((ctb != this) && OpenClicked ()) {
                activeButton = this;
                wairToNow.SetCurrentTab (wairToNow.chartView);
            }

            // chart probably has to be redrawn cuz the trail info changed
            wairToNow.chartView.invalidate ();
        }
    }

    /**
     * Show an existing trail recording file.
     */
    private class ShowExistingButton extends TrailButton implements OnLongClickListener, Runnable {
        protected long started;

        private int rtplayback;
        private Iterator<Position> rtpbiter;
        private long realtime;
        private Position lastposition;
        private Position nextposition;

        public ShowExistingButton (String n, int t, long d)
        {
            name = n;
            type = t;
            started = d;
            setOnLongClickListener (this);
            SetButtonText ();
        }

        @Override  // TrailButton
        public boolean OpenClicked ()
        {
            // try to read existing trail file into memory
            displaytrail = new LinkedList<> ();
            playbacktrail = new LinkedList<> ();
            Exception e = ReadTrailFile (displaytrail, playbacktrail, name, type, true);
            if (e != null) {
                ErrorMsg ("Error reading " + name, e);
                if (playbacktrail.size () <= 0) {
                    displaytrail = null;
                    playbacktrail = null;
                    return false;
                }
            }

            // make radio buttons to select playback speed
            SharedPreferences prefs = wairToNow.getPreferences (Activity.MODE_PRIVATE);
            rtplayback = prefs.getInt ("crumbPlaybackSpeed", 0);
            RadioGroup rg = new RadioGroup (wairToNow);
            rg.setOrientation (LinearLayout.VERTICAL);
            rg.addView (new PaceButton (0, "static"));
            rg.addView (new PaceButton (1, "normal"));
            rg.addView (new PaceButton (2, "2x normal"));
            rg.addView (new PaceButton (3, "3x normal"));
            rg.addView (new PaceButton (5, "5x normal"));
            rg.addView (new PaceButton (8, "8x normal"));
            rg.addView (new PaceButton (10, "10x normal"));
            rg.check (rtplayback);
            ScrollView sv = new ScrollView (wairToNow);
            sv.addView (rg);

            // display dialog to query playback speed
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle (name);
            adb.setView (sv);
            adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    OpenItUp ();
                }
            });
            adb.setNegativeButton ("Cancel", null);
            adb.show ();

            return false;
        }

        // radio button to select playback speed
        private class PaceButton extends RadioButton implements OnClickListener {
            public PaceButton (int n, String s)
            {
                super (wairToNow);
                setId (n);
                setOnClickListener (this);
                setText (s);
                wairToNow.SetTextSize (this);
            }
            public void onClick (View v)
            {
                rtplayback = getId ();
            }
        }

        // playback speed selected, display the trail and start playback
        private void OpenItUp ()
        {
            activeButton = this;
            wairToNow.SetCurrentTab (wairToNow.chartView);

            SharedPreferences prefs = wairToNow.getPreferences (Activity.MODE_PRIVATE);
            SharedPreferences.Editor editr = prefs.edit ();
            editr.putInt ("crumbPlaybackSpeed", rtplayback);
            editr.commit ();

            // set button blue meaning this is a playback
            setTextColor (Color.BLUE);

            // position chart screen to first point in the recording
            Position firstpos = playbacktrail.getFirst ();
            wairToNow.chartView.SetCenterLatLon (firstpos.latitude, firstpos.longitude);

            // maybe do real-time playback
            if (rtplayback > 0) {

                // set up to step through the points in the file sequentially
                rtpbiter = playbacktrail.iterator ();

                // get first two points in the file
                // if there is just one, nothing to playback
                if (!rtpbiter.hasNext ()) {
                    rtpbiter = null;
                } else {
                    lastposition = firstpos;
                    nextposition = rtpbiter.next ();

                    // get time the first point is being played back
                    realtime = SystemClock.uptimeMillis ();

                    // allow chart to reposition to keep playback point in the center
                    wairToNow.chartView.ReCenter ();

                    // don't listen to any GPS updates so we don't move screen to GPS position
                    wairToNow.gpsDisabled ++;

                    // move chart to first point
                    run ();
                }
            } else {
                playbacktrail = null;
            }
        }

        /**
         * Playback the next co-ordinate on the existing trail.
         */
        @Override  // Runnable
        public void run ()
        {
            if (rtpbiter != null) {

                // get next point along the trail
                if (lastposition != null) {

                    // pretend the playback position report just came in from the GPS
                    // notify anyone (such as ChartView) who cares
                    wairToNow.SetCurrentLocation (
                            lastposition.speed,
                            lastposition.altitude,
                            lastposition.heading,
                            lastposition.latitude,
                            lastposition.longitude,
                            lastposition.time
                    );

                    // step airplane along at same rate as GPS did * rtplayback factor
                    if (nextposition != null) {
                        long realnow  = SystemClock.uptimeMillis ();
                        long reallate = realnow - realtime;
                        long delta    = (nextposition.time - lastposition.time) / rtplayback - reallate;
                        if (delta <= 0) delta = 1;
                        realtime      = realnow + delta;
                        WairToNow.wtnHandler.runDelayed (delta, this);

                        lastposition = nextposition;
                        nextposition = rtpbiter.hasNext () ? rtpbiter.next () : null;
                    }
                } else {

                    // no more points, playback is complete
                    PlaybackComplete ();
                }
            }
        }

        private void PlaybackComplete ()
        {
            rtpbiter = null;
            lastposition = null;
            nextposition = null;

            // when real GPS co-ord comes in, don't re-center the screen,
            // just leave it at last trail co-ordinate
            wairToNow.chartView.SetCenterLatLon (wairToNow.currentGPSLat, wairToNow.currentGPSLon);

            // allow real incoming GPS co-ords to be processed now
            wairToNow.gpsDisabled --;
        }

        @Override  // TrailButton
        public void CloseClicked ()
        {
            displaytrail = null;
            playbacktrail = null;
            if (rtpbiter != null) {
                PlaybackComplete ();
            }
        }

        @Override  // TrailButton
        public void GotGPSLocation ()
        { }

        @Override  // OnLongClickListener
        public boolean onLongClick (View v)
        {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle (name);
            adb.setMessage (GetStats ());
            adb.setPositiveButton ("Close", null);
            adb.setNeutralButton ("Rename", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    final EditText newNameView = new EditText (wairToNow);
                    newNameView.setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    newNameView.setSingleLine ();
                    newNameView.setText (name);
                    AlertDialog.Builder adb2 = new AlertDialog.Builder (wairToNow);
                    adb2.setTitle ("Rename " + name + " to...");
                    adb2.setView (newNameView);
                    adb2.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                        @Override
                        public void onClick (DialogInterface dialogInterface, int i)
                        {
                            String newName = newNameView.getText ().toString ().trim ();
                            String newPath = GetPath (newName, type);
                            File newFile = new File (newPath);
                            if (newFile.exists ()) {
                                ErrorMsg ("Rename error", "Already have file of that name");
                                return;
                            }
                            File oldFile = new File (GetPath (name, type));
                            if (!oldFile.renameTo (newFile)) {
                                ErrorMsg ("Rename error", "Error renaming " + name + " to " + newName);
                                return;
                            }
                            name = newName;
                            SetButtonText ();
                        }
                    });
                    adb2.setNegativeButton ("Cancel", null);
                    adb2.show ();
                }
            });
            adb.setNegativeButton ("Delete", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    AlertDialog.Builder adb2 = new AlertDialog.Builder (wairToNow);
                    adb2.setTitle ("Delete " + name);
                    adb2.setMessage ("Are you sure you want to delete the file?");
                    adb2.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                        @Override
                        public void onClick (DialogInterface dialogInterface, int i)
                        {
                            File oldFile = new File (GetPath (name, type));
                            if (!oldFile.delete ()) {
                                ErrorMsg ("Delete error", "Error deleting " + name);
                                return;
                            }
                            buttonList.removeView (ShowExistingButton.this);
                        }
                    });
                    adb2.setNegativeButton ("Cancel", null);
                    adb2.show ();
                }
            });
            adb.show ();
            return true;
        }

        /**
         * Set the button's text string based on the name
         * and whether the file exists or not.
         */
        @SuppressLint("SetTextI18n")
        protected void SetButtonText ()
        {
            String dateSuffix = "";
            if (started > 0) {
                java.text.DateFormat f = java.text.DateFormat.getDateTimeInstance ();
                dateSuffix = " (" + f.format (started) +")";
            }
            setText (name + dateSuffix);
        }

        /**
         * Get statistics string for display.
         */
        private String GetStats ()
        {
            // get list of points on trail by reading fike
            LinkedList<Position> trail = new LinkedList<> ();
            //noinspection ThrowableResultOfMethodCallIgnored
            Exception e = ReadTrailFile (trail, null, name, type, true);
            String err = "";
            if (e != null) {
                String msg = e.getMessage ();
                if (msg == null) msg = e.getClass ().getSimpleName ();
                err  = "read error: " + msg;
                if (trail.size () <= 0) return err;
                err += "\n";
            }

            // scan list to generate stats
            float distnm  = 0;
            float peakkts = 0;
            Position lastpos = null;
            long finished = 0;
            long started  = 0;
            for (Position pos : trail) {
                if (lastpos == null) {
                    started = pos.time;
                } else {
                    distnm += Lib.LatLonDist (lastpos.latitude, lastpos.longitude,
                            pos.latitude, pos.longitude);
                }
                float kts = pos.speed * Lib.KtPerMPS;
                if (peakkts < kts) peakkts = kts;
                finished = pos.time;
                lastpos  = pos;
            }

            // format elapsed time string
            long elms = finished - started;
            int elsec = (int) (elms / 1000);
            int elmin = elsec / 60;
            int elhr  = elmin / 60;
            int elday = elhr  / 24;
            String elapsed = "";
            if (elday > 0) elapsed +=  elday       + "d ";
            if (elhr  > 0) elapsed += (elhr  % 24) + "h ";
            if (elmin > 0) elapsed += (elmin % 60) + "m ";
            elapsed += (elsec % 60) + "s";

            // format rest of string
            float avgkts = distnm * 3600000.0F / elms;

            java.text.DateFormat f = java.text.DateFormat.getDateTimeInstance ();
            boolean sm = wairToNow.optionsView.ktsMphOption.getAlt ();

            return err +
                     "started: " + f.format (started) +
                  "\nfinished: " + f.format (finished) +
                   "\nelapsed: " + elapsed +
                  "\ndistance: " + Lib.DistString  (distnm,  sm) +
                 "\navg speed: " + Lib.SpeedString (avgkts,  sm) +
                "\npeak speed: " + Lib.SpeedString (peakkts, sm);
        }
    }

    /**
     * Create a new trail recording file and start recording to it.
     */
    private class CreateNewButton extends TrailButton {
        @SuppressLint("SetTextI18n")
        public CreateNewButton ()
        {
            setText ("Create...");
        }

        @Override  // TrailButton
        public boolean OpenClicked ()
        {
            // ask user for filename to create
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Create...");
            final EditText nameView = new EditText (wairToNow);
            nameView.setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            nameView.setSingleLine ();
            GregorianCalendar nowCal = new GregorianCalendar ();
            String nowStr = String.format (Locale.US, "%04d-%02d%02d-%02d%02d",
                    nowCal.get (Calendar.YEAR),
                    nowCal.get (Calendar.MONTH) + 1,
                    nowCal.get (Calendar.DATE),
                    nowCal.get (Calendar.HOUR_OF_DAY),
                    nowCal.get (Calendar.MINUTE));
            nameView.setText (nowStr);
            adb.setView (nameView);
            adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    final String name = nameView.getText ().toString ().trim ();

                    // re-prompt if they have entered a name that already exists
                    if (new File (GetPath (name, TYPE_CSV_GZ)).exists () || new File (GetPath (name, TYPE_GPX_GZ)).exists ()) {
                        AlertDialog.Builder adb2 = new AlertDialog.Builder (wairToNow);
                        adb2.setTitle ("Create...");
                        adb2.setMessage (name + " already exists, overwrite?");
                        adb2.setPositiveButton ("Overwrite", new DialogInterface.OnClickListener () {
                            @Override
                            public void onClick (DialogInterface dialogInterface, int i)
                            {
                                // user confirmed to overwrite, remove the old file's button
                                int nchildren = buttonList.getChildCount ();
                                for (i = nchildren; --i >= 0; ) {
                                    View v = buttonList.getChildAt (i);
                                    if ((v instanceof TrailButton) && name.equals (((TrailButton) v).name)) {
                                        buttonList.removeViewAt (i);
                                    }
                                }

                                // create a new button to overwrite the old file
                                buttonList.addView (new RecordToButton (name), 1);
                            }
                        });
                        adb2.setNegativeButton ("Cancel", null);
                        adb2.show ();
                        return;
                    }

                    // file doesn't already exist, create a recording button for it
                    buttonList.addView (new RecordToButton (name), 1);
                }
            });
            adb.setNegativeButton ("Cancel", null);
            adb.show ();
            return false;
        }

        @Override  // TrailButton
        public void CloseClicked ()
        { }

        @Override  // TrailButton
        public void GotGPSLocation ()
        { }
    }

    /**
     * New file is being recorded to when this button is active.
     */
    private class RecordToButton extends ShowExistingButton {
        private boolean closed;
        private BufferedWriter writer;

        public RecordToButton (String name)
        {
            super (name, TYPE_GPX_GZ, 0);
            onClick (this);
        }

        @Override  // ShowExistingButton
        public boolean OpenClicked ()
        {
            if (closed) {
                return super.OpenClicked ();
            }

            // create output file
            try {
                writer = new BufferedWriter (
                        new OutputStreamWriter (
                                new GZIPOutputStream (
                                        new FileOutputStream (GetPath (name, TYPE_GPX_GZ)))));
                writer.write ("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
                writer.write ("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>\n");
                displaytrail = new LinkedList<> ();
            } catch (IOException ioe) {
                Log.w (TAG, "error creating " + name, ioe);
                ErrorMsg ("Error creating " + name, ioe);
                return false;
            }

            // turn button red indicating record mode
            setTextColor (Color.RED);
            return true;
        }

        @Override  // ShowExistingButton
        public void CloseClicked ()
        {
            if (closed) {
                super.CloseClicked ();
                return;
            }

            try {
                writer.write ("</trkseg></trk></gpx>\n");
                writer.close ();
            } catch (IOException ioe) {
                Log.w (TAG, "error closing " + name, ioe);
                ErrorMsg ("Error closing " + name, ioe);
            }
            writer = null;
            displaytrail = null;

            // any future calls are shunted to ShowExistingButton
            closed = true;

            // set the text on the button to include the file's date/time
            SetButtonText ();
        }

        @Override  // ShowExistingButton
        public void GotGPSLocation ()
        {
            if (closed) {
                super.GotGPSLocation ();
                return;
            }

            // get recording start time from first sample
            if (started == 0) started = wairToNow.currentGPSTime;

            // maybe add it to list of points on screen
            Position pos = curGPSPos;
            if (pos == null) pos = new Position ();
            pos.latitude  = wairToNow.currentGPSLat;
            pos.longitude = wairToNow.currentGPSLon;
            pos.altitude  = wairToNow.currentGPSAlt;
            pos.heading   = wairToNow.currentGPSHdg;
            pos.speed     = wairToNow.currentGPSSpd;
            pos.time      = wairToNow.currentGPSTime;
            if (addPointToTrail (displaytrail, pos, INTERVALMS)) {
                wairToNow.chartView.invalidate ();
                pos = null;
            }
            curGPSPos = pos;

            // try to write record to file
            try {
                if (pos == null) {
                    writer.write (" </trkseg><trkseg>\n");
                }
                writer.write (
                        "  <trkpt lat=\"" + wairToNow.currentGPSLat +
                                "\" lon=\"" + wairToNow.currentGPSLon +
                                "\"><ele>" + wairToNow.currentGPSAlt +
                                "</ele><heading>" + wairToNow.currentGPSHdg +
                                "</heading><speed>" + wairToNow.currentGPSSpd +
                                "</speed><time>" + gpxdatefmt.format (wairToNow.currentGPSTime) +
                                "</time></trkpt>\n");
            } catch (IOException ioe) {
                Log.w (TAG, "error writing " + name, ioe);
                ErrorMsg ("Error writing " + name, ioe);
            }
        }
    }

    /**
     * Get start date of trail file.
     * @param name = name of trail file without directory or suffix
     * @param type = TYPE_CSV_GZ or TYPE_GPX_GZ
     * @return 0: file does not exist or is corrupt; else: date/time recording started
     */
    private static long GetTrailFileDate (String name, int type)
    {
        LinkedList<Position> trail = new LinkedList<> ();
        //noinspection ThrowableResultOfMethodCallIgnored
        Exception e = ReadTrailFile (trail, null, name, type, false);
        if (e != null) return 0;
        return trail.getFirst ().time;
    }

    /**
     * Read trail file into a linked list.
     * @param displaytrail = list to put displayable trail points in (INTERVALMS apart)
     * @param playbacktrail = list to put playback trail points in (1ms apart) (or null)
     * @param name = name of trail file (without directory or suffix)
     * @param type = TYPE_CSV_GZ or TYPE_GPX_GZ
     * @param all = true: read all records; false: read just first record
     * @return null: success; else: error
     */
    @SuppressWarnings("ConstantConditions")
    private static Exception ReadTrailFile (LinkedList<Position> displaytrail,
                                            LinkedList<Position> playbacktrail,
                                            String name, int type, boolean all)
    {
        try {
            BufferedReader br = new BufferedReader (
                    new InputStreamReader (new GZIPInputStream (
                            new FileInputStream (GetPath (name, type)))),
                    8192);
            try {
                switch (type) {
                    case TYPE_CSV_GZ: {
                        String line;
                        Position pos = null;
                        while ((line = br.readLine ()) != null) {
                            String[] cols = Lib.QuotedCSVSplit (line);
                            if (pos == null) pos = new Position ();
                            pos.altitude  = Float.parseFloat (cols[0]);
                            pos.heading   = Float.parseFloat (cols[1]);
                            pos.latitude  = Float.parseFloat (cols[2]);
                            pos.longitude = Float.parseFloat (cols[3]);
                            pos.speed     = Float.parseFloat (cols[4]);
                            pos.time      = Long.parseLong   (cols[5]);
                            if (addPointToTrail (displaytrail, pos, INTERVALMS) |
                                    addPointToTrail (playbacktrail, pos, 1)) pos = null;
                            if (!all) break;
                        }
                        if (displaytrail.size () <= 0) throw new IOException ("empty file");
                        break;
                    }

                    case TYPE_GPX_GZ: {
                        XmlPullParser xpp = Xml.newPullParser ();
                        xpp.setFeature (XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                        xpp.setInput (br);
                        Position pos = null;
                        String text = null;
                        for (int eventType = xpp.getEventType (); eventType != XmlPullParser.END_DOCUMENT; eventType = xpp.next ()) {
                            switch (eventType) {
                                case XmlPullParser.START_TAG: {
                                    text = null;
                                    switch (xpp.getName ()) {
                                        case "trkpt": {
                                            if (pos == null) pos = new Position ();
                                            pos.latitude  = Float.parseFloat (xpp.getAttributeValue (null, "lat"));
                                            pos.longitude = Float.parseFloat (xpp.getAttributeValue (null, "lon"));
                                            break;
                                        }
                                    }
                                    break;
                                }
                                case XmlPullParser.TEXT: {
                                    text = xpp.getText ();
                                    break;
                                }
                                case XmlPullParser.END_TAG: {
                                    switch (xpp.getName ()) {
                                        case "ele": {
                                            if (pos == null) throw new NullPointerException ("<ele> outside of <trkpt>");
                                            if (text == null) throw new NullPointerException ("no text for <ele>");
                                            pos.altitude = Float.parseFloat (text);
                                            text = null;
                                            break;
                                        }
                                        case "heading": {
                                            if (pos == null) throw new NullPointerException ("<heading> outside of <trkpt>");
                                            if (text == null) throw new NullPointerException ("no text for <heading>");
                                            pos.heading = Float.parseFloat (text);
                                            text = null;
                                            break;
                                        }
                                        case "speed": {
                                            if (pos == null) throw new NullPointerException ("<speed> outside of <trkpt>");
                                            if (text == null) throw new NullPointerException ("no text for <speed>");
                                            pos.speed = Float.parseFloat (text);
                                            text = null;
                                            break;
                                        }
                                        case "time": {
                                            if (pos == null) throw new NullPointerException ("<time> outside of <trkpt>");
                                            Date date = gpxdatefmt.parse (text);
                                            pos.time = date.getTime ();
                                            text = null;
                                            break;
                                        }
                                        case "trkpt": {
                                            if (addPointToTrail (displaytrail, pos, INTERVALMS) |
                                                    addPointToTrail (playbacktrail, pos, 1)) pos = null;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!all && displaytrail.size () > 0) break;
                        }
                        break;
                    }

                    default: throw new RuntimeException ("bad type " + type);
                }
            } finally {
                br.close ();
            }
        } catch (Exception e) {
            Log.w (TAG, "error reading " + name, e);
            return e;
        }
        return null;
    }

    /**
     * Add given point to on-screen trail list if INTERVALMS since last one.
     * @return true iff point was added to trail
     */
    private static boolean addPointToTrail (LinkedList<Position> trail, Position pos, int intrvlms)
    {
        if (trail == null) return false;

        if (trail.isEmpty ()) {
            trail.addLast (pos);
            return true;
        }

        Position firstpos = trail.getFirst ();
        Position lastpos  = trail.getLast ();
        long lastinterval = (lastpos.time - firstpos.time) / intrvlms;
        long thisinterval = (pos.time - firstpos.time) / intrvlms;
        if (thisinterval > lastinterval) {
            trail.addLast (pos);
            return true;
        }

        return false;
    }

    private static String GetPath (String name, int type)
    {
        return crumbsdir + "/" + URLEncoder.encode (name) + typeSuffixes[type];
    }

    private void ErrorMsg (String title, Exception e)
    {
        String m = e.getMessage ();
        if (m == null) m = e.getClass ().getSimpleName ();
        ErrorMsg (title, m);
    }

    private void ErrorMsg (String title, String message)
    {
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle (title);
        adb.setMessage (message);
        adb.setNeutralButton ("OK", null);
        adb.show ();
    }
}
