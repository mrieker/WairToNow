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
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLHandshakeException;

/**
 * Maintain list of current TFRs.
 */
public class TFROutlines {

    // https://www.faasafety.gov/gslac/ALC/course_content.aspx?cID=42&sID=240&preview=true
    private final static int GAMEAGLFT = 3000;
    private final static int GAMERADNM = 3;

    private final static double[] nullDoubleArray = new double[0];
    public  final static int COLOR = 0xFFFF5500;
    private final static int TOUCHDIST = 20;
    private final static String TAG = "WairToNow";
    private final static String[] pathtfrcols = new String[] {
            "p_id", "p_area", "p_eff", "p_exp", "p_tz", "p_bot", "p_top", "p_fet", "p_lls" };
    private final static TimeZone utctz = TimeZone.getTimeZone ("UTC");

    private final static String faaurl = "https://tfr.faa.gov";
    private final static String faapfx = "save_pages/detail_";

    private boolean useproxy;
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
        tfrFilter = wtn.optionsView.tfrFilterOption.getVal ();

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
            // - gametfrs.db gets downloaded from server via gametfrs.php
            // - pathtfrs.db gets built by scanning tfr.faa.gov
            gtpermfile = new File (WairToNow.dbdir + "/nobudb/gametfrs4.db");
            gttempfile = new File (WairToNow.dbdir + "/nobudb/gametfrs4.db.tmp");
            ptpermfile = new File (WairToNow.dbdir + "/nobudb/pathtfrs2.db");
        }

        synchronized (pathlock) {

            // if user is shutting TFRs off, break thread out if its sleep
            if ((pathThread != null) && (tfrFilter == OptionsView.TFR_NONE)) {
                pathThread.interrupt ();
            }

            // if user is turning TFRs on, start thread up
            if ((pathThread == null) && (tfrFilter != OptionsView.TFR_NONE)) {
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

            // anyway, calculate pixels for current latlon<->pixel mapping
            dopredraw (pathoutlines, chart, now);
            dopredraw (gameoutlines, chart, now);

            // draw all backgrounds
            for (Outline outline : pathoutlines) {
                if (outline.isFilteredOn (now)) {
                    outline.draw (canvas, bgpaint);
                }
            }
            for (Outline outline : gameoutlines) {
                if (outline.isFilteredOn (now)) {
                    outline.draw (canvas, bgpaint);
                }
            }

            // draw non-highlighted foregrounds
            for (Outline outline : pathoutlines) {
                if (!outline.highlight && outline.isFilteredOn (now)) {
                    outline.draw (canvas, fgpaint);
                }
            }
            for (GameOut outline : gameoutlines) {
                if (!outline.highlight && outline.isFilteredOn (now)) {
                    outline.draw (canvas,
                            (outline.exptime < 0xFFFFFFFFL * 1000) ? fgpaint : blpaint);
                }
            }

            // draw highlighted foregrounds
            for (Outline outline : pathoutlines) {
                if (outline.highlight && outline.isFilteredOn (now)) {
                    outline.draw (canvas, hipaint);
                }
            }
            for (Outline outline : gameoutlines) {
                if (outline.highlight && outline.isFilteredOn (now)) {
                    outline.draw (canvas, hipaint);
                }
            }
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

    // compute paths for current latlon<->pixel mapping
    // also delete all expired TFRs
    private void dopredraw (Collection<? extends Outline> outlines, Chart2DView chart, long now)
    {
        for (Iterator<? extends Outline> it = outlines.iterator (); it.hasNext ();) {
            Outline outline = it.next ();
            if (outline.exptime < now) {
                it.remove ();
            } else if (outline.isFilteredOn (now)) {
                outline.predraw (chart);
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
        Collections.sort (touched);
        return touched;
    }

    /**
     * Either an outlined (path) TFR or a stadium (game) TFR.
     */
    public abstract class Outline implements Comparable<Outline> {
        public boolean highlight;   // draw highlighted outline
        public long efftime;        // effective time
        public long exptime;        // expiration time

        protected double cntrlat;   // centroid of area (or NaN if runt)
        protected double cntrlon;
        protected String tzname;    // local timezone (or null if unknown)

        private double elevfeet = Double.NaN;
        private short elevmetres = 0;

        public abstract void predraw (Chart2DView chart);
        public abstract void draw (Canvas canvas, Paint paint);
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

        // try to get elevation of TFR
        protected double getElevFeet ()
        {
            if (Double.isNaN (elevfeet)) {
                elevmetres = Topography.getElevMetres (cntrlat, cntrlon);
                elevfeet = elevmetres * Lib.FtPerM;
                if (elevfeet < 0.0) elevfeet = 0.0;
            }
            return (elevmetres == Topography.INVALID_ELEV) ? Double.NaN : elevfeet;
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
                    tv.append (getTZName ());
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
                    tv.append (getTZName ());
                }
            }
        }

        // get timezone name displayed in the info alert box
        private String getTZName ()
        {
            switch (tzname) {
                case "America/New_York": return "Eastern";
                case "America/Chicago": return "Central";
                case "America/Denver": return "Mountain";
                case "America/Los_Angeles": return "Pacific";
            }
            if (tzname.startsWith ("America/")) return tzname.substring (8);
            if (tzname.startsWith ("Pacific/")) return tzname.substring (8);
            return tzname;
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
     * One of these per area of multi-area TFR.
     */
    private class PathOut extends Outline implements View.OnClickListener {
        private AirChart achart;            // chart the chtpixs are for
        private boolean appears;            // appears on canvas
        private char areacode;              // area letter 'A', 'B', 'C', ...
        private Context context;
        private double northmostLat;        // northmost latitude of points
        private double southmostLat;        // southmost latitude of points
        private double eastmostLon;         // eastmost longitude of points
        private double westmostLon;         // westmost longitude of points
        private double[] canpixs;           // where last drawn on canvas
        private double[] chtpixs;           // chart pixels for the outline
        private double[] latlons;           // list of all points, first == last
        private long fetched;               // date/time fetched from internet
        private Path path = new Path ();    // draws the shape
        private PointD canpix = new PointD ();
        private String botaltcode;          // bottom altitude code "ALT 2499 FT"
        private String tfrid;               // main TFR id "0_0986"
        private String topaltcode;          // top altitude code

        /**
         * Construct by decoding XML file from FAA.
         *  Input:
         *   xpp = positioned just past <TFRAreaGroup>
         *   ident = "0_9876"
         *   now = current time (time we fetched list.html from FAA)
         *   defefftime = default effective time
         *   defexptime = default expiration time
         *   tzname = timezone the TFR is located in (might be null)
         *   areacode = 'A', 'B', 'C', ... for multi-area TFRs
         *  Output:
         *   this filled in from XML
         *   xpp = positioned just past </TFRAreaGroup>
         */
        public void readXml (XmlPullParser xpp, String ident, long now,
                             long defefftime, long defexptime,
                             String tzname, char areacode)
                throws Exception
        {
            tfrid   = ident;
            efftime = defefftime;
            exptime = defexptime;
            fetched = now;

            this.areacode = areacode;
            this.tzname   = tzname;

            double geolat = Double.NaN;
            double geolon = Double.NaN;
            double[] lllist = new double[400];
            int nlls = 0;
            botaltcode = "(code)";
            topaltcode = "(code)";
            String botaltnum = "(num)";
            String botaltuom = "(uom)";
            String topaltnum = "(num)";
            String topaltuom = "(uom)";

            // scan the XML text
            StringBuilder started = new StringBuilder ();
            started.append (" TFRAreaGroup");
            for (int eventType; (started.length () > 0) && (eventType = xpp.next ()) != XmlPullParser.END_DOCUMENT;) {
                switch (eventType) {
                    case XmlPullParser.START_TAG: {
                        started.append (' ');
                        started.append (xpp.getName ());
                        break;
                    }

                    case XmlPullParser.TEXT: {
                        String text = xpp.getText ().trim ();
                        switch (started.toString ()) {
                            case " TFRAreaGroup aseTFRArea codeDistVerLower": {
                                botaltcode = text;
                                break;
                            }
                            case " TFRAreaGroup aseTFRArea valDistVerLower": {
                                botaltnum  = text;
                                break;
                            }
                            case " TFRAreaGroup aseTFRArea uomDistVerLower": {
                                botaltuom  = text;
                                break;
                            }
                            case " TFRAreaGroup aseTFRArea codeDistVerUpper": {
                                topaltcode = text;
                                break;
                            }
                            case " TFRAreaGroup aseTFRArea valDistVerUpper": {
                                topaltnum  = text;
                                break;
                            }
                            case " TFRAreaGroup aseTFRArea uomDistVerUpper": {
                                topaltuom  = text;
                                break;
                            }

                            case " TFRAreaGroup aseTFRArea ScheduleGroup dateEffective": {
                                efftime = decodeXmlTime (text);
                                break;
                            }
                            case " TFRAreaGroup aseTFRArea ScheduleGroup dateExpire": {
                                exptime = decodeXmlTime (text);
                                break;
                            }

                            case " TFRAreaGroup abdMergedArea Avx geoLat": {
                                geolat = decodeXmlLL (text, 'N', 'S');
                                break;
                            }
                            case " TFRAreaGroup abdMergedArea Avx geoLong": {
                                geolon = decodeXmlLL (text, 'E', 'W');
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
                            case " TFRAreaGroup abdMergedArea Avx": {
                                lllist = appendll (lllist, nlls, geolat, geolon);
                                nlls  += 2;
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

            // make sure first and last lat/lon are the same
            if (nlls > 0) {
                if ((lllist[0] != lllist[nlls-2]) || (lllist[1] != lllist[nlls-1])) {
                    lllist = appendll (lllist, nlls, lllist[0], lllist[1]);
                    nlls  += 2;
                }
            }

            // save an exact sized array
            latlons = new double[nlls];
            System.arraycopy (lllist, 0, latlons, 0, nlls);

            // ALT/HEI 2499 FL/FT
            botaltcode += " " + botaltnum + " " + botaltuom;
            topaltcode += " " + topaltnum + " " + topaltuom;

            calcCenterLatLon ();

            computeLatLonLimits ();

            fillTimeZone ();
        }

        private double[] appendll (double[] lllist, int nlls, double lat, double lon)
        {
            if (nlls + 2 > lllist.length) {
                double[] newlll = new double[lllist.length*3/2];
                System.arraycopy (lllist, 0, newlll, 0, nlls);
                lllist = newlll;
            }
            lllist[nlls++] = lat;
            lllist[nlls]   = lon;
            return lllist;
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
            areacode   = result.getString (1).charAt (0);
            efftime    = result.getLong   (2) * 1000;
            exptime    = result.getLong   (3) * 1000;
            tzname     = result.getString (4);
            botaltcode = result.getString (5);
            topaltcode = result.getString (6);
            fetched    = result.getLong   (7) * 1000;
            byte[] llblob = result.getBlob (8);

            int nlls = llblob.length / 4;
            latlons = new double[nlls];

            int j = 0;
            for (int i = 0; i < nlls; i ++) {
                int ill = llblob[j++];
                ill = (ill << 8) | (llblob[j++] & 0xFF);
                ill = (ill << 8) | (llblob[j++] & 0xFF);
                ill = (ill << 8) | (llblob[j++] & 0xFF);
                latlons[i] = ill * 90.0 / 0x40000000;
            }

            calcCenterLatLon ();

            computeLatLonLimits ();

            fillTimeZone ();

            return fetched;
        }

        // calculate TFR center lat,lon
        // adapted from centroid.c
        //  Written by Joseph O'Rourke
        //  orourke@cs.smith.edu
        //  October 27, 1995
        private void calcCenterLatLon ()
        {
            int n = latlons.length - 2;
            if (n >= 6) {
                double glat = 0.0;
                double glon = 0.0;
                double alat = latlons[0];
                double alon = latlons[1];
                double blat = latlons[2];
                double blon = latlons[3];
                double areasum2 = 0.0;

                for (int i = 4; i < n;) {
                    double clat = latlons[i++];
                    double clon = latlons[i++];

                    double a2 =
                            (blat - alat) * (clon - alon) -
                            (clat - alat) * (blon - alon);

                    glat += a2 * (alat + blat + clat);
                    glon += a2 * (alon + blon + clon);
                    areasum2 += a2;

                    blat = clat;
                    blon = clon;
                }

                if (Math.abs (areasum2) >= 1.0/4096.0/4096.0) {
                    cntrlat = glat / (3.0 * areasum2);
                    cntrlon = glon / (3.0 * areasum2);
                    return;
                }
            }

            // runt
            cntrlat = Double.NaN;
            cntrlon = Double.NaN;
            latlons = nullDoubleArray;
        }

        // compute the lat/lon limits
        private void computeLatLonLimits ()
        {
            northmostLat = -90.0;
            southmostLat =  90.0;
            eastmostLon = -180.0;
            westmostLon =  180.0;
            for (int i = latlons.length; i > 0;) {
                double lon = latlons[--i];
                double lat = latlons[--i];
                if (northmostLat < lat) northmostLat = lat;
                if (southmostLat > lat) southmostLat = lat;
                eastmostLon = Lib.Eastmost (eastmostLon, lon);
                westmostLon = Lib.Westmost (westmostLon, lon);
            }
        }

        // set up some timezone for the TFR
        // if not given any or given UTC, try to get TFRs local timezone
        private void fillTimeZone ()
        {
            if ((tzname == null) || tzname.equals ("") || tzname.equals ("UTC")) {
                if (Double.isNaN (cntrlat)) {
                    tzname = null;
                } else {
                    tzname = getTimezoneAtLatLon (cntrlat, cntrlon);
                }
            }
        }

        /**
         * Write to SQL database.
         * Even write runts so we don't keep fetching them from web.
         */
        public void writeDB (SQLiteDatabase sqldb)
        {
            int nlls = latlons.length;
            byte[] llblob = new byte[nlls*4];
            int j = 0;
            for (double ll : latlons) {
                int ill = (int) Math.round (ll * (0x40000000 / 90.0));
                llblob[j++] = (byte) (ill >> 24);
                llblob[j++] = (byte) (ill >> 16);
                llblob[j++] = (byte) (ill >>  8);
                llblob[j++] = (byte)  ill;
            }

            char[] areaarr = new char[] { areacode };
            String areastr = new String (areaarr);

            ContentValues values = new ContentValues (9);
            values.put ("p_id",   tfrid);
            values.put ("p_area", areastr);
            values.put ("p_eff",  efftime / 1000);
            values.put ("p_exp",  exptime / 1000);
            values.put ("p_tz",   tzname);
            values.put ("p_bot",  botaltcode);
            values.put ("p_top",  topaltcode);
            values.put ("p_fet",  fetched / 1000);
            values.put ("p_lls",  llblob);
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
            tv2.setText (tfrid.replace ('_', '/'));
            if (! useproxy) {
                tv2.setOnClickListener (this);
                tv2.setPaintFlags (tv2.getPaintFlags () | Paint.UNDERLINE_TEXT_FLAG);
                tv2.setTextColor (Color.CYAN);
            }

            LinearLayout ll1 = new LinearLayout (ctx);
            ll1.setOrientation (LinearLayout.HORIZONTAL);
            ll1.addView (tv1);
            ll1.addView (tv2);

            if ((areacode != 'A') || otherAreaCodes ()) {
                TextView tv4 = new TextView (ctx);
                tv4.setText (" area " + areacode);
                ll1.addView (tv4);
            }

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

        // see if there are other TFRs with same tfrid but different area code
        private boolean otherAreaCodes ()
        {
            for (PathOut other : pathoutlines) {
                if (other.tfrid.equals (tfrid) && (other.areacode != areacode)) {
                    return true;
                }
            }
            return false;
        }

        // do computation for drawing
        @Override
        public void predraw (Chart2DView chart)
        {
            appears = false;
            if (! Double.isNaN (cntrlat) &&
                    Lib.LatOverlap (southmostLat, northmostLat, chart.chartView.pmap.canvasSouthLat, chart.chartView.pmap.canvasNorthLat) &&
                    Lib.LonOverlap (westmostLon, eastmostLon, chart.chartView.pmap.canvasWestLon, chart.chartView.pmap.canvasEastLon)) {
                appears = true;

                // for air charts, lambert latlon->pixel is expensive,
                // so precompute it as the outline->chart pixel mapping is constant,
                // ie, one could draw the outline in ink on a paper chart
                // changes only when the user selects a different chart
                int n = latlons.length;
                DisplayableChart dchart = chart.chartView.selectedChart;
                if (achart != dchart) {
                    if (dchart instanceof AirChart) {
                        achart = (AirChart) dchart;
                        if (chtpixs == null) chtpixs = new double[n];
                        for (int i = 0; i < n;) {
                            double lat = latlons[i];
                            double lon = latlons[i+1];
                            achart.LatLon2ChartPixelExact (lat, lon, canpix);
                            chtpixs[i++] = canpix.x;
                            chtpixs[i++] = canpix.y;
                        }
                    } else {
                        // street map, don't have latlon->chartpixel & chartpixel->canvaspixel mapping available
                        achart  = null;
                        chtpixs = null;
                    }
                }

                // compute the outline path
                // it changes if the chart is panned and/or zoomed
                if (canpixs == null) canpixs = new double[n];
                path.rewind ();
                boolean first = true;
                for (int i = 0; i < n;) {
                    if (chtpixs == null) {
                        double lat = latlons[i];
                        double lon = latlons[i+1];
                        chart.LatLon2CanPixExact (lat, lon, canpix);
                    } else {
                        double chx = chtpixs[i];
                        double chy = chtpixs[i+1];
                        achart.ChartPixel2CanPix (chx, chy, canpix);
                    }
                    if (first) path.moveTo ((float) canpix.x, (float) canpix.y);
                          else path.lineTo ((float) canpix.x, (float) canpix.y);
                    canpixs[i++] = canpix.x;
                    canpixs[i++] = canpix.y;
                    first = false;
                }
                path.close ();
            }
        }

        // draw TFR on the given canvas, highlighted or normal
        @Override
        public void draw (Canvas canvas, Paint paint)
        {
            if (appears) {
                canvas.drawPath (path, paint);
            }
        }

        // see if the given canvas x,y pixel is near the TFR outline
        @Override
        public boolean touched (double canpixx, double canpixy)
        {
            if (! appears) return false;
            double p1x = canpixs[0];
            double p1y = canpixs[1];
            int n = canpixs.length;
            for (int i = 2; i < n;) {
                double p2x = canpixs[i++];
                double p2y = canpixs[i++];
                if (Lib.distToLineSeg (canpixx, canpixy, p1x, p1y, p2x, p2y) < TOUCHDIST) return true;
                p1x = p2x;
                p1y = p2y;
            }
            return false;
        }

        // id in alert box was clicked on, open web page
        @Override
        public void onClick (View v)
        {
            String weblink = faaurl + "/" + faapfx + tfrid + ".html";
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
            String[] parts = altcode.split (" ");
            if (parts.length != 3) return altcode;
            if (parts[1].equals ("0")) return "SFC";
            switch (parts[2]) {
                case "FL": return "FL " + parts[1];
                case "FT": {
                    switch (parts[0]) {
                        case "ALT": return parts[1] + " ft MSL";
                        case "HEI": return parts[1] + " ft AGL";
                    }
                    break;
                }
            }
            return altcode;
        }

        // convert the '{ALT|HEI} number {FL|FT}' string to displayable MSL string
        // returns null if same as getAltitude()
        private String getAltMSL (String altcode)
        {
            // the only thing we convert is 'HEI number FT'
            // but number 0 always means surface
            String[] parts = altcode.split (" ");
            if (parts.length != 3) return null;
            if (parts[1].equals ("0")) return null;
            if (! parts[0].equals ("HEI")) return null;
            if (! parts[2].equals ("FT")) return null;

            double elft = getElevFeet ();
            if (Double.isNaN (elft)) return null;
            try {
                return Math.round (Double.parseDouble (parts[1]) + elft) + " ft MSL";
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }

    // decode time string in format yyyy-mm-ddThh:mm:ss
    private static long decodeXmlTime (String str)
            throws ParseException
    {
        String[] strs = str.split ("[-T:]");
        if (strs.length != 6) throw new ParseException ("bad time string " + str, -1);
        int[] nums = new int[6];
        for (int i = 0; i < 6; i ++) nums[i] = Integer.parseInt (strs[i], 10);
        return Date.UTC (nums[0] - 1900, nums[1] - 1, nums[2], nums[3], nums[4], nums[5]);
    }

    // decode latlon string in format number{pos/neg}
    private static double decodeXmlLL (String str, char pos, char neg)
    {
        int n = str.length () - 1;
        double ll = Double.parseDouble (str.substring (0, n));
        char e = str.charAt (n);
        if (e == neg) ll = - ll;
        else if (e != pos) throw new NumberFormatException ("bad latlon suffix " + str);
        return ll;
    }

    // if no timezone given for tfr, try to get one
    // returns string like "America/New_York"
    private static String getTimezoneAtLatLon (double lat, double lon)
    {
        try {
            BufferedReader br = getHttpReader (MaintView.dldir + "/tzforlatlon.php?lat=" + lat + "&lon=" + lon);
            try {
                String tzname = br.readLine ();
                if (tzname != null) tzname = tzname.trim ();
                return tzname;
            } finally {
                br.close ();
            }
        } catch (IOException ioe) {
            return null;
        }
    }

    /**
     * These TFRs are always 3000' AGL and 3nm radius circle
     */
    private class GameOut extends Outline {
        public String text;

        private double northlat;
        private double radpix;
        private PointD canpix = new PointD ();

        public GameOut (long efftime, long exptime, String text, Cursor result)
        {
            this.efftime  = efftime;
            this.exptime  = exptime;
            this.text     = text;
            this.cntrlat  = result.getDouble (2);
            this.cntrlon  = result.getDouble (3);
            this.tzname   = result.getString (4);
            this.northlat = Lib.LatHdgDist2Lat (cntrlat, 0, GAMERADNM);
        }

        @Override
        public void predraw (Chart2DView chart)
        {
            chart.LatLon2CanPixExact (northlat, cntrlon, canpix);
            double northpixy = canpix.y;

            chart.LatLon2CanPixExact (cntrlat, cntrlon, canpix);
            radpix = canpix.y - northpixy;
        }

        @Override
        public void draw (Canvas canvas, Paint paint)
        {
            canvas.drawCircle ((float) canpix.x, (float) canpix.y, (float) radpix, paint);
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
            double elft = getElevFeet ();
            if (! Double.isNaN (elft)) {
                tv.append ("\n...approx: SFC - " + Math.round (elft + GAMEAGLFT) + " ft MSL");
            }
            if (efftime < 0xFFFFFFFFL * 1000) {
                formatTimeRange (tv);
            }
            return tv;
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
                    sqldb.execSQL ("CREATE TABLE IF NOT EXISTS pathtfrs (p_id TEXT NOT NULL, " +
                            "p_area TEXT NOT NULL, p_eff INTEGER NOT NULL, p_exp INTEGER NOT NULL, " +
                            "p_tz TEXT, p_bot TEXT NOT NULL, p_top TEXT NOT NULL, " +
                            "p_fet INTEGER NOT NULL, p_lls BLOB NOT NULL, " +
                            "PRIMARY KEY (p_id, p_area))");

                    sqldb.execSQL ("CREATE INDEX IF NOT EXISTS pathtfrs_id ON pathtfrs (p_id)");

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

                    // Thu, 02 May 2013 23:03:06 GMT
                    SimpleDateFormat lmsdf = new SimpleDateFormat ("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
                    lmsdf.setTimeZone (utctz);

                    long listmod = 0;

                    while (true) {

                        // maybe we are being shut off
                        synchronized (pathlock) {
                            if (tfrFilter == OptionsView.TFR_NONE) {
                                statusline = null;
                                pathThread = null;
                                pathoutlines = new LinkedList<> ();
                                break;
                            }
                        }

                        // attempt to download new version of TFR database from FAA
                        pathouts = new LinkedList<> ();
                        now = System.currentTimeMillis ();
                        Log.i (TAG, "downloading " + faaurl + " since " + lmsdf.format (listmod));
                        try {
                            HttpURLConnection httpCon = possiblyProxiedConnection ("tfr2/list.html", listmod);
                            try {
                                int rc = httpCon.getResponseCode ();
                                Log.d (TAG, faaurl + " http status " + rc);
                                switch (rc) {

                                    // some change to web page, update database with new TFRs
                                    case 200: {
                                        BufferedReader br = new BufferedReader (new InputStreamReader (httpCon.getInputStream ()));
                                        sqldb.beginTransaction ();
                                        try {
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
                                                        if (result.moveToFirst ()) {

                                                            // if so, don't fetch XML file but just use database values
                                                            do {
                                                                PathOut tfr = new PathOut ();
                                                                tfr.readDB (result);
                                                                pathouts.add (tfr);
                                                            } while (result.moveToNext ());

                                                            // update database cuz this TFR is in current list.html
                                                            sqldb.update ("pathtfrs", updatenow, "p_id=?", det);
                                                        } else {

                                                            // if not, read XML from FAA website and write to database
                                                            // this may generate more than one PathOut
                                                            Log.i (TAG, "downloading " + faaurl + " " + detail);
                                                            readXml (pathouts, sqldb, detail, now);
                                                        }
                                                    } catch (Exception e) {
                                                        Log.w (TAG, "exception reading TFR " + detail, e);
                                                    } finally {
                                                        result.close ();
                                                    }
                                                }
                                            }

                                            // delete all old TFRs from database that aren't on FAA website anymore
                                            sqldb.delete ("pathtfrs", "p_fet<" + (now / 1000), null);

                                            // remember new list.html modified time
                                            listmod = httpCon.getHeaderFieldDate ("last-modified", 0);
                                            Log.d (TAG, faaurl + " last-modified " + lmsdf.format (listmod));

                                            // got all TFRs listed on most recent FAA webpage, commit it
                                            sqldb.setTransactionSuccessful ();
                                        } finally {
                                            sqldb.endTransaction ();
                                            br.close ();
                                        }
                                        Log.i (TAG, "download " + faaurl + " complete");

                                        // post the updated TFR list to screen
                                        fetched = now;
                                        postit (now, fetched, pathouts);
                                        break;
                                    }

                                    // list.html unchanged, just update TFRs as of ... time
                                    case 304: {
                                        fetched = now;
                                        postit (now, fetched, null);
                                        break;
                                    }

                                    // website failed
                                    default: throw new IOException ("http response code " + rc);
                                }
                            } finally {
                                httpCon.disconnect ();
                            }

                            // check FAA website again in 10 minutes
                            try { Thread.sleep (600000); } catch (InterruptedException ignored) { }
                        } catch (Exception e) {

                            // maybe no internet
                            Log.w (TAG, "exception reading " + faaurl, e);
                            // update TFRs as of ... (maybe includes date part)
                            if (fetched > 0) postit (now, fetched, null);
                            // try again in one minute
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
            statusline = "TFRs as of " + sdfout.format (fetched) + "z";
            if (pathouts != null) pathoutlines = pathouts;
            if (chartview != null) chartview.postInvalidate ();
        }

        /**
         * Construct by downloading and decoding XML file from FAA.
         * May contain multiple areas.
         * Write each area to database.
         *  Input:
         *   ident = eg "0_9876"
         *   now = time list.html was fetched
         *  Output:
         *   pathouts = PathOut object added for each area
         *   sqldb = record added for each area
         */
        private void readXml (LinkedList<PathOut> pathouts, SQLiteDatabase sqldb, String ident, long now)
                throws Exception
        {
            // start reading XML file from FAA website
            HttpURLConnection httpCon = possiblyProxiedConnection (faapfx + ident + ".xml", 0);
            try {
                int rc = httpCon.getResponseCode ();
                if (rc != 200) throw new IOException ("http response code " + rc);
                BufferedReader br = new BufferedReader (new InputStreamReader (httpCon.getInputStream ()));

                // strip first couple garbage characters off the front or XmlPullParser will puque
                while (true) {
                    br.mark (1);
                    int c = br.read ();
                    if (c < 0) throw new EOFException ("eof reading xml file");
                    if (c == '<') break;
                }
                br.reset ();

                // pass to parser
                XmlPullParser xpp = Xml.newPullParser ();
                xpp.setFeature (XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                xpp.setInput (br);

                // start with area A and we don't have timezone next
                char areacode = 'A';
                long efftime  = 0;
                long exptime  = 0xFFFFFFFFL * 1000;
                String tzname = null;

                // scan the XML text
                StringBuilder started = new StringBuilder ();
                for (int eventType = xpp.getEventType (); eventType != XmlPullParser.END_DOCUMENT; eventType = xpp.next ()) {
                    switch (eventType) {
                        case XmlPullParser.START_TAG: {
                            started.append (' ');
                            started.append (xpp.getName ());
                            switch (started.toString ()) {
                                case " XNOTAM-Update Group Add Not TfrNot TFRAreaGroup": {
                                    PathOut pathout = new PathOut ();
                                    pathout.readXml (xpp, ident, now, efftime, exptime, tzname, areacode ++);
                                    pathouts.add (pathout);
                                    pathout.writeDB (sqldb);
                                    started.delete (started.length () - " TFRAreaGroup".length (), started.length ());
                                    break;
                                }
                            }
                            break;
                        }

                        case XmlPullParser.TEXT: {
                            String text = xpp.getText ().trim ();
                            switch (started.toString ()) {
                                case " XNOTAM-Update Group Add Not dateEffective": {
                                    efftime = decodeXmlTime (text);
                                    break;
                                }
                                case " XNOTAM-Update Group Add Not dateExpire": {
                                    exptime = decodeXmlTime (text);
                                    break;
                                }
                                case " XNOTAM-Update Group Add Not codeTimeZone": {
                                    tzname = text;
                                    break;
                                }
                            }
                            break;
                        }

                        case XmlPullParser.END_TAG: {
                            String ending = " " + xpp.getName ();
                            String startd = started.toString ();
                            if (! startd.endsWith (ending)) throw new Exception ("xml <" + startd + "> ends with </" + ending + ">");
                            started.delete (started.length () - ending.length (), started.length ());
                            break;
                        }
                    }
                }
            } finally {
                httpCon.disconnect ();
            }
        }
    }

    // read a page from FAA website
    //  urlfile = name of file to read
    //  ifmodsince = 0: read whatever version
    //            else: read only if modified after this time
    private HttpURLConnection possiblyProxiedConnection (String urlfile, long ifmodsince)
            throws IOException
    {
        // try direct access
        if (! useproxy) {
            URL url = new URL (faaurl + "/" + urlfile);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection ();
            try {
                httpCon.setRequestMethod ("GET");
                if (ifmodsince > 0) {
                    // Thu, 02 May 2013 23:03:06 GMT
                    SimpleDateFormat lmsdf = new SimpleDateFormat ("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
                    lmsdf.setTimeZone (utctz);
                    String lmstr = lmsdf.format (ifmodsince);
                    httpCon.setRequestProperty ("if-modified-since", lmstr);
                }
                try {
                    httpCon.connect ();
                    HttpURLConnection hc = httpCon;
                    httpCon = null;
                    return hc;
                } catch (SSLHandshakeException she) {
                    useproxy = true;
                }
            } finally {
                if (httpCon != null) httpCon.disconnect ();
            }
        }

        // ssl handshake exception going directly to FAA website, use proxy
        String urlstr = MaintView.dldir + "/tfrfaagov.php?tfrfaaurl=" + urlfile;
        if (ifmodsince > 0) {
            urlstr += "&ifmodsince=" + (ifmodsince / 1000);
        }
        URL url = new URL (urlstr);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection ();
        try {
            httpCon.setRequestMethod ("GET");
            httpCon.connect ();
            HttpURLConnection hc = httpCon;
            httpCon = null;
            return hc;
        } finally {
            if (httpCon != null) httpCon.disconnect ();
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
                    long now = System.currentTimeMillis ();
                    String query = "SELECT g_eff,g_desc,s_lat,s_lon,s_tz,s_name,g_exp" +
                            " FROM stadiums LEFT JOIN gametfrs ON g_stadium=s_name" +
                            " WHERE s_lat<" + gnlat + " AND s_lat>" + gslat +
                            " AND s_lon<" + gelon + " AND s_lon>" + gwlon;
                    SQLiteDatabase sqldb = SQLiteDatabase.openDatabase (
                            gtpermfile.getPath (), null,
                            SQLiteDatabase.NO_LOCALIZED_COLLATORS | SQLiteDatabase.OPEN_READONLY);
                    try {
                        Cursor result = sqldb.rawQuery (query, null);
                        try {
                            if (result.moveToFirst ()) do {
                                // null values if stadium without any event scheduled
                                // so say event happens way far off in the future
                                long efftime = (result.isNull (0) ? 0xFFFFFFFFL : result.getLong (0)) * 1000;
                                long exptime = (result.isNull (6) ? 0xFFFFFFFFL : result.getLong (6)) * 1000;
                                // only do it if not expired
                                if (exptime > now) {
                                    // only do earliest per stadium
                                    String stadium = result.getString (5);
                                    GameOut oldgo = outlines.get (stadium);
                                    if ((oldgo == null) || (efftime < oldgo.efftime)) {
                                        String text = stadium;
                                        // add team names if available for verification
                                        if (! result.isNull (1)) {
                                            text += "\n" + result.getString (1);
                                        }
                                        // add TFR to list, possibly replacing a later one at this stadium
                                        GameOut gameout = new GameOut (efftime, exptime, text, result);
                                        outlines.put (stadium, gameout);
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
                // wait a minute before doing anything to retry download
                try { Thread.sleep (60000); } catch (InterruptedException ignored) { }
                // some error reading database, delete and get another downloaded
                // do it locked to prevent draw() from starting us up again
                synchronized (gamelock) {
                    Lib.Ignored (gtpermfile.delete ());
                    gameThread = null;
                    if (gupdThread != null) gupdThread.interrupt ();
                }
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
                        gupdThread   = null;
                        gamenorthlat = 0.0;
                        gamesouthlat = 0.0;
                        gameeastlon  = 0.0;
                        gamewestlon  = 0.0;
                        gameoutlines = new LinkedList<> ();
                        break;
                    }
                }

                int nextrun = 3600000;
                try {
                    long now = System.currentTimeMillis ();

                    // see if we already have an up-to-date database
                    // they are created a little before 00:00z each day
                    boolean needupdate = true;
                    if (gtpermfile.exists ()) {
                        SQLiteDatabase sqldb = SQLiteDatabase.openDatabase (gtpermfile.toString (), null, SQLiteDatabase.OPEN_READONLY);
                        try {
                            Cursor result = sqldb.query ("asof", new String[] { "as_of" }, null, null, null, null, null);
                            try {
                                if (result.moveToFirst ()) {
                                    long asof = result.getLong (0);
                                    needupdate = asof / 86400 < now / 86400000;
                                }
                            } finally {
                                result.close ();
                            }
                        } finally {
                            sqldb.close ();
                        }
                    }

                    if (needupdate) {

                        // we don't have the one for today, try to download it
                        // it's quite possible we don't have internet connection
                        Log.i (TAG, "fetching latest gametfrs4.db");
                        GZIPInputStream gis = new GZIPInputStream (getHttpStream (MaintView.dldir + "/getgametfrs4.php"));
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
                        if (! gttempfile.renameTo (gtpermfile)) throw new IOException ("error renaming gametfrs4.db.tmp file");
                        Log.i (TAG, "fetch gametfrs4.db complete");

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
                    }
                    nextrun = 86400000;
                } catch (Exception e) {

                    // probably no internet connection
                    Log.w (TAG, "exception updating gametfrs4.db", e);
                }

                // schedule to run one hour from now if failed to update
                // ... or during first hour of next day if update was ok
                long now = System.currentTimeMillis ();
                long del = nextrun - now % nextrun + now % 3600000;
                Log.d (TAG, "gupdthread next update " + Lib.TimeStringUTC (now + del));
                try { Thread.sleep (del); } catch (InterruptedException ignored) { }
            }
        }
    }
}
