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
import android.os.Handler;
import android.os.Message;
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Display a menu to download database and charts.
 */
@SuppressLint("ViewConstructor")
public class MaintView
        extends LinearLayout
        implements Handler.Callback, WairToNow.CanBeMainView, DialogInterface.OnCancelListener,
        CompoundButton.OnCheckedChangeListener {
    public  final static String TAG = "WairToNow";
    public  final static String dldir = "http://www.outerworldapps.com/WairToNow";
    public  final static int NWARNDAYS = 5;
    public  final static int INDEFINITE = 99999999;

    public static int deaddate;     // yyyymmdd of when charts are expired
    public static int warndate;     // yyyymmdd of when charts about to be expired

    private boolean updateDLProgSent;
    private Category enrCategory;
    private Category helCategory;
    private Category otherCategory;
    private Category secCategory;
    private Category tacCategory;
    private Category wacCategory;
    private CheckExpdateThread checkExpdateThread;
    private DownloadButton downloadButton;
    private DownloadThread downloadThread;
    private Handler maintViewHandler;
    public  HashMap<String,String[]> chartedLims;
    private LinkedList<Category> allCategories = new LinkedList<> ();
    private LinkedList<Downloadable> allDownloadables = new LinkedList<> ();
    public  WairToNow wairToNow;
    private ProgressDialog downloadProgress;
    private ScrollView itemsScrollView;
    private StateMapView stateMapView;
    private TextView runwayDiagramDownloadStatus;
    private Thread guiThread;
    private UnloadButton unloadButton;
    private UnloadThread unloadThread;
    private volatile boolean downloadCancelled;
    private WaypointsCheckBox waypointsCheckBox;

    private static final int MaintViewHandlerWhat_OPENDLPROG   =  0;
    private static final int MaintViewHandlerWhat_UPDATEDLPROG =  1;
    private static final int MaintViewHandlerWhat_CLOSEDLPROG  =  2;
    private static final int MaintViewHandlerWhat_DLCOMPLETE   =  3;
    private static final int MaintViewHandlerWhat_UNCHECKBOX   =  4;
    private static final int MaintViewHandlerWhat_HAVENEWCHART =  5;
    private static final int MaintViewHandlerWhat_REMUNLDCHART =  6;
    private static final int MaintViewHandlerWhat_UNLDDONE     =  7;
    private static final int MaintViewHandlerWhat_DLERROR      =  8;
    private static final int MaintViewHandlerWhat_UPDRWDGMDLST =  9;
    private static final int MaintViewHandlerWhat_EXPDATECHECK = 10;

    private final static String[] columns_apt_faaid_faciluse = new String[] { "apt_faaid", "apt_faciluse" };
    private final static String[] columns_count_rp_faaid     = new String[] { "COUNT(rp_faaid)" };
    private final static String[] columns_pl_state           = new String[] { "pl_state" };
    private final static String[] columns_name               = new String[] { "name" };
    private final static String[] columns_gr_bmpixx          = new String[] { "gr_bmpixx" };
    private final static String[] columns_pl_faaid           = new String[] { "pl_faaid" };

    @SuppressLint("SetTextI18n")
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

        Category miscCategory  = new Category ("Misc");
        Category plateCategory = new Category ("Plates");
        enrCategory   = new Category ("ENR");
        helCategory   = new Category ("HEL");
        secCategory   = new Category ("SEC");
        tacCategory   = new Category ("TAC");
        wacCategory   = new Category ("WAC");
        otherCategory = new Category ("Other");

        LinearLayout ll1 = new LinearLayout (ctx);
        ll1.setOrientation (LinearLayout.HORIZONTAL);
        ll1.addView (miscCategory.selButton);
        ll1.addView (plateCategory.selButton);
        ll1.addView (enrCategory.selButton);
        ll1.addView (helCategory.selButton);
        ll1.addView (secCategory.selButton);
        ll1.addView (tacCategory.selButton);
        ll1.addView (wacCategory.selButton);
        ll1.addView (otherCategory.selButton);

        DetentHorizontalScrollView hs1 = new DetentHorizontalScrollView (ctx);
        wairToNow.SetDetentSize (hs1);
        hs1.addView (ll1);
        addView (hs1);

        itemsScrollView = new ScrollView (ctx);
        addView (itemsScrollView);

        waypointsCheckBox = new WaypointsCheckBox ();
        TopographyCheckBox topographyCheckBox = new TopographyCheckBox ();
        miscCategory.addView (waypointsCheckBox);
        miscCategory.addView (topographyCheckBox);

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
        HashMap<String, ChartCheckBox> possibleCharts = new HashMap<> ();
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
            AirChart airChart = AirChart.Factory (this, csvLine);
            ChartCheckBox chartCheckBox = new ChartCheckBox (airChart);
            airChart.enddate = 0;  // cuz we don't yet know if it is downloaded
            if (!possibleCharts.containsKey (airChart.spacenamenr)) {
                possibleCharts.put (airChart.spacenamenr, chartCheckBox);
                if (airChart.spacenamenr.contains ("TAC")) {
                    tacCategory.addView (chartCheckBox);
                } else if (airChart.spacenamenr.contains ("WAC")) {
                    wacCategory.addView (chartCheckBox);
                } else if (airChart.spacenamenr.contains ("ENR")) {
                    enrCategory.addView (chartCheckBox);
                } else if (airChart.spacenamenr.contains ("HEL")) {
                    helCategory.addView (chartCheckBox);
                } else if (airChart.spacenamenr.contains ("SEC")) {
                    secCategory.addView (chartCheckBox);
                } else {
                    otherCategory.addView (chartCheckBox);
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
        for (ChartCheckBox ccb : possibleCharts.values ()) {
            String undername = ccb.GetSpaceNameNoRev ().replace (' ', '_') + "_";
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
                        ccb.airChart.ParseChartLimitsLine (csvline);
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

    /**
     * Check all downloaded charts for expiration date.
     * If expired or about to expire, output warning dialog if there is internet connectivity.
     */
    public void ExpdateCheck ()
    {
        UpdateAllButtonColors ();
        if (checkExpdateThread == null) {
            checkExpdateThread = new CheckExpdateThread ();
            checkExpdateThread.start ();
        }
    }

    /**
     * Check expiration dates and webserver availability.
     */
    private class CheckExpdateThread extends Thread {
        @Override
        public void run ()
        {
            try {

                /*
                 * Check for internet connectivity by seeing if we can access webserver.
                 */
                URL url = new URL (dldir + "/chartlimits.php");
                HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
                try {
                    httpCon.setRequestMethod ("GET");
                    httpCon.connect ();
                    int rc = httpCon.getResponseCode ();
                    if (rc != HttpURLConnection.HTTP_OK) {
                        throw new IOException ("http response code " + rc);
                    }

                    /*
                     * Able to access webserver, might as well see if there are helicopter chart updates.
                     * If so, mark the current helicopter expiration date = updated chart effective date.
                     */
                    BufferedReader csvReader = new BufferedReader (new InputStreamReader (httpCon.getInputStream ()), 4096);
                    String csvLine;
                    while ((csvLine = csvReader.readLine ()) != null) {
                        AirChart newAirChart = AirChart.Factory (MaintView.this, csvLine);
                        AirChart oldAirChart = null;
                        for (Downloadable downloadable : allDownloadables) {
                            if (downloadable.GetSpaceNameNoRev ().equals (newAirChart.spacenamenr)) {
                                oldAirChart = downloadable.GetAirChart ();
                                break;
                            }
                        }
                        if ((oldAirChart != null) && (oldAirChart.enddate == INDEFINITE)
                                && (newAirChart.begdate > oldAirChart.begdate)) {
                            oldAirChart.enddate = newAirChart.begdate;
                        }
                    }
                } finally {
                    httpCon.disconnect ();
                    SQLiteDBs.CloseAll ();
                }

                /*
                 * Webserver accessible, update buttons and maybe output dialog warning box.
                 */
                wairToNow.runOnUiThread (new Runnable () {
                    @Override
                    public void run ()
                    {
                        int maintColor = UpdateAllButtonColors ();
                        String msg = null;
                        switch (maintColor) {
                            case Color.RED: {
                                msg = "Charts are expired";
                                break;
                            }
                            case Color.YELLOW: {
                                msg = "Charts are about to expire";
                                break;
                            }
                        }
                        if (msg != null) {
                            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                            adb.setTitle ("Chart Maint");
                            adb.setMessage (msg);
                            adb.setPositiveButton ("Update", new DialogInterface.OnClickListener () {
                                @Override
                                public void onClick (DialogInterface dialogInterface, int i)
                                {
                                    wairToNow.maintButton.DisplayNewTab ();
                                }
                            });
                            adb.setNegativeButton ("Tomorrow", null);
                            adb.show ();
                        }
                    }
                });

                /*
                 * Webserver accessible, maybe there are ACRA reports to send on.
                 */
                AcraApplication.sendReports (wairToNow);
            } catch (IOException ioe) {
                Log.i (TAG, "error probing " + dldir, ioe);
            } finally {
                SQLiteDBs.CloseAll ();

                /*
                 * Run this all again tomorrow.
                 */
                long msperday = 24 * 60 * 60 * 1000L;
                long now = System.currentTimeMillis ();
                long delay = msperday - now * msperday;
                Message msg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_EXPDATECHECK);
                maintViewHandler.sendMessageDelayed (msg, delay);
            }
        }
    }

    /**
     * Update all button and link colors based on current date vs expiration dates.
     */
    private int UpdateAllButtonColors ()
    {
        // get dates to check chart expiration dates against
        GregorianCalendar now = new GregorianCalendar ();
        deaddate = now.get (Calendar.YEAR) * 10000 + (now.get (Calendar.MONTH) -
                Calendar.JANUARY + 1) * 100 + now.get (Calendar.DAY_OF_MONTH);
        now.add (Calendar.DAY_OF_YEAR, NWARNDAYS);
        warndate = now.get (Calendar.YEAR) * 10000 + (now.get (Calendar.MONTH) -
                Calendar.JANUARY + 1) * 100 + now.get (Calendar.DAY_OF_MONTH);

        // update text color based on current date
        for (Downloadable dcb : allDownloadables) {
            int enddate = dcb.GetEndDate ();
            int textColor = DownloadLinkColor (enddate);
            dcb.UpdateSingleLinkText (textColor,
                    (enddate == 0) ? "not downloaded" :
                            (enddate == INDEFINITE) ? "valid indefinitely" :
                                    ("valid to " + enddate));
        }

        // update category button color
        int maintColor = Color.TRANSPARENT;
        for (Category cat : allCategories) {
            int catColor = cat.UpdateButtonColor ();
            if (cat != wacCategory) {
                maintColor = ComposeButtonColor (maintColor, catColor);
            }
        }

        // update maint button color
        // exclude WACs cuz we don't have any updates
        wairToNow.maintButton.setRingColor (maintColor);
        return maintColor;
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

    @Override  // CanBeMainView
    public int GetOrientation ()
    {
        // force portrait, ie, disallow automatic flipping so it won't restart the app
        // in the middle of downloading some chart just because the screen got tilted.
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    /**
     * This screen is about to become active.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        UpdateAllButtonColors ();

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
                updateDLProgSent = false;
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
                updateDLProgSent = false;
                if (downloadProgress != null) {
                    if (msg.arg2 != 0) downloadProgress.setMax (msg.arg2);
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
                wairToNow.chartView.DownloadComplete ();
                UpdateAllButtonColors ();

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
                UpdateAllButtonColors ();
                break;
            }
            case MaintViewHandlerWhat_UNLDDONE: {
                if (unloadButton.unloadAlertDialog != null) {
                    unloadButton.unloadAlertDialog.dismiss ();
                    unloadButton.unloadAlertDialog = null;
                }
                wairToNow.chartView.DownloadComplete ();
                UpdateAllButtonColors ();

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
            case MaintViewHandlerWhat_EXPDATECHECK: {
                checkExpdateThread = null;
                ExpdateCheck ();
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
    @SuppressLint("SetTextI18n")
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
        String dbname = "plates_" + expdate + ".db";
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
        public RingedButton selButton;

        public Category (String name)
        {
            super (wairToNow);
            setOrientation (VERTICAL);
            selButton = new RingedButton (wairToNow);
            selButton.setOnClickListener (this);
            selButton.setText (name);
            wairToNow.SetTextSize (selButton);
            selButton.setVisibility (GONE);
            allCategories.addLast (this);
        }

        /**
         * Set what the button color should be based on current date
         * vs expiration dates of all items in this category.
         * @return button color
         */
        public int UpdateButtonColor ()
        {
            int color = UpdateButtonColor (Color.TRANSPARENT, this);
            selButton.setRingColor (color);
            return color;
        }

        private int UpdateButtonColor (int color, ViewGroup vg)
        {
            for (int i = vg.getChildCount (); -- i >= 0;) {
                View v = vg.getChildAt (i);
                if (v instanceof Downloadable) {
                    Downloadable dcb = (Downloadable) v;
                    int enddate = dcb.GetEndDate ();
                    int textColor = DownloadLinkColor (enddate);
                    color = ComposeButtonColor (color, textColor);
                } else if (v instanceof ViewGroup) {
                    color = UpdateButtonColor (color, (ViewGroup) v);
                }
            }
            return color;
        }

        @Override  // LinearLayout
        public void addView (View v)
        {
            super.addView (v);
            selButton.setVisibility (VISIBLE);
        }

        @Override  // OnClickListener
        public void onClick (View v)
        {
            for (Category cat : allCategories) {
                cat.selButton.setTextColor ((cat == this) ? Color.RED : Color.BLACK);
            }

            itemsScrollView.removeAllViews ();
            itemsScrollView.addView (this);
        }
    }

    private static int ComposeButtonColor (int curColor, int newColor)
    {
        if (newColor == Color.RED) curColor = Color.RED;
        if (newColor == Color.YELLOW && curColor != Color.RED) curColor = Color.YELLOW;
        return curColor;
    }

    /**
     * Use an appropriate color:
     *    WHITE  : not loaded
     *    GREEN  : good for at least NWARNDAYS more days
     *    YELLOW : expires within NWARNDAYS days
     *    RED    : expires in less than 1 day
     */
    public static int DownloadLinkColor (int enddate)
    {
        if (enddate == 0) return Color.WHITE;
        if (enddate < deaddate) return Color.RED;
        if (enddate < warndate) return Color.YELLOW;
        return Color.GREEN;
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
        String GetSpaceNameNoRev ();
        boolean IsChecked ();
        void CheckBox ();
        void UncheckBox ();
        int GetEndDate ();
        boolean HasSomeUnloadableCharts ();
        void DownloadComplete (String csvName) throws IOException;
        void RemovedDownloadedChart ();
        void UpdateSingleLinkText (int c, String t);
        AirChart GetAirChart ();
    }

    /**
     * These download checkboxes are a checkbox followed by a text string.
     */
    public abstract class DownloadCheckBox extends LinearLayout implements Downloadable {
        protected CheckBox checkBox;    // checkbox to enable {down,un}loading
        protected TextView linkText;    // displays chart name and expiration date strings

        public DownloadCheckBox ()
        {
            super (wairToNow);
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
        public boolean IsChecked () { return checkBox.isChecked (); }
        public void CheckBox () { checkBox.setChecked (true); }
        public void UncheckBox () { checkBox.setChecked (false); }
        public AirChart GetAirChart () { return null; }

        public abstract boolean HasSomeUnloadableCharts ();
        public abstract void DownloadComplete (String csvName) throws IOException;
        public abstract void RemovedDownloadedChart ();

        @SuppressLint("SetTextI18n")
        public void UpdateSingleLinkText (int c, String t)
        {
            linkText.setTextColor (c);
            linkText.setText (GetSpaceNameNoRev () + ": " + t);
        }
    }

    /**
     * Enable download topography zip files.
     * datums/topo/{-90..89}.zip
     */
    private class TopographyCheckBox extends DownloadCheckBox {

        @Override  // DownloadCheckBox
        public int GetEndDate ()
        {
            return GetTopographyExpDate ();
        }

        @Override  // DownloadCheckBox
        public String GetSpaceNameNoRev ()
        {
            return "Topography";
        }

        @Override  // DownloadCheckBox
        public boolean HasSomeUnloadableCharts ()
        {
            return GetEndDate () != 0;
        }

        @Override  // DownloadCheckBox
        public void DownloadComplete (String csvName)
        { }

        @Override  // DownloadCheckBox
        public void RemovedDownloadedChart ()
        { }
    }

    public static int GetTopographyExpDate ()
    {
        // see if we have all 180 /datums/topo/<ilatdeg>.zip files
        String[] topoNames = new File (WairToNow.dbdir + "/datums/topo").list ();
        if (topoNames == null) return 0;
        int numZipFiles = 0;
        for (String topoName : topoNames) {
            if (topoName.endsWith (".zip")) {
                int i = topoName.lastIndexOf ('/') + 1;
                try {
                    int ilatdeg = Integer.parseInt (topoName.substring (i, topoName.length () - 4));
                    if ((ilatdeg >= -90) && (ilatdeg < 90)) numZipFiles ++;
                } catch (NumberFormatException nfe) {
                    Lib.Ignored ();
                }
            }
        }

        // if so, indefinite expiration, else download needed
        return (numZipFiles == 180) ? INDEFINITE : 0;
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
        private int enddate;

        public WaypointsCheckBox ()
        {
            enddate = GetWaypointExpDate ();
        }

        @Override  // DownloadCheckBox
        public int GetEndDate ()
        {
            return enddate;
        }

        @Override  // DownloadCheckBox
        public String GetSpaceNameNoRev ()
        {
            return "Waypoints";
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
        public void RemovedDownloadedChart ()
        {
            enddate = 0;
        }

        @Override  // DownloadCheckBox
        public void UncheckBox ()
        {
            super.UncheckBox ();
            enddate = GetWaypointExpDate ();
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
        int enddate_database = 0;

        String[] dbnames = SQLiteDBs.Enumerate ();
        for (String dbname : dbnames) {
            if (dbname.startsWith ("waypoints_") && dbname.endsWith (".db")) {
                int i = Integer.parseInt (dbname.substring (10, dbname.length () - 3));
                if (enddate_database < i) {
                    enddate_database = i;
                }
            }
        }

        return enddate_database;
    }

    /**
     * This is the checkbox for downloading an aeronautical chart.
     */
    private class ChartCheckBox extends DownloadCheckBox {
        public final AirChart airChart;

        public ChartCheckBox (AirChart ac)
        {
            airChart = ac;
        }

        @Override  // DownloadCheckBox
        public int GetEndDate ()
        {
            return airChart.enddate;
        }

        @Override  // DownloadCheckBox
        public String GetSpaceNameNoRev ()
        {
            return airChart.spacenamenr;
        }

        /**
         * Determine if this checkbox has some unloadable files.
         */
        @Override  // DownloadCheckBox
        public boolean HasSomeUnloadableCharts ()
        {
            return airChart.enddate != 0;
        }

        /**
         * Get corresponding air chart, if any.
         */
        @Override  // DownloadCheckBox
        public AirChart GetAirChart () { return airChart; }

        /**
         * Download has completed for a chart.
         *
         * @param csvName = name of .csv file for a chart
         *                eg, "charts/New_York_86.csv"
         */
        @Override  // DownloadCheckBox
        public void DownloadComplete (String csvName)
                throws IOException
        {
            BufferedReader reader = new BufferedReader (new FileReader (WairToNow.dbdir + "/" + csvName), 256);
            try {
                String csvLine = reader.readLine ();
                airChart.ParseChartLimitsLine (csvLine);
            } finally {
                reader.close ();
            }
        }

        /**
         * Chart files have been removed from flash.
         */
        @Override  // DownloadCheckBox
        public void RemovedDownloadedChart ()
        {
            airChart.enddate = 0;
        }
    }

    /**
     * Iterate through air charts, downloaded or not.
     */
    public Iterator<AirChart> GetAirChartIterator ()
    {
        return new AirChartIterator ();
    }
    private class AirChartIterator implements Iterator<AirChart>
    {
        private AirChart nextOne = null;
        private Iterator<Downloadable> pcit = allDownloadables.iterator ();

        public void remove ()
        {
            throw new RuntimeException ("can't remove charts");
        }
        public AirChart next ()
        {
            AirChart ac;
            while ((ac = nextOne) == null) {
                if (!hasNext ()) throw new RuntimeException ("end of list");
            }
            nextOne = null;
            return ac;
        }
        public boolean hasNext ()
        {
            if (nextOne != null) return true;
            while (pcit.hasNext ()) {
                Downloadable d = pcit.next ();
                if (d instanceof ChartCheckBox) {
                    nextOne = ((ChartCheckBox) d).airChart;
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Start downloading the latest rev of the given chart.
     * @param spacenamenr = name of chart without revision number
     */
    public void StartDownloadingChart (String spacenamenr)
    {
        for (Downloadable d : allDownloadables) {
            if (d.GetSpaceNameNoRev ().equals (spacenamenr)) {

                // pretend like the user clicked the checkbox to download this chart
                d.CheckBox ();

                // pretend like the user clicked the Download button
                downloadButton.onClick (null);
            }
        }
    }

    /**
     * Start downloading state-specific info.
     */
    public void StateDwnld (String state, Runnable done)
    {
        stateMapView.StartDwnld (state, done);
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
                //noinspection deprecation
                AbsoluteLayout al = new AbsoluteLayout (wairToNow);

                for (StateCheckBox sb : stateCheckBoxes.values ()) {
                    sb.Rebuild ();
                    //noinspection deprecation
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

        public void StartDwnld (String state, Runnable done)
        {
            stateCheckBoxes.get (state).StartDwnld (done);
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
        private LinkedList<Runnable> whenDoneRun = new LinkedList<> ();
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
        }

        public void StartDwnld (Runnable done)
        {
            if (done != null) whenDoneRun.addLast (done);
            cb.setChecked (true);
            downloadButton.onClick (null);
        }

        // Downloadable implementation
        public String GetSpaceNameNoRev () { return "State " + ss; }
        public boolean IsChecked () { return cb.isChecked (); }
        public void CheckBox ()
        {
            cb.setChecked (true);
        }
        public AirChart GetAirChart () { return null; }
        public void UncheckBox ()
        {
            cb.setChecked (false);
            enddate = GetPlatesExpDate (ss);
        }
        public int GetEndDate () { return enddate; }
        public boolean HasSomeUnloadableCharts ()
        {
            return enddate != 0;
        }
        public void DownloadComplete (String csvName) throws IOException
        {
            while (!whenDoneRun.isEmpty ()) {
                Runnable done = whenDoneRun.remove ();
                done.run ();
            }
        }

        /**
         * The named chart has been removed from list of downloaded charts.
         */
        @Override  // DownloadCheckBox
        public void RemovedDownloadedChart ()
        {
            // nothing downloaded any more
            enddate = 0;
        }

        @SuppressLint("SetTextI18n")
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
            if (dbname.startsWith ("plates_") && dbname.endsWith (".db")) {
                int expdate = Integer.parseInt (dbname.substring (7, dbname.length () - 3));
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
            if (dbname.startsWith ("plates_") && dbname.endsWith (".db")) {
                int expdate = Integer.parseInt (dbname.substring (7, dbname.length () - 3));
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
        @SuppressLint("SetTextI18n")
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

        @SuppressLint("SetTextI18n")
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
                DownloadOutlinesTXT ();

                for (Downloadable dcb : allDownloadables) {
                    if (downloadCancelled) break;
                    if (!dcb.IsChecked ()) continue;

                    String dcbspacename = dcb.GetSpaceNameNoRev ();
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

                    // display initial progress box showing 0/nlines complete so far
                    dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_OPENDLPROG, nlines, 0,
                                                            "Downloading " + dcbspacename);
                    maintViewHandler.sendMessage (dlmsg);

                    // download each file listed in the file list one by one
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
                        if (newFileName.startsWith ("datums/waypoints_") && newFileName.endsWith (".db.gz")) {
                            int expdate = Integer.parseInt (newFileName.substring (17, newFileName.length () - 6));
                            DownloadWaypoints (newFileName, expdate);
                            MaybeDeleteOldWaypointsDB ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/apdgeorefs_") && newFileName.endsWith (".csv") &&
                                ((i = newFileName.indexOf ("/", 18)) >= 0)) {
                            int expdate = Integer.parseInt (newFileName.substring (18, i));
                            String statecode = newFileName.substring (i + 1, newFileName.length () - 4);
                            DownloadMachineAPDGeoRefs (newFileName, expdate, statecode);
                            MaybeDeleteOldPlatesDB ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/iapgeorefs_") && newFileName.endsWith (".csv") &&
                                ((i = newFileName.indexOf ("/", 18)) >= 0)) {
                            int expdate = Integer.parseInt (newFileName.substring (18, i));
                            String statecode = newFileName.substring (i + 1, newFileName.length () - 4);
                            DownloadMachineIAPGeoRefs (newFileName, expdate, statecode);
                            MaybeDeleteOldPlatesDB ();
                            UpdateDownloadProgress ();
                            continue;
                        }
                        if (newFileName.startsWith ("datums/aptplates_") && newFileName.endsWith (".csv") &&
                                ((i = newFileName.indexOf ("/state/")) >= 0)) {
                            int expdate = Integer.parseInt (newFileName.substring (17, i));
                            String statecode = newFileName.substring (i + 7, newFileName.length () - 4);
                            DownloadPlates (newFileName, expdate, statecode);
                            MaybeDeleteOldPlatesDB ();
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
                String emsg = e.getMessage ();
                if (emsg == null) emsg = e.getClass ().toString ();
                dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_DLERROR, emsg);
                maintViewHandler.sendMessage (dlmsg);
            } finally {
                downloadThread = null;
                maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_DLCOMPLETE);
                SQLiteDBs.CloseAll ();
            }
        }

        private void UpdateDownloadProgress ()
        {
            if (!updateDLProgSent) {
                updateDLProgSent = true;
                Message m = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UPDATEDLPROG, nlines, 0, null);
                maintViewHandler.sendMessage (m);
            }
        }

        /**
         * Callback from DownloadFileList() for each file downloaded
         */
        @Override // DFLUpd
        public void downloaded ()
        {
            if (!updateDLProgSent) {
                updateDLProgSent = true;
                Message dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UPDATEDLPROG, ++ nlines, 0, null);
                maintViewHandler.sendMessage (dlmsg);
            }
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

    private void DownloadOutlinesTXT () throws IOException
    {
        Log.i (TAG, "downloading outlines.txt");
        String csvname = WairToNow.dbdir + "/outlines.txt";
        DownloadFile ("outlines.txt", csvname + ".tmp");
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
                        String undername    = dcb.GetSpaceNameNoRev ().replace (' ', '_');
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
            MaybeDeleteOldPlatesDB ();
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/iapgeorefs_") && filelistline.endsWith (".csv") &&
                ((i = filelistline.indexOf ("/", 18)) >= 0)) {
            int expdate = Integer.parseInt (filelistline.substring (18, i));
            String statecode = filelistline.substring (i + 1, filelistline.length () - 4);
            DeleteMachineIAPGeoRefs (expdate, statecode);
            MaybeDeleteOldPlatesDB ();
            plainfile = false;
        }
        if (filelistline.startsWith ("datums/aptplates_") && filelistline.endsWith (".csv") &&
                ((i = filelistline.indexOf ("/state/")) >= 0)) {
            int expdate = Integer.parseInt (filelistline.substring (17, i));
            String statecode = filelistline.substring (i + 7, filelistline.length () - 4);
            DeletePlates (expdate, statecode);
            MaybeDeleteOldPlatesDB ();
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
            String spacename = dcb.GetSpaceNameNoRev ();
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
        String dbname = "plates_" + expdate + ".db";
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

        @SuppressLint("SetTextI18n")
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
            public void MouseUp (float x, float y)
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

    private interface DFLUpd {
        void downloaded ();
        boolean isCancelled ();
    }

    /**
     * Perform bulk download
     * @param bulkfilelist = list of <servername,localname> files
     */
    private static void DownloadFileList (AbstractMap<String,String> bulkfilelist, DFLUpd dflupd)
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
                            dflupd.downloaded ();
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
    private void DownloadFile (String servername, String localname)
            throws IOException
    {
        DownloadStuffFile dsf = new DownloadStuffFile ();
        dsf.localname = localname;
        dsf.DownloadWhat (servername);
    }

    private class DownloadStuffFile extends DownloadStuff {
        public String localname;

        @Override
        public void DownloadContents () throws IOException
        {
            Lib.Ignored (new File (localname.substring (0, localname.lastIndexOf ('/') + 1)).mkdirs ());
            FileOutputStream os = new FileOutputStream (localname);
            byte[] buff = new byte[4096];
            try {
                int len;
                while ((len = Read (buff)) > 0) {
                    os.write (buff, 0, len);
                }
            } finally {
                os.close ();
            }
        }
    }

    /**
     * Download datums/waypoints_<expdate>.db.gz file from web server and expand it
     * @param servername = name of file on server
     * @param expdate = expiration date of waypoints data
     */
    private void DownloadWaypoints (String servername, int expdate)
            throws IOException
    {
        DownloadStuffWaypoints dsw = new DownloadStuffWaypoints ();
        dsw.dbname = "waypoints_" + expdate + ".db";
        dsw.DownloadWhat (servername);
    }

    private class DownloadStuffWaypoints extends DownloadStuff {
        public String dbname;

        @Override
        public void DownloadContents () throws IOException
        {
            if (SQLiteDBs.open (dbname) == null) {
                String dbpath = SQLiteDBs.creating (dbname);
                GZIPInputStream gis = new GZIPInputStream (this);
                FileOutputStream fos = new FileOutputStream (dbpath + ".tmp");
                try {
                    long time = System.nanoTime ();
                    byte[] buff = new byte[4096];
                    int rc;
                    while ((rc = gis.read (buff)) > 0) {
                        fos.write (buff, 0, rc);
                        UpdateWayptProgress ();
                    }
                    time = System.nanoTime () - time;
                    Log.i (TAG, "waypoints download time " + (time / 1000000) + " ms");
                } finally {
                    fos.close ();
                }
                Lib.RenameFile (dbpath + ".tmp", dbpath);
                SQLiteDBs.created (dbname);
            }
        }
    }

    /**
     * Delete older waypoints_<expdate>.db files.
     */
    private static void MaybeDeleteOldWaypointsDB ()
    {
        String[] dbnames = SQLiteDBs.Enumerate ();
        for (int i = 0; i < dbnames.length; i ++) {
            String dbnamei = dbnames[i];
            if (dbnamei == null) continue;
            if (!dbnamei.startsWith ("waypoints_")) continue;
            for (int j = 0; j < dbnames.length; j ++ ) {
                String dbnamej = dbnames[j];
                if (dbnamej == null) continue;
                if (!dbnamej.startsWith ("waypoints_")) continue;
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
    private void DownloadPlates (String servername, int expdate, String statecode)
            throws IOException
    {
        DownloadStuffPlate dsp = new DownloadStuffPlate ();
        dsp.dbname = "plates_" + expdate + ".db";
        dsp.statecode = statecode;
        dsp.DownloadWhat (servername);
    }

    private class DownloadStuffPlate extends DownloadStuff {
        public String dbname;
        public String statecode;

        @Override
        public void DownloadContents () throws IOException
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
                int numAdded = 0;

                // get list of all airports in the state and request OpenStreetMap tiles for runway diagrams
                int waypointexpdate = GetWaypointExpDate ();
                SQLiteDBs wpdb = SQLiteDBs.open ("waypoints_" + waypointexpdate + ".db");
                if (wpdb != null) {
                    Cursor result = wpdb.query (
                            true, "airports", columns_apt_faaid_faciluse,
                            "apt_state=?", new String[] { statecode },
                            null, null, null, null);
                    try {
                        if (result.moveToFirst ()) {
                            do {
                                if (result.getString (1).equals ("PU")) {
                                    ContentValues values = new ContentValues (3);
                                    values.put ("rp_faaid", result.getString (0));
                                    values.put ("rp_state", statecode);
                                    values.put ("rp_lastry", 0);
                                    sqldb.insertWithOnConflict ("rwypreloads", null, values, SQLiteDatabase.CONFLICT_IGNORE);

                                    if (++ numAdded == 256) {
                                        sqldb.yieldIfContendedSafely ();
                                        numAdded = 0;
                                    }
                                }
                            } while (result.moveToNext ());
                        }
                    } finally {
                        result.close ();
                    }
                }

                // set up records for all FAA plates for all airports in the state that have plates
                String csv;
                while ((csv = ReadLine ()) != null) {
                    String[] cols = Lib.QuotedCSVSplit (csv);
                    ContentValues values = new ContentValues (4);
                    values.put ("pl_state",    statecode);
                    values.put ("pl_faaid",    cols[0]);  // eg, "BVY"
                    values.put ("pl_descrip",  cols[1]);  // eg, "IAP-LOC RWY 16"
                    values.put ("pl_filename", cols[2]);  // eg, "gif_150/050/39r16.gif"
                    sqldb.insertWithOnConflict ("plates", null, values, SQLiteDatabase.CONFLICT_IGNORE);

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
        String dbname = "plates_" + expdate + ".db";
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
    private void DownloadMachineAPDGeoRefs (String servername, int expdate, String statecode)
            throws IOException
    {
        DownloadStuffMachineAPDGeoRefs dsmgr = new DownloadStuffMachineAPDGeoRefs ();
        dsmgr.dbname = "plates_" + expdate + ".db";
        dsmgr.statecode = statecode;
        dsmgr.DownloadWhat (servername);
    }

    private class DownloadStuffMachineAPDGeoRefs extends DownloadStuff {
        public String dbname;
        public String statecode;

        @Override
        public void DownloadContents () throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("apdgeorefs")) {
                sqldb.execSQL ("CREATE TABLE apdgeorefs (gr_icaoid TEXT NOT NULL, gr_state TEXT NOT NULL, gr_tfwa REAL NOT NULL, gr_tfwb REAL NOT NULL, gr_tfwc REAL NOT NULL, gr_tfwd REAL NOT NULL, gr_tfwe REAL NOT NULL, gr_tfwf REAL NOT NULL, gr_wfta REAL NOT NULL, gr_wftb REAL NOT NULL, gr_wftc REAL NOT NULL, gr_wftd REAL NOT NULL, gr_wfte REAL NOT NULL, gr_wftf REAL NOT NULL);");
                sqldb.execSQL ("CREATE UNIQUE INDEX apdgeorefbyicaoid ON apdgeorefs (gr_icaoid);");
            }

            long time = System.nanoTime ();
            sqldb.beginTransaction ();
            try {
                int numAdded = 0;
                String csv;
                while ((csv = ReadLine ()) != null) {
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
        String dbname = "plates_" + expdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);

        if ((sqldb != null) && sqldb.tableExists ("apdgeorefs")) {
            sqldb.execSQL ("DELETE FROM apdgeorefs WHERE gr_state='" + statecode + "'");
        }
    }

    /**
     * Download machine-generated instrument approach plate georef data for the given cycle.
     */
    private void DownloadMachineIAPGeoRefs (String servername, int expdate, String statecode)
            throws IOException
    {
        DownloadStuffMachineIAPGeoRefs dsmgr = new DownloadStuffMachineIAPGeoRefs ();
        dsmgr.dbname = "plates_" + expdate + ".db";
        dsmgr.statecode = statecode;
        dsmgr.DownloadWhat (servername);
    }

    private class DownloadStuffMachineIAPGeoRefs extends DownloadStuff {
        public String dbname;
        public String statecode;

        @Override
        public void DownloadContents () throws IOException
        {
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (! sqldb.tableExists ("iapgeorefs")) {
                sqldb.execSQL ("CREATE TABLE iapgeorefs (gr_icaoid TEXT NOT NULL, gr_state TEXT NOT NULL, gr_plate TEXT NOT NULL, gr_waypt TEXT NOT NULL, gr_bmpixx INTEGER NOT NULL, gr_bmpixy INTEGER NOT NULL);");
                sqldb.execSQL ("CREATE INDEX iapgeorefbyplate ON iapgeorefs (gr_icaoid,gr_plate);");
            }

            long time = System.nanoTime ();
            sqldb.beginTransaction ();
            try {
                int numAdded = 0;
                String csv;
                while ((csv = ReadLine ()) != null) {
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
        String dbname = "plates_" + expdate + ".db";
        SQLiteDBs sqldb = SQLiteDBs.open (dbname);

        if ((sqldb != null) && sqldb.tableExists ("iapgeorefs")) {
            sqldb.execSQL ("DELETE FROM iapgeorefs WHERE gr_state='" + statecode + "'");
        }
    }

    /**
     * If we have all the new tables and states of the plates_expdate.db file,
     * delete any old versions.
     */
    private static void MaybeDeleteOldPlatesDB ()
    {
        String[] dbnames = SQLiteDBs.Enumerate ();
        for (int i = 0; i < dbnames.length; i ++) {
            String dbnamei = dbnames[i];
            if (dbnamei == null) continue;
            if (!dbnamei.startsWith ("plates_")) continue;
            for (int j = 0; j < dbnames.length; j ++ ) {
                String dbnamej = dbnames[j];
                if (dbnamej == null) continue;
                if (!dbnamej.startsWith ("plates_")) continue;
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
    private abstract class DownloadStuff extends InputStream {
        private BufferedReader bufferedReader;
        private InputStream inputStream;
        private int numBytesRead;
        private int numBytesTotal;

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
                        inputStream = httpCon.getInputStream ();
                        numBytesTotal = httpCon.getContentLength ();
                        this.DownloadContents ();
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
        public abstract void DownloadContents () throws IOException;

        /**
         * Read the reply data.
         */
        protected int Read (byte[] buff) throws IOException
        {
            return this.read (buff);
        }

        protected String ReadLine () throws IOException
        {
            if (bufferedReader == null) {
                bufferedReader = new BufferedReader (new InputStreamReader (this));
            }
            return bufferedReader.readLine ();
        }

        /**
         * Update the download progress bar based on number of bytes read.
         */
        protected void UpdateWayptProgress ()
        {
            if ((numBytesTotal > 0) && !updateDLProgSent) {
                updateDLProgSent = true;
                Message m = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UPDATEDLPROG,
                        numBytesRead, numBytesTotal, null);
                maintViewHandler.sendMessage (m);
            }
        }

        /**
         * Input stream wrapper that counts bytes.
         */
        @Override  // InputStream
        public int read () throws IOException
        {
            int rc = inputStream.read ();
            if (rc >= 0) numBytesRead ++;
            return rc;
        }

        @Override  // InputStream
        public int read (@NonNull byte[] buff, int offs, int size) throws IOException
        {
            int rc = inputStream.read (buff, offs, size);
            if (rc > 0) numBytesRead += rc;
            return rc;
        }
    }
}
