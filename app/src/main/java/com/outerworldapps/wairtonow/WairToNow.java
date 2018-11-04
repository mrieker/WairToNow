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
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.acra.ACRA;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

public class WairToNow extends Activity {
    private final static String TAG = "WairToNow";
    public final static long agreePeriod = 60 * 24 * 60 * 60 * 1000L;  // agree every this often
    public final static long gpsDownDelay = 4000;     // gps down this long for blink warning
    public final static double gpsMinSpeedMPS = 3.0;  // must be going this fast for heading valid

    public static String dbdir;
    public static WTNHandler wtnHandler;

    private final static int airplaneHeight = 313 - 69;

    ////private AltimeterView altimeterView;
    public  volatile boolean downloadCancelled;
    private boolean atAMinimumShown;
    private boolean gpsAvailable;
    private boolean hasAgreed;
    private boolean lastLocQueued;
    private boolean tabsVisible;
    public  ChartView chartView;
    public  CrumbsView crumbsView;
    public  CurrentCloud currentCloud;
    private DetentHorizontalScrollView tabButtonScroller;
    public  double currentGPSAlt;    // metres MSL
    public  double currentGPSLat;    // degrees
    public  double currentGPSLon;    // degrees
    public  double currentGPSHdg;    // degrees true
    public  double currentGPSSpd;    // metres per second
    public  float dotsPerInch, dotsPerInchX, dotsPerInchY;
    public  float textSize, thickLine, thinLine;
    private FrameLayout toolbarLayout;
    private GlassView glassView;
    public  final HashMap<String,MetarRepo> metarRepos = new HashMap<> ();
    public  volatile InputStream downloadStream;
    public  int displayWidth;
    public  int displayHeight;
    public  int gpsDisabled;
    private int gpsLastInterval;
    public  LinearLayout downloadStatusRow;
    private LinearLayout menuButtonColumn;
    private LinearLayout tabButtonLayout;
    private LinearLayout tabViewLayout;
    private LinearLayout.LayoutParams ctvllp = new LinearLayout.LayoutParams (
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    private LinearLayout.LayoutParams tbsllp = new LinearLayout.LayoutParams (
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    public  long currentGPSTime;    // milliseconds since Jan 1, 1970 UTC
    private long gpsThrottleStart;
    private long lastLocUpdate;
    public  MaintView maintView;
    public  final NexradRepo nexradRepo = new NexradRepo ();
    public  OpenStreetMap openStreetMap;
    public  OptionsView optionsView;
    private Paint airplanePaint = new Paint ();
    private Paint gpsAvailablePaint;
    private Path airplanePath = new Path ();
    public  RouteView routeView;
    public  SensorsView sensorsView;
    private TabButton agreeButton;
    public  TabButton chartButton;
    private TabButton crumbsButton;
    public  TabButton currentTabButton;
    private TabButton glassButton;
    public  TabButton maintButton;
    public  TabButton sensorsButton;
    private TabButton virtNav1Button;
    private TabButton virtNav2Button;
    public  TextView downloadStatusText;
    public  final TrafficRepo trafficRepo = new TrafficRepo ();
    public  UserWPView userWPView;
    private VirtNavView virtNav1View, virtNav2View;
    public  WaypointView waypointView1, waypointView2;
    private Waypoint pendingCourseSetWP;

    /** Called when the activity is first created. */
    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate (savedInstanceState);

        // allow startIntent() to let other apps read our files
        // https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed
        // also allows links in the HelpView to work so they don't get FileUriExposedException
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder ();
        StrictMode.setVmPolicy (builder.build ());

        if (wtnHandler == null) wtnHandler = new WTNHandler ();

        ctvllp.weight = 1;

        // get display and pixel size
        DisplayMetrics metrics = new DisplayMetrics ();
        getWindowManager ().getDefaultDisplay ().getMetrics (metrics);
        dotsPerInchX = metrics.xdpi;
        dotsPerInchY = metrics.ydpi;
        dotsPerInch  = (float) Math.sqrt (dotsPerInchX * dotsPerInchY);
        thickLine    = dotsPerInch / 12.0F;
        thinLine     = dotsPerInch / 24.0F;

        // find out where we put our downloaded data
        // use external storage cuz it can be very large
        File efd = getExternalFilesDir (null);
        if (efd == null) {
            StartupError ("external storage not available", null);
            return;
        }
        dbdir = efd.getAbsolutePath ();

        // dump prefs to logcat for debugging
        SharedPreferences prefs = getPreferences (MODE_PRIVATE);
        Map<String,?> keys = prefs.getAll ();
        for (Map.Entry<String,?> entry : keys.entrySet ()) {
            Log.d (TAG, "pref[" + entry.getKey () + "]=" + entry.getValue ().toString ());
        }

        // get last GPS position in case GPS not synchronized yet
        loadLastKnownPosition ();

        /*
         * Make sure database directory exists.
         * Also mark it nomedia so media scanner will leave it alone
         * as it will contain thousands of .png files.
         */
        File dbdirfile = new File (dbdir);
        Lib.Ignored (dbdirfile.mkdirs ());
        File nmf = new File (dbdirfile, ".nomedia");
        if (!nmf.exists ()) {
            try {
                Lib.Ignored (nmf.createNewFile ());
            } catch (IOException ioe) {
                Log.w (TAG, "error creating " + nmf.getAbsolutePath (), ioe);
            }
        }

        /*
         * Get text size to use throughout.
         */
        textSize = dotsPerInch / 7;

        /*
         * Also get screen size in pixels.
         */
        displayWidth  = metrics.widthPixels;
        displayHeight = metrics.heightPixels;

        /*
         * Paint used to display GPS NOT AVAILABLE box.
         */
        gpsAvailablePaint = new Paint ();
        gpsAvailablePaint.setColor (Color.WHITE);
        gpsAvailablePaint.setStrokeWidth (thickLine);
        gpsAvailablePaint.setStyle (Paint.Style.FILL_AND_STROKE);
        gpsAvailablePaint.setTextAlign (Paint.Align.CENTER);

        // airplane icon pointing up with center at (0,0)
        int acy = 181;
        airplanePath.moveTo (0, 313 - acy);
        airplanePath.lineTo ( -44, 326 - acy);
        airplanePath.lineTo ( -42, 301 - acy);
        airplanePath.lineTo ( -15, 281 - acy);
        airplanePath.lineTo ( -18, 216 - acy);
        airplanePath.lineTo (-138, 255 - acy);
        airplanePath.lineTo (-138, 219 - acy);
        airplanePath.lineTo (-17, 150 - acy);
        airplanePath.lineTo ( -17,  69 - acy);
        airplanePath.cubicTo (0, 39 - acy,
                0, 39 - acy,
                +17, 69 - acy);
        airplanePath.lineTo ( +17, 150 - acy);
        airplanePath.lineTo (+138, 219 - acy);
        airplanePath.lineTo (+138, 255 - acy);
        airplanePath.lineTo ( +18, 216 - acy);
        airplanePath.lineTo ( +15, 281 - acy);
        airplanePath.lineTo ( +42, 301 - acy);
        airplanePath.lineTo ( +44, 326 - acy);
        airplanePath.lineTo (0, 313 - acy);

        airplanePaint.setColor (CurrentCloud.currentColor);
        airplanePaint.setStrokeWidth (thinLine);
        airplanePaint.setTextAlign (Paint.Align.CENTER);

        /*
         * License agreement.
         */
        AgreeView agreeView = new AgreeView (this);

        /*
         * Load options before doing anything else.
         */
        optionsView = new OptionsView (this);

        /*
         * Cloud in upper-left corner showing current position from GPS.
         */
        currentCloud = new CurrentCloud (this);

        /*
         * Open Street Maps access.
         */
        openStreetMap = new OpenStreetMap (this);

        /*
         * Planning page.
         */
        PlanView planView = new PlanView (this);

        /*
         * Create a view that views charts based on lat/lon.
         */
        chartView = new ChartView (this);

        /*
         * Create a view for updating database.
         */
        try {
            maintView = new MaintView (this);
        } catch (Exception e) {
            StartupError ("error reading chart database", e);
            return;
        }

        /*
         * Create a view that browses files.
         */
        FilesView filesView = new FilesView (this);

        /*
         * Create a view that displays help pages.
         */
        HelpView helpView = new HelpView (this);

        /*
         * Create a view that simulates a glass cockpit.
         */
        glassView = new GlassView (this);

        /*
         * Altimeter based on pressure.
         */
        ////altimeterView = new AltimeterView (this);

        /*
         * Create a new that analyzes a route clearance.
         */
        routeView = new RouteView (this);

        /*
         * Create a view that can manage user waypoints.
         */
        try {
            userWPView = new UserWPView (this);
        } catch (IOException ioe) {
            StartupError ("error reading user waypoints", ioe);
            return;
        }

        /*
         * Create two views that can select a waypoint based on its name.
         */
        waypointView1 = new WaypointView (this, "FAAWP1");
        waypointView2 = new WaypointView (this, "FAAWP2");

        /*
         * Record path (breadcrumbs).
         */
        crumbsView = new CrumbsView (this);

        /*
         * View GPS status.
         */
        sensorsView = new SensorsView (this);

        /*
         * Virtual nav dials.
         */
        virtNav1View = new VirtNavView (this, "VirtNav1");
        virtNav2View = new VirtNavView (this, "VirtNav2");
        virtNav1Button = new TabButton (virtNav1View);
        virtNav2Button = new TabButton (virtNav2View);

        /*
         * Toolbar at top of screen.
         */
        ImageView iconImageView = new ImageView (this);
        iconImageView.setAdjustViewBounds (true);
        iconImageView.setImageResource (R.drawable.icon);
        iconImageView.setMaxHeight (Math.round (textSize));

        LinearLayout tbl = new LinearLayout (this);
        tbl.setOrientation (LinearLayout.HORIZONTAL);
        tbl.addView (iconImageView);
        tbl.addView (new ToolbarText (" WairToNow"));

        FrameLayout.LayoutParams omblp = new FrameLayout.LayoutParams (
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        omblp.gravity = Gravity.TOP | Gravity.END;
        ToolbarText omb = new ToolbarText (" Menu");
        omb.setLayoutParams (omblp);
        omb.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                ExpandMenu ();
            }
        });

