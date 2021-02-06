//    Copyright (C) 2021, Mike Rieker, Beverly, MA USA
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
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.util.JsonReader;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Handle the entry/editing of manual georeferencing on IAP plates.
 */
public class ManualGeoRef {
    public final static String TAG = "WairToNow";

    private final static int DKGREEN = Color.rgb (0, 170, 0);

    private WairToNow wairToNow;

    private EditText manualcontribusername;
    private EditText manualcontribpassword;
    private EditText manualcontribemailaddr;
    private DetentHorizontalScrollView geoRefInputScroll;
    private LinearLayout manualcontriblayout;

    private boolean showIAPEditButtons;
    private double crosshairBMX;                    // where georef crosshairs are in bitmap co-ords
    private double crosshairBMY;
    private GeoRefInput iapGeoRefInput;
    private IAPRealPlateImage iapRealPlateImage;
    private IExactMapper manualmapper;
    private LinearLayout displayLayout;
    private LinkedList<Contribution> contributions;
    private LinkedList<GeoRefPoint> geoRefPoints = new LinkedList<> ();
    private Paint crosshairPaint = new Paint ();
    private Paint editButtonPaint = new Paint ();
    private Path editButtonPath = new Path ();
    private RectF editButtonBounds = new RectF ();
    private TextView mappingStatusView;

    private Paint georefpointpaint = new Paint ();
    private Paint georefrectpaint = new Paint ();
    private Path georefpointmarker = new Path ();

    public ManualGeoRef (IAPRealPlateImage rpi)
    {
        iapRealPlateImage = rpi;
        wairToNow = rpi.wairToNow;

        crosshairPaint.setColor (Color.MAGENTA);
        crosshairPaint.setStrokeWidth (3.0F);
        CornerPathEffect pe = new CornerPathEffect (50.0F);
        editButtonPaint.setPathEffect (pe);
        editButtonPaint.setStrokeWidth (3.0F);
        georefpointpaint.setColor (Color.RED);
        georefpointpaint.setTextSize (wairToNow.textSize);

        geoRefInputScroll = new DetentHorizontalScrollView (wairToNow);
        wairToNow.SetDetentSize (geoRefInputScroll);
        geoRefInputScroll.setLayoutParams (new LinearLayout.LayoutParams (ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        iapGeoRefInput = new GeoRefInput ();
        geoRefInputScroll.addView (iapGeoRefInput);

        mappingStatusView = new TextView (wairToNow);
        wairToNow.SetTextSize (mappingStatusView);

        displayLayout = new LinearLayout (wairToNow);
        displayLayout.setOrientation (LinearLayout.VERTICAL);
        displayLayout.addView (geoRefInputScroll);
        displayLayout.addView (mappingStatusView);

        readGeoRefDatabase ();
    }

    /**
     * Draw triangle-shaped edit button at bottom of area on canvas where the plate is drawn.
     * Also draw manual georef cursor lines and reference points if editor open.
     */
    public void DrawEditButton (Canvas canvas, boolean hasdbasemapper)
    {
        int width  = iapRealPlateImage.getWidth  ();
        int height = iapRealPlateImage.getHeight ();

        // box where the button goes
        float ts = wairToNow.textSize;
        float xc = width / 2.0F;
        float xl = xc - ts * 2.0F;
        float xr = xc + ts * 2.0F;
        float yb = height - ts;
        float yt = height - ts * 2.0F;

        // so we know where clicking on it is
        editButtonBounds.left   = xl;
        editButtonBounds.right  = xr;
        editButtonBounds.top    = yt;
        editButtonBounds.bottom = yb;

        // draw triangle button
        // - outline if closed; solid if open
        editButtonPath.rewind ();
        if (showIAPEditButtons) {
            editButtonPaint.setStyle (Paint.Style.FILL_AND_STROKE);

            editButtonPath.moveTo (xc, yb);
            editButtonPath.lineTo (xl, yt);
            editButtonPath.lineTo (xr, yt);
            editButtonPath.lineTo (xc, yb);

            // also draw crosshairs and existing georef points if open
            float crosshairCanvasX = (float) iapRealPlateImage.BitmapX2CanvasX (crosshairBMX);
            float crosshairCanvasY = (float) iapRealPlateImage.BitmapY2CanvasY (crosshairBMY);
            canvas.drawLine (crosshairCanvasX, 0, crosshairCanvasX, height, crosshairPaint);
            canvas.drawLine (0, crosshairCanvasY, width, crosshairCanvasY, crosshairPaint);

            for (GeoRefPoint grp : geoRefPoints) {
                grp.draw (canvas);
            }

            iapRealPlateImage.drawNavaidPoints (canvas);
        } else {

            // closed, just draw button outline
            editButtonPaint.setStyle (Paint.Style.STROKE);
            editButtonPath.moveTo (xc, yt);
            editButtonPath.lineTo (xl, yb);
            editButtonPath.lineTo (xr, yb);
            editButtonPath.lineTo (xc, yt);
            editButtonPath.moveTo (xc, yt);
            editButtonPath.lineTo (xr, yb);
            editButtonPath.lineTo (xl, yb);
            editButtonPath.lineTo (xc, yt);
        }

        // draw button:
        //  GREEN = manual mapping valid
        //  GRAY  = database mapping present
        //  RED   = not mapped
        editButtonPaint.setColor (
                (manualmapper != null) ? DKGREEN :
                        hasdbasemapper ? Color.GRAY :
                                Color.RED);
        canvas.drawPath (editButtonPath, editButtonPaint);

        georefrectpaint.setColor (Color.WHITE);
        georefrectpaint.setStyle (Paint.Style.FILL_AND_STROKE);
    }

    /**
     * Toggle showing/hiding IAP georef editing toolbar.
     *  Input:
     *   x,y = mouse click on plate canvas
     *  Output:
     *   hides/shows cursors, edit buttons and contributed mappings
     */
    public void maybeShowHideEditToolbar (double x, double y)
    {
        if (editButtonBounds.contains ((float) x, (float) y)) {

            // show/hide toolbar itself
            showIAPEditButtons = ! showIAPEditButtons;

            wairToNow.iapGeoRefInputRow = null;

            // initially position crosshairs in center of screen
            if (showIAPEditButtons) {
                crosshairBMX = iapRealPlateImage.CanvasX2BitmapX (iapRealPlateImage.getWidth  () / 2.0);
                crosshairBMY = iapRealPlateImage.CanvasY2BitmapY (iapRealPlateImage.getHeight () / 2.0);

                wairToNow.iapGeoRefInputRow = displayLayout;

                maybePromptManualContrib ();

                new TryDownloadContributions ().start ();
            }

            wairToNow.SetCurrentView ();
            iapRealPlateImage.invalidate ();
        }
    }

    /**
     * If this is the first time the user has opened any plate for editing,
     * prompt if they want to send contributions to the server.
     */
    @SuppressLint("SetTextI18n")
    private void maybePromptManualContrib ()
    {
        // existence of the register table means they have been prompted before
        final SQLiteDBs sqldb = SQLiteDBs.create ("manualiapgeorefs.db");
        if (sqldb.tableExists ("register")) return;

        // create input boxes
        if (manualcontriblayout == null) {
            manualcontribusername  = new EditText (wairToNow);
            manualcontribpassword  = new EditText (wairToNow);
            manualcontribemailaddr = new EditText (wairToNow);

            manualcontribusername.setSingleLine ();
            manualcontribpassword.setSingleLine ();
            manualcontribemailaddr.setSingleLine ();

            manualcontribusername.setInputType (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            manualcontribpassword.setInputType (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            manualcontribemailaddr.setInputType (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

            TextView tv1 = new TextView (wairToNow);
            tv1.setText ("You can upload your georefs to the WairToNow server so others can " +
                    "benefit.  The username you enter here will be published with your " +
                    "contributions but the email address will not.  The email address will only " +
                    "be used for very low traffic notifications of contributions, password reset, " +
                    "never for spam.  You can change these settings on the Options page later if " +
                    "you want.  Internet connectivity is required for this to work.  Click 'Maybe " +
                    "Later' if not connected to the Internet, then use Options page later when " +
                    "connected to the Internet.");
            TextView tv2 = new TextView (wairToNow);
            tv2.setText ("Username (3 to 16 characters, letters, digits, period, underscore):");
            TextView tv3 = new TextView (wairToNow);
            tv3.setText ("Password:");
            TextView tv4 = new TextView (wairToNow);
            tv4.setText ("Email Address:");

            LinearLayout ll = new LinearLayout (wairToNow);
            ll.setOrientation (LinearLayout.VERTICAL);
            ll.addView (tv1);
            ll.addView (tv2);
            ll.addView (manualcontribusername);
            ll.addView (tv3);
            ll.addView (manualcontribpassword);
            ll.addView (tv4);
            ll.addView (manualcontribemailaddr);
            manualcontriblayout = ll;
        } else {
            ViewParent p = manualcontriblayout.getParent ();
            if (p instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) p;
                g.removeAllViews ();
            }
        }

        // display dialog box
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("Manual GeoRef Contribution");
        adb.setView (manualcontriblayout);

        adb.setPositiveButton ("Contribute", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                String un = manualcontribusername.getText ().toString ().trim ();
                String pw = manualcontribpassword.getText ().toString ().trim ();
                String em = manualcontribemailaddr.getText ().toString ().trim ();
                if (un.equals ("") || pw.equals ("") || em.equals ("")) {
                    maybePromptManualContrib ();
                } else {
                    RegisterContributionUser rcu = new RegisterContributionUser (wairToNow) {
                        @Override
                        public void reprompt ()
                        {
                            maybePromptManualContrib ();
                        }
                        @Override
                        public void success ()
                        { }
                    };
                    rcu.username  = un;
                    rcu.password  = pw;
                    rcu.emailaddr = em;
                    rcu.start ();
                }
            }
        });

        adb.setNegativeButton ("Maybe Later", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                RegisterContributionUser.createRegisterTable (sqldb, "", "", "");
            }
        });

        adb.show ();
    }

