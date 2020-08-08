//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/**
 * Maintain list of current TFRs.
 */
public class TFROutlines {

    // https://www.faasafety.gov/gslac/ALC/course_content.aspx?cID=42&sID=240&preview=true
    private final static int GAMEAGLFT = 3000;
    private final static int GAMERADNM = 3;

    public  final static int COLOR = 0xFFFF5500;
    private final static int GAMETIME = 8*60*60*1000;
    private final static int TOUCHDIST = 20;
    private final static Outline[] nullOutlineArray = new Outline[0];
    private final static String TAG = "WairToNow";
    private final static TimeZone utctz = TimeZone.getTimeZone ("UTC");

    private Collection<GameOut> gameoutlines;       // game TFRs in vicinity of canvas
    private Collection<PathOut> pathoutlines;       // all path-style TFRs
    private double canvasnorthlat;                  // what is currently covered by the canvas
    private double canvassouthlat;
    private double canvaseastlon;
    private double canvaswestlon;
    private double gamenorthlat;                    // what is currently covered by gameoutlines
    private double gamesouthlat;
    private double gameeastlon;
    private double gamewestlon;
    private DownloadThread downloadThread;
    private GameThread gameThread;
    private int tfrFilter;
    private final Object downlock = new Object ();
    private final Object gamelock = new Object ();
    private Paint bgpaint;
    private Paint blpaint;
    private Paint fgpaint;
    private Paint hipaint;
    private SimpleDateFormat sdfout;
    private String dbpermname;
    private String dbservname;
    private View chartview;

    public String statusline;

    /**
     * Start reading TFRs from database.
     */
    public void populate (WairToNow wtn)
    {
        synchronized (downlock) {
            tfrFilter = wtn.optionsView.tfrFilterOption.getVal ();

            // if user is shutting TFRs off, break thread out if its sleep
            if ((downloadThread != null) && (tfrFilter == OptionsView.TFR_NONE)) {
                downloadThread.interrupt ();
            }

            // if user is turning TFRs on, set stuff up and start thread up
            if ((downloadThread == null) && (tfrFilter != OptionsView.TFR_NONE)) {
                sdfout = new SimpleDateFormat ("yyyy-MM-dd@HH:mm", Locale.US);

                bgpaint = new Paint ();
                bgpaint.setColor (COLOR);
                bgpaint.setStrokeWidth (TOUCHDIST * 2);
                bgpaint.setStyle (Paint.Style.STROKE);
                bgpaint.setXfermode (new PorterDuffXfermode (PorterDuff.Mode.SCREEN));

                blpaint = new Paint ();
                blpaint.setColor (COLOR);
                blpaint.setPathEffect (new DashPathEffect (new float[] { 5, 10 }, 0));
                blpaint.setStrokeWidth (5);
                blpaint.setStyle (Paint.Style.STROKE);

                fgpaint = new Paint ();
                fgpaint.setColor (COLOR);
                fgpaint.setStrokeWidth (5);
                fgpaint.setStyle (Paint.Style.STROKE);

                hipaint = new Paint ();
                hipaint.setColor (COLOR);
                hipaint.setStrokeWidth (10);
                hipaint.setStyle (Paint.Style.STROKE);

                // database filenames
                dbpermname = WairToNow.dbdir + "/nobudb/tfrs.db";
                dbservname = MaintView.dldir + "/gettfrs.php";

                // start with empty lists so we don't have to test for null
                gameoutlines = new LinkedList<> ();
                pathoutlines = new LinkedList<> ();

                // start downloading and/or reading data files
                downloadThread = new DownloadThread ();
                downloadThread.start ();
            }
        }
    }

