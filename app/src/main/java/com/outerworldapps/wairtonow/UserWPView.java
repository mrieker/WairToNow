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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * This view presents a screen that lets the user manage user-defined waypoints.
 */
public class UserWPView extends LinearLayout
        implements WairToNow.CanBeMainView {
    private final static String csvName = WairToNow.dbdir + "/userwaypts.csv";

    private DeleteButton deleteButton;
    private DescrEditText descrEditText;
    private WairToNow wairToNow;
    private IdentTextView identTextView;
    private LatLonView latView;
    private LatLonView lonView;
    private LinearLayout waypointListView;
    private SaveButton saveButton;
    private TreeMap<String,UserWP> waypoints;

    public UserWPView (WairToNow ctx)
            throws IOException
    {
        super (ctx);
        wairToNow = ctx;

        setOrientation (VERTICAL);

        /*
         * Read existing user defined waypoints from database.
         */
        waypoints = new TreeMap<> ();
        try {
            BufferedReader csvReader = new BufferedReader (new FileReader (csvName), 1024);
            try {
                String csvLine;
                while ((csvLine = csvReader.readLine ()) != null) {
                    UserWP userWP = new UserWP (csvLine);
                    waypoints.put (userWP.ident, userWP);
                }
            } finally {
                try { csvReader.close (); } catch (IOException ioe) { Lib.Ignored (); }
            }
        } catch (FileNotFoundException fnfe) {
            Lib.Ignored ();
        }

        /*
         * Start with window title.
         */
        TextView tv1 = new TextView (ctx);
        tv1.setText ("User Waypoints");
        wairToNow.SetTextSize (tv1);
        addView (tv1);

        /*
         * This scroll box is used to list the user waypoints.
         */
        waypointListView = new LinearLayout (ctx);
        waypointListView.setOrientation (VERTICAL);

        ScrollView sv1 = new ScrollView (ctx);
        sv1.addView (waypointListView);
        sv1.setLayoutParams (new LinearLayout.LayoutParams (ViewGroup.LayoutParams.FILL_PARENT,
                                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                                            1));
        addView (sv1);

        for (UserWP wp : waypoints.values ()) {
            waypointListView.addView (wp);
        }

        /*
         * These are boxes the user can enter the waypoint data in.
         */
        LinearLayout ll0 = new LinearLayout (ctx);
        ll0.setOrientation (HORIZONTAL);
        identTextView = new IdentTextView (ctx);
        identTextView.setLayoutParams (new LinearLayout.LayoutParams (ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                      ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                      1));
        ll0.addView (identTextView);
        ll0.addView (new CapGPSButton ());
        ll0.addView (new CapCtrButton ());
        ll0.addView (new ClearButton (ctx));
        addView (ll0);

        latView = new LatLonView (ctx, false);
        lonView = new LatLonView (ctx, true);

        latView.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize);
        lonView.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize);

        addView (latView);
        addView (lonView);

        descrEditText = new DescrEditText (ctx);
        addView (descrEditText);

        LinearLayout ll2 = new LinearLayout (ctx);
        ll2.setOrientation (HORIZONTAL);

        /*
         * This button can be clicked to save waypoint to database.
         */
        saveButton = new SaveButton (ctx);
        ll2.addView (saveButton);

        /*
         * This button can be clicked to delete waypoint from database.
         */
        deleteButton = new DeleteButton (ctx);
        ll2.addView (deleteButton);

        /*
         * This button can be clicked to set the waypoint as destination.
         */
        DestinationButton destinationButton = new DestinationButton (ctx);
        ll2.addView (destinationButton);

        /*
         * This button can be clicked to set the waypoint as center location in screen.
         */
        LocationButton locationButton = new LocationButton (ctx);
        ll2.addView (locationButton);

        HorizontalScrollView hs2 = new HorizontalScrollView (ctx);
        hs2.addView (ll2);
        addView (hs2);
    }

    /**
     * WairToNow.CanBeMainView implementation
     */
    @Override
    public String GetTabName ()
    {
        return "UserWP";
    }
    @Override
    public void CloseDisplay ()
    { }
    @Override
    public void ReClicked ()
    { }
    @Override
    public void OpenDisplay ()
    {
        wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_USER);

        // in case lat/lon format option was changed
        String format = wairToNow.optionsView.LatLonString (10.0F/3600.0F, 'N', 'S');
        latView.setFormat (format);
        lonView.setFormat (format);
        for (UserWP wp : waypoints.values ()) {
            wp.Reformat ();
        }
    }
    @Override
    public View GetBackPage ()
    {
        return wairToNow.chartView;
    }

    /**
     * Get list of user waypoints
     */
    public Collection<UserWP> GetUserWPs ()
    {
        return waypoints.values ();
    }

    /**
     * Set the given waypoint as being currently selected for editing.
     * @param wp = waypoint to edit
     */
    private void UserWPSelected (UserWP wp)
    {
        if (wp == null) {
            identTextView.setVal ("");
            latView.clear ();
            lonView.clear ();
            descrEditText.setText ("");
        } else {
            identTextView.setVal (wp.ident);
            latView.setVal (wp.lat);
            lonView.setVal (wp.lon);
            descrEditText.setText (wp.descr);
        }
    }

    /**
     * Get whatever user waypoint is selected by whatever is in the ident box.
     * @return null if nothing matches, else returns the user waypoint.
     */
    private UserWP getSelectedUserWP ()
    {
        return waypoints.get (identTextView.getText ().toString ());
    }

    /**
     * Try to write current waypoint list to .csv datafile.
     */
    private void WriteCSVFile ()
    {
        try {
            BufferedWriter csvWriter = new BufferedWriter (new FileWriter (csvName + ".tmp"), 1024);
            try {
                for (UserWP wp : waypoints.values ()) {
                    csvWriter.write (wp.getCSV () + '\n');
                }
            } finally {
                csvWriter.close ();
            }
            Lib.RenameFile (csvName + ".tmp", csvName);
        } catch (IOException ioe) {
            Log.e ("UserWPView", "error writing " + csvName, ioe);
            AlertDialog.Builder builder = new AlertDialog.Builder (wairToNow);
            builder.setTitle ("Error writing user waypoint database");
            builder.setMessage (ioe.getMessage ());
            builder.setNegativeButton ("Cancel", new DialogInterface.OnClickListener () {
                public void onClick (DialogInterface dialog, int which)
                {
                    dialog.dismiss ();
                }
            });
            AlertDialog dialog = builder.create ();
            dialog.show ();
        }
    }

    /**
     * Text box used to enter the identifier name in.
     */
    private class IdentTextView extends EditText implements TextWatcher {
        private String oldText = "";

        public IdentTextView (Context ctx)
        {
            super (ctx);
            setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            setSingleLine (true);
            wairToNow.SetTextSize (this);
            addTextChangedListener (this);
        }

        public void setVal (String s)
        {
            setText (s);
            UpdateHighlighting (s);
        }

        /**
         * Allow only upper-case letters and numbers for identifiers.
         * Also, if any existing waypoint matches the ident, highlight it.
         */
        @Override  // TextWatcher
        public void afterTextChanged (Editable s)
        {
            /*
             * Allow only upper-case letters and numbers.
             * Convert lower-case letters to upper-case letters.
             * Strip everything else out.
             */
            String stri = s.toString ();
            int len = stri.length ();
            char[] chro = new char[len];
            int j = 0;
            for (int i = 0; i < len; i ++) {
                char ch = stri.charAt (i);
                if ((ch >= 'a') && (ch <= 'z')) ch += 'A' - 'a';
                if ((ch >= 'A') && (ch <= 'Z')) chro[j++] = ch;
                if ((ch >= '0') && (ch <= '9')) chro[j++] = ch;
            }
            String stro = new String (chro, 0, j);

            /*
             * Unhighlight old waypoint button and highlight new waypoint button.
             */
            UpdateHighlighting (stro);

            /*
             * If resultant string is different than what's in the box,
             * replace box's contents with result.
             */
            if (!stro.equals (stri)) {
                s.replace (0, s.length (), stro, 0, j);
            }
        }

        @Override  // TextWatcher
        public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override  // TextWatcher
        public void onTextChanged (CharSequence s, int start, int before, int count) { }

        /**
         * Unhighlight old waypoint button if any and highlight new waypoint button if any.
         */
        private void UpdateHighlighting (String newText)
        {
            UserWP oldwp = waypoints.get (oldText);
            UserWP newwp = waypoints.get (newText);
            if (newwp != oldwp) {
                if (oldwp != null) oldwp.setTextColor (Color.BLACK);
                if (newwp != null) {
                    newwp.setTextColor (Color.RED);
                    waypointListView.requestChildFocus (newwp, newwp);
                    saveButton.setTextColor (Color.RED);
                    deleteButton.setTextColor (Color.RED);
                    deleteButton.setEnabled (true);
                } else {
                    saveButton.setTextColor (Color.BLACK);
                    deleteButton.setTextColor (Color.BLACK);
                    deleteButton.setEnabled (false);
                }
            }
            oldText = newText;
        }
    }

    /**
     * Capture the current location into the lat/lon boxes.
     */
    private class CapGPSButton extends Button implements OnClickListener {
        public CapGPSButton ()
        {
            super (wairToNow);
            setText ("GPS");
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
        }

        @Override
        public void onClick (View v)
        {
            latView.setVal (wairToNow.currentGPSLat);
            lonView.setVal (wairToNow.currentGPSLon);
        }
    }
    private class CapCtrButton extends Button implements OnClickListener {
        public CapCtrButton ()
        {
            super (wairToNow);
            setText ("Center");
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
        }

        @Override
        public void onClick (View v)
        {
            latView.setVal (wairToNow.chartView.centerLat);
            lonView.setVal (wairToNow.chartView.centerLon);
        }
    }

    /**
     * Clear all the edit boxes to make it easy to enter new data.
     */
    private class ClearButton extends Button implements OnClickListener {
        public ClearButton (Context ctx)
        {
            super (ctx);
            setText ("Clear");
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
        }

        @Override
        public void onClick (View v)
        {
            identTextView.setVal ("");
            latView.clear ();
            lonView.clear ();
            descrEditText.setText ("");
        }
    }

    /**
     * Text box used to enter the description in.
     */
    private class DescrEditText extends EditText implements TextWatcher {
        public DescrEditText (Context ctx)
        {
            super (ctx);
            setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            setSingleLine (true);
            wairToNow.SetTextSize (this);
            addTextChangedListener (this);
        }

        /**
         * Disallow control characters and multiple spaces.
         */
        @Override  // TextWatcher
        public void afterTextChanged (Editable s)
        {
            /*
             * Disallow control characters and multiple spaces.
             */
            String stri = s.toString ();
            int len = stri.length ();
            char[] chro = new char[len];
            int j = 0;
            for (int i = 0; i < len; i ++) {
                char ch = stri.charAt (i);
                if (ch < ' ') continue;
                if ((ch == ' ') && (j == 0)) continue;
                if ((ch == ' ') && (chro[j-1] == ' ')) continue;
                chro[j++] = ch;
            }
            String stro = new String (chro, 0, j);

            /*
             * If resultant string is different than what's in the box,
             * replace box's contents with result.
             */
            if (!stro.equals (stri)) {
                s.replace (0, s.length (), stro, 0, j);
            }
        }

        @Override  // TextWatcher
        public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override  // TextWatcher
        public void onTextChanged (CharSequence s, int start, int before, int count) { }
    }

    /**
     * Save current data to database.
     */
    private class SaveButton extends Button implements OnClickListener {
        public SaveButton (Context ctx)
        {
            super (ctx);
            setText ("Save");
            wairToNow.SetTextSize (this);
            setEnabled (true);
            setOnClickListener (this);
        }

        public void onClick (View v)
        {
            /*
             * Get values from editing boxes.
             */
            String ident = identTextView.getText ().toString ();
            float  lat   = latView.getVal ();
            float  lon   = lonView.getVal ();
            String descr = descrEditText.getText ().toString ();

            /*
             * Can't save a null ident string.
             */
            if (ident.equals ("")) return;

            /*
             * If new waypoint, create struct to store it in and set up values.
             * Otherwise, just modify values.
             */
            UserWP wp = getSelectedUserWP ();
            if (wp == null) {
                identTextView.setVal ("");
                wp = new UserWP (ident, lat, lon, descr);
                waypoints.put (ident, wp);
                int index = 0;
                for (String id : waypoints.keySet ()) {
                    if (id.equals (wp.ident)) break;
                    index ++;
                }
                waypointListView.addView (wp, index);
                UserWPSelected (wp);
            } else {
                wp.setVal (lat, lon, descr);
            }

            /*
             * Try to write .csv file with new/modified waypoint.
             */
            WriteCSVFile ();
        }
    }

    /**
     * Delete current waypoint from database.
     * Leave values in edit window after delete in case they deleted by mistake.
     */
    private class DeleteButton extends Button implements OnClickListener {

        public DeleteButton (Context ctx)
        {
            super (ctx);
            setText ("Delete");
            wairToNow.SetTextSize (this);
            setEnabled (false);
            setOnClickListener (this);
        }

        public void onClick (View v)
        {
            UserWP wp = getSelectedUserWP ();
            if (wp == null) return;

            // no longer have a current waypoint
            // this disables the delete button
            // and turns the save button black
            UserWPSelected (null);

            // remove entry from internal list and from displayed list
            waypoints.remove (wp.ident);
            waypointListView.removeView (wp);

            // set edit values to the original values
            // in case they clicked delete by mistake
            UserWPSelected (wp);

            // write updated .csv file
            WriteCSVFile ();
        }
    }

    /**
     * Button used to set the waypoint's location as the course line destination.
     */
    private class DestinationButton extends Button implements OnClickListener {
        public DestinationButton (Context ctx)
        {
            super (ctx);
            setText ("GoTo");
            wairToNow.SetTextSize (this);
            setEnabled (true);
            setOnClickListener (this);
        }

        /**
         * Called when the destination button is clicked.
         * Sets the course line on the display to go from current
         * position to wherever the waypoint is.
         */
        @Override
        public void onClick (View v)
        {
            wairToNow.SetDestinationWaypoint (latView.getVal (), lonView.getVal (), identTextView.getText ().toString ());
        }
    }

    /**
     * Button used to set the waypoint's location as the course line destination.
     */
    private class LocationButton extends Button implements OnClickListener {
        public LocationButton (Context ctx)
        {
            super (ctx);
            setText ("Locate");
            wairToNow.SetTextSize (this);
            setEnabled (true);
            setOnClickListener (this);
        }

        /**
         * Called when the location button is clicked.
         * Sets the waypoint in the middle of the screen.
         */
        @Override
        public void onClick (View v)
        {
            wairToNow.chartView.SetCenterLatLon (latView.getVal (), lonView.getVal ());
            wairToNow.SetCurrentTab (wairToNow.chartView);
        }
    }

    /**
     * Each individual user waypoint in the database.
     * Click on it to select it.
     */
    public class UserWP extends Button implements OnClickListener {
        public float lat;
        public float lon;
        public String ident;
        public String descr;

        public UserWP (String csvLine)
        {
            super (wairToNow);

            String[] tokens = Lib.QuotedCSVSplit (csvLine);
            ident = tokens[0];
            lat   = Float.parseFloat (tokens[1]);
            lon   = Float.parseFloat (tokens[2]);
            descr = tokens[3];

            wairToNow.SetTextSize (this);
            setOnClickListener (this);
            Reformat ();
        }

        public UserWP (String ident, float lat, float lon, String descr)
        {
            super (wairToNow);
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
            this.ident = ident;
            setVal (lat, lon, descr);
        }

        public void setVal (float lat, float lon, String descr)
        {
            this.lat   = lat;
            this.lon   = lon;
            this.descr = descr;
            Reformat ();
        }

        /**
         * Format button text according to user-selected options.
         */
        public void Reformat ()
        {
            String text = ident + " " + wairToNow.optionsView.LatLonString (lat, 'N', 'S') + " " +
                                        wairToNow.optionsView.LatLonString (lon, 'E', 'W') + "\n" + descr;
            setText (text);
        }

        /**
         * Return line for .csv file.
         */
        public String getCSV ()
        {
            String des = descr.trim ().replace ("\\", "\\\\").replace ("\"", "\\");
            return ident + ',' + lat + ',' + lon + ",\"" + des + '"';
        }

        /**
         * Clicking on the entry selects it by loading it in the edit boxes.
         */
        @Override
        public void onClick (View button)
        {
            UserWPSelected (this);
        }
    }
}
