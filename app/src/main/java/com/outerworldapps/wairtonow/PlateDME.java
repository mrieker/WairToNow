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

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Manage DME display on the IAP plates.
 */
public class PlateDME {
    public final static String TAG = "WairToNow";

    private final static int DMECB_DIST = 1;
    private final static int DMECB_TIME = 2;
    private final static int DMECB_RADL = 4;

    private boolean   dmeShowing;
    private double    dmeCharWidth, dmeTextAscent, dmeTextHeight;
    private long      dmeButtonDownAt = Long.MAX_VALUE;
    private NNTreeMap<String,DMECheckboxes> dmeCheckboxeses = new NNTreeMap<> ();
    private Paint     dmeBGPaint      = new Paint ();
    private Paint     dmeTxPaint      = new Paint ();
    private Path      dmeButtonPath   = new Path  ();
    private IAPPlateImage plateView;
    private RectF     dmeButtonBounds = new RectF ();  // where DME dialog display button is on canvas
    private String    icaoid;
    private String    plateid;
    private WairToNow wairToNow;

    private final static String[] columns_dc_dmeid_dc_checked = new String[] { "dc_dmeid", "dc_checked" };

    public PlateDME (WairToNow wtn, IAPPlateImage pv, String ii, String pi)
    {
        wairToNow = wtn;
        plateView = pv;
        icaoid    = ii;
        plateid   = pi;

        dmeBGPaint.setColor (Color.BLACK);
        dmeBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        dmeBGPaint.setStrokeWidth (wairToNow.thickLine);

        dmeTxPaint.setColor (Color.YELLOW);
        dmeTxPaint.setStyle (Paint.Style.FILL);
        dmeTxPaint.setStrokeWidth (3);
        dmeTxPaint.setTextSize (wairToNow.textSize * 1.125F);
        dmeTxPaint.setTextAlign (Paint.Align.LEFT);
        dmeTxPaint.setTypeface (Typeface.MONOSPACE);

        float[] charWidths = new float[1];
        Rect textBounds = new Rect ();
        dmeTxPaint.getTextBounds ("M", 0, 1, textBounds);
        dmeTxPaint.getTextWidths ("M", charWidths);
        dmeCharWidth  = charWidths[0];
        dmeTextAscent = textBounds.height ();
        dmeTextHeight = dmeTxPaint.getTextSize ();

        FillDMECheckboxes ();
    }

    /**
     * Add the given waypoint as having DME enabled on the display.
     */
    public void showDMEBox (Waypoint dmewp)
    {
        if (! dmeCheckboxeses.containsKey (dmewp.ident)) {
            DMECheckboxes dmecb = new DMECheckboxes (dmewp);
            dmecb.setChecked (DMECB_DIST);
            dmeCheckboxeses.put (dmewp.ident, dmecb);
            dmeShowing = true;
            plateView.invalidate ();
        }
    }

    /**
     * Mouse pressed on the plate somewhere.
     */
    public void GotMouseDown (double x, double y)
    {
        if (dmeButtonBounds.contains ((float) x, (float) y)) {
            dmeButtonDownAt = SystemClock.uptimeMillis ();
            WairToNow.wtnHandler.runDelayed (500, new Runnable () {
                @Override
                public void run ()
                {
                    if (SystemClock.uptimeMillis () - 500 >= dmeButtonDownAt) {
                        DMEButtonClicked (false);
                    }
                }
            });
        }
    }

    /**
     * Mouse released on the plate.
     */
    public void GotMouseUp ()
    {
        DMEButtonClicked (true);
    }