    /**
     * Draw TFRs on given canvas.
     *  Input:
     *   canvas = what to draw them on
     *   chart = chart as background giving latlon<->pixel mapping
     *   now = current time so we can expire old TFRs
     */
    public void draw (Canvas canvas, Chart2DView chart, long now)
    {
        if (tfrFilter != OptionsView.TFR_NONE) {

            // what to invalidate when download threads complete
            chartview = chart;

            // see if we need to re-scan game TFRs cuz chart has moved too far
            // if so, start a thread to reload gameoutlines
            // ...and meanwhile we will draw the old stuff
            synchronized (gamelock) {
                canvasnorthlat = chart.chartView.pmap.canvasNorthLat;
                canvassouthlat = chart.chartView.pmap.canvasSouthLat;
                canvaseastlon  = chart.chartView.pmap.canvasEastLon;
                canvaswestlon  = chart.chartView.pmap.canvasWestLon;
                if ((gameThread == null) && needToLoadGames ()) {
                    gameThread = new GameThread ();
                    gameThread.start ();
                }
            }

            // anyway, draw what we currently know about
            drawNormal (pathoutlines, canvas, chart, now);
            drawNormal (gameoutlines, canvas, chart, now);
            drawHighlighted (pathoutlines, canvas, chart, now);
            drawHighlighted (gameoutlines, canvas, chart, now);
        }
    }

    // see if area covered by gameoutlines is sufficient for the current canvas.
    // if canvas goes outside area covered by gameoutlines, we need to re-read
    // the GameTFRs.db database to see what is outside and reset the boundaries.
    private boolean needToLoadGames ()
    {
        return (canvasnorthlat > gamenorthlat) ||
                (canvassouthlat < gamesouthlat) ||
                (canvaseastlon > gameeastlon) ||
                (canvaswestlon < gamewestlon);
    }

    // draw all non-highlighted TFRs in the given list
    // also delete all expired TFRs
    private void drawNormal (Collection<? extends Outline> outlines, Canvas canvas, Chart2DView chart, long now)
    {
        for (Iterator<? extends Outline> it = outlines.iterator (); it.hasNext ();) {
            Outline outline = it.next ();
            if (outline.exptime < now) {
                it.remove ();
            } else if (! outline.highlight && outline.isFilteredOn (now)) {
                outline.draw (canvas, chart);
            }
        }
    }

    // draw all highlighted TFRs in the list
    private void drawHighlighted (Collection<? extends Outline> outlines, Canvas canvas, Chart2DView chart, long now)
    {
        for (Outline outline : outlines) {
            if (outline.highlight && outline.isFilteredOn (now)) {
                outline.draw (canvas, chart);
            }
        }
    }

    /**
     * See if any TFR outlines are near the given canpixx,canpixy.
     */
    public Collection<Outline> touched (double canpixx, double canpixy)
    {
        // if turned off, return empty list
        LinkedList<Outline> touched = new LinkedList<> ();
        if (tfrFilter == OptionsView.TFR_NONE) return touched;

        // get all TFRs near the given canpixx,canpixy
        long now = System.currentTimeMillis ();
        for (Outline outline : pathoutlines) {
            if (outline.isFilteredOn (now) && outline.touched (canpixx, canpixy)) {
                touched.add (outline);
            }
        }
        for (Outline outline : gameoutlines) {
            if (outline.isFilteredOn (now) && outline.touched (canpixx, canpixy)) {
                touched.add (outline);
            }
        }

        // return sorted by ascending effective time
        Outline[] array = touched.toArray (nullOutlineArray);
        Arrays.sort (array);
        return Arrays.asList (array);
    }

    /**
     * Either an outlined (path) TFR or a stadium (game) TFR.
     */
    public abstract class Outline implements Comparable<Outline> {
        public boolean highlight;
        public long efftime;
        public long exptime;
        public String tzname;

        public abstract void draw (Canvas canvas, Chart2DView chart);
        public abstract boolean touched (double canpixx, double canpixy);
        public abstract View getInfo (Context ctx);

        // see if the options filter enables this TFR
        public boolean isFilteredOn (long now)
        {
            switch (tfrFilter) {
                case OptionsView.TFR_NONE: return false;
                case OptionsView.TFR_ACTIVE: return efftime < now;
                case OptionsView.TFR_TODAY: return efftime - now < 24*60*60*1000;
                default: return true;
            }
        }