        toolbarLayout = new FrameLayout (this);
        toolbarLayout.addView (tbl);
        toolbarLayout.addView (omb);

        /*
         * Download status box near top of screen.
         */
        downloadStatusRow = new LinearLayout (this);
        downloadStatusRow.setOrientation (LinearLayout.HORIZONTAL);
        downloadStatusRow.setVisibility (View.GONE);

        DownloadStopButton dsb = new DownloadStopButton ();
        downloadStatusRow.addView (dsb);

        downloadStatusText = new TextView (this);
        SetTextSize (downloadStatusText);
        downloadStatusRow.addView (downloadStatusText);

        /*
         * Set up tab system.
         */
        agreeButton               = new TabButton (agreeView);
        chartButton               = new TabButton (chartView);
        TabButton waypt1Button    = new TabButton (waypointView1);
        TabButton waypt2Button    = new TabButton (waypointView2);
        TabButton userWPButton    = new TabButton (userWPView);
        glassButton               = new TabButton (glassView);
        ////TabButton altimeterButton = new TabButton (altimeterView);
        TabButton routeButton     = new TabButton (routeView);
        crumbsButton              = new TabButton (crumbsView);
        TabButton planButton      = new TabButton (planView);
        TabButton optionsButton   = new TabButton (optionsView);
        maintButton               = new TabButton (maintView);
        sensorsButton             = new TabButton (sensorsView);
        TabButton filesButton     = new TabButton (filesView);
        TabButton helpButton      = new TabButton (helpView);
        tabButtonLayout = new LinearLayout (this);
        tabButtonLayout.setOrientation (LinearLayout.HORIZONTAL);
        tabButtonLayout.addView (agreeButton);
        tabButtonLayout.addView (chartButton);
        tabButtonLayout.addView (waypt1Button);
        tabButtonLayout.addView (waypt2Button);
        tabButtonLayout.addView (userWPButton);
        tabButtonLayout.addView (glassButton);
        ////tabButtonLayout.addView (altimeterButton);
        tabButtonLayout.addView (routeButton);
        tabButtonLayout.addView (crumbsButton);
        tabButtonLayout.addView (planButton);
        tabButtonLayout.addView (virtNav1Button);
        tabButtonLayout.addView (virtNav2Button);
        tabButtonLayout.addView (optionsButton);
        tabButtonLayout.addView (maintButton);
        tabButtonLayout.addView (sensorsButton);
        tabButtonLayout.addView (filesButton);
        tabButtonLayout.addView (helpButton);
        //tabButtonLayout.addView (new TabButton (new ExpView (this)));