    /**
     * Read local database to see what DMEs the user has enabled in the past.
     */
    private void FillDMECheckboxes ()
    {
        dmeCheckboxeses.clear ();
        String dbname = "dmecheckboxes.db";
        try {
            SQLiteDBs sqldb = SQLiteDBs.open (dbname);
            if (sqldb != null) {
                if (sqldb.tableExists ("dmecheckboxes")) {
                    Cursor result = sqldb.query (
                            "dmecheckboxes", columns_dc_dmeid_dc_checked,
                            "dc_icaoid=? AND dc_plate=?", new String[] { icaoid, plateid },
                            null, null, null, null);
                    try {
                        if (result.moveToFirst ()) {
                            do {
                                String id = result.getString (0);
                                Waypoint wp = plateView.FindWaypoint (id);
                                if (wp != null) {
                                    int checked = result.getInt (1);
                                    dmeShowing |= checked != 0;
                                    DMECheckboxes dmecb = new DMECheckboxes (wp);
                                    dmecb.setChecked (checked);
                                    dmeCheckboxeses.put (wp.ident, dmecb);
                                }
                            } while (result.moveToNext ());
                        }
                    } finally {
                        result.close ();
                    }
                }
            }
        } catch (Exception e) {
            Log.e (TAG, "error reading " + dbname, e);
        }
    }