        // append efftime/exptime strings to the given text view
        protected void formatTimeRange (TextView tv)
        {
            long now = System.currentTimeMillis ();
            long deff = efftime - now;
            if (deff >= 24*60*60*1000) {
                long days = deff / (24*60*60*1000);
                tv.append ("\nACTIVE IN ");
                tv.append (Long.toString (days));
                tv.append (" DAY");
                if (days > 1) tv.append ("S");
            } else if (deff >= 60*60*1000) {
                long hrs = deff / (60*60*1000);
                tv.append ("\nACTIVE IN ");
                tv.append (Long.toString (hrs));
                tv.append (" HOUR");
                if (hrs > 1) tv.append ("S");
            } else if (deff >= 60*1000) {
                long mins = deff / (60*1000);
                tv.append ("\nACTIVE IN ");
                tv.append (Long.toString (mins));
                tv.append (" MINUTE");
                if (mins > 1) tv.append ("S");
            } else if (deff >= 0) {
                tv.append ("\nACTIVE IN LESS THAN A MINUTE");
            } else {
                tv.append ("\nACTIVE NOW");
            }

            sdfout.setTimeZone (utctz);
            String utcefftime = sdfout.format (efftime);
            tv.append ("\nEffective: ");
            tv.append (utcefftime);
            tv.append (" UTC");

            TimeZone lcltz = Lib.getTimeZone (tzname);
            if (lcltz != null) {
                sdfout.setTimeZone (lcltz);
                String lclefftime = sdfout.format (efftime);
                if (lclefftime.equals (utcefftime)) {
                    lcltz = null;
                } else {
                    tv.append ("\n           ");
                    tv.append (lclefftime);
                    tv.append (" ");
                    tv.append (tzname);
                }
            }

            tv.append ("\nExpires: ");
            if (exptime >= 0xFFFFFFFFL * 1000) tv.append ("indefinite");
            else {
                sdfout.setTimeZone (utctz);
                tv.append (sdfout.format (exptime));
                tv.append (" UTC");
                if (lcltz != null) {
                    sdfout.setTimeZone (lcltz);
                    String lclexptime = sdfout.format (exptime);
                    tv.append ("\n           ");
                    tv.append (lclexptime);
                    tv.append (" ");
                    tv.append (tzname);
                }
            }
        }

        // sort by ascending effective time for touched() method
        @SuppressWarnings("UseCompareMethod")
        @Override
        public int compareTo (Outline o)
        {
            if (efftime < o.efftime) return -1;
            if (efftime > o.efftime) return  1;
            if (exptime < o.exptime) return -1;
            if (exptime > o.exptime) return  1;
            return 0;
        }
    }

    /**
     * Contains path of an arbitrary shaped TFR
     */
    private class PathOut extends Outline implements View.OnClickListener {
        public LatLon[] latlons;
        public PointD[] canpixes;

        public String botaltcode;
        public String tfrid;
        public String weblink;
        public String topaltcode;

        public double northmostLat;
        public double southmostLat;
        public double eastmostLon;
        public double westmostLon;

        private boolean appears;
        private Context context;
        private Path path = new Path ();

        // get displayable string
        @SuppressLint("SetTextI18n")
        @Override
        public View getInfo (Context ctx)
        {
            context = ctx;
            TextView tv1 = new TextView (ctx);
            tv1.setText ("TFR ID: ");
            TextView tv2 = new TextView (ctx);
            tv2.setOnClickListener (this);
            tv2.setPaintFlags (tv2.getPaintFlags () | Paint.UNDERLINE_TEXT_FLAG);
            tv2.setText (tfrid);
            tv2.setTextColor (Color.CYAN);
            LinearLayout ll1 = new LinearLayout (ctx);
            ll1.setOrientation (LinearLayout.HORIZONTAL);
            ll1.addView (tv1);
            ll1.addView (tv2);
            TextView tv3 = new TextView (ctx);
            String botalt = getAltitude (botaltcode);
            String topalt = getAltitude (topaltcode);
            tv3.append ("altitude: ");
            tv3.append (botalt);
            tv3.append (" - ");
            tv3.append (topalt);
            String botmsl = getAltMSL (botaltcode);
            String topmsl = getAltMSL (topaltcode);
            if ((botmsl != null) || (topmsl != null)) {
                tv3.append ("\n...approx: ");
                tv3.append ((botmsl == null) ? botalt : botmsl);
                tv3.append (" - ");
                tv3.append ((topmsl == null) ? topalt : topmsl);
            }
            formatTimeRange (tv3);
            LinearLayout ll2 = new LinearLayout (ctx);
            ll2.setOrientation (LinearLayout.VERTICAL);
            ll2.addView (ll1);
            ll2.addView (tv3);
            return ll2;
        }

