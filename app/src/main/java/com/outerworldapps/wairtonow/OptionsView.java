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
import android.content.pm.ActivityInfo;
import android.util.Log;
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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Display a menu to download database and charts.
 */
@SuppressLint("ViewConstructor")
public class OptionsView
        extends LinearLayout
        implements WairToNow.CanBeMainView {
    public final static String TAG = "WairToNow";

    public  CheckOption  capGridOption;
    public  CheckOption  faaWPOption;
    public  CheckOption  gpsCompassOption;
    public  CheckOption  powerLockOption;
    public  CheckOption  showNexrad;
    public  CheckOption  showTraffic;
    public  CheckOption  userWPOption;
    public  CheckOption  typeBOption;
    public  DefAltOption ktsMphOption;
    public  DefAltOption magTrueOption;
    public  DefAltOption listMapOption;
    public  IntOption    chartTrackOption;
    public  IntOption    chartOrientOption;
    public  IntOption    gpsUpdateOption;
    public  IntOption    latLonOption;
    private WairToNow    wairToNow;

    private final static int LLO_DDMMMM = 0;
    private final static int LLO_DDMMSS = 1;
    private final static int LLO_DDDDDD = 2;

    public final static int CTO_COURSEUP = 0;
    public final static int CTO_NORTHUP  = 1;
    public final static int CTO_TRACKUP  = 2;
    public final static int CTO_FINGEROT = 3;

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
        faaWPOption       = new CheckOption  ("Show FAA waypoints",          false);
        userWPOption      = new CheckOption  ("Show User waypoints",         true);
        typeBOption       = new TypeBOption  ();
        powerLockOption   = new CheckOption  ("Power Lock",                  false);
        gpsCompassOption  = new CheckOption  ("GPS status compass",          false);
        showNexrad        = new CheckOption  ("Show ADS-B Nexrad (2D only)", false);
        showTraffic       = new CheckOption  ("Show ADS-B Traffic",          false);
        magTrueOption     = new DefAltOption ("Magnetic", "True");
        listMapOption     = new DefAltOption ("Maint Plates List", "Maint Plates Map");
        ktsMphOption      = new DefAltOption ("Kts", "MPH");

        latLonOption      = new IntOption (
            new String[] {
                "ddd" + (char)0xB0 + "mm'ss.ss\"",
                "ddd" + (char)0xB0 + "mm.mmmm'",
                "ddd.dddddd" + (char)0xB0 },
            new int[] { LLO_DDMMSS, LLO_DDMMMM, LLO_DDDDDD });

        chartOrientOption = new IntOption (
            new String[] {
                "Chart orientation Unlocked",
                "Chart locked in Portrait",
                "Chart locked in Landscape" },
            new int[] {
                ActivityInfo.SCREEN_ORIENTATION_USER,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE });

        chartTrackOption = new IntOption (
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

        gpsUpdateOption = new IntOption (
                new String[] {
                        "GPS updates 10 per sec",
                        "GPS updates 3 per sec",
                        "GPS updates 1 per sec"
                },
                new int[] { 10, 3, 1 }
        );

        LinearLayout ll1 = new LinearLayout (ctx);
        ll1.setOrientation (LinearLayout.VERTICAL);

        ll1.addView (capGridOption);
        ll1.addView (faaWPOption);
        ll1.addView (userWPOption);
        ll1.addView (typeBOption);
        ll1.addView (showNexrad);
        ll1.addView (showTraffic);
        ll1.addView (powerLockOption);
        ll1.addView (gpsCompassOption);
        ll1.addView (chartOrientOption);
        ll1.addView (chartTrackOption);
        ll1.addView (magTrueOption);
        ll1.addView (listMapOption);
        ll1.addView (latLonOption);
        ll1.addView (ktsMphOption);
        ll1.addView (gpsUpdateOption);

        ScrollView sv1 = new ScrollView (ctx);
        sv1.addView (ll1);
        addView (sv1);

        ReadOptionsCsvFile ();
    }

    /**
     * Format a heading into a string according to user-selected options.
     * @param hdg = true heading (degrees)
     * @param lat = latitude (degrees) where the heading is wanted
     * @param lon = longitude (degrees) where the heading is wanted
     * @param alt = altitude (metres MSL) where the heading is wanted
     * @return string of heading
     */
    public String HdgString (float hdg, float lat, float lon, float alt)
    {
        String suffix = (char)0xB0 + " True";
        if (!magTrueOption.getAlt ()) {
            hdg += Lib.MagVariation (lat, lon, alt);
            suffix = (char)0xB0 + " Mag";
        }
        int ihdg = (int)(hdg + 1439.5F) % 360 + 1;
        return Integer.toString (ihdg + 1000).substring (1) + suffix;
    }

    /**
     * Convert a lat/lon numeric value to a string according to user-selected options.
     * @param val = numeric value (degrees)
     * @param posch = char to display if positive (N or E)
     * @param negch = char to display if negative (S or W)
     * @return resultant string
     */
    public String LatLonString (float val, char posch, char negch)
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

                str = Integer.toString (deg) + (char)0xB0 + Integer.toString (min) + '\'';

                if (sec00 > 0) {
                    String sec00st = Integer.toString (sec00);
                    int sec00ln;
                    while ((sec00ln = sec00st.length ()) < 3) sec00st = "0" + sec00st;

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

                String min0000st = Integer.toString (min0000);
                int min0000ln;
                while ((min0000ln = min0000st.length ()) < 5) min0000st = "0" + min0000st;

                str = Integer.toString (deg) + (char)0xB0 + min0000st.substring (0, min0000ln - 4) + '.' + min0000st.substring (min0000ln - 4);
                while (str.endsWith ("0")) str = str.substring (0, str.length () - 1);
                if (str.endsWith (".")) str = str.substring (0, str.length () - 1);
                str += '\'';
                break;
            }

            // ddd.dddddd^
            case LLO_DDDDDD: {
                int ival = Math.round (val * 1000000);
                int dpts = 6;
                while ((dpts > 1) && (ival % 10 == 0)) {
                    ival /= 10;
                    dpts --;
                }
                str = Integer.toString (ival);
                int len = str.length ();
                while (len <= dpts) {
                    str = '0' + str;
                    len ++;
                }
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
                    if (name.equals ("capGrid"))     capGridOption.checkBox.setChecked    (valu.equals (boolTrue));
                    if (name.equals ("faaWPs"))      faaWPOption.checkBox.setChecked      (valu.equals (boolTrue));
                    if (name.equals ("userWPs"))     userWPOption.checkBox.setChecked     (valu.equals (boolTrue));
                    if (name.equals ("typeB"))       typeBOption.checkBox.setChecked      (valu.equals (boolTrue));
                    if (name.equals ("powerLock"))   powerLockOption.checkBox.setChecked  (valu.equals (boolTrue));
                    if (name.equals ("gpsCompass"))  gpsCompassOption.checkBox.setChecked (valu.equals (boolTrue));
                    if (name.equals ("showNexrad"))  showNexrad.checkBox.setChecked       (valu.equals (boolTrue));
                    if (name.equals ("showTraffic")) showTraffic.checkBox.setChecked      (valu.equals (boolTrue));
                    if (name.equals ("chartOrient")) chartOrientOption.setKey (valu);
                    if (name.equals ("chartTrack"))  chartTrackOption.setKey  (valu);
                    if (name.equals ("magtrueAlt"))  magTrueOption.setAlt     (valu.equals (boolTrue));
                    if (name.equals ("plateMap"))    listMapOption.setAlt     (valu.equals (boolTrue));
                    if (name.equals ("latlonAlt"))   latLonOption.setKey      (valu);
                    if (name.equals ("ktsMphAlt"))   ktsMphOption.setAlt      (valu.equals (boolTrue));
                    if (name.equals ("gpsUpdate"))   gpsUpdateOption.setKey   (valu);
                }
            } finally {
                csvreader.close ();
            }
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
                csvwriter.write ("capGrid,"     + Boolean.toString (capGridOption.checkBox.isChecked ())    + "\n");
                csvwriter.write ("faaWPs,"      + Boolean.toString (faaWPOption.checkBox.isChecked ())      + "\n");
                csvwriter.write ("userWPs,"     + Boolean.toString (userWPOption.checkBox.isChecked ())     + "\n");
                csvwriter.write ("typeB,"       + Boolean.toString (typeBOption.checkBox.isChecked ())      + "\n");
                csvwriter.write ("powerLock,"   + Boolean.toString (powerLockOption.checkBox.isChecked ())  + "\n");
                csvwriter.write ("gpsCompass,"  + Boolean.toString (gpsCompassOption.checkBox.isChecked ()) + "\n");
                csvwriter.write ("showNexrad,"  + Boolean.toString (showNexrad.checkBox.isChecked ())       + "\n");
                csvwriter.write ("showTraffic," + Boolean.toString (showTraffic.checkBox.isChecked ())      + "\n");
                csvwriter.write ("chartOrient," + chartOrientOption.getKey ()                               + "\n");
                csvwriter.write ("chartTrack,"  + chartTrackOption.getKey ()                                + "\n");
                csvwriter.write ("magtrueAlt,"  + Boolean.toString (magTrueOption.getAlt ())                + "\n");
                csvwriter.write ("plateMap,"    + Boolean.toString (listMapOption.getAlt ())                + "\n");
                csvwriter.write ("latlonAlt,"   + latLonOption.getKey ()                                    + "\n");
                csvwriter.write ("ktsMphAlt,"   + Boolean.toString (ktsMphOption.getAlt ())                 + "\n");
                csvwriter.write ("gpsUpdate,"   + gpsUpdateOption.getKey ()                                 + "\n");
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
    public int GetOrientation ()
    {
        return ActivityInfo.SCREEN_ORIENTATION_USER;
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

        @Override
        public void onCheckedChanged (CompoundButton buttonView, boolean isChecked)
        {
            WriteOptionsCsvFile ();
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
        private int selection;

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
            //noinspection ResourceType
            rbalt.setId (1);
            addView (rbalt);

            setOnCheckedChangeListener (this);
        }

        public boolean getAlt ()
        {
            return selection != 0;
        }

        public void setAlt (boolean isalt)
        {
            selection = isalt ? 1 : 0;
            //noinspection ResourceType
            check (selection);
        }

        @Override
        public void onCheckedChanged (RadioGroup group, int checkedId)
        {
            selection = checkedId;
            WriteOptionsCsvFile ();
        }
    }

    /**
     * Present a spinner selection of several integer values.
     */
    public class IntOption extends RadioGroup implements RadioGroup.OnCheckedChangeListener {
        private int selection;
        private int[] vals;
        private String[] keys;

        public IntOption (String[] kkeys, int[] vvals)
        {
            super (wairToNow);
            keys = kkeys;
            vals = vvals;
            for (int i = 0; i < kkeys.length; i ++) {
                RadioButton rb = new RadioButton (wairToNow);
                rb.setText (kkeys[i]);
                wairToNow.SetTextSize (rb);
                rb.setId (i);
                addView (rb);
            }
            setOnCheckedChangeListener (this);
        }

        public int getVal ()
        {
            return vals[selection];
        }

        public String getKey ()
        {
            return keys[selection];
        }

        public void setKey (String key)
        {
            for (int i = 0; i < keys.length; i ++) {
                if (key.equals (keys[i])) {
                    selection = i;
                    check (i);
                    return;
                }
            }
            throw new RuntimeException ("unknown key " + key);
        }

        @Override
        public void onCheckedChanged (RadioGroup group, int checkedId)
        {
            selection = checkedId;
            WriteOptionsCsvFile ();
        }
    }

    /**
     * @brief Present a slider to select a floatingpoint value.
     */
    /*public class SliderOption extends LinearLayout implements SeekBar.OnSeekBarChangeListener {
        private static final int SMAX = 1024;

        private float minlg;
        private float maxlg;
        private SeekBar slider;

        public SliderOption (Context ctx, String minlbl, float minval, float defval, float maxval, String maxlbl)
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

        public float getValue ()
        {
            float ratio = (float) slider.getProgress () / (float) SMAX;
            float vallg = (maxlg - minlg) * ratio + minlg;
            return (float) Math.exp (vallg);
        }

        public void setValue (float val)
        {
            float vallg = Math.log (val);
            float ratio = (vallg - minlg) / (maxlg - minlg);
            if (ratio < 0.0F) ratio = 0.0F;
            if (ratio > 1.0F) ratio = 1.0F;
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