    /**
     * Draw DME strings in lower left corner.
     * If no DME strings enabled, draw a little button to open menu.
     */
    public void DrawDMEs (Canvas canvas, double gpslat, double gpslon, double gpsalt, long gpstime, double gpsmv)
    {
        int canvasHeight = plateView.getHeight ();

        // see if any checkboxes checked (or maybe whole thing is disabled with little triangle button)
        int allChecked = 0;
        int numidchars = 5;
        int numnumchrs = 0;
        int dmex = (int) Math.ceil (dmeTextHeight / 2);
        int dmey = canvasHeight - dmex;
        if (dmeShowing) {
            dmeButtonBounds.bottom = dmey;
            for (String dmeIdent : dmeCheckboxeses.keySet ()) {
                DMECheckboxes dmecb = dmeCheckboxeses.nnget (dmeIdent);
                int checked = dmecb.getChecked ();
                if (checked != 0) {
                    int nchars = dmeIdent.length ();
                    if (numidchars < nchars) numidchars = nchars;
                    nchars  = 0;
                    nchars += ((checked / DMECB_DIST) & 1) * 5;
                    nchars += ((checked / DMECB_TIME) & 1) * 6;
                    nchars += ((checked / DMECB_RADL) & 1) * 5;
                    if (numnumchrs < nchars) numnumchrs = nchars;
                    allChecked |= checked;
                    dmeButtonBounds.top = (float) (dmey - dmeTextAscent);
                    dmey -= Math.ceil (dmeTextHeight);
                }
            }
            dmeButtonBounds.left  = dmex;
            dmeButtonBounds.right = dmex + (int) Math.ceil (dmeCharWidth * (numidchars + numnumchrs));
        }

        if (allChecked == 0) {

            // user doesn't want any DMEs shown, draw a little button instead
            float ts = wairToNow.textSize;
            dmeButtonBounds.left   = 0;
            dmeButtonBounds.right  = Math.round (ts * 4);
            dmeButtonBounds.top    = Math.round (canvasHeight - ts * 4);
            dmeButtonBounds.bottom = canvasHeight;

            dmeButtonPath.rewind ();
            dmeButtonPath.moveTo (ts, canvasHeight - ts * 2);
            dmeButtonPath.lineTo (ts * 2, canvasHeight - ts * 2);
            dmeButtonPath.lineTo (ts * 2, canvasHeight - ts);

            canvas.drawPath (dmeButtonPath, dmeBGPaint);
            canvas.drawPath (dmeButtonPath, dmeTxPaint);
        } else {

            // draw black background rectangle where text goes
            canvas.drawRect (dmeButtonBounds, dmeBGPaint);

            // draw DME strings
            int[] slants = new int[6];
            for (String dmeIdent : dmeCheckboxeses.keySet ()) {
                DMECheckboxes dmecb = dmeCheckboxeses.nnget (dmeIdent);
                int checked = dmecb.getChecked ();
                if (checked != 0) {
                    Waypoint wp = dmecb.waypoint;
                    StringBuilder sb = new StringBuilder (numidchars + numnumchrs);
                    sb.append (dmeIdent);
                    while (sb.length () < numidchars) sb.append (' ');
                    int nslants = 0;

                    // distance (in nautical miles) to DME station
                    double dmeelev = wp.GetDMEElev ();
                    double dmelat  = wp.GetDMELat  ();
                    double dmelon  = wp.GetDMELon  ();
                    double distBin = Lib.LatLonDist (gpslat, gpslon, dmelat, dmelon);
                    boolean dmeSlantRange = false;
                    if (dmeelev != Waypoint.ELEV_UNKNOWN) {
                        distBin = Math.hypot (distBin, gpsalt / Lib.MPerNM - dmeelev / Lib.FtPerNM);
                        dmeSlantRange = true;
                    }
                    if ((checked & DMECB_DIST) != 0) {
                        if (dmeSlantRange) slants[nslants++] = sb.length ();
                        int dist10Bin = (int) Math.round (distBin * 10);
                        if (dist10Bin > 9999) {
                            sb.append (" --.-");
                        } else {
                            if (dist10Bin < 1000) sb.append (' ');
                            if (dist10Bin <  100) sb.append (' ');
                            sb.append (dist10Bin / 10);
                            sb.append ('.');
                            sb.append (dist10Bin % 10);
                        }
                        if (dmeSlantRange) slants[nslants++] = sb.length ();
                    }

                    // compute nautical miles per second we are closing in on the station
                    // ...like a real DME box would
                    if (dmecb.dmeLastTime != 0) {
                        long dtms = gpstime - dmecb.dmeLastTime;
                        if (dtms > 0) dmecb.dmeLastSpeed = (dmecb.dmeLastDist - distBin) * 1000.0 / dtms;
                    } else {
                        dmecb.dmeLastSpeed = 0.0;
                    }

                    // save the latest position
                    dmecb.dmeLastDist = distBin;
                    dmecb.dmeLastTime = gpstime;

                    // see if user wants time-to-station displayed
                    if ((checked & DMECB_TIME) != 0) {
                        if (dmeSlantRange) slants[nslants++] = sb.length ();
                        int len1 = sb.length ();

                        // make sure heading is valid and that the divide below won't overflow
                        if (distBin <= Math.abs (dmecb.dmeLastSpeed) * 60000.0) {

                            // how many seconds TO the DME antenna
                            // negative means it is behind us
                            int seconds = (int) Math.round (distBin / dmecb.dmeLastSpeed);

                            // we do from -99:59 to 99:59
                            if ((seconds > -6000) && (seconds < 6000)) {
                                if (seconds < 0) {
                                    sb.append ('-');
                                    seconds = - seconds;
                                }
                                sb.append (seconds / 60);
                                sb.append (':');
                                int len2 = sb.length ();
                                sb.append (seconds % 60);
                                while (sb.length () - len2 < 2) sb.insert (len2, '0');
                            }
                        }
                        if (sb.length () == len1) sb.append ("--:--");
                        while (sb.length () - len1 < 6) sb.insert (len1, ' ');
                        if (dmeSlantRange) slants[nslants++] = sb.length ();
                    }

                    // get what radial we are on from the navaid (should match what's on an IAP plate)
                    // if waypoint is a vor, use what a real vor receiver would show
                    // if not, use what a compass in the aircraft would show
                    if ((checked & DMECB_RADL) != 0) {
                        boolean radSlantRange = wp.isAVOR ();
                        double hdgDeg = radSlantRange ?
                                Lib.LatLonTC (wp.lat, wp.lon, gpslat, gpslon) + wp.magvar :
                                Lib.LatLonTC (gpslat, gpslon, wp.lat, wp.lon) + 180.0 + gpsmv;
                        int hdgMag = (int) (Math.round (hdgDeg) + 359) % 360 + 1;
                        if (radSlantRange) slants[nslants++] = sb.length ();
                        sb.append (' ');
                        sb.append ((char) ('0' + hdgMag / 100));
                        sb.append ((char) ('0' + hdgMag / 10 % 10));
                        sb.append ((char) ('0' + hdgMag % 10));
                        sb.append ((char) 0x00B0);
                        if (radSlantRange) slants[nslants++] = sb.length ();
                    }

                    // display resultant string
                    dmey += Math.ceil (dmeTextHeight);
                    int j = 0;
                    float x = dmex;
                    for (int i = 0; i < nslants;) {
                        int slantBeg = slants[i++];
                        int slantEnd = slants[i++];
                        while ((i < nslants) && (slants[i] == slantEnd)) {
                            i ++;
                            slantEnd = slants[i++];
                        }
                        if (j < slantBeg) {
                            canvas.drawText (sb, j, slantBeg, x, dmey, dmeTxPaint);
                            x += dmeTxPaint.measureText (sb, j, slantBeg);
                        }
                        dmeTxPaint.setTextSkewX (-0.25F);
                        canvas.drawText (sb, slantBeg, slantEnd, x, dmey, dmeTxPaint);
                        x += dmeTxPaint.measureText (sb, slantBeg, slantEnd);
                        dmeTxPaint.setTextSkewX (0.0F);
                        j  = slantEnd;
                    }
                    if (j < sb.length ()) {
                        canvas.drawText (sb, j, sb.length (), x, dmey, dmeTxPaint);
                    }
                }
            }
        }
    }