        // draw TFR on the given canvas, highlighted or normal
        @Override
        public void draw (Canvas canvas, Chart2DView chart)
        {
            if (Lib.LatOverlap (southmostLat, northmostLat, chart.chartView.pmap.canvasSouthLat, chart.chartView.pmap.canvasNorthLat) &&
                    Lib.LonOverlap (westmostLon, eastmostLon, chart.chartView.pmap.canvasWestLon, chart.chartView.pmap.canvasEastLon)) {
                path.rewind ();
                appears = false;
                boolean first = true;
                int i = 0;
                for (LatLon ll : latlons) {
                    PointD canpix = canpixes[i++];
                    appears |= chart.LatLon2CanPixExact (ll.lat, ll.lon, canpix);
                    if (first) path.moveTo ((float) canpix.x, (float) canpix.y);
                    else path.lineTo ((float) canpix.x, (float) canpix.y);
                    first = false;
                }
                path.close ();
                if (appears) {
                    canvas.drawPath (path, bgpaint);
                    canvas.drawPath (path, fgpaint);
                    if (highlight) canvas.drawPath (path, hipaint);
                }
            }
        }

        // see if the given canvas x,y pixel is within the TFR
        @Override
        public boolean touched (double canpixx, double canpixy)
        {
            if (! appears) return false;
            int n = canpixes.length - 1;
            for (int i = 0; i < n;) {
                PointD p1 = canpixes[i];
                PointD p2 = canpixes[++i];
                if (Lib.distToLineSeg (canpixx, canpixy, p1.x, p1.y, p2.x, p2.y) < TOUCHDIST) return true;
            }
            return false;
        }

        // id was clicked on, open web page
        @Override
        public void onClick (View v)
        {
            try {
                Intent intent = new Intent (Intent.ACTION_VIEW);
                intent.setData (Uri.parse (weblink));
                context.startActivity (intent);
            } catch (Throwable e) {
                AlertDialog.Builder adb = new AlertDialog.Builder (context);
                adb.setTitle ("Error fetching TFR web page");
                adb.setMessage (e.getMessage ());
                adb.create ().show ();
            }
        }

        // convert the '{ALT|HEI} number {FL|FT}' string to displayable string
        private String getAltitude (String altcode)
        {
            if (altcode.equals ("HEI 0 FT")) return "SFC";
            String[] parts = altcode.split (" ");
            switch (parts[0]) {
                case "ALT": {
                    switch (parts[2]) {
                        case "FL": return "FL " + parts[1];
                        case "FT": return parts[1] + " ft MSL";
                    }
                    break;
                }
                case "HEI": {
                    if (parts[2].equals ("FT")) return parts[1] + " ft AGL";
                    break;
                }
            }
            return altcode;
        }

        // convert the '{ALT|HEI} number {FL|FT}' string to displayable MSL string
        // returns null if same as getAltitude()
        private String getAltMSL (String altcode)
        {
            if (altcode.equals ("HEI 0 FT")) return null;
            String[] parts = altcode.split (" ");
            if (! parts[0].equals ("HEI")) return null;
            if (! parts[2].equals ("FT")) return null;

            // the only thing we convert is 'HEI number FT'
            calcElevFeet ();
            if (elevmetres == Topography.INVALID_ELEV) return null;
            return Math.round (Double.parseDouble (parts[1]) + elevfeet) + " ft MSL";
        }

        // try to get elevation of TFR area
        private short elevmetres = 0;
        private double elevfeet = Double.NaN;
        private void calcElevFeet ()
        {
            if (Double.isNaN (elevfeet)) {
                int nsegs = latlons.length - 1;
                double totallen = 0.0;
                double totallat = 0.0;
                double totallon = 0.0;
                for (int i = 0; i < nsegs;) {
                    LatLon p1 = latlons[i];
                    LatLon p2 = latlons[++i];
                    double dist = Lib.LatLonDist (p1.lat, p1.lon, p2.lat, p2.lon);
                    totallen += dist;
                    totallat += (p1.lat + p2.lat) * dist;
                    totallon += (p1.lon + p2.lon) * dist;
                }
                double centerlat = totallat / (2.0 * totallen);
                double centerlon = totallon / (2.0 * totallen);

                elevmetres = Topography.getElevMetres (centerlat, centerlon);
                elevfeet = elevmetres * Lib.FtPerM;
            }
        }
    }

