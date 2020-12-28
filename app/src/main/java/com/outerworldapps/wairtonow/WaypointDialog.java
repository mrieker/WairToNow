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
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Display a dialog to select a waypoint optionally with an RNAV-style offset.
 */
public abstract class WaypointDialog implements Comparator<Waypoint> {
    private WairToNow wtn;
    private String title;
    private Waypoint.Airport nearapt;
    private String oldid;
    private DBase dbase;
    private String ermsg;

    public abstract void wpSeld (Waypoint wp);
    public abstract int compare (Waypoint a, Waypoint b);

    public WaypointDialog (
            WairToNow wtn,
            String title,
            Waypoint.Airport nearapt,
            String oldid,
            DBase dbase)
    {
        this.wtn = wtn;
        this.title = title;
        this.nearapt = nearapt;
        this.oldid = oldid;
        this.dbase = dbase;
    }

    /**
     * Display a text dialog for user to enter a waypoint ident.
     */
    @SuppressLint("SetTextI18n")
    public void show ()
    {
        // set up a text box to enter the new identifier in
        final EditText ident = new EditText (wtn);
        ident.setEms (10);
        ident.setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        ident.setSingleLine ();

        // set up a text box to enter a RNAV offset radial
        final EditText rnavradl = new EditText (wtn);
        rnavradl.setEms (3);
        rnavradl.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rnavradl.setSingleLine ();
        rnavradl.addTextChangedListener (new TextWatcher () {
            private String before;

            @Override
            public void beforeTextChanged (CharSequence charSequence, int i, int i1, int i2)
            {
                before = charSequence.toString ();
            }

            @Override
            public void onTextChanged (CharSequence charSequence, int i, int i1, int i2)
            { }

            @Override
            public void afterTextChanged (Editable editable)
            {
                String after = editable.toString ().trim ();
                if (!after.equals ("")) {
                    try {
                        double value = Double.parseDouble (after);
                        if ((value >= 0.0) && (value <= 360.0)) return;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                    editable.replace (0, after.length (), before);
                }
            }
        });

        // set up a spinner to say mag/true for RNAV offset radial
        final MagTrueSpinner magTrue = new MagTrueSpinner (wtn);

        // set up a text box to enter RNAV offset distance
        final EditText rnavdist = new EditText (wtn);
        rnavdist.setEms (3);
        rnavdist.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rnavdist.setSingleLine ();
        rnavdist.addTextChangedListener (new TextWatcher () {
            private String before;

            @Override
            public void beforeTextChanged (CharSequence charSequence, int i, int i1, int i2)
            {
                before = charSequence.toString ();
            }

            @Override
            public void onTextChanged (CharSequence charSequence, int i, int i1, int i2)
            { }

            @Override
            public void afterTextChanged (Editable editable)
            {
                String after = editable.toString ().trim ();
                if (!after.equals ("")) {
                    try {
                        double value = Double.parseDouble (after);
                        if ((value >= 0.0) && (value <= 99999.0)) return;
                    } catch (NumberFormatException nfe) {
                        Lib.Ignored ();
                    }
                    editable.replace (0, after.length (), before);
                }
            }
        });

        // fill in initial values for those boxes from given oldid string
        boolean hasRNavOffset = false;
        try {
            Waypoint.RNavParse rnavParse = new Waypoint.RNavParse (oldid);
            ident.setText (rnavParse.baseident);
            if (rnavParse.rnavsuffix != null) {
                rnavradl.setText (Lib.DoubleNTZ (rnavParse.rnavradial));
                magTrue.setMag (rnavParse.magnetic);
                rnavdist.setText (Lib.DoubleNTZ (rnavParse.rnavdistance));
                hasRNavOffset = true;
            }
        } catch (Exception e) {
            ident.setText (oldid);
        }

        // put those boxes in a linear layout
        final LinearLayout llh = new LinearLayout (wtn);
        llh.setOrientation (LinearLayout.HORIZONTAL);
        llh.addView (ident);
        if (hasRNavOffset) {

            // already has RNAV offset, display the RNAV offset boxes too
            addRNavOffsetBoxes (llh, rnavradl, magTrue, rnavdist);
        } else {

            // no RNAV offset, give them a button to show the boxes if they want
            Button rnavOffsetButton = new Button (wtn);
            rnavOffsetButton.setText ("RNAV>>");
            rnavOffsetButton.setOnClickListener (new View.OnClickListener () {
                @Override
                public void onClick (View view)
                {
                    llh.removeAllViews ();
                    llh.addView (ident);
                    addRNavOffsetBoxes (llh, rnavradl, magTrue, rnavdist);
                }
            });
            llh.addView (rnavOffsetButton);
        }

        // maybe display error message below input boxes
        LinearLayout ll = llh;
        if (ermsg != null) {
            TextView ertxt = new TextView (wtn);
            wtn.SetTextSize (ertxt);
            ertxt.setText (ermsg);

            LinearLayout llv = new LinearLayout (wtn);
            llv.setOrientation (LinearLayout.VERTICAL);
            llv.addView (llh);
            llv.addView (ertxt);
            ll = llv;
        }

        // wrap that all in an horizontal scroll in case of small screen
        DetentHorizontalScrollView sv = new DetentHorizontalScrollView (wtn);
        wtn.SetDetentSize (sv);
        sv.addView (ll);

        // set up and display an alert dialog box to enter new identifier
        AlertDialog.Builder adb = new AlertDialog.Builder (wtn);
        adb.setTitle (title);
        adb.setView (sv);
        adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                waypointEntered (ident, rnavradl, magTrue, rnavdist);
            }
        });
        adb.setNeutralButton ("OFF", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                ident.setText ("");
                waypointEntered (ident, rnavradl, magTrue, rnavdist);
            }
        });
        adb.setNegativeButton ("Cancel", null);
        final AlertDialog ad = adb.show ();

        // set up listener for the DONE keyboard key
        ident.setOnEditorActionListener (new TextView.OnEditorActionListener () {
            @Override
            public boolean onEditorAction (TextView textView, int i, KeyEvent keyEvent)
            {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    Lib.dismiss (ad);
                    waypointEntered (ident, rnavradl, magTrue, rnavdist);
                }
                return false;
            }
        });
    }

    private static void addRNavOffsetBoxes (LinearLayout ll, EditText rnavradl, MagTrueSpinner magTrue, EditText rnavdist)
    {
        Context ctx = ll.getContext ();
        ll.addView (new TextViewString (ctx, "["));
        ll.addView (rnavradl);
        ll.addView (new TextViewString (ctx, "\u00B0"));
        ll.addView (magTrue);
        ll.addView (new TextViewString (ctx, "@"));
        ll.addView (rnavdist);
        TextView nm = new TextViewString (ctx, "nm");
        nm.setSingleLine ();
        ll.addView (nm);
    }

    private void waypointEntered (
            EditText ident,
            EditText rnavradl,
            MagTrueSpinner magTrue,
            EditText rnavdist)
    {
        // if ident entered is blank, tell caller that user wants no ident selected
        String newid = ident.getText ().toString ().replace (" ", "").toUpperCase (Locale.US);
        if (newid.equals ("")) {
            wpSeld (null);
        } else {

            // something in ident box, look it up in waypoint database
            final LinkedList<Waypoint> wplist = Waypoint.GetWaypointsByIdent (newid, wtn, dbase);

            // get RNAV offset info
            String rnavradlstr = rnavradl.getText ().toString ().trim ();
            final boolean magnetic = magTrue.getMag ();
            String rnavdiststr = rnavdist.getText ().toString ().trim ();

            // if a RNAV distance given, wrap the waypoint with RNAV radial/distance info
            final double rnavradlbin;
            final double rnavdistbin;
            final String rnavsuffix;
            if (!rnavradlstr.equals ("") || !rnavdiststr.equals ("")) {
                try {
                    rnavradlbin = Double.parseDouble (rnavradlstr);
                    rnavdistbin = Double.parseDouble (rnavdiststr);
                    if (rnavradlbin <  0.0) throw new NumberFormatException ("radial lt 0");
                    if (rnavradlbin >  360) throw new NumberFormatException ("radial gt 360");
                    if (rnavdistbin <= 0.0) throw new NumberFormatException ("distance le 0");
                    rnavsuffix = Lib.DoubleNTZ (rnavradlbin) + (magnetic ? "@" : "T@") +
                            Lib.DoubleNTZ (rnavdistbin);
                } catch (NumberFormatException nfe) {
                    oldid = newid + "[" + rnavradlstr + (magnetic ? "@" : "T@") + rnavdiststr;
                    ermsg = "Bad RNAV radial and/or distance\n" +
                                    "Radial is number 0 to 360\n" +
                                    "Distance is number gt 0";
                    show ();
                    return;
                }
            } else {
                rnavradlbin = Double.NaN;
                rnavdistbin = Double.NaN;
                rnavsuffix  = null;
            }

            switch (wplist.size ()) {
                case 0: {
                    if ((nearapt != null) && newid.startsWith ("RW")) {
                        Waypoint wp = nearapt.GetRunways ().get (newid.substring (2));
                        if (wp != null) {
                            if (rnavsuffix != null) {
                                wp = new Waypoint.RNavOffset (wp, rnavdistbin, rnavradlbin, magnetic, rnavsuffix);
                            }
                            wpSeld (wp);
                            break;
                        }
                    }
                    oldid = newid;
                    if (rnavsuffix != null) {
                        oldid = newid + "[" + rnavsuffix;
                    }
                    ermsg = "Waypoint not found\n" +
                                    "Airport ICAO ID (eg KBOS)\n" +
                                    "VOR or NDB ID (eg BOS)\n" +
                                    "Loc/ILS ID (eg IBOS)\n" +
                                    "Fix ID (eg BOSOX)\n" +
                                    "Runway numbers (eg KBOS.04R)\n" +
                                    "Synthetic ILS/DME (eg IBOS.04R)";
                    show ();
                    break;
                }

                case 1: {
                    Waypoint wp = wplist.getFirst ();
                    if (rnavsuffix != null) {
                        wp = new Waypoint.RNavOffset (wp, rnavdistbin, rnavradlbin, magnetic, rnavsuffix);
                    }
                    wpSeld (wp);
                    break;
                }

                default: {
                    final Waypoint[] wparray = wplist.toArray (Waypoint.nullarray);
                    Arrays.sort (wparray, this);
                    CharSequence[] names = new CharSequence[wparray.length];
                    int i = 0;
                    for (Waypoint wp : wparray) {
                        names[i++] = wp.MenuKey ();
                    }
                    AlertDialog.Builder adb = new AlertDialog.Builder (wtn);
                    adb.setTitle ("Select Waypoint " + newid);
                    adb.setItems (names, new DialogInterface.OnClickListener () {
                        @Override
                        public void onClick (DialogInterface dialog, int which)
                        {
                            Waypoint wp = wparray[which];
                            if (rnavsuffix != null) {
                                wp = new Waypoint.RNavOffset (wp, rnavdistbin, rnavradlbin, magnetic, rnavsuffix);
                            }
                            wpSeld (wp);
                        }
                    });
                    adb.setNegativeButton ("Cancel", null);
                    adb.show ();
                    break;
                }
            }
        }
    }
}