        tabButtonScroller = new DetentHorizontalScrollView (this);
        SetDetentSize (tabButtonScroller);
        tabButtonScroller.addView (tabButtonLayout);

        tabViewLayout = new LinearLayout (this);
        tabViewLayout.setOrientation (LinearLayout.VERTICAL);
        tabViewLayout.addView (toolbarLayout);
        tabViewLayout.addView (downloadStatusRow);
        tabViewLayout.addView (tabButtonScroller);

        // make menu button column for upper right corner overlay
        menuButtonColumn = new LinearLayout (this);
        menuButtonColumn.setOrientation (LinearLayout.VERTICAL);
        FrameLayout.LayoutParams menuButtonColumnLayout =
                new FrameLayout.LayoutParams (ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        menuButtonColumnLayout.gravity = Gravity.TOP | Gravity.END;
        menuButtonColumn.setLayoutParams (menuButtonColumnLayout);

        // make menu button column invisible to begin with
        CollapseMenu ();

        // overlay the menu button column on the rest of the screen
        FrameLayout frameLayout = new FrameLayout (this);
        frameLayout.addView (tabViewLayout);
        frameLayout.addView (menuButtonColumn);
        setContentView (frameLayout);

        // set up initial active tab
        tabsVisible = prefs.getBoolean ("tabVisibility", true);
        UpdateTabVisibilities ();
        chartButton.DisplayNewTab ();
    }

    /**
     * Collapse the upper right corner menu.
     */
    private void CollapseMenu ()
    {
        menuButtonColumn.removeAllViews ();
    }