    /**
     * These TFRs are always 3000' AGL and 3nm radius circle
     */
    private class GameOut extends Outline {
        public double lat;
        public double lon;
        public String text;

        private double northlat;
        private double radpix;
        private PointD canpix = new PointD ();

        @Override
        public void draw (Canvas canvas, Chart2DView chart)
        {
            if (northlat == 0.0) northlat = Lib.LatHdgDist2Lat (lat, 0, GAMERADNM);
            chart.LatLon2CanPixExact (northlat, lon, canpix);
            double northpixy = canpix.y;

            chart.LatLon2CanPixExact (lat, lon, canpix);
            radpix = canpix.y - northpixy;

            canvas.drawCircle ((float) canpix.x, (float) canpix.y, (float) radpix, bgpaint);
            canvas.drawCircle ((float) canpix.x, (float) canpix.y, (float) radpix,
                    (efftime < 0xFFFFFFFFL * 1000) ? fgpaint : blpaint);

            if (highlight) canvas.drawCircle ((float) canpix.x, (float) canpix.y, (float) radpix, hipaint);
        }

        @Override
        public boolean touched (double canpixx, double canpixy)
        {
            double dist = Math.hypot (canpixx - canpix.x, canpixy - canpix.y);
            return Math.abs (dist - radpix) < TOUCHDIST;
        }

        @Override
        public View getInfo (Context ctx)
        {
            TextView tv = new TextView (ctx);
            tv.append (text);
            tv.append ("\naltitude: SFC - " + GAMEAGLFT + " ft AGL");
            calcElevFeet ();
            if (elevmetres != Topography.INVALID_ELEV) {
                tv.append ("\n...approx: SFC - " + Math.round (elevfeet + GAMEAGLFT) + " ft MSL");
            }
            if (efftime < 0xFFFFFFFFL * 1000) {
                formatTimeRange (tv);
            }
            return tv;
        }

        // try to get elevation of TFR
        private short elevmetres = 0;
        private double elevfeet = Double.NaN;
        private void calcElevFeet ()
        {
            if (Double.isNaN (elevfeet)) {
                elevmetres = Topography.getElevMetres (lat, lon);
                elevfeet = elevmetres * Lib.FtPerM;
            }
        }
    }

    /**
     * Downloads updated TFR files every 10 minutes.
     * Decodes all Path TFRs (there are less than 100 of them).
     * Leaves the Game TFRs to GameThread to sift and decode.
     */
    private class DownloadThread extends Thread {
        private long generated;

        @Override
        public void run ()
        {
            boolean first = true;
            while (true) {

                // maybe we are being shut off
                synchronized (downlock) {
                    if (tfrFilter == OptionsView.TFR_NONE) {
                        statusline = null;
                        downloadThread = null;
                        break;
                    }
                }

                // attempt to download new version of the TFR database
                boolean ok = downloadgzfile (dbservname, dbpermname);

                if (ok || first) {

                    // read all path-style TFRs into memory
                    // there are less than 100 of them
                    Collection<PathOut> pathouts = decodepathfile ();

                    // tell GameThread to read the new database
                    // the canvas will always be bigger than this
                    // ...so draw() will trigger GameThread
                    synchronized (gamelock) {
                        gamenorthlat = 0.0;
                        gamesouthlat = 0.0;
                        gameeastlon  = 0.0;
                        gamewestlon  = 0.0;
                    }

                    // refresh the canvas with the new TFRs
                    long now = System.currentTimeMillis ();
                    SimpleDateFormat sdfout = new SimpleDateFormat (
                            (now - generated < 12*60*60*1000) ? "HH:mm" : "yyyy-MM-dd@HH:mm",
                            Locale.US);
                    sdfout.setTimeZone (utctz);
                    statusline   = "TFRs as of " + sdfout.format (generated) + "z";
                    pathoutlines = pathouts;
                    if (chartview != null) chartview.postInvalidate ();
                }

                // if we downloaded a datafile, wait 10 minutes before downloading again
                // if we failed to download, wait just 1 minute then try again
                try { Thread.sleep (ok ? 600000 : 60000); } catch (InterruptedException ignored) { }

                first = false;
            }
        }