    /**
     * One mapping downloaded from the server (contributed by this or another user).
     */
    private class Contribution extends CheckBox implements View.OnClickListener {
        public int effdate;      // 20160923
        private LinkedList<GeoRefPoint> mapping;  // decoded mapping

        // un = user who uploaded the mapping
        // en = date that user uploaded the mapping
        // ef = plate effective date
        // mp = json-formatted mapping points
        @SuppressLint("SetTextI18n")
        public Contribution (String un, int en, int ef, String mp)
                throws Exception
        {
            super (wairToNow);

            // plate effective date yyyymmdd
            // serves as a revision id
            effdate = ef;

            // decode the mapping to a list of georef points
            mapping = new LinkedList<> ();
            JSONArray arr = new JSONArray (mp);
            for (int i = 0; i < arr.length (); i ++) {
                JSONObject obj = arr.getJSONObject (i);
                GeoRefPoint grp = new GeoRefPoint ();
                grp.lat = obj.has ("lat") ? obj.getDouble ("lat") : Double.NaN;
                grp.lon = obj.has ("lon") ? obj.getDouble ("lon") : Double.NaN;
                grp.bitmapx = obj.getInt ("bmx");
                grp.bitmapy = obj.getInt ("bmy");
                mapping.add (grp);
            }

            // text to display next to checkmark
            setText (" entered: " + en + " by: " + un);
            wairToNow.SetTextSize (this);

            // listen for checkmark being checked/unchecked
            setOnClickListener (this);
        }

        // checkmark was clicked to select/deselect this contributed mapping
        @Override
        public void onClick (View v)
        {
            if (isChecked ()) {

                // checked on, uncheck all other checkboxes
                ViewParent p = getParent ();
                if (p instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) p;
                    for (int i = 0; i < g.getChildCount (); i ++) {
                        View c = g.getChildAt (i);
                        if (c instanceof Contribution) {
                            Contribution cont = (Contribution) c;
                            if (c != this) cont.setChecked (false);
                        }
                    }
                }

                // select this contributed mapping
                // ...by pretending we entered these points by hand
                geoRefPoints.clear ();
                geoRefPoints.addAll (mapping);
                // write to maniapgeorefs table just like manually entered points
                writeGeoRefDatabase ();
                // generate corresponding mapping projection
                tryManualMapping ();
                // draw the mapping points on the plate just as we do when entered by hand
                iapRealPlateImage.invalidate ();
            }
        }

