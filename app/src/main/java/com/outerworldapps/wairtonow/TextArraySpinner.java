//    Copyright (C) 2016, Mike Rieker, Beverly, MA USA
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
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/**
 * Our own spinner that selects items from dynamically built text array.
 */
public class TextArraySpinner extends Button implements View.OnClickListener {
    public final static int NEGATIVE = -1;
    public final static int NOTHING  = -2;
    public final static int POSITIVE = -3;

    public interface OnItemSelectedListener {
        boolean onItemSelected (View view, int index);
        boolean onNegativeClicked (View view);
        boolean onPositiveClicked (View view);
    }

    private final static String buttonSuffix = "  \u25BC";  // little down-triangle

    private AlertDialog dialog;
    private int index = NOTHING;
    private String title;
    private String[] unlabels = new String[3];
    private String[] labels = new String[0];
    private OnClickListener userListener;
    private OnItemSelectedListener clicked;

    public TextArraySpinner (Context ctx, AttributeSet attrs)
    {
        super (ctx, attrs);
        super.setOnClickListener (this);
    }

    public TextArraySpinner (Context ctx)
    {
        super (ctx);
        super.setOnClickListener (this);
    }

    /**
     * Set title message for dialog box if desired.
     */
    public void setTitle (String title)
    {
        this.title = title;
    }

    /**
     * Set the labels to be used for selection
     */
    public void setLabels (String[] labels, String negative, String nothing, String positive)
    {
        this.labels = labels;
        this.unlabels[~NEGATIVE] = negative;
        this.unlabels[~NOTHING]  = nothing;
        this.unlabels[~POSITIVE] = positive;
    }

    /**
     * Set up callbacks when an item is clicked on.
     * Gives a callback even if same item is clicked twice in a row.
     */
    public void setOnItemSelectedListener (OnItemSelectedListener listener)
    {
        clicked = listener;
    }

    /**
     * Get index of current selection.
     * @return index in labels[] array (or -1 if nothing selected)
     */
    public int getIndex ()
    {
        return index;
    }

    /**
     * Force spinner to show the given selection on the button.
     */
    public void setIndex (int index)
    {
        this.index = index;
        String text = ((index < 0) ? unlabels[~index] : labels[index]) + buttonSuffix;
        setText (text);
    }

    /**
     * Intercept enabling/disabling.
     */
    @Override
    public void setEnabled (boolean enabled)
    {
        super.setEnabled (enabled);

        // if disabled, make sure dialog box is closed
        if (!enabled) {
            Lib.dismiss (dialog);
            dialog = null;
        }
    }

    /**
     * Intercept setting up a click listener.
     */
    @Override
    public void setOnClickListener (OnClickListener listener)
    {
        userListener = listener;
    }

    /**
     * Internal callback triggered when main button is clicked.
     */
    @Override  // OnClickListener
    public void onClick (View v)
    {
        // maybe user is listening for clicks
        if (userListener != null) {
            userListener.onClick (v);
        }

        // shouldn't think there's a dialog up but clear it off if so
        Lib.dismiss (dialog);
        dialog = null;

        // make a group of radio buttons for the selection
        Context ctx = getContext ();
        RadioGroup radiogroup = new RadioGroup (ctx);

        // make their text size same as the main button
        float textSizeSize = getTextSize ();
        int textSizeUnit = TypedValue.COMPLEX_UNIT_PX;

        // set up listener if one of the normal buttons gets clicked
        OnClickListener itemSelectListener = new OnClickListener () {
            @Override
            public void onClick (View view) {
                selected ((int) view.getTag ());
            }
        };

        // loop through the list of normal button labels
        // create a radio button for each and link to list
        for (int i = 0; i < labels.length; i ++) {
            String label = labels[i];

            RadioButton radiobutton = new RadioButton (ctx);
            radiobutton.setText (label);
            radiobutton.setTextSize (textSizeUnit, textSizeSize);
            radiobutton.setChecked (i == index);
            radiobutton.setTag (i);
            radiobutton.setOnClickListener (itemSelectListener);

            radiogroup.addView (radiobutton);
        }

        // throw up an alert dialog with the radio buttons
        AlertDialog.Builder adb = new AlertDialog.Builder (ctx);
        if (title != null) adb.setTitle (title);
        adb.setView (radiogroup);

        // maybe do positive/negative buttons too
        if (unlabels[~POSITIVE] != null) {
            adb.setPositiveButton (unlabels[~POSITIVE], new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    positived ();
                }
            });
        }

        if (unlabels[~NEGATIVE] != null) {
            adb.setNegativeButton (unlabels[~NEGATIVE], new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    negatived ();
                }
            });
        }

        // display the dialog
        dialog = adb.show ();
    }

    // one of the normal selection buttons was clicked
    private void selected (int i)
    {
        Lib.dismiss (dialog);
        dialog = null;
        if ((clicked == null) || clicked.onItemSelected (this, i)) {
            setIndex (i);
        }
    }

    // the positive button was clicked
    private void positived ()
    {
        if ((clicked == null) || clicked.onPositiveClicked (this)) {
            setIndex (POSITIVE);
        }
    }

    // the negative button was clicked
    private void negatived ()
    {
        if ((clicked == null) || clicked.onNegativeClicked (this)) {
            setIndex (NEGATIVE);
        }
    }
}
