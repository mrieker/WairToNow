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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.rongi.rotate_layout.layout.RotateLayout;

/**
 * Display a menu to download database and charts.
 */
public class MaintView
        extends LinearLayout
        implements Handler.Callback, WairToNow.CanBeMainView, DialogInterface.OnCancelListener,
        CompoundButton.OnCheckedChangeListener {
    public  final static String TAG = "WairToNow";
    public  final static String dldir = "http://toutatis.nii.net/WairToNow";

    private Category enrCategory;
    private Category helCategory;
    private Category miscCategory;
    private Category plateCategory;
    private Category secCategory;
    private Category tacCategory;
    private Category wacCategory;
    private DownloadButton downloadButton;
    private DownloadThread downloadThread;
    private Handler maintViewHandler;
    private HashMap<String,String[]> chartedLims;
    public  LinkedList<Downloadable> allDownloadables = new LinkedList<> ();
    private WairToNow wairToNow;
    private ProgressDialog downloadProgress;
    private ScrollView itemsScrollView;
    private StateMapView stateMapView;
    private TextView runwayDiagramDownloadStatus;
    private Thread guiThread;
    private UnloadButton unloadButton;
    private UnloadThread unloadThread;
    private volatile boolean downloadCancelled;
    private WaypointsCheckBox waypointsCheckBox;

    private static final int MaintViewHandlerWhat_OPENDLPROG   = 0;
    private static final int MaintViewHandlerWhat_UPDATEDLPROG = 1;
    private static final int MaintViewHandlerWhat_CLOSEDLPROG  = 2;
    private static final int MaintViewHandlerWhat_DLCOMPLETE   = 3;
    private static final int MaintViewHandlerWhat_UNCHECKBOX   = 4;
    private static final int MaintViewHandlerWhat_HAVENEWCHART = 5;
    private static final int MaintViewHandlerWhat_REMUNLDCHART = 6;
    private static final int MaintViewHandlerWhat_UNLDDONE     = 7;
    private static final int MaintViewHandlerWhat_DLERROR      = 8;
    private static final int MaintViewHandlerWhat_UPDRWDGMDLST = 9;

    private final static String[] columns_count_rp_faaid = new String[] { "COUNT(rp_faaid)" };
    private final static String[] columns_pl_state       = new String[] { "pl_state" };
    private final static String[] columns_name           = new String[] { "name" };
    private final static String[] columns_gr_bmpixx      = new String[] { "gr_bmpixx" };
    private final static String[] columns_pl_faaid       = new String[] { "pl_faaid" };

    public MaintView (WairToNow ctx)
            throws IOException
    {
        super (ctx);
        wairToNow = ctx;

        guiThread = Thread.currentThread ();

        Lib.Ignored (new File (WairToNow.dbdir + "/charts").mkdirs ());
        Lib.Ignored (new File (WairToNow.dbdir + "/datums").mkdirs ());

        maintViewHandler = new Handler (this);

        setOrientation (VERTICAL);

        TextView tv1 = new TextView (ctx);
        tv1.setText ("Chart Maint");
        wairToNow.SetTextSize (tv1);
        addView (tv1);

        downloadButton = new DownloadButton (ctx);
        addView (downloadButton);

        unloadButton = new UnloadButton (ctx);
        addView (unloadButton);

        miscCategory  = new Category ("Misc");
        plateCategory = new Category ("Plates");
        enrCategory   = new Category ("ENR");
        helCategory   = new Category ("HEL");
        secCategory   = new Category ("SEC");
        tacCategory   = new Category ("TAC");
        wacCategory   = new Category ("WAC");

        LinearLayout ll1 = new LinearLayout (ctx);
        ll1.setOrientation (LinearLayout.HORIZONTAL);
        ll1.addView (miscCategory.selButton);
        ll1.addView (plateCategory.selButton);
        ll1.addView (enrCategory.selButton);
        ll1.addView (helCategory.selButton);
        ll1.addView (secCategory.selButton);
        ll1.addView (tacCategory.selButton);
        ll1.addView (wacCategory.selButton);

        HorizontalScrollView hs1 = new HorizontalScrollView (ctx);
        hs1.addView (ll1);
        addView (hs1);

        itemsScrollView = new ScrollView (ctx);
        addView (itemsScrollView);

        waypointsCheckBox = new WaypointsCheckBox ();
        miscCategory.addView (waypointsCheckBox);

        runwayDiagramDownloadStatus = new TextView (ctx);
        wairToNow.SetTextSize (runwayDiagramDownloadStatus);

        stateMapView = new StateMapView ();

        plateCategory.addView (runwayDiagramDownloadStatus);
        plateCategory.addView (stateMapView);

        enrCategory.addView (new ChartDiagView (R.drawable.low_index_us));
        secCategory.addView (new ChartDiagView (R.drawable.faa_sec_diag));
        wacCategory.addView (new ChartDiagView (R.drawable.faa_wac_diag));

        GetChartNames ();

        miscCategory.onClick (null);
    }

    private void GetChartNames ()
            throws IOException
    {
        /*
         * Read pixel and lat/lon limits that tell where the legend areas are.
         */
        chartedLims = new HashMap<> ();
        FileReader limFileReader;
        try {
            limFileReader = new FileReader (WairToNow.dbdir + "/chartedlims.csv");
        } catch (FileNotFoundException fnfe) {
            DownloadChartedLimsCSV ();
            limFileReader = new FileReader (WairToNow.dbdir + "/chartedlims.csv");
        }
        BufferedReader limBufferedReader = new BufferedReader (limFileReader, 4096);
        String limLine;
        while ((limLine = limBufferedReader.readLine ()) != null) {
            String[] limParts = Lib.QuotedCSVSplit (limLine);
            chartedLims.put (limParts[limParts.length-1], limParts);
        }
        limBufferedReader.close ();

        /*
         * Read all possible chart names from chartlimits.csv.
         * It is an aggregation of all charts/<undername>.csv files on the server.
         * It maps the lat/lon <-> chart pixels.
         */
        HashMap<String,PossibleChart> possibleCharts = new HashMap<> ();
        FileReader csvFileReader;
        try {
            csvFileReader = new FileReader (WairToNow.dbdir + "/chartlimits.csv");
        } catch (FileNotFoundException fnfe) {
            DownloadChartLimitsCSV ();
            csvFileReader = new FileReader (WairToNow.dbdir + "/chartlimits.csv");
        }
        BufferedReader csvReader = new BufferedReader (csvFileReader, 4096);
        String csvLine;
        while ((csvLine = csvReader.readLine ()) != null) {
            PossibleChart chart = new PossibleChart (csvLine);
            chart.enddate = 0;  // cuz we don't yet know if it is downloaded
            if (!possibleCharts.containsKey (chart.spacename)) {
                possibleCharts.put (chart.spacename, chart);
                if (chart.spacename.contains ("TAC")) {
                    tacCategory.addView (chart);
                } else if (chart.spacename.contains ("WAC")) {
                    wacCategory.addView (chart);
                } else if (chart.spacename.contains ("ENR")) {
                    enrCategory.addView (chart);
                } else if (chart.spacename.contains ("HEL")) {
                    helCategory.addView (chart);
                } else {
                    secCategory.addView (chart);
                }
            }
        }
        csvReader.close ();

        /*
         * Read all downloaded charts from charts/<undername>.csv.
         * The values therein may be different than in chartlimits.csv
         * so update the in-memory values correspondingly.
         * This also sets the chart's enddate to indicate the chart
         * has been downloaded.
         */
        File[] chartfiles = new File (WairToNow.dbdir + "/charts").listFiles ();
        for (PossibleChart pc : possibleCharts.values ()) {
            String undername = pc.GetSpaceName ().replace (' ', '_') + "_";
            File csvfile = null;
            for (File f : chartfiles) {
                if (f.getName ().startsWith (undername) && f.getName ().endsWith (".csv")) {
                    csvfile = f;
                    break;
                }
            }
            if (csvfile != null) {
                try {
                    BufferedReader csvreader = new BufferedReader (new FileReader (csvfile), 256);
                    try {
                        String csvline = csvreader.readLine ();
                        pc.ParseChartLimitsLine (csvline);
                    } finally {
                        csvreader.close ();
                    }
                } catch (FileNotFoundException fnfe) {
                    Lib.Ignored ();
                } catch (IOException ioe) {
                    Log.e (TAG, "error reading " + csvfile.getAbsolutePath (), ioe);
                }
            }
        }
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Maint";
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
     * This screen is about to become active.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        // update color based on current date
        for (Downloadable dcb : allDownloadables) {
            UpdateDCBsLinkText (dcb);
        }

        // force portrait, ie, disallow automatic flipping so it won't restart the app
        // in the middle of downloading some chart just because the screen got tilted.
        wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // maybe list/map mode changed for plate display so rebuild buttons
        stateMapView.Rebuild ();

        // make sure runway diagram download status is up to date
        UpdateRunwayDiagramDownloadStatus ();
    }

    /**
     * This screen is no longer current so close bitmaps to conserve memory.
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
     * Process messages sent to MaintViewHandler.
     */
    @Override
    public boolean handleMessage (Message msg)
    {
        switch (msg.what) {
            case MaintViewHandlerWhat_OPENDLPROG: {
                if (downloadProgress != null) {
                    downloadProgress.dismiss ();
                }
                downloadProgress = new ProgressDialog (wairToNow);
                downloadProgress.setProgressStyle (ProgressDialog.STYLE_HORIZONTAL);
                downloadProgress.setOnCancelListener (this);
                downloadProgress.setTitle (msg.obj.toString ());
                downloadProgress.setMax (msg.arg1);
                downloadProgress.setProgress (msg.arg2);
                downloadProgress.show ();
                break;
            }
            case MaintViewHandlerWhat_UPDATEDLPROG: {
                if (downloadProgress != null) {
                    downloadProgress.setProgress (msg.arg1);
                }
                break;
            }
            case MaintViewHandlerWhat_CLOSEDLPROG: {
                if (downloadProgress != null) {
                    downloadProgress.dismiss ();
                    downloadProgress = null;
                }
                break;
            }
            case MaintViewHandlerWhat_DLCOMPLETE: {
                downloadButton.UpdateEnabled ();
                unloadButton.UpdateEnabled ();
                wairToNow.chartView.DiscoverAirChartFiles ();

                // maybe some database was marked for delete in the download thread
                // (as a result of an upgrade, we delete old database files)
                // so close our handle so that it will actually delete
                SQLiteDBs.CloseAll ();
                break;
            }
            case MaintViewHandlerWhat_UNCHECKBOX: {
                ((Downloadable)msg.obj).UncheckBox ();
                break;
            }
            case MaintViewHandlerWhat_HAVENEWCHART: {
                HaveNewChart hnc = (HaveNewChart)msg.obj;
                try {
                    hnc.dcb.DownloadComplete (hnc.csvName);
                } catch (IOException e) {
                    Log.e (TAG, "can't open downloaded charts", e);
                }
                break;
            }
            case MaintViewHandlerWhat_REMUNLDCHART: {
                Downloadable dcb = (Downloadable) msg.obj;
                dcb.RemovedDownloadedChart ();
                UpdateDCBsLinkText (dcb);
                break;
            }
            case MaintViewHandlerWhat_UNLDDONE: {
                if (unloadButton.unloadAlertDialog != null) {
                    unloadButton.unloadAlertDialog.dismiss ();
                    unloadButton.unloadAlertDialog = null;
                }
                wairToNow.chartView.DiscoverAirChartFiles ();

                // maybe some database was marked for delete in the unload thread
                // so close our handle so that it will actually delete
                SQLiteDBs.CloseAll ();
                break;
            }
            case MaintViewHandlerWhat_DLERROR: {
                AlertDialog.Builder builder = new AlertDialog.Builder (wairToNow);
                builder.setTitle ("Error downloading files");
                builder.setMessage (msg.obj.toString ());
                builder.setNegativeButton ("Cancel", new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int which)
                    {
                        dialog.dismiss ();
                    }
                });
                AlertDialog dialog = builder.create ();
                dialog.show();
                break;
            }
            case MaintViewHandlerWhat_UPDRWDGMDLST: {
                UpdateRunwayDiagramDownloadStatus ();
                break;
            }
        }
        return true;
    }

    /**
     * Back button was pressed while dialog box was up.
     * Cancel the download where it is.
     */
    @Override // DialogInterface.OnCancelListener
    public void onCancel (DialogInterface dialog)
    {
        downloadCancelled = true;
    }

    /**
     * The 'Plate' category page shows how many runway background tiles we have yet to download.
     */
    public void UpdateRunwayDiagramDownloadStatus ()
    {
        /*
         * Via handler if not the GUI thread cuz we are going to update a GUI element.
         */
        if (Thread.currentThread () != guiThread) {
            maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_UPDRWDGMDLST);
            return;
        }

        /*
         * Count the records in the rwyprealoads table, as it is updated as tiles are downloaded.
         */
        int expdate = MaintView.GetPlatesExpDate ();
        String dbname = "cycles28_" + expdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
        if (sqldb != null) {
            if (sqldb.tableExists ("rwypreloads")) {
                Cursor cursor = sqldb.query (
                        "rwypreloads", columns_count_rp_faaid,
                        null, null, null, null, null, null);
                try {
                    int n = 0;
                    if (cursor.moveToFirst ()) {
                        n = cursor.getInt (0);
                    }

                    /*
                     * Update text box with number of records.
                     */
                    if (n == 0) {
                        runwayDiagramDownloadStatus.setText ("");
                        runwayDiagramDownloadStatus.setVisibility (GONE);
                    } else {
                        String s = (n == 1) ? "" : "s";
                        runwayDiagramDownloadStatus.setText ("  " + n + " remaining runway diagram background" + s + " to download");
                        runwayDiagramDownloadStatus.setVisibility (VISIBLE);
                    }
                } finally {
                    cursor.close ();
                }
            }
        }
    }

    /**
     * Category of charts/files that can be downloaded.
     */
    private class Category extends LinearLayout implements OnClickListener {
        public Button selButton;

        public Category (String name)
        {
            super (wairToNow);
            setOrientation (VERTICAL);
            selButton = new Button (wairToNow);
            selButton.setOnClickListener (this);
            selButton.setText (name);
            wairToNow.SetTextSize (selButton);
        }

        public void onClick (View v)
        {
            miscCategory.selButton.setTextColor  (Color.BLACK);
            plateCategory.selButton.setTextColor (Color.BLACK);
            enrCategory.selButton.setTextColor   (Color.BLACK);
            helCategory.selButton.setTextColor   (Color.BLACK);
            secCategory.selButton.setTextColor   (Color.BLACK);
            tacCategory.selButton.setTextColor   (Color.BLACK);
            wacCategory.selButton.setTextColor   (Color.BLACK);

            selButton.setTextColor (Color.RED);

            itemsScrollView.removeAllViews ();
            itemsScrollView.addView (this);
        }
    }

    /**
     * Update the displayed link text and color based on what charts we actually
     * have completely and successfully downloaded pertaining to this PossibleChart.
     */
    private void UpdateDCBsLinkText (Downloadable dcb)
    {
        /*
         * If none at all, display link in white with a message.
         */
        int enddate = dcb.GetEndDate ();
        if (enddate == 0) {
            dcb.UpdateSingleLinkText (Color.WHITE, "not downloaded");
            return;
        }

        /*
         * Use an appropriate color
         */
        int textColor = DownloadLinkColor (enddate);
        dcb.UpdateSingleLinkText (textColor, "valid to " + enddate);
    }

    /**
     * Use an appropriate color:
     *    GREEN  : good for at least 10 more days
     *    YELLOW : expires within 10 days
     *    RED    : expires in less than 1 day
     */
    private static int DownloadLinkColor (int enddate)
    {
        GregorianCalendar now = new GregorianCalendar ();
        GregorianCalendar end = new GregorianCalendar (enddate / 10000, (enddate / 100) % 100 - 1, enddate % 100);
        int textColor = Color.RED;
        if (end.after (now)) {
            textColor = Color.YELLOW;
            now.add (Calendar.DAY_OF_YEAR, 10);
            if (end.after (now)) {
                textColor = Color.GREEN;
            }
        }
        return textColor;
    }

    /**
     * Called back when the download checkbox is checked or unchecked.
     */
    @Override  // CompoundButton.OnCheckedChangeListener
    public void onCheckedChanged (CompoundButton buttonView, boolean isChecked)
    {
        downloadButton.UpdateEnabled ();
        unloadButton.UpdateEnabled ();
    }

    /**
     * Anything we want to be able to download implements this interface.
     * And its constructor must add the instance to allDownloadables.
     * And the checkbox itself must setOnChedkedChangeListener (MaintView.this);
     */
    public interface Downloadable {
        String GetSpaceName ();
        boolean IsChecked ();
        void UncheckBox ();
        int GetEndDate ();
        boolean HasSomeUnloadableCharts ();
        void DownloadComplete (String csvName) throws IOException;
        void RemovedDownloadedChart ();
        void UpdateSingleLinkText (int c, String t);
    }

    /**
     * These download checkboxes are a checkbox followed by a text string.
     */
    private abstract class DownloadCheckBox extends LinearLayout implements Downloadable {
        protected CheckBox checkBox;    // checkbox to enable {down,un}loading
        protected int      enddate;     // expiration date yyyymmdd or 0 if not downloaded
        protected String   spacename;   // name of chart with spaces (not underscores)
        protected TextView linkText;    // displays chart name and expiration date strings

        public DownloadCheckBox ()
        {
            super (wairToNow);
            enddate = 0;
            setOrientation (HORIZONTAL);
            checkBox = new CheckBox (wairToNow);
            checkBox.setOnCheckedChangeListener (MaintView.this);
            addView (checkBox);
            linkText = new TextView (wairToNow);
            wairToNow.SetTextSize (linkText);
            addView (linkText);
            allDownloadables.addLast (this);
        }

        /**
         * Downloadable implementation.
         */
        public String GetSpaceName () { return spacename; }
        public boolean IsChecked () { return checkBox.isChecked (); }
        public void UncheckBox () { checkBox.setChecked (false); }
        public int GetEndDate () { return enddate; }

        public abstract boolean HasSomeUnloadableCharts ();
        public abstract void DownloadComplete (String csvName) throws IOException;

        /**
         * The named chart has been removed from list of downloaded charts.
         */
        @Override  // DownloadCheckBox
        public void RemovedDownloadedChart ()
        {
            // nothing downloaded any more
            enddate = 0;
        }

        public void UpdateSingleLinkText (int c, String t)
        {
            linkText.setTextColor (c);
            linkText.setText (spacename + ": " + t);
        }
    }

    /**
     * Enable download waypoints files.
     * datums/airports_<expdate>.csv
     * datums/fixes_<expdate>.csv
     * datums/localizers_<expdate>.csv
     * datums/navaids_<expdate>.csv
     * datums/runways_<expdate>.csv
     */
    private class WaypointsCheckBox extends DownloadCheckBox {
        public WaypointsCheckBox ()
        {
            spacename = "Waypoints";
            enddate = GetWaypointExpDate ();
            UpdateDCBsLinkText (this);
        }

        /**
         * We don't allow unloading the waypoint files.
         */
        @Override  // DownloadCheckBox
        public boolean HasSomeUnloadableCharts () { return false; }

        /**
         * Waypoint file download has completed.
         * @param csvName = "datums/airports_<expdate>.csv" etc
         */
        @Override  // DownloadCheckBox
        public void DownloadComplete (String csvName)
        { }

        @Override  // DownloadCheckBox
        public void UncheckBox ()
        {
            super.UncheckBox ();
            enddate = GetWaypointExpDate ();
            UpdateDCBsLinkText (this);
            wairToNow.waypointView1.waypointsWithin.clear ();
            wairToNow.waypointView2.waypointsWithin.clear ();
        }
    }

    /**
     * Get name of current valid waypoint files (56-day cycle).
     * @return 0: no such files; else: expiration date of files
     */
    public static int GetWaypointExpDate ()
    {
        // we need all of the cycles56_<expdate>.db with airports, fixes, localizers, navaids and runways tables

        int enddate_database = 0;

        String[] dbnames = SQLiteDBs.Enumerate ();
        for (String dbname : dbnames) {
            if (dbname.startsWith ("cycles56_") && dbname.endsWith (".db")) {
                try {
                    int i = Integer.parseInt (dbname.substring (9, dbname.length () - 3));
                    if (enddate_database < i) {
                        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                        if ((sqldb != null) &&
                                sqldb.tableExists ("airports") &&
                                sqldb.tableExists ("fixes") &&
                                sqldb.tableExists ("localizers") &&
                                sqldb.tableExists ("navaids") &&
                                sqldb.tableExists ("runways")) {
                            enddate_database = i;
                        }
                    }
                } catch (NumberFormatException nfe) {
                    Lib.Ignored ();
                }
            }
        }

        return enddate_database;
    }

    /**
     * One of these per possible chart, ie, that we can possibly handle.
     * There is one of these per record in chartlimits.csv.
     * Non-zero expdate indicates the chart is currently downloaded.
     */
    private static ThreadLocal<LatLon> llPerThread = new ThreadLocal<LatLon> () {
        @Override protected LatLon initialValue ()
        {
            return new LatLon ();
        }
    };
    private static ThreadLocal<Point> ptPerThread = new ThreadLocal<Point> () {
        @Override protected Point initialValue ()
        {
            return new Point ();
        }
    };
    public class PossibleChart extends DownloadCheckBox {
        private float centerLat, centerLon;
        private float chartedEastLon, chartedWestLon;
        private float chartedSouthLat, chartedNorthLat;
        private float corrEasting, corrNorthing;
        private float lamb_F, lamb_l0, lamb_n, lamb_p0;
        private float radius, stanPar1, stanPar2;
        private float tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f;
        public  int autoOrder;
        private int chartedEastPix, chartedWestPix;
        private int chartedSouthPix, chartedNorthPix;
        public  int height, width;  // pixel width & height of this chart including legend areas

        /**
         * Constructor
         * @param csvLine = line from chartlimits.csv
         */
        public PossibleChart (String csvLine)
        {
            ParseChartLimitsLine (csvLine);
        }

        public void ParseChartLimitsLine (String csvLine)
        {
            String[] values = Lib.QuotedCSVSplit (csvLine);
            centerLat = Float.parseFloat (values[0]);
            centerLon = Float.parseFloat (values[1]);
            stanPar1  = Float.parseFloat (values[2]);
            stanPar2  = Float.parseFloat (values[3]);
            width     = Integer.parseInt (values[ 4]);
            height    = Integer.parseInt (values[ 5]);
            radius    = Float.parseFloat (values[6]);
            tfw_a     = Float.parseFloat (values[7]);
            tfw_b     = Float.parseFloat (values[8]);
            tfw_c     = Float.parseFloat (values[9]);
            tfw_d     = Float.parseFloat (values[10]);
            tfw_e     = Float.parseFloat (values[11]);
            tfw_f     = Float.parseFloat (values[12]);
            enddate   = Integer.parseInt (values[14]);
            spacename =                   values[15];  // eg, "New York SEC 87"
            int i = spacename.lastIndexOf (' ');       // remove revnum from the end
            if (i >= 0) spacename = spacename.substring (0, i);

            /*
             * Most helicopter charts don't have a published expiration date,
             * so we get a zero for enddate.  Make it something non-zero so
             * we don't confuse it with an unloaded chart.
             */
            if (enddate == 0) enddate = 99999999;

            /*
             * Compute Lambert Conformal Projection parameters.
             *
             * Map Projections -- A Working Manual
             * John P. Snyder
             * US Geological Survey Professional Paper 1395
             * http://pubs.usgs.gov/pp/1395/report.pdf
             * see pages viii, v107, v295
             */
            float lamb_o0 = centerLat / 180.0F * Mathf.PI;
            float lamb_o1 = stanPar1 / 180.0F * Mathf.PI;
            float lamb_o2 = stanPar2 / 180.0F * Mathf.PI;
            lamb_l0 = centerLon / 180.0F * Mathf.PI;
            lamb_n  = (float) (Math.log(Math.cos(lamb_o1)/Math.cos(lamb_o2))/Math.log(Math.tan(Math.PI/4+lamb_o2/2)/Math.tan(Math.PI/4+lamb_o1/2)));
            lamb_F  = (float) ((Math.cos(lamb_o1)*Math.pow(Math.tan(Math.PI/4+lamb_o1/2),lamb_n))/lamb_n);
            lamb_p0 = (float) (radius*lamb_F/Math.pow(Math.tan(Math.PI/4+lamb_o0/2),lamb_n));

            /*
             * Determine limits of charted (non-legend) area.
             * There are both lat/lon and pixel limits.
             * A point must be within both sets of limits to be considered to be in charted area.
             */
            // pixel defaults are just the whole chart size
            chartedEastPix  = width;
            chartedWestPix  = 0;
            chartedNorthPix = 0;
            chartedSouthPix = height;

            // we also set up easting/northing correction factors
            corrEasting  = 1.0F;
            corrNorthing = 1.0F;

            // check out northwest corner
            LatLon ll = new LatLon ();
            Pixel2LatLon (0, 0, ll);
            chartedNorthLat = ll.lat;
            chartedWestLon  = ll.lon;

            // check out northeast corner
            Pixel2LatLon (width, 0, ll);
            if (chartedNorthLat < ll.lat) chartedNorthLat = ll.lat;
            chartedEastLon  = ll.lon;

            // check out southwest corner
            Pixel2LatLon (0, height, ll);
            chartedSouthLat = ll.lat;
            chartedWestLon  = Lib.Westmost (chartedWestLon, ll.lon);

            // check out southeast corner
            Pixel2LatLon (width, height, ll);
            if (chartedSouthLat > ll.lat) chartedSouthLat = ll.lat;
            chartedEastLon  = Lib.Eastmost (chartedEastLon, ll.lon);

            // values from chartedlims.csv override the defaults
            String[] limParts = chartedLims.get (spacename);
            if (limParts != null) {
                for (i = limParts.length - 1; -- i >= 0;) {
                    String limPart = limParts[i];
                    if (limPart.startsWith ("e")) {
                        corrEasting = Float.parseFloat (limPart.substring (1));
                        continue;
                    }
                    if (limPart.startsWith ("n")) {
                        corrNorthing = Float.parseFloat (limPart.substring (1));
                        continue;
                    }
                    if (limPart.startsWith ("@")) {
                        autoOrder = Integer.parseInt (limPart.substring (1));
                        continue;
                    }
                    char dir = limPart.charAt (limPart.length () - 1);
                    if (limPart.charAt (0) == '#') {
                        int val = Integer.parseInt (limPart.substring (1, limPart.length () - 1));
                        switch (dir) {
                            case 'E': chartedEastPix  = val; break;
                            case 'N': chartedNorthPix = val; break;
                            case 'S': chartedSouthPix = val; break;
                            case 'W': chartedWestPix  = val; break;
                        }
                    } else {
                        float val = ParseLatLon (limPart.substring (0, limPart.length () - 1));
                        switch (dir) {
                            case 'E': chartedEastLon  = val; break;
                            case 'N': chartedNorthLat = val; break;
                            case 'S': chartedSouthLat = val; break;
                            case 'W': chartedWestLon  = val; break;
                        }
                    }
                }
            }

            // wrap east limit to be just east of west limit
            while (chartedEastLon < chartedWestLon) chartedEastLon += 360.0;
            while (chartedEastLon >= chartedWestLon + 360.0) chartedEastLon -= 360.0;

            /*
             * Update link text color in case enddate changed.
             */
            UpdateDCBsLinkText (this);
        }

        /**
         * Given a lat/lon, compute pixel within the chart
         * @param lat = latitude within the chart
         * @param lon = longitude within the chart
         * @param p = where to return corresponding pixel
         * @return p filled in
         */
        public boolean LatLon2Pixel (float lat, float lon, Point p)
        {
            /*
             * Calculate number of metres east of Longitude_of_Central_Meridian
             * and metres north of Latitude_of_Projection_Origin using Lambert
             * Conformal Projection formulae.
             */
            float lamb_o   = lat / 180.0F * Mathf.PI;
            float lamb_l   = lon / 180.0F * Mathf.PI;
            while (lamb_l - lamb_l0 < -Mathf.PI) lamb_l += 2 * Mathf.PI;
            while (lamb_l - lamb_l0 >= Mathf.PI) lamb_l -= 2 * Mathf.PI;
            float lamb_p   = (float) (radius * lamb_F / Math.pow (Math.tan (Math.PI/4 + lamb_o/2), lamb_n));
            float lamb_t   = lamb_n * (lamb_l - lamb_l0);
            float easting  = lamb_p * Mathf.sin (lamb_t);
            float northing = lamb_p0 - lamb_p * Mathf.cos (lamb_t);

            // CAP grid lines appear a bit narrowly on these charts
            // so set corrEasting > 1.0 to stretch them out horizontally
            easting  *= corrEasting;

            // likewise with northing
            northing *= corrNorthing;

            /*
             * Compute how far north,east of upper left corner of whole image.
             */
            northing -= tfw_f;
            easting  -= tfw_e;

            /*
             * Compute corresponding image pixel number.
             *
             *  lon = tfw_a * xpix + tfw_b * ypix + tfw_c
             *  lat = tfw_d * xpix + tfw_e * ypix + tfw_f
             */
            float numer = tfw_d * easting - tfw_c * northing;
            float denom = tfw_a * tfw_d - tfw_b * tfw_c;
            int x = (int)(numer / denom);

            numer = tfw_a * northing - tfw_b * easting;
            int y = (int)(numer / denom);

            if (p != null) {
                p.x = x;
                p.y = y;
            }

            return (x >= 0) && (x < width) && (y >= 0) && (y < height);
        }

        /**
         * Given a pixel within the chart, compute corresponding lat/lon
         * @param x = pixel within the entire side of the sectional
         * @param y = pixel within the entire side of the sectional
         * @param ll = where to return corresponding lat/lon
         */
        public boolean Pixel2LatLon (float x, float y, LatLon ll)
        {
            float easting, northing;

            // opposite steps of LatLon2Pixel()

            easting   = tfw_a * x + tfw_c * y + tfw_e;
            northing  = tfw_b * x + tfw_d * y + tfw_f;
            easting  /= corrEasting;
            northing /= corrNorthing;

            // From LatLon2Pixel():
            //
            //   p = r * F / (tan (pi/4 + o/2) ** n)
            //   t = n * (l - l0)
            //   E = p * sin (t)
            //   N = p0 - p * cos (t)
            //
            // Now solve for l,o:
            //
            //   E = p * sin (t)  =>  E ** 2 = (p * sin (t)) ** 2
            //   N = p0 - p * cos (t)  =>  p * cos (t) = p0 - N  =>  (p * cos (t)) ** 2 = (p0 - N) ** 2
            //   (p * sin (t)) ** 2) + (p * cos (t)) ** 2 = E ** 2 + (p0 - N) ** 2  =>  p ** 2 = E ** 2 + (p0 - N) ** 2  =>
            //       p = +/- sqrt (E ** 2 + (p0 - N) ** 2)
            //       choose +/- such that r * F / p >= 0
            //
            //   p = r * F / (tan (pi/4 + o/2) ** n)  =>  o = 2 * arctan ((r * F / p) ** (1 / n)) - pi/2
            //
            //   t = n * (l - l0)  =>  l = t / n + l0

            float lamb_p = Mathf.sqrt (Sq (easting) + Sq (lamb_p0 - northing));
            if (lamb_F < 0) lamb_p = -lamb_p;
            float lamb_t = (float) (Math.asin (easting / lamb_p));
            float lamb_o = (float) (2 * Math.atan (Math.pow (radius * lamb_F / lamb_p, 1.0 / lamb_n)) - Math.PI / 2);
            float lamb_l = lamb_t / lamb_n + lamb_l0;

            ll.lat = lamb_o / Mathf.PI * 180.0F;
            ll.lon = lamb_l / Mathf.PI * 180.0F;

            return (x >= 0) && (x < width) && (y >= 0) && (y < height);
        }

        /**
         * Return color to draw button text color.
         */
        public int DownldLinkColor ()
        {
            return DownloadLinkColor (enddate);
        }

        /**
         * See if the given lat,lon is within the chart area (not legend)
         */
        public boolean LatLonIsCharted (float lat, float lon)
        {
            /*
             * If lat/lon out of lat/lon limits, point is not in chart area
             */
            if ((lat < chartedSouthLat) || (lat > chartedNorthLat)) return false;
            while (lon < chartedWestLon) lon += 360.0;
            while (lon >= chartedWestLon + 360.0) lon -= 360.0;
            if (lon > chartedEastLon) return false;

            /*
             * It is within lat/lon limits, check pixel limits too.
             */
            Point pt = ptPerThread.get ();
            return LatLon2Pixel (lat, lon, pt) &&
                (pt.y >= chartedNorthPix) && (pt.y <= chartedSouthPix) &&
                (pt.x >= chartedWestPix)  && (pt.x <= chartedEastPix);
        }

        /**
         * See if the given pixel is within the chart area (not legend)
         */
        public boolean PixelIsCharted (int pixx, int pixy)
        {
            /*
             * If pixel is out of pixel limits, point is not in chart area
             */
            if ((pixy < chartedNorthPix) || (pixy > chartedSouthPix)) return false;
            if ((pixx < chartedWestPix)  || (pixx > chartedEastPix))  return false;

            /*
             * It is within pixel limits, check lat/lon limits too.
             */
            LatLon ll = llPerThread.get ();
            Pixel2LatLon (pixx, pixy, ll);
            if ((ll.lat < chartedSouthLat) || (ll.lat > chartedNorthLat)) return false;
            while (ll.lon < chartedWestLon) ll.lon += 360.0;
            while (ll.lon >= chartedWestLon + 360.0) ll.lon -= 360.0;
            return ll.lon <= chartedEastLon;
        }

        /**
         * Generate clipping path that excludes legend info
         * @param corners   = where to output clipping path to in bitmap pixels
         * @param leftPixel = left edge of tile in whole image
         * @param topPixel  = top edge of tile in whole image
         * @param ritePixel = right edge of tile in whole image
         * @param botPixel  = bottom edge of tile in whole image
         */
        public void GetChartedClipPath (float[] corners, int leftPixel, int topPixel, int ritePixel, int botPixel)
        {
            // clip given image pixels to charted pixel limits
            if (leftPixel < chartedWestPix) leftPixel = chartedWestPix;
            if (ritePixel > chartedEastPix) ritePixel = chartedEastPix;
            if (topPixel < chartedNorthPix) topPixel = chartedNorthPix;
            if (botPixel > chartedSouthPix) botPixel = chartedSouthPix;

            // compute lat/lon of those four corners
            LatLon ll = llPerThread.get ();

            Pixel2LatLon (leftPixel, topPixel, ll);
            float tlLat = ll.lat;
            float tlLon = ll.lon;

            Pixel2LatLon (ritePixel, topPixel, ll);
            float trLat = ll.lat;
            float trLon = ll.lon;

            Pixel2LatLon (leftPixel, botPixel, ll);
            float blLat = ll.lat;
            float blLon = ll.lon;

            Pixel2LatLon (ritePixel, botPixel, ll);
            float brLat = ll.lat;
            float brLon = ll.lon;

            // clip those four corners to the charted lat/lon limits
            if (tlLat > chartedNorthLat) tlLat = chartedNorthLat;
            if (trLat > chartedNorthLat) trLat = chartedNorthLat;
            if (blLat < chartedSouthLat) blLat = chartedSouthLat;
            if (brLat < chartedSouthLat) brLat = chartedSouthLat;

            tlLon = Lib.Eastmost (tlLon, chartedWestLon);
            blLon = Lib.Eastmost (blLon, chartedWestLon);
            trLon = Lib.Westmost (trLon, chartedEastLon);
            brLon = Lib.Westmost (brLon, chartedEastLon);

            // convert clipped lat/lon to bitmap pixels
            Point pt = ptPerThread.get ();

            LatLon2Pixel (tlLat, tlLon, pt);
            corners[0] = pt.x;
            corners[1] = pt.y;

            LatLon2Pixel (trLat, trLon, pt);
            corners[2] = pt.x;
            corners[3] = pt.y;

            LatLon2Pixel (brLat, brLon, pt);
            corners[4] = pt.x;
            corners[5] = pt.y;

            LatLon2Pixel (blLat, blLon, pt);
            corners[6] = pt.x;
            corners[7] = pt.y;
        }

        /**
         * Determine if this checkbox has some unloadable charts.
         */
        @Override  // DownloadCheckBox
        public boolean HasSomeUnloadableCharts ()
        {
            return enddate != 0;
        }

        /**
         * Download has completed for a chart.
         * @param csvName = name of .csv file for a chart
         *                  eg, "charts/New_York_86.csv"
         */
        @Override  // DownloadCheckBox
        public void DownloadComplete (String csvName)
                throws IOException
        {
            BufferedReader reader = new BufferedReader (new FileReader (WairToNow.dbdir + "/" + csvName), 256);
            try {
                String csvLine = reader.readLine ();
                ParseChartLimitsLine (csvLine);
            } finally {
                reader.close ();
            }
        }

        /**
         * Start downloading this single chart.
         */
        public void DownloadSingle ()
        {
            // pretend like the user clicked the checkbox to download this chart
            checkBox.setChecked (true);

            // pretend like the user clicked the Download button
            downloadButton.onClick (null);
        }
    }

    private static float Sq (float x) { return x*x; }

    /**
     * Parse numeric portion of lat/lon string as found in the chartedlims.csv file.
     */
    private static float ParseLatLon (String str)
    {
        int i = str.indexOf ('^');
        if (i < 0) {
            return Float.parseFloat (str);
        }
        float deg = Float.parseFloat (str.substring (0, i));
        return deg + Float.parseFloat (str.substring (++ i)) / 60.0F;
    }

    /**
     * Displays a US-map-shaped array of checkboxes for each state for downloading plates for those states.
     */
    private class StateMapView extends HorizontalScrollView {
        private TreeMap<String,StateCheckBox> stateCheckBoxes = new TreeMap<> ();
        private ViewGroup scbParent;

        public StateMapView () throws IOException
        {
            super (wairToNow);

            AssetManager am = wairToNow.getAssets ();
            BufferedReader rdr = new BufferedReader (new InputStreamReader (am.open ("statelocation.dat")), 1024);
            String line;
            while ((line = rdr.readLine ()) != null) {
                String parts[] = line.split (" ");
                StateCheckBox sb = new StateCheckBox (
                        parts[0],
                        Integer.parseInt (parts[1]),
                        Integer.parseInt (parts[2])
                );
                stateCheckBoxes.put (parts[0], sb);
            }
            rdr.close ();

            Rebuild ();
        }

        /**
         * The option to use list-shaped or map-shaped display may have changed,
         * so rebuild the list.
         */
        public void Rebuild ()
        {
            this.removeAllViews ();
            if (scbParent != null) scbParent.removeAllViews ();

            if (wairToNow.optionsView.listMapOption.getAlt ()) {
                AbsoluteLayout al = new AbsoluteLayout (wairToNow);

                for (StateCheckBox sb : stateCheckBoxes.values ()) {
                    sb.Rebuild ();
                    AbsoluteLayout.LayoutParams lp = new AbsoluteLayout.LayoutParams (
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            sb.xx * 4, sb.yy * 4
                    );
                    al.addView (sb, lp);
                }

                ViewGroup.LayoutParams vglp = new ViewGroup.LayoutParams (ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
                RotateLayout.LayoutParams rllp = new RotateLayout.LayoutParams (vglp);
                rllp.angle = -90;  // clockwise
                RotateLayout rl = new RotateLayout (wairToNow);
                rl.addView (al, rllp);

                this.addView (rl);

                scbParent = al;
            } else {
                LinearLayout ll = new LinearLayout (wairToNow);
                ll.setOrientation (LinearLayout.VERTICAL);
                for (StateCheckBox sb : stateCheckBoxes.values ()) {
                    sb.Rebuild ();
                    ll.addView (sb);
                }

                this.addView (ll);

                scbParent = ll;
            }
        }
    }

    /**
     * One of these per state to enable downloading plates for that state.
     * Consists of a checkbox and overlaying text giving the state name.
     */
    private class StateCheckBox extends FrameLayout implements Downloadable {
        public int xx;     // x,y within StateMapView
        public int yy;
        public String ss;  // two-letter state code (capital letters)

        private boolean mapStyle;
        private CheckBox cb;
        private int enddate;
        private TextView lb;
        private ViewGroup cblbParent;

        public StateCheckBox (String s, int x, int y)
        {
            super (wairToNow);

            xx = x;
            yy = y;
            ss = s;

            allDownloadables.addLast (this);

            enddate = GetPlatesExpDate (ss);

            cb = new CheckBox (wairToNow);
            cb.setOnCheckedChangeListener (MaintView.this);

            lb = new TextView (wairToNow);
            wairToNow.SetTextSize (lb);
        }

        public void Rebuild ()
        {
            this.removeAllViews ();
            if (cblbParent != null) cblbParent.removeAllViews ();

            mapStyle = wairToNow.optionsView.listMapOption.getAlt ();
            if (mapStyle) {
                LayoutParams lp1 = new LayoutParams (
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.LEFT
                );
                addView (cb, lp1);

                LayoutParams lp2 = new LayoutParams (
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.LEFT
                );
                addView (lb, lp2);

                cblbParent = this;
            } else {
                LinearLayout ll = new LinearLayout (wairToNow);
                ll.setOrientation (LinearLayout.HORIZONTAL);

                ll.addView (cb);
                ll.addView (lb);

                addView (ll);

                cblbParent = ll;
            }

            // maybe change text from just state to state and status or wisa wersa
            UpdateDCBsLinkText (this);
        }

        // Downloadable implementation
        public String GetSpaceName () { return "State " + ss; }
        public boolean IsChecked () { return cb.isChecked (); }
        public void UncheckBox ()
        {
            cb.setChecked (false);
            enddate = GetPlatesExpDate (ss);
            UpdateDCBsLinkText (this);
        }
        public int GetEndDate () { return enddate; }
        public boolean HasSomeUnloadableCharts ()
        {
            return enddate != 0;
        }
        public void DownloadComplete (String csvName) throws IOException { }

        /**
         * The named chart has been removed from list of downloaded charts.
         */
        @Override  // DownloadCheckBox
        public void RemovedDownloadedChart ()
        {
            // nothing downloaded any more
            enddate = 0;
        }

        public void UpdateSingleLinkText (int c, String t)
        {
            lb.setTextColor (c);
            if (mapStyle) {
                lb.setText (ss);
            } else {
                lb.setText ("State " + ss + ": " + t);
            }
        }
    }

    /**
     * Get expiration date of the airport plates for the airports of a given state (28-day cycle).
     * @return 0 if state not downloaded, else expiration date yyyymmdd
     */
    public static int GetPlatesExpDate (String ss)
    {
        int lastexpdate = 0;
        String[] dbnames = SQLiteDBs.Enumerate ();
        for (String dbname : dbnames) {
            if (dbname.startsWith ("cycles28_") && dbname.endsWith (".db")) {
                int expdate = Integer.parseInt (dbname.substring (9, dbname.length () - 3));
                if (lastexpdate < expdate) {
                    SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                    if ((sqldb != null) && sqldb.tableExists ("plates")) {
                        Cursor result = sqldb.query (
                                "plates", columns_pl_state,
                                "pl_state=?", new String[] { ss },
                                null, null, null, "1");
                        try {
                            if (result.moveToFirst ()) {
                                lastexpdate = expdate;
                            }
                        } finally {
                            result.close ();
                        }
                    }
                }
            }
        }
        return lastexpdate;
    }

    /**
     * This gets the expiration date of the latest 28-day cycle file.
     * It only has to contain one state's data.
     * @return expiration date (or 0 if no file present)
     */
    public static int GetPlatesExpDate ()
    {
        int lastexpdate = 0;
        String[] dbnames = SQLiteDBs.Enumerate ();
        for (String dbname : dbnames) {
            if (dbname.startsWith ("cycles28_") && dbname.endsWith (".db")) {
                int expdate = Integer.parseInt (dbname.substring (9, dbname.length () - 3));
                if (lastexpdate < expdate) lastexpdate = expdate;
            }
        }
        return lastexpdate;
    }

    /**
     * Click on this button to download everything that has its
     * download checkbox checked.
     */
    private class DownloadButton extends Button implements OnClickListener {
        public DownloadButton (Context ctx)
        {
            super (ctx);
            setText ("Download checked charts");
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
            setEnabled (false);
        }

        /**
         * Set enabled state of button based on whether or not anything
         * has been selected for download.  It is also disabled if we
         * are already in the middle of a {down,un}load.
         */
        public void UpdateEnabled ()
        {
            if ((downloadThread == null) && (unloadThread == null)) {
                for (Downloadable dcb : allDownloadables) {
                    if (dcb.IsChecked ()) {
                        setEnabled (true);
                        return;
                    }
                }
            }
            setEnabled (false);
        }

        /**
         * Download button was clicked.
         */
        @Override
        public void onClick (View v)
        {
            if (downloadThread == null) {

                // block clicking the download button while thread running
                setEnabled (false);

                // if we don't have any waypoint database,
                // force downloading it along with whatever user wants
                if (GetWaypointExpDate () == 0) {
                    waypointsCheckBox.checkBox.setChecked (true);
                }

                // start download thread going
                downloadCancelled = false;
                downloadThread = new DownloadThread ();
                downloadThread.start ();
            }
        }
    }

    /**
     * Click on this button to download everything that has its
     * download checkbox checked.
     */
    private class UnloadButton extends Button implements OnClickListener {
        private AlertDialog unloadAlertDialog;

        public UnloadButton (Context ctx)
        {
            super (ctx);
            setText ("Unload checked charts");
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
            setEnabled (false);
        }

        /**
         * Set enabled state of button based on whether or not anything
         * has been selected for download.  It is also disabled if we
         * are already in the middle of a {down,up}load.
         */
        public void UpdateEnabled ()
        {
            if ((downloadThread == null) && (unloadThread == null)) {
                for (Downloadable dcb : allDownloadables) {
                    if (dcb.IsChecked ()) {
                        if (!dcb.HasSomeUnloadableCharts ()) break;
                        setEnabled (true);
                        return;
                    }
                }
            }
            setEnabled (false);
        }

        /**
         * Unload button was clicked.
         */
        @Override
        public void onClick (View v)
        {
            if (unloadAlertDialog == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder (wairToNow);
                builder.setTitle ("Unload checked charts");
                builder.setMessage ("Are you sure?");
                builder.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int which)
                    {
                        if (unloadThread == null) {
                            setEnabled (false);
                            downloadButton.setEnabled (false);
                            unloadAlertDialog.dismiss ();
                            AlertDialog.Builder builder = new AlertDialog.Builder (wairToNow);
                            builder.setTitle ("Unloading charts");
                            builder.setMessage ("Please wait...");
                            unloadAlertDialog = builder.create ();
                            unloadAlertDialog.show ();
                            unloadThread = new UnloadThread ();
                            unloadThread.start ();
                        }
                    }
                });
                builder.setNegativeButton ("Cancel", new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int which)
                    {
                        unloadAlertDialog.dismiss ();
                        unloadAlertDialog = null;
                    }
                });
                unloadAlertDialog = builder.create ();
                unloadAlertDialog.show ();
            }
        }
    }

    /**
     * Download files in the background.
     */
    private class DownloadThread extends Thread implements DFLUpd {
        private int nlines;

        /**
         * Runs in a separate thread to download all files selected by the corresponding checkbox.
         */
        public void run ()
        {
            setName ("MaintView downloader");

            Message dlmsg;

            try {
                DownloadChartedLimsCSV ();
                DownloadChartLimitsCSV ();

                for (Downloadable dcb : allDownloadables) {
                    if (downloadCancelled) break;
                    if (!dcb.IsChecked ()) continue;

                    String dcbspacename = dcb.GetSpaceName ();
                    dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_OPENDLPROG, 1, 0,
                            "Downloading " + dcbspacename);
                    maintViewHandler.sendMessage (dlmsg);

                    /*
                     * Get list of files to download for the selected chart.
                     * Always download the list but leave it in the .tmp file so we don't overwrite existing file.
                     */
                    String undername    = dcbspacename.replace (' ', '_');
                    String permlistname = WairToNow.dbdir + "/charts/" + undername + ".filelist.txt";
                    String templistname = permlistname + ".tmp";
                    DownloadFile ("charts_filelist.php?undername=" + undername, templistname);

                    /*
                     * Download any chart files from that list that we don't already have.
                     * Except we always download .csv files because sectional charts sometimes
                     * have changing expiration dates as listed in the server-side
                     * chartlist_all.htm file.
                     */
                    nlines = 0;
                    BufferedReader filelistReader = new BufferedReader (new FileReader (templistname), 4096);
                    while (filelistReader.readLine () != null) nlines ++;
                    filelistReader.close ();

                    dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_OPENDLPROG, nlines, 0,
                                                            "Downloading " + dcbspacename);
                    maintViewHandler.sendMessage (dlmsg);

                    filelistReader = new BufferedReader (new FileReader (templistname), 4096);
                    String filelistLine;
                    nlines = 0;
                    HashSet<String> newfilelist         = new HashSet<> ();
                    HashMap<String,String> sameoldfiles = new HashMap<> ();
                    TreeMap<String,String> bulkdownload = new TreeMap<> ();
                    while ((filelistLine = filelistReader.readLine ()) != null) {
                        if (downloadCancelled) return;
                        ++ nlines;

                        /*
                         * Line can be of form:
                         *    <newfilename>               :: new cycle's file different than old cycle's file
                         * or
                         *    <newfilename>=<oldfilename> :: new cycle's file is the same as old cycle's file
                         */
                        String newFileName = filelistLine;
                        String oldFileName = "";
                        int i = filelistLine.indexOf ('=');
                        if (i > 0) {
                            newFileName = filelistLine.substring (0, i);
                            oldFileName = filelistLine.substring (++ i);
                        }

                        newfilelist.add (newFileName);

                        /*
                         * Use special downloaders for files that go into sqlite databases.
                         */
                        if (newFileName.startsWith ("datums/airports_") && newFileName.endsWith (".csv")) {
                            int expdate = Integer.parseInt (newFileName.substring (16, newFileName.length () - 4));
                            DownloadAirports (newFileName, expdate);
                            MaybeDeleteOldCycles56 ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/fixes_") && newFileName.endsWith (".csv")) {
                            int expdate = Integer.parseInt (newFileName.substring (13, newFileName.length () - 4));
                            DownloadFixes (newFileName, expdate);
                            MaybeDeleteOldCycles56 ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/localizers_") && newFileName.endsWith (".csv")) {
                            int expdate = Integer.parseInt (newFileName.substring (18, newFileName.length () - 4));
                            DownloadLocalizers (newFileName, expdate);
                            MaybeDeleteOldCycles56 ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/navaids_") && newFileName.endsWith (".csv")) {
                            int expdate = Integer.parseInt (newFileName.substring (15, newFileName.length () - 4));
                            DownloadNavaids (newFileName, expdate);
                            MaybeDeleteOldCycles56 ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/runways_") && newFileName.endsWith (".csv")) {
                            int expdate = Integer.parseInt (newFileName.substring (15, newFileName.length () - 4));
                            DownloadRunways (newFileName, expdate);
                            MaybeDeleteOldCycles56 ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/apdgeorefs_") && newFileName.endsWith (".csv") &&
                                ((i = newFileName.indexOf ("/", 18)) >= 0)) {
                            int expdate = Integer.parseInt (newFileName.substring (18, i));
                            String statecode = newFileName.substring (i + 1, newFileName.length () - 4);
                            DownloadMachineAPDGeoRefs (newFileName, expdate, statecode);
                            MaybeDeleteOldCycles28 ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/iapgeorefs_") && newFileName.endsWith (".csv") &&
                                ((i = newFileName.indexOf ("/", 18)) >= 0)) {
                            int expdate = Integer.parseInt (newFileName.substring (18, i));
                            String statecode = newFileName.substring (i + 1, newFileName.length () - 4);
                            DownloadMachineIAPGeoRefs (newFileName, expdate, statecode);
                            MaybeDeleteOldCycles28 ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/aptplates_") && newFileName.endsWith (".csv") &&
                                ((i = newFileName.indexOf ("/state/")) >= 0)) {
                            int expdate = Integer.parseInt (newFileName.substring (17, i));
                            String statecode = newFileName.substring (i + 7, newFileName.length () - 4);
                            DownloadPlates (newFileName, expdate, statecode);
                            MaybeDeleteOldCycles28 ();
                            UpdateDownloadProgress ();
                            wairToNow.openStreetMap.StartPrefetchingRunwayTiles ();
                            UpdateRunwayDiagramDownloadStatus ();
                            continue;
                        }

                        /*
                         * Normal file, queue for bulk download.
                         */
                        String permnewname = WairToNow.dbdir + "/" + newFileName;
                        String permoldname = WairToNow.dbdir + "/" + oldFileName;
                        if (permnewname.endsWith (".csv") || !new File (permnewname).exists ()) {
                            if (!oldFileName.equals ("") && new File (permoldname).exists ()) {
                                sameoldfiles.put (permoldname, permnewname);
                                UpdateDownloadProgress ();
                            } else {
                                bulkdownload.put (newFileName, permnewname);
                                -- nlines;
                            }
                        }
                    }
                    filelistReader.close ();

                    /*
                     * Perform bulk download of normal files.
                     */
                    DownloadFileList (bulkdownload, this);
                    maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_CLOSEDLPROG);
                    if (downloadCancelled) return;

                    /*
                     * All successfully downloaded...
                     * First, rename any old files that we can use over again if any.
                     * Then, delete old unusable versions if any.
                     */
                    if (sameoldfiles.size () > 0) {
                        dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_OPENDLPROG, sameoldfiles.size (), 0, "Renaming old files...");
                        maintViewHandler.sendMessage (dlmsg);
                        for (Map.Entry<String,String> entry : sameoldfiles.entrySet ()) {
                            Lib.RenameFile (entry.getKey (), entry.getValue ());
                        }
                        maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_CLOSEDLPROG);
                    }

                    dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_OPENDLPROG, 0, 0, "Deleting old files...");
                    maintViewHandler.sendMessage (dlmsg);
                    try {
                        filelistReader = new BufferedReader (new FileReader (permlistname), 4096);
                        while ((filelistLine = filelistReader.readLine ()) != null) {
                            String newFileName = filelistLine;
                            int i = filelistLine.indexOf ('=');
                            if (i > 0) newFileName = filelistLine.substring (0, i);
                            if (!newfilelist.contains (newFileName)) {
                                DeleteChartFile (dcb, newFileName);
                            }
                        }
                        filelistReader.close ();
                    } catch (FileNotFoundException fnfe) {
                        Lib.Ignored ();
                    }
                    maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_CLOSEDLPROG);

                    /*
                     * Rename new filelist over old filelist, so the replacement is complete.
                     */
                    Lib.RenameFile (templistname, permlistname);

                    /*
                     * Uncheck the download checkbox because it is all downloaded.
                     */
                    dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UNCHECKBOX, dcb);
                    maintViewHandler.sendMessage(dlmsg);

                    /*
                     * Create one of our own DownloadedCharts objects and insert in chart.downloadedCharts.
                     * This updates our buttons to reflect the new valid until date.
                     */
                    for (String filename : newfilelist) {
                        if (filename.endsWith (".csv")) {
                            HaveNewChart hnc = new HaveNewChart ();
                            hnc.dcb = dcb;
                            hnc.csvName = filename;
                            dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_HAVENEWCHART, hnc);
                            maintViewHandler.sendMessage(dlmsg);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e (TAG, "MaintView thread exception", e);
                maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_CLOSEDLPROG);
                maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_DLCOMPLETE);
                dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_DLERROR, e.getMessage ());
                maintViewHandler.sendMessage (dlmsg);
            } finally {
                downloadThread = null;
                maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_DLCOMPLETE);
                SQLiteDBs.CloseAll ();
            }
        }

        private void UpdateDownloadProgress ()
        {
            Message m = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UPDATEDLPROG, nlines, 0, null);
            maintViewHandler.sendMessage (m);
        }

        /**
         * Callback from DownloadFileList() for each file downloaded
         * @param servername = name of file on server
         * @param localname  = name of file on flash
         */
        @Override // DFLUpd
        public void downloaded (String servername, String localname)
        {
            Message dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UPDATEDLPROG, ++ nlines, 0, null);
            maintViewHandler.sendMessage (dlmsg);
        }
        @Override // DFLUpd
        public boolean isCancelled ()
        {
            return downloadCancelled;
        }
    }

    private void DownloadChartedLimsCSV () throws IOException
    {
        Log.i (TAG, "downloading chartedlims.csv");
        String csvname = WairToNow.dbdir + "/chartedlims.csv";
        DownloadFile ("chartedlims.csv", csvname + ".tmp");
        Lib.RenameFile (csvname + ".tmp", csvname);
    }

    private void DownloadChartLimitsCSV () throws IOException
    {
        Log.i (TAG, "downloading chartlimits.csv");
        String csvname = WairToNow.dbdir + "/chartlimits.csv";
        DownloadFile ("chartlimits.php", csvname + ".tmp");
        Lib.RenameFile (csvname + ".tmp", csvname);
    }

    /**
     * Unload (delete) chart files in background.
     */
    private class UnloadThread extends Thread {
        @Override
        public void run ()
        {
            setName ("MaintView unloader");

            Message dlmsg;

            try {
                for (Downloadable dcb : allDownloadables) {
                    if (!dcb.IsChecked ()) continue;
                    if (dcb.HasSomeUnloadableCharts ()) {

                        /*
                         * Delete all files corresponding to this chart.
                         * They are listed in the <undername>.filelist.txt file as created by the downloader.
                         */
                        String undername    = dcb.GetSpaceName ().replace (' ', '_');
                        String permlistname = WairToNow.dbdir + "/charts/" + undername + ".filelist.txt";
                        BufferedReader filelistReader = null;
                        try {
                            filelistReader = new BufferedReader (new FileReader (permlistname), 1024);
                            String filelistLine;
                            while ((filelistLine = filelistReader.readLine ()) != null) {
                                DeleteChartFile (dcb, filelistLine);
                            }
                        } catch (IOException ioe) {
                            Log.w (TAG, "error deleting chart files " + permlistname, ioe);
                        } finally {
                            if (filelistReader != null) try { filelistReader.close (); } catch (IOException ioe) { Lib.Ignored (); }
                        }

                        /*
                         * Also delete the <undername>.filelist.txt file.
                         */
                        Lib.Ignored (new File (permlistname).delete ());
                    }

                    /*
                     * Uncheck the checkbox now that all files for that chart are deleted.
                     */
                    dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UNCHECKBOX, dcb);
                    maintViewHandler.sendMessage(dlmsg);
                }
            } catch (Exception e) {
                Log.e (TAG, "thread exception", e);
            } finally {
                unloadThread = null;
                maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_UNLDDONE);
                SQLiteDBs.CloseAll ();
            }
        }
    }

    /**
     * Delete a chart file
     * @param dcb = which chart the file belongs to
     * @param filelistline = name of file being deleted
     */
    private void DeleteChartFile (Downloadable dcb, String filelistline)
    {
        int i;
        boolean plainfile = true;

        /*
         * Use special unloaders for files that go into sqlite databases.
         */
        if (filelistline.startsWith ("datums/airports_") && filelistline.endsWith (".csv")) {
            // don't allow deleting waypoints
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/fixes_") && filelistline.endsWith (".csv")) {
            // don't allow deleting waypoints
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/localizers_") && filelistline.endsWith (".csv")) {
            // don't allow deleting waypoints
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/navaids_") && filelistline.endsWith (".csv")) {
            // don't allow deleting waypoints
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/runways_") && filelistline.endsWith (".csv")) {
            // don't allow deleting waypoints
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/apdgeorefs_") && filelistline.endsWith (".csv") &&
                ((i = filelistline.indexOf ("/", 18)) >= 0)) {
            int expdate = Integer.parseInt (filelistline.substring (18, i));
            String statecode = filelistline.substring (i + 1, filelistline.length () - 4);
            DeleteMachineAPDGeoRefs (expdate, statecode);
            MaybeDeleteOldCycles28 ();
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/iapgeorefs_") && filelistline.endsWith (".csv") &&
                ((i = filelistline.indexOf ("/", 18)) >= 0)) {
            int expdate = Integer.parseInt (filelistline.substring (18, i));
            String statecode = filelistline.substring (i + 1, filelistline.length () - 4);
            DeleteMachineIAPGeoRefs (expdate, statecode);
            MaybeDeleteOldCycles28 ();
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/aptplates_") && filelistline.endsWith (".csv") &&
                ((i = filelistline.indexOf ("/state/")) >= 0)) {
            int expdate = Integer.parseInt (filelistline.substring (17, i));
            String statecode = filelistline.substring (i + 7, filelistline.length () - 4);
            DeletePlates (expdate, statecode);
            MaybeDeleteOldCycles28 ();
            plainfile = false;
        }

        /*
         * Some plates files are shared throughout a region, eg, an alternate approach minimums
         * might be shared between Massachusetts and New Verhampmontshire.
         */
        if (filelistline.startsWith ("datums/aptplates_") && filelistline.contains (".gif.p") &&
                ((i = filelistline.indexOf ("/gif_150/")) >= 0)) {
            int expdate      = Integer.parseInt (filelistline.substring (17, i));
            String filename  = filelistline.substring (++ i);
            String spacename = dcb.GetSpaceName ();
            if (!spacename.startsWith ("State ") || (spacename.length () != 8)) {
                throw new RuntimeException ("bad state name " + spacename);
            }
            String statecode = spacename.substring (6);
            plainfile = MaybeDeletePlateFile (filename, expdate, statecode);
        }

        if (plainfile) {

            // delete the file
            String fname = WairToNow.dbdir + "/" + filelistline;
            Lib.Ignored (new File (fname).delete ());

            // try to delete its directory in case directory is now empty
            while ((i = fname.lastIndexOf ('/')) > WairToNow.dbdir.length ()) {
                fname = fname.substring (0, i);
                if (!new File (fname).delete ()) break;
            }
        }

        // for .csv files, notify dcb that the file was deleted
        // we don't need to notify for the other files (eg, .png files)
        // as notifying for the .csv file is sufficient
        if (filelistline.endsWith (".csv")) {
            Message msg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_REMUNLDCHART, dcb);
            maintViewHandler.sendMessage(msg);
        }
    }

    /**
     * See if it is OK to delete the given plate file.
     * @param filename  = name of gif file, starting with "gif_150/"
     * @param expdate   = expiration date of the gif file
     * @param statecode = state code of state it is being deleted from
     * @return true iff it is ok to delete
     */
    private static boolean MaybeDeletePlateFile (String filename, int expdate, String statecode)
    {
        String dbname = "cycles28_" + expdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);

        // if no file or table, can't possible have any references to the gif
        if (sqldb == null) return true;
        if (!sqldb.tableExists ("plates")) return true;

        // see if any other state references the gif
        // if so, can't delete it, otherwise we can
        Cursor result = sqldb.query (
                "plates", columns_pl_faaid,
                "pl_filename=? AND pl_state<>?", new String[] { filename, statecode },
                null, null, null, "1");
        try {
            return !result.moveToFirst ();
        } finally {
            result.close ();
        }
    }

    private static class HaveNewChart {
        public Downloadable dcb;
        public String csvName;
    }

    /**
     * Displays a diagram that shows what charts are available.
     */
    private class ChartDiagView extends Button implements OnClickListener {
        private Bitmap   bitmap;
        private CDViewer viewer;
        private int      resid;
        private Matrix   matrix = new Matrix ();

        public ChartDiagView (int rid)
        {
            super (wairToNow);
            resid = rid;
            setText ("Show diagram");
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
        }

        public void onClick (View v)
        {
            viewer = new CDViewer ();
            wairToNow.SetCurrentTab (viewer);
        }

        private class CDViewer extends View implements WairToNow.CanBeMainView {
            private CDPanAndZoom pnz;

            public CDViewer ()
            {
                super (wairToNow);
                pnz = new CDPanAndZoom ();
            }

            @Override  // View
            public boolean onTouchEvent (@NonNull MotionEvent event)
            {
                return pnz.OnTouchEvent (event);
            }

            @Override  // View
            public void onDraw (Canvas canvas)
            {
                canvas.drawBitmap (bitmap, matrix, null);
            }

            @Override  // CanBeMainView
            public String GetTabName ()
            {
                return "Maint";
            }

            @Override  // CanBeMainView
            public void OpenDisplay()
            {
                if (bitmap == null) bitmap = BitmapFactory.decodeResource (wairToNow.getResources (), resid);
                if (bitmap == null) throw new RuntimeException ("bitmap create failed");
            }

            @Override  // CanBeMainView
            public void CloseDisplay()
            {
                if (bitmap != null) bitmap.recycle ();
                bitmap = null;
            }

            @Override  // WairToNow.CanBeMainView
            public void ReClicked ()
            {
                wairToNow.SetCurrentTab (MaintView.this);
            }

            @Override  // CanBeMainView
            public View GetBackPage()
            {
                return MaintView.this;
            }
        }

        private class CDPanAndZoom extends PanAndZoom {

            public CDPanAndZoom ()
            {
                super (wairToNow);
            }

            @Override
            public void MouseDown (float x, float y)
            { }

            @Override
            public void MouseUp ()
            { }

            @Override
            public void Panning (float x, float y, float dx, float dy)
            {
                matrix.postTranslate (dx, dy);
                viewer.invalidate ();
            }

            @Override
            public void Scaling (float fx, float fy, float sf)
            {
                matrix.postScale (sf, sf, fx, fy);
                viewer.invalidate ();
            }

        }
    }

    public interface DFLUpd {
        void downloaded (String servername, String localname);
        boolean isCancelled ();
    }

    /**
     * Perform bulk download
     * @param bulkfilelist = list of <servername,localname> files
     */
    public static void DownloadFileList (AbstractMap<String,String> bulkfilelist, DFLUpd dflupd)
            throws IOException
    {
        if (!bulkfilelist.isEmpty ()) {
            int retry = 0;
            URL url = new URL (dldir + "/bulkdownload.php");
            while (!bulkfilelist.isEmpty ()) {
                if (dflupd.isCancelled ()) return;
                Log.i (TAG, "downloading bulk starting with " + bulkfilelist.keySet ().iterator ().next ());
                try {
                    HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
                    try {

                        /*
                         * Send list of files to fetch as a POST request to bulkdownload.php.
                         * One filename per f0, f1, ... POST variable.
                         */
                        httpCon.setRequestMethod ("POST");
                        httpCon.setDoOutput (true);
                        httpCon.setChunkedStreamingMode (0);
                        PrintWriter os = new PrintWriter (httpCon.getOutputStream ());
                        int n = 0;
                        for (String servername : bulkfilelist.keySet ()) {
                            if (n > 0) os.print ('&');
                            os.print ("f" + n + "=" + servername);
                            if (++ n > 250) break;
                        }
                        os.flush ();

                        /*
                         * Hopefully get 200 HTTP OK reply.
                         */
                        int rc = httpCon.getResponseCode ();
                        if (rc != HttpURLConnection.HTTP_OK) {
                            throw new IOException ("http response code " + rc + " on bulk download");
                        }

                        /*
                         * Read each file and store in flash.
                         * As each is read successfully, remove from list of files to read.
                         * Each file given in same order requested in form:
                         *   @@name=nameasgiven\n
                         *   @@size=numberofbytes\n
                         *   binaryfilecontents
                         *   @@eof\n
                         * Then at end:
                         *   @@done\n
                         */
                        BufferedInputStream is = new BufferedInputStream (httpCon.getInputStream (), 32768);
                        while (true) {
                            if (dflupd.isCancelled ()) return;
                            String nametag    = Lib.ReadStreamLine (is);
                            if (nametag.equals ("@@done")) break;
                            String sizetag    = Lib.ReadStreamLine (is);
                            if (!nametag.startsWith ("@@name=")) throw new IOException ("bad download name tag " + nametag);
                            if (!sizetag.startsWith ("@@size=")) throw new IOException ("bad download size tag " + sizetag);
                            String servername = nametag.substring (7);
                            String permname   = bulkfilelist.get (servername);
                            String tempname   = permname + ".tmp";
                            int bytelen;
                            try {
                                bytelen = Integer.parseInt (sizetag.substring (7));
                            } catch (NumberFormatException nfe) {
                                throw new IOException ("bad download size tag " + sizetag);
                            }
                            Lib.WriteStreamToFile (is, bytelen, tempname);
                            String eoftag = Lib.ReadStreamLine (is);
                            if (!eoftag.equals ("@@eof")) throw new IOException ("bad end-of-file tag " + eoftag);
                            Lib.RenameFile (tempname, permname);
                            dflupd.downloaded (servername, permname);
                            bulkfilelist.remove (servername);
                            retry = 0;
                        }
                    } finally {
                        httpCon.disconnect ();
                    }
                } catch (IOException ioe) {
                    if (++ retry > 3) throw ioe;
                    Log.i (TAG, "retrying bulk download", ioe);
                }
            }
            Log.i (TAG, "bulk download complete");
        }
    }

    /**
     * Download a file from web server and store as is in a local file
     * @param servername = name of file on server
     * @param localname = name of local file
     */
    public static void DownloadFile (String servername, String localname)
            throws IOException
    {
        DownloadStuffFile dsf = new DownloadStuffFile ();
        dsf.localname = localname;
        dsf.DownloadWhat (servername);
    }

    private static class DownloadStuffFile extends DownloadStuff {
        public String localname;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            Lib.Ignored (new File (localname.substring (0, localname.lastIndexOf ('/') + 1)).mkdirs ());
            FileOutputStream os = new FileOutputStream (localname);
            byte[] buff = new byte[4096];
            try {
                int len;
                while ((len = is.read (buff)) > 0) {
                    os.write (buff, 0, len);
                }
            } finally {
                os.close ();
            }
        }
    }

    /**
     * Download datums/airports_<expdate>.csv file from web server and store it in database table
     * @param servername = name of file on server
     * @param expdate = expiration date of airports data
     */
    private static void DownloadAirports (String servername, int expdate)
            throws IOException
    {
        DownloadStuffAirports dsa = new DownloadStuffAirports ();
        dsa.dbname = "cycles56_" + expdate + ".db";
        dsa.DownloadWhat (servername);
    }

    private static class DownloadStuffAirports extends DownloadStuff {
        public String dbname;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("airports")) {
                long time = System.nanoTime ();
                sqldb.execSQL ("DROP INDEX IF EXISTS airports_lats;");
                sqldb.execSQL ("DROP INDEX IF EXISTS airports_lons;");
                sqldb.execSQL ("DROP INDEX IF EXISTS aptkeys_keys;");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmpapts;");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmpakys;");
                sqldb.execSQL ("CREATE TABLE tmpapts (apt_icaoid TEXT, apt_faaid TEXT NOT NULL, apt_elev INTEGER NOT NULL, apt_name TEXT NOT NULL, apt_lat INTEGER NOT NULL, apt_lon INTEGER NOT NULL, apt_desc TEXT NOT NULL);");
                sqldb.execSQL ("CREATE TABLE tmpakys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE INDEX airports_lats ON tmpapts (apt_lat);");
                sqldb.execSQL ("CREATE INDEX airports_lons ON tmpapts (apt_lon);");
                sqldb.execSQL ("CREATE INDEX aptkeys_keys  ON tmpakys (kw_key);");
                sqldb.beginTransaction ();
                try {
                    BufferedReader br = new BufferedReader (new InputStreamReader (is), 4096);
                    String csv;
                    int numAdded = 0;
                    while ((csv = br.readLine ()) != null) {
                        String[] cols = Lib.QuotedCSVSplit (csv);
                        ContentValues values = new ContentValues (6);
                        values.put ("apt_icaoid", cols[0]);
                        values.put ("apt_faaid",  cols[1]);
                        values.put ("apt_elev",   (int) ((cols[2].equals ("") ? Waypoint.ELEV_UNKNOWN : Float.parseFloat (cols[2])) * Waypoint.elevdbfact + 0.5));
                        values.put ("apt_name",   cols[3]);
                        values.put ("apt_lat",    (int) (Float.parseFloat (cols[4]) * Waypoint.lldbfact + 0.5));
                        values.put ("apt_lon",    (int) (Float.parseFloat (cols[5]) * Waypoint.lldbfact + 0.5));
                        values.put ("apt_desc",   cols[7]);
                        long rowid = sqldb.insert ("tmpapts", null, values);

                        int i = cols[7].indexOf ('\n');
                        if (i < 0) i = cols[7].length ();
                        String keystr = cols[0] + " " + cols[1] + " " + cols[3] + " " + cols[7].substring (0, i);
                        keystr = keystr.replace (',', ' ').replace ('.', ' ').replace ('/', ' ').toUpperCase (Locale.US);
                        String[] keys = keystr.split (" ");
                        int len = keys.length;
                        for (i = 0; i < len; i ++) {
                            String key = keys[i];
                            int j;
                            for (j = 0; j < i; j ++) {
                                if (keys[j].equals (key)) break;
                            }
                            if (j >= i) {
                                values = new ContentValues (2);
                                values.put ("kw_key",   key);
                                values.put ("kw_rowid", rowid);
                                sqldb.insert ("tmpakys", null, values);
                            }
                        }

                        if (++ numAdded == 256) {
                            sqldb.yieldIfContendedSafely ();
                            numAdded = 0;
                        }
                    }
                    sqldb.execSQL ("ALTER TABLE tmpapts RENAME TO airports;");
                    sqldb.execSQL ("ALTER TABLE tmpakys RENAME TO aptkeys;");
                    sqldb.setTransactionSuccessful ();
                } finally {
                    sqldb.endTransaction ();
                }
                time = System.nanoTime () - time;
                Log.i (TAG, "airports download time " + (time / 1000000) + " ms");
            }
        }
    }

    /**
     * Download datums/localizers_<expdate>.csv file from web server and store it in database table
     * @param servername = name of file on server
     * @param expdate = expiration date of airports data
     */
    private static void DownloadLocalizers (String servername, int expdate)
            throws IOException
    {
        DownloadStuffLocalizers dsl = new DownloadStuffLocalizers ();
        dsl.dbname = "cycles56_" + expdate + ".db";
        dsl.DownloadWhat (servername);
    }

    private static class DownloadStuffLocalizers extends DownloadStuff {
        public String dbname;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("localizers")) {
                long time = System.nanoTime ();
                sqldb.execSQL ("DROP INDEX IF EXISTS localizers_lats;");
                sqldb.execSQL ("DROP INDEX IF EXISTS localizers_lons;");
                sqldb.execSQL ("DROP INDEX IF EXISTS lockeys_keys;");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmplocs;");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmplkys;");
                sqldb.execSQL ("CREATE TABLE tmplocs (loc_type TEXT, loc_faaid TEXT, loc_elev INTEGER NOT NULL, loc_name TEXT NOT NULL, loc_lat INTEGER NOT NULL, loc_lon INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE TABLE tmplkys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE INDEX localizers_lats ON tmplocs (loc_lat);");
                sqldb.execSQL ("CREATE INDEX localizers_lons ON tmplocs (loc_lon);");
                sqldb.execSQL ("CREATE INDEX lockeys_keys ON tmplkys (kw_key);");
                sqldb.beginTransaction ();
                try {
                    BufferedReader br = new BufferedReader (new InputStreamReader (is), 4096);
                    String csv;
                    int numAdded = 0;
                    while ((csv = br.readLine ()) != null) {
                        String[] cols = Lib.QuotedCSVSplit (csv);
                        ContentValues values = new ContentValues (6);
                        values.put ("loc_type",   cols[0]);
                        values.put ("loc_faaid",  cols[1]);
                        values.put ("loc_elev",   (int) ((cols[2].equals ("") ? Waypoint.ELEV_UNKNOWN : Float.parseFloat (cols[2])) * Waypoint.elevdbfact + 0.5));
                        values.put ("loc_name",   cols[3]);
                        values.put ("loc_lat",    (int) (Float.parseFloat (cols[4]) * Waypoint.lldbfact + 0.5));
                        values.put ("loc_lon",    (int) (Float.parseFloat (cols[5]) * Waypoint.lldbfact + 0.5));
                        long rowid = sqldb.insert ("tmplocs", null, values);

                        int i = cols[6].indexOf ('\n');
                        if (i < 0) i = cols[6].length ();
                        String keystr = cols[0] + " " + cols[1] + " " + cols[3] + " " + cols[6].substring (0, i);
                        keystr = keystr.replace (',', ' ').replace ('.', ' ').replace ('/', ' ').toUpperCase (Locale.US);
                        String[] keys = keystr.split (" ");
                        int len = keys.length;
                        for (i = 0; i < len; i ++) {
                            String key = keys[i];
                            int j;
                            for (j = 0; j < i; j ++) {
                                if (keys[j].equals (key)) break;
                            }
                            if (j >= i) {
                                values = new ContentValues (4);
                                values.put ("kw_key",   key);
                                values.put ("kw_rowid", rowid);
                                sqldb.insert ("tmplkys", null, values);
                            }
                        }

                        if (++ numAdded == 256) {
                            sqldb.yieldIfContendedSafely ();
                            numAdded = 0;
                        }
                    }
                    sqldb.execSQL ("ALTER TABLE tmplocs RENAME TO localizers;");
                    sqldb.execSQL ("ALTER TABLE tmplkys RENAME TO lockeys;");
                    sqldb.setTransactionSuccessful ();
                } finally {
                    sqldb.endTransaction ();
                }
                time = System.nanoTime () - time;
                Log.i (TAG, "localizers download time " + (time / 1000000) + " ms");
            }
        }
    }

    /**
     * Download datums/navaids_<expdate>.csv file from web server and store it in database table
     * @param servername = name of file on server
     * @param expdate = expiration date of airports data
     */
    private static void DownloadNavaids (String servername, int expdate)
            throws IOException
    {
        DownloadStuffNavaids dsn = new DownloadStuffNavaids ();
        dsn.dbname = "cycles56_" + expdate + ".db";
        dsn.DownloadWhat (servername);
    }

    private static class DownloadStuffNavaids extends DownloadStuff {
        public String dbname;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("navaids")) {
                long time = System.nanoTime ();
                sqldb.execSQL ("DROP INDEX IF EXISTS navaids_lats;");
                sqldb.execSQL ("DROP INDEX IF EXISTS navaids_lons;");
                sqldb.execSQL ("DROP INDEX IF EXISTS navkeys_keys;");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmpnavs;");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmpnkys;");
                sqldb.execSQL ("CREATE TABLE tmpnavs (nav_type TEXT, nav_faaid TEXT, nav_elev INTEGER NOT NULL, nav_name TEXT NOT NULL, nav_lat INTEGER NOT NULL, nav_lon INTEGER NOT NULL, nav_magvar INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE TABLE tmpnkys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE INDEX navaids_lats ON tmpnavs (nav_lat);");
                sqldb.execSQL ("CREATE INDEX navaids_lons ON tmpnavs (nav_lon);");
                sqldb.execSQL ("CREATE INDEX navkeys_keys ON tmpnkys (kw_key);");
                sqldb.beginTransaction ();
                try {
                    BufferedReader br = new BufferedReader (new InputStreamReader (is), 4096);
                    String csv;
                    int numAdded = 0;
                    while ((csv = br.readLine ()) != null) {
                        String[] cols = Lib.QuotedCSVSplit (csv);
                        ContentValues values = new ContentValues (7);
                        values.put ("nav_type",   cols[0]);
                        values.put ("nav_faaid",  cols[1]);
                        values.put ("nav_elev",   (int) ((cols[2].equals ("") ? Waypoint.ELEV_UNKNOWN : Float.parseFloat (cols[2])) * Waypoint.elevdbfact + 0.5));
                        values.put ("nav_name",   cols[3]);
                        values.put ("nav_lat",    (int) (Float.parseFloat (cols[4]) * Waypoint.lldbfact + 0.5));
                        values.put ("nav_lon",    (int) (Float.parseFloat (cols[5]) * Waypoint.lldbfact + 0.5));
                        values.put ("nav_magvar", cols[6].equals ("") ? Waypoint.VAR_UNKNOWN : Integer.parseInt (cols[6]));
                        long rowid = sqldb.insert ("tmpnavs", null, values);

                        int i = cols[6].indexOf ('\n');
                        if (i < 0) i = cols[6].length ();
                        String keystr = cols[0] + " " + cols[1] + " " + cols[3] + " " + cols[6].substring (0, i);
                        keystr = keystr.replace (',', ' ').replace ('.', ' ').replace ('/', ' ').toUpperCase (Locale.US);
                        String[] keys = keystr.split (" ");
                        int len = keys.length;
                        for (i = 0; i < len; i ++) {
                            String key = keys[i];
                            int j;
                            for (j = 0; j < i; j ++) {
                                if (keys[j].equals (key)) break;
                            }
                            if (j >= i) {
                                values = new ContentValues (4);
                                values.put ("kw_key",   key);
                                values.put ("kw_rowid", rowid);
                                sqldb.insert ("tmpnkys", null, values);
                            }
                        }

                        if (++ numAdded == 256) {
                            sqldb.yieldIfContendedSafely ();
                            numAdded = 0;
                        }
                    }
                    sqldb.execSQL ("ALTER TABLE tmpnavs RENAME TO navaids;");
                    sqldb.execSQL ("ALTER TABLE tmpnkys RENAME TO navkeys;");
                    sqldb.setTransactionSuccessful ();
                } finally {
                    sqldb.endTransaction ();
                }
                time = System.nanoTime () - time;
                Log.i (TAG, "navaids download time " + (time / 1000000) + " ms");
            }
        }
    }

    /**
     * Download datums/fixes_<expdate>.csv file from web server and store it in database table
     * @param servername = name of file on server
     * @param expdate = expiration date of fixes data
     */
    private static void DownloadFixes (String servername, int expdate)
            throws IOException
    {
        DownloadStuffFixes dsf = new DownloadStuffFixes ();
        dsf.dbname = "cycles56_" + expdate + ".db";
        dsf.DownloadWhat (servername);
    }

    private static class DownloadStuffFixes extends DownloadStuff {
        public String dbname;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("fixes")) {
                long time = SystemClock.uptimeMillis ();
                sqldb.execSQL ("DROP INDEX IF EXISTS fixes_lats;");
                sqldb.execSQL ("DROP INDEX IF EXISTS fixes_lons;");
                sqldb.execSQL ("DROP INDEX IF EXISTS fixkeys_keys;");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmpfixs;");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmpfkys;");
                sqldb.execSQL ("CREATE TABLE tmpfixs (fix_name TEXT NOT NULL, fix_lat INTEGER NOT NULL, fix_lon INTEGER NOT NULL, fix_desc TEXT NOT NULL);");
                sqldb.execSQL ("CREATE TABLE tmpfkys (kw_key TEXT NOT NULL, kw_rowid INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE INDEX fixes_lats ON tmpfixs (fix_lat);");
                sqldb.execSQL ("CREATE INDEX fixes_lons ON tmpfixs (fix_lon);");
                sqldb.execSQL ("CREATE INDEX fixkeys_keys ON tmpfkys (kw_key);");
                sqldb.beginTransaction ();
                try {
                    BufferedReader br = new BufferedReader (new InputStreamReader (is), 4096);
                    String csv;
                    int numAdded = 0;
                    while ((csv = br.readLine ()) != null) {
                        String[] cols = Lib.QuotedCSVSplit (csv);
                        ContentValues values = new ContentValues (3);
                        values.put ("fix_name", cols[0]);
                        values.put ("fix_lat",  (int) (Float.parseFloat (cols[1]) * Waypoint.lldbfact));
                        values.put ("fix_lon",  (int) (Float.parseFloat (cols[2]) * Waypoint.lldbfact));
                        values.put ("fix_desc", cols[3] + " " + cols[4]);
                        long rowid = sqldb.insert ("tmpfixs", null, values);

                        values = new ContentValues (4);
                        values.put ("kw_key",   cols[0]);
                        values.put ("kw_rowid", rowid);
                        sqldb.insert ("tmpfkys", null, values);

                        if (++ numAdded == 256) {
                            sqldb.yieldIfContendedSafely ();
                            numAdded = 0;
                        }
                    }
                    sqldb.execSQL ("ALTER TABLE tmpfixs RENAME TO fixes;");
                    sqldb.execSQL ("ALTER TABLE tmpfkys RENAME TO fixkeys;");
                    sqldb.setTransactionSuccessful ();
                } finally {
                    sqldb.endTransaction ();
                }
                time = SystemClock.uptimeMillis () - time;
                Log.i (TAG, "fixes download time " + time + " ms");
            }
        }
    }

    /**
     * Download datums/runways_<expdate>.csv file from web server and store it in database table
     * @param servername = name of file on server
     * @param expdate = expiration date of waypoint data
     */
    private static void DownloadRunways (String servername, int expdate)
            throws IOException
    {
        DownloadStuffRunways dsn = new DownloadStuffRunways ();
        dsn.dbname = "cycles56_" + expdate + ".db";
        dsn.DownloadWhat (servername);
    }

    private static class DownloadStuffRunways extends DownloadStuff {
        public String dbname;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("runways")) {
                long time = System.nanoTime ();
                sqldb.execSQL ("DROP INDEX IF EXISTS runways_faaids");
                sqldb.execSQL ("DROP TABLE IF EXISTS tmprwys;");
                sqldb.execSQL ("CREATE TABLE tmprwys (rwy_faaid TEXT, rwy_number TEXT NOT NULL, rwy_truehdg INTEGER, rwy_tdze REAL NOT NULL, rwy_beglat INTEGER NOT NULL, rwy_beglon INTEGER NOT NULL, rwy_endlat INTEGER NOT NULL, rwy_endlon INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE INDEX runways_faaids ON tmprwys (rwy_faaid);");
                sqldb.beginTransaction ();
                try {
                    BufferedReader br = new BufferedReader (new InputStreamReader (is), 4096);
                    String csv;
                    int numAdded = 0;
                    while ((csv = br.readLine ()) != null) {
                        String[] cols = Lib.QuotedCSVSplit (csv);
                        ContentValues values = new ContentValues (8);
                        values.put ("rwy_faaid",   cols[0]);  // eg, "BOS"
                        values.put ("rwy_number",  cols[1]);  // eg, "04L"
                        values.put ("rwy_truehdg", cols[2].equals ("") ? null : Integer.parseInt (cols[2]));
                        values.put ("rwy_tdze",    Float.parseFloat (cols[3]));
                        values.put ("rwy_beglat", (int) (Float.parseFloat (cols[4]) * Waypoint.lldbfact));
                        values.put ("rwy_beglon", (int) (Float.parseFloat (cols[5]) * Waypoint.lldbfact));
                        values.put ("rwy_endlat", (int) (Float.parseFloat (cols[6]) * Waypoint.lldbfact));
                        values.put ("rwy_endlon", (int) (Float.parseFloat (cols[7]) * Waypoint.lldbfact));
                        sqldb.insert ("tmprwys", null, values);

                        if (++ numAdded == 256) {
                            sqldb.yieldIfContendedSafely ();
                            numAdded = 0;
                        }
                    }
                    sqldb.execSQL ("ALTER TABLE tmprwys RENAME TO runways;");
                    sqldb.setTransactionSuccessful ();
                } finally {
                    sqldb.endTransaction ();
                }
                time = System.nanoTime () - time;
                Log.i (TAG, "runways download time " + (time / 1000000) + " ms");
            }
        }
    }

    /**
     * If the newest cycles56 database file has all the tables that the old one has,
     * delete the old one.
     */
    private static void MaybeDeleteOldCycles56 ()
    {
        String[] dbnames = SQLiteDBs.Enumerate ();
        for (int i = 0; i < dbnames.length; i ++) {
            String dbnamei = dbnames[i];
            if (dbnamei == null) continue;
            if (!dbnamei.startsWith ("cycles56_")) continue;
            for (int j = 0; j < dbnames.length; j ++ ) {
                String dbnamej = dbnames[j];
                if (dbnamej == null) continue;
                if (!dbnamej.startsWith ("cycles56_")) continue;
                if (dbnamei.compareTo (dbnamej) > 0) {
                    SQLiteDBs sqldbi = SQLiteDBs.open (dbnamei);
                    if (sqldbi == null) continue;
                    SQLiteDBs sqldbj = SQLiteDBs.open (dbnamej);
                    if (sqldbj == null) continue;
                    Cursor cursorj = sqldbj.query (
                            "sqlite_master", columns_name,
                            "type='table'", null,
                            null, null, null, null);
                    boolean killj = true;
                    try {
                        if (cursorj.moveToFirst ()) {
                            do {
                                String tablej = cursorj.getString (0);
                                killj &= sqldbi.tableExists (tablej);
                            } while (cursorj.moveToNext ());
                        }
                    } finally {
                        cursorj.close ();
                    }
                    if (killj) {
                        sqldbj.markForDelete ();
                        SQLiteDBs.CloseAll ();
                        dbnames[j] = null;
                    }
                }
            }
        }
    }

    /**
     * Download a state/<stateid>.csv file that maps an airport ID to its state
     * and all the plates (airport diagrams, iaps, sids, stars, etc)
     * for the airports in that state.
     */
    private static void DownloadPlates (String servername, int expdate, String statecode)
            throws IOException
    {
        DownloadStuffPlate dsp = new DownloadStuffPlate ();
        dsp.dbname = "cycles28_" + expdate + ".db";
        dsp.statecode = statecode;
        dsp.DownloadWhat (servername);
    }

    public static class DownloadStuffPlate extends DownloadStuff {
        public String dbname;
        public String statecode;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("plates")) {
                sqldb.execSQL ("DROP INDEX IF EXISTS plate_state;");
                sqldb.execSQL ("DROP INDEX IF EXISTS plate_faaid;");
                sqldb.execSQL ("DROP INDEX IF EXISTS plate_unique;");
                sqldb.execSQL ("DROP TABLE IF EXISTS plates;");
                sqldb.execSQL ("CREATE TABLE plates (pl_state TEXT NOT NULL, pl_faaid TEXT NOT NULL, pl_descrip TEXT NOT NULL, pl_filename TEXT NOT NULL);");
                sqldb.execSQL ("CREATE INDEX plate_state ON plates (pl_state);");
                sqldb.execSQL ("CREATE INDEX plate_faaid ON plates (pl_faaid);");
                sqldb.execSQL ("CREATE UNIQUE INDEX plate_unique ON plates (pl_faaid,pl_descrip);");
            }
            if (!sqldb.tableExists ("rwypreloads")) {
                sqldb.execSQL ("DROP INDEX IF EXISTS rwypld_lastry;");
                sqldb.execSQL ("DROP TABLE IF EXISTS rwypreloads;");
                sqldb.execSQL ("CREATE TABLE rwypreloads (rp_faaid TEXT NOT NULL PRIMARY KEY, rp_state TEXT NOT NULL, rp_lastry INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE INDEX rwypld_lastry ON rwypreloads (rp_lastry);");
            }

            long time = System.nanoTime ();
            sqldb.beginTransaction ();
            try {
                BufferedReader br = new BufferedReader (new InputStreamReader (is), 4096);
                int numAdded = 0;
                String csv;
                while ((csv = br.readLine ()) != null) {
                    String[] cols = Lib.QuotedCSVSplit (csv);
                    ContentValues values = new ContentValues (4);
                    values.put ("pl_state",    statecode);
                    values.put ("pl_faaid",    cols[0]);  // eg, "BVY"
                    values.put ("pl_descrip",  cols[1]);  // eg, "IAP-LOC RWY 16"
                    values.put ("pl_filename", cols[2]);  // eg, "gif_150/050/39r16.gif"
                    sqldb.insertWithOnConflict ("plates", null, values, SQLiteDatabase.CONFLICT_IGNORE);

                    values = new ContentValues (3);
                    values.put ("rp_faaid",  cols[0]);
                    values.put ("rp_state",  statecode);
                    values.put ("rp_lastry", 0);
                    sqldb.insertWithOnConflict ("rwypreloads", null, values, SQLiteDatabase.CONFLICT_IGNORE);

                    if (++ numAdded == 256) {
                        sqldb.yieldIfContendedSafely ();
                        numAdded = 0;
                    }
                }
                sqldb.setTransactionSuccessful ();
            } finally {
                sqldb.endTransaction ();
            }
            time = System.nanoTime () - time;
            Log.i (TAG, "plates " + statecode + " download time " + (time / 1000000) + " ms");
        }
    }

    /**
     * Delete all plate records (maps airport id, plate name -> gif filename) for a given state and expiration date.
     */
    private static void DeletePlates (int expdate, String statecode)
    {
        String dbname = "cycles28_" + expdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
        if (sqldb != null) {
            if (sqldb.tableExists ("plates")) {
                sqldb.execSQL ("DELETE FROM plates WHERE pl_state='" + statecode + "'");
            }
            if (sqldb.tableExists ("rwypreloads")) {
                sqldb.execSQL ("DELETE FROM rwypreloads WHERE rp_state='" + statecode + "'");
            }
        }
    }

    /**
     * Download machine-generated airport diagram georef data for the given cycle.
     */
    private static void DownloadMachineAPDGeoRefs (String servername, int expdate, String statecode)
            throws IOException
    {
        DownloadStuffMachineAPDGeoRefs dsmgr = new DownloadStuffMachineAPDGeoRefs ();
        dsmgr.dbname = "cycles28_" + expdate + ".db";
        dsmgr.statecode = statecode;
        dsmgr.DownloadWhat (servername);
    }

    public static class DownloadStuffMachineAPDGeoRefs extends DownloadStuff {
        public String dbname;
        public String statecode;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("apdgeorefs")) {
                sqldb.execSQL ("CREATE TABLE apdgeorefs (gr_icaoid TEXT NOT NULL, gr_state TEXT NOT NULL, gr_tfwa REAL NOT NULL, gr_tfwb REAL NOT NULL, gr_tfwc REAL NOT NULL, gr_tfwd REAL NOT NULL, gr_tfwe REAL NOT NULL, gr_tfwf REAL NOT NULL, gr_wfta REAL NOT NULL, gr_wftb REAL NOT NULL, gr_wftc REAL NOT NULL, gr_wftd REAL NOT NULL, gr_wfte REAL NOT NULL, gr_wftf REAL NOT NULL);");
                sqldb.execSQL ("CREATE UNIQUE INDEX apdgeorefbyicaoid ON apdgeorefs (gr_icaoid);");
            }

            long time = System.nanoTime ();
            sqldb.beginTransaction ();
            try {
                BufferedReader br = new BufferedReader (new InputStreamReader (is), 4096);
                int numAdded = 0;
                String csv;
                while ((csv = br.readLine ()) != null) {
                    String[] cols = Lib.QuotedCSVSplit (csv);
                    ContentValues values = new ContentValues (14);
                    values.put ("gr_icaoid", cols[0]);   // eg, "KBVY"
                    values.put ("gr_state", statecode);  // eg, "MA"
                    values.put ("gr_tfwa", Float.parseFloat (cols[ 1]));
                    values.put ("gr_tfwb", Float.parseFloat (cols[ 2]));
                    values.put ("gr_tfwc", Float.parseFloat (cols[ 3]));
                    values.put ("gr_tfwd", Float.parseFloat (cols[ 4]));
                    values.put ("gr_tfwe", Float.parseFloat (cols[ 5]));
                    values.put ("gr_tfwf", Float.parseFloat (cols[ 6]));
                    values.put ("gr_wfta", Float.parseFloat (cols[ 7]));
                    values.put ("gr_wftb", Float.parseFloat (cols[ 8]));
                    values.put ("gr_wftc", Float.parseFloat (cols[ 9]));
                    values.put ("gr_wftd", Float.parseFloat (cols[10]));
                    values.put ("gr_wfte", Float.parseFloat (cols[11]));
                    values.put ("gr_wftf", Float.parseFloat (cols[12]));
                    sqldb.insertWithOnConflict ("apdgeorefs", null, values, SQLiteDatabase.CONFLICT_IGNORE);

                    if (++ numAdded == 256) {
                        sqldb.yieldIfContendedSafely ();
                        numAdded = 0;
                    }
                }
                sqldb.setTransactionSuccessful ();
            } finally {
                sqldb.endTransaction ();
            }
            time = System.nanoTime () - time;
            Log.i (TAG, "apdgeorefs " + statecode + " download time " + (time / 1000000) + " ms");
        }
    }

    /**
     * Delete all airport diagram machine-detected georeference records for a given state and expiration date.
     */
    private static void DeleteMachineAPDGeoRefs (int expdate, String statecode)
    {
        String dbname = "cycles28_" + expdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);

        if ((sqldb != null) && sqldb.tableExists ("apdgeorefs")) {
            sqldb.execSQL ("DELETE FROM apdgeorefs WHERE gr_state='" + statecode + "'");
        }
    }

    /**
     * Download machine-generated instrument approach plate georef data for the given cycle.
     */
    private static void DownloadMachineIAPGeoRefs (String servername, int expdate, String statecode)
            throws IOException
    {
        DownloadStuffMachineIAPGeoRefs dsmgr = new DownloadStuffMachineIAPGeoRefs ();
        dsmgr.dbname = "cycles28_" + expdate + ".db";
        dsmgr.statecode = statecode;
        dsmgr.DownloadWhat (servername);
    }

    public static class DownloadStuffMachineIAPGeoRefs extends DownloadStuff {
        public String dbname;
        public String statecode;

        @Override
        public void DownloadContents (InputStream is) throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (! sqldb.tableExists ("iapgeorefs")) {
                sqldb.execSQL ("CREATE TABLE iapgeorefs (gr_icaoid TEXT NOT NULL, gr_state TEXT NOT NULL, gr_plate TEXT NOT NULL, gr_waypt TEXT NOT NULL, gr_bmpixx INTEGER NOT NULL, gr_bmpixy INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE INDEX iapgeorefbyplate ON iapgeorefs (gr_icaoid,gr_plate);");
            }

            long time = System.nanoTime ();
            sqldb.beginTransaction ();
            try {
                BufferedReader br = new BufferedReader (new InputStreamReader (is), 4096);
                int numAdded = 0;
                String csv;
                while ((csv = br.readLine ()) != null) {
                    String[] cols = Lib.QuotedCSVSplit (csv);
                    String icaoid = cols[0];
                    String plate  = cols[1];
                    String waypt  = cols[2];

                    // see if record already exists with same ICAOID/PLATE/WAYPT, ignore the incoming record
                    Cursor result = sqldb.query (
                            "iapgeorefs", columns_gr_bmpixx,
                            "gr_icaoid=? AND gr_plate=? AND gr_waypt=?", new String[] { icaoid, plate, waypt },
                            null, null, null, "1");
                    boolean dup;
                    try {
                        dup = result.moveToFirst ();
                    } finally {
                        result.close ();
                    }
                    if (dup) continue;

                    // not already there, insert the new record
                    ContentValues values = new ContentValues (6);
                    values.put ("gr_icaoid", icaoid);     // eg, "KBVY"
                    values.put ("gr_state",  statecode);  // eg, "MA"
                    values.put ("gr_plate",  plate);      // eg, "IAP-LOC RWY 16"
                    values.put ("gr_waypt",  waypt);      // eg, "TAITS"
                    values.put ("gr_bmpixx", Integer.parseInt (cols[3]));
                    values.put ("gr_bmpixy", Integer.parseInt (cols[4]));
                    sqldb.insertWithOnConflict ("iapgeorefs", null, values, SQLiteDatabase.CONFLICT_IGNORE);

                    // if we have added a handful, flush them out
                    if (++ numAdded == 256) {
                        sqldb.yieldIfContendedSafely ();
                        numAdded = 0;
                    }
                }
                sqldb.setTransactionSuccessful ();
            } finally {
                sqldb.endTransaction ();
            }
            time = System.nanoTime () - time;
            Log.i (TAG, "iapgeorefs " + statecode + " download time " + (time / 1000000) + " ms");
        }
    }

    /**
     * Delete all instrument apporach procedure machine-detected georeference records for a given state and expiration date.
     */
    private static void DeleteMachineIAPGeoRefs (int expdate, String statecode)
    {
        String dbname = "cycles28_" + expdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);

        if ((sqldb != null) && sqldb.tableExists ("iapgeorefs")) {
            sqldb.execSQL ("DELETE FROM iapgeorefs WHERE gr_state='" + statecode + "'");
        }
    }

    /**
     * If we have all the new tables and states of the cycles28_expdate.db file,
     * delete any old versions.
     */
    private static void MaybeDeleteOldCycles28 ()
    {
        String[] dbnames = SQLiteDBs.Enumerate ();
        for (int i = 0; i < dbnames.length; i ++) {
            String dbnamei = dbnames[i];
            if (dbnamei == null) continue;
            if (!dbnamei.startsWith ("cycles28_")) continue;
            for (int j = 0; j < dbnames.length; j ++ ) {
                String dbnamej = dbnames[j];
                if (dbnamej == null) continue;
                if (!dbnamej.startsWith ("cycles28_")) continue;
                if (dbnamei.compareTo (dbnamej) > 0) {
                    SQLiteDBs sqldbi = SQLiteDBs.open (dbnamei);
                    if (sqldbi == null) continue;
                    SQLiteDBs sqldbj = SQLiteDBs.open (dbnamej);
                    if (sqldbj == null) continue;

                    // it's ok to kill J if I has all the tables that J has
                    Cursor cursorj = sqldbj.query (
                            "sqlite_master", columns_name,
                            "type='table'", null,
                            null, null, null, null);
                    boolean killj = true;
                    try {
                        if (cursorj.moveToFirst ()) {
                            do {
                                String tablej = cursorj.getString (0);
                                killj &= sqldbi.tableExists (tablej);
                            } while (cursorj.moveToNext ());
                        }
                    } finally {
                        cursorj.close ();
                    }

                    // and I must have all the states that J has in order to kill J
                    if (killj && sqldbi.tableExists ("plates") && sqldbj.tableExists ("plates")) {
                        cursorj = sqldbj.query (
                                true, "plates", columns_pl_state,
                                null, null, null, null, null, null);
                        try {
                            if (cursorj.moveToFirst ()) {
                                do {
                                    String statej = cursorj.getString (0);
                                    Cursor cursori = sqldbi.query (
                                            "plates", columns_pl_faaid,
                                            "pl_state=?", new String[] { statej },
                                            null, null, null, "1");
                                    try {
                                        killj &= cursori.moveToFirst ();
                                    } finally {
                                        cursori.close ();
                                    }
                                } while (cursorj.moveToNext ());
                            }
                        } finally {
                            cursorj.close ();
                        }
                    }

                    // I contains everything J has, so kill J
                    if (killj) {
                        sqldbj.markForDelete ();
                        SQLiteDBs.CloseAll ();
                        dbnames[j] = null;
                    }
                }
            }
        }
    }

    /**
     * Download a file from web server and do various things with it
     */
    public static abstract class DownloadStuff {
        // servername = name of file on server
        public void DownloadWhat (String servername) throws IOException
        {
            Log.i (TAG, "downloading from " + servername);
            int retry = 0;
            URL url = new URL (dldir + "/" + servername);
            while (true) {
                try {
                    HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
                    try {
                        httpCon.setRequestMethod ("GET");
                        httpCon.connect ();
                        int rc = httpCon.getResponseCode ();
                        if (rc != HttpURLConnection.HTTP_OK) {
                            throw new IOException ("http response code " + rc + " on " + servername);
                        }
                        InputStream is = httpCon.getInputStream ();
                        this.DownloadContents (is);
                        break;
                    } finally {
                        httpCon.disconnect ();
                    }
                } catch (IOException ioe) {
                    if (++ retry > 3) throw ioe;
                    Log.i (TAG, "retrying download of " + servername, ioe);
                }
            }
        }

        /**
         * This says what to do with the contents.
         */
        public abstract void DownloadContents (InputStream is) throws IOException;
    }
}