        // download gz file from server, gunzip, and store on flash
        private boolean downloadgzfile (String servname, String permname)
        {
            Log.i (TAG, "downloading " + servname);
            String tempname = permname + ".tmp";
            try {
                URL url = new URL (servname);
                HttpURLConnection httpCon = (HttpURLConnection) url.openConnection ();
                try {
                    httpCon.setRequestMethod ("GET");
                    httpCon.connect ();
                    int rc = httpCon.getResponseCode ();
                    if (rc != HttpURLConnection.HTTP_OK) {
                        throw new IOException ("http response code " + rc);
                    }
                    InputStream nis = new GZIPInputStream (httpCon.getInputStream ());
                    FileOutputStream fos = new FileOutputStream (tempname);
                    try {
                        byte[] buf = new byte[4096];
                        while ((rc = nis.read (buf)) > 0) {
                            fos.write (buf, 0, rc);
                        }
                    } finally {
                        fos.close ();
                    }
                    nis.close ();
                } finally {
                    httpCon.disconnect ();
                }
                Lib.RenameFile (tempname, permname);
                return true;
            } catch (Exception e) {
                Log.w (TAG, "exception downloading " + servname, e);
                return false;
            }
        }

        // decode arbitrary path table, build outlines list
        // usually less than 100 of these
        private Collection<PathOut> decodepathfile ()
        {
            generated = 0;
            LinkedList<PathOut> pathouts = new LinkedList<> ();

            try {
                SQLiteDatabase sqldb = SQLiteDatabase.openDatabase (dbpermname, null, SQLiteDatabase.OPEN_READONLY);

                try {
                    // read as-of data from database
                    Cursor result = sqldb.query ("asof", new String[] { "as_of" },
                            null, null, null, null, null);
                    try {
                        if (! result.moveToFirst ()) throw new Exception ("missing as_of");
                        generated = result.getInt (0) * 1000L;
                    } finally {
                        result.close ();
                    }

                    // read all path-style TFRs from database
                    result = sqldb.query ("pathtfrs",
                            new String[] { "p_eff", "p_exp", "p_low", "p_top", "p_ints", "p_id", "p_link", "p_tz" },
                            null, null, null, null, null);
                    try {
                        if (result.moveToFirst ()) do {
                            // make object to hold database record
                            PathOut outline    = new PathOut ();
                            outline.efftime    = result.getLong (0) * 1000;  // effective time
                            outline.exptime    = result.getLong (1) * 1000;  // expiration time
                            outline.botaltcode = result.getString (2);       // bottom altitude
                            outline.topaltcode = result.getString (3);       // top altitude
                            outline.tfrid      = result.getString (5);       // tfr ident
                            outline.weblink    = result.getString (6);       // webpage link
                            outline.tzname     = result.getString (7);       // timezone tfr is in
                            byte[] ints = result.getBlob (4);                // lat,lon encoded as ints
                            int nlatlons = ints.length / 8;
                            outline.latlons  = new LatLon[nlatlons];
                            outline.canpixes = new PointD[nlatlons];
                            // copy array to object
                            // database makes sure last point same as first point
                            for (int i = 0; i < nlatlons; i++) {
                                // decode lat,lon and append to array
                                int ilat = (ints[i*8+0] << 24) | ((ints[i*8+1] & 0xFF) << 16) | ((ints[i*8+2] & 0xFF) << 8) | (ints[i*8+3] & 0xFF);
                                int ilon = (ints[i*8+4] << 24) | ((ints[i*8+5] & 0xFF) << 16) | ((ints[i*8+6] & 0xFF) << 8) | (ints[i*8+7] & 0xFF);
                                double lat = ilat * 90.0 / 0x40000000;
                                double lon = ilon * 90.0 / 0x40000000;
                                outline.latlons[i] = new LatLon (lat, lon);
                                // keep track of lat,lon limits
                                if (i == 0) {
                                    outline.northmostLat = outline.southmostLat = lat;
                                    outline.eastmostLon = outline.westmostLon = lon;
                                } else {
                                    if (outline.northmostLat < lat) outline.northmostLat = lat;
                                    if (outline.southmostLat > lat) outline.southmostLat = lat;
                                    outline.eastmostLon = Lib.Eastmost (outline.eastmostLon, lon);
                                    outline.westmostLon = Lib.Westmost (outline.westmostLon, lon);
                                }
                                // make entry to hold canvas pixel x,y
                                outline.canpixes[i] = new PointD ();
                            }
                            // add to list of all path-style TFRs
                            pathouts.add (outline);
                        } while (result.moveToNext ());
                    } finally {
                        result.close ();
                    }
                } finally {
                    sqldb.close ();
                }
            } catch (Exception e) {
                Log.w (TAG, "exception decoding " + dbpermname, e);
            }
            return pathouts;
        }
    }