    /**
     * Lower left corner of plate view was clicked and so we bring up the DME selection alert.
     */
    private void DMEButtonClicked (boolean mouseUp)
    {
        if (dmeButtonDownAt == Long.MAX_VALUE) return;
        dmeButtonDownAt = Long.MAX_VALUE;

        // if there are some dme fixes enabled, short click will show/hide them
        if (mouseUp) {
            if (dmeShowing) {
                dmeShowing = false;
                plateView.invalidate ();
                return;
            }
            for (DMECheckboxes dmecb : dmeCheckboxeses.values ()) {
                if (dmecb.getChecked () != 0) {
                    dmeShowing = true;
                    plateView.invalidate ();
                    return;
                }
            }
        }

        // long click or short click with nothing enabled brings up menu

        // build list of existing DME waypoints
        LinearLayout llv = new LinearLayout (wairToNow);
        llv.setOrientation (LinearLayout.VERTICAL);
        for (DMECheckboxes dmecb : dmeCheckboxeses.values ()) {
            dmecb.dmeWasChecked = dmecb.getChecked ();
            ViewParent parent = dmecb.getParent ();
            if (parent != null) ((ViewGroup) parent).removeAllViews ();
            llv.addView (dmecb);
        }

        // add an entry box so user can select some other waypoint
        final DMECheckboxes dmeTextEntry = new DMECheckboxes (null);
        llv.addView (dmeTextEntry);

        // put it in a dialog box
        ScrollView sv = new ScrollView (wairToNow);
        sv.addView (llv);
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("- D - T - R - Waypoint");
        adb.setView (sv);

        // if user clicks OK, save changes to database
        adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                dmeShowing = false;

                String dbname = "dmecheckboxes.db";
                try {
                    SQLiteDBs sqldb = SQLiteDBs.create (dbname);
                    if (!sqldb.tableExists ("dmecheckboxes")) {
                        sqldb.execSQL ("DROP INDEX IF EXISTS dmecbs_idplate");
                        sqldb.execSQL ("DROP INDEX IF EXISTS dmecbs_idplateid");
                        sqldb.execSQL ("CREATE TABLE dmecheckboxes (dc_icaoid TEXT NOT NULL, dc_plate TEXT NOT NULL, dc_dmeid TEXT NOT NULL, dc_checked INTEGER NOT NULL)");
                        sqldb.execSQL ("CREATE INDEX dmecbs_idplate ON dmecheckboxes (dc_icaoid,dc_plate)");
                        sqldb.execSQL ("CREATE UNIQUE INDEX dmecbs_idplateid ON dmecheckboxes (dc_icaoid,dc_plate,dc_dmeid)");
                    }

                    // if a new ident was typed in the box, add it to the checkbox list as checked
                    String ident = dmeTextEntry.identtv.getText ().toString ().replace (" ", "").toUpperCase (Locale.US);
                    if (!ident.equals ("")) {
                        Waypoint wp = plateView.FindWaypoint (ident);
                        if (wp != null) {
                            DMECheckboxes dmecb = new DMECheckboxes (wp);
                            int checked = dmeTextEntry.getChecked ();
                            if (checked == 0) checked = DMECB_DIST;
                            dmecb.setChecked (checked);
                            dmeCheckboxeses.put (wp.ident, dmecb);

                            ContentValues values = new ContentValues (4);
                            values.put ("dc_icaoid", icaoid);
                            values.put ("dc_plate", plateid);
                            values.put ("dc_dmeid", wp.ident);
                            values.put ("dc_checked", DMECB_DIST);
                            sqldb.insertWithOnConflict ("dmecheckboxes", values, SQLiteDatabase.CONFLICT_REPLACE);

                            dmeShowing = true;
                        }
                    }

                    // save the checkbox state of the existing checkboxes to the database
                    // also enable showing DMEs if any are enabled
                    for (DMECheckboxes dmecb : dmeCheckboxeses.values ()) {
                        Waypoint wp = dmecb.waypoint;
                        dmecb.dmeLastTime = 0;
                        int checked = dmecb.getChecked ();
                        dmeShowing |= checked != 0;
                        if (checked != dmecb.dmeWasChecked) {
                            ContentValues values = new ContentValues (1);
                            values.put ("dc_checked", checked);
                            String[] whargs = new String[] { icaoid, plateid, wp.ident };
                            sqldb.update ("dmecheckboxes", values, "dc_icaoid=? AND dc_plate=? AND dc_dmeid=?", whargs);
                        }
                    }
                } catch (Exception e) {
                    Log.e (TAG, "error writing " + dbname, e);
                }

                // update display
                plateView.invalidate ();
            }
        });

        // if user clicks Cancel, reload button states and dismiss dialog
        adb.setNegativeButton ("Cancel", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                for (DMECheckboxes dmecb : dmeCheckboxeses.values ()) {
                    dmecb.dmeLastTime = 0;
                    dmecb.setChecked (dmecb.dmeWasChecked);
                }
            }
        });

        // display dialog box
        adb.show ();
    }

    /**
     * A row of checkboxes and ident text for DME display.
     */
    private class DMECheckboxes extends LinearLayout {
        public CheckBox cbdist;     // distance enabling checkbox
        public CheckBox cbtime;     // time enabling checkbox
        public CheckBox cbradl;     // radial enabling checkbox
        public TextView identtv;    // waypoint ident
        public Waypoint waypoint;   // corresponding waypoint or null for input box

        public double dmeLastDist;
        public double dmeLastSpeed;
        public int    dmeWasChecked;
        public long   dmeLastTime;

        public DMECheckboxes (Waypoint wp)
        {
            super (wairToNow);

            waypoint = wp;

            cbdist = new CheckBox (wairToNow);
            cbtime = new CheckBox (wairToNow);
            cbradl = new CheckBox (wairToNow);

            if (waypoint != null) {
                identtv = new TextView (wairToNow);
                wairToNow.SetTextSize (identtv);
                identtv.setText (waypoint.ident);
                identtv.setTextColor (Color.WHITE);
            } else {
                EditText dmeTextEntry = new EditText (wairToNow);
                dmeTextEntry.setEms (5);
                dmeTextEntry.setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                dmeTextEntry.setSingleLine ();
                identtv = dmeTextEntry;
            }

            setOrientation (HORIZONTAL);
            addView (cbdist);
            addView (cbtime);
            addView (cbradl);
            addView (identtv);
        }

        public int getChecked ()
        {
            int checked = 0;
            if (cbdist.isChecked ()) checked |= DMECB_DIST;
            if (cbtime.isChecked ()) checked |= DMECB_TIME;
            if (cbradl.isChecked ()) checked |= DMECB_RADL;
            return checked;
        }

        public void setChecked (int checked)
        {
            cbdist.setChecked ((checked & DMECB_DIST) != 0);
            cbtime.setChecked ((checked & DMECB_TIME) != 0);
            cbradl.setChecked ((checked & DMECB_RADL) != 0);
        }
    }
}
