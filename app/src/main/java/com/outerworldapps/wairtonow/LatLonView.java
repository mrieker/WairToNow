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

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Editable lat/lon box.
 */
public class LatLonView extends LinearLayout {
    private Context ctx;
    private float   textSizeSize;
    private int     textSizeUnit;
    private int     max;
    private Num     degtv;
    private Num     mintv;
    private Num     sectv;
    private Neg     negtv;
    private String  format;
    private String  posst;
    private String  negst;

    public LatLonView (Context ctx, boolean lon)
    {
        super (ctx);
        this.ctx   = ctx;
        this.posst = lon ? "East" : "North";
        this.negst = lon ? "West" : "South";
        this.max   = lon ?  180   :   90;
        setOrientation (HORIZONTAL);
        format = "";
        textSizeSize = 10;
        textSizeUnit = TypedValue.COMPLEX_UNIT_PX;
        Format ();
    }

    /**
     * Set format of the boxes used
     * @param fmt = contains ": dd^mm'ss.ss"
     *              contains ': dd^mm.mmmm'
     *                    else: dd.dddddd^
     */
    public void setFormat (String fmt)
    {
        format = fmt;
        Reformat ();
    }

    /**
     * Set text size used for boxes.
     */
    public void setTextSize (int unit, float size)
    {
        textSizeUnit = unit;
        textSizeSize = size;
        Reformat ();
    }

    /**
     * Clear strings out of all boxes.
     */
    public void clear ()
    {
        degtv.Clear ();
        if (mintv != null) mintv.Clear ();
        negtv.Clear ();
        if (sectv != null) sectv.Clear ();
    }

    /**
     * Get the lat/lon value in degrees.
     */
    public float getVal ()
    {
        float deg = degtv.getVal ();
        if (mintv == null) {
            deg /= 1000000;
        } else if (sectv == null) {
            deg += (float)mintv.getVal () / 600000;
        } else {
            deg += (float)mintv.getVal () / 60;
            deg += (float)sectv.getVal () / 360000;
        }
        if (negtv.getVal ()) deg = -deg;
        return deg;
    }

    /**
     * Set the lat/lon value to given number of degrees.
     */
    public void setVal (float val)
    {
        boolean neg = (val < 0);
        if (neg) val = -val;
        negtv.setVal (neg);
        if (mintv == null) {
            degtv.setVal (Math.round (val * 1000000));
        } else if (sectv == null) {
            val *= 600000;
            val += 0.5;
            int min = (int)val;
            int deg = min / 600000;
            min %= 600000;

            degtv.setVal (deg);
            mintv.setVal (min);
        } else {
            val *= 360000;
            val += 0.5;
            int sec = (int)val;
            int deg = sec / 360000;
            sec %= 360000;
            int min = sec / 6000;
            sec %= 6000;

            degtv.setVal (deg);
            mintv.setVal (min);
            sectv.setVal (sec);
        }
    }

    /**
     * Set up the dd/mm/ss boxes according to the current format and text size.
     */
    private void Reformat ()
    {
        float val = getVal ();  // save current numeric value
        removeAllViews ();      // clear out any boxes that exist
        Format ();              // set up new boxes
        setVal (val);           // set value in new boxes
    }

    private void Format ()
    {
        if (format.contains ("\"")) {
            // up to 3 digits for the degrees
            degtv = new Num (0, max, 0);
            degtv.setEms (3);
            addView (degtv);

            // a degree symbol
            TextView degsymtv = new TextView (ctx);
            degsymtv.setText (new char[] { (char)0xB0 }, 0, 1);
            degsymtv.setTextSize (textSizeUnit, textSizeSize);
            addView (degsymtv);

            // up to two digits for the minutes
            mintv = new Num (0, 59, 0);
            mintv.setEms (2);
            addView (mintv);

            // a minutes symbol
            TextView minsymtv = new TextView (ctx);
            minsymtv.setText ("'");
            minsymtv.setTextSize (textSizeUnit, textSizeSize);
            addView (minsymtv);

            // up to two.two digits for the seconds
            sectv = new Num (0, 59, 2);
            sectv.setEms (4);
            addView (sectv);

            // a seconds symbol
            TextView secsymtv = new TextView (ctx);
            secsymtv.setText ("\"");
            secsymtv.setTextSize (textSizeUnit, textSizeSize);
            addView (secsymtv);
        } else if (format.contains ("\'")) {
            // up to 3 digits for the degrees
            degtv = new Num (0, max, 0);
            degtv.setEms (3);
            addView (degtv);

            // a degree symbol
            TextView degsymtv = new TextView (ctx);
            degsymtv.setText (new char[] { (char)0xB0 }, 0, 1);
            degsymtv.setTextSize (textSizeUnit, textSizeSize);
            addView (degsymtv);

            // up to two.four digits for the minutes
            mintv = new Num (0, 59, 4);
            mintv.setEms (5);
            addView (mintv);

            // a minutes symbol
            TextView minsymtv = new TextView (ctx);
            minsymtv.setText ("'");
            minsymtv.setTextSize (textSizeUnit, textSizeSize);
            addView (minsymtv);

            // no seconds box
            sectv = null;
        } else {
            // up to 3.6 digits for the degrees
            degtv = new Num (0, max, 6);
            degtv.setEms (10);
            addView (degtv);

            // a degree symbol
            TextView degsymtv = new TextView (ctx);
            degsymtv.setText (new char[] { (char)0xB0 }, 0, 1);
            degsymtv.setTextSize (textSizeUnit, textSizeSize);
            addView (degsymtv);

            // no minutes or seconds boxes
            mintv = null;
            sectv = null;
        }

        // a single character for the direction
        negtv = new Neg ();
        addView (negtv);
    }