    /**
     * Decode a range of Game TFRs from whatever GameTFRs.zip file we currently have.
     */
    private class GameThread extends Thread {
        @Override
        public void run ()
        {
            while (true) {
                try {
                    // see if we already cover the canvas, exit if so
                    // otherwise, get lat/lon range covered by canvas
                    double cnlat, cslat, celon, cwlon;
                    synchronized (gamelock) {
                        if (! needToLoadGames ()) {
                            gameThread = null;
                            break;
                        }
                        cnlat = canvasnorthlat;
                        cslat = canvassouthlat;
                        celon = canvaseastlon;
                        cwlon = canvaswestlon;
                    }

                    long now = System.currentTimeMillis ();

                    // get lat,lon range covering more than the canvas so
                    // we don't re-scan for every little movement of chart
                    double canheight = cnlat - cslat;
                    double canwidth  = celon - cwlon;
                    double gnlat = cnlat + canheight / 2.0;
                    double gslat = cslat - canheight / 2.0;
                    double gelon = celon + canwidth  / 2.0;
                    double gwlon = cwlon - canwidth  / 2.0;

                    // read records on and nearby the canvas from the .db file into memory
                    // should be just a few out of a couple thousand
                    // note that there may be many for such as 'Yankee Stadium'
                    // ...so just keep the earliest one that hasn't expired
                    HashMap<String,GameOut> outlines = new HashMap<> ();
                    String where =
                            "latitude<" + gnlat + " AND latitude>" + gslat +
                                    " AND longitude<" + gelon + " AND longitude>" + gwlon;
                    SQLiteDatabase sqldb = SQLiteDatabase.openDatabase (dbpermname, null, SQLiteDatabase.OPEN_READONLY);
                    try {
                        Cursor result = sqldb.query ("gametfr",
                                new String[] { "effective", "name", "latitude", "longitude" },
                                where, null, null, null, null, null);
                        try {
                            if (result.moveToFirst ()) do {
                                GameOut gameout = new GameOut ();
                                gameout.efftime = result.getLong (0);
                                gameout.exptime = gameout.efftime + GAMETIME;
                                gameout.text = result.getString (1);
                                gameout.lat  = result.getDouble (2);
                                gameout.lon  = result.getDouble (3);
                                if (gameout.exptime > now) {
                                    GameOut oldgo = outlines.get (gameout.text);
                                    if ((oldgo == null) || (gameout.efftime < oldgo.efftime)) {
                                        outlines.put (gameout.text, gameout);
                                    }
                                }
                            } while (result.moveToNext ());
                        } finally {
                            result.close ();
                        }
                    } finally {
                        sqldb.close ();
                    }

                    // tell drawing routine what we got now
                    synchronized (gamelock) {
                        gamenorthlat = gnlat;
                        gamesouthlat = gslat;
                        gameeastlon  = gelon;
                        gamewestlon  = gwlon;
                        gameoutlines = outlines.values ();
                    }

                    // redraw the chart with new game TFRs
                    if (chartview != null) chartview.postInvalidate ();
                } catch (Exception e) {
                    Log.w (TAG, "exception processing game tfrs", e);
                    try { Thread.sleep (60000); } catch (InterruptedException ignored) { }
                }
            }
        }
    }
}
