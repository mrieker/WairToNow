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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static android.content.Context.MODE_PRIVATE;

/**
 * Display a menu to download database and charts.
 */
@SuppressLint("ViewConstructor")
public class MaintView
        extends LinearLayout
        implements Handler.Callback, WairToNow.CanBeMainView, DialogInterface.OnCancelListener,
        CompoundButton.OnCheckedChangeListener {
    public  final static String TAG = "WairToNow";
    public  final static String dldir = GetDLDir ();
    public  final static int NWARNDAYS = 5;
    public  final static int INDEFINITE = 99999999;

    public static int deaddate;     // yyyymmdd of when charts are expired
    public static int warndate;     // yyyymmdd of when charts about to be expired

    private boolean checkExpdateAlarmed;
    private boolean downloadAgain;
    private boolean updateDLProgSent;
    private Category enrCategory;
    private Category helCategory;
    private Category miscCategory;
    private Category otherCategory;
    private Category secCategory;
    private Category tacCategory;
    private CheckExpdateThread checkExpdateThread;
    private DownloadButton downloadButton;
    private DownloadThread downloadThread;
    private Handler maintViewHandler;
    public  HashMap<String,String[]> chartedLims;
    private LinkedList<Category> allCategories = new LinkedList<> ();
    private LinkedList<Downloadable> allDownloadables = new LinkedList<> ();
    private final Object postDLProgLock = new Object ();
    private ScrollView itemsScrollView;
    private StateMapView stateMapView;
    private String postDLProcText;
    private String updateDLProgTitle;
    private TextView runwayDiagramDownloadStatus;
    private Thread guiThread;
    private UnloadButton unloadButton;
    private UnloadThread unloadThread;
    public  WairToNow wairToNow;
    private WaypointsCheckBox waypointsCheckBox;

    private static final int MaintViewHandlerWhat_OPENDLPROG   = 0;
    private static final int MaintViewHandlerWhat_UPDATEDLPROG = 1;
    private static final int MaintViewHandlerWhat_POSTDLPROG   = 2;
    private static final int MaintViewHandlerWhat_DLCOMPLETE   = 3;
    private static final int MaintViewHandlerWhat_UNCHECKBOX   = 4;
    private static final int MaintViewHandlerWhat_UNLDDONE     = 5;
    private static final int MaintViewHandlerWhat_DLERROR      = 6;
    private static final int MaintViewHandlerWhat_UPDRWDGMDLST = 7;
    private static final int MaintViewHandlerWhat_OPENDELPROG  = 8;

    private final static String PARTIAL = ".tmp";
    private final static String[] columns_apt_faaid_faciluse = new String[] { "apt_faaid", "apt_faciluse" };
    private final static String[] columns_count_rp_faaid     = new String[] { "COUNT(rp_faaid)" };
    private final static String[] columns_cp_legs            = new String[] { "cp_legs" };

    // get the URL we download from
    // this URL contains things like chartedlims.csv, chartlimits.php, etc
    private static String GetDLDir ()
    {
        try {
            BufferedReader br = new BufferedReader (
                    new FileReader (WairToNow.dbdir + "/dlurl.txt"), 256);
            try {
                String dlurl = br.readLine ();
                dlurl = (dlurl == null) ? "" : dlurl.trim ();
                if (!dlurl.equals ("")) return dlurl;
            } finally {
                br.close ();
            }
        } catch (FileNotFoundException fnfe) {
            Lib.Ignored ();
        } catch (IOException ioe) {
            Log.e (TAG, "error reading dlurl.txt", ioe);
        }
        return "http://www.outerworldapps.com/WairToNow";
    }

    @SuppressLint("SetTextI18n")
    public MaintView (WairToNow ctx)
            throws InterruptedException, IOException
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

        runwayDiagramDownloadStatus = new TextView (ctx);
        runwayDiagramDownloadStatus.setVisibility (GONE);
        wairToNow.SetTextSize (runwayDiagramDownloadStatus);
        addView (runwayDiagramDownloadStatus);

        downloadButton = new DownloadButton (ctx);
        addView (downloadButton);

        unloadButton = new UnloadButton (ctx);
        addView (unloadButton);

        miscCategory  = new Category ("Misc");
        Category plateCategory = new Category ("Plates");
        enrCategory   = new Category ("ENR");
        helCategory   = new Category ("HEL");
        secCategory   = new Category ("SEC");
        tacCategory   = new Category ("TAC");
        otherCategory = new Category ("Other");

        LinearLayout ll1 = new LinearLayout (ctx);
        ll1.setOrientation (LinearLayout.HORIZONTAL);
        ll1.addView (miscCategory.selButton);
        ll1.addView (plateCategory.selButton);
        ll1.addView (enrCategory.selButton);
        ll1.addView (helCategory.selButton);
        ll1.addView (secCategory.selButton);
        ll1.addView (tacCategory.selButton);
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
        miscCategory.addView (new TextView (wairToNow));
        miscCategory.addView (new UpdateAllButton ());

        stateMapView = new StateMapView ();

        plateCategory.addView (stateMapView);

        enrCategory.addView (new ChartDiagView (R.drawable.low_index_us));
        secCategory.addView (new ChartDiagView (R.drawable.faa_sec_diag));

        GetChartNames ();

        miscCategory.onClick (null);

        // used by CheckExpdateThread to wake itself back up
        BroadcastReceiver receiver = new BroadcastReceiver () {
            @Override
            public void onReceive (Context context, Intent intent) {
                checkExpdateAlarmed = false;
                ExpdateCheck ();
            }
        };

        IntentFilter filter = new IntentFilter ();
        filter.addAction ("WairToNow.CheckExpdateThread.WakeUp");
        wairToNow.registerReceiver (receiver, filter);
    }

    private void GetChartNames ()
            throws InterruptedException, IOException
    {
        /*
         * Read pixel and lat/lon limits that tell where the legend areas are.
         */
        chartedLims = new HashMap<> ();
        FileReader limFileReader;
        try {
            limFileReader = new FileReader (WairToNow.dbdir + "/chartedlims.csv");
        } catch (FileNotFoundException fnfe) {
            DownloadChartedLimsCSVThread dclt = new DownloadChartedLimsCSVThread ();
            dclt.start ();
            dclt.join ();
            if (dclt.ioe != null) throw dclt.ioe;
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
            DownloadChartLimitsCSVThread dclt = new DownloadChartLimitsCSVThread ();
            dclt.start ();
            dclt.join ();
            if (dclt.ioe != null) throw dclt.ioe;
            csvFileReader = new FileReader (WairToNow.dbdir + "/chartlimits.csv");
        }
        BufferedReader csvReader = new BufferedReader (csvFileReader, 4096);
        String csvLine;
        while ((csvLine = csvReader.readLine ()) != null) {
            AirChart airChart = AirChart.Factory (this, csvLine);
            ChartCheckBox chartCheckBox = new ChartCheckBox (airChart);
            if (!possibleCharts.containsKey (airChart.spacenamenr)) {
                possibleCharts.put (airChart.spacenamenr, chartCheckBox);
                if (airChart.spacenamenr.contains ("TAC")) {
                    tacCategory.addView (chartCheckBox);
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
    }

    private class DownloadChartedLimsCSVThread extends Thread {
        public IOException ioe;
        @Override
        public void run ()
        {
            try {
                DownloadChartedLimsCSV ();
            } catch (IOException ioe) {
                this.ioe = ioe;
            }
        }
    }

    private class DownloadChartLimitsCSVThread extends Thread {
        public IOException ioe;
        @Override
        public void run ()
        {
            try {
                DownloadChartLimitsCSV ();
            } catch (IOException ioe) {
                this.ioe = ioe;
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
            int nextrunms = 60 * 60 * 1000;
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

                        // all csv lines end with ...,begdate,enddate,space name with revno
                        String[] csvParts = Lib.QuotedCSVSplit (csvLine);
                        int np = csvParts.length;
                        int newbegdate;
                        try {
                            newbegdate = Integer.parseInt (csvParts[np - 3]);
                        } catch (NumberFormatException nfe) {
                            continue;
                        }
                        String newnamewr = csvParts[np-1];
                        int ls = newnamewr.lastIndexOf (' ');
                        if (ls < 0) continue;
                        String newnamenr = newnamewr.substring (0, ls);

                        // find existing chart with same name excluding revno
                        AirChart oldAirChart = null;
                        for (Downloadable downloadable : allDownloadables) {
                            if (downloadable.GetSpaceNameNoRev ().equals (newnamenr)) {
                                oldAirChart = downloadable.GetAirChart ();
                                break;
                            }
                        }

                        // if found and it has an indefinite end date and there is a newer
                        // revision waiting, set existing end date to newer one's begin date
                        if ((oldAirChart != null) && (oldAirChart.enddate == INDEFINITE)
                                && (newbegdate > oldAirChart.begdate)) {
                            oldAirChart.enddate = newbegdate;
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
                            GregorianCalendar now = new GregorianCalendar ();
                            final int today = now.get (Calendar.YEAR) * 10000 +
                                    (now.get (Calendar.MONTH) - Calendar.JANUARY + 1) * 100 +
                                    now.get (Calendar.DAY_OF_MONTH);

                            final SharedPreferences prefs = wairToNow.getPreferences (MODE_PRIVATE);
                            final int chartMaintDelay = prefs.getInt ("chartMaintDelay", 0);

                            // don't prompt more than once per day
                            if (today > chartMaintDelay) {
                                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                                adb.setTitle ("Chart Maint");
                                adb.setMessage (msg);
                                adb.setPositiveButton ("Update", new DialogInterface.OnClickListener () {
                                    @Override
                                    public void onClick (DialogInterface dialogInterface, int i)
                                    {
                                        miscCategory.onClick (null);
                                        wairToNow.maintButton.DisplayNewTab ();
                                    }
                                });
                                adb.setNegativeButton ("Tomorrow", new DialogInterface.OnClickListener () {
                                    @Override
                                    public void onClick (DialogInterface dialogInterface, int i)
                                    {
                                        // if user says don't prompt again until tomorrow,
                                        // remember which day we last prompted on
                                        SharedPreferences.Editor editr = prefs.edit ();
                                        editr.putInt ("chartMaintDelay", today);  // yyyymmdd
                                        editr.apply ();
                                    }
                                });
                                adb.show ();
                            }
                        }
                    }
                });

                /*
                 * Webserver accessible, maybe there are ACRA reports to send on.
                 */
                AcraApplication.sendReports (wairToNow);

                /*
                 * Don't need to run for another half day.
                 */
                nextrunms *= 12;
            } catch (IOException ioe) {
                Log.i (TAG, "error probing " + dldir, ioe);
            } finally {
                SQLiteDBs.CloseAll ();

                /*
                 * Call back via alarm in an hour or in half a day.
                 * Use an alarm so the time counts when screen is off.
                 */
                final long nextrunat = System.currentTimeMillis () + nextrunms;
                wairToNow.runOnUiThread (new Runnable () {
                    @Override
                    public void run () {
                        checkExpdateThread = null;

                        if (!checkExpdateAlarmed) {
                            checkExpdateAlarmed = true;

                            Intent intent = new Intent ();
                            intent.setAction ("WairToNow.CheckExpdateThread.WakeUp");
                            PendingIntent pendint = PendingIntent.getBroadcast (wairToNow, 0, intent, 0);

                            AlarmManager am = (AlarmManager) wairToNow.getSystemService (Context.ALARM_SERVICE);
                            if (am == null) throw new NullPointerException ();
                            am.set (AlarmManager.RTC, nextrunat, pendint);
                        }
                    }
                });
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
            maintColor = ComposeButtonColor (maintColor, catColor);
        }

        // update maint button color
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

            // about to start downloading, open a dialog box
            case MaintViewHandlerWhat_OPENDLPROG: {
                updateDLProgSent = false;
                updateDLProgTitle = msg.obj.toString ();
                wairToNow.downloadStatusText.setText (updateDLProgTitle);
                wairToNow.downloadStatusRow.setVisibility (VISIBLE);
                break;
            }

            // file is being downloaded, update dialog box with progress
            case MaintViewHandlerWhat_UPDATEDLPROG: {
                updateDLProgSent = false;
                StringBuilder sb = new StringBuilder ();
                sb.append (updateDLProgTitle);
                sb.append (": ");
                if (msg.arg2 > 0) {
                    long pct = Math.round (msg.arg1 * 100.0 / msg.arg2);
                    sb.append (' ');
                    sb.append (pct);
                    sb.append ("% ");
                }
                sb.append (String.format (Locale.US, "%,d / %,d", msg.arg1, msg.arg2));
                wairToNow.downloadStatusText.setText (sb);
                break;
            }

            // file is being downloaded, update dialog box with progress
            case MaintViewHandlerWhat_POSTDLPROG: {
                synchronized (postDLProgLock) {
                    String text = postDLProcText;
                    if (text != null) {
                        postDLProcText = null;
                        StringBuilder sb = new StringBuilder ();
                        sb.append (updateDLProgTitle);
                        sb.append (": ");
                        sb.append (text);
                        wairToNow.downloadStatusText.setText (sb);
                    }
                    postDLProgLock.notifyAll ();
                }
                break;
            }

            // file download completed successfully
            case MaintViewHandlerWhat_UNCHECKBOX: {
                wairToNow.downloadStatusRow.setVisibility (GONE);
                wairToNow.downloadStatusText.setText ("");
                Downloadable dcb = (Downloadable) msg.obj;
                if (msg.arg1 != 0) {
                    dcb.DownloadFileComplete ();
                } else {
                    dcb.RemovedDownloadedChart ();
                }
                dcb.UncheckBox ();
                break;
            }

            // download thread has exited
            case MaintViewHandlerWhat_DLCOMPLETE: {

                // thread has exited
                // if more downloads requested meanwhile, start it back up
                downloadThread = null;
                if (downloadAgain) {
                    downloadAgain = false;
                    downloadButton.onClick (null);
                    break;
                }

                // all done, remove status dialog
                wairToNow.downloadStatusRow.setVisibility (GONE);
                wairToNow.downloadStatusText.setText ("");

                // tell anything that cares that thread has exited
                for (Downloadable dcb : allDownloadables) {
                    dcb.DownloadThreadExited ();
                }
                wairToNow.chartView.DownloadComplete ();
                downloadButton.UpdateEnabled ();
                unloadButton.UpdateEnabled ();
                UpdateAllButtonColors ();

                // maybe some database was marked for delete in the download thread
                // (as a result of an upgrade, we delete old database files)
                // so close our handle so that it will actually delete
                SQLiteDBs.CloseAll ();
                break;
            }

            case MaintViewHandlerWhat_UNLDDONE: {
                unloadThread = null;
                Lib.dismiss (unloadButton.unloadAlertDialog);
                unloadButton.unloadAlertDialog = null;
                wairToNow.chartView.DownloadComplete ();
                UpdateAllButtonColors ();

                // maybe some database was marked for delete in the unload thread
                // so close our handle so that it will actually delete
                SQLiteDBs.CloseAll ();
                break;
            }
            case MaintViewHandlerWhat_DLERROR: {
                DlError dlerror = (DlError) msg.obj;
                AlertDialog.Builder builder = new AlertDialog.Builder (wairToNow);
                builder.setTitle ("Error downloading " +
                        ((dlerror.dcb == null) ? "files" : dlerror.dcb.GetSpaceNameNoRev ()));
                builder.setMessage (dlerror.msg);
                builder.setNegativeButton ("Cancel", new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int which)
                    {
                        Lib.dismiss (dialog);
                    }
                });
                AlertDialog dialog = builder.create ();
                dialog.show();
                downloadAgain = false;
                break;
            }
            case MaintViewHandlerWhat_UPDRWDGMDLST: {
                UpdateRunwayDiagramDownloadStatus ();
                break;
            }
            case MaintViewHandlerWhat_OPENDELPROG: {
                updateDLProgSent = false;
                wairToNow.downloadStatusText.setText (msg.obj.toString ());
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
        wairToNow.downloadCancelled = true;
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
         * Count the records in the rwypreloads table, as it is updated as tiles are downloaded.
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
     * Get zip file containing data about airports in the state
     * - plates (apt diagrams, instrument approaches, sids, stars, etc)
     * - AFD-like html files
     * - georef, cifp csv files
     * Returns null if state file not downloaded
     * @param ss = 2-letter state code, eg, "MA"
     */
    public ZipFile getStateZipFile (String ss)
            throws IOException
    {
        return stateMapView.stateCheckBoxes.get (ss).getStateZipFile ();
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
     * Clicking the 'Update All' button downloads the latest rev
     * of charts that are already downloaded with an older rev.
     */
    private class UpdateAllButton extends Button implements OnClickListener {
        @SuppressLint("SetTextI18n")
        public UpdateAllButton ()
        {
            super (wairToNow);
            setText ("Update All");
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
        }

        @Override
        public void onClick (View v)
        {
            // check all items that already have something on them
            for (Downloadable downloadable : allDownloadables) {
                if (downloadable.GetEndDate () > 0) downloadable.CheckBox ();
            }

            // make like we checked the Download button
            downloadButton.onClick (null);
        }
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
        void DownloadFiles () throws IOException;
        void DownloadFileComplete ();
        void DownloadThreadExited ();
        void DeleteDownloadedFiles (boolean all);
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

        public abstract void DownloadFiles () throws IOException;
        public abstract void DownloadFileComplete ();
        public abstract void DownloadThreadExited ();
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

        /**
         * Download the topo.zip file from the server.
         * Then split it up into the little per-latitude zips.
         */
        public void DownloadFiles () throws IOException
        {
            String topopn = WairToNow.dbdir + "/datums/topo";
            if (new File (topopn).exists ()) return;

            DownloadBigFile ("datums/topo.zip", topopn + ".zip");

            SendPostDLProc ("digesting");
            byte[] buf = new byte[32768];
            try {
                ZipFile zf = new ZipFile (topopn + ".zip");
                Lib.Ignored (new File (topopn + PARTIAL).mkdir ());
                for (Enumeration<? extends ZipEntry> it = zf.entries (); it.hasMoreElements ();) {
                    ZipEntry ze = it.nextElement ();
                    String zn = ze.getName ();
                    if (zn.startsWith ("topo/")) {
                        String fn = topopn + PARTIAL + zn.substring (4);
                        FileOutputStream fos = new FileOutputStream (fn);
                        InputStream zis = zf.getInputStream (ze);
                        for (int rc; (rc = zis.read (buf)) > 0;) {
                            fos.write (buf, 0, rc);
                        }
                        fos.close ();
                        zis.close ();
                    }
                }
                Lib.RenameFile (topopn + PARTIAL, topopn);
                Lib.Ignored (new File (topopn + ".zip").delete ());
            } catch (IOException ioe) {
                Log.w (TAG, "error unpacking topo.zip", ioe);
            }
        }

        /**
         * Download has completed
         */
        @Override  // DownloadCheckBox
        public void DownloadFileComplete ()
        { }

        @Override  // DownloadCheckBox
        public void DownloadThreadExited ()
        { }

        @Override  // DownloadCheckBox
        public void DeleteDownloadedFiles (boolean all)
        {
            // since there is only one topo version
            // delete it iff we are deleting all versions
            if (all) {
                File[] files = new File (WairToNow.dbdir + "/datums").listFiles ();
                for (File file : files) {
                    if (file.getName ().startsWith ("topo")) {
                        Lib.RecursiveDelete (file);
                    }
                }
            }
        }

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
     * datums/waypoints_<expdate>.db.gz
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

        @Override  // DownloadCheckBox
        public void DeleteDownloadedFiles (boolean all)
        {
            int latestexdate = 0;
            String latestdbname = "";

            String[] dbnames = SQLiteDBs.Enumerate ();

            // if deleting all but latest, get name of latest waypoint file
            if (!all) {
                for (String dbname : dbnames) {
                    if (dbname.startsWith ("waypoints_") && dbname.endsWith (".db")) {
                        int i = Integer.parseInt (dbname.substring (10, dbname.length () - 3));
                        if (latestexdate < i) {
                            latestexdate = i;
                            latestdbname = dbname;
                        }
                    }
                }
            }

            // delete all versions except possibly the latest version
            for (String dbname : dbnames) {
                if (dbname.startsWith ("waypoints_") && !dbname.equals (latestdbname)) {
                    SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                    if (sqldb != null) sqldb.markForDelete ();
                }
            }

            // delete any partial downloads
            if (all) {
                File[] files = new File (WairToNow.dbdir).listFiles ();
                for (File file : files) {
                    if (file.getName ().startsWith ("waypoints_")) {
                        Lib.Ignored (file.delete ());
                    }
                }
            }

            // if deleted everything, say we ain't got nothing
            if (all) enddate = 0;
        }

        /**
         * Download the waypoints_<expdate>.db.gz file from the server.
         */
        public void DownloadFiles () throws IOException
        {
            /*
             * Get name of latest waypoint file.
             */
            String servername = ReadSingleLine ("filelist.php?undername=Waypoints");
            if (!servername.startsWith ("datums/waypoints_") || !servername.endsWith (".db.gz")) {
                throw new IOException ("bad waypoint filename " + servername);
            }

            /*
             * Download that file and gunzip it iff we don't already have it.
             */
            String localname = servername.substring (7, servername.length () - 3);
            if (SQLiteDBs.open (localname) == null) {
                DownloadStuffWaypoints dsw = new DownloadStuffWaypoints ();
                dsw.dbname = localname;
                dsw.DownloadWhat (servername);
            }
        }

        /**
         * Waypoint file download has completed.
         */
        @Override  // DownloadCheckBox
        public void DownloadFileComplete ()
        { }

        @Override  // DownloadCheckBox
        public void DownloadThreadExited ()
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

        /**
         * Download datums/waypoints_<expdate>.db.gz file from web server and expand it.
         */
        private class DownloadStuffWaypoints extends DownloadStuff {
            public String dbname;

            @Override
            public void DownloadContents () throws IOException
            {
                // read and gunzip to a temp file
                String dbpath = SQLiteDBs.creating (dbname);
                GZIPInputStream gis = new GZIPInputStream (this);
                FileOutputStream fos = new FileOutputStream (dbpath + PARTIAL);
                try {
                    wairToNow.downloadStream = this;
                    byte[] buff = new byte[32768];
                    int rc;
                    while ((rc = gis.read (buff)) > 0) {
                        if (wairToNow.downloadCancelled) throw new IOException ("download cancelled");
                        fos.write (buff, 0, rc);
                        UpdateBytesProgress ();
                    }
                } finally {
                    wairToNow.downloadStream = null;
                    fos.close ();
                }

                // completely downloaded and expanded
                // rename temp file to permanent and
                // tell SQLiteDBs it has a new database file
                Lib.RenameFile (dbpath + PARTIAL, dbpath);
                SQLiteDBs.created (dbname);
            }
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
         * Delete all files for this chart.
         */
        @Override  // DownloadCheckBox
        public void DeleteDownloadedFiles (boolean all)
        {
            int latestrevno = 0;
            String latestfname = "";

            File[] files = new File (WairToNow.dbdir + "/charts/").listFiles ();
            String undername = airChart.spacenamenr.replace (' ', '_') + "_";

            // if deleting all but latest, get name of latest chart .wtn.zip file
            if (!all) {
                for (File file : files) {
                    String name = file.getName ();
                    if (name.startsWith (undername) && name.endsWith (".wtn.zip")) {
                        int revno = Integer.parseInt (name.substring (undername.length (), name.length () - 8));
                        if (latestrevno < revno) {
                            latestrevno = revno;
                            latestfname = undername + revno;  // eg, "New_York_SEC_96"
                        }
                    }
                }
            }

            // delete all .wtn.zip files for this chart except possibly the latest
            // there might also be a directory of that name with tiles we generated
            // also delete any partial downloads
            for (File file : files) {
                String name = file.getName ();
                if (name.startsWith (undername) &&                  // eg, "New_York_SEC_"
                        !name.equals (latestfname) &&               // eg, "New_York_SEC_96"
                        !name.equals (latestfname + ".wtn.zip")) {  // eg, "New_York_SEC_96.wtn.zip"
                    Lib.RecursiveDelete (file);
                }
            }
        }

        /**
         * Get corresponding air chart, if any.
         */
        @Override  // DownloadCheckBox
        public AirChart GetAirChart () { return airChart; }

        /**
         * Download the latest chartname_expdate.zip file from the server.
         */
        public void DownloadFiles () throws IOException
        {
            /*
             * Get name of latest chart zip file.
             */
            String undername = airChart.spacenamenr.replace (' ', '_');
            String servername = ReadSingleLine ("filelist.php?undername=" + undername);
            if (!servername.startsWith ("charts/" + undername + "_") || !servername.endsWith (".wtn.zip")) {
                throw new IOException ("bad waypoint filename " + servername);
            }

            /*
             * Download it from server iff we don't already have it.
             */
            String localname = WairToNow.dbdir + "/" + servername;
            if (new File (localname).exists ()) return;
            DownloadBigFile (servername, localname);
        }

        /**
         * Download has completed for a chart.
         */
        @Override  // DownloadCheckBox
        public void DownloadFileComplete ()
        {
            // close any old bitmaps and parse csv line from latest .wtn.zip file
            airChart.StartUsingLatestDownloadedRevision ();
        }

        @Override  // DownloadCheckBox
        public void DownloadThreadExited ()
        { }

        /**
         * Chart files have been removed from flash.
         */
        @Override  // DownloadCheckBox
        public void RemovedDownloadedChart ()
        {
            airChart.StartUsingLatestDownloadedRevision ();
            if (wairToNow.chartView.selectedChart == airChart) {
                wairToNow.chartView.SelectChart (null);
            }
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
     * Start downloading the latest rev of the given chart asap.
     * @param spacenamenr = name of chart without revision number
     */
    public void StartDownloadingChart (String spacenamenr)
    {
        for (Downloadable d : allDownloadables) {
            if (d.GetSpaceNameNoRev ().equals (spacenamenr)) {

                // don't download anything else cuz we want this asap
                for (Downloadable dcb : allDownloadables) dcb.UncheckBox ();

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
     * Displays a list of checkboxes for each state for downloading plates for those states.
     */
    private class StateMapView extends HorizontalScrollView {
        public  TreeMap<String,StateCheckBox> stateCheckBoxes = new TreeMap<> ();

        public StateMapView () throws IOException
        {
            super (wairToNow);

            LinearLayout ll = new LinearLayout (wairToNow);
            ll.setOrientation (LinearLayout.VERTICAL);

            AssetManager am = wairToNow.getAssets ();
            BufferedReader rdr = new BufferedReader (new InputStreamReader (am.open ("statelocation.dat")), 1024);
            String line;
            while ((line = rdr.readLine ()) != null) {
                StateCheckBox sb = new StateCheckBox (line.substring (0, 2), line.substring (3));
                stateCheckBoxes.put (sb.ss, sb);
                ll.addView (sb);
            }
            rdr.close ();

            this.addView (ll);
        }

        /**
         * Force downloading this state's info asap.
         * Call the completion routine when done (or on failure).
         */
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
        public String ss;  // two-letter state code (capital letters)
        public String fullname;  // full name string

        private ZipFile stateZipFile;

        private CheckBox cb;
        private int enddate;
        private LinkedList<Runnable> whenDoneRun = new LinkedList<> ();
        private TextView lb;

        public StateCheckBox (String s, String f)
        {
            super (wairToNow);

            ss = s;
            fullname = f;

            allDownloadables.addLast (this);

            enddate = GetPlatesExpDate (ss);

            cb = new CheckBox (wairToNow);
            cb.setOnCheckedChangeListener (MaintView.this);

            lb = new TextView (wairToNow);
            wairToNow.SetTextSize (lb);

            LinearLayout ll = new LinearLayout (wairToNow);
            ll.setOrientation (LinearLayout.HORIZONTAL);

            ll.addView (cb);
            ll.addView (lb);

            addView (ll);
        }

        /**
         * Force downloading this state's info asap.
         * Call the completion routine when done (or on failure).
         */
        public void StartDwnld (Runnable done)
        {
            if (done != null) whenDoneRun.addLast (done);
            for (Downloadable dcb : allDownloadables) dcb.UncheckBox ();
            CheckBox ();
            downloadButton.onClick (null);
        }

        public ZipFile getStateZipFile ()
                throws IOException
        {
            synchronized (this) {
                if (stateZipFile == null) {
                    enddate = GetPlatesExpDate (ss);
                    if (enddate > 0) {
                        String zn = WairToNow.dbdir + "/datums/statezips_" + enddate + "/" + ss + ".zip";
                        stateZipFile = new ZipFile (zn);
                    }
                }
                return stateZipFile;
            }
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

        /**
         * Download the per-state zip file from the server.
         */
        public void DownloadFiles () throws IOException
        {
            // get name of latest per-state zip file from server
            String servername = ReadSingleLine ("filelist.php?undername=State_" + ss);
            if (!servername.startsWith ("datums/statezips_") ||
                    !servername.endsWith ("/" + ss + ".zip") ||
                    (servername.length () != 32)) {
                throw new IOException ("bad state filename " + servername);
            }
            int expdate = Integer.parseInt (servername.substring (17, 25));

            // download from server, takes a while cuz they are 50MB..250MB
            String permname = WairToNow.dbdir + "/" + servername;
            if (new File (permname).exists ()) return;
            String tempname = permname + PARTIAL;
            DownloadBigFile (servername, tempname);

            // the .csv files should be at the beginning
            // copy them to SQLite database files
            SendPostDLProc ("digesting");
            int haveall4 = 0;
            ZipFile zf = new ZipFile (tempname);
            try {
                for (Enumeration<? extends ZipEntry> it = zf.entries (); it.hasMoreElements (); ) {
                    ZipEntry ze = it.nextElement ();
                    String en = ze.getName ();
                    Log.i (TAG, "writing " + en + " to SQLite");
                    BufferedReader br = new BufferedReader (new InputStreamReader (zf.getInputStream (ze)));
                    try {
                        switch (en) {
                            case "aptplates.csv": {
                                WritePlatesDatabase (br, expdate, ss);
                                haveall4 |= 1;
                                break;
                            }
                            case "apdgeorefs.csv": {
                                WriteApdGeorefsDatabase (br, expdate, ss);
                                haveall4 |= 2;
                                break;
                            }
                            case "iapgeorefs2.csv": {
                                WriteIapGeorefsDatabase (br, expdate, ss);
                                haveall4 |= 4;
                                break;
                            }
                            case "iapcifps.csv": {
                                WriteCifpsDatabase (br, expdate, ss);
                                haveall4 |= 8;
                                break;
                            }
                        }
                    } finally {
                        br.close ();
                    }
                    if (haveall4 == 15) break;
                }
            } finally {
                zf.close ();
            }
            if (haveall4 != 15) throw new IOException ("missing a .csv file in " + servername);

            // all download-time processing complete
            // rename to permanent name so we don't download this one again
            Lib.RenameFile (tempname, permname);

            // start prefetching runway diagram tyles
            wairToNow.openStreetMap.StartPrefetchingRunwayTiles ();
            UpdateRunwayDiagramDownloadStatus ();
        }

        /**
         * Maybe something wants to know when download has completed.
         */
        @Override  // Downloadable
        public void DownloadFileComplete ()
        {
            while (!whenDoneRun.isEmpty ()) {
                Runnable done = whenDoneRun.remove ();
                done.run ();
            }
        }

        @Override  // DownloadCheckBox
        public void DownloadThreadExited ()
        {
            while (!whenDoneRun.isEmpty ()) {
                Runnable done = whenDoneRun.remove ();
                done.run ();
            }
        }

        @Override  // DownloadCheckBox
        public void DeleteDownloadedFiles (boolean all)
        {
            int latestrevno = 0;
            String latestfname = "";

            String zipname = ss + ".zip";
            File[] files = new File (WairToNow.dbdir + "/datums/").listFiles ();

            // if deleting all but latest, get name of latest statezips_expdate/ss.zip file
            if (!all) {
                for (File file : files) {
                    String name = file.getName ();
                    if (name.startsWith ("statezips_")) {
                        File zipfile = new File (file, zipname);
                        if (zipfile.exists ()) {
                            int revno = Integer.parseInt (name.substring (10));
                            if (latestrevno < revno) {
                                latestrevno = revno;
                                latestfname = name;
                            }
                        }
                    }
                }
            }

            // delete all versions of ss.zip except possibly the latest one
            // delete the corresponding statezips_expdate directory iff it is now empty
            // also delete any partial download
            for (File file : files) {
                String name = file.getName ();
                if (name.startsWith ("statezips_") && !name.equals (latestfname)) {
                    File[] fs = file.listFiles ();
                    for (File f : fs) {
                        if (f.getName ().startsWith (zipname)) {
                            Lib.Ignored (f.delete ());
                        }
                    }
                }
                Lib.Ignored (file.delete ());
            }

            // remove the corresponding records from the SQLite database
            // if SQLite database is empty, delete it
            String latestplates = "plates_" + latestrevno + ".db";
            String[] dbnames = SQLiteDBs.Enumerate ();
            for (String dbname : dbnames) {
                if (dbname.startsWith ("plates_") && !dbname.equals (latestplates)) {
                    SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                    if (sqldb == null) continue;
                    boolean dbempty = true;
                    if (sqldb.tableExists ("iapgeorefs2")) {
                        sqldb.execSQL ("DELETE FROM iapgeorefs2 WHERE gr_state='" + ss + "'");
                        if (sqldb.tableEmpty ("iapgeorefs2")) sqldb.execSQL ("DROP TABLE iapgeorefs2");
                        else dbempty = false;
                    }
                    if (sqldb.tableExists ("iapcifps")) {
                        sqldb.execSQL ("DELETE FROM iapcifps WHERE cp_state='" + ss + "'");
                        if (sqldb.tableEmpty ("iapcifps")) sqldb.execSQL ("DROP TABLE iapcifps");
                        else dbempty = false;
                    }
                    if (sqldb.tableExists ("apdgeorefs")) {
                        sqldb.execSQL ("DELETE FROM apdgeorefs WHERE gr_state='" + ss + "'");
                        if (sqldb.tableEmpty ("apdgeorefs")) sqldb.execSQL ("DROP TABLE apdgeorefs");
                        else dbempty = false;
                    }
                    if (sqldb.tableExists ("plates")) {
                        sqldb.execSQL ("DELETE FROM plates WHERE pl_state='" + ss + "'");
                        if (sqldb.tableEmpty ("plates")) sqldb.execSQL ("DROP TABLE plates");
                        else dbempty = false;
                    }
                    if (sqldb.tableExists ("rwypreloads")) {
                        sqldb.execSQL ("DELETE FROM rwypreloads WHERE rp_state='" + ss + "'");
                        if (sqldb.tableEmpty ("rwypreloads")) sqldb.execSQL ("DROP TABLE rwypreloads");
                        else dbempty = false;
                    }
                    if (dbempty) sqldb.markForDelete ();
                }
            }

            // if deleted everything, say we ain't got nothing
            if (all) enddate = 0;
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
            lb.setText (ss + " " + fullname + ": " + t);
        }

        /**
         * Save a state/<stateid>.csv file that maps an airport ID to its state
         * and all the plates (airport diagrams, iaps, sids, stars, etc)
         * for the airports in that state.
         */
        private void WritePlatesDatabase (BufferedReader br, int expdate, String statecode)
                throws IOException
        {
            String dbname = "plates_" + expdate + ".db";

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
                while ((csv = br.readLine ()) != null) {
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
        }

        /**
         * Save machine-generated airport diagram georef data for the given cycle.
         */
        private void WriteApdGeorefsDatabase (BufferedReader br, int expdate, String statecode)
                throws IOException
        {
            String dbname = "plates_" + expdate + ".db";

            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (!sqldb.tableExists ("apdgeorefs")) {
                sqldb.execSQL ("CREATE TABLE apdgeorefs (gr_icaoid TEXT NOT NULL, gr_state TEXT NOT NULL, " +
                        "gr_tfwa REAL NOT NULL, gr_tfwb REAL NOT NULL, gr_tfwc REAL NOT NULL, " +
                        "gr_tfwd REAL NOT NULL, gr_tfwe REAL NOT NULL, gr_tfwf REAL NOT NULL, " +
                        "gr_wfta REAL NOT NULL, gr_wftb REAL NOT NULL, gr_wftc REAL NOT NULL, " +
                        "gr_wftd REAL NOT NULL, gr_wfte REAL NOT NULL, gr_wftf REAL NOT NULL);");
                sqldb.execSQL ("CREATE UNIQUE INDEX apdgeorefbyicaoid ON apdgeorefs (gr_icaoid);");
            }

            sqldb.beginTransaction ();
            try {
                int numAdded = 0;
                String csv;
                while ((csv = br.readLine ()) != null) {
                    String[] cols = Lib.QuotedCSVSplit (csv);
                    ContentValues values = new ContentValues (14);
                    values.put ("gr_icaoid", cols[0]);   // eg, "KBVY"
                    values.put ("gr_state", statecode);  // eg, "MA"
                    values.put ("gr_tfwa", Double.parseDouble (cols[ 1]));
                    values.put ("gr_tfwb", Double.parseDouble (cols[ 2]));
                    values.put ("gr_tfwc", Double.parseDouble (cols[ 3]));
                    values.put ("gr_tfwd", Double.parseDouble (cols[ 4]));
                    values.put ("gr_tfwe", Double.parseDouble (cols[ 5]));
                    values.put ("gr_tfwf", Double.parseDouble (cols[ 6]));
                    values.put ("gr_wfta", Double.parseDouble (cols[ 7]));
                    values.put ("gr_wftb", Double.parseDouble (cols[ 8]));
                    values.put ("gr_wftc", Double.parseDouble (cols[ 9]));
                    values.put ("gr_wftd", Double.parseDouble (cols[10]));
                    values.put ("gr_wfte", Double.parseDouble (cols[11]));
                    values.put ("gr_wftf", Double.parseDouble (cols[12]));
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
        }

        /**
         * Save FAA-supplied Coded Instrument Flight Procedures data for the given cycle.
         */
        private void WriteCifpsDatabase (BufferedReader br, int expdate, String statecode)
                throws IOException
        {
            String dbname = "plates_" + expdate + ".db";

            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (! sqldb.tableExists ("iapcifps")) {
                sqldb.execSQL ("CREATE TABLE iapcifps (cp_icaoid TEXT NOT NULL, cp_state TEXT NOT NULL, cp_appid TEXT NOT NULL, cp_segid TEXT NOT NULL, cp_legs TEXT NOT NULL);");
                sqldb.execSQL ("CREATE INDEX iapcifpbyicaoid ON iapcifps (cp_icaoid);");
            }

            sqldb.beginTransaction ();
            try {
                int numAdded = 0;
                String csv;
                while ((csv = br.readLine ()) != null) {
                    int i = csv.indexOf (';');
                    if (i < 0) throw new IOException ("missing ;");
                    String[] ids  = csv.substring (0, i).split (",");
                    if (ids.length != 3) throw new IOException ("bad num ids");
                    String icaoid = ids[0];
                    String appid  = ids[1];
                    String segid  = ids[2];
                    String legs   = csv.substring (++ i);

                    // see if record already exists with same ICAOID/APPID/SEGID, ignore the incoming record
                    Cursor result = sqldb.query (
                            "iapcifps", columns_cp_legs,
                            "cp_icaoid=? AND cp_appid=? AND cp_segid=?",
                            new String[] { icaoid, appid, segid },
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
                    values.put ("cp_icaoid", icaoid);     // eg, "KBVY"
                    values.put ("cp_state",  statecode);  // eg, "MA"
                    values.put ("cp_appid",  appid);      // eg, "L16"
                    values.put ("cp_segid",  segid);      // eg, "~f~"
                    values.put ("cp_legs",   legs);
                    sqldb.insertWithOnConflict ("iapcifps", null, values, SQLiteDatabase.CONFLICT_IGNORE);

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
        }

        /**
         * Save FAA-provided georeferencing info.
         * @param expdate = IAP plate expiration date
         * @param statecode = state the plates belong to
         */
        private void WriteIapGeorefsDatabase (BufferedReader br, int expdate, String statecode)
                throws IOException
        {
            String dbname = "plates_" + expdate + ".db";

            SQLiteDBs sqldb = SQLiteDBs.create (dbname);
            if (! sqldb.tableExists ("iapgeorefs2")) {
                // gr_circ added dynamically in PlateView.java
                sqldb.execSQL ("CREATE TABLE iapgeorefs2 (gr_icaoid TEXT NOT NULL, gr_state TEXT NOT NULL, gr_plate TEXT NOT NULL, " +
                        "gr_clat REAL NOT NULL, gr_clon REAL NOT NULL, gr_stp1 REAL NOT NULL, gr_stp2 REAL NOT NULL, " +
                        "gr_rada REAL NOT NULL, gr_radb REAL NOT NULL, gr_tfwa REAL NOT NULL, gr_tfwb REAL NOT NULL, " +
                        "gr_tfwc REAL NOT NULL, gr_tfwd REAL NOT NULL, gr_tfwe REAL NOT NULL, gr_tfwf REAL NOT NULL);");
                sqldb.execSQL ("CREATE INDEX iapgeorefbyplate2 ON iapgeorefs2 (gr_icaoid,gr_plate);");
            }

            sqldb.beginTransaction ();
            try {
                int numAdded = 0;
                String csv;
                while ((csv = br.readLine ()) != null) {
                    String[] cols = Lib.QuotedCSVSplit (csv);
                    ContentValues values = new ContentValues (15);
                    values.put ("gr_icaoid", cols[0]);    // eg, "KBVY"
                    values.put ("gr_state",  statecode);  // eg, "MA"
                    values.put ("gr_plate",  cols[1]);    // eg, "IAP-LOC RWY 16"
                    values.put ("gr_clat",   Double.parseDouble (cols[ 2]));
                    values.put ("gr_clon",   Double.parseDouble (cols[ 3]));
                    values.put ("gr_stp1",   Double.parseDouble (cols[ 4]));
                    values.put ("gr_stp2",   Double.parseDouble (cols[ 5]));
                    values.put ("gr_rada",   Double.parseDouble (cols[ 6]));
                    values.put ("gr_radb",   Double.parseDouble (cols[ 7]));
                    values.put ("gr_tfwa",   Double.parseDouble (cols[ 8]));
                    values.put ("gr_tfwb",   Double.parseDouble (cols[ 9]));
                    values.put ("gr_tfwc",   Double.parseDouble (cols[10]));
                    values.put ("gr_tfwd",   Double.parseDouble (cols[11]));
                    values.put ("gr_tfwe",   Double.parseDouble (cols[12]));
                    values.put ("gr_tfwf",   Double.parseDouble (cols[13]));
                    sqldb.insertWithOnConflict ("iapgeorefs2", null, values, SQLiteDatabase.CONFLICT_IGNORE);

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
        }
    }

    /**
     * Get expiration date of the airport plates for the airports of a given state (28-day cycle).
     * @return 0 if state not downloaded, else expiration date yyyymmdd
     */
    public static int GetPlatesExpDate (String ss)
    {
        int latestexpdate = 0;
        File[] files = new File (WairToNow.dbdir + "/datums").listFiles ();
        if (files == null) return 0;
        for (File file : files) {
            String name = file.getName ();
            if (name.startsWith ("statezips_")) {
                int expdate = Integer.parseInt (name.substring (10));
                if (latestexpdate < expdate) {
                    if (new File (file, ss + ".zip").exists ()) {
                        latestexpdate = expdate;
                    }
                }
            }
        }
        return latestexpdate;
    }

    /**
     * This gets the expiration date of the latest 28-day cycle file.
     * @return expiration date (or 0 if no file present)
     */
    public static int GetPlatesExpDate ()
    {
        int latestexpdate = 0;
        File[] files = new File (WairToNow.dbdir + "/datums").listFiles ();
        if (files == null) return 0;
        for (File file : files) {
            String name = file.getName ();
            if (name.startsWith ("statezips_")) {
                int expdate = Integer.parseInt (name.substring (10));
                if (latestexpdate < expdate) {
                    latestexpdate = expdate;
                }
            }
        }
        return latestexpdate;
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
                wairToNow.downloadCancelled = false;
                downloadThread = new DownloadThread ();
                downloadThread.start ();
            } else {

                // tell thread to re-scan list of things to download
                downloadAgain = true;
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
                            Lib.dismiss (unloadAlertDialog);
                            AlertDialog.Builder builder = new AlertDialog.Builder (wairToNow);
                            builder.setTitle ("Unloading charts");
                            builder.setMessage ("Please wait...");
                            builder.setCancelable (false);
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
                        Lib.dismiss (unloadAlertDialog);
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
     * Downloads all files selected by the corresponding checkbox.
     */
    private class DownloadThread extends Thread {
        @Override
        public void run ()
        {
            setName ("MaintView downloader");

            Downloadable curdcb = null;
            Message dlmsg;

            try {
                DownloadChartedLimsCSV ();
                DownloadChartLimitsCSV ();
                DownloadOutlinesTXT ();

                for (Downloadable dcb : allDownloadables) {
                    if (!dcb.IsChecked ()) continue;
                    curdcb = dcb;

                    /*
                     * Display progress dialog box saying we're starting to download files.
                     */
                    String dcbspacename = dcb.GetSpaceNameNoRev ();
                    dlmsg = maintViewHandler.obtainMessage (
                            MaintViewHandlerWhat_OPENDLPROG, 0, 0,
                            "Downloading " + dcbspacename);
                    maintViewHandler.sendMessage (dlmsg);

                    /*
                     * Download files.
                     */
                    dcb.DownloadFiles ();

                    /*
                     * Purge out old versions.
                     */
                    SendPostDLProc ("purging old versions");
                    dcb.DeleteDownloadedFiles (false);

                    /*
                     * Clear progress dialog box from screen.
                     * Uncheck the download checkbox because it is all downloaded.
                     * Also call dcb.DownloadFileComplete() method in GUI thread.
                     */
                    curdcb = null;
                    dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UNCHECKBOX, 1, 0, dcb);
                    maintViewHandler.sendMessage(dlmsg);
                }
            } catch (Exception e) {
                Log.e (TAG, "MaintView DownloadThread exception", e);
                String emsg = e.getMessage ();
                if (emsg == null) emsg = e.getClass ().toString ();
                DlError dlerror = new DlError ();
                dlerror.dcb = curdcb;
                dlerror.msg = emsg;
                dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_DLERROR, dlerror);
                maintViewHandler.sendMessage (dlmsg);
            } finally {
                maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_DLCOMPLETE);
                SQLiteDBs.CloseAll ();
            }
        }
    }

    private static class DlError {
        public Downloadable dcb;
        public String msg;
    }

    private void DownloadChartedLimsCSV () throws IOException
    {
        Log.i (TAG, "downloading chartedlims.csv");
        String csvname = WairToNow.dbdir + "/chartedlims.csv";
        DownloadFile ("chartedlims.csv", csvname);
    }

    private void DownloadChartLimitsCSV () throws IOException
    {
        Log.i (TAG, "downloading chartlimits.csv");
        String csvname = WairToNow.dbdir + "/chartlimits.csv";
        DownloadFile ("chartlimits.php", csvname);
    }

    private void DownloadOutlinesTXT () throws IOException
    {
        Log.i (TAG, "downloading outlines.txt");
        String csvname = WairToNow.dbdir + "/outlines.txt";
        DownloadFile ("outlines.txt", csvname);
    }

    /**
     * Update the download progress dialog with message to replace percentage part.
     * Wait for it to post so we don't hog CPU, thus letting the user see the message.
     * @param text = text for "Downloading whatever: text"
     */
    private void SendPostDLProc (String text)
    {
        synchronized (postDLProgLock) {
            try {
                while (postDLProcText != null) {
                    postDLProgLock.wait ();
                }
                postDLProcText = text;
                maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_POSTDLPROG);
                while (postDLProcText != null) {
                    postDLProgLock.wait ();
                }
            } catch (InterruptedException ie) {
                Lib.Ignored ();
            }
        }
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
                    dcb.DeleteDownloadedFiles (true);
                    dlmsg = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UNCHECKBOX, dcb);
                    maintViewHandler.sendMessage(dlmsg);
                }
            } catch (Exception e) {
                Log.e (TAG, "thread exception", e);
            } finally {
                maintViewHandler.sendEmptyMessage (MaintViewHandlerWhat_UNLDDONE);
                SQLiteDBs.CloseAll ();
            }
        }
    }

    /**
     * Download a big file, displaying a byte-based progress bar.
     * Downloads to a temp file, then when complete, renames the temp file.
     * Will resume downloading a partial download.
     */
    @SuppressWarnings("ConstantConditions")
    private void DownloadBigFile (String servername, String localname)
            throws IOException
    {
        Log.i (TAG, "downloading from " + servername);
        File tempfile = new File (localname + PARTIAL);
        int retry = 0;
        while (true) {
            if (wairToNow.downloadCancelled) {
                throw new IOException ("download cancelled");
            }
            long skip = tempfile.length ();
            URL url = new URL (dldir + "/bulkdownload.php?h0=sum&f0=" + servername + "&s0=" + skip);
            try {
                HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
                try {
                    SumThread sumThread = null;
                    if (skip > 0) {
                        sumThread = new SumThread ();
                        sumThread.tempfile = tempfile;
                        sumThread.skip = skip;
                        sumThread.start ();
                    }

                    // tell server what file to send us
                    httpCon.setRequestMethod ("GET");
                    httpCon.connect ();
                    int rc = httpCon.getResponseCode ();
                    if (rc != HttpURLConnection.HTTP_OK) {
                        throw new IOException ("http response code " + rc + " on " + servername);
                    }
                    InputStream inputStream = httpCon.getInputStream ();

                    // it should send us back the filename, skip position and total size
                    String nameLine = Lib.ReadStreamLine (inputStream);
                    if (!nameLine.equals ("@@name=" + servername)) {
                        throw new IOException ("bad @@name " + nameLine);
                    }
                    if (skip > 0) {
                        String skipLine = Lib.ReadStreamLine (inputStream);
                        if (!skipLine.equals ("@@skip=" + skip)) {
                            throw new IOException ("bad @@skip " + skipLine);
                        }
                    }
                    String sizeLine = Lib.ReadStreamLine (inputStream);
                    if (!sizeLine.startsWith ("@@size=")) {
                        throw new IOException ("bad @@size " + sizeLine);
                    }
                    long size = Long.parseLong (sizeLine.substring (7));

                    // start downloading the rest of the file and sum as we go along
                    long serversum;
                    long localsum = 0;
                    byte[] buf = new byte[32768];
                    Lib.Ignored (tempfile.getParentFile ().mkdirs ());
                    FileOutputStream outputStream = new FileOutputStream (tempfile, true);
                    try {
                        while (skip < size) {

                            // read as much as we can but not more than file has left
                            long len = size - skip;
                            if (len > buf.length) len = buf.length;
                            rc = inputStream.read (buf, 0, (int) len);
                            if (wairToNow.downloadCancelled) {
                                retry = 999999999;
                                throw new IOException ("download cancelled");
                            }
                            if (rc <= 0) throw new EOFException ("end of file");

                            // write to temp file
                            outputStream.write (buf, 0, rc);
                            skip += rc;

                            // update progress bar
                            if (!updateDLProgSent || (skip >= size)) {
                                updateDLProgSent = true;
                                Message m = maintViewHandler.obtainMessage (MaintViewHandlerWhat_UPDATEDLPROG,
                                        (int) skip, (int) size, null);
                                maintViewHandler.sendMessage (m);
                            }

                            // update sum
                            for (int i = 0; i < rc; i ++) {
                                localsum += buf[i] & 0xFF;
                            }
                        }

                        // there should be an @@sum exactly here
                        // giving us what the server has for the whole file
                        String sumLine = Lib.ReadStreamLine (inputStream);
                        if (!sumLine.startsWith ("@@sum=")) {
                            Lib.Ignored (tempfile.delete ());
                            throw new IOException ("bad @@sum " + sumLine);
                        }
                        serversum = Long.parseLong (sumLine.substring (6));
                    } finally {
                        outputStream.close ();
                    }

                    // make sure the two sums match
                    if (sumThread != null) {
                        try {
                            sumThread.join ();
                        } catch (InterruptedException ie) {
                            Lib.Ignored ();
                        }
                        if (sumThread.error != null) throw sumThread.error;
                        localsum += sumThread.localsum;
                    }
                    if (localsum != serversum) {
                        Lib.Ignored (tempfile.delete ());
                        throw new IOException ("sum mismatch");
                    }

                    // have complete verified file, rename to permanent name
                    if (!tempfile.renameTo (new File (localname))) {
                        throw new IOException ("error renaming to permanent");
                    }
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
     * Compute checksum of a file.
     */
    private class SumThread extends Thread {
        public File tempfile;       // file to compute checksum of
        public IOException error;   // null if success, else IO exception
        public long localsum;       // resultant checksum
        public long skip;           // number of bytes in file to process

        @Override
        public void run ()
        {
            try {
                FileInputStream fis = new FileInputStream (tempfile);
                try {
                    byte[] buf = new byte[32768];
                    int rc;
                    long sum = 0;
                    for (long offs = 0; offs < skip; offs += rc) {
                        long len = skip - offs;
                        if (len > buf.length) len = buf.length;
                        rc = fis.read (buf, 0, (int) len);
                        if (rc <= 0) throw new EOFException ("EOF reading file");
                        for (int i = 0; i < rc; i ++) {
                            sum += buf[i] & 0xFF;
                        }
                    }
                    localsum = sum;
                } finally {
                    fis.close ();
                }
            } catch (IOException ioe) {
                error = ioe;
            }
        }
    }

    /**
     * Read single line from remote server file
     * @param servername = name of file on server
     */
    private String ReadSingleLine (String servername)
            throws IOException
    {
        ReadSingleLineStuff rsls = new ReadSingleLineStuff ();
        rsls.DownloadWhat (servername);
        return rsls.line;
    }

    private class ReadSingleLineStuff extends DownloadStuff {
        public String line;

        @Override
        public void DownloadContents () throws IOException
        {
            try {
                wairToNow.downloadStream = this;
                BufferedReader br = new BufferedReader (new InputStreamReader (this));
                line = br.readLine ();
                if (line == null) throw new EOFException ("file empty");
            } finally {
                wairToNow.downloadStream = null;
            }
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
            FileOutputStream os = new FileOutputStream (localname + PARTIAL);
            byte[] buff = new byte[32768];
            try {
                wairToNow.downloadStream = this;
                int len;
                while ((len = read (buff)) > 0) {
                    if (wairToNow.downloadCancelled) throw new IOException ("download cancelled");
                    os.write (buff, 0, len);
                }
            } finally {
                wairToNow.downloadStream = null;
                os.close ();
            }
            Lib.RenameFile (localname + PARTIAL, localname);
        }
    }

    /**
     * Download a file from web server and do various things with it
     */
    private abstract class DownloadStuff extends InputStream {
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
         * Update the download progress bar based on number of bytes read.
         */
        protected void UpdateBytesProgress ()
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

        @Override  // InputStream
        public void close () throws IOException
        {
            inputStream.close ();
        }
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

            @SuppressLint("ClickableViewAccessibility")
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
            public void MouseDown (double x, double y)
            { }

            @Override
            public void MouseUp (double x, double y)
            { }

            @Override
            public void Panning (double x, double y, double dx, double dy)
            {
                matrix.postTranslate ((float) dx, (float) dy);
                viewer.invalidate ();
            }

            @Override
            public void Scaling (double fx, double fy, double sf)
            {
                matrix.postScale ((float) sf, (float) sf, (float) fx, (float) fy);
                viewer.invalidate ();
            }

        }
    }
}