    /**
     * Box for editing numeric deg/min/sec values
     */
    private class Num extends EditText implements TextWatcher {
        private boolean replacing;
        private int dec, min, max;
        private String oldText = "";

        public Num (int min, int max, int dec)
        {
            super (ctx);
            this.min = min;
            this.max = max;
            this.dec = dec;
            setInputType (InputType.TYPE_CLASS_NUMBER | ((dec > 0) ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
            setSingleLine (true);
            setTextSize (textSizeUnit, textSizeSize);
            addTextChangedListener (this);
        }

        public void Clear ()
        {
            setText ("");
            oldText = "";
        }

        /**
         * Get value in box * 10 ** dec
         */
        public int getVal ()
        {
            if (oldText.equals ("")) {
                setText ("0");
                return 0;
            }
            String istr = oldText;
            String dstr = "";
            int i = oldText.indexOf ('.');
            if (i >= 0) {
                istr = oldText.substring (0, i);
                dstr = oldText.substring (++ i);
            }
            int ival = Integer.parseInt (istr + dstr);
            for (i = dec - dstr.length (); -- i >= 0;) ival *= 10;
            return ival;
        }

        /**
         * Set value in box * 10 ** dec
         */
        public void setVal (int val)
        {
            oldText = Lib.ValToString (val, dec);
            setText (oldText);
        }

        /**
         * Make sure they can only input digits and stay in range.
         */
        @Override  // TextWatcher
        public void afterTextChanged (Editable s)
        {
            String str = s.toString ();
            if (str.equals ("")) return;
            try {
                int i = str.indexOf ('.');
                if ((dec > 0) && (i >= 0)) {
                    int ival = Integer.parseInt (str.substring (0, i));
                    if ((ival >= min) && (ival <= max)) {
                        String dstr = str.substring (++ i);
                        if (dstr.length () <= dec) {
                            if (dstr.length () > 0) Lib.Ignored (Integer.parseInt (dstr));
                            String tlz = Integer.toString (ival) + "." + dstr;
                            if (tlz.equals (str)) {
                                oldText = str;
                                return;
                            }
                            oldText = tlz;
                        }
                    }
                } else {
                    int val = Integer.parseInt (str);
                    if ((val >= min) && (val <= max)) {
                        String tlz = Integer.toString (val);
                        if (tlz.equals (str)) {
                            oldText = str;
                            return;
                        }
                        oldText = tlz;
                    }
                }
            } catch (NumberFormatException nfe) {
                Lib.Ignored ();
            }
            if (!replacing) {
                replacing = true;
                s.replace (0, s.length (), oldText, 0, oldText.length ());
                replacing = false;
            }
        }

        @Override  // TextWatcher
        public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override  // TextWatcher
        public void onTextChanged (CharSequence s, int start, int before, int count) { }
    }

    /**
     * Box for editing neg/pos indicator value
     */
    private class Neg extends Spinner implements OnClickListener {
        private AlertDialog alert;
        private boolean isNeg, isPos;

        /**
         * Constructor
         */
        public Neg ()
        {
            super (ctx);

            String[] items = new String[] { "", posst, negst };
            ArrayAdapter<String> adapter = new ArrayAdapter<> (ctx, android.R.layout.simple_spinner_item, items);
            setAdapter (adapter);
        }

        /**
         * Clear the state to the unselected value.
         */
        public void Clear ()
        {
            isNeg = false;
            isPos = false;
            setSelection (0);
            if (alert != null) {
                alert.dismiss ();
                alert = null;
            }
        }

        /**
         * Get currently selected value
         * @return true: negative ("South" or "West")
         *        false: positive ("North" or "East")
         */
        public boolean getVal ()
        {
            TextView selected = (TextView)getSelectedView ();
            return (selected != null) && selected.getText ().toString ().equals (negst);
        }

        /**
         * Set selected value
         * @param val = true: negative ("South" or "West")
         *             false: positive ("North" or "East")
         */
        public void setVal (boolean val)
        {
            isNeg =  val;
            isPos = !val;
            setSelection (val ? 2 : 1);
        }

        /**
         * The given popup is really ugly so supply our own.
         */
        @Override  // Spinner
        public boolean performClick ()
        {
            Context ctx = getContext ();

            if (alert != null) alert.dismiss ();

            RadioButton rbpos = new RadioButton (ctx);
            rbpos.setText (posst);
            rbpos.setTextSize (textSizeUnit, textSizeSize);
            rbpos.setChecked (isPos);
            rbpos.setTag (false);
            rbpos.setOnClickListener (this);

            RadioButton rbneg = new RadioButton (ctx);
            rbneg.setText (negst);
            rbneg.setTextSize (textSizeUnit, textSizeSize);
            rbneg.setChecked (isNeg);
            rbneg.setTag (true);
            rbneg.setOnClickListener (this);

            RadioGroup rgroup = new RadioGroup (ctx);
            rgroup.addView (rbpos);
            rgroup.addView (rbneg);

            AlertDialog.Builder adb = new AlertDialog.Builder (getContext ());
            adb.setView (rgroup);
            alert = adb.show ();

            return true;
        }

        @Override  // OnClickListener
        public void onClick (View v)
        {
            setVal ((Boolean) v.getTag ());
            alert.dismiss ();
            alert = null;
        }
    }
}
