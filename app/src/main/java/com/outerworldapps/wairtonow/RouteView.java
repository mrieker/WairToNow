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
import android.content.DialogInterface;
import android.graphics.Color;
import android.location.Location;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Enter a route (such as a clearance) and analyze it.
 */
public class RouteView extends ScrollView implements WairToNow.CanBeMainView {
    private final static String TAG = "WairToNow";
    private final static String datadir = WairToNow.dbdir + "/routes";

    private AlertDialog loadButtonMenu;
    private boolean displayOpen;
    private boolean trackingOn;
    private Button buttonTrack;
    private EditText editTextD;
    private EditText editTextR;
    private EditText editTextA;
    private EditText editTextF;
    private EditText editTextT;
    private EditText editTextN;
    private float lastGPSAlt;
    private float lastGPSHdg;
    private float lastGPSLat;
    private float lastGPSLon;
    private float lastGPSpeed;
    private float startingLat;
    private float startingLon;
    private int goodColor;
    private LinkedList<Waypoint> analyzedRoute;
    private RouteStep firstStep;
    private RouteStep lastStep;
    private SpannableStringBuilder msgBuilder;
    private String editTextRHint;
    private TextView textViewC;
    private WairToNow wairToNow;
    private Waypoint pointAhead;

    public RouteView (WairToNow ctx)
    {
        super (ctx);
        wairToNow = ctx;

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
            public void onClick (View view) {
                editTextD.setText ("");
                editTextR.setText ("");
                editTextA.setText ("");
                editTextF.setText ("");
                editTextT.setText ("");
                editTextN.setText ("");
                textViewC.setText ("");
                editTextR.setHint (editTextRHint);
                analyzedRoute = null;
                pointAhead = null;
                ShutTrackingOff ();
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
                trackingOn = (analyzedRoute != null) & !trackingOn;
                buttonTrack.setText (trackingOn ? "track off" : "track on ");
                if (trackingOn) CalcPointAhead ();
            }
        });

        ShutTrackingOff ();

        View routeView = ctx.findViewById (R.id.routeView);
        ((ViewGroup) (routeView.getParent ())).removeAllViews ();
        addView (routeView);
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
        startingLat = lastGPSLat;
        startingLon = lastGPSLon;
        analyzedRoute = null;
        pointAhead = null;
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
                LinkedList<Waypoint> commonWaypoints = new LinkedList<> ();
                for (Waypoint prevwp : prevrswpt.waypoints) {
                    for (Waypoint.Airway airway : thisrsawy.airways) {
                        for (Waypoint thiswp : airway.waypoints) {
                            if (prevwp.equals (thiswp)) {
                                commonWaypoints.addLast (thiswp);
                            }
                        }
                    }
                }
                if (commonWaypoints.isEmpty ()) {
                    appendErrorMessage ("waypoint " + prevrswpt.ident + " not found on airway " + thisrsawy.ident + "\n");
                }
                prevrswpt.waypoints = commonWaypoints;
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
                LinkedList<Waypoint> commonWaypoints = new LinkedList<> ();
                for (Waypoint thiswp : thisrswpt.waypoints) {
                    for (Waypoint.Airway airway : prevrsawy.airways) {
                        for (Waypoint prevwp : airway.waypoints) {
                            if (thiswp.equals (prevwp)) {
                                commonWaypoints.addLast (prevwp);
                            }
                        }
                    }
                }
                if (commonWaypoints.isEmpty ()) {
                    appendErrorMessage ("waypoint " + thisrswpt.ident + " not found on airway " + prevrsawy.ident + "\n");
                }
                thisrswpt.waypoints = commonWaypoints;
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
        float bestdistance = 99999.0F;
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
            float lastlat  = 0.0F;
            float lastlon  = 0.0F;
            float distance = 0.0F;
            for (RouteStep rs = firstStep; rs != null; rs = rs.nextStep) {

                // if step is airway, add distance from last point
                // along each waypoint selected on airway
                if (rs instanceof RouteStepAirwy) {
                    RouteStepAirwy rsawy = (RouteStepAirwy) rs;
                    for (Waypoint wp : rsawy.GetSelectedWaypoints ()) {
                        if ((lastlat != 0.0F) || (lastlon != 0.0F)) {
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
                    if ((lastlat != 0.0F) || (lastlon != 0.0F)) {
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
        analyzedRoute = new LinkedList<> ();
        Waypoint prevwp = null;
        for (RouteStep rs = firstStep; rs != null; rs = rs.nextStep) {
            if (rs instanceof RouteStepAirwy) {
                RouteStepAirwy rsawy = (RouteStepAirwy) rs;
                appendGoodMessage ("Airway " + rsawy.ident + " {\n");
                for (Waypoint thiswp : rsawy.GetSelectedWaypoints ()) {
                    if (!thiswp.equals (prevwp)) {
                        appendGoodMessage ("  " + thiswp.MenuKey () + "\n");
                        analyzedRoute.addLast (thiswp);
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
                    analyzedRoute.addLast (thiswp);
                }
                prevwp = thiswp;
            }
        }

        /*
         * Edit out redundant points.
         */
        int nanal = analyzedRoute.size ();
        Waypoint[] ararray = new Waypoint[nanal];
        analyzedRoute.toArray (ararray);
        analyzedRoute.clear ();
        prevwp = null;
        for (int i = 0; i < nanal; i ++) {
            Waypoint thiswp = ararray[i];
            Waypoint nextwp = (i < nanal - 1) ? ararray[i+1] : null;
            if ((prevwp == null) || (nextwp == null) || (thiswp instanceof Waypoint.Navaid) || !inARow (prevwp, thiswp, nextwp)) {
                analyzedRoute.addLast (thiswp);
                prevwp = thiswp;
            }
        }

        /*
         * Enable tracking checkbox if remaining route has any waypoints.
         */
        if (analyzedRoute.isEmpty ()) {
            analyzedRoute = null;
        } else {
            buttonTrack.setEnabled (true);
        }

        appendGoodMessage ("done\n");

        showMessages ();
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
     * See if the given waypoints are all in a row.
     */
    private static boolean inARow (Waypoint wp1, Waypoint wp2, Waypoint wp3)
    {
        if ((wp1.lat == wp2.lat) && (wp1.lon == wp2.lon)) return true;
        if ((wp2.lat == wp3.lat) && (wp2.lon == wp3.lon)) return true;

        float tc12 = Lib.LatLonTC (wp1.lat, wp1.lon, wp2.lat, wp2.lon);
        float tc23 = Lib.LatLonTC (wp2.lat, wp2.lon, wp3.lat, wp3.lon);
        float tcdiff = Math.abs (tc12 - tc23);
        if (tcdiff > 180.0F) tcdiff = 360.0F - tcdiff;
        return tcdiff < 1.0F;
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Route";
    }

    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        displayOpen = true;
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
    public void ShutTrackingOff ()
    {
        trackingOn = false;
        buttonTrack.setEnabled (analyzedRoute != null);
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
         */
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("Save route");
        final EditText nameEntry = new EditText (wairToNow);
        adb.setView (nameEntry);
        adb.setPositiveButton ("Save", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                String name = nameEntry.getText ().toString ().trim ();
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
                                SaveButtonWriteFile (file);
                            }
                        });
                        adb2.setNegativeButton ("Cancel", null);
                        adb2.show ();
                    } else {

                        /*
                         * Ok to write to file.
                         */
                        SaveButtonWriteFile (file);
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
    private void SaveButtonWriteFile (File file)
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
    private void LoadButtonClicked ()
    {
        /*
         * Create listener for the file buttons.
         * When button clicked, dismiss menu and open file.
         */
        OnClickListener clicked = new OnClickListener () {
            @Override
            public void onClick (View view) {
                loadButtonMenu.dismiss ();
                loadButtonMenu = null;
                LoadButtonReadFile ((File) view.getTag ());
            }
        };

        /*
         * Make a list of buttons of existing files.
         */
        LinearLayout buttonList = new LinearLayout (wairToNow);
        buttonList.setOrientation (LinearLayout.VERTICAL);
        File[] filearray = new File (datadir).listFiles ();
        if (filearray != null) {
            for (File file : filearray) {
                String name = file.getName ();
                if (name.endsWith (".xml")) {
                    name = name.substring (0, name.length () - 4);
                    Button filebutton = new Button (wairToNow);
                    filebutton.setText (URLDecoder.decode (name));
                    wairToNow.SetTextSize (filebutton);
                    filebutton.setTag (file);
                    filebutton.setOnClickListener (clicked);
                    buttonList.addView (filebutton);
                }
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
            public void onClick (DialogInterface dialogInterface, int i) {
                loadButtonMenu = null;
            }
        });
        loadButtonMenu = adb.show ();
    }

    /**
     * A particular file was selected for loading.
     */
    public void LoadButtonReadFile (File file)
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
     * Get list of waypoints following the given waypoint along the analyzed route.
     * @param start = start after this point
     * @return null: start point not found; else: iterator for rest of waypoints
     */
    public Iterator<Waypoint> GetActiveWaypointsAfter (Waypoint start)
    {
        if (trackingOn) {
            for (Iterator<Waypoint> it = analyzedRoute.iterator (); it.hasNext ();) {
                Waypoint wp = it.next ();
                if (wp.equals (start)) return it;
            }
        }
        return null;
    }

    /**
     * Got an update from the GPS.
     * If we are tracking, change the course if we have progressed to the next waypoint.
     */
    public void SetGPSLocation (Location loc)
    {
        lastGPSAlt  = (float) loc.getAltitude ();
        lastGPSHdg  = loc.getBearing ();
        lastGPSLat  = (float) loc.getLatitude ();
        lastGPSLon  = (float) loc.getLongitude ();
        lastGPSpeed = loc.getSpeed ();

        if (trackingOn) CalcPointAhead ();
    }

    private void CalcPointAhead ()
    {
        pointAhead = null;
        float lastlat = startingLat;
        float lastlon = startingLon;

        // scan route to find closest point ahead of us
        float bestdist = 999999.0F;
        float bestllat = 0.0F;
        float bestllon = 0.0F;
        for (Waypoint wp : analyzedRoute) {
            float tcFromCurrentToPoint = Lib.LatLonTC (lastGPSLat, lastGPSLon, wp.lat, wp.lon);

            // lastGPSHdg = where nose is pointing, 1..360
            // tcFromCurrentToPoint = where point is, -180..+179
            // anglediff = difference, 0..359
            int anglediff = Math.round (lastGPSHdg - tcFromCurrentToPoint + 360.0F) % 360;

            // diff of 359 is same as diff of 001
            // diff of 181 is same as diff of 179
            if (anglediff > 180) anglediff = 360 - anglediff;

            // see if point is out front window somewhere from wingtip to wingtip
            if (anglediff < 90) {

                // ok, see if closest such point found so far
                float dist = Lib.LatLonDist (lastGPSLat, lastGPSLon, wp.lat, wp.lon);
                if (bestdist > dist) {
                    bestdist = dist;
                    bestllat = lastlat;
                    bestllon = lastlon;
                    pointAhead = wp;
                }
            }

            // save point's lat/lon in case next point is selected as the best
            lastlat = wp.lat;
            lastlon = wp.lon;
        }

        // draw course line from previous waypoint on route to the closest waypoint ahead
        wairToNow.chartView.SetCourseLine (bestllat, bestllon, pointAhead);

        // if route page open, update the display
        if (displayOpen) UpdateTrackingDisplay ();
    }

    /**
     * Called when tracking active and we received a GPS update.
     * It updates the message window with current positioning information.
     */
    private void UpdateTrackingDisplay ()
    {
        /*
         * Display all points ahead of us and the heading and distance between them.
         */
        textViewC.setText ("");
        textViewC.setTextColor (ChartView.courseColor);
        boolean started = false;
        float lastlat = lastGPSLat;
        float lastlon = lastGPSLon;
        float totnm   = 0.0F;
        String lastid = "@pp";
        boolean mphOption = wairToNow.optionsView.ktsMphOption.getAlt ();
        for (Waypoint wp : analyzedRoute) {
            if (wp == pointAhead) started = true;
            if (started) {
                float tc = Lib.LatLonTC   (lastlat, lastlon, wp.lat, wp.lon);
                float nm = Lib.LatLonDist (lastlat, lastlon, wp.lat, wp.lon);
                totnm += nm;
                String hdgstr  = wairToNow.optionsView.HdgString (tc, lastlat, lastlon, lastGPSAlt);
                hdgstr = hdgstr.replace (" ", "").replace ("Mag", "M").replace ("True", "T");
                String diststr = Lib.DistString (nm, mphOption).replace (" ", "");
                textViewC.append (lastid + "\u279F" + hdgstr + " " + diststr + "\u279F" + wp.ident + "\n");
                lastlat = wp.lat;
                lastlon = wp.lon;
                lastid  = wp.ident;
            }
        }

        /*
         * Display total distance and estimated time enroute to final point.
         */
        if (totnm > 0.0F) {
            String diststr = Lib.DistString (totnm, mphOption);
            String etestr = "";
            if (lastGPSpeed > 1.0F) {
                int seconds = Math.round (totnm / lastGPSpeed / Lib.KtPerMPS * 3600.0F);
                int minutes = seconds / 60;
                int hours   = minutes / 60;
                seconds %= 60;
                minutes %= 60;
                etestr = " ete: ";
                if (hours > 0) {
                    etestr += hours + ":" + Integer.toString (minutes + 100).substring (1);
                } else {
                    etestr += minutes;
                }
                etestr += ":" + Integer.toString (seconds + 100).substring (1);
            }
            textViewC.append ("total: " + diststr + etestr);
        }
    }
}
