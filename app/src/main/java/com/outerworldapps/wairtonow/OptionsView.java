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
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Display a page of selectable options.
 */
@SuppressLint("ViewConstructor")
public class OptionsView
        extends LinearLayout
        implements WairToNow.CanBeMainView {
    public final static String TAG = "WairToNow";

    // works on 30 (emulated Nexus 10)
    // works on 29 (emulated Nexus 10)
    // works on 28 (hardware Tab A)
    // broken on 28 (emulated Nexus 10)
    // broken on 27 (emulated Nexus 10)
    // broken on 25 (emulated Nexus 10)
    // broken on 24 (hardware LG K20)
    // broken on 22 (hardware Nexus 10)
    private final static boolean PDOVERLAYWORKS = false; // (Build.VERSION.SDK_INT >= 29);

    private final static String fontSizeDefault = "Medium";
    private final static NNLinkedHashMap<String,Integer> fontSizeMap = getFontSizeMap ();

    public  CheckOption  capGridOption;
    public  CheckOption  collDetOption;
    public  CheckOption  dbFAAOption;
    public  DbEnOption   dbOAOption;
    public  DbEnOption   dbOFMOption;
    public  CheckOption  gpsCompassOption;
    public  CheckOption  invPlaColOption;
    public  CheckOption  powerLockOption;
    public  CheckOption  showNexrad;
    public  CheckOption  showTraffic;
    public  CheckOption  showWxSumDot;
    public  CheckOption  synthILSDMEOption;
    public  CheckOption  tfrPDOverOption;
    public  CheckOption  typeBOption;
    public  CheckOption  userWPOption;
    public  CheckOption  wayptOption;
    public  DefAltOption ktsMphOption;
    public  DefAltOption magTrueOption;
    public  FontOption   fontSizeOption;
    public  IntOption    chartTrackOption;
    public  IntOption    circCatOption;
    public  IntOption    gpsUpdateOption;
    public  IntOption    latLonOption;
    public  IntOption    tfrFilterOption;

    private TableLayout mangeoreftable;
    private WairToNow wairToNow;

    private final static int LLO_DDMMMM = 0;
    private final static int LLO_DDMMSS = 1;
    private final static int LLO_DDDDDD = 2;

    public final static int CTO_COURSEUP = 0;
    public final static int CTO_NORTHUP  = 1;
    public final static int CTO_TRACKUP  = 2;
    public final static int CTO_FINGEROT = 3;

    public final static int TFR_NONE = 0;
    public final static int TFR_ACTIVE = 1;
    public final static int TFR_TODAY = 2;
    public final static int TFR_3DAYS = 3;
    public final static int TFR_ALL = 4;

    private static NNLinkedHashMap<String,Integer> getFontSizeMap ()
    {
        NNLinkedHashMap<String,Integer> fsm = new NNLinkedHashMap<> ();
        fsm.put ("Tiny",  12);
        fsm.put ("Small",  9);
        fsm.put ("Medium", 7);
        fsm.put ("Large",  5);
        fsm.put ("Huge",   4);
        return fsm;
    }

    // called very early
    public static int getFontSize ()
    {
        try {
            BufferedReader csvreader = new BufferedReader (new FileReader (WairToNow.dbdir + "/options.csv"), 1024);
            try {
                String csvline;
                while ((csvline = csvreader.readLine ()) != null) {
                    int i = csvline.indexOf (',');
                    String name = csvline.substring (0, i);
                    String valu = csvline.substring (++ i);
                    if (name.equals ("fontSize")) return fontSizeMap.nnget (valu);
                }
            } finally {
                csvreader.close ();
            }
        } catch (IOException ioe) {
            Log.i (TAG, "no options file yet");
        }
        return fontSizeMap.nnget (fontSizeDefault);
    }

    @SuppressLint("SetTextI18n")
    public OptionsView (WairToNow ctx)
    {
        super (ctx);

        wairToNow = ctx;

        setOrientation (LinearLayout.VERTICAL);

        TextView tv1 = new TextView (ctx);
        tv1.setText ("Options");
        wairToNow.SetTextSize (tv1);
        addView (tv1);

        capGridOption     = new CheckOption ("Show CAP grids",              false);
        collDetOption     = new CheckOption ("Show obstacle/terrain collision", false);
        wayptOption       = new CheckOption ("Show Database waypoints",     false);
        userWPOption      = new CheckOption ("Show User waypoints",         true);
        invPlaColOption   = new CheckOption ("Invert plate colors",         false);
        synthILSDMEOption = new CheckOption ("Show Synth ILS/DME Plates",   false);
        typeBOption       = new TypeBOption ();
        powerLockOption   = new CheckOption ("Power Lock",                  false);
        gpsCompassOption  = new CheckOption ("GPS status compass",          false);
        showNexrad        = new CheckOption ("Show ADS-B Nexrad (2D only)", false);
        showTraffic       = new CheckOption ("Show ADS-B Traffic",          false);
        showWxSumDot      = new CheckOption ("Show Wx Summary Dots",        false);
        dbFAAOption       = new CheckOption ("Use FAA database & charts",   true);
        dbOAOption        = new DbEnOption ("Use ourairports.com database", "file:///android_asset/oawayptwarning.html");
        dbOFMOption       = new DbEnOption ("Use openflightmaps.org database & charts", "file:///android_asset/ofmwayptwarning.html");
        tfrPDOverOption   = new CheckOption ("TFR PD Overlay shading", PDOVERLAYWORKS);

        magTrueOption = new DefAltOption ("Magnetic", "True");
        ktsMphOption  = new DefAltOption ("Kts", "MPH");

        fontSizeOption  = new FontOption ();

        tfrFilterOption = new IntOption ("TFR Filter",
                new String[] { "TFR: ALL", "TFR: 3DAYS", "TFR: TODAY", "TFR: ACTIVE", "TFR: NONE" },
                new int[] { TFR_ALL, TFR_3DAYS, TFR_TODAY, TFR_ACTIVE, TFR_NONE });
        tfrFilterOption.setKeyNoWrite (tfrFilterOption.keys[1]);


        latLonOption    = new IntOption ("LatLon Format",
            new String[] {
                "ddd" + (char)0xB0 + "mm'ss.ss\"",
                "ddd" + (char)0xB0 + "mm.mmmm'",
                "ddd.dddddd" + (char)0xB0 },
            new int[] { LLO_DDMMSS, LLO_DDMMMM, LLO_DDDDDD });

        chartTrackOption = new IntOption ("Chart Up",
            new String[] { "Course Up", "Finger Rotate", "North Up", "Track Up" },
            new int[] { CTO_COURSEUP, CTO_FINGEROT, CTO_NORTHUP, CTO_TRACKUP });
        chartTrackOption.setKeyNoWrite (chartTrackOption.keys[2]);

        gpsUpdateOption = new IntOption ("GPS Update Rate",
                new String[] {
                        "GPS updates 10 per sec",
                        "GPS updates 3 per sec",
                        "GPS updates 1 per sec"
                },
                new int[] { 10, 3, 1 }
        );

        circCatOption = new IntOption ("Circling Category",
                new String[] {
                        "Circle Category A (1-90kt)",
                        "Circle Category B (91-120kt)",
                        "Circle Category C (121-140kt)",
                        "Circle Category D (141-165kt)",
                        "Circle Category E (166+kt)",
                        "Circle Shading Disabled"
                },
                new int[] { 0, 1, 2, 3, 4, -1 }
        );

        mangeoreftable = new TableLayout (wairToNow);

        LinearLayout ll1 = new LinearLayout (ctx);
        ll1.setOrientation (LinearLayout.VERTICAL);

        ll1.addView (fontSizeOption);
        ll1.addView (dbFAAOption);
        ll1.addView (dbOAOption);
        ll1.addView (dbOAOption.warning);
        ll1.addView (dbOFMOption);
        ll1.addView (dbOFMOption.warning);
        ll1.addView (tfrFilterOption);
        ll1.addView (tfrPDOverOption);
        ll1.addView (capGridOption);
        ll1.addView (collDetOption);
        ll1.addView (wayptOption);
        ll1.addView (userWPOption);
        ll1.addView (invPlaColOption);
        ll1.addView (synthILSDMEOption);
        ll1.addView (typeBOption);
        ll1.addView (showNexrad);
        ll1.addView (showTraffic);
        ll1.addView (showWxSumDot);
        ll1.addView (powerLockOption);
        ll1.addView (gpsCompassOption);
        ll1.addView (chartTrackOption);
        ll1.addView (magTrueOption);
        ll1.addView (latLonOption);
        ll1.addView (ktsMphOption);
        ll1.addView (gpsUpdateOption);
        ll1.addView (circCatOption);
        ll1.addView (mangeoreftable);

        LayoutParams llp = new LayoutParams (LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ScrollView sv1 = new ScrollView (ctx);
        sv1.addView (ll1);
        addView (sv1, llp);

        ReadOptionsCsvFile ();
    }

    /**
     * Format a heading into a string according to user-selected options.
     * @param hdg = true heading (degrees)
     * @param magvar = magnetic variation (degrees; mag = true + magvar)
     * @return string of heading
     */
    public String HdgString (double hdg, double magvar)
    {
        String suffix = (char)0xB0 + " true";
        if (!magTrueOption.getAlt ()) {
            hdg += magvar;
            suffix = (char)0xB0 + " mag";
        }
        while (hdg < 0.0) hdg += 360.0;
        int ihdg = (int) Math.round (hdg + 359) % 360 + 1;
        return Integer.toString (ihdg + 1000).substring (1) + suffix;
    }

    /**
     * Convert a lat/lon numeric value to a string according to user-selected options.
     * @param val = numeric value (degrees)
     * @param posch = char to display if positive (N or E)
     * @param negch = char to display if negative (S or W)
     * @return resultant string
     */
    public String LatLonString (double val, char posch, char negch)
    {
        if (val < 0) {
            posch ^= negch;
            negch ^= posch;
            posch ^= negch;
            val = -val;
        }
        if (val > 180) {
            posch ^= negch;
            negch ^= posch;
            posch ^= negch;
            val = 360 - val;
        }
        String str;
        switch (latLonOption.getVal ()) {

            // ddd^mm'ss.ss"
            case LLO_DDMMSS: {
                val *= 360000;
                val += 0.5;
                int sec00 = (int)val;
                int min   = sec00 / 6000;
                int deg   = min   / 60;
                sec00 %= 6000;
                min   %= 60;

                str = Integer.toString (deg) + (char)0xB0 + min + '\'';

                if (sec00 > 0) {
                    String sec00st = String.format (Locale.US, "%03d", sec00);
                    int sec00ln = sec00st.length ();
                    str += sec00st.substring (0, sec00ln - 2) + '.' + sec00st.substring (sec00ln - 2);
                    while (str.endsWith ("0")) str = str.substring (0, str.length () - 1);
                    if (str.endsWith (".")) str = str.substring (0, str.length () - 1);
                    str += '"';
                }
                break;
            }

            // ddd^mm.mmmmm'
            case LLO_DDMMMM: {
                val *= 600000;
                val += 0.5;
                int min0000 = (int)val;
                int deg = min0000 / 600000;
                min0000 %= 600000;

                String min0000st = String.format (Locale.US, "%05d", min0000);
                int min0000ln = min0000st.length ();
                str = Integer.toString (deg) + (char)0xB0 + min0000st.substring (0, min0000ln - 4) + '.' + min0000st.substring (min0000ln - 4);
                while (str.endsWith ("0")) str = str.substring (0, str.length () - 1);
                if (str.endsWith (".")) str = str.substring (0, str.length () - 1);
                str += '\'';
                break;
            }

            // ddd.dddddd^
            case LLO_DDDDDD: {
                int ival = (int) Math.round (val * 1000000);
                int dpts = 6;
                while ((dpts > 1) && (ival % 10 == 0)) {
                    ival /= 10;
                    dpts --;
                }
                str = String.format (Locale.US, "%0" + dpts + "d", ival);
                int len = str.length ();
                str = str.substring (0, len - dpts) + '.' + str.substring (len - dpts) + (char)0xB0;
                break;
            }

            default: throw new RuntimeException ();
        }
        return str + posch;
    }

    /**
     * Load significant state from non-volatile memory.
     */
    private final static String boolTrue = Boolean.toString (true);
    private void ReadOptionsCsvFile ()
    {
        try {
            try {
                ReadOptionsCsvFileWork ();
            } catch (FileNotFoundException fnfe) {
                Log.i (TAG, "no options.csv file yet", fnfe);
                WriteOptionsCsvFile ();
                ReadOptionsCsvFileWork ();
            }
        } catch (Exception e) {
            Log.w (TAG, "error reading options.csv", e);
        }
    }

    private void ReadOptionsCsvFileWork ()
            throws IOException
    {
        BufferedReader csvreader = new BufferedReader (new FileReader (WairToNow.dbdir + "/options.csv"), 1024);
        try {
            String csvline;
            while ((csvline = csvreader.readLine ()) != null) {
                int i = csvline.indexOf (',');
                String name = csvline.substring (0, i);
                String valu = csvline.substring (++ i);
                if (name.equals ("capGrid"))      capGridOption.setCheckedNoWrite     (valu.equals (boolTrue));
                if (name.equals ("collDet"))      collDetOption.setCheckedNoWrite     (valu.equals (boolTrue));
                if (name.equals ("waypts"))       wayptOption.setCheckedNoWrite       (valu.equals (boolTrue));
                if (name.equals ("userWPs"))      userWPOption.setCheckedNoWrite      (valu.equals (boolTrue));
                if (name.equals ("invPlaCol"))    invPlaColOption.setCheckedNoWrite   (valu.equals (boolTrue));
                if (name.equals ("synthILSDMEs")) synthILSDMEOption.setCheckedNoWrite (valu.equals (boolTrue));
                if (name.equals ("typeB"))        typeBOption.setCheckedNoWrite       (valu.equals (boolTrue));
                if (name.equals ("powerLock"))    powerLockOption.setCheckedNoWrite   (valu.equals (boolTrue));
                if (name.equals ("gpsCompass"))   gpsCompassOption.setCheckedNoWrite  (valu.equals (boolTrue));
                if (name.equals ("showNexrad"))   showNexrad.setCheckedNoWrite        (valu.equals (boolTrue));
                if (name.equals ("showTraffic"))  showTraffic.setCheckedNoWrite       (valu.equals (boolTrue));
                if (name.equals ("showWxSumDot")) showWxSumDot.setCheckedNoWrite      (valu.equals (boolTrue));
                if (name.equals ("chartTrack"))   chartTrackOption.setKeyNoWrite      (valu);
                if (name.equals ("magtrueAlt"))   magTrueOption.setAltNoWrite         (valu.equals (boolTrue));
                if (name.equals ("latlonAlt"))    latLonOption.setKeyNoWrite          (valu);
                if (name.equals ("ktsMphAlt"))    ktsMphOption.setAltNoWrite          (valu.equals (boolTrue));
                if (name.equals ("gpsUpdate"))    gpsUpdateOption.setKeyNoWrite       (valu);
                if (name.equals ("circCat"))      circCatOption.setKeyNoWrite         (valu);
                if (name.equals ("fontSize"))     fontSizeOption.setKeyNoWrite        (valu);
                if (name.equals ("tfrFilter"))    tfrFilterOption.setKeyNoWrite       (valu);
                if (name.equals ("tfrPDOver"))    tfrPDOverOption.setCheckedNoWrite   (valu.equals (boolTrue));
                if (name.equals ("dbFAAEnab"))    dbFAAOption.setCheckedNoWrite       (valu.equals (boolTrue));
                if (name.equals ("dbOAEnab"))     dbOAOption.setCheckedNoWrite        (valu.equals (boolTrue));
                if (name.equals ("dbOFMEnab"))    dbOFMOption.setCheckedNoWrite       (valu.equals (boolTrue));
            }
        } finally {
            csvreader.close ();
        }
    }

    /**
     * Save significant state to non-volatile memory.
     */
    private void WriteOptionsCsvFile ()
    {
        String csvname = WairToNow.dbdir + "/options.csv";
        try {
            BufferedWriter csvwriter = new BufferedWriter (new FileWriter (csvname), 1024);
            try {
                csvwriter.write ("capGrid,"      + capGridOption.checkBox.isChecked ()     + "\n");
                csvwriter.write ("collDet,"      + collDetOption.checkBox.isChecked ()     + "\n");
                csvwriter.write ("waypts,"       + wayptOption.checkBox.isChecked ()       + "\n");
                csvwriter.write ("userWPs,"      + userWPOption.checkBox.isChecked ()      + "\n");
                csvwriter.write ("invPlaCol,"    + invPlaColOption.checkBox.isChecked ()   + "\n");
                csvwriter.write ("synthILSDMEs," + synthILSDMEOption.checkBox.isChecked () + "\n");
                csvwriter.write ("typeB,"        + typeBOption.checkBox.isChecked ()       + "\n");
                csvwriter.write ("powerLock,"    + powerLockOption.checkBox.isChecked ()   + "\n");
                csvwriter.write ("gpsCompass,"   + gpsCompassOption.checkBox.isChecked ()  + "\n");
                csvwriter.write ("showNexrad,"   + showNexrad.checkBox.isChecked ()        + "\n");
                csvwriter.write ("showTraffic,"  + showTraffic.checkBox.isChecked ()       + "\n");
                csvwriter.write ("showWxSumDot," + showWxSumDot.checkBox.isChecked ()      + "\n");
                csvwriter.write ("chartTrack,"   + chartTrackOption.getKey ()              + "\n");
                csvwriter.write ("magtrueAlt,"   + magTrueOption.getAlt ()                 + "\n");
                csvwriter.write ("latlonAlt,"    + latLonOption.getKey ()                  + "\n");
                csvwriter.write ("ktsMphAlt,"    + ktsMphOption.getAlt ()                  + "\n");
                csvwriter.write ("gpsUpdate,"    + gpsUpdateOption.getKey ()               + "\n");
                csvwriter.write ("circCat,"      + circCatOption.getKey ()                 + "\n");
                csvwriter.write ("fontSize,"     + fontSizeOption.getKey ()                + "\n");
                csvwriter.write ("tfrFilter,"    + tfrFilterOption.getKey ()               + "\n");
                csvwriter.write ("tfrPDOver,"    + tfrPDOverOption.checkBox.isChecked ()   + "\n");
                csvwriter.write ("dbFAAEnab,"    + dbFAAOption.checkBox.isChecked ()       + "\n");
                csvwriter.write ("dbOAEnab,"     + dbOAOption.checkBox.isChecked ()        + "\n");
                csvwriter.write ("dbOFMEnab,"    + dbOFMOption.checkBox.isChecked ()       + "\n");
            } finally {
                csvwriter.close ();
            }
        } catch (IOException ioe) {
            Log.e (TAG, "error writing " + csvname, ioe);
        }
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Options";
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
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
        dbOAOption.warning.setVisibility (GONE);
        dbOFMOption.warning.setVisibility (GONE);
        buildManGeoRefTable ();
    }

    @Override  // CanBeMainView
    public void OrientationChanged ()
    { }

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
     * Build the manual georeference contribution registration table for display.
     */
    @SuppressLint("SetTextI18n")
    private void buildManGeoRefTable ()
    {
        mangeoreftable.removeAllViews ();
        SQLiteDBs sqldb = SQLiteDBs.open ("manualiapgeorefs.db");
        if (sqldb != null) {
            Cursor result = sqldb.query ("register", new String[] { "mr_username", "mr_password", "mr_emailaddr"}, null, null, null, null, null, null);
            try {
                if (result.moveToFirst ()) {
                    String unold = result.getString (0);
                    String pwold = result.getString (1);
                    String emold = result.getString (2);

                    TextView hdglbl  = new TextView (wairToNow);
                    wairToNow.SetTextSize (hdglbl);
                    hdglbl.setText ("Manual GeoRef Contribution");

                    TextView unlabel = new TextView (wairToNow);
                    wairToNow.SetTextSize (unlabel);
                    unlabel.setText ("   Username:");

                    TextView pwlabel = new TextView (wairToNow);
                    wairToNow.SetTextSize (pwlabel);
                    pwlabel.setText ("   Password:");

                    TextView emlabel = new TextView (wairToNow);
                    wairToNow.SetTextSize (emlabel);
                    emlabel.setText ("   Email Addr:");

                    final EditText pwvalue = new EditText (wairToNow);
                    final EditText emvalue = new EditText (wairToNow);
                    pwvalue.setSingleLine ();
                    emvalue.setSingleLine ();
                    pwvalue.setEms (10);
                    emvalue.setEms (10);
                    pwvalue.setInputType (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    emvalue.setInputType (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                    wairToNow.SetTextSize (pwvalue);
                    wairToNow.SetTextSize (emvalue);

                    CheckBox pwvisib = new CheckBox (wairToNow);
                    wairToNow.SetTextSize (pwvisib);
                    pwvisib.setText ("show");
                    pwvisib.setOnCheckedChangeListener (new CompoundButton.OnCheckedChangeListener () {
                        @Override
                        public void onCheckedChanged (CompoundButton compoundButton, boolean b)
                        {
                            pwvalue.setTransformationMethod (b ?
                                    HideReturnsTransformationMethod.getInstance () :
                                    PasswordTransformationMethod.getInstance ());
                        }
                    });

                    TableRow unrow = new TableRow (wairToNow);
                    TableRow pwrow = new TableRow (wairToNow);
                    TableRow emrow = new TableRow (wairToNow);
                    mangeoreftable.addView (hdglbl);
                    mangeoreftable.addView (unrow);
                    mangeoreftable.addView (pwrow);
                    mangeoreftable.addView (emrow);

                    unrow.addView (unlabel);
                    pwrow.addView (pwlabel);
                    emrow.addView (emlabel);

                    if (unold.equals ("")) {
                        // register new username, password, email address
                        final EditText unvalue = new EditText (wairToNow);
                        Button rgbuttn = new Button (wairToNow);
                        Button prbuttn = new Button (wairToNow);
                        unvalue.setSingleLine ();
                        unvalue.setEms (10);
                        unvalue.setInputType (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                        wairToNow.SetTextSize (unvalue);
                        wairToNow.SetTextSize (rgbuttn);
                        wairToNow.SetTextSize (prbuttn);
                        rgbuttn.setText ("REGISTER");
                        prbuttn.setText ("PW RESET");
                        unrow.addView (unvalue);
                        unrow.addView (rgbuttn);
                        unrow.addView (prbuttn);
                        pwrow.addView (pwvalue);
                        pwrow.addView (pwvisib);
                        emrow.addView (emvalue);
                        rgbuttn.setOnClickListener (new OnClickListener () {
                            @Override
                            public void onClick (View view)
                            {
                                rgbuttonClicked (unvalue, pwvalue, emvalue);
                            }
                        });
                        prbuttn.setOnClickListener (new OnClickListener () {
                            @Override
                            public void onClick (View view)
                            {
                                prbuttonClicked (unvalue.getText ().toString ().trim ());
                            }
                        });
                    } else {
                        // modify password or email address
                        TextView unvalue = new TextView (wairToNow);
                        Button   clbuttn = new Button (wairToNow);
                        Button   prbuttn = new Button (wairToNow);
                        Button   pwbuttn = new Button (wairToNow);
                        Button   embuttn = new Button (wairToNow);
                        wairToNow.SetTextSize (unvalue);
                        wairToNow.SetTextSize (clbuttn);
                        wairToNow.SetTextSize (prbuttn);
                        wairToNow.SetTextSize (pwbuttn);
                        wairToNow.SetTextSize (embuttn);
                        unvalue.setText (unold);
                        pwvalue.setText (pwold);
                        emvalue.setText (emold);
                        clbuttn.setText ("CLEAR");
                        prbuttn.setText ("PW RESET");
                        pwbuttn.setText ("SAVE");
                        embuttn.setText ("SAVE");
                        unrow.addView (unvalue);
                        unrow.addView (clbuttn);
                        unrow.addView (prbuttn);
                        pwrow.addView (pwvalue);
                        pwrow.addView (pwbuttn);
                        pwrow.addView (pwvisib);
                        emrow.addView (emvalue);
                        emrow.addView (embuttn);
                        clbuttn.setOnClickListener (new OnClickListener () {
                            @Override
                            public void onClick (View view)
                            {
                                clbuttonClicked ();
                            }
                        });
                        prbuttn.setOnClickListener (new OnClickListener () {
                            @Override
                            public void onClick (View view)
                            {
                                prbuttonClicked (null);
                            }
                        });
                        pwbuttn.setOnClickListener (new OnClickListener () {
                            @Override
                            public void onClick (View view) {
                                pwbuttonClicked (pwvalue);
                            }
                        });
                        embuttn.setOnClickListener (new OnClickListener () {
                            @Override
                            public void onClick (View view) {
                                embuttonClicked (emvalue);
                            }
                        });
                    }
                }
            } finally {
                result.close ();
            }
        }
    }

    /**
     * REGISTER contribution user button clicked.
     */
    private void rgbuttonClicked (EditText unvalue, EditText pwvalue, EditText emvalue)
    {
        String username  = unvalue.getText ().toString ().trim ();
        String password  = pwvalue.getText ().toString ().trim ();
        String emailaddr = emvalue.getText ().toString ().trim ();
        if (username.equals ("") || password.equals ("") || emailaddr.equals ("")) {
            alertMessage ("Fill in username, password and email address");
        } else {
            RegisterContributionUser rcu = new RegisterContributionUser (wairToNow) {
                @Override
                public void reprompt ()
                { }
                @Override
                public void success ()
                {
                    buildManGeoRefTable ();
                }
            };
            rcu.username  = username;
            rcu.password  = password;
            rcu.emailaddr = emailaddr;
            rcu.start ();
        }
    }

    /**
     * CLEAR contribution user button clicked.
     * Clear the database registration.
     */
    private void clbuttonClicked ()
    {
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("Clear Contribution Registration");
        adb.setMessage ("This will clear your registration.  You can re-register with a " +
                "different username or re-register with the same username and password.");
        adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                SQLiteDBs sqldb = SQLiteDBs.open ("manualiapgeorefs.db");
                if (sqldb != null) {
                    RegisterContributionUser.createRegisterTable (sqldb, "", "", "");
                    buildManGeoRefTable ();
                }
            }
        });
        adb.setNegativeButton ("Cancel", null);
        adb.show ();
    }

    /**
     * PW RESET contribution password button clicked.
     * Tell server to send an email with a link to reset the password.
     */
    private void prbuttonClicked (final String unentry)
    {
        new Thread () {
            @Override
            public void run ()
            {
                String username = unentry;
                if (username == null) {
                    SQLiteDBs sqldb = SQLiteDBs.open ("manualiapgeorefs.db");
                    if (sqldb != null) {
                        Cursor result = sqldb.query ("register", new String[] { "mr_username" }, null, null, null, null, null, null);
                        try {
                            if (result.moveToFirst ()) {
                                username = result.getString (0);
                            }
                        } finally {
                            result.close ();
                        }
                    }
                }
                if (username != null) {
                    try {
                        oneLineHttpReplyOK ("/manualgeoref.php?func=pwreset&username=" + URLEncoder.encode (username));
                        alertMessage ("password reset email sent - check email (including spam folder) then click link therein");
                    } catch (Exception e) {
                        alertMessage ("error resetting password: " + e.getMessage ());
                    }
                }
                SQLiteDBs.CloseAll ();
            }
        }.start ();
    }

    /**
     * SAVE contribution password button clicked.
     * Send it to server.  If server says OK, save it to database.
     */
    private void pwbuttonClicked (EditText pwvalue)
    {
        final String pwnew = pwvalue.getText ().toString ().trim ();
        pwvalue.setText (pwnew);
        new Thread () {
            @Override
            public void run ()
            {
                SQLiteDBs sqldb = SQLiteDBs.open ("manualiapgeorefs.db");
                if (sqldb != null) {
                    Cursor result = sqldb.query ("register", new String[] { "mr_username", "mr_password" }, null, null, null, null, null, null);
                    try {
                        if (result.moveToFirst ()) {
                            String unold = result.getString (0);
                            String pwold = result.getString (1);
                            try {
                                oneLineHttpReplyOK ("/manualgeoref.php?func=modpass" +
                                        "&username=" + URLEncoder.encode (unold) +
                                        "&oldpw=" + URLEncoder.encode (pwold) +
                                        "&newpw=" + URLEncoder.encode (pwnew));
                                ContentValues values = new ContentValues (1);
                                values.put ("mr_password", pwnew);
                                sqldb.update ("register", values, null, null);
                                alertMessage ("password updated");
                            } catch (Exception e) {
                                Log.w (TAG, "error updating contribution password", e);
                                alertMessage ("error updating password: " + e.getMessage ());
                            }
                        }
                    } finally {
                        result.close ();
                    }
                }
                SQLiteDBs.CloseAll ();
            }
        }.start ();
    }

    /**
     * SAVE contribution email button clicked.
     * Send it to server.  If server says OK, save it to database.
     */
    private void embuttonClicked (EditText emvalue)
    {
        final String emnew = emvalue.getText ().toString ().trim ();
        emvalue.setText (emnew);
        new Thread () {
            @Override
            public void run ()
            {
                SQLiteDBs sqldb = SQLiteDBs.open ("manualiapgeorefs.db");
                if (sqldb != null) {
                    Cursor result = sqldb.query ("register", new String[] { "mr_username", "mr_password" }, null, null, null, null, null, null);
                    try {
                        if (result.moveToFirst ()) {
                            String unold = result.getString (0);
                            String pwold = result.getString (1);
                            try {
                                oneLineHttpReplyOK ("/manualgeoref.php?func=modemail" +
                                        "&username="  + URLEncoder.encode (unold) +
                                        "&password="  + URLEncoder.encode (pwold) +
                                        "&emailaddr=" + URLEncoder.encode (emnew));
                                ContentValues values = new ContentValues (1);
                                values.put ("mr_emailaddr", emnew);
                                sqldb.update ("register", values, null, null);
                                alertMessage ("email address updated - check email (including spam folder) for confirmation");
                            } catch (Exception e) {
                                Log.w (TAG, "error updating contribution emailaddr", e);
                                alertMessage ("error updating email address: " + e.getMessage ());
                            }
                        }
                    } finally {
                        result.close ();
                    }
                }
                SQLiteDBs.CloseAll ();
            }
        }.start ();
    }

    /**
     * Send the given URL string to the server.
     * The server should send a one-line reply of 'OK'
     */
    private static void oneLineHttpReplyOK (String urlstr)
            throws Exception
    {
        URL url = new URL (MaintView.dldir + urlstr);
        HttpURLConnection httpcon = (HttpURLConnection) url.openConnection ();
        try {
            int rc = httpcon.getResponseCode ();
            if (rc != HttpURLConnection.HTTP_OK) throw new IOException ("http reply code " + rc);
            BufferedReader br = new BufferedReader (new InputStreamReader (httpcon.getInputStream ()));
            String line = br.readLine ();
            if (line == null) throw new IOException ("server reply empty");
            line = line.trim ();
            if (line.equals ("")) throw new IOException ("server reply empty");
            if (! line.equals ("OK")) throw new IOException (line);
        } finally {
            httpcon.disconnect ();
        }
    }

    /**
     * Display the given message in an alert dialog.
     * Can be called from any thread.
     */
    private void alertMessage (final String msg)
    {
        wairToNow.runOnUiThread (new Runnable () {
            @Override
            public void run ()
            {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setMessage (msg);
                adb.setPositiveButton ("OK", null);
                adb.show ();
            }
        });
    }

    /**
     * Present a checkbox selection of an option.
     */
    public class CheckOption extends LinearLayout implements CompoundButton.OnCheckedChangeListener {
        public CheckBox checkBox;
        private boolean nowrite;

        public CheckOption (String name, boolean def)
        {
            super (wairToNow);
            setOrientation (HORIZONTAL);
            checkBox = new CheckBox (wairToNow);
            checkBox.setChecked (def);
            checkBox.setOnCheckedChangeListener (this);
            addView (checkBox);
            TextView tv1 = new TextView (wairToNow);
            tv1.setText (name);
            wairToNow.SetTextSize (tv1);
            addView (tv1);
        }

        public void setCheckedNoWrite (boolean checked)
        {
            nowrite = true;
            try {
                checkBox.setChecked (checked);
            } finally {
                nowrite = false;
            }
        }

        @Override
        public void onCheckedChanged (CompoundButton buttonView, boolean isChecked)
        {
            if (!nowrite) WriteOptionsCsvFile ();
        }
    }

    public class TypeBOption extends CheckOption {
        public TypeBOption ()
        {
            super ("Type B Compliant", false);
        }

        @Override
        public void onCheckedChanged (CompoundButton buttonView, boolean isChecked)
        {
            super.onCheckedChanged (buttonView, isChecked);
            wairToNow.UpdateTabVisibilities ();
        }
    }

    /**
     * Database enable checkbox that has an associated warning box.
     */
    public class DbEnOption extends CheckOption {
        public Warning warning;

        public DbEnOption (String name, String warn)
        {
            super (name, false);
            warning = new Warning (warn);
        }

        @Override
        public void onCheckedChanged (CompoundButton buttonView, boolean isChecked)
        {
            super.onCheckedChanged (buttonView, isChecked);
            warning.setVisibility (isChecked ? VISIBLE : GONE);
        }

        private class Warning extends WebView {
            public Warning (String html)
            {
                super (wairToNow);

                WebSettings settings = getSettings ();
                //settings.setBuiltInZoomControls (true);
                //settings.setDomStorageEnabled (true);
                //settings.setJavaScriptEnabled (true);
                settings.setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
                settings.setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
                //settings.setSupportZoom (true);
                //addJavascriptInterface (new PlanView.JavaScriptObject (), "pvjso");

                loadUrl (html);
            }
        }
    }

    /**
     * Present a spinner selection of a default and alternate value.
     */
    public class DefAltOption extends RadioGroup implements RadioGroup.OnCheckedChangeListener {
        private boolean nowrite;
        private int selection;

        @SuppressLint("ResourceType")
        public DefAltOption (String def, String alt)
        {
            super (wairToNow);

            RadioButton rbdef = new RadioButton (wairToNow);
            rbdef.setText (def);
            wairToNow.SetTextSize (rbdef);
            rbdef.setId (0);
            addView (rbdef);

            RadioButton rbalt = new RadioButton (wairToNow);
            rbalt.setText (alt);
            wairToNow.SetTextSize (rbalt);
            rbalt.setId (1);
            addView (rbalt);

            setOnCheckedChangeListener (this);
        }

        public boolean getAlt ()
        {
            return selection != 0;
        }

        public void setAltNoWrite (boolean isalt)
        {
            nowrite = true;
            try {
                selection = isalt ? 1 : 0;
                //noinspection ResourceType
                check (selection);
            } finally {
                nowrite = false;
            }
        }

        @Override
        public void onCheckedChanged (RadioGroup group, int checkedId)
        {
            selection = checkedId;
            if (!nowrite) WriteOptionsCsvFile ();
        }
    }

    /**
     * Present a spinner selection of font sizes.
     */
    public class FontOption extends IntOption {
        private String oldvalue;

        public FontOption ()
        {
            super ("Font Size (restarts app)",
                    getFontSizeKeys (),
                    getFontSizeInts ());
            setKeyNoWrite (fontSizeDefault);
        }

        @Override  // IntOption
        public boolean onItemSelected (View view, int index)
        {
            super.onItemSelected (view, index);

            // if changed, restart the app
            if (! getKey ().equals (oldvalue)) {
                // https://stackoverflow.com/questions/6609414/how-do-i-programmatically-restart-an-android-app
                Intent startActivity = new Intent (wairToNow, wairToNow.getClass ());
                int pendingIntentId = 123456;
                PendingIntent pendingIntent = PendingIntent.getActivity (wairToNow, pendingIntentId, startActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) wairToNow.getSystemService (Context.ALARM_SERVICE);
                if (mgr != null) mgr.set (AlarmManager.RTC, System.currentTimeMillis () + 333, pendingIntent);
                System.exit (0);
            }
            return true;
        }

        // set strings in selection box to the size that font would be set to
        // eg, 'Tiny' is displayed in tiny font, 'Huge' is displayed in huge font
        // also save the current selection as oldvalue
        @Override  // TextArraySpinner
        public void adjustRadioGroup (RadioGroup radiogroup)
        {
            oldvalue = null;
            for (int i = radiogroup.getChildCount (); -- i >= 0;) {
                View child = radiogroup.getChildAt (i);
                if (child instanceof RadioButton) {
                    RadioButton rb = (RadioButton) child;
                    String text = rb.getText ().toString ();
                    Integer size = fontSizeMap.get (text);
                    if (size != null) {
                        rb.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.dotsPerInch / size);
                        if (rb.isChecked ()) oldvalue = text;
                    }
                }
            }
        }
    }

    private static String[] getFontSizeKeys ()
    {
        return fontSizeMap.keySet ().toArray (new String[0]);
    }

    private static int[] getFontSizeInts ()
    {
        String[] fontSizeNames = getFontSizeKeys ();
        int[] fontSizeIntegers = new int[fontSizeNames.length];
        int i = 0;
        for (String fontSizeName : fontSizeNames) fontSizeIntegers[i++] = fontSizeMap.nnget (fontSizeName);
        return fontSizeIntegers;
    }

    /**
     * Present a spinner selection of several integer values.
     */
    public class IntOption extends TextArraySpinner implements TextArraySpinner.OnItemSelectedListener {
        private boolean nowrite;
        private int selection;
        private int[] vals;
        private String[] keys;

        public IntOption (String title, String[] kkeys, int[] vvals)
        {
            super (wairToNow);
            keys = kkeys;
            vals = vvals;

            wairToNow.SetTextSize (this);

            super.setLabels (keys, null, null, null);
            super.setOnItemSelectedListener (this);
            super.setTitle (title);
        }

        public int getVal ()
        {
            return vals[selection];
        }

        public String getKey ()
        {
            return keys[selection];
        }

        public void setKeyNoWrite (String key)
        {
            nowrite = true;
            try {
                for (int i = 0; i < keys.length; i ++) {
                    if (key.equals (keys[i])) {
                        selection = i;
                        super.setIndex (i);
                        return;
                    }
                }
                throw new RuntimeException ("unknown key " + key);
            } finally {
                nowrite = false;
            }
        }

        @Override  // TextArraySpinner.OnItemSelectedListener
        public boolean onItemSelected (View view, int index)
        {
            selection = index;
            if (!nowrite) WriteOptionsCsvFile ();
            return true;
        }

        @Override  // TextArraySpinner.OnItemSelectedListener
        public boolean onNegativeClicked (View view)
        {
            return false;
        }

        @Override  // TextArraySpinner.OnItemSelectedListener
        public boolean onPositiveClicked (View view)
        {
            return false;
        }
    }

    /**
     * @brief Present a slider to select a floatingpoint value.
     */
    /*public class SliderOption extends LinearLayout implements SeekBar.OnSeekBarChangeListener {
        private static final int SMAX = 1024;

        private double minlg;
        private double maxlg;
        private SeekBar slider;

        public SliderOption (Context ctx, String minlbl, double minval, double defval, double maxval, String maxlbl)
        {
            super (ctx);

            minlg = Math.log (minval);
            maxlg = Math.log (maxval);

            TextView minvaltxt = new TextView (ctx);
            minvaltxt.setText (minlbl + "\n" + minval);
            LayoutParams minvtlp = new LayoutParams (LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0.0f);
            minvaltxt.setLayoutParams (minvtlp);

            slider = new SeekBar (ctx);
            slider.setMax (SMAX);
            slider.setOnSeekBarChangeListener (this);
            LayoutParams sliderlp = new LayoutParams (LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1.0f);
            slider.setLayoutParams (sliderlp);

            TextView maxvaltxt = new TextView (ctx);
            maxvaltxt.setText (maxlbl + "\n" + maxval);
            LayoutParams maxvtlp = new LayoutParams (LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0.0f);
            maxvaltxt.setLayoutParams (maxvtlp);

            setOrientation (HORIZONTAL);
            addView (minvaltxt);
            addView (slider);
            addView (maxvaltxt);

            setValue (defval);
        }

        public double getValue ()
        {
            double ratio = (double) slider.getProgress () / (double) SMAX;
            double vallg = (maxlg - minlg) * ratio + minlg;
            return (double) Math.exp (vallg);
        }

        public void setValue (double val)
        {
            double vallg = Math.log (val);
            double ratio = (vallg - minlg) / (maxlg - minlg);
            if (ratio < 0.0) ratio = 0.0;
            if (ratio > 1.0) ratio = 1.0;
            slider.setProgress ((int) (ratio * SMAX));
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
        {
            int thisprog = slider.getProgress ();
            if (this == tacScaleOption) {
                if (wacScaleOption.slider.getProgress () < thisprog) {
                    wacScaleOption.slider.setProgress (thisprog);
                }
            }
            if (this == wacScaleOption) {
                if (tacScaleOption.slider.getProgress () > thisprog) {
                    tacScaleOption.slider.setProgress (thisprog);
                }
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar)
        {
            WriteOptionsCsvFile ();
        }
    }*/
}