        // see if this contribution is being displayed on the plate
        public boolean isBeingDisplayed ()
        {
            if (mapping.size () != geoRefPoints.size ()) return false;
            for (GeoRefPoint contpoint : mapping) {
                boolean matched = false;
                for (GeoRefPoint disppoint : geoRefPoints) {
                    matched = (disppoint.bitmapx == contpoint.bitmapx) &&
                            (disppoint.bitmapy == contpoint.bitmapy) &&
                            (Double.doubleToLongBits (disppoint.lat) == Double.doubleToLongBits (contpoint.lat)) &&
                            (Double.doubleToLongBits (disppoint.lon) == Double.doubleToLongBits (contpoint.lon));
                    if (matched) break;
                }
                if (! matched) return false;
            }
            return true;
        }
    }

    /**
     * Read contributions by others into local database.
     */
    private class TryDownloadContributions extends Thread {

        @Override
        public void run ()
        {
            SQLiteDBs sqldb = SQLiteDBs.create ("manualiapgeorefs.db");
            if (! sqldb.tableExists ("lastdownload")) {
                sqldb.execSQL ("CREATE TABLE lastdownload (lastdownload INTEGER NOT NULL)");
            }
            if (! sqldb.tableExists ("contributions")) {
                sqldb.execSQL ("CREATE TABLE contributions (co_icaoid TEXT NOT NULL, " +
                        "co_plateid TEXT NOT NULL, co_effdate INTEGER NOT NULL, " +
                        "co_username TEXT NOT NULL, co_entdate INTEGER NOT NULL, " +
                        "co_mapping TEXT NOT NULL, " +
                        "PRIMARY KEY (co_icaoid,co_plateid,co_effdate,co_username,co_entdate))");
                sqldb.execSQL ("CREATE INDEX contributions_plate ON contributions (co_icaoid,co_plateid,co_effdate)");
            }

            try {
                // get yyyymmdd of last time we downloaded
                int lastdownload = 0;
                Cursor result = sqldb.query ("lastdownload", new String[] { "lastdownload" }, null, null, null, null, null, null);
                if (result.moveToFirst ()) lastdownload = result.getInt (0);
                result.close ();

                // get yyyymmdd of this time we are downloading
                SimpleDateFormat sdf = new SimpleDateFormat ("yyyyMMdd", Locale.US);
                sdf.setTimeZone (TimeZone.getTimeZone ("UTC"));
                int thisdownload = Integer.parseInt (sdf.format (System.currentTimeMillis ()));

                // download all entered on or after the last yyyymmdd
                // save to local database
                URL url = new URL (MaintView.dldir + "/manualgeoref.php?func=download&since=" + lastdownload);
                HttpURLConnection httpcon = (HttpURLConnection) url.openConnection ();
                try {
                    int rc = httpcon.getResponseCode ();
                    if (rc != HttpURLConnection.HTTP_OK) throw new IOException ("http reply code " + rc);
                    JsonReader jr = new JsonReader (new InputStreamReader (httpcon.getInputStream ()));
                    jr.beginArray ();
                    while (jr.hasNext ()) {
                        int    effdate  = 0;
                        int    entdate  = 0;
                        String username = null;
                        String icaoid   = null;
                        String plateid  = null;
                        String mapping  = null;
                        jr.beginObject ();
                        while (jr.hasNext ()) {
                            switch (jr.nextName ()) {
                                case "username": username = jr.nextString (); break;
                                case "icaoid":   icaoid   = jr.nextString (); break;
                                case "plateid":  plateid  = jr.nextString (); break;
                                case "effdate":  effdate  = jr.nextInt    (); break;
                                case "mapping":  mapping  = jr.nextString (); break;
                                case "entdate":  entdate  = jr.nextInt    (); break;
                            }
                        }
                        jr.endObject ();
                        if ((username != null) && (icaoid != null) && (plateid != null) && (effdate != 0) && (entdate != 0) && (mapping != null)) {
                            ContentValues values = new ContentValues (6);
                            values.put ("co_username", username);
                            values.put ("co_icaoid",   icaoid);
                            values.put ("co_plateid",  plateid);
                            values.put ("co_effdate",  effdate);
                            values.put ("co_entdate",  entdate);
                            values.put ("co_mapping",  mapping);
                            sqldb.insertWithOnConflict ("contributions", values, SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                    jr.endArray ();
                } finally {
                    httpcon.disconnect ();
                }

                // don't need to download those entries again
                ContentValues values = new ContentValues (1);
                values.put ("lastdownload", thisdownload);
                if (lastdownload == 0) {
                    sqldb.insert ("lastdownload", values);
                } else {
                    sqldb.update ("lastdownload", values, null, null);
                }
            } catch (Exception e) {
                Log.w (TAG, "exception downloading manual iap georefs", e);
            }

            // now see what all contributions we have for this plate
            LinkedList<Contribution> conts = new LinkedList<> ();
            Cursor result = sqldb.query ("contributions",
                    new String[] { "co_username", "co_entdate", "co_effdate", "co_mapping" },
                    "co_icaoid=? AND co_plateid=?",
                    new String[] { iapRealPlateImage.airport.ident, iapRealPlateImage.plateid },
                    null, null, "co_effdate DESC,co_entdate", null);
            try {
                while (result.moveToNext ()) {
                    String username = result.getString (0);
                    int    entdate  = result.getInt (1);
                    int    effdate  = result.getInt (2);
                    String mapping  = result.getString (3);
                    try {
                        conts.add (new Contribution (username, entdate, effdate, mapping));
                    } catch (Exception e) {
                        Log.w (TAG, "exception decoding contribution", e);
                    }
                }
            } finally {
                result.close ();
            }
            contributions = conts;

            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    displayContributions ();
                }
            });

            SQLiteDBs.CloseAll ();
        }
    }

    /**
     * (Re-)Build display layout:
     *   first row: edit buttons
     *   rest rows: downloaded contributions
     */
    @SuppressLint("SetTextI18n")
    private void displayContributions ()
    {
        displayLayout.removeAllViews ();
        displayLayout.addView (geoRefInputScroll);
        displayLayout.addView (mappingStatusView);

        // contributions are sorted by descending effective date
        // the older effective date plate mappings might still apply
        // ...in case there is no recent mapping available
        // ...or may take minor adjustment then can be re-saved
        int lasteffdate = iapRealPlateImage.plateff;
        for (Contribution contribution : contributions) {
            if (lasteffdate != contribution.effdate) {
                lasteffdate = contribution.effdate;
                TextView tv = new TextView (wairToNow);
                wairToNow.SetTextSize (tv);
                tv.setText ("older plate effective " + lasteffdate);
                displayLayout.addView (tv);
            }
            displayLayout.addView (contribution);
        }

        updateContribCheckmarks ();
    }

    /**
     * One point manually entered on plate
     * Color:
     *   YELLOW : crosshairs on it so will be deleted if DELETE clicked
     *    GREEN : is being used to determine mapping
     *      RED : not being used to determine mapping
     */
    private class GeoRefPoint extends LatLon {
        public boolean touching;
        public boolean usedmap;
        public int bitmapx;
        public int bitmapy;

        // draw a triangle at the point and text indicating numeric latitude or longitude
        public void draw (Canvas canvas)
        {
            int canvasx = (int) Math.round (iapRealPlateImage.BitmapX2CanvasX (bitmapx));
            int canvasy = (int) Math.round (iapRealPlateImage.BitmapY2CanvasY (bitmapy));

            int xhaircx = (int) Math.round (iapRealPlateImage.BitmapX2CanvasX (crosshairBMX));
            int xhaircy = (int) Math.round (iapRealPlateImage.BitmapY2CanvasY (crosshairBMY));

            float ts = wairToNow.textSize;

            georefpointpaint.setColor (touching ? Color.YELLOW : usedmap ? DKGREEN : Color.RED);

            if (! Double.isNaN (lat)) {
                touching = (xhaircx > canvasx - ts) &&
                        (xhaircx < canvasx) &&
                        (xhaircy > canvasy - ts) &&
                        (xhaircy < canvasy + ts);

                georefpointmarker.reset ();
                georefpointmarker.moveTo (canvasx, canvasy);
                georefpointmarker.lineTo (canvasx - ts, canvasy - ts);
                georefpointmarker.lineTo (canvasx - ts, canvasy + ts);
                georefpointmarker.lineTo (canvasx, canvasy);
                canvas.drawPath (georefpointmarker, georefpointpaint);

                georefpointpaint.setTextAlign (Paint.Align.LEFT);
                CharSequence latstr = formatLLStr (lat, 'N', 'S');
                int strlen = latstr.length ();
                float tx = canvasx + ts * 0.5F;
                float ty = canvasy + ts * 0.5F;
                float tw = georefpointpaint.measureText (latstr, 0, strlen);
                float th = georefpointpaint.getTextSize ();
                canvas.drawRect (tx, ty - th, tx + tw, ty, georefrectpaint);
                canvas.drawText (latstr, 0, strlen, tx, ty - 3, georefpointpaint);
            }

            if (! Double.isNaN (lon)) {
                touching = (xhaircx > canvasx - ts) &&
                        (xhaircx < canvasx + ts) &&
                        (xhaircy > canvasy - ts) &&
                        (xhaircy < canvasy);

                georefpointmarker.reset ();
                georefpointmarker.moveTo (canvasx, canvasy);
                georefpointmarker.lineTo (canvasx - ts, canvasy - ts);
                georefpointmarker.lineTo (canvasx + ts, canvasy - ts);
                georefpointmarker.lineTo (canvasx, canvasy);
                canvas.drawPath (georefpointmarker, georefpointpaint);

                georefpointpaint.setTextAlign (Paint.Align.CENTER);
                CharSequence lonstr = formatLLStr (lon, 'E', 'W');
                int strlen = lonstr.length ();
                //noinspection UnnecessaryLocalVariable
                float tx = canvasx;
                float ty = canvasy + ts;
                float tw = georefpointpaint.measureText (lonstr, 0, strlen);
                float th = georefpointpaint.getTextSize ();
                canvas.drawRect (tx - tw / 2.0F, ty - th, tx + tw / 2.0F, ty, georefrectpaint);
                canvas.drawText (lonstr, 0, lonstr.length (), tx, ty - 3, georefpointpaint);
            }
        }

        private CharSequence formatLLStr (double ll, char pos, char neg)
        {
            int llhun = (int) Math.round (ll * 360000.0);
            if (llhun == 0) return "00\u00B000'";
            if (llhun < 0) {
                pos = neg;
                llhun = - llhun;
            }
            StringBuilder sb = new StringBuilder ();
            sb.append (pos);
            sb.append (' ');
            int lldeg = llhun / 360000;
            if (lldeg < 10) sb.append ('0');
            sb.append (lldeg);
            sb.append ('\u00B0');
            int llmin = llhun / 6000 % 60;
            if (llmin < 10) sb.append ('0');
            sb.append (llmin);
            sb.append ('\'');
            llhun %= 6000;
            if (llhun > 0) {
                int llsec = llhun / 100;
                if (llsec < 10) sb.append ('0');
                sb.append (llsec);
                llhun %= 100;
                if (llhun > 0) {
                    sb.append ('.');
                    if (llhun < 10) sb.append ('0');
                    sb.append (llhun);
                }
                sb.append ('"');
            }
            return sb;
        }
    }

    /**
     * IAP GeoRef editing toolbar.
     */
    private final static String[] secondsStrings = new String[] {
            "00\"=0.0' ", "06\"=0.1' ", "10\" ", "12\"=0.2' ", "18\"=0.3' ", "20\" ", "24\"=0.4' ",
            "30\"=0.5' ", "36\"=0.6' ", "40\" ", "42\"=0.7' ", "48\"=0.8' ", "50\" ", "54\"=0.9' " };

    private class GeoRefInput extends LinearLayout {
        private EditText minutesFreeFrm;
        private EditText secondsFreeFrm;
        private String[] degreesStrings;
        private TextArraySpinner degreesSpinner;
        private TextArraySpinner minutesSpinner;
        private TextArraySpinner secondsSpinner;

        @SuppressLint("SetTextI18n")
        public GeoRefInput ()
        {
            super (wairToNow);
            setOrientation (HORIZONTAL);

            // make arrow buttons for moving crosshairs around
            addView (new GeoRefArrow (new int[] { 4, 2, 2, 6, 6, 6, 4, 2 }, 0, -1));  // up
            addView (new GeoRefArrow (new int[] { 4, 6, 2, 2, 6, 2, 4, 6 }, 0,  1));  // down
            addView (new GeoRefArrow (new int[] { 2, 4, 6, 2, 6, 6, 2, 4 }, -1, 0));  // left
            addView (new GeoRefArrow (new int[] { 6, 4, 2, 2, 2, 6, 6, 4 },  1, 0));  // right

            // make spinners for entering lat/lon degrees/minutes
            degreesSpinner = new TextArraySpinner (wairToNow);
            minutesSpinner = new TextArraySpinner (wairToNow);
            secondsSpinner = new TextArraySpinner (wairToNow);
            minutesFreeFrm = new EditText (wairToNow);
            secondsFreeFrm = new EditText (wairToNow);

            minutesFreeFrm.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            secondsFreeFrm.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            minutesFreeFrm.setEms (5);
            secondsFreeFrm.setEms (5);
            minutesFreeFrm.setVisibility (GONE);
            secondsFreeFrm.setVisibility (GONE);

            wairToNow.SetTextSize (degreesSpinner);
            wairToNow.SetTextSize (minutesSpinner);
            wairToNow.SetTextSize (secondsSpinner);
            wairToNow.SetTextSize (minutesFreeFrm);
            wairToNow.SetTextSize (secondsFreeFrm);

            int midlat = (int) Math.floor (iapRealPlateImage.airport.lat);
            int midlon = (int) Math.floor (iapRealPlateImage.airport.lon);
            degreesStrings = new String[6];
            for (int i = 0; i < 3; i ++) {
                int latint = midlat - 1 + i;
                int lonint = midlon - 1 + i;
                degreesStrings[i]   = (latint < 0) ? ("S " + (~ latint)) : ("N " + latint);
                degreesStrings[i+3] = (lonint < 0) ? ("W " + (~ lonint)) : ("E " + lonint);
            }

            minutesSpinner.setNumCols (6);
            String[] minutesStrings = new String[60];
            for (int i = 0; i < 60; i ++) {
                minutesStrings[i] = Character.toString ((char) (i / 10 + '0')) +
                        ((char) (i % 10 + '0')) + ' ';
            }

            secondsSpinner.setNumCols (2);

            degreesSpinner.setLabels (degreesStrings, null, "deg", null);
            minutesSpinner.setLabels (minutesStrings, null, "min", "FreeForm");
            secondsSpinner.setLabels (secondsStrings, null, "sec", "FreeForm");

            degreesSpinner.setIndex (TextArraySpinner.NOTHING);
            minutesSpinner.setIndex (TextArraySpinner.NOTHING);
            secondsSpinner.setIndex (TextArraySpinner.NOTHING);

            minutesSpinner.setOnItemSelectedListener (new TextArraySpinner.OnItemSelectedListener () {
                public boolean onItemSelected (View view, int index) { return true; }
                public boolean onNegativeClicked (View view) { return false; }
                public boolean onPositiveClicked (View view)
                {
                    minutesSpinner.setVisibility (GONE);
                    secondsSpinner.setVisibility (GONE);
                    minutesFreeFrm.setVisibility (VISIBLE);
                    return true;
                }
            });

            secondsSpinner.setOnItemSelectedListener (new TextArraySpinner.OnItemSelectedListener () {
                public boolean onItemSelected (View view, int index) { return true; }
                public boolean onNegativeClicked (View view) { return false; }
                public boolean onPositiveClicked (View view)
                {
                    secondsSpinner.setVisibility (GONE);
                    secondsFreeFrm.setVisibility (VISIBLE);
                    return true;
                }
            });

            addView (degreesSpinner);
            addView (minutesSpinner);
            addView (minutesFreeFrm);
            addView (secondsSpinner);
            addView (secondsFreeFrm);

            // add SAVE, DELETE, CONTRIB buttons
            addView (new GeoRefSave    ());
            addView (new GeoRefDelete  ());
            addView (new GeoRefContrib ());

            Button helpbutton = new Button (wairToNow);
            wairToNow.SetTextSize (helpbutton);
            helpbutton.setText ("HELP");
            addView (helpbutton);
            helpbutton.setOnClickListener (new OnClickListener () {
                @Override
                public void onClick (View view)
                {
                    wairToNow.helpView.loadUrl ("file:///android_asset/eurogeoref.html");
                    wairToNow.SetCurrentTab (wairToNow.helpView);
                }
            });
        }

        // get degrees in deg/min/sec boxes
        public double getDegs ()
        {
            int degidx = degreesSpinner.getIndex ();
            if (degidx < 0) {
                degreesSpinner.requestFocus ();
                return Double.NaN;
            }
            double degrees = Integer.parseInt (degreesStrings[degidx].substring (2));

            if (minutesFreeFrm.getVisibility () == VISIBLE) {
                String minff = minutesFreeFrm.getText ().toString ().trim ();
                try {
                    return degrees + Double.parseDouble (minff) / 60.0;
                } catch (NumberFormatException nfe) {
                    minutesFreeFrm.requestFocus ();
                    return Double.NaN;
                }
            }
            int minidx = minutesSpinner.getIndex ();
            if (minidx < 0) {
                minutesSpinner.requestFocus ();
                return Double.NaN;
            }
            degrees += minidx / 60.0;

            if (secondsFreeFrm.getVisibility () == VISIBLE) {
                String secff = secondsFreeFrm.getText ().toString ().trim ();
                try {
                    return degrees + Double.parseDouble (secff) / 3600.0;
                } catch (NumberFormatException nfe) {
                    secondsFreeFrm.requestFocus ();
                    return Double.NaN;
                }
            }
            int secidx = secondsSpinner.getIndex ();
            if (secidx < 0) {
                secondsSpinner.requestFocus ();
                return Double.NaN;
            }
            degrees += Integer.parseInt (secondsStrings[secidx].substring (0, 2)) / 3600.0;

            return degrees;
        }

        // get hemisphere in degrees box
        //  returns 'N' 'S' 'E' 'W'
        public char getHemi ()
        {
            int degidx = degreesSpinner.getIndex ();
            if (degidx < 0) return 0;
            return degreesStrings[degidx].charAt (0);
        }
    }

    // arrow button to move magenta cursor lines around
    private class GeoRefArrow extends Button implements View.OnClickListener, View.OnLongClickListener {
        private int deltax, deltay;
        private int[] raw;
        private long downAt, longAt;
        private Path path;

        public GeoRefArrow (int[] logo, int dx, int dy)
        {
            super (wairToNow);
            raw    = logo;
            deltax = dx;
            deltay = dy;
            setOnClickListener (this);
            setOnLongClickListener (this);
            setText (" ");
            setTypeface (Typeface.MONOSPACE);
            wairToNow.SetTextSize (this);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override  // TextView
        public boolean onTouchEvent (@NonNull MotionEvent me)
        {
            switch (me.getActionMasked ()) {
                case MotionEvent.ACTION_DOWN: {
                    downAt = SystemClock.uptimeMillis ();
                    longAt = 0;
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    downAt = 0;
                    longAt = 0;
                    break;
                }
            }
            return super.onTouchEvent (me);
        }

        @Override  // OnClickListener
        public void onClick (View v)
        {
            Clicked (deltax, deltay);
        }

        @Override  // OnLongClickListener
        public boolean onLongClick (View v)
        {
            longAt = SystemClock.uptimeMillis ();
            LongClicked ();
            return true;
        }

        /**
         * Move the crosshairs by a large amount if button is still down.
         * Then start a timer to move them again by the long press interval.
         */
        public void LongClicked ()
        {
            if (longAt > downAt) {
                Clicked (deltax * 48, deltay * 48);
                WairToNow.wtnHandler.runDelayed (longAt - downAt, new Runnable () {
                    @Override
                    public void run ()
                    {
                        LongClicked ();
                    }
                });
            }
        }

        /**
         * Update crosshairs position by the given amount and re-draw plate image.
         */
        private void Clicked (int dx, int dy)
        {
            crosshairBMX += dx;
            crosshairBMY += dy;
            int w = iapRealPlateImage.fgbitmap.getWidth  () - 1;
            int h = iapRealPlateImage.fgbitmap.getHeight () - 1;
            if (crosshairBMX < 0) crosshairBMX = 0;
            if (crosshairBMY < 0) crosshairBMY = 0;
            if (crosshairBMX > w) crosshairBMX = w;
            if (crosshairBMY > h) crosshairBMY = h;
            iapRealPlateImage.invalidate ();
        }

        /**
         * Draw the arrow triangle after making sure the button is square.
         */
        @Override
        public void onDraw (@NonNull Canvas canvas)
        {
            super.onDraw (canvas);

            if (path == null) {
                int size = getHeight ();
                path = new Path ();
                for (int i = 0; i < raw.length;) {
                    float x = size * raw[i++] / 8.0F;
                    float y = size * raw[i++] / 8.0F;
                    if (i == 0) path.moveTo (x, y);
                    else path.lineTo (x, y);
                }
                setWidth (size);
            } else {
                canvas.drawPath (path, getPaint ());
            }
        }
    }

    /**
     * SAVE button clicked, add point to in-memory geoRefsPoints list and write to database.
     */
    private class GeoRefSave extends Button implements View.OnClickListener {
        @SuppressLint("SetTextI18n")
        public GeoRefSave ()
        {
            super (wairToNow);
            setOnClickListener (this);
            setText ("SAVE");
            wairToNow.SetTextSize (this);
        }

        @Override
        public void onClick (View v)
        {
            GeoRefPoint grp = new GeoRefPoint ();
            grp.bitmapx = (int) crosshairBMX;
            grp.bitmapy = (int) crosshairBMY;
            grp.lat = Double.NaN;
            grp.lon = Double.NaN;
            double degs = iapGeoRefInput.getDegs ();
            if (! Double.isNaN (degs)) {
                switch (iapGeoRefInput.getHemi ()) {
                    case 'N': grp.lat =   degs; break;
                    case 'S': grp.lat = - degs; break;
                    case 'E': grp.lon =   degs; break;
                    case 'W': grp.lon = - degs; break;
                }
                geoRefPoints.add (grp);
                writeGeoRefDatabase ();
                tryManualMapping ();
                iapRealPlateImage.invalidate ();
                updateContribCheckmarks ();
            }
        }
    }

    /**
     * DELETE button clicked, delete points the crosshairs are touching.
     */
    private class GeoRefDelete extends Button implements View.OnClickListener {
        @SuppressLint("SetTextI18n")
        public GeoRefDelete ()
        {
            super (wairToNow);
            setOnClickListener (this);
            setText ("DELETE");
            wairToNow.SetTextSize (this);
        }

        @Override
        public void onClick (View v)
        {
            for (Iterator<GeoRefPoint> it = geoRefPoints.iterator (); it.hasNext ();) {
                GeoRefPoint grp = it.next ();
                if (grp.touching) it.remove ();
            }
            writeGeoRefDatabase ();
            tryManualMapping ();
            iapRealPlateImage.invalidate ();
            updateContribCheckmarks ();
        }
    }

    // a point was manually entered or removed, see if it matches any contribution
    private void updateContribCheckmarks ()
    {
        int nchilds = displayLayout.getChildCount ();
        for (int i = 0; i < nchilds; i ++) {
            View view = displayLayout.getChildAt (i);
            if (view instanceof Contribution) {
                Contribution cont = (Contribution) view;
                cont.setChecked (cont.isBeingDisplayed ());
            }
        }
    }

    /**
     * CONTRIB button clicked, contribute the mapping to the server for others to use.
     */
    private class GeoRefContrib extends Button implements View.OnClickListener, Runnable {
        @SuppressLint("SetTextI18n")
        public GeoRefContrib ()
        {
            super (wairToNow);
            setOnClickListener (this);
            setText ("CONTRIB");
            wairToNow.SetTextSize (this);
        }

        @Override
        public void onClick (View v)
        {
            new Thread (this).start ();
        }

        @Override
        public void run ()
        {
            SQLiteDBs sqldb = SQLiteDBs.create ("manualiapgeorefs.db");
            try {
                Cursor result = sqldb.query ("register", new String[] { "mr_username", "mr_password"},
                        null, null, null, null, null, null);
                if (! result.moveToFirst ()) throw new Exception ("no registered username");
                String user = result.getString (0);
                String pass = result.getString (1);
                StringBuilder sb = new StringBuilder ();
                sb.append (MaintView.dldir);
                sb.append ("/manualgeoref.php?func=saventry&username=");
                sb.append (URLEncoder.encode (user));
                sb.append ("&password=");
                sb.append (URLEncoder.encode (pass));
                sb.append ("&icaoid=");
                sb.append (iapRealPlateImage.airport.ident);
                sb.append ("&plateid=");
                sb.append (URLEncoder.encode (iapRealPlateImage.plateid));
                sb.append ("&effdate=");
                sb.append (iapRealPlateImage.plateff);
                sb.append ("&mapping=[");
                boolean first = true;
                for (GeoRefPoint grp : geoRefPoints) {
                    if (grp.usedmap) {
                        if (!first) sb.append (',');
                        sb.append ('{');
                        String sep = "";
                        if (!Double.isNaN (grp.lat)) {
                            sb.append ("\"lat\":");
                            sb.append (grp.lat);
                            sep = ",";
                        }
                        if (!Double.isNaN (grp.lon)) {
                            sb.append (sep);
                            sb.append ("\"lon\":");
                            sb.append (grp.lon);
                            sep = ",";
                        }
                        sb.append (sep);
                        sb.append ("\"bmx\":");
                        sb.append (grp.bitmapx);
                        sb.append (",\"bmy\":");
                        sb.append (grp.bitmapy);
                        sb.append ('}');
                        first = false;
                    }
                }
                if (! first) {
                    sb.append (']');
                    URL url = new URL (sb.toString ());
                    HttpURLConnection httpcon = (HttpURLConnection) url.openConnection ();
                    try {
                        int rc = httpcon.getResponseCode ();
                        if (rc != HttpURLConnection.HTTP_OK) throw new IOException ("http response code " + rc);
                        BufferedReader br = new BufferedReader (new InputStreamReader (httpcon.getInputStream ()));
                        String line = br.readLine ();
                        if (line == null) line = "";
                        line = line.trim ();
                        if (line.equals ("OK")) {
                            //noinspection CallToThreadRun
                            new TryDownloadContributions ().run ();
                        } else {
                            if (line.equals ("")) line = "null server reply";
                            alertMessage (line);
                        }
                    } finally {
                        httpcon.disconnect ();
                    }
                }
            } catch (Exception e) {
                Log.w (TAG, "exception uploading mapping", e);
                alertMessage (e.getMessage ());
            }
            SQLiteDBs.CloseAll ();
        }

        private void alertMessage (final String msg)
        {
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                    adb.setTitle ("Error Contributing Mapping");
                    adb.setMessage (msg);
                    adb.setPositiveButton ("OK", null);
                    adb.show ();
                }
            });
        }
    }

    /**
     * Read manual georeference points from database into geoRefPoints.
     * These are our most recently manually entered points or most recently selected contribution points.
     */
    private void readGeoRefDatabase ()
    {
        geoRefPoints.clear ();
        SQLiteDBs sqldb = SQLiteDBs.open ("manualiapgeorefs.db");
        if ((sqldb != null) && sqldb.tableExists ("maniapgeorefs")) {
            Cursor result = sqldb.query ("maniapgeorefs",
                    new String[] { "mg_lat", "mg_lon", "mg_bmx", "mg_bmy" },
                    "mg_icaoid=? AND mg_plate=? AND mg_effdate=" + iapRealPlateImage.plateff,
                    new String[] { iapRealPlateImage.airport.getICAOID (), iapRealPlateImage.plateid },
                    null, null, null, null);
            try {
                while (result.moveToNext ()) {
                    GeoRefPoint grp = new GeoRefPoint ();
                    grp.lat = result.isNull (0) ? Double.NaN : result.getDouble (0);
                    grp.lon = result.isNull (1) ? Double.NaN : result.getDouble (1);
                    grp.bitmapx = result.getInt (2);
                    grp.bitmapy = result.getInt (3);
                    geoRefPoints.add (grp);
                }
            } finally {
                result.close ();
            }
        }
        tryManualMapping ();
    }

    /**
     * Write manual georeference points from geoRefPoints to database.
     */
    private void writeGeoRefDatabase ()
    {
        SQLiteDBs sqldb = SQLiteDBs.create ("manualiapgeorefs.db");
        sqldb.execSQL ("BEGIN");
        if (! sqldb.tableExists ("maniapgeorefs")) {
            sqldb.execSQL ("CREATE TABLE maniapgeorefs (mg_icaoid TEXT NOT NULL, mg_plate TEXT NOT NULL, " +
                    "mg_effdate INTEGER NOT NULL, mg_lat REAL, mg_lon REAL, mg_bmx INTEGER NOT NULL, mg_bmy INTEGER NOT NULL)");
            sqldb.execSQL ("CREATE INDEX maniapgeorefs_icaoid_plate ON maniapgeorefs (mg_icaoid,mg_plate,mg_effdate)");
        }
        String icaoid = iapRealPlateImage.airport.getICAOID ();
        sqldb.delete ("maniapgeorefs", "mg_icaoid=? AND mg_plate=?", new String[] { icaoid, iapRealPlateImage.plateid });
        for (GeoRefPoint grpt : geoRefPoints) {
            ContentValues values = new ContentValues (7);
            values.put ("mg_icaoid",  icaoid);
            values.put ("mg_plate",   iapRealPlateImage.plateid);
            values.put ("mg_effdate", iapRealPlateImage.plateff);
            values.put ("mg_lat", grpt.lat);
            values.put ("mg_lon", grpt.lon);
            values.put ("mg_bmx", grpt.bitmapx);
            values.put ("mg_bmy", grpt.bitmapy);
            sqldb.insert ("maniapgeorefs", values);
        }
        sqldb.execSQL ("COMMIT");
    }

    /**
     * See if geoRefPoints manual georef contains sufficient points to give a mapping.
     * If so, use that mapping.
     * If not, revert to FAA/ReadEuroIAPPng.cs mapping if any.
     */
    public void tryManualMapping ()
    {
        manualmapper = null;
        mappingStatusView.setText ("");
        if (! tryManualMapping8 ()) {
            tryManualMapping4 ();
        }
        iapRealPlateImage.setManualMapper (manualmapper);
    }

    private void setMappingStatus (CharSequence text)
    {
        CharSequence old = mappingStatusView.getText ();
        if (old.length () > 0) mappingStatusView.append ("\n");
        mappingStatusView.append (text);
    }

    // see if there are 8 points for mapping
    // two on north edge, two on south edge, two on east edge, two on west edge
    private boolean tryManualMapping8 ()
    {
        int npoints = geoRefPoints.size ();
        if (npoints < 8) return false;

        // separate into lat markers and lon markers
        GeoRefPoint[] latPoints = new GeoRefPoint[npoints];
        GeoRefPoint[] lonPoints = new GeoRefPoint[npoints];
        int nlats = 0;
        int nlons = 0;
        for (GeoRefPoint grp : geoRefPoints) {
            grp.usedmap = false;
            if (! Double.isNaN (grp.lat)) latPoints[nlats++] = grp;
            if (! Double.isNaN (grp.lon)) lonPoints[nlons++] = grp;
        }
        if ((nlats < 4) || (nlons < 4)) return false;

        // if there are more than 4, get the 4 that make the biggest polygon
        //TODO find4FarthestApart (latPoints, nlats);
        //TODO find4FarthestApart (lonPoints, nlons);

        // set up x,y -> lat,lon mapping (tfw's) using those points
        // also get bitmap box enclosing those 8 points
        int lox = 999999999;
        int hix = -99999999;
        int loy = 999999999;
        int hiy = -99999999;
        double[][] tfwmat = new double[8][];
        for (int i = 0; i < 4; i ++) {
            GeoRefPoint latpt = latPoints[i];
            GeoRefPoint lonpt = lonPoints[i];

            if (lox > latpt.bitmapx) lox = latpt.bitmapx;
            if (hix < latpt.bitmapx) hix = latpt.bitmapx;
            if (loy > latpt.bitmapy) loy = latpt.bitmapy;
            if (hiy < latpt.bitmapy) hiy = latpt.bitmapy;

            // tfw_a * x + tfw_c * y + tfw_e + tfw_g * x * y = lon
            tfwmat[i]   = new double[] { lonpt.bitmapx, 0, lonpt.bitmapy, 0, 1, 0, lonpt.bitmapx * lonpt.bitmapy, 0, lonpt.lon };

            // tfw_b * x + tfw_d * y + tfw_f + tfw_h * x * y = lat
            tfwmat[i+4] = new double[] { 0, latpt.bitmapx, 0, latpt.bitmapy, 0, 1, 0, latpt.bitmapx * latpt.bitmapy, latpt.lat };
        }

        try {
            Lib.RowReduce (tfwmat);
        } catch (ArithmeticException ae) {
            setMappingStatus ("8pt: redundant points");
            return false;
        }

        BiLinProj proj = new BiLinProj ();

        proj.tfw_a = tfwmat[0][8];
        proj.tfw_b = tfwmat[1][8];
        proj.tfw_c = tfwmat[2][8];
        proj.tfw_d = tfwmat[3][8];
        proj.tfw_e = tfwmat[4][8];
        proj.tfw_f = tfwmat[5][8];
        proj.tfw_g = tfwmat[6][8];
        proj.tfw_h = tfwmat[7][8];

        // get points (x, y, lat, lon) of 4 corners of the bitmap box
        // fill in tfwmat and wftmat with those mappings
        GeoRefPoint topleft = new GeoRefPoint ();
        GeoRefPoint toprite = new GeoRefPoint ();
        GeoRefPoint botleft = new GeoRefPoint ();
        GeoRefPoint botrite = new GeoRefPoint ();

        topleft.bitmapx = botleft.bitmapx = lox;
        toprite.bitmapx = botrite.bitmapx = hix;
        topleft.bitmapy = toprite.bitmapy = loy;
        botleft.bitmapy = botrite.bitmapy = hiy;

        GeoRefPoint[] corners = new GeoRefPoint[] { topleft, toprite, botleft, botrite };
        double[][] wftmat = new double[8][];
        for (int i = 0; i < 4; i ++) {
            GeoRefPoint corner = corners[i];
            proj.ChartPixel2LatLonExact (corner.bitmapx, corner.bitmapy, corner);

            // tfw_a * x + tfw_c * y + tfw_e + tfw_g * x * y = lon
            // tfw_b * x + tfw_d * y + tfw_f + tfw_h * x * y = lat
            tfwmat[i]   = new double[] { corner.bitmapx, 0, corner.bitmapy, 0, 1, 0, corner.bitmapx * corner.bitmapy, 0, corner.lon };
            tfwmat[i+4] = new double[] { 0, corner.bitmapx, 0, corner.bitmapy, 0, 1, 0, corner.bitmapx * corner.bitmapy, corner.lat };

            // wft_a * lon + wft_c * lat + wft_e + wft_g * lon * lat = x
            // wft_b * lon + wft_d * lat + wft_f + wft_h * lon * lat = y
            wftmat[i]   = new double[] { corner.lon, 0, corner.lat, 0, 1, 0, corner.lon * corner.lat, 0, corner.bitmapx };
            wftmat[i+4] = new double[] { 0, corner.lon, 0, corner.lat, 0, 1, 0, corner.lon * corner.lat, corner.bitmapy };
        }

        // fill the projection with that mapping
        try {
            Lib.RowReduce (tfwmat);
            Lib.RowReduce (wftmat);
        } catch (ArithmeticException ae) {
            setMappingStatus ("8pt: redundant points");
            return false;
        }

        proj.tfw_a = tfwmat[0][8];
        proj.tfw_b = tfwmat[1][8];
        proj.tfw_c = tfwmat[2][8];
        proj.tfw_d = tfwmat[3][8];
        proj.tfw_e = tfwmat[4][8];
        proj.tfw_f = tfwmat[5][8];
        proj.tfw_g = tfwmat[6][8];
        proj.tfw_h = tfwmat[7][8];

        proj.wft_a = wftmat[0][8];
        proj.wft_b = wftmat[1][8];
        proj.wft_c = wftmat[2][8];
        proj.wft_d = wftmat[3][8];
        proj.wft_e = wftmat[4][8];
        proj.wft_f = wftmat[5][8];
        proj.wft_g = wftmat[6][8];
        proj.wft_h = wftmat[7][8];

        // get centerpoint using that mapping
        double cx  = (lox + hix) / 2.0;
        double cy  = (loy + hiy) / 2.0;
        LatLon cll = new LatLon ();
        proj.ChartPixel2LatLonExact (cx, cy, cll);

        // get a point up from that
        LatLon upll = new LatLon ();
        proj.ChartPixel2LatLonExact (cx, (loy + cy) / 2.0, upll);

        // 'up' heading
        double uphdgrads = Lib.LatLonTC_rad (cll.lat, cll.lon, upll.lat, upll.lon);
        double uphdgdegs = Math.toDegrees (uphdgrads);

        // find a point 1nm southeast of center
        double selat = Lib.LatHdgDist2Lat (cll.lat, uphdgdegs + 135, 1.0);
        double selon = Lib.LatLonHdgDist2Lon (cll.lat, cll.lon, uphdgdegs + 135, 1.0);
        PointD sexy = new PointD ();
        proj.LatLon2ChartPixelExact (selat, selon, sexy);

        // should be same number of X and Y pixels
        double dx = sexy.x - cx;
        double dy = sexy.y - cy;
        if ((dx < dy * 0.9) || (dy < dx * 0.9)) {
            setMappingStatus (String.format (Locale.US, "8pt: aspect ratio %.2f", dy / dx));
            return false;
        }

        // mark those 4 points as 'used for mapping' so user can see which ones are being used
        for (int i = 0; i < 4; i ++) {
            latPoints[i].usedmap = true;
            lonPoints[i].usedmap = true;
        }

        // start using the new mapping
        manualmapper = proj;
        return true;
    }

    // see if there are 4 points for mapping
    // two lat markers, two lon markers
    // assumes lat,lon lines are horizontal and vertical
    private void tryManualMapping4 ()
    {
        // scan list of manually entered points
        // get northmost, southmost, eastmost and westmost
        GeoRefPoint northlat = null;
        GeoRefPoint southlat = null;
        GeoRefPoint eastlon  = null;
        GeoRefPoint westlon  = null;
        for (GeoRefPoint grp : geoRefPoints) {
            grp.usedmap = false;
            if (! Double.isNaN (grp.lat)) {
                if ((northlat == null) || (northlat.lat < grp.lat)) northlat = grp;
                if ((southlat == null) || (southlat.lat > grp.lat)) southlat = grp;
            }
            if (! Double.isNaN (grp.lon)) {
                if ((eastlon == null) || (eastlon.lon < grp.lon)) eastlon = grp;
                if ((westlon == null) || (westlon.lon > grp.lon)) westlon = grp;
            }
        }

        // make sure we got at least 2 of each lat,lon and make sure they are different
        if ((northlat == southlat) || (eastlon == westlon)) return;

        double dlon = westlon.lon - eastlon.lon;
        double dlat = southlat.lat - northlat.lat;

        if ((Math.abs (dlon) < 1.0/360.0) || (Math.abs (dlat) < 1.0/360.0)) {
            if (Math.abs (dlon) < 1.0/360.0) setMappingStatus ("4pt: longitudes too close");
            if (Math.abs (dlat) < 1.0/360.0) setMappingStatus ("4pt: latitudes too close");
            return;
        }

        int dx_nsup = eastlon.bitmapx  - westlon.bitmapx;
        int dy_nsup = southlat.bitmapy - northlat.bitmapy;
        int dx_ewup = southlat.bitmapx - northlat.bitmapx;
        int dy_ewup = westlon.bitmapy  - eastlon.bitmapy;

        double[][] tfwmat, wftmat;
        if (Math.abs (dx_nsup) + Math.abs (dy_nsup) > Math.abs (dx_ewup) + Math.abs (dy_ewup)) {

            // assume north or south is up
            if ((Math.abs (dx_nsup) < 60) || (Math.abs (dy_nsup) < 60)) {
                setMappingStatus ("4pt: no much gap between north/south points");
                return;
            }

            // tfw_a * x +     0 * y + tfw_e = lon
            //     0 * x + tfw_d * y + tfw_f = lat
            tfwmat = new double[][] {
                    { eastlon.bitmapx, 0, 0, 0, 1, 0, eastlon.lon },
                    { 0, 1, 0, 0, 0, 0, 0 },  // tfw_a = 0
                    { 0, 0, 1, 0, 0, 0, 0 },  // tfw_c = 0
                    { 0, 0, 0, northlat.bitmapy, 0, 1, northlat.lat },
                    { westlon.bitmapx, 0, 0, 0, 1, 0, westlon.lon },
                    { 0, 0, 0, southlat.bitmapy, 0, 1, southlat.lat },
            };

            // wft_a * lon +     0 * lat + wft_e = x
            //     0 * lon + wft_d * lat + wft_f = y
            wftmat = new double[][] {
                    { eastlon.lon, 0, 0, 0, 1, 0, eastlon.bitmapx },
                    { 0, 1, 0, 0, 0, 0, 0 },    // wft_b = 0
                    { 0, 0, 1, 0, 0, 0, 0 },    // wft_c = 0
                    { 0, 0, 0, northlat.lat, 0, 1, northlat.bitmapy },
                    { westlon.lon, 0, 0, 0, 1, 0, westlon.bitmapx },
                    { 0, 0, 0, southlat.lat, 0, 1, southlat.bitmapy },
            };
        } else {

            // assume east or west is up
            if ((Math.abs (dx_ewup) < 60) || (Math.abs (dy_ewup) < 60)) {
                setMappingStatus ("4pt: no much gap between east/west points");
                return;
            }

            //     0 * x + tfw_c * y + tfw_e = lon
            // tfw_b * x +     0 * y + tfw_f = lat
            tfwmat = new double[][] {
                    { 1, 0, 0, 0, 0, 0, 0 },  // tfw_a = 0
                    { 0, northlat.bitmapx, 0, 0, 0, 1, northlat.lat },
                    { 0, 0, eastlon.bitmapy, 0, 1, 0, eastlon.lon },
                    { 0, 0, 0, 1, 0, 0, 0 },  // tfw_d = 0
                    { 0, 0, westlon.bitmapy, 0, 1, 0, westlon.lon },
                    { 0, southlat.bitmapx, 0, 0, 0, 1, southlat.lat },
            };

            //     0 * lon + wft_c * lat + wft_e = x
            // wft_b * lon +     0 * lat + wft_f = y
            wftmat = new double[][] {
                    { 1, 0, 0, 0, 0, 0, 0 },    // wft_a = 0
                    { 0, eastlon.lon, 0, 0, 0, 1, eastlon.bitmapy },
                    { 0, 0, northlat.lat, 0, 1, 0, northlat.bitmapx },
                    { 0, 0, 0, 1, 0, 0, 0 },    // wft_d = 0
                    { 0, 0, southlat.lat, 0, 1, 0, southlat.bitmapx },
                    { 0, westlon.lon, 0, 0, 0, 1, westlon.bitmapy },
            };
        }
        Lib.RowReduce (tfwmat);
        Lib.RowReduce (wftmat);

        // set up mapping transformation
        BiLinProj proj = new BiLinProj ();
        proj.tfw_a = tfwmat[0][6];
        proj.tfw_b = tfwmat[1][6];
        proj.tfw_c = tfwmat[2][6];
        proj.tfw_d = tfwmat[3][6];
        proj.tfw_e = tfwmat[4][6];
        proj.tfw_f = tfwmat[5][6];
        proj.wft_a = wftmat[0][6];
        proj.wft_b = wftmat[1][6];
        proj.wft_c = wftmat[2][6];
        proj.wft_d = wftmat[3][6];
        proj.wft_e = wftmat[4][6];
        proj.wft_f = wftmat[5][6];

        // test mapping
        // - get bitmap x,y near center of bitmap
        double cx = (northlat.bitmapx + southlat.bitmapx + eastlon.bitmapx + westlon.bitmapx) / 4.0;
        double cy = (northlat.bitmapy + southlat.bitmapy + eastlon.bitmapy + westlon.bitmapy) / 4.0;
        // - get alleged lat/lon at that point
        LatLon ll = new LatLon ();
        proj.ChartPixel2LatLonExact (cx, cy, ll);
        // - get bitmap x,y one mile south and one mile east
        PointD pt = new PointD ();
        proj.LatLon2ChartPixelExact (ll.lat - 1.0 / 60.0, ll.lon + 1.0 / 60.0 / Math.cos (Math.toRadians (ll.lat)), pt);
        // - make sure the box is square
        double dx = Math.abs (pt.x - cx);
        double dy = Math.abs (pt.y - cy);
        if ((dx < dy * 0.9) || (dy < dx * 0.9)) {
            setMappingStatus (String.format (Locale.US, "4pt: aspect ratio %.2f", dy / dx));
            return;
        }

        // mark those 4 points as 'used for mapping' so user can see which ones are being used
        northlat.usedmap = true;
        southlat.usedmap = true;
        eastlon.usedmap  = true;
        westlon.usedmap  = true;

        // start using this mapper
        manualmapper = proj;
    }
}
