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
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Enter a route (such as a clearance) and analyze it.
 */
@SuppressLint("ViewConstructor")
public class RouteView extends ScrollView implements WairToNow.CanBeMainView {
    private final static String TAG = "WairToNow";
    private final static String datadir = WairToNow.dbdir + "/routes";

    public boolean trackingOn;
    public int pointAhead;
    public Waypoint[] analyzedRouteArray;

    private AlertDialog loadButtonMenu;
    private boolean displayOpen;
    private Button buttonTrack;
    private EditText editTextD;
    private EditText editTextR;
    private EditText editTextA;
    private EditText editTextF;
    private EditText editTextT;
    private EditText editTextN;
    private double startingLat;
    private double startingLon;
    private int goodColor;
    private RouteStep firstStep;
    private RouteStep lastStep;
    private SpannableStringBuilder msgBuilder;
    private String editTextRHint;
    private String loadedFromName;
    private TextView textViewC;
    private WairToNow wairToNow;

    public RouteView (WairToNow ctx)
    {
        super (ctx);
        wairToNow = ctx;

        loadedFromName = "";

        // so the findVewById()s don't return null
        ctx.setContentView (R.layout.routeview);

        editTextD = (EditText) ctx.findViewById (R.id.editTextD);
        editTextR = (EditText) ctx.findViewById (R.id.editTextR);
        editTextA = (EditText) ctx.findViewById (R.id.editTextA);
        editTextF = (EditText) ctx.findViewById (R.id.editTextF);
        editTextT = (EditText) ctx.findViewById (R.id.editTextT);
        editTextN = (EditText) ctx.findViewById (R.id.editTextN);
        textViewC = (TextView) ctx.findViewById (R.id.textViewC);

        editTextD.addTextChangedListener (new TextWatcher () {
            @Override
            public void beforeTextChanged (CharSequence charSequence, int i, int i1, int i2)
            { }

            @Override
            public void onTextChanged (CharSequence charSequence, int i, int i1, int i2)
            { }

            @Override
            public void afterTextChanged (Editable editable)
            {
                ShutTrackingOff ();
            }
        });

        editTextRHint = editTextR.getHint ().toString ();
        editTextR.addTextChangedListener (new TextWatcher () {
            @Override
            public void beforeTextChanged (CharSequence charSequence, int i, int i1, int i2)
            {
                // the text box doesn't collapse to a single line when the hint is
                // removed so we have to remove the hint manually, then fortunately
                // the text box will collapse to a single line.
                editTextR.setHint ("");
            }

            @Override
            public void onTextChanged (CharSequence charSequence, int i, int i1, int i2)
            { }

            @Override
            public void afterTextChanged (Editable editable)
            {
                ShutTrackingOff ();

                // doesn't seem to be a way to say single logical line input with wrapping
                // so we have the text box set to multi-line input.  but we want the enter
                // key to advance on to the altitude box as we don't need newlines in the
                // route box as such.
                String s = editable.toString ();
                if (s.indexOf ('\n') >= 0) {
                    editable.replace (0, editable.length (), s.replace ('\n', ' ').trim ());
                    WairToNow.wtnHandler.runDelayed (100, new Runnable () {
                        @Override
                        public void run ()
                        {
                            editTextA.requestFocus ();
                        }
                    });
                }
            }
        });

        Button buttonCheck = (Button) ctx.findViewById (R.id.buttonCheck);
        Button buttonClear = (Button) ctx.findViewById (R.id.buttonClear);
        Button buttonLoad  = (Button) ctx.findViewById (R.id.buttonLoad);
        Button buttonSave  = (Button) ctx.findViewById (R.id.buttonSave);
        buttonTrack = (Button) ctx.findViewById (R.id.buttonTrack);

        buttonCheck.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                ComputeRoute ();
            }
        });

        buttonClear.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Clear confirmation");
                adb.setMessage ("Confirm clear");
                adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialogInterface, int i)
                    {
                        clearFields ();
                    }
                });
                adb.setNegativeButton ("Cancel", null);
                adb.show ();
            }
        });

        buttonLoad.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view) {
                LoadButtonClicked ();
            }
        });

        buttonSave.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view) {
                SaveButtonClicked ();
            }
        });

        buttonTrack.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view) {
                trackingOn = (analyzedRouteArray != null) & !trackingOn;
                buttonTrack.setText (trackingOn ? "track off" : "track on ");
                if (trackingOn) CalcPointAhead ();
            }
        });

        ShutTrackingOff ();

        View routeView = ctx.findViewById (R.id.routeView);
        ((ViewGroup) (routeView.getParent ())).removeAllViews ();
        addView (routeView);

        clearFields ();
    }

    /**
     * Clear all form fields.
     */
    private void clearFields ()
    {
        editTextD.setText ("");
        editTextR.setText ("");
        editTextA.setText ("");
        editTextF.setText ("");
        editTextT.setText ("");
        editTextN.setText ("");
        textViewC.setText ("");
        editTextR.setHint (editTextRHint);
        analyzedRouteArray = null;
        loadedFromName     = "";
        pointAhead         = 999999999;
        ShutTrackingOff ();
    }

    /**
     * A waypoint of 'Present Position' can be entered as
     * first step of route by entering @PP.
     */
    private class PresPosWaypt extends Waypoint {
        public PresPosWaypt ()
        {
            ident = "@PP";
            lat = startingLat;
            lon = startingLon;
        }
        public String MenuKey ()
        {
            return GetDetailText ();
        }
        public String GetType ()
        {
            return "@PP";
        }
        public String GetDetailText ()
        {
            return "present position";
        }
    }

    /**
     * Steps along the route.
     */
    private abstract class RouteStep {
        public RouteStep nextStep;
        public RouteStep prevStep;
        public String ident;
    }

    /**
     * A step along the route is a named waypoint.
     * We get a list if there is more than one waypoint in database of same name.
     * These are also wedged between two airways giving the set of intersecting
     * waypoints, which are most likely of differing names.
     * We select the specific waypoint when attempting to solve the route by
     * picking the one that gives the shortest total route distance.
     */
    private class RouteStepWaypt extends RouteStep {
        private LinkedList<Waypoint> waypoints;

        public int selectedsolution;

        public RouteStepWaypt (String id, LinkedList<Waypoint> wps)
        {
            ident = id;
            waypoints = wps;
        }
    }

    /**
     * The step along the route is an airway.
     * Airways are list of waypoints with multiple entry and exit points.
     * The entry and exit points are selected by selecting an alternative
     * of the waypoint steps just before and after the airway step.
     */
    private class RouteStepAirwy extends RouteStep {
        private Collection<Waypoint.Airway> airways;

        public RouteStepAirwy (String id, Collection<Waypoint.Airway> awys)
        {
            ident = id;
            airways = awys;
        }

        /**
         * Get list of waypoints along selected route.
         * Route is selected by the adjacent waypoints selectedsolution.
         */
        public Collection<Waypoint> GetSelectedWaypoints ()
        {
            // get selected entry and exit waypoints
            RouteStepWaypt prevrs = (RouteStepWaypt) prevStep;
            RouteStepWaypt nextrs = (RouteStepWaypt) nextStep;
            Waypoint prevwp = prevrs.waypoints.get (prevrs.selectedsolution);
            Waypoint nextwp = nextrs.waypoints.get (nextrs.selectedsolution);

            // scan through all the alternatives for this airway
            // eg, there is a V1 in conus and hawaii
            // but only one of them matches the selected adjacent waypoints
            for (Waypoint.Airway airway : airways) {

                // find indices of entry/exit waypoints along the airway
                int previx = -1;
                int nextix = -1;
                for (int i = airway.waypoints.length; -- i >= 0;) {
                    Waypoint wp = airway.waypoints[i];
                    if (wp.equals (prevwp)) previx = i;
                    if (wp.equals (nextwp)) nextix = i;
                }
                if ((previx < 0) || (nextix < 0)) continue;

                // get list of waypoints from entry to exit inclusive
                // and in order from entry to exit point
                ArrayList<Waypoint> selectedWaypoints;
                if (previx < nextix) {
                    selectedWaypoints = new ArrayList<> (nextix - previx + 1);
                    //noinspection ManualArrayToCollectionCopy
                    for (int i = previx; i <= nextix; i ++) {
                        Waypoint wp = airway.waypoints[i];
                        selectedWaypoints.add (wp);
                    }
                } else {
                    selectedWaypoints = new ArrayList<> (previx - nextix + 1);
                    for (int i = previx; i >= nextix; -- i) {
                        Waypoint wp = airway.waypoints[i];
                        selectedWaypoints.add (wp);
                    }
                }
                return selectedWaypoints;
            }

            // nothing matches, return empty list
            return new LinkedList<> ();
        }
    }

    /**
     * Compute the route given in the box.
     */
    private void ComputeRoute ()
    {
        startingLat        = wairToNow.currentGPSLat;
        startingLon        = wairToNow.currentGPSLon;
        analyzedRouteArray = null;
        pointAhead         = 999999999;
        ShutTrackingOff ();

        goodColor = Color.GREEN;
        msgBuilder = new SpannableStringBuilder ();

        /*
         * Parse entered string into a list of waypoints and airways.
         */
        firstStep = null;
        lastStep  = null;
        String str = editTextR.getText ().toString ().toUpperCase (Locale.US);
        String[] words = str.replace ('\n', ' ').split (" ");
        editTextR.setText ("");
        boolean firstWord = true;
        for (String word : words) {
            if (!firstWord) editTextR.append (" ");
            firstWord = false;
            editTextR.append (word);

            // skip redundant spaces
            if (word.equals ("")) continue;

            // anything starting with # is ignored
            if (word.startsWith ("#")) continue;

            // recognize @PP='present position' as the first step
            if (word.equals ("@PP")) {
                if (firstStep != null) {
                    appendErrorMessage ("@PP not at beginning\n");
                } else {
                    LinkedList<Waypoint> pps = new LinkedList<> ();
                    pps.addLast (new PresPosWaypt ());
                    insertOnEnd (new RouteStepWaypt ("@PP", pps));
                }
                continue;
            }

            // @ anything else is an airport
            if (word.startsWith ("@")) {
                String aptid = word.substring (1);
                Waypoint aptwp = Waypoint.GetAirportByIdent (aptid);
                if (aptwp == null) {
                    appendErrorMessage ("unknown airport " + aptid + "\n");
                } else {
                    LinkedList<Waypoint> pps = new LinkedList<> ();
                    pps.addLast (aptwp);
                    insertOnEnd (new RouteStepWaypt (aptwp.ident, pps));
                }
                continue;
            }

            // see if given a valid waypoint name (user or FAA defined)
            UserWPView.UserWP uwp = wairToNow.userWPView.waypoints.get (word);
            if (uwp != null) {
                LinkedList<Waypoint> pps = new LinkedList<> ();
                pps.addLast (uwp);
                insertOnEnd (new RouteStepWaypt (uwp.ident, pps));
                continue;
            }
            LinkedList<Waypoint> wps = Waypoint.GetWaypointsByIdent (word);
            if ((wps != null) && !wps.isEmpty ()) {
                insertOnEnd (new RouteStepWaypt (wps.iterator ().next ().ident, wps));
                continue;
            }

            // see if given an valid airway name
            Collection<Waypoint.Airway> awys = Waypoint.Airway.GetAirways (word);
            if (awys != null) {
                insertOnEnd (new RouteStepAirwy (word, awys));
                continue;
            }

            // we just don't know...
            appendErrorMessage ("unknown " + word + "\n");
        }

        if (firstStep == null) {
            appendErrorMessage ("no valid steps given in route box\n");
            showMessages ();
            return;
        }

        /*
         * Destination is final waypoint of the route.
         */
        String destid = editTextD.getText ().toString ().trim ().toUpperCase (Locale.US);
        editTextD.setText (destid);
        if (!destid.equals ("")) {
            Waypoint destwp = wairToNow.userWPView.waypoints.get (destid);
            if (destwp == null) destwp = Waypoint.GetAirportByIdent (destid);
            if (destwp == null) {
                appendErrorMessage ("unknown destination " + destid + "\n");
            } else {
                LinkedList<Waypoint> wplist = new LinkedList<> ();
                wplist.add (destwp);
                insertOnEnd (new RouteStepWaypt (destwp.ident, wplist));
                editTextD.setText (destwp.ident);
            }
        }

        /*
         * Figure out where we can possibly get on and off the airways
         * by looking at the adjacent waypoint steps.  If there are two
         * airways in a row (eg, V1 V146), insert a waypoint that is the
         * set of all the waypoints in common to the airways.
         */
        RouteStep prevrs = null;
        for (RouteStep thisrs = firstStep; thisrs != null; thisrs = thisrs.nextStep) {

            /*
             * If waypoint followed by airway, find that waypoint on the airway.
             * Then narrow down the waypoint step to the one that matches the airway
             * (in case there is more than one waypoint with the same name, we also
             * match the lat/lon with the airway).
             */
            if ((prevrs instanceof RouteStepWaypt) && (thisrs instanceof RouteStepAirwy)) {
                RouteStepWaypt prevrswpt = (RouteStepWaypt) prevrs;
                RouteStepAirwy thisrsawy = (RouteStepAirwy) thisrs;
                prevrswpt.waypoints = findWaypointOnAirway (prevrswpt, thisrsawy);
            }

            /*
             * If airway followed by waypoint, find that waypoint on the airway.
             * Then narrow down the waypoint step to the one that matches the airway
             * (in case there is more than one waypoint with the same name, we also
             * match the lat/lon with the airway).
             */
            if ((prevrs instanceof RouteStepAirwy) && (thisrs instanceof RouteStepWaypt)) {
                RouteStepAirwy prevrsawy = (RouteStepAirwy) prevrs;
                RouteStepWaypt thisrswpt = (RouteStepWaypt) thisrs;
                thisrswpt.waypoints = findWaypointOnAirway (thisrswpt, prevrsawy);
            }

            /*
             * If two adjacent airways, find intersection set and enter that as a waypoint
             * between the two airways.  There may be several waypoints of different names
             * entered as that single RouteStepWaypt entry, but the specific one will be
             * selected when we scan for the shortest route below.
             */
            if ((prevrs instanceof RouteStepAirwy) && (thisrs instanceof RouteStepAirwy)) {
                RouteStepAirwy prevrsawy = (RouteStepAirwy) prevrs;
                RouteStepAirwy thisrsawy = (RouteStepAirwy) thisrs;
                LinkedList<Waypoint> commonWaypoints = new LinkedList<> ();
                for (Waypoint.Airway prevawy : prevrsawy.airways) {
                    for (Waypoint.Airway thisawy : thisrsawy.airways) {
                        for (Waypoint prevwp : prevawy.waypoints) {
                            for (Waypoint thiswp : thisawy.waypoints) {
                                if (prevwp.equals (thiswp)) {
                                    commonWaypoints.addLast (thiswp);
                                }
                            }
                        }
                    }
                }
                if (commonWaypoints.isEmpty ()) {
                    appendErrorMessage ("no intersection between " + prevrsawy.ident + " and " + thisrsawy.ident + "\n");
                }
                insertAfter (prevrs, new RouteStepWaypt (prevrsawy.ident + "*" + thisrsawy.ident, commonWaypoints));
            }

            prevrs = thisrs;
        }

        /*
         * At this point, all airway steps should have a waypoint step both
         * before and after them, narrowed down to waypoints that match those
         * that are on the airway.  If none matched the airway, they will be
         * empty sets and an error message has already been displayed.
         */

        /*
         * See how many possibilities we have to select from.
         */
        long numsolutions = 1;
        for (RouteStep rs = firstStep; rs != null; rs = rs.nextStep) {
            if (!(rs instanceof RouteStepWaypt)) continue;
            int num = ((RouteStepWaypt) rs).waypoints.size ();
            numsolutions *= num;
            if (numsolutions > 1000) {
                appendErrorMessage ("ambiguous route, try inserting waypoints between airways\n");
                showMessages ();
                return;
            }
        }
        if (numsolutions == 0) {
            appendErrorMessage ("no possible solution\n");
            showMessages ();
            return;
        }

        /*
         * Find solution (combination of waypoint alternatives) of shortest distance.
         */
        double bestdistance = 99999.0;
        int bestsolution = -1;
        for (int solutionnum = 0; solutionnum < numsolutions; solutionnum ++) {

            // select a possible solution for all the waypoint steps
            int solutionidx = solutionnum;
            for (RouteStep rs = firstStep; rs != null; rs = rs.nextStep) {
                if (!(rs instanceof RouteStepWaypt)) continue;
                RouteStepWaypt rswpt = (RouteStepWaypt) rs;
                int nsolutions = rswpt.waypoints.size ();
                rswpt.selectedsolution = solutionidx % nsolutions;
                solutionidx /= nsolutions;
            }

            // calculate total route distance using those selections
            double lastlat  = 0.0;
            double lastlon  = 0.0;
            double distance = 0.0;
            for (RouteStep rs = firstStep; rs != null; rs = rs.nextStep) {

                // if step is airway, add distance from last point
                // along each waypoint selected on airway
                if (rs instanceof RouteStepAirwy) {
                    RouteStepAirwy rsawy = (RouteStepAirwy) rs;
                    for (Waypoint wp : rsawy.GetSelectedWaypoints ()) {
                        if ((lastlat != 0.0) || (lastlon != 0.0)) {
                            distance += Lib.LatLonDist (lastlat, lastlon, wp.lat, wp.lon);
                        }
                        lastlat = wp.lat;
                        lastlon = wp.lon;
                    }
                }

                // if step is waypoint, add distance from last point to this waypoint
                if (rs instanceof RouteStepWaypt) {
                    RouteStepWaypt rswpt = (RouteStepWaypt) rs;
                    Waypoint wp = rswpt.waypoints.get (rswpt.selectedsolution);
                    if ((lastlat != 0.0) || (lastlon != 0.0)) {
                        distance += Lib.LatLonDist (lastlat, lastlon, wp.lat, wp.lon);
                    }
                    lastlat = wp.lat;
                    lastlon = wp.lon;
                }
            }

            // select shortest distance
            if (bestdistance > distance) {
                bestdistance = distance;
                bestsolution = solutionnum;
            }
        }

        /*
         * Select the shortest solution by marking which waypoint in the waypoint lists we chose.
         * Then the airways if any will use the selected waypoints as their entry/exit points.
         */
        for (RouteStep rs = firstStep; rs != null; rs = rs.nextStep) {
            if (rs instanceof RouteStepWaypt) {
                RouteStepWaypt rswpt = (RouteStepWaypt) rs;
                int nsolutions = rswpt.waypoints.size ();
                rswpt.selectedsolution = bestsolution % nsolutions;
                bestsolution /= nsolutions;
            }
        }

        /*
         * Show resultant total distace.
         */
        boolean mphOption = wairToNow.optionsView.ktsMphOption.getAlt ();
        appendGoodMessage ("total distance " + Lib.DistString (bestdistance, mphOption) + "\n");

        /*
         * Capture the final routing and display it.
         */
        LinkedList<Waypoint> arlist = new LinkedList<> ();
        Waypoint prevwp = null;
        for (RouteStep rs = firstStep; rs != null; rs = rs.nextStep) {
            if (rs instanceof RouteStepAirwy) {
                RouteStepAirwy rsawy = (RouteStepAirwy) rs;
                appendGoodMessage ("Airway " + rsawy.ident + " {\n");
                for (Waypoint thiswp : rsawy.GetSelectedWaypoints ()) {
                    if (!thiswp.equals (prevwp)) {
                        appendGoodMessage ("  " + thiswp.MenuKey () + "\n");
                        arlist.addLast (thiswp);
                    }
                    prevwp = thiswp;
                }
                appendGoodMessage ("}\n");
            }
            if (rs instanceof RouteStepWaypt) {
                RouteStepWaypt rswpt = (RouteStepWaypt) rs;
                Waypoint thiswp = rswpt.waypoints.get (rswpt.selectedsolution);
                if (!thiswp.equals (prevwp)) {
                    appendGoodMessage (thiswp.MenuKey () + "\n");
                    arlist.addLast (thiswp);
                }
                prevwp = thiswp;
            }
        }

        /*
         * Edit out redundant points.
         * Leave any points alone that are explicitly stated in clearance however,
         * maybe user wants an ETA at that point for position reporting.
         * Also leave navaids in place as they define an airway so the pilot will
         * want to know when they pass.
         */
        int nanal = arlist.size ();
        Waypoint[] ararray = new Waypoint[nanal];
        arlist.toArray (ararray);
        arlist.clear ();
        prevwp = null;
        for (int i = 0; i < nanal; i ++) {
            Waypoint thiswp = ararray[i];
            Waypoint nextwp = (i < nanal - 1) ? ararray[i+1] : null;
            if ((prevwp == null) || (nextwp == null) || !inARow (prevwp, thiswp, nextwp) ||
                    statedInClearance (thiswp) || (thiswp instanceof Waypoint.Navaid)) {
                arlist.addLast (thiswp);
                prevwp = thiswp;
            }
        }

        /*
         * Enable tracking checkbox if remaining route has any waypoints.
         */
        if (!arlist.isEmpty ()) {
            analyzedRouteArray = arlist.toArray (new Waypoint[arlist.size()]);
            buttonTrack.setEnabled (true);
        }

        appendGoodMessage ("done\n");

        showMessages ();
    }

    /**
     * Find the given waypoint on the given airway.
     */
    private LinkedList<Waypoint> findWaypointOnAirway (RouteStepWaypt rswpt, RouteStepAirwy rsawy)
    {
        // collect waypoint on airway that matches the given waypoint
        // theoretically there can be more than one of same name
        LinkedList<Waypoint> commonWaypoints = new LinkedList<> ();

        // collect waypoints on airway near the given waypoint in case given waypoint not on airway
        // sort by ascenting distance from given waypoint
        TreeMap<Double,Waypoint> nearbyWpsOnAirwy = new TreeMap<> ();

        // loop through all the given waypoints (there may be more than one of same name)
        for (Waypoint rswptwp : rswpt.waypoints) {

            // loop through all the given airways (there may be more than one of same name)
            for (Waypoint.Airway airway : rsawy.airways) {

                // loop through all waypoints on that airway
                for (Waypoint rsawywp : airway.waypoints) {

                    // save matching waypoint if name and lat/lon matches
                    if (rswptwp.equals (rsawywp)) {
                        commonWaypoints.addLast (rsawywp);
                    } else if (!(rsawywp instanceof Waypoint.Intersection)) {

                        // otherwise, save it sorted by ascending distance from given waypoint
                        // don't save any of the numbered airway intersection waypoints
                        // just things like VORs or fixes
                        double dist = Lib.LatLonDist (rswptwp.lat, rswptwp.lon, rsawywp.lat, rsawywp.lon);
                        nearbyWpsOnAirwy.put (dist, rsawywp);
                    }
                }
            }
        }

        // see if any exact match was found
        if (commonWaypoints.isEmpty ()) {

            // if not, output error message
            appendErrorMessage ("waypoint " + rswpt.ident + " not found on airway " + rsawy.ident + "\n");

            // display two nearby waypoints that are on the airway
            Iterator<Waypoint> it = nearbyWpsOnAirwy.values ().iterator ();
            if (it.hasNext ()) {
                Waypoint wp = it.next ();
                appendErrorMessage ("...maybe insert " + wp.ident);
                if (it.hasNext ()) {
                    wp = it.next ();
                    appendErrorMessage (" or " + wp.ident);
                }
                appendErrorMessage ("\n");
            }
        }

        // return what was found.  if nothing, return empty set.
        return commonWaypoints;
    }

    private void appendErrorMessage (String string)
    {
        appendColoredMessage (Color.RED, string);
        goodColor = Color.YELLOW;
    }

    private void appendGoodMessage (String string)
    {
        appendColoredMessage (goodColor, string);
    }

    private void appendColoredMessage (int color, String string)
    {
        int beg = msgBuilder.length ();
        msgBuilder.append (string);
        int end = msgBuilder.length ();
        ForegroundColorSpan fcs = new ForegroundColorSpan (color);
        msgBuilder.setSpan (fcs, beg, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    private void showMessages ()
    {
        textViewC.setText (msgBuilder);
    }

    /**
     * Insert the route step on the end of the list of steps.
     */
    private void insertOnEnd (RouteStep thisrs)
    {
        if (lastStep == null) firstStep = thisrs;
        else lastStep.nextStep = thisrs;

        thisrs.prevStep = lastStep;
        lastStep = thisrs;
    }

    /**
     * Insert a route step after a given route step.
     * @param prevrs = route step to be inserted after
     * @param thisrs = route step to insert
     */
    private void insertAfter (RouteStep prevrs, RouteStep thisrs)
    {
        thisrs.prevStep = prevrs;
        thisrs.nextStep = prevrs.nextStep;

        thisrs.prevStep.nextStep = thisrs;

        if (thisrs.nextStep != null) {
            thisrs.nextStep.prevStep = thisrs;
        } else {
            lastStep = thisrs;
        }
    }

    /**
     * See if the given waypoints are all lined up.
     */
    private static boolean inARow (Waypoint wp1, Waypoint wp2, Waypoint wp3)
    {
        if ((wp1.lat == wp2.lat) && (wp1.lon == wp2.lon)) return true;
        if ((wp2.lat == wp3.lat) && (wp2.lon == wp3.lon)) return true;

        double tc12 = Lib.LatLonTC (wp1.lat, wp1.lon, wp2.lat, wp2.lon);
        double tc23 = Lib.LatLonTC (wp2.lat, wp2.lon, wp3.lat, wp3.lon);
        double tcdiff = Math.abs (tc12 - tc23);
        if (tcdiff > 180.0) tcdiff = 360.0 - tcdiff;
        return tcdiff < 1.0;  // all lined up within a degree
    }

    /**
     * See if the given waypoint was stated explicitly as a step in the route clearance.
     */
    private boolean statedInClearance (Waypoint wp)
    {
        for (RouteStep rs = firstStep; rs != null; rs = rs.nextStep) {
            if (rs.ident.equals (wp.ident)) return true;
        }
        return false;
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Route";
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

    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        displayOpen = true;
        boolean typeB = wairToNow.optionsView.typeBOption.checkBox.isChecked ();
        if (typeB) ShutTrackingOff ();
        buttonTrack.setVisibility (typeB ? GONE : VISIBLE);
        if (trackingOn) UpdateTrackingDisplay ();
    }

    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        displayOpen = false;
    }

    @Override  // WairToNow.CanBeMainView
    public void ReClicked ()
    { }

    @Override  // WairToNow.CanBeMainView
    public View GetBackPage ()
    {
        return this;
    }

    /**
     * Make sure we are no longer tracking progress along the analyzed route,
     * (user is probably changing the route or just wants to stop tracking).
     */
    @SuppressLint("SetTextI18n")
    public void ShutTrackingOff ()
    {
        trackingOn = false;
        buttonTrack.setEnabled (analyzedRouteArray != null);
        buttonTrack.setText ("track on");
        textViewC.setText ("");
    }

    /**
     * Save button was clicked, write form values to a simple XML file.
     */
    private void SaveButtonClicked ()
    {
        /*
         * Make sure we have a directory to save to.
         */
        Lib.Ignored (new File (datadir).mkdirs ());

        /*
         * Display dialog to ask for filename to save to.
         * Default to whatever the current data was loaded from.
         */
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("Save route");
        final EditText nameEntry = new EditText (wairToNow);
        nameEntry.setText (loadedFromName);
        adb.setView (nameEntry);
        adb.setPositiveButton ("Save", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                final String name = nameEntry.getText ().toString ().trim ();
                if (!name.equals ("")) {

                    /*
                     * See if file already exists so we don't accidentally overwrite it.
                     */
                    final File file = new File (datadir + "/" + URLEncoder.encode (name) + ".xml");
                    if (file.exists ()) {
                        AlertDialog.Builder adb2 = new AlertDialog.Builder (wairToNow);
                        adb2.setTitle ("Save route");
                        adb2.setMessage (name + " already exists, overwrite?");
                        adb2.setPositiveButton ("Overwrite", new DialogInterface.OnClickListener () {
                            @Override
                            public void onClick (DialogInterface dialogInterface, int i)
                            {
                                SaveButtonWriteFile (file, name);
                            }
                        });
                        adb2.setNegativeButton ("Cancel", null);
                        adb2.show ();
                    } else {

                        /*
                         * Ok to write to file.
                         */
                        SaveButtonWriteFile (file, name);
                    }
                }
            }
        });
        adb.setNegativeButton ("Cancel", null);
        adb.show ();
    }

    /**
     * Filename accepted, write form values to file.
     */
    private void SaveButtonWriteFile (File file, String name)
    {
        String path = file.getPath ();
        try {
            FileWriter fw = new FileWriter (path + ".tmp");
            try {
                fw.write ("<clearance>\n");
                fw.write ("  <destination>" + xmlenc (editTextD.getText ().toString ()) + "</destination>\n");
                fw.write ("  <route>"       + xmlenc (editTextR.getText ().toString ()) + "</route>\n");
                fw.write ("  <altitude>"    + xmlenc (editTextA.getText ().toString ()) + "</altitude>\n");
                fw.write ("  <frequency>"   + xmlenc (editTextF.getText ().toString ()) + "</frequency>\n");
                fw.write ("  <transponder>" + xmlenc (editTextT.getText ().toString ()) + "</transponder>\n");
                fw.write ("  <notes>"       + xmlenc (editTextN.getText ().toString ()) + "</notes>\n");
                fw.write ("</clearance>\n");
            } finally {
                fw.close ();
            }
            Lib.RenameFile (path + ".tmp", path);
            loadedFromName = name;
        } catch (Exception e) {
            Log.e (TAG, "error writing " + path, e);

            String msg = e.getMessage ();
            if (msg == null) msg = e.getClass ().getSimpleName ();
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Error saving file");
            adb.setMessage (msg);
            adb.setPositiveButton ("OK", null);
        }
    }

    private static String xmlenc (String s)
    {
        return s.replace ("&", "&amp;").replace ("<", "&lt;");
    }

    /**
     * Load button just clicked, load form values from an XML file.
     */
    @SuppressLint("SetTextI18n")
    private void LoadButtonClicked ()
    {
        final String ntsep = " (";

        /*
         * Create listener for the file buttons.
         * When button clicked, dismiss menu and open file.
         */
        OnClickListener clicked = new OnClickListener () {
            @Override
            public void onClick (View view) {
                if (loadButtonMenu != null) {
                    loadButtonMenu.dismiss ();
                    loadButtonMenu = null;
                }
                File file = (File) view.getTag ();
                String name = ((TextView) view).getText ().toString ();
                int i = name.lastIndexOf (ntsep);
                name = name.substring (0, i);
                LoadButtonReadFile (file, name);
            }
        };

        OnLongClickListener longClicked = new OnLongClickListener () {
            @Override
            public boolean onLongClick (View view)
            {
                if (loadButtonMenu != null) {
                    loadButtonMenu.dismiss ();
                    loadButtonMenu = null;
                }
                File file = (File) view.getTag ();
                String name = ((TextView) view).getText ().toString ();
                int i = name.lastIndexOf (ntsep);
                name = name.substring (0, i);
                LoadButtonLongClick (file, name);
                return true;
            }
        };

        /*
         * Make a list of buttons of existing files.
         */
        LinearLayout buttonList = new LinearLayout (wairToNow);
        buttonList.setOrientation (LinearLayout.VERTICAL);
        File[] filearray = new File (datadir).listFiles ();
        if (filearray != null) {
            TreeMap<String,Button> sortedButtons = new TreeMap<> ();
            SimpleDateFormat sdf = new SimpleDateFormat ("yyyy-MM-dd HH:mm", Locale.US);
            for (File file : filearray) {
                String name = file.getName ();
                if (name.endsWith (".xml")) {
                    name = name.substring (0, name.length () - 4);
                    String decodedName = URLDecoder.decode (name);
                    long mtim = file.lastModified ();
                    String modifiedTime = sdf.format (new Date (mtim));
                    Button filebutton = new Button (wairToNow);
                    filebutton.setText (decodedName + ntsep + modifiedTime + ")");
                    wairToNow.SetTextSize (filebutton);
                    filebutton.setTag (file);
                    filebutton.setOnClickListener (clicked);
                    filebutton.setOnLongClickListener (longClicked);
                    sortedButtons.put (decodedName, filebutton);
                }
            }
            for (Button filebutton : sortedButtons.values ()) {
                buttonList.addView (filebutton);
            }
        }

        /*
         * Wrap the buttons in a scroller.
         */
        ScrollView buttonScroller = new ScrollView (wairToNow);
        buttonScroller.addView (buttonList);

        /*
         * Display dialog with the scroller list of buttons.
         */
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("Load route");
        adb.setView (buttonScroller);
        adb.setPositiveButton ("Cancel", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                loadButtonMenu = null;
            }
        });
        loadButtonMenu = adb.show ();
    }

    /**
     * A particular file was selected for renaming or deleting.
     */
    private void LoadButtonLongClick (final File file, final String name)
    {
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("Rename or Delete?");
        adb.setMessage (name);
        adb.setPositiveButton ("Rename", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Rename route");
                final EditText newedit = new EditText (wairToNow);
                newedit.setText (name);
                wairToNow.SetTextSize (newedit);
                adb.setView (newedit);
                adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialogInterface, int i)
                    {
                        String newname = newedit.getText ().toString ();
                        final File newfile = new File (datadir, URLEncoder.encode (newname) + ".xml");
                        if (newfile.exists ()) {
                            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                            adb.setTitle ("Rename route");
                            adb.setMessage (newname + " already exists, overwrite?");
                            adb.setPositiveButton ("Overwrite", new DialogInterface.OnClickListener () {
                                @Override
                                public void onClick (DialogInterface dialogInterface, int i)
                                {
                                    Lib.Ignored (file.renameTo (newfile));
                                }
                            });
                            adb.setNegativeButton ("Cancel", null);
                            adb.show ();
                        } else {
                            Lib.Ignored (file.renameTo (newfile));
                        }
                    }
                });
                adb.setNegativeButton ("Cancel", null);
                adb.show ();
            }
        });
        adb.setNeutralButton ("Delete", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Delete route");
                adb.setMessage (name);
                adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialogInterface, int i)
                    {
                        Lib.Ignored (file.delete ());
                    }
                });
                adb.setNegativeButton ("Cancel", null);
                adb.show ();
            }
        });
        adb.setNegativeButton ("Cancel", null);
        adb.show ();
    }

    /**
     * A particular file was selected for loading.
     */
    public void LoadButtonReadFile (File file, String name)
    {
        /*
         * If we are tracking some old route, stop.
         * Also clears validation/tracking message box.
         */
        ShutTrackingOff ();

        try {

            /*
             * Open file for XML parsing.
             */
            BufferedReader br = new BufferedReader (new FileReader (file), 4096);
            try {
                XmlPullParser xpp = Xml.newPullParser ();
                xpp.setFeature (XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                xpp.setInput (br);

                /*
                 * Loop through all the XML tags in the file.
                 */
                String text = "";
                for (int eventType = xpp.getEventType (); eventType != XmlPullParser.END_DOCUMENT; eventType = xpp.next ()) {
                    switch (eventType) {

                        // all our tags have text between them as the content of the boxes
                        case XmlPullParser.START_TAG: {
                            text = "";
                            break;
                        }
                        case XmlPullParser.TEXT: {
                            text = xpp.getText ();
                            break;
                        }

                        // when we get the end tag, we should have the corresponding text
                        case XmlPullParser.END_TAG: {
                            switch (xpp.getName ()) {
                                case "destination": {
                                    editTextD.setText (text);
                                    text = "";
                                    break;
                                }
                                case "route": {
                                    editTextR.setText (text);
                                    text = "";
                                    break;
                                }
                                case "altitude": {
                                    editTextA.setText (text);
                                    text = "";
                                    break;
                                }
                                case "frequency": {
                                    editTextF.setText (text);
                                    text = "";
                                    break;
                                }
                                case "transponder": {
                                    editTextT.setText (text);
                                    text = "";
                                    break;
                                }
                                case "notes": {
                                    editTextN.setText (text);
                                    text = "";
                                    break;
                                }
                            }
                        }
                    }
                }
            } finally {
                br.close ();
            }
            loadedFromName = name;
        } catch (Exception e) {
            Log.e (TAG, "error reading " + file.getAbsolutePath (), e);

            String msg = e.getMessage ();
            if (msg == null) msg = e.getClass ().getSimpleName ();
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Error loading file");
            adb.setMessage (msg);
            adb.setPositiveButton ("OK", null);
        }
    }

    /**
     * Got an update from the GPS.
     * If we are tracking, change the course if we have progressed to the next waypoint.
     */
    public void SetGPSLocation ()
    {
        if (trackingOn) CalcPointAhead ();
    }

    /**
     * Calculate which point along the analyzed route is next.
     * Set the destination to that point with the route being the segment before the point.
     * Update the tracking table display as well to show heading/distance to the point.
     */
    private void CalcPointAhead ()
    {
        // scan route to find closest point
        double destdist = 999999.0;
        int   desti    = -1;
        int   npoints  = analyzedRouteArray.length;
        for (int i = npoints; -- i >= 0;) {
            Waypoint wp = analyzedRouteArray[i];
            double dist = Lib.LatLonDist (wairToNow.currentGPSLat, wairToNow.currentGPSLon, wp.lat, wp.lon);
            if (destdist > dist) {
                destdist = dist;
                desti    = i;
            }
        }

        // if closest point is the last point, then we are headed to the closest point.
        if (desti < npoints - 1) {

            // if closest point is the first point, then we are headed to the next point.
            if (desti == 0) desti = 1;
            else {

                // somewhere in middle, get distances from segment behind the point and ahead of it
                double distbehind = distFromRouteSeg (desti, desti - 1);
                double distahead  = distFromRouteSeg (desti, desti + 1);

                // if beyond segment behind point or if closer to segment ahead of point,
                // then we are headed to the next point.
                if ((distbehind < 0.0) || (Math.abs (distahead) < distbehind)) desti ++;
            }
        }

        // draw course line from previous waypoint on route to the destination waypoint ahead
        pointAhead = desti;
        double lastlat = startingLat;
        double lastlon = startingLon;
        if (desti > 0) {
            Waypoint wp = analyzedRouteArray[desti-1];
            lastlat = wp.lat;
            lastlon = wp.lon;
        }
        wairToNow.chartView.SetCourseLine (lastlat, lastlon, analyzedRouteArray[pointAhead]);

        // if route page open, update the display
        if (displayOpen) UpdateTrackingDisplay ();
    }

    /**
     * See how close we are from the given route segment.
     * @param i = closer of the two segment endpoints
     * @param j = farther of the two segment endpoints
     * @return nm distance from route segment (neg if before I, pos if along segment from I to J)
     */
    private double distFromRouteSeg (int i, int j)
    {
        Waypoint wpi = analyzedRouteArray[i];
        Waypoint wpj = analyzedRouteArray[j];

        double lastlat = wairToNow.currentGPSLat;
        double lastlon = wairToNow.currentGPSLon;

        // see if before segment from I to J.
        // if so, we are distance I from route segment.
        // use dot product, treating lat/lon as rectangular coords, that's good enough.
        if (((wpj.lat - wpi.lat) * (lastlat - wpi.lat) +
                (wpj.lon - wpi.lon) * (lastlon - wpi.lon)) < 0) {
            return - Lib.LatLonDist (wpi.lat, wpi.lon, lastlat, lastlon);
        }

        // since we are closer to I than J, we can't be off end of segment from I to J

        // somewhere in middle of segment, distance is off-course distance
        return Math.abs (Lib.GCOffCourseDist (wpi.lat, wpi.lon, wpj.lat, wpj.lon, lastlat, lastlon));
    }

    /**
     * Called when tracking active and we received a GPS update.
     * It updates the message window with current positioning information.
     */
    private void UpdateTrackingDisplay ()
    {
        StringBuilder sb = new StringBuilder ();

        /*
         * Show current Zulu time for position reporting purposes.
         */
        long nowms = System.currentTimeMillis ();
        sb.append ("@pp ");
        int nowmin = (int) ((nowms / 60000) % 1440);
        sb.append (' ');
        sb.append (Integer.toString (nowmin / 60 + 100).substring (1));
        sb.append (Integer.toString (nowmin % 60 + 100).substring (1));
        sb.append ("Z\n");

        /*
         * Display all points ahead of us and the heading and distance between them.
         * Also display ETA in Zulu for position reporting purposes.
         */
        double lastGPSAlt = wairToNow.currentGPSAlt;
        double lastGPSpeed = wairToNow.currentGPSSpd;
        double lastlat = wairToNow.currentGPSLat;
        double lastlon = wairToNow.currentGPSLon;
        double totnm   = 0.0;
        boolean mphOption = wairToNow.optionsView.ktsMphOption.getAlt ();
        for (int i = pointAhead; i < analyzedRouteArray.length; i ++) {
            sb.append ('\u25B6');
            Waypoint wp = analyzedRouteArray[i];
            double tc = Lib.LatLonTC (lastlat, lastlon, wp.lat, wp.lon);
            double nm = Lib.LatLonDist (lastlat, lastlon, wp.lat, wp.lon);
            totnm += nm;
            String hdgstr  = wairToNow.optionsView.HdgString (tc, lastlat, lastlon, lastGPSAlt);
            sb.append (hdgstr.replace (" ", "").replace ("Mag", "M").replace ("True", "T"));
            sb.append (' ');
            sb.append (Lib.DistString (nm, mphOption).replace (" ", ""));
            sb.append ('\u25B6');
            sb.append (wp.ident);
            if (lastGPSpeed > WairToNow.gpsMinSpeedMPS) {
                long etams = nowms + Math.round (totnm / lastGPSpeed / Lib.KtPerMPS * 3600000.0);
                int etamin = (int) ((etams / 60000) % 1440);
                sb.append (' ');
                sb.append (Integer.toString (etamin / 60 + 100).substring (1));
                sb.append (Integer.toString (etamin % 60 + 100).substring (1));
                sb.append ('Z');
            }
            sb.append ('\n');
            lastlat = wp.lat;
            lastlon = wp.lon;
        }

        /*
         * Display total distance and estimated time enroute to final point.
         */
        if (totnm > 0.0) {
            sb.append ("total: ");
            sb.append (Lib.DistString (totnm, mphOption));
            if (lastGPSpeed > WairToNow.gpsMinSpeedMPS) {
                sb.append (" ete: ");
                int seconds = (int) Math.round (totnm / lastGPSpeed / Lib.KtPerMPS * 3600.0);
                int minutes = seconds / 60;
                int hours   = minutes / 60;
                seconds %= 60;
                minutes %= 60;
                if (hours > 0) {
                    sb.append (hours);
                    sb.append (':');
                    sb.append (Integer.toString (minutes + 100).substring (1));
                } else {
                    sb.append (minutes);
                }
                sb.append (':');
                sb.append (Integer.toString (seconds + 100).substring (1));
            }
        }

        /*
         * Display resultant string.
         */
        textViewC.setText (sb);
        textViewC.setTextColor (Color.CYAN);
    }
}
