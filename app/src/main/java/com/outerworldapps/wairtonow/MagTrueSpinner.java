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
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

/**
 * Spinner widget to select Magnetic or True.
 */
public class MagTrueSpinner extends Spinner implements View.OnClickListener {
    private final static String[] items = new String[] { "Mag", "True" };

    private AlertDialog alert;
    private boolean isMag;

    /**
     * Constructor
     */
    public MagTrueSpinner (Context ctx)
    {
        super (ctx);

        ArrayAdapter<String> adapter = new ArrayAdapter<> (ctx, android.R.layout.simple_spinner_item, items);
        setAdapter (adapter);

        setMag (true);
    }

    /**
     * Get currently selected value
     * @return true: magnetic
     *        false: true
     */
    public boolean getMag ()
    {
        return isMag;
    }

    /**
     * Set selected value
     * @param val = true: magnetic
     *             false: true
     */
    public void setMag (boolean val)
    {
        isMag = val;
        setSelection (val ? 0 : 1);
    }

    /**
     * The given popup is really ugly so supply our own.
     */
    @Override  // Spinner
    public boolean performClick ()
    {
        Context ctx = getContext ();

        if (alert != null) alert.dismiss ();

        RadioButton rbmag = new RadioButton (ctx);
        rbmag.setText (items[0]);
        rbmag.setChecked (isMag);
        rbmag.setTag (true);
        rbmag.setOnClickListener (this);

        RadioButton rbtru = new RadioButton (ctx);
        rbtru.setText (items[1]);
        rbtru.setChecked (!isMag);
        rbtru.setTag (false);
        rbtru.setOnClickListener (this);

        RadioGroup rgroup = new RadioGroup (ctx);
        rgroup.addView (rbmag);
        rgroup.addView (rbtru);

        AlertDialog.Builder adb = new AlertDialog.Builder (getContext ());
        adb.setView (rgroup);
        alert = adb.show ();

        return true;
    }

    @Override  // OnClickListener
    public void onClick (View v)
    {
        setMag ((Boolean) v.getTag ());
        alert.dismiss ();
        alert = null;
    }
}
