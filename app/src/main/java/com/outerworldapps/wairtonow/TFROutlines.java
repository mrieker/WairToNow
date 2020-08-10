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
import android.content.ContentValues;
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
import android.util.Xml;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
    private final static LatLon[] nullLatLonArray = new LatLon[0];
    private final static Outline[] nullOutlineArray = new Outline[0];
    private final static String TAG = "WairToNow";
    private final static String[] gametfrcols = new String[] { "g_eff", "g_name", "g_lat", "g_lon" };
    private final static String[] pathtfrcols = new String[] { "p_id", "p_eff", "p_exp", "p_tz", "p_bot", "p_top", "p_fet", "p_lls" };
    private final static TimeZone utctz = TimeZone.getTimeZone ("UTC");

    private final static String faaurl = "https://tfr.faa.gov";
    private final static String faapfx = "/save_pages/detail_";

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
    private File gtpermfile;
    private File gttempfile;
    private File ptpermfile;
    private PathThread pathThread;
    private GameThread gameThread;
    private GupdThread gupdThread;
    private int tfrFilter;
    private final Object pathlock = new Object ();
    private final Object gamelock = new Object ();
    private Paint bgpaint;
    private Paint blpaint;
    private Paint fgpaint;
    private Paint hipaint;
    private SimpleDateFormat sdfout;
    private View chartview;

    public String statusline;

    /**
     * Chart page being opened, start reading TFRs from database.
     */
    public void populate (WairToNow wtn)
    {
        synchronized (pathlock) {
            tfrFilter = wtn.optionsView.tfrFilterOption.getVal ();

            // if user is shutting TFRs off, break thread out if its sleep
            if ((pathThread != null) && (tfrFilter == OptionsView.TFR_NONE)) {
                pathThread.interrupt ();
            }

            // if user is turning TFRs on, set stuff up and start thread up
            if ((pathThread == null) && (tfrFilter != OptionsView.TFR_NONE)) {

                // one-time inits
                if (sdfout == null) {
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

                    // start with empty lists so we don't have to test for null
                    gameoutlines = new LinkedList<> ();
                    pathoutlines = new LinkedList<> ();

                    // game and path tfr databases
                    // - gametfrs2.db gets downloaded from server via gametfrs.php
                    // - pathtfrs.db gets built by scanning tfr.faa.gov
                    gtpermfile = new File (WairToNow.dbdir + "/nobudb/gametfrs2.db");
                    gttempfile = new File (WairToNow.dbdir + "/nobudb/gametfrs2.db.tmp");
                    ptpermfile = new File (WairToNow.dbdir + "/nobudb/pathtfrs.db");
                }

                // start downloading and/or reading data files
                pathThread = new PathThread ();
                pathThread.start ();
            }
        }

        // make sure we have gametfr updater going if TFRs enabled
        // shut it down if TFRs disabled
        synchronized (gamelock) {
            if ((gupdThread != null) && (tfrFilter == OptionsView.TFR_NONE)) {
                gupdThread.interrupt ();
            }
            if ((gupdThread == null) && (tfrFilter != OptionsView.TFR_NONE)) {
                gupdThread = new GupdThread ();
                gupdThread.start ();
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
                if ((gameThread == null) && needToLoadGames () && gtpermfile.exists ()) {
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
    // the gametfrs.db database to see what is outside and reset the boundaries.
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
     * Contains path of an arbitrary shaped TFR (Hazard, Security, VIP, ...)
     */
    private class PathOut extends Outline implements View.OnClickListener {
        private boolean appears;
        private Context context;
        private double northmostLat;
        private double southmostLat;
        private double eastmostLon;
        private double westmostLon;
        private LatLon[] latlons;
        private long fetched;
        private Path path = new Path ();
        private PointD[] canpixs;
        private String botaltcode;
        private String tfrid;
        private String topaltcode;

        /**
         * Construct by downloading and decoding XML file from FAA.
         *  Input:
         *   ident = "0_9876"
         *  Output:
         *   this filled in
         */
        public void readXml (String ident, long now)
                throws Exception
        {
            tfrid   = ident;
            efftime = 0;
            exptime = 0xFFFFFFFFL * 1000;
            fetched = now;

            double geolat = Double.NaN;
            double geolon = Double.NaN;
            LinkedList<LatLon> lllist = new LinkedList<> ();
            String botaltval = null;
            String botaltuom = null;
            String topaltval = null;
            String topaltuom = null;

            SimpleDateFormat sdf = new SimpleDateFormat ("yyyy-MM-dd@HH:mm:ss", Locale.US);
            sdf.setTimeZone (utctz);

            // start reading XML file from FAA website
            BufferedReader br = getHttpReader (faaurl + faapfx + tfrid + ".xml");
            try {

                // XmlPullParser gags on stuff before <Group> and after </Group>
                // so strip all that off
                StringBuilder sb = new StringBuilder ();
                for (String line; (line = br.readLine ()) != null;) {
                    sb.append (line);
                }
                br.close ();
                String st = sb.toString ().trim ();
                int gb = st.indexOf ("<Group>");
                int ge = st.lastIndexOf ("</Group>");
                st = st.substring (gb, ge + 8);
                br = new BufferedReader (new StringReader (st));

                // pass to parser
                XmlPullParser xpp = Xml.newPullParser ();
                xpp.setFeature (XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                xpp.setInput (br);

                // scan the XML text
                StringBuilder started = new StringBuilder ();
                for (int eventType = xpp.getEventType (); eventType != XmlPullParser.END_DOCUMENT; eventType = xpp.next ()) {
                    switch (eventType) {
                        case XmlPullParser.START_TAG: {
                            started.append (" ");
                            started.append (xpp.getName ());
                            break;
                        }

                        case XmlPullParser.TEXT: {
                            switch (started.toString ()) {
                                case " Group Add Not dateEffective": {
                                    efftime = decodeXmlTime (sdf, xpp.getText ());
                                    break;
                                }
                                case " Group Add Not dateExpire": {
                                    exptime = decodeXmlTime (sdf, xpp.getText ());
                                    break;
                                }
                                case " Group Add Not codeTimeZone": {
                                    tzname = xpp.getText ();
                                    break;
                                }

                                case " Group Add Not TfrNot TFRAreaGroup aseTFRArea codeDistVerLower": {
                                    botaltcode = xpp.getText ();
                                    break;
                                }
                                case " Group Add Not TfrNot TFRAreaGroup aseTFRArea valDistVerLower": {
                                    botaltval  = xpp.getText ();
                                    break;
                                }
                                case " Group Add Not TfrNot TFRAreaGroup aseTFRArea uomDistVerLower": {
                                    botaltuom  = xpp.getText ();
                                    break;
                                }
                                case " Group Add Not TfrNot TFRAreaGroup aseTFRArea codeDistVerUpper": {
                                    topaltcode = xpp.getText ();
                                    break;
                                }
                                case " Group Add Not TfrNot TFRAreaGroup aseTFRArea valDistVerUpper": {
                                    topaltval  = xpp.getText ();
                                    break;
                                }
                                case " Group Add Not TfrNot TFRAreaGroup aseTFRArea uomDistVerUpper": {
                                    topaltuom  = xpp.getText ();
                                    break;
                                }

                                case " Group Add Not TfrNot TFRAreaGroup abdMergedArea Avx geoLat": {
                                    geolat = decodeXmlLL (xpp.getText (), 'N', 'S');
                                    break;
                                }
                                case " Group Add Not TfrNot TFRAreaGroup abdMergedArea Avx geoLong": {
                                    geolon = decodeXmlLL (xpp.getText (), 'E', 'W');
                                    break;
                                }
                            }
                            break;
                        }

                        case XmlPullParser.END_TAG: {
                            String ending = " " + xpp.getName ();
                            String startd = started.toString ();
                            if (! startd.endsWith (ending)) throw new Exception ("xml <" + startd + "> ends with </" + ending + ">");
                            switch (startd) {
                                case " Group Add Not TfrNot TFRAreaGroup abdMergedArea Avx": {
                                    lllist.add (new LatLon (geolat, geolon));
                                    geolat = Double.NaN;
                                    geolon = Double.NaN;
                                    break;
                                }
                            }
                            started.delete (started.length () - ending.length (), started.length ());
                            break;
                        }
                    }
                }
            } finally {
                br.close ();
            }

            // make sure first and last lat/lon are the same
            if (! lllist.isEmpty ()) {
                LatLon llbeg = lllist.getFirst ();
                LatLon llend = lllist.getLast ();
                if ((llbeg.lat != llend.lat) || (llbeg.lon != llend.lon)) {
                    lllist.add (llbeg);
                }
            }

            // make it into an array cuz it won't change after this
            // also alloc array for canvas pixels
            latlons = lllist.toArray (nullLatLonArray);
            int nlls = latlons.length;
            canpixs = new PointD[nlls];
            for (int i = 0; i < nlls; i ++) {
                canpixs[i] = new PointD ();
            }

            // ALT/HEI 2499 FL/FT
            botaltcode += " " + botaltval + " " + botaltuom;
            topaltcode += " " + topaltval + " " + topaltuom;

            computeLatLonLimits ();
        }

        /**
         * Construct from row read from database.
         *  Input:
         *   result = row of pathtfrcols
         *  Output:
         *   returns time TFR was fetched from FAA
         *   this filled in
         */
        public long readDB (Cursor result)
        {
            tfrid      = result.getString (0);
            efftime    = result.getLong   (1) * 1000;
            exptime    = result.getLong   (2) * 1000;
            tzname     = result.getString (3);
            botaltcode = result.getString (4);
            topaltcode = result.getString (5);
            fetched    = result.getLong   (6) * 1000;
            byte[] llblob = result.getBlob (7);

            int nlls = llblob.length / 8;
            latlons = new LatLon[nlls];
            canpixs = new PointD[nlls];

            int j = 0;
            for (int i = 0; i < nlls; i ++) {
                int ilat = llblob[j++];
                ilat = (ilat << 8) | (llblob[j++] & 0xFF);
                ilat = (ilat << 8) | (llblob[j++] & 0xFF);
                ilat = (ilat << 8) | (llblob[j++] & 0xFF);
                int ilon = llblob[j++];
                ilon = (ilon << 8) | (llblob[j++] & 0xFF);
                ilon = (ilon << 8) | (llblob[j++] & 0xFF);
                ilon = (ilon << 8) | (llblob[j++] & 0xFF);
                latlons[i] = new LatLon (ilat * 90.0 / 0x40000000, ilon * 90.0 / 0x40000000);
                canpixs[i] = new PointD ();
            }

            computeLatLonLimits ();

            return fetched;
        }

        // compute the lat/lon limits
        private void computeLatLonLimits ()
        {
            northmostLat = -90.0;
            southmostLat =  90.0;
            eastmostLon = -180.0;
            westmostLon =  180.0;
            for (LatLon ll : latlons) {
                if (northmostLat < ll.lat) northmostLat = ll.lat;
                if (southmostLat > ll.lat) southmostLat = ll.lat;
                eastmostLon = Lib.Eastmost (eastmostLon, ll.lon);
                westmostLon = Lib.Westmost (westmostLon, ll.lon);
            }
        }

        /**
         * Write to SQL database.
         */
        public void writeDB (SQLiteDatabase sqldb)
        {
            int nlls = latlons.length;
            byte[] llblob = new byte[nlls*8];
            int j = 0;
            for (LatLon latlon : latlons) {
                int ilat = (int) Math.round (latlon.lat * 0x40000000 / 90.0);
                int ilon = (int) Math.round (latlon.lon * 0x40000000 / 90.0);
                llblob[j++] = (byte) (ilat >> 24);
                llblob[j++] = (byte) (ilat >> 16);
                llblob[j++] = (byte) (ilat >>  8);
                llblob[j++] = (byte)  ilat;
                llblob[j++] = (byte) (ilon >> 24);
                llblob[j++] = (byte) (ilon >> 16);
                llblob[j++] = (byte) (ilon >>  8);
                llblob[j++] = (byte)  ilon;
            }

            ContentValues values = new ContentValues (5);
            values.put ("p_id",  tfrid);
            values.put ("p_eff", efftime / 1000);
            values.put ("p_exp", exptime / 1000);
            values.put ("p_tz",  tzname);
            values.put ("p_bot", botaltcode);
            values.put ("p_top", topaltcode);
            values.put ("p_fet", fetched / 1000);
            values.put ("p_lls", llblob);
            sqldb.insert ("pathtfrs", null, values);
        }

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
            tv2.setText (tfrid.replace ('_', '/'));
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
                    PointD canpix = canpixs[i++];
                    appears |= chart.LatLon2CanPixExact (ll.lat, ll.lon, canpix);
                    if (first) path.moveTo ((float) canpix.x, (float) canpix.y);
                    else path.lineTo ((float) canpix.x, (float) canpix.y);
                    first = false;
                }
                path.close ();
                if (appears) {
                    canvas.drawPath (path, bgpaint);
                    canvas.drawPath (path, highlight ? hipaint : fgpaint);
                }
            }
        }

        // see if the given canvas x,y pixel is within the TFR
        @Override
        public boolean touched (double canpixx, double canpixy)
        {
            if (! appears) return false;
            int n = canpixs.length - 1;
            for (int i = 0; i < n;) {
                PointD p1 = canpixs[i];
                PointD p2 = canpixs[++i];
                if (Lib.distToLineSeg (canpixx, canpixy, p1.x, p1.y, p2.x, p2.y) < TOUCHDIST) return true;
            }
            return false;
        }

        // id in altert box was clicked on, open web page
        @Override
        public void onClick (View v)
        {
            String weblink = faaurl + faapfx + tfrid + ".html";
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
            if (altcode.endsWith (" 0 FT")) return "SFC";
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
            if (altcode.endsWith (" 0 FT")) return null;

            // the only thing we convert is 'HEI number FT'
            String[] parts = altcode.split (" ");
            if (! parts[0].equals ("HEI")) return null;
            if (! parts[2].equals ("FT")) return null;

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

    // decode time string in format yyyy-mm-ddThh:mm:ss
    private static long decodeXmlTime (SimpleDateFormat sdf, String str)
            throws ParseException
    {
        str = str.replace ("T", "@");
        return sdf.parse (str).getTime ();
    }

    // decode latlon string in format number{pos/net}
    private static double decodeXmlLL (String str, char pos, char neg)
    {
        int n = str.length () - 1;
        double ll = Double.parseDouble (str.substring (0, n));
        char e = str.charAt (n);
        if (e == neg) ll = - ll;
        else if (e != pos) throw new NumberFormatException ("bad latlon suffin on " + str);
        return ll;
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
     * Read web page file.
     */
    private static BufferedReader getHttpReader (String name)
            throws IOException
    {
        return new BufferedReader (new InputStreamReader (getHttpStream (name)));
    }

    private static InputStream getHttpStream (String name)
            throws IOException
    {
        URL url = new URL (name);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection ();
        httpCon.setRequestMethod ("GET");
        httpCon.connect ();
        int rc = httpCon.getResponseCode ();
        if (rc != HttpURLConnection.HTTP_OK) {
            throw new IOException ("http response code " + rc);
        }
        return httpCon.getInputStream ();
    }

    /**
     * Downloads updated Path TFR files every 10 minutes from the FAA.
     * Updates the on-screen list.
     */
    private class PathThread extends Thread {

        @Override
        public void run ()
        {
            setName ("PathThread");

            try {
                SQLiteDatabase sqldb = SQLiteDatabase.openDatabase (
                        ptpermfile.getPath (), null, SQLiteDatabase.CREATE_IF_NECESSARY);
                try {
                    sqldb.execSQL ("CREATE TABLE IF NOT EXISTS pathtfrs (p_id TEXT PRIMARY KEY, " +
                            "p_eff INTEGER NOT NULL, p_exp INTEGER NOT NULL, " +
                            "p_tz TEXT, p_bot TEXT NOT NULL, p_top TEXT NOT NULL, " +
                            "p_fet INTEGER NOT NULL, p_lls BLOB NOT NULL)");

                    // build TFRs from what we have from before
                    LinkedList<PathOut> pathouts = new LinkedList<> ();
                    long fetched = 0;
                    long now = System.currentTimeMillis ();
                    Cursor result = sqldb.query ("pathtfrs", pathtfrcols, null, null, null, null, null);
                    try {
                        if (result.moveToFirst ()) do {
                            PathOut tfr = new PathOut ();
                            fetched = tfr.readDB (result);
                            pathouts.add (tfr);
                        } while (result.moveToNext ());
                    } finally {
                        result.close ();
                    }

                    // post them for display
                    // fetched should be the same for all in database
                    // ...as we rollback if there is a partial update
                    if (fetched > 0) postit (now, fetched, pathouts);

                    while (true) {

                        // maybe we are being shut off
                        synchronized (pathlock) {
                            if (tfrFilter == OptionsView.TFR_NONE) {
                                statusline = null;
                                pathThread = null;
                                break;
                            }
                        }

                        // attempt to download new version of TFR database from FAA
                        pathouts = new LinkedList<> ();
                        now = System.currentTimeMillis ();
                        Log.i (TAG, "downloading " + faaurl);
                        try {
                            BufferedReader br = getHttpReader (faaurl + "/tfr2/list.html");
                            try {
                                sqldb.beginTransaction ();
                                ContentValues updatenow = new ContentValues (1);
                                updatenow.put ("p_fet", now / 1000);
                                HashSet<String> dups = new HashSet<> ();
                                for (String line; (line = br.readLine ()) != null;) {
                                    int i, j;
                                    for (i = 0; (j = line.indexOf (faapfx, i)) >= 0;) {
                                        j += faapfx.length ();
                                        i  = line.indexOf (".html", j);
                                        if (i < 0) break;

                                        // skip any duplicated TFR entries
                                        String detail = line.substring (j, i);
                                        if (dups.contains (detail)) continue;
                                        dups.add (detail);

                                        // see if we already have the info from the XML file in the database
                                        String[] det = new String[] { detail };
                                        result = sqldb.query ("pathtfrs", pathtfrcols, "p_id=?", det, null, null, null, null);
                                        try {
                                            PathOut tfr = new PathOut ();
                                            if (result.moveToFirst ()) {

                                                // if so, don't fetch XML file but just use database values
                                                sqldb.update ("pathtfrs", updatenow, "p_id=?", det);
                                                tfr.readDB (result);
                                            } else {

                                                // if not, read XML from FAA website and write to database
                                                Log.i (TAG, "downloading " + faaurl + " " + detail);
                                                tfr.readXml (detail, now);
                                                tfr.writeDB (sqldb);
                                            }

                                            // either way, add TFR to on-screen list
                                            pathouts.add (tfr);
                                        } catch (Exception e) {
                                            Log.w (TAG, "exception reading TFR " + detail, e);
                                        } finally {
                                            result.close ();
                                        }
                                    }
                                }

                                // delete all old TFRs from database that aren't on FAA website anymore
                                sqldb.delete ("pathtfrs", "p_fet<" + (now / 1000), null);

                                // got all TFRs listed on most recent FAA webpage, commit it
                                sqldb.setTransactionSuccessful ();
                            } finally {
                                sqldb.endTransaction ();
                                br.close ();
                            }
                            Log.i (TAG, "download " + faaurl + " complete");

                            // post the updated TFR list to screen
                            postit (now, now, pathouts);

                            // check FAA website again in 10 minutes
                            try { Thread.sleep (600000); } catch (InterruptedException ignored) { }
                        } catch (Exception e) {

                            // maybe no internet, check again in one minute
                            Log.w (TAG, "exception reading " + faaurl, e);
                            try { Thread.sleep (60000); } catch (InterruptedException ignored) { }
                        }
                    }
                } finally {
                    sqldb.close ();
                }
            } catch (Exception e) {
                Log.w (TAG, "exception processing database", e);
            }
        }

        // refresh the canvas with the new TFRs
        private void postit (long now, long fetched, Collection<PathOut> pathouts)
        {
            SimpleDateFormat sdfout = new SimpleDateFormat (
                    (now - fetched < 12*60*60*1000) ? "HH:mm" : "yyyy-MM-dd@HH:mm",
                    Locale.US);
            sdfout.setTimeZone (utctz);
            statusline   = "TFRs as of " + sdfout.format (fetched) + "z";
            pathoutlines = pathouts;
            if (chartview != null) chartview.postInvalidate ();
        }
    }

    /**
     * Decode a range of Game TFRs from whatever database we currently have.
     */
    private class GameThread extends Thread {
        @Override
        public void run ()
        {
            setName ("GameThread");

            try {
                while (true) {

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
                    String where = "g_lat<" + gnlat + " AND g_lat>" + gslat +
                                    " AND g_lon<" + gelon + " AND g_lon>" + gwlon;
                    SQLiteDatabase sqldb = SQLiteDatabase.openDatabase (
                                gtpermfile.getPath (), null, SQLiteDatabase.OPEN_READONLY);
                    try {
                        long now = System.currentTimeMillis ();
                        Cursor result = sqldb.query ("gametfrs", gametfrcols,
                                where, null, null, null, null, null);
                        try {
                            if (result.moveToFirst ()) do {
                                GameOut gameout = new GameOut ();
                                gameout.efftime = result.getLong (0) * 1000;
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

                    // repeat in case canvas has moved a lot
                }
            } catch (Exception e) {
                Log.w (TAG, "exception processing game tfrs", e);
                // some error reading database, delete and get another downloaded asap
                // do it locked to prevent draw() from starting us up again
                synchronized (gamelock) {
                    Lib.Ignored (gtpermfile.delete ());
                    gameThread = null;
                }
                if (gupdThread != null) gupdThread.interrupt ();
            }
        }
    }

    /**
     * Runs once a day to get game TFR database from server.
     */
    private class GupdThread extends Thread {
        @Override
        public void run ()
        {
            setName ("GupdThread");

            while (true) {

                // maybe we are shut down
                synchronized (gamelock) {
                    if (tfrFilter == OptionsView.TFR_NONE) {
                        gupdThread = null;
                        break;
                    }
                }

                int nextrun = 3600000;
                try {
                    long now = System.currentTimeMillis ();

                    // see if we already have an up-to-date database
                    // they are created a little before 00:00z each day
                    if (gtpermfile.exists ()) {
                        SQLiteDatabase sqldb = SQLiteDatabase.openDatabase (gtpermfile.toString (), null, SQLiteDatabase.OPEN_READONLY);
                        try {
                            Cursor result = sqldb.query ("asof", new String[] { "as_of" }, null, null, null, null, null);
                            try {
                                if (result.moveToFirst ()) {
                                    long asof = result.getLong (0);
                                    if (asof / 86400 >= now / 86400000) return;
                                }
                            } finally {
                                result.close ();
                            }
                        } finally {
                            sqldb.close ();
                        }
                    }

                    // we don't have the one for today, try to download it
                    // it's quite possible we don't have internet connection
                    Log.i (TAG, "fetching latest gametfrs2.db");
                    GZIPInputStream gis = new GZIPInputStream (getHttpStream (MaintView.dldir + "/getgametfrs.php"));
                    try {
                        FileOutputStream fos = new FileOutputStream (gttempfile);
                        try {
                            byte[] buf = new byte[4096];
                            for (int rc; (rc = gis.read (buf)) > 0;) {
                                fos.write (buf, 0, rc);
                            }
                        } finally {
                            fos.close ();
                        }
                    } finally {
                        gis.close ();
                    }
                    if (! gttempfile.renameTo (gtpermfile)) throw new IOException ("error renaming gametfrs2.db.tmp file");
                    Log.i (TAG, "fetch gametfrs2.db complete");

                    // get chart redrawn with latest data
                    // this causes the main draw() to be called which
                    // ...then runs the GameThread to read the database
                    synchronized (gamelock) {
                        gamenorthlat = -90.0;
                        gamesouthlat =  90.0;
                        gameeastlon = -180.0;
                        gamewestlon =  180.0;
                    }
                    if (chartview != null) chartview.postInvalidate ();
                    nextrun = 86400000;
                } catch (Exception e) {

                    // probably no internet connection
                    Log.w (TAG, "exception updating gametfrs2.db", e);
                }

                // schedule to run one hour from now if failed to update
                // ... or during first hour of next day if update was ok
                long now = System.currentTimeMillis ();
                long del = nextrun - now % nextrun + now % 3600000;
                try { Thread.sleep (del); } catch (InterruptedException ignored) { }
            }
        }
    }
}