    /**
     * Expand the upper right corner menu to show the buttons.
     */
    private void ExpandMenu ()
    {
        menuButtonColumn.removeAllViews ();

        AddMenuItem ("Chart", new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                CollapseMenu ();
                chartButton.onClick (chartButton);
            }
        });

        AddMenuItem ("Exit", new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                CollapseMenu ();
                MyFinish ();
            }
        });

        if (tabsVisible) {
            AddMenuItem ("Hide Tabs", new View.OnClickListener () {
                @Override
                public void onClick (View view) {
                    CollapseMenu ();
                    SetTabVisibility (false);
                }
            });
        }

        AddMenuItem ("\u25B6 \u25B6", new View.OnClickListener () {
            @Override  // 25B6=right triangle
            public void onClick (View view) {
                CollapseMenu ();
            }
        });

        if (!tabsVisible) {
            AddMenuItem ("Pages", new View.OnClickListener () {
                @Override
                public void onClick (View view) {
                    CollapseMenu ();
                    ShowMoreMenu ();
                }
            });
        }

        AddMenuItem ("Re-center", new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                CollapseMenu ();
                if (hasAgreed) {
                    if (currentTabButton != chartButton) {
                        chartButton.DisplayNewTab ();
                    }
                    chartView.ReCenter ();
                }
            }
        });

        if (!tabsVisible) {
            AddMenuItem ("Show Tabs", new View.OnClickListener () {
                @Override
                public void onClick (View view) {
                    CollapseMenu ();
                    SetTabVisibility (true);
                }
            });
        }
    }

    /**
     * Strings at top of screen in toolbar.
     * Standard TextView has a little margin at the top and so everything looks crooked.
     */
    private class ToolbarText extends View {
        private Paint paint;
        private Rect bounds;
        private String text;

        public ToolbarText (String txt)
        {
            super (WairToNow.this);
            paint  = new Paint ();
            bounds = new Rect ();
            text   = txt;
            paint.setColor (Color.WHITE);
            paint.setTextSize (textSize);
            paint.getTextBounds (text, 0, text.length (), bounds);
        }

        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec)
        {
            // bounds.width() leaves out the leading space
            // ...so use bounds.right to include it
            // +3 on the height to leave extra room below the icon and the chart
            setMeasuredDimension (bounds.right, Math.round (textSize) + 3);
        }

        @Override
        public void onDraw (Canvas canvas)
        {
            canvas.drawText (text, 0, (textSize + bounds.height ()) / 2.0F, paint);
        }
    }

    /**
     * Add an entry to the menu button column in upper right corner of screen.
     * @param label = what button is named
     * @param action = what to do when button is clicked
     */
    private void AddMenuItem (String label, View.OnClickListener action)
    {
        Button showMenuButton = new MenuButton ();
        showMenuButton.setOnClickListener (action);
        showMenuButton.setText (label);
        menuButtonColumn.addView (showMenuButton);
    }

    // like Button except gives black background instead of transparent
    private class MenuButton extends Button {
        public MenuButton ()
        {
            super (WairToNow.this);
            SetTextSize (this);
        }

        // overriding onDraw() just makes the whole thing black
        @Override
        public void draw (Canvas canvas)
        {
            canvas.drawColor (Color.BLACK);
            super.draw (canvas);
        }
    }

    /**
     * Close the app after confirmation.
     * The real finish() doesn't really exit.
     */
    public void MyFinish ()
    {
        AlertDialog.Builder adb = new AlertDialog.Builder (this);
        adb.setTitle ("Exit");
        adb.setMessage ("Are you sure you want to exit?");
        adb.setPositiveButton ("Yes - Close App", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i) {
                saveLastKnownPosition ();
                crumbsView.CloseFiles ();
                SQLiteDBs.CloseAll ();
                System.exit (0);
            }
        });
        adb.setNegativeButton ("No - Stay Open", null);
        adb.show ();
    }

    /**
     * Type B option was changed, update tab button visibilities.
     */
    public void UpdateTabVisibilities ()
    {
        try {
            boolean typeB = optionsView.typeBOption.checkBox.isChecked ();
            int vis = typeB ? View.GONE : View.VISIBLE;
            crumbsButton.setVisibility   (vis);
            glassButton.setVisibility    (vis);
            virtNav1Button.setVisibility (vis);
            virtNav2Button.setVisibility (vis);
        } catch (NullPointerException npe) {
            // might get this when called during startup
            Lib.Ignored ();
        }
    }

    /**
     * Some error starting up, display error message dialog then die.
     */
    private void StartupError (String msg, Exception e)
    {
        Log.d (TAG, "StartupError: " + msg, e);

        ACRA.getErrorReporter ().handleException (new StartupErrorException (msg, e));

        if (e != null) {
            String emsg = e.getMessage ();
            if (emsg == null) emsg = e.getClass ().getSimpleName ();
            msg += ":  " + emsg;
        }

        AlertDialog.Builder adb = new AlertDialog.Builder (this);
        adb.setTitle ("Startup error");
        adb.setMessage (msg + "\nTry clearing data or removing and re-installing app.");
        adb.setPositiveButton ("OK", new DialogInterface.OnClickListener ()
        {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                System.exit (-1);
            }
        });
        adb.setNegativeButton ("Clear Data", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                EmptyDirectory (new File (dbdir));
                System.exit (-1);
            }

            private void EmptyDirectory (File dir)
            {
                for (File file : dir.listFiles ()) {
                    String path = file.getPath ();
                    if (path.endsWith ("/crumbs")) continue;
                    if (path.endsWith ("/dmecheckboxes.db")) continue;
                    if (path.endsWith ("/georefs.db")) continue;
                    if (path.endsWith ("/options.csv")) continue;
                    if (path.endsWith ("/routes")) continue;
                    if (path.endsWith ("/userwaypts.csv")) continue;
                    if (file.isDirectory ()) EmptyDirectory (file);
                    Lib.Ignored (file.delete ());
                }
            }
        });
        adb.show ();
    }

    private static class StartupErrorException extends Exception {
        private String msg;
        public StartupErrorException (String m, Exception e)
        {
            super (e);
            msg = m;
        }
        @Override
        public String toString ()
        {
            return "StartupErrorException: " + msg + "\n" + super.toString ();
        }
    }

    /**
     * App is being taken out of memory, make sure files are closed.
     */
    @Override
    public void onDestroy ()
    {
        saveLastKnownPosition ();
        crumbsView.CloseFiles ();
        SQLiteDBs.CloseAll ();
        super.onDestroy ();
    }

    /**
     * Stop any downloading in progress.
     */
    private class DownloadStopButton extends Button implements View.OnClickListener {
        @SuppressLint("SetTextI18n")
        public DownloadStopButton ()
        {
            super (WairToNow.this);
            setText ("Stop");
            SetTextSize (this);
            setOnClickListener (this);
        }

        @Override
        public void onClick (View v)
        {
            downloadCancelled = true;
            try {
                downloadStream.close ();
            } catch (Exception e) {
                Lib.Ignored ();
            }
        }
    }

    /**
     * The button used for each tab.
     */
    public class TabButton extends RingedButton implements View.OnClickListener {
        public final String ident;
        public View view;  // must also be CanBeMainView

        public TabButton (View view)
        {
            super (WairToNow.this);
            this.ident = ((CanBeMainView) view).GetTabName ();
            this.view  = view;
            setText (ident);
            SetTextSize (this);
            setOnClickListener (this);
        }

        @Override  // View.OnClickListener
        public void onClick (View v)
        {
            // maybe it's a re-click of the current tab
            if (this == currentTabButton) {
                ((CanBeMainView)view).ReClicked ();
            } else if (hasAgreed) {
                DisplayNewTab ();
            }
        }

        public void DisplayNewTab ()
        {
            // turn all other buttons black and turn this one red
            for (int i = tabButtonLayout.getChildCount (); -- i >= 0;) {
                TabButton tb = (TabButton) tabButtonLayout.getChildAt (i);
                tb.setTextColor (Color.BLACK);
            }
            setTextColor (Color.RED);

            // first tell the current view that it is being removed
            if (currentTabButton != null) {
                ((CanBeMainView)currentTabButton.view).CloseDisplay ();
            }

            // show the new view
            currentTabButton = this;
            SetCurrentView ();

            // tell the new view that it is now being displayed
            CanBeMainView cbmv = (CanBeMainView) view;
            //noinspection ResourceType
            setRequestedOrientation (cbmv.GetOrientation ());
            if (cbmv.IsPowerLocked ()) {
                getWindow ().addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow ().clearFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            cbmv.OpenDisplay ();
        }
    }

    /**
     * Set the currently displayed tab and change the view therein.
     * @param view = view to make currently displayed and attached
     *               to it's GetTabName() tab
     */
    public void SetCurrentTab (View view)
    {
        CanBeMainView mv = (CanBeMainView) view;
        String ident = mv.GetTabName ();
        for (int i = tabButtonLayout.getChildCount (); -- i >= 0;) {
            TabButton tb = (TabButton) tabButtonLayout.getChildAt (i);
            if (tb.ident.equals (ident)) {
                tb.view = view;
                tb.DisplayNewTab ();
                break;
            }
        }
    }

    /**
     * Turn on/off visibility of tab button row.
     */
    private void SetTabVisibility (boolean vis)
    {
        // save new value to preferences
        tabsVisible = vis;
        SharedPreferences prefs = getPreferences (MODE_PRIVATE);
        SharedPreferences.Editor editr = prefs.edit ();
        editr.putBoolean ("tabVisibility", vis);
        editr.commit ();

        // rebuild screen contents with/without button row
        SetCurrentView ();
    }

    /**
     * Build display with selected tab view and button row.
     */
    private void SetCurrentView ()
    {
        tabViewLayout.removeAllViews ();
        tabViewLayout.addView (toolbarLayout);
        tabViewLayout.addView (downloadStatusRow);
        if (currentTabButton != null) {
            View v = currentTabButton.view;
            ViewParent p = v.getParent ();
            if (p instanceof ViewGroup) {
                ((ViewGroup) p).removeView (v);
            }
            tabViewLayout.addView (v, ctvllp);
        }
        if (tabsVisible) {
            tabViewLayout.addView (tabButtonScroller, tbsllp);
        }
    }

    /**
     * Set standard text size used throughout.
     */
    public void SetTextSize (TextView tv)
    {
        tv.setTextSize (TypedValue.COMPLEX_UNIT_PX, textSize);
    }

    /**
     * Set standard horizontal scroller detent size.
     */
    public void SetDetentSize (DetentHorizontalScrollView dhsv)
    {
        dhsv.setDetentSize (Math.min (displayWidth, displayHeight) / 5.0F);
    }

    /**
     * Set destination waypoint for the course line
     */
    public void SetDestinationWaypoint (Waypoint wp)
    {
        routeView.ShutTrackingOff ();
        if (wp == null) {
            chartView.SetCourseLine (0, 0, null);
        } else if ((currentGPSLat != 0) && (currentGPSLon != 0)) {
            chartView.SetCourseLine (currentGPSLat, currentGPSLon, wp);
        } else {
            pendingCourseSetWP = wp;
        }
        SetCurrentTab (chartView);
    }

    /**
     * Location of the airplane has been received from GPS.
     */
    public void LocationReceived (double speed, double altitude, double heading,
                                  double latitude, double longitude, long time)
    {
        if (gpsAvailable) {
            int thisInterval = (int) Math.round ((double) (time - gpsThrottleStart) *
                    (double) optionsView.gpsUpdateOption.getVal () / 1000.0);
            if (thisInterval <= gpsLastInterval) return;
            gpsLastInterval = thisInterval;
        } else {
            gpsThrottleStart = time;
            gpsLastInterval = 0;

            // say GPS is available cuz we just got a point
            gpsAvailable = true;
        }

        // reset GPS timeout timer
        lastLocUpdate = SystemClock.uptimeMillis ();
        if (!lastLocQueued) {
            lastLocQueued = true;
            Message msg = wtnHandler.obtainMessage (334, WairToNow.this);
            wtnHandler.sendMessageAtTime (msg, lastLocUpdate + gpsDownDelay);
        }

        // post it through to update position on screen unless disabled
        if (gpsDisabled == 0) {
            SetCurrentLocation (speed, altitude, heading, latitude, longitude, time);
        }
    }

    /**
     * Set the current location of the airplane.
     */
    public void SetCurrentLocation (double speed, double altitude, double heading,
                                    double latitude, double longitude, long time)
    {
        currentGPSTime = time;
        currentGPSLat  = latitude;
        currentGPSLon  = longitude;
        currentGPSAlt  = altitude;
        currentGPSSpd  = speed;
        if (currentGPSSpd > gpsMinSpeedMPS) {
            currentGPSHdg = heading;
        }

        if (pendingCourseSetWP != null) {
            chartView.SetCourseLine (currentGPSLat, currentGPSLon, pendingCourseSetWP);
            pendingCourseSetWP = null;
        }

        ////altimeterView.setGPSAltitude (currentGPSAlt);
        chartView.SetGPSLocation ();
        crumbsView.SetGPSLocation ();
        glassView.SetGPSLocation ();
        routeView.SetGPSLocation ();
        sensorsView.SetGPSLocation ();
        virtNav1View.SetGPSLocation ();
        virtNav2View.SetGPSLocation ();
        waypointView1.SetGPSLocation ();
        waypointView2.SetGPSLocation ();
    }

    /**
     * Called when app is now in the foreground.
     */
    @Override
    public void onResume ()
    {
        super.onResume ();

        hasAgreed = false;
        if (agreeButton != null) {
            SharedPreferences prefs = getPreferences (Activity.MODE_PRIVATE);
            long now = System.currentTimeMillis ();
            long agreed = prefs.getLong ("hasAgreed", 0);
            if (now - agreed < agreePeriod) {
                HasAgreed ();
            } else {
                agreeButton.setVisibility (View.VISIBLE);
                agreeButton.DisplayNewTab ();
            }
        }
    }

    /**
     * Once we think they have agreed to licenses,
     * start sampling GPS and allow other tab buttons to work.
     */
    public void HasAgreed ()
    {
        hasAgreed = true;
        agreeButton.setVisibility (View.GONE);
        sensorsView.startGPSReceiver ();

        if (!atAMinimumShown && (MaintView.GetWaypointExpDate () <= 0)) {
            atAMinimumShown = true;
            AlertDialog.Builder adb = new AlertDialog.Builder (this);
            adb.setTitle ("Chart Maint");
            adb.setMessage ("At a minimum you should download a nearby chart.");
            adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    chartButton.DisplayNewTab ();  // display chart page
                    chartView.ReClicked ();        // display chart select menu
                }
            });
            adb.setNegativeButton ("Not Now", null);
            adb.show ();
        }

        // maybe show alert box for expired charts
        maintView.ExpdateCheck ();
    }

    /**
     * Called when app is now in the background.
     */
    @Override
    public void onPause ()
    {
        super.onPause ();
        sensorsView.stopGPSReceiverDelayed ();

        // maybe some database files marked for delete
        // so close our handles so they will be deleted
        SQLiteDBs.CloseAll ();
    }

    /**
     * Write simple log file for debugging.
     * @param str = line to write
     */
    @SuppressWarnings("unused")
    public static void WriteLog (String str)
    {

        try {
            if (logFile == null) {
                logFile = new PrintWriter (new FileOutputStream (WairToNow.dbdir + "/log.txt", true), true);
            }
            logFile.println (logFmt.format (new Date ()) + " " + str);
        } catch (Exception e) {
            Lib.Ignored ();
        }
    }
    @SuppressWarnings("unused")
    public static void FlushLog ()
    {
        if (logFile != null) logFile.flush ();
    }

    private static PrintWriter logFile;
    private static SimpleDateFormat logFmt = new SimpleDateFormat ("yyyy-MM-dd@HH:mm:ss", Locale.US);

    /***************************************\
     *  GPS Location and Status listeners  *
    \***************************************/

    public static class WTNHandler extends Handler {
        public void runDelayed (long ms, Runnable it)
        {
            Message m = obtainMessage (332, it);
            sendMessageDelayed (m, ms);
        }

        @Override
        public void handleMessage (Message msg)
        {
            switch (msg.what) {
                case 332: {
                    ((Runnable) msg.obj).run ();
                    break;
                }
                case 334: {
                    WairToNow wtn = (WairToNow) msg.obj;
                    long now = SystemClock.uptimeMillis ();
                    if (now >= wtn.lastLocUpdate + gpsDownDelay) {
                        wtn.lastLocQueued = false;
                        wtn.gpsAvailable = false;
                        if (wtn.currentTabButton != null) wtn.currentTabButton.view.invalidate ();
                    } else {
                        Message m = wtnHandler.obtainMessage (334, wtn);
                        wtnHandler.sendMessageAtTime (m, wtn.lastLocUpdate + gpsDownDelay);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Draw GPS availability message on georeferenced page.
     * @param canvas = what to draw it on
     * @param view   = view being drawn on
     */
    public void drawGPSAvailable (Canvas canvas, final View view)
    {
        int color = Color.TRANSPARENT;
        if (gpsDisabled != 0) {
            color = Color.YELLOW;
        } else if (!gpsAvailable) {
            color = Color.RED;
        }
        if (color != Color.TRANSPARENT) {
            long now = SystemClock.uptimeMillis ();
            if ((now & 1024) == 0) {
                gpsAvailablePaint.setColor (color);
                int w = view.getWidth ();
                int h = view.getHeight ();
                canvas.drawLine (0, 0, w, 0, gpsAvailablePaint);
                canvas.drawLine (w, 0, w, h, gpsAvailablePaint);
                canvas.drawLine (w, h, 0, h, gpsAvailablePaint);
                canvas.drawLine (0, h, 0, 0, gpsAvailablePaint);
            }
            wtnHandler.runDelayed (1024 - now % 1024, new Runnable () {
                @Override
                public void run ()
                {
                    view.invalidate ();
                }
            });
        }
    }

    public boolean blinkingRedOn ()
    {
        if (gpsDisabled != 0) return false;
        if (gpsAvailable) return false;
        long now = SystemClock.uptimeMillis ();
        return (now & 1024) == 0;
    }

    /**
     * Draw the airplane location arrow symbol.
     * @param canvas     = canvas to draw it on
     * @param pt         = where on canvas to draw symbol
     * @param canHdgRads = true heading on canvas that is up
     * Other inputs:
     *   heading = true heading for the arrow symbol (degrees, latest gps sample)
     *   speed = airplane speed (mps, latest gps sample)
     *   airplanePath,Scaling = drawing of airplane pointing at 0deg true
     *   thisGPSTimestamp = time of latest gps sample
     *   lastGPSTimestamp = time of previous gps sample
     *   lastGPSHeading = true heading for the arrow symbol (previous gps sample)
     */
    public void DrawLocationArrow (Canvas canvas, PointD pt, double canHdgRads)
    {
        /*
         * If not receiving GPS signal, blink the icon.
         */
        if (blinkingRedOn ()) return;

        /*
         * If not moving, draw circle instead of airplane.
         */
        if (currentGPSSpd < gpsMinSpeedMPS) {
            airplanePaint.setStyle (Paint.Style.STROKE);
            canvas.drawCircle ((float) pt.x, (float) pt.y, textSize * 0.5F, airplanePaint);
            return;
        }

        /*
         * Heading angle relative to UP on screen.
         */
        double hdg = currentGPSHdg - Math.toDegrees (canHdgRads);

        /*
         * Draw the icon.
         */
        canvas.save ();
        canvas.translate ((float) pt.x, (float) pt.y);  // anything drawn below will be translated this much
        canvas.rotate ((float) hdg);                    // anything drawn below will be rotated this much
        DrawAirplaneSymbol (canvas, textSize * 1.5);    // draw the airplane with vectors and filling
        canvas.restore ();                              // remove translation/scaling/rotation
    }

    public void DrawAirplaneSymbol (Canvas canvas, double pixels)
    {
        double scale = pixels / airplaneHeight;
        airplanePaint.setStyle (Paint.Style.FILL);
        canvas.scale ((float) scale, (float) scale);    // anything drawn below will be scaled this much
        canvas.drawPath (airplanePath, airplanePaint);  // draw the airplane with vectors and filling
    }

    /*************************\
     *  MENU key processing  *
    \*************************/

    // Display the main menu when the hardware menu button is clicked.
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // main menu
        menu.add ("Chart");
        menu.add ("Pages");
        menu.add ("Hide Tabs");
        menu.add ("Show Tabs");
        menu.add ("Re-center");
        menu.add ("Exit");

        return true;
    }

    // This is called when someone clicks on an item in the
    // main menu displayed by the hardware menu button.
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
        CharSequence sel = menuItem.getTitle ();
        if ("Chart".equals (sel)) {
            chartButton.onClick (chartButton);
        }
        if ("Exit".equals (sel)) {
            MyFinish ();
        }
        if ("Hide Tabs".equals (sel)) {
            SetTabVisibility (false);
        }
        if ("Pages".equals (sel)) {
            ShowMoreMenu ();
        }
        if ("Re-center".equals (sel) && hasAgreed) {
            chartView.ReCenter ();
        }
        if ("Show Tabs".equals (sel)) {
            SetTabVisibility (true);
        }
        return true;
    }

    /**
     * Save most recent GPS position to preferences so it will be there on restart.
     */
    private void saveLastKnownPosition ()
    {
        SharedPreferences prefs = getPreferences (MODE_PRIVATE);
        SharedPreferences.Editor editr = prefs.edit ();
        editr.putFloat ("lastKnownAlt", (float) currentGPSAlt);
        editr.putFloat ("lastKnownHdg", (float) currentGPSHdg);
        editr.putFloat ("lastKnownLat", (float) currentGPSLat);
        editr.putFloat ("lastKnownLon", (float) currentGPSLon);
        editr.putFloat ("lastKnownSpd", (float) currentGPSSpd);
        editr.putLong ("lastKnownTime", currentGPSTime);
        editr.commit ();
    }

    /**
     * Load last saved GPS position.
     */
    private void loadLastKnownPosition ()
    {
        SharedPreferences prefs = getPreferences (MODE_PRIVATE);
        currentGPSAlt  = prefs.getFloat ("lastKnownAlt", 0);
        currentGPSHdg  = prefs.getFloat ("lastKnownHdg", 0);
        currentGPSLat  = prefs.getFloat ("lastKnownLat", 0);
        currentGPSLon  = prefs.getFloat ("lastKnownLon", 0);
        currentGPSSpd  = prefs.getFloat ("lastKnownSpd", 0);
        currentGPSTime = prefs.getLong ("lastKnownTime", 0);
    }

    /**
     * Sense landscape/portrait change.
     */
    @Override
    public void onConfigurationChanged (Configuration config)
    {
        super.onConfigurationChanged (config);

        DisplayMetrics metrics = new DisplayMetrics ();
        getWindowManager ().getDefaultDisplay ().getMetrics (metrics);

        displayWidth  = metrics.widthPixels;
        displayHeight = metrics.heightPixels;

        dotsPerInchX  = metrics.xdpi;
        dotsPerInchY  = metrics.ydpi;
        dotsPerInch   = (float) Math.sqrt (dotsPerInchX * dotsPerInchY);
    }

    /**
     * "More" button clicked, show buttons, same as Tab buttons,
     * in an alert dialog box.
     */
    private void ShowMoreMenu ()
    {
        /*
         * Determine number of buttons columns.
         */
        int numCols = 2;
        if (displayWidth > displayHeight) {
            numCols = 4;
        }

        /*
         * Start a dialog box and a scroll view to put the buttons in.
         */
        ScrollView sv = new ScrollView (this);
        AlertDialog.Builder adb = new AlertDialog.Builder (this);
        adb.setView (sv);
        adb.setNegativeButton ("Cancel", null);

        /*
         * Set up on click listener to select the page when clicked.
         */
        final AlertDialog ad = adb.create ();
        View.OnClickListener menuButtonListener = new View.OnClickListener () {
            @Override
            public void onClick (View view)
            {
                Lib.dismiss (ad);
                TabButton tb = (TabButton) view.getTag ();
                tb.onClick (tb);
            }
        };

        /*
         * Go through the buttons in the tabButtonLayout,
         * creating a regular button for each one.
         */
        LinkedList<Button> menuButtons = new LinkedList<> ();
        int numButtons = tabButtonLayout.getChildCount ();
        for (int i = 0; i < numButtons; i ++) {
            View v = tabButtonLayout.getChildAt (i);
            if (v instanceof TabButton) {
                TabButton tb = (TabButton) v;
                if (tb.getVisibility () != View.GONE) {

                    /*
                     * Create a menu button that calls the TabButton when clicked.
                     */
                    Button but = new Button (this);
                    but.setOnClickListener (menuButtonListener);
                    but.setTag (tb);
                    but.setText (tb.ident);
                    but.setTextColor ((tb == currentTabButton) ? Color.RED : Color.BLACK);
                    but.setVisibility (tb.getVisibility ());
                    SetTextSize (but);
                    menuButtons.addLast (but);
                }
            }
        }

        /*
         * Determine number of button rows and allocate rows.
         */
        int numRows = (menuButtons.size () + numCols - 1) / numCols;
        TableLayout tableLayout = new TableLayout (this);
        TableRow[] rows = new TableRow[numRows];
        int i;
        for (i = 0; i < numRows; i ++) {
            rows[i] = new TableRow (this);
            tableLayout.addView (rows[i]);
        }

        /*
         * Add the menu buttons to the table.
         */
        i = 0;
        for (Button but : menuButtons) {
            rows[i].addView (but);
            if (++ i == numRows) i = 0;
        }

        /*
         * Add the button table to the scroll view and show the dialog.
         */
        sv.addView (tableLayout);
        ad.show ();
    }

    /*************************\
     *  BACK key processing  *
    \*************************/

    // Called directly by the hardware Back key
    @Override
    public void onBackPressed()
    {
        View backView = ((CanBeMainView) currentTabButton.view).GetBackPage ();
        if (backView != null) {
            SetCurrentTab (backView);
        } else {
            super.onBackPressed ();
        }
    }

    // Permission granting
    public final static int RC_INTGPS = 9876;
    @Override
    public void onRequestPermissionsResult (int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults)
    {
        if (requestCode == RC_INTGPS) {
            sensorsView.internalGPS.onRequestPermissionsResult ();
        }
    }

    /**
     * If a view implements this interface and is made current
     * via our SetCurrentTab() method, It will be called when the
     * view is replaced with something else, so it knows it can
     * close any bitmaps it might have open (to avoid out-of-
     * memory exceptions).
     */
    public interface CanBeMainView {
        String GetTabName ();
        int GetOrientation ();
        boolean IsPowerLocked ();
        void OpenDisplay ();
        void CloseDisplay ();
        void ReClicked ();
        View GetBackPage ();
    }
}
