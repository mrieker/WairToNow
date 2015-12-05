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

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WairToNow extends Activity {
    private final static String TAG = "WairToNow";
    public final static long gpsDownDelay = 15000;

    public static String dbdir;
    public static WTNHandler wtnHandler;

    private boolean gpsAvailable;
    private boolean hasAgreed;
    private boolean lastLocQueued;
    private boolean tabsVisible;
    public  ChartView chartView;
    public  CrumbsView crumbsView;
    private DetentHorizontalScrollView tabButtonScroller;
    public  float currentGPSLat;
    public  float currentGPSLon;
    private float currentGPSHdg;
    public  float textSize;
    private GlassView glassView;
    private GPSListener gpsListener;
    private GPSStatusView gpsStatusView;
    public  int displayWidth;
    public  int displayHeight;
    private LinearLayout tabButtonLayout;
    private LinearLayout tabViewLayout;
    private LinearLayout.LayoutParams ctvllp = new LinearLayout.LayoutParams (
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    private LinearLayout.LayoutParams tbsllp = new LinearLayout.LayoutParams (
            LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    private LocationManager locationManager;
    private long lastLocUpdate;
    public  MaintView maintView;
    public  OpenStreetMap openStreetMap;
    public  OptionsView optionsView;
    private Paint gpsAvailableBGPaint;
    private Paint gpsAvailableTxPaint;
    private PlanView planView;
    public  RouteView routeView;
    private TabButton agreeButton;
    public  TabButton currentTabButton;
    public  UserWPView userWPView;
    public  WaypointView waypointView1, waypointView2;
    private Waypoint pendingCourseSetWP;

    /** Called when the activity is first created. */
    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate (savedInstanceState);

        if (wtnHandler == null) wtnHandler = new WTNHandler ();

        ctvllp.weight = 1;

        File efd = getExternalFilesDir (null);
        if (efd == null) {
            StartupError ("external storage not available");
            return;
        }
        dbdir = efd.getAbsolutePath ();

        /*
         * Make sure database directory exists.
         * Also mark it nomedia so meadia scanner will leave it alone
         * as it will contain thoushands of .png files.
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
        DisplayMetrics metrics = new DisplayMetrics ();
        getWindowManager ().getDefaultDisplay ().getMetrics (metrics);
        float dpi = Mathf.sqrt (metrics.xdpi * metrics.ydpi);
        textSize = dpi / 7;

        /*
         * Also get screen size in pixels.
         */
        displayWidth  = metrics.widthPixels;
        displayHeight = metrics.heightPixels;

        /*
         * Paint used to display GPS NOT AVAILABLE string.
         */
        gpsAvailableBGPaint = new Paint ();
        gpsAvailableBGPaint.setColor (Color.WHITE);
        gpsAvailableBGPaint.setStrokeWidth (20);
        gpsAvailableBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        gpsAvailableBGPaint.setTextAlign (Paint.Align.CENTER);
        gpsAvailableBGPaint.setTextSize (textSize * 2);
        gpsAvailableTxPaint = new Paint ();
        gpsAvailableTxPaint.setColor (Color.RED);
        gpsAvailableTxPaint.setStrokeWidth (2);
        gpsAvailableTxPaint.setTextAlign (Paint.Align.CENTER);
        gpsAvailableTxPaint.setTextSize (textSize * 2);

        /*
         * License agreement.
         */
        AgreeView agreeView = new AgreeView (this);

        /*
         * Load options before doing anything else.
         */
        optionsView = new OptionsView (this);

        /*
         * Open Street Maps access.
         */
        openStreetMap = new OpenStreetMap (this);

        /*
         * Planning page.
         */
        planView = new PlanView (this);

        /*
         * Create a view that views charts based on lat/lon.
         * Create a view for updating database.
         */
        try {
            maintView = new MaintView (this);
            chartView = new ChartView (this);
        } catch (IOException ioe) {
            StartupError ("error reading chart database", ioe);
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
        gpsStatusView = new GPSStatusView (this);

        /*
         * Set up GPS sampling.
         * We don't actually enable it here,
         * it gets enabled/disabled by the onResume()/onPause()
         * methods so we only sample when the app is actually
         * being used.
         */
        locationManager = (LocationManager)getSystemService (LOCATION_SERVICE);
        gpsListener = new GPSListener ();
        Location lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastLoc == null) {
            lastLoc = new Location ("WairToNow onCreate()");
        }
        gpsListener.onLocationChanged (lastLoc);

        /*
         * Set up tab system.
         */
        agreeButton               = new TabButton (agreeView);
        TabButton chartButton     = new TabButton (chartView);
        TabButton waypt1Button    = new TabButton (waypointView1);
        TabButton waypt2Button    = new TabButton (waypointView2);
        TabButton userWPButton    = new TabButton (userWPView);
        TabButton glassButton     = new TabButton (glassView);
        TabButton routeButton     = new TabButton (routeView);
        TabButton crumbsButton    = new TabButton (crumbsView);
        TabButton planButton      = new TabButton (planView);
        TabButton optionsButton   = new TabButton (optionsView);
        TabButton maintButton     = new TabButton (maintView);
        TabButton gpsStatusButton = new TabButton (gpsStatusView);
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
        tabButtonLayout.addView (routeButton);
        tabButtonLayout.addView (crumbsButton);
        tabButtonLayout.addView (planButton);
        tabButtonLayout.addView (optionsButton);
        tabButtonLayout.addView (maintButton);
        tabButtonLayout.addView (gpsStatusButton);
        tabButtonLayout.addView (filesButton);
        tabButtonLayout.addView (helpButton);
        //tabButtonLayout.addView (new TabButton (new ExpView (this)));

        tabButtonScroller = new DetentHorizontalScrollView (this);
        SetDetentSize (tabButtonScroller);
        tabButtonScroller.addView (tabButtonLayout);

        tabViewLayout = new LinearLayout (this);
        tabViewLayout.setOrientation (LinearLayout.VERTICAL);
        tabViewLayout.addView (tabButtonScroller);

        setContentView (tabViewLayout);
        tabsVisible = true;

        chartButton.DisplayNewTab ();
    }

    /**
     * Some error starting up, display error message dialog then die.
     */
    private void StartupError (String msg, Exception e)
    {
        String emsg = e.getMessage ();
        if (emsg == null) emsg = e.getClass ().getSimpleName ();
        StartupError (msg + ": " + emsg);
    }

    private void StartupError (String msg)
    {
        AlertDialog.Builder adb = new AlertDialog.Builder (this);
        adb.setTitle ("Startup error");
        adb.setMessage (msg);
        adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i) {
                System.exit (- 1);
            }
        });
        adb.show ();
    }

    /**
     * App is being taken out of memory, make sure files are closed.
     */
    @Override
    public void onDestroy ()
    {
        crumbsView.CloseFiles ();
        SQLiteDBs.CloseAll ();
        super.onDestroy ();
    }

    /**
     * The button used for each tab.
     */
    private class TabButton extends Button implements View.OnClickListener {
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

            // show the new view and force tab buttons visible
            currentTabButton = this;
            SetTabVisibility (true);

            // tell the new view that it is now being displayed
            ((CanBeMainView)view).OpenDisplay ();
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
    public void SetTabVisibility (boolean vis)
    {
        tabsVisible = vis;
        tabViewLayout.removeAllViews ();
        tabViewLayout.addView (currentTabButton.view, ctvllp);
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
        dhsv.setDetentSize (Math.min (displayWidth, displayHeight) / 4.0F);
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
     * Set the current location of the airplane.
     */
    public void SetCurrentLocation (Location loc)
    {
        /* Course on approach to KBVY/RW09
            long now = System.currentTimeMillis ();
            if (starttime == 0) {
                starttime = now;
                chartView.SetCourseLine (lat0, lon0, 42.5841410277778, -70.9161444166667, "KBVY");
            }
            float dt  = (now - starttime) / 60000.0F;
            if (dt >= 1.0F) starttime = now;
            float lat = lat0 + (lat1 - lat0) * dt;
            float lon = lon0 + (lon1 - lon0) * dt;
            loc.setAltitude  (100);
            loc.setBearing   (Lib.LatLonTC (lat0, lon0, lat1, lon1));
            loc.setLatitude  (lat);
            loc.setLongitude (lon);
            Message m = wtnHandler.obtainMessage (333, new Object[] { gpsListener, loc });
            wtnHandler.sendMessageDelayed (m, 333);
        */

        //  loc.setLatitude  (  42.0 + 28.0/60);  //KBED
        //  loc.setLongitude ( -71.0 - 17.5/60);  //KBED

        //  loc.setLatitude  (  42.0 + 35.0/60);  //KBVY
        //  loc.setLongitude ( -70.0 - 55.0/60);  //KBVY
        //  loc.setBearing   (35.0f);

        //  loc.setLatitude  (  42.0 + 09.6/60);  //KELM
        //  loc.setLongitude ( -76.0 - 53.5/60);  //KELM

        //  loc.setLatitude  (  41.0 + 55.0/60);  //near KELM shows four charts
        //  loc.setLongitude ( -76.0 - 57.0/60);  //near KELM
        //  loc.setBearing   (35.0f);

        //  loc.setLatitude  (  61.174083);       //PANC Anchorage
        //  loc.setLongitude (-149.998194);

        //  loc.setLatitude  (41.863060);         //near KELM shows twisted Det North east edge
        //  loc.setLongitude (-76.981982);

        //  loc.setLatitude  (  31.0 + 35.0/60);  //KFHU rotated apt diagram
        //  loc.setLongitude (-110.0 - 21.0/60);  //KFHU

        //  loc.setLatitude  (  35.237);  //near KSBP
        //  loc.setLongitude (-120.900);

        //  loc.setLatitude  ( -14.332);  //KPPG southern hemisphere
        //  loc.setLongitude (-170.712);

        //  loc.setLatitude  (  41.0 + 50.0/60);  //1M8 (Myrics)
        //  loc.setLongitude ( -71.0 -  1.0/60);

        //  loc.setLatitude  ( 13.483874);        //PGUM east longitude
        //  loc.setLongitude (144.797170);

        //  loc.setLatitude  ( 52.0 + 42.0/60);   //PASY east longitude
        //  loc.setLongitude (174.0 +  7.0/60);

        currentGPSLat = (float) loc.getLatitude ();
        currentGPSLon = (float) loc.getLongitude ();
        if (loc.getSpeed () < 1.0F) {
            loc.setBearing (currentGPSHdg);
        } else {
            currentGPSHdg = loc.getBearing ();
        }

        if (pendingCourseSetWP != null) {
            chartView.SetCourseLine (currentGPSLat, currentGPSLon, pendingCourseSetWP);
            pendingCourseSetWP = null;
        }

        chartView.SetGPSLocation (loc);
        crumbsView.SetGPSLocation (loc);
        glassView.SetGPSLocation (loc);
        gpsStatusView.SetGPSLocation (loc);
        routeView.SetGPSLocation (loc);
        waypointView1.SetGPSLocation (loc);
        waypointView2.SetGPSLocation (loc);

        if (currentTabButton != null) {
            currentTabButton.view.invalidate ();
        }
    }

    /**
     * Called when app is now in the foreground.
     */
    @Override
    public void onResume ()
    {
        super.onResume ();

        hasAgreed = false;
        SharedPreferences prefs = getPreferences (Activity.MODE_PRIVATE);
        long now = System.currentTimeMillis ();
        long agreed = prefs.getLong ("hasAgreed", 0);
        if (now - agreed < 30*24*60*60*1000L) {
            HasAgreed ();
        } else {
            agreeButton.setVisibility (View.VISIBLE);
            agreeButton.DisplayNewTab ();
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
        locationManager.requestLocationUpdates (LocationManager.GPS_PROVIDER, 1000L, 1.0F, gpsListener);
        locationManager.addGpsStatusListener (gpsListener);
    }

    /**
     * Called when app is now in the background.
     */
    @Override
    public void onPause ()
    {
        super.onPause ();
        locationManager.removeUpdates (gpsListener);
        locationManager.removeGpsStatusListener (gpsListener);

        // maybe some database files marked for delete
        // so close our handles so they will be deleted
        SQLiteDBs.CloseAll ();
    }

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
                case 333: {
                    Object[] args = (Object[]) msg.obj;
                    GPSListener gll = (GPSListener) args[0];
                    Location loc = (Location) args[1];
                    gll.onLocationChanged (loc);
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

    private class GPSListener implements GpsStatus.Listener, LocationListener {
        private GpsStatus gpsStatus;

        @Override  // LocationListener
        public void onLocationChanged (Location loc)
        {
            lastLocUpdate = SystemClock.uptimeMillis ();
            if (!lastLocQueued) {
                lastLocQueued = true;
                Message msg = wtnHandler.obtainMessage (334, WairToNow.this);
                wtnHandler.sendMessageAtTime (msg, lastLocUpdate + gpsDownDelay);
            }

            onStatusChanged (loc.getProvider (), LocationProvider.AVAILABLE, null);
            if (!planView.pretendEnabled) SetCurrentLocation (loc);
        }

        @Override  // LocationListener
        public void onProviderDisabled(String arg0)
        { }

        @Override  // LocationListener
        public void onProviderEnabled(String arg0)
        { }

        @Override  // LocationListener
        public void onStatusChanged(String provider, int status, Bundle extras)
        {
            gpsAvailable = (status == LocationProvider.AVAILABLE);
            if (currentTabButton != null) currentTabButton.view.invalidate ();
        }

        @Override  // GpsStatus.Listener
        public void onGpsStatusChanged (int event)
        {
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS: {
                    gpsStatus = locationManager.getGpsStatus (gpsStatus);
                    gpsStatusView.SetGPSStatus (gpsStatus);
                    if (currentTabButton != null) currentTabButton.view.invalidate ();
                }
            }
        }
    }

    /**
     * Draw GPS availability message on georeferenced page.
     * @param canvas = what to draw it on
     * @param view   = view being drawn on
     */
    public void drawGPSAvailable (Canvas canvas, View view)
    {
        String msg = null;
        if (planView.pretendEnabled) {
            msg = "PLAN PRETEND MODE";
        } else if (!gpsAvailable) {
            msg = "GPS NOT UPDATING";
        }
        if (msg != null) {
            int w = view.getWidth ();
            int h = view.getHeight ();
            float msgw = gpsAvailableTxPaint.measureText (msg);
            if (msgw > w * 7 / 8) {
                float ts = gpsAvailableTxPaint.getTextSize ();
                ts *= (w * 7 / 8) / msgw;
                gpsAvailableBGPaint.setTextSize (ts);
                gpsAvailableTxPaint.setTextSize (ts);
            }
            canvas.drawText (msg, w / 2, h * 3 / 4, gpsAvailableBGPaint);
            canvas.drawText (msg, w / 2, h * 3 / 4, gpsAvailableTxPaint);
        }
    }

    /*************************\
     *  MENU key processing  *
    \*************************/

    // Display the main menu when the hardware menu button is clicked.
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // main menu
        menu.add ("Hide");
        menu.add ("Show");
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
        if ("Exit".equals (sel)) {
            SQLiteDBs.CloseAll ();
            System.exit (0); // finish () doesn't really exit
        }
        if ("Hide".equals (sel)) {
            SetTabVisibility (false);
        }
        if ("Re-center".equals (sel) && hasAgreed) {
            chartView.ReCenter ();
            SetCurrentTab (chartView);
        }
        if ("Show".equals (sel)) {
            SetTabVisibility (true);
        }
        return true;
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

    /**
     * If a view implements this interface and is made current
     * via our SetCurrentTab() method, It will be called when the
     * view is replaced with something else, so it knows it can
     * close any bitmaps it might have open (to avoid out-of-
     * memory exceptions).
     */
    public interface CanBeMainView {
        String GetTabName ();
        void OpenDisplay ();
        void CloseDisplay ();
        void ReClicked ();
        View GetBackPage ();
    }
}
