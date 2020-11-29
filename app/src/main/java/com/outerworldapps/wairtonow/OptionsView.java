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
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * Display a menu to download database and charts.
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
    public  CheckOption  faaWPOption;
    public  CheckOption  gpsCompassOption;
    public  CheckOption  invPlaColOption;
    public  CheckOption  powerLockOption;
    public  CheckOption  showNexrad;
    public  CheckOption  showTraffic;
    public  CheckOption  showWxSumDot;
    public  CheckOption  synthILSDMEOption;
    public  CheckOption  tfrPDOverOption;
    public  CheckOption  userWPOption;
    public  CheckOption  typeBOption;
    public  DefAltOption ktsMphOption;
    public  DefAltOption magTrueOption;
    public  FontOption   fontSizeOption;
    public  IntOption    chartTrackOption;
    public  IntOption    circCatOption;
    public  IntOption    gpsUpdateOption;
    public  IntOption    latLonOption;
    public  IntOption    tfrFilterOption;
    private WairToNow    wairToNow;

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

        TextView tv1 = new TextView (ctx);
        tv1.setText ("Options");
        wairToNow.SetTextSize (tv1);
        addView (tv1);

        capGridOption     = new CheckOption  ("Show CAP grids",              false);
        collDetOption     = new CheckOption  ("Show obstacle/terrain collision", false);
        faaWPOption       = new CheckOption  ("Show FAA waypoints",          false);
        userWPOption      = new CheckOption  ("Show User waypoints",         true);
        invPlaColOption   = new CheckOption  ("Invert plate colors",         false);
        synthILSDMEOption = new CheckOption  ("Show Synth ILS/DME Plates",   false);
        typeBOption       = new TypeBOption  ();
        powerLockOption   = new CheckOption  ("Power Lock",                  false);
        gpsCompassOption  = new CheckOption  ("GPS status compass",          false);
        showNexrad        = new CheckOption  ("Show ADS-B Nexrad (2D only)", false);
        showTraffic       = new CheckOption  ("Show ADS-B Traffic",          false);
        showWxSumDot      = new CheckOption  ("Show Wx Summary Dots",        false);
        magTrueOption     = new DefAltOption ("Magnetic", "True");
        ktsMphOption      = new DefAltOption ("Kts", "MPH");

        fontSizeOption    = new FontOption ();

        tfrFilterOption = new IntOption ("TFR Filter",
                new String[] { "TFR: ALL", "TFR: 3DAYS", "TFR: TODAY", "TFR: ACTIVE", "TFR: NONE" },
                new int[] { TFR_ALL, TFR_3DAYS, TFR_TODAY, TFR_ACTIVE, TFR_NONE });
        tfrPDOverOption = new CheckOption ("TFR PD Overlay shading", PDOVERLAYWORKS);

        latLonOption      = new IntOption ("LatLon Format",
            new String[] {
                "ddd" + (char)0xB0 + "mm'ss.ss\"",
                "ddd" + (char)0xB0 + "mm.mmmm'",
                "ddd.dddddd" + (char)0xB0 },
            new int[] { LLO_DDMMSS, LLO_DDMMMM, LLO_DDDDDD });

        chartTrackOption = new IntOption ("Chart Up",
            new String[] {
                "Course Up",
                "Finger Rotate",
                "North Up",
                "Track Up" },
            new int[] {
                CTO_COURSEUP,
                CTO_FINGEROT,
                CTO_NORTHUP,
                CTO_TRACKUP });

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

        LinearLayout ll1 = new LinearLayout (ctx);
        ll1.setOrientation (LinearLayout.VERTICAL);

        ll1.addView (fontSizeOption);
        ll1.addView (tfrFilterOption);
        ll1.addView (tfrPDOverOption);
        ll1.addView (capGridOption);
        ll1.addView (collDetOption);
        ll1.addView (faaWPOption);
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
            BufferedReader csvreader = new BufferedReader (new FileReader (WairToNow.dbdir + "/options.csv"), 1024);
            try {
                String csvline;
                while ((csvline = csvreader.readLine ()) != null) {
                    int i = csvline.indexOf (',');
                    String name = csvline.substring (0, i);
                    String valu = csvline.substring (++ i);
                    if (name.equals ("capGrid"))      capGridOption.setCheckedNoWrite     (valu.equals (boolTrue));
                    if (name.equals ("collDet"))      collDetOption.setCheckedNoWrite     (valu.equals (boolTrue));
                    if (name.equals ("faaWPs"))       faaWPOption.setCheckedNoWrite       (valu.equals (boolTrue));
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
                }
            } finally {
                csvreader.close ();
            }
        } catch (FileNotFoundException fnfe) {
            Log.i (TAG, "no options file yet");
            capGridOption.setCheckedNoWrite     (false);
            collDetOption.setCheckedNoWrite     (false);
            faaWPOption.setCheckedNoWrite       (false);
            userWPOption.setCheckedNoWrite      (true);
            invPlaColOption.setCheckedNoWrite   (false);
            synthILSDMEOption.setCheckedNoWrite (false);
            typeBOption.setCheckedNoWrite       (false);
            powerLockOption.setCheckedNoWrite   (true);
            gpsCompassOption.setCheckedNoWrite  (true);
            showNexrad.setCheckedNoWrite        (true);
            showTraffic.setCheckedNoWrite       (true);
            showWxSumDot.setCheckedNoWrite      (true);
            chartTrackOption.setKeyNoWrite      (chartTrackOption.keys[2]);   // north up
            magTrueOption.setAltNoWrite         (false);
            latLonOption.setKeyNoWrite          (latLonOption.keys[0]);
            ktsMphOption.setAltNoWrite          (false);
            gpsUpdateOption.setKeyNoWrite       (gpsUpdateOption.keys[0]);
            circCatOption.setKeyNoWrite         (circCatOption.keys[0]);
            fontSizeOption.setKeyNoWrite        (fontSizeDefault);
            tfrFilterOption.setKeyNoWrite       ("TFR: 3DAYS");
            tfrPDOverOption.setCheckedNoWrite   (PDOVERLAYWORKS);
        } catch (Exception e) {
            Log.w (TAG, "error reading options.csv", e);
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
                csvwriter.write ("faaWPs,"       + faaWPOption.checkBox.isChecked ()       + "\n");
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
    { }

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
