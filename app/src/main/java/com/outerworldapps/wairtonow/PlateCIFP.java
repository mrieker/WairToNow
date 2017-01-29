//    Copyright (C) 2017, Mike Rieker, Beverly, MA USA
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
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

/**
 * Manage CIFP display on the IAP plates.
 */
public class PlateCIFP {
    public final static String TAG = "WairToNow";

    private final static int CLIMBFT_NM =   200;  // default climb rate
    private final static int DMEARCSTEPDEG = 10;  // DME arc steps (degrees)
    private final static int MINSPEEDKT =    60;  // minimum speed knots
    private final static int HOLDLEGSEC =    60;  // number of seconds for hold legs
    private final static int STDDEGPERSEC =   3;  // standard turn degrees per second
    private final static int MINFILLET  =     6;  // min degrees turn for fillet
    private final static int MAXFILLET  =   174;  // max degrees turn for fillet
    private final static int TURNWRNSEC =    10;  // number of seconds to warn for turn
    private final static int SHOWAPPIDSEC =   5;  // show approach id when selected
    private final static float RUNTNM   =  0.5F;  // legs shorter than this are runts

    private final static float stdcirdradsec = 180.0F / Mathf.PI / STDDEGPERSEC;

    private final static String CIFPSEG_FINAL = "~f~";
    private final static String CIFPSEG_MISSD = "~m~";
    private final static String CIFPSEG_REFNV = "~r~";
    private final static String CIFPSEG_RADAR = "(rv)";

    private static HashMap<String,Character> circlingcodes = makeCirclingCodes ();

    private AlertDialog selectMenu;
    private boolean   drawTextEnable;
    public  CIFPSegment cifpSelected;
    private CIFPStep[] cifpSteps;
    private float     aptmagvar;
    private float     bmpixpernm;
    private float     bmpixpersec;
    private float     cifpTextAscent, cifpTextHeight;
    private float     curposalt;    // altitude feet
    private float     curposhdg;    // heading degrees true
    private float     filletCenterX;
    private float     filletCenterY;
    private float     filletRadius;
    private float     filletStart;
    private float     filletSweep;
    private float     runtbmpix;
    private float[]   cifpDotBitmapXYs = new float[32];
    private HashMap<String,SelectButton> selectButtons;
    private int       acraftbmx;
    private int       acraftbmy;
    private int       cifpDotIndex;
    private int       curposbmx;
    private int       curposbmy;
    private int       currentStep;
    private int       mapIndex;     // index of first step of MAP in cifpSteps[]
    private int       selectButtonHeight;
    private int       smallestAcraftDist;
    private int       startCyans;   // start of cyans in cifpDotBitmapXYs[]
    private int       startGreens;  // start of greens in cifpDotBitmapXYs[]
    private long      cifpButtonDownAt = Long.MAX_VALUE;
    private long      lastGPSUpdateTime;
    private long      selectedTime;
    private Paint     cifpBGPaint = new Paint ();
    private Paint     cifpTxPaint = new Paint ();
    private Paint     dotsPaint   = new Paint ();
    private Paint     selectButtonPaint = new Paint ();
    private Path      cifpButtonPath = new Path ();
    private PlateView.IAPPlateImage plateView;
    private Rect      textBounds = new Rect ();
    private RectF     cifpButtonBounds = new RectF ();  // where CIFP dialog display button is on canvas
    private RectF     drawArcBox = new RectF ();
    private String    plateid;
    private String[]  cifpTextLines;
    private TreeMap<String,CIFPApproach> cifpApproaches;
    private WairToNow wairToNow;
    private Waypoint.Airport airport;

    private final static String[] columns_cp_misc = new String[] { "cp_appid", "cp_segid", "cp_legs" };

    // convert 3-letter circling approach type to 1-letter approach type
    // p149 5.10 approach route identifier
    // p150 Table 5-10 Circle-to-Land Procedures Identifier
    private static HashMap<String,Character> makeCirclingCodes ()
    {
        HashMap<String,Character> cc = new HashMap<> ();
        cc.put ("LBC", 'B');  // localizer back-course
        cc.put ("VDM", 'D');  // vor with required dme
        cc.put ("GLS", 'J');  // gnss landing system
        cc.put ("LOC", 'L');  // localizer only
        cc.put ("NDB", 'N');  // ndb only
        cc.put ("GPS", 'P');  // gps
        cc.put ("NDM", 'Q');  // ndb with required dme
        cc.put ("RNV", 'R');  // rnav/gps
        cc.put ("VOR", 'V');  // vor only
        cc.put ("SDF", 'U');  // sdf
        cc.put ("LDA", 'X');  // lda
        return cc;
    }

    public PlateCIFP (WairToNow wtn, PlateView.IAPPlateImage pv, Waypoint.Airport apt, String pi)
    {
        wairToNow = wtn;
        plateView = pv;
        airport   = apt;
        plateid   = pi;

        cifpBGPaint.setColor (Color.BLACK);
        cifpBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        cifpBGPaint.setStrokeWidth (30);

        cifpTxPaint.setColor (Color.YELLOW);
        cifpTxPaint.setStyle (Paint.Style.FILL);
        cifpTxPaint.setStrokeWidth (3);
        cifpTxPaint.setTextSize (wairToNow.textSize * 1.125F);
        cifpTxPaint.setTextAlign (Paint.Align.RIGHT);
        cifpTxPaint.setTypeface (Typeface.MONOSPACE);

        dotsPaint.setColor (Color.MAGENTA);
        dotsPaint.setPathEffect (new DashPathEffect (new float[] { 15, 30 }, 0));
        dotsPaint.setStyle (Paint.Style.STROKE);
        dotsPaint.setStrokeCap (Paint.Cap.ROUND);
        dotsPaint.setStrokeWidth (15);

        selectButtonPaint.setTextSize (wairToNow.textSize);

        cifpTxPaint.getTextBounds ("M", 0, 1, textBounds);
        cifpTextAscent = textBounds.height ();
        cifpTextHeight = cifpTxPaint.getTextSize ();

        aptmagvar = airport.GetMagVar (0.0F);
    }

    /**
     * Mouse pressed on the plate somewhere.
     */
    public void GotMouseDown (float x, float y)
    {
        // check for button in lower right corner of plate
        if (cifpButtonBounds.contains (x, y)) {
            cifpButtonDownAt = SystemClock.uptimeMillis ();
            WairToNow.wtnHandler.runDelayed (500, new Runnable () {
                @Override
                public void run ()
                {
                    if (SystemClock.uptimeMillis () - 500 >= cifpButtonDownAt) {
                        CIFPButtonClicked (false);
                    }
                }
            });
        }

        // check for one of the select buttons
        // they can be clicked on the approach id string (ILS, LOC, VOR, etc)
        if (selectButtons != null) {
            // loop through all the shown buttons
            int nbuttons = 0;
            for (SelectButton sb : selectButtons.values ()) {
                // assume it is not being clicked
                sb.clicked = null;
                // there may be more than one approach type on a button
                for (String apptype : sb.appBounds.keySet ()) {
                    // see if that type was clicked
                    // if only one type for the button, the bounds is for the whole button
                    RectF bounds = sb.appBounds.get (apptype);
                    if (bounds.contains (x, y)) {
                        // remember a button was clicked
                        // and which approach type was clicked
                        nbuttons ++;
                        sb.clicked = apptype;
                        // only one approach type can be clicked on a button
                        // as the approach types never overlap for a single button
                        break;
                    }
                }
            }
            // see if a select button was clicked
            switch (nbuttons){
                case 0: break;
                // just one, so it is ok to select that approach
                case 1: {
                    for (SelectButton sb : selectButtons.values ()) {
                        if (sb.clicked != null) {
                            CIFPSegmentSelected (sb.segments.get (sb.clicked));
                            break;
                        }
                    }
                    break;
                }
                // multiple (they overlap), bring up menu
                default: {
                    displaySelectionMenu ();
                    break;
                }
            }
        }
    }

    /**
     * Mouse released on the plate.
     */
    public void GotMouseUp ()
    {
        CIFPButtonClicked (true);
    }

    /**
     * Get the navaid for the approach,
     * eg, if it is a VOR approach,
     * get the VOR the approach uses
     */
    public Waypoint getRefNavWaypt ()
    {
        // if particular transition selected, use that approach type's ref navaid
        // (they should all be the same anyway)
        if (cifpSelected != null) return cifpSelected.approach.refnavwp;

        // otherwise, make sure we have CIFP database for this plate loaded
        if (cifpApproaches == null) GetCIFPSegments ();
        if (cifpApproaches.isEmpty ()) return null;

        // return navaid for the first approach type (they should all be the same anyway)
        return cifpApproaches.values ().iterator ().next ().refnavwp;
    }

    /**
     * Get selected approach long name.
     *   "KBVY LOC 16/(rv)"
     */
    public String getApproachName ()
    {
        if (cifpSelected == null) return null;
        return cifpSelected.getName ();
    }

    /**
     * Get selected approach/segment FAF waypoint
     */
    public Waypoint getFafWaypt ()
    {
        if (cifpSelected == null) return null;
        CIFPSegment finalseg = cifpSelected.approach.finalseg;
        return finalseg.getFafWaypt ();
    }

    /**
     * Get glideslope elevation over FAF.
     */
    public float getFafGSElev ()
    {
        if (cifpSelected == null) return Float.NaN;
        CIFPSegment finalseg = cifpSelected.approach.finalseg;
        CIFPLeg fafleg = finalseg.getFafLeg ();
        if (fafleg == null) return Float.NaN;
        return fafleg.getAltitude ();
    }

    /**
     * Get selected approach/segment runway waypoint.
     */
    public Waypoint getRwyWaypt ()
    {
        if (cifpSelected == null) return null;
        CIFPSegment finalseg = cifpSelected.approach.finalseg;
        return finalseg.getRwyWaypt ();
    }

    /**
     * Get glideslope elevation over runway waypoint.
     */
    public float getRwyGSElev ()
    {
        if (cifpSelected == null) return Float.NaN;
        CIFPSegment finalseg = cifpSelected.approach.finalseg;
        CIFPLeg rwyleg = finalseg.legs[finalseg.legs.length-1];
        return rwyleg.getAltitude ();
    }

    /**
     * Get current segment info so VirtNavView can maintain a nav dial.
     */
    // true course degrees
    public float getVirtNavOBSTrue ()
    {
        if (cifpSteps == null) return Float.NaN;

        // normally use OBS as given by current step
        int di = currentStep;
        if (di < 0) di = 0;
        CIFPStep cs = cifpSteps[di];

        // but if we are in the fillet at the end,
        // use next step's OBS setting so pilot can see where to turn to
        // skip over runt steps as they don't generate fillets and they
        // probably don't know what their heading is either
        if (cs.numSecToFillet () <= 0) {
            while (++ di < cifpSteps.length) {
                CIFPStep ns = cifpSteps[di];
                if (!ns.isRuntStep ()) {
                    cs = ns;
                    break;
                }
            }
        }

        // get the OBS setting for that step
        return cs.getVNOBSTrue ();
    }

    // goes in small white text box above dial
    public String getVirtNavTextLine ()
    {
        if (cifpTextLines == null) return null;
        return cifpTextLines[currentStep+1];
    }

    // used for needle deflection and DME distance and DME waypoint
    public String getVirtNavWaypoint (LatLon ll)
    {
        if (cifpSteps == null) return null;
        CIFPStep cs = (currentStep < 0) ? cifpSteps[0] : cifpSteps[currentStep];
        return cs.getVNWaypoint (ll);
    }

    /**
     * A new GPS location was received, update state.
     */
    public void SetGPSLocation ()
    {
        // make sure we have a segment selected for display
        // also we might get double calls (via WaypointView and VirtNavView),
        // so reject redundant GPS updates
        if ((cifpSelected != null) && (lastGPSUpdateTime < wairToNow.currentGPSTime)) {
            try {
                UpdateState ();
            } catch (Exception e) {
                String msg = "error updating CIFP " + airport.ident + "." +
                        cifpSelected.approach.appid + "." + cifpSelected.segid;
                Log.e (TAG, msg, e);
                errorMessage (msg, e);
                cifpSelected = null;
            }
        }
    }

    // update state regardless of whether plate is visible or not
    // in case user comes back to plate later or the state is being
    // used by VirtNavView without viewing the plate itself
    private void UpdateState () throws Exception
    {
        lastGPSUpdateTime = wairToNow.currentGPSTime;

        // compute the path in dots
        int nsteps = cifpSteps.length;

        curposalt = wairToNow.currentGPSAlt / Lib.FtPerM;
        curposhdg = wairToNow.currentGPSHdg;
        curposbmx = acraftbmx = plateView.LatLon2BitmapX (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
        curposbmy = acraftbmy = plateView.LatLon2BitmapY (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
        float spd = wairToNow.currentGPSSpd;

        // we need a minimum speed so we can draw dots
        if (spd < MINSPEEDKT / 3600.0F * Lib.MPerNM) {
            spd = MINSPEEDKT / 3600.0F * Lib.MPerNM;
        }

        // how many bitmap pixels we will cross in a second at our current speed
        bmpixpersec = bmpixpernm / Lib.MPerNM * spd;

        // if first time through, save current position as starting point
        // this sets the starting position where the initial line is drawn from
        if (currentStep < 0) {
            CIFPStep step0 = cifpSteps[0];
            step0.begptalt = curposalt;
            step0.begpthdg = curposhdg;
            step0.begptbmx = curposbmx;
            step0.begptbmy = curposbmy;
            currentStep = 0;
            Log.d (TAG, "PlateCIFP*: currentStep=" + currentStep + " <" + cifpSteps[currentStep].getTextLine () + ">");
        }

        // gather text strings and locations of all dots
        cifpDotIndex = 0;
        startGreens  = Integer.MAX_VALUE;
        startCyans   = Integer.MAX_VALUE;
        for (int i = currentStep; i < nsteps; i ++) {
            CIFPStep step = cifpSteps[i];

            // save index of where each color starts
            // (magenta always starts at zero)
            if ((i > currentStep) && (startGreens == Integer.MAX_VALUE)) {
                startGreens = cifpDotIndex;
            }
            if ((i > currentStep) && (i >= mapIndex) && (startCyans == Integer.MAX_VALUE)) {
                startCyans = cifpDotIndex;
            }

            // current step starts where it started last time,
            // ie, once a step becomes current, its start point doesn't change.
            // subsequent steps start where the previous step ended.
            // this is where the airplane is at the beginning of the step,
            // assuming the previous step was flown perfectly.
            if (i == currentStep) {
                curposalt = step.begptalt;
                curposhdg = step.begpthdg;
                curposbmx = step.begptbmx;
                curposbmy = step.begptbmy;
            } else {
                step.begptalt = curposalt;
                step.begpthdg = curposhdg;
                step.begptbmx = curposbmx;
                step.begptbmy = curposbmy;
            }

            // get text string for text box
            cifpTextLines[i+1] = step.getTextLine ();

            // compute dots that make up step and put in array
            // compute alt,hdg,lat,lon at end of this step
            smallestAcraftDist = Integer.MAX_VALUE;
            step.drawStepDots ();
            step.acrftdist = smallestAcraftDist;

            // save the values saying where the step ended.
            // this is where the airplane is at the end of the step
            // assuming the step was flown perfectly.
            step.endptalt = curposalt;
            step.endpthdg = curposhdg;
            step.endptbmx = curposbmx;
            step.endptbmy = curposbmy;
        }

        // draw fillet arc showing turn at end of current step to next step.
        // the next step actually determines the shape of the fillet arc,
        // but the fillet is considered part of the current step so it stays magenta.
        // it's possible that the next step is a runt, in which case we ignore it and
        // use the one after that.
        filletRadius = 0.0F;
        for (int i = currentStep; ++ i < nsteps;) {
            CIFPStep ns = cifpSteps[i];
            if (!ns.isRuntStep ()) {
                ns.drawFillet ();
                break;
            }
        }

        // advance current step if aircraft is closer to dots in next step
        // than it is to dots in the current step.
        // but we must also skip any steps that are runts,
        // as we wouldn't be able to see any difference
        // in distance away from them and would get stuck.
        int csacrftdist = cifpSteps[currentStep].acrftdist;
        for (int i = currentStep; ++ i < nsteps;) {
            CIFPStep ns = cifpSteps[i];
            if (!ns.isRuntStep ()) {
                int nsacrftdist = ns.acrftdist;
                if (nsacrftdist <= csacrftdist) {
                    currentStep = i;
                    Log.d (TAG, "PlateCIFP*: currentStep=" + i + " <" + ns.getTextLine () + ">");
                }
                break;
            }
        }
    }

    /**
     * Display a pop-up menu with a list of all approach transitions available.
     */
    private void displaySelectionMenu ()
    {
        // set up a list for segment buttons
        LinearLayout llv = new LinearLayout (wairToNow);
        llv.setOrientation (LinearLayout.VERTICAL);

        // put it in a dialog box
        ScrollView sv = new ScrollView (wairToNow);
        sv.addView (llv);
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("Select Starting Point");
        adb.setView (sv);

        // if user clicks Cancel, just close dialog
        adb.setNegativeButton ("Cancel", null);

        // display dialog box
        selectMenu = adb.show ();

        // set up button listener to select segment when clicked
        View.OnClickListener buttonClicked = new View.OnClickListener () {
            @Override
            public void onClick (View view)
            {
                CIFPSegmentSelected ((CIFPSegment) view.getTag ());
            }
        };

        // make a button for each transition segment of each approach
        for (CIFPApproach approach : cifpApproaches.values ()) {
            if (cifpApproaches.size () > 1) {
                TextView apptext = new TextView (wairToNow);
                wairToNow.SetTextSize (apptext);
                apptext.setText (approach.type);
                llv.addView (apptext);
            }
            for (CIFPSegment segment : approach.transsegs.values ()) {
                Button button = new Button (wairToNow);
                wairToNow.SetTextSize (button);
                button.setText (segment.segid);
                button.setOnClickListener (buttonClicked);
                button.setTag (segment);
                llv.addView (button);
            }
        }
    }

    /**
     * Build list of buttons that can be clicked on to select which
     * approach and segment will be tracked.
     * The buttons apprear on the plate at the location of the fix
     * that the corresponding segment starts at.  The (rv) transition
     * appears over the airport itself.
     */
    private void displaySelectionButtons ()
    {
        selectButtons = new HashMap<> ();

        // go through all transition segments associated with this plate
        for (CIFPApproach app : cifpApproaches.values ()) {
            selectButtonPaint.getTextBounds (app.type, 0, app.type.length (), textBounds);
            app.typeWidth = textBounds.width ();

            for (CIFPSegment seg : app.transsegs.values ()) {

                // see if we already have a select button for that transition waypoint name
                SelectButton sb = selectButtons.get (seg.segid);
                if (sb == null) {
                    Waypoint waypt = null;
                    if (seg.segid.equals (CIFPSEG_RADAR)) {

                        // for the (rv) segment, put button on the airport
                        waypt = airport;
                    } else {

                        // named segment, look for waypoint to see where to put button
                        LinkedList<Waypoint> waypts = Waypoint.GetWaypointsByIdent (seg.segid);
                        float bestdist = 200.0F;
                        for (Waypoint wp : waypts) {
                            float dist = Lib.LatLonDist (wp.lat, wp.lon, airport.lat, airport.lon);
                            if (bestdist > dist) {
                                bestdist = dist;
                                waypt = wp;
                            }
                        }

                        // skip the segment if no such waypoint
                        if (waypt == null) continue;
                    }

                    // waypoint found, create a select button
                    sb = new SelectButton ();
                    selectButtons.put (seg.segid, sb);
                    sb.segid = seg.segid;
                    sb.bmpx  = plateView.LatLon2BitmapX (waypt.lat, waypt.lon);
                    sb.bmpy  = plateView.LatLon2BitmapY (waypt.lat, waypt.lon);

                    selectButtonPaint.getTextBounds (seg.segid, 0, seg.segid.length (), textBounds);
                    selectButtonHeight = textBounds.height ();
                    sb.idwidth = textBounds.width ();
                }

                // add this approach to the button
                // there may be more than one, eg, ILS or LOC
                sb.segments.put (app.type, seg);
                sb.appBounds.put (app.type, new RectF ());
            }
        }
        plateView.invalidate ();
    }

    /**
     * One of these per transition segment (including (rv) transition).
     * They get displayed at the corresponding waypoint on the plate
     * so the pilot can select which waypoint to start the approach at.
     */
    private class SelectButton {
        public HashMap<String,RectF> appBounds = new HashMap<> ();  // indexed by app.type
        public int bmpx, bmpy;  // bitmap pixel where button goes (waypoint location)
        public int idwidth;     // width of segid string (canvas pixels)
        public String clicked;
        public String segid;    // "(rv)", "LADTI", etc
        public TreeMap<String,CIFPSegment> segments = new TreeMap<> ();  // indexed by app.type

        /**
         * Draw text strings for the waypoint that act as buttons
         * to select that waypoint as the starting point for the
         * approach.
         *
         * If there is just one approach for a given waypoint, just show
         * the waypoint name.  If there is more than one approach (such
         * as ILS and LOC), show the approach types as well as the
         * waypoint name.
         */
        public void drawButton (Canvas canvas)
        {
            // get canvas X,Y where the waypoint is located
            float canx = plateView.BitmapX2CanvasX (bmpx);
            float cany = plateView.BitmapY2CanvasY (bmpy);

            if (segments.size () == 1) {
                String apptype = segments.keySet ().iterator ().next ();

                // draw black backgound rectangle to draw text on
                int totw = idwidth + selectButtonHeight;
                canvas.drawRect (canx, cany - selectButtonHeight, canx + totw, cany, cifpBGPaint);

                // draw waypoint id string on plate near its location
                selectButtonPaint.setColor (Color.GREEN);
                canx += selectButtonHeight / 2.0F;
                canvas.drawText (segid, canx, cany, selectButtonPaint);

                // make the id string clickable
                RectF bounds = appBounds.get (apptype);
                bounds.set (canx, cany - selectButtonHeight, canx + idwidth, cany);
            } else {

                // draw black background rectangle to draw text on
                int totw = idwidth + selectButtonHeight;
                for (String apptype : segments.keySet ()) {
                    CIFPSegment seg = segments.get (apptype);
                    totw += seg.approach.typeWidth + selectButtonHeight;
                }
                canvas.drawRect (canx, cany - selectButtonHeight, canx + totw, cany, cifpBGPaint);

                // draw waypoint id string on plate near its location
                selectButtonPaint.setColor (Color.CYAN);
                canx += selectButtonHeight / 2.0F;
                canvas.drawText (segid, canx, cany, selectButtonPaint);
                canx += idwidth + selectButtonHeight;

                // draw app id for each approach following the waypoint id
                // remember the boundary of the text strings so they can be clicked
                selectButtonPaint.setColor (Color.GREEN);
                for (String apptype : segments.keySet ()) {
                    int w = segments.get (apptype).approach.typeWidth;
                    RectF bounds = appBounds.get (apptype);
                    bounds.set (canx, cany - selectButtonHeight, canx + w, cany);
                    canvas.drawText (apptype, canx, cany, selectButtonPaint);
                    canx += w + selectButtonHeight;
                }
            }
        }
    }

    /**
     * Draw dots of the CIFP path on the plate.
     * Draw CIFP strings in lower right corner.
     * If no CIFP selected, draw a little button to open menu.
     */
    public void DrawCIFP (Canvas canvas)
    {
        if (selectButtons != null) {
            for (String segid : selectButtons.keySet ()) {
                SelectButton sb = selectButtons.get (segid);
                sb.drawButton (canvas);
            }
        }

        // if approach is selected, draw the path in dots
        if (cifpSelected != null) {

            // draw dots for steps in reverse course order
            // so current step's dots end up on top
            for (int i = cifpDotIndex; i > 0;) {
                float x0 = cifpDotBitmapXYs[--i];
                dotsPaint.setColor (
                        (i > startCyans) ? Color.CYAN :
                                (i > startGreens) ? Color.GREEN :
                                        Color.MAGENTA
                );
                if (!Float.isNaN (x0)) {
                    float y0 = cifpDotBitmapXYs[--i];
                    float x1 = cifpDotBitmapXYs[--i];
                    float y1 = cifpDotBitmapXYs[--i];
                    x0 = plateView.BitmapX2CanvasX (Math.round (x0));
                    y0 = plateView.BitmapY2CanvasY (Math.round (y0));
                    x1 = plateView.BitmapX2CanvasX (Math.round (x1));
                    y1 = plateView.BitmapY2CanvasY (Math.round (y1));
                    canvas.drawLine (x0, y0, x1, y1, dotsPaint);
                } else {
                    float radius = cifpDotBitmapXYs[--i];
                    float centx  = cifpDotBitmapXYs[--i];
                    float centy  = cifpDotBitmapXYs[--i];
                    float start  = cifpDotBitmapXYs[--i];
                    float sweep  = cifpDotBitmapXYs[--i];
                    drawArcBox.set (
                            plateView.BitmapX2CanvasX (Math.round (centx - radius)),
                            plateView.BitmapY2CanvasY (Math.round (centy - radius)),
                            plateView.BitmapX2CanvasX (Math.round (centx + radius)),
                            plateView.BitmapY2CanvasY (Math.round (centy + radius)));
                    canvas.drawArc (drawArcBox, start, sweep, false, dotsPaint);
                }
            }

            // draw fillet arc in magenta
            if (filletRadius > 0.0F) {

                // make bounding box for the circle
                float r = filletRadius;
                float x = filletCenterX;
                float y = filletCenterY;
                drawArcBox.set (
                        plateView.BitmapX2CanvasX (Math.round (x - r)),
                        plateView.BitmapY2CanvasY (Math.round (y - r)),
                        plateView.BitmapX2CanvasX (Math.round (x + r)),
                        plateView.BitmapY2CanvasY (Math.round (y + r)));

                // draw the arc
                canvas.drawArc (drawArcBox, filletStart, filletSweep, false, dotsPaint);
            }
        }

        int canvasHeight = plateView.getHeight ();
        int canvasWidth  = plateView.getWidth ();

        // see if user wants text drawn
        // also be sure text has been initialized by UpdateStep()
        if (!drawTextEnable || (currentStep < 0)) {

            // user doesn't want any CIFP text, draw a little button instead
            float ts = wairToNow.textSize;
            cifpButtonBounds.left   = (int) (canvasWidth - ts * 2);
            cifpButtonBounds.right  = canvasWidth;
            cifpButtonBounds.top    = (int) (canvasHeight - ts * 2);
            cifpButtonBounds.bottom = canvasHeight;

            cifpButtonPath.rewind ();
            cifpButtonPath.moveTo (canvasWidth - ts, canvasHeight - ts * 2);
            cifpButtonPath.lineTo (canvasWidth - ts * 2, canvasHeight - ts * 2);
            cifpButtonPath.lineTo (canvasWidth - ts * 2, canvasHeight - ts);

            cifpTxPaint.setColor (Color.YELLOW);
            canvas.drawPath (cifpButtonPath, cifpBGPaint);
            canvas.drawPath (cifpButtonPath, cifpTxPaint);
        } else {

            // make up a first line showing distance and time to end of current step
            // or if coming up to a turn, warn of impending turn
            CIFPStep curstep = cifpSteps[currentStep];
            int   sectoturn  = curstep.numSecToFillet ();
            String firstline;
            if ((curstep.filetdir != null) && (sectoturn <= TURNWRNSEC)) {
                // near or in the turn, display message saying to turn
                int maghdg = ((Math.round (curstep.filetftc + aptmagvar) + 719) % 360) + 1;
                if (sectoturn > 0) {
                    firstline = String.format ("%s %03d\u00B0 in %d sec", curstep.filetdir, maghdg, sectoturn);
                } else {
                    firstline = String.format ("%s %03d\u00B0 now", curstep.filetdir, maghdg);
                }
            } else if (SystemClock.uptimeMillis () > selectedTime + SHOWAPPIDSEC * 1000) {
                // it will be a while to the turn, say how far and how long to waypoint
                float dist = Mathf.hypot (curstep.endptbmx - acraftbmx,
                        curstep.endptbmy - acraftbmy) / bmpixpernm;
                firstline = String.format ("%.1f nm", dist);
                if (wairToNow.currentGPSSpd > WairToNow.gpsMinSpeedMPS) {
                    int sec = Math.round (dist * Lib.MPerNM / wairToNow.currentGPSSpd);
                    firstline += String.format ("  %02d:%02d", sec / 60, sec % 60);
                }
            } else {
                // just selected approach, show its name for a few seconds
                firstline = cifpSelected.getName ();
            }
            cifpTextLines[currentStep] = firstline;

            // see how big the text box needs to be
            int textwidth = 0;
            int cifpx = canvasWidth  - (int) Mathf.ceil (cifpTextHeight / 2);
            int cifpy = canvasHeight - (int) Mathf.ceil (cifpTextHeight / 2);
            cifpButtonBounds.right = cifpx;
            cifpButtonBounds.bottom = cifpy;
            int nsteps = cifpSteps.length;
            for (int i = currentStep; i <= nsteps; i ++) {
                String line = cifpTextLines[i];
                int nchars = line.length ();
                cifpTxPaint.getTextBounds (line, 0, nchars, textBounds);
                int linewidth = textBounds.width ();
                if (textwidth < linewidth) {
                    textwidth = linewidth;
                }
                cifpy -= (int) Mathf.ceil (cifpTextHeight);
            }
            cifpButtonBounds.left = cifpx - (int) Mathf.ceil (textwidth);
            cifpButtonBounds.top  = cifpy - cifpTextAscent + cifpTextHeight;

            // draw black background rectangle where text goes
            canvas.drawRect (cifpButtonBounds, cifpBGPaint);

            // draw text on that background
            for (int i = currentStep; i <= nsteps; i ++) {
                if (i <= currentStep + 1) cifpTxPaint.setColor (Color.MAGENTA);
                  else if (i <= mapIndex) cifpTxPaint.setColor (Color.GREEN);
                                     else cifpTxPaint.setColor (Color.CYAN);
                cifpy += (int) Mathf.ceil (cifpTextHeight);
                String line = cifpTextLines[i];
                canvas.drawText (line, cifpx, cifpy, cifpTxPaint);
            }
        }
    }

    /**
     * Lower right corner of plate view was clicked and so we bring up the CIFP selection alert.
     *
     * Some approach already selected (cifpSelected != null):
     *  - short click: toggle text shown / hidden
     *  -  long click: prompt to clear (deselect) approach
     * Buttons being shown (selectButtons != null):
     *  - short click: remove buttons
     *  -  long click: show menu
     * Nothing (both cifpSelected and selectButtons == null):
     *  - short click: show buttons
     *  -  long click: show menu
     */
    @SuppressLint("SetTextI18n")
    private void CIFPButtonClicked (boolean mouseUp)
    {
        if (cifpButtonDownAt == Long.MAX_VALUE) return;
        cifpButtonDownAt = Long.MAX_VALUE;

        // mouseUp = true: short click
        // mouseUp = false: long clock

        // check for approach already selected
        if (cifpSelected != null) {
            if (mouseUp) {

                // short click: toggle text display
                drawTextEnable = !drawTextEnable;
                plateView.invalidate ();
            } else {

                // long click: prompt to clear approach selection
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Confirm");
                adb.setMessage ("Discontinue approach?");
                adb.setPositiveButton ("DISCONTINUE", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialogInterface, int i)
                    {
                        cifpSelected   = null;
                        drawTextEnable = false;
                        cifpTextLines  = null;
                        mapIndex       = Integer.MAX_VALUE;
                        currentStep    = -1;
                    }
                });
                adb.setNegativeButton ("KEEP IT", null);
                adb.show ();
            }
            return;
        }

        // check for approach selection buttons already showing
        if (selectButtons != null) {

            // always clear buttons
            selectButtons = null;
            plateView.invalidate ();

            // if long click, display selection menu
            if (!mouseUp) displaySelectionMenu ();
            return;
        }

        // first time through, scan the database
        if (bmpixpernm == 0.0F) {
            bmpixpernm = plateView.LatLon2BitmapY (airport.lat, airport.lon) -
                    plateView.LatLon2BitmapY (airport.lat + 1.0F / Lib.NMPerDeg, airport.lon);
            if (bmpixpernm == 0.0F) {
                errorMessage ("", "IAP plate not geo-referenced");
                return;
            }
            runtbmpix = bmpixpernm * RUNTNM;
        }
        if (cifpApproaches == null) GetCIFPSegments ();
        if (cifpApproaches.isEmpty ()) {
            errorMessage ("", "No CIFP information available");
            return;
        }

        // nothing showing
        if (mouseUp) {

            // short click: show buttons
            displaySelectionButtons ();
        } else {

            // long click: show menu
            displaySelectionMenu ();
        }
    }

    /**
     * Display the given transition segment
     */
    private void CIFPSegmentSelected (CIFPSegment segment)
    {
        selectButtons = null;
        if (selectMenu != null) {
            selectMenu.dismiss ();
            selectMenu = null;
        }
        try {
            cifpSelected = segment;

            // assume they want the text shown
            drawTextEnable = true;

            // get steps from transition, final and missed segments
            LinkedList<CIFPStep> steps = new LinkedList<> ();
            CIFPLeg prevleg = cifpSelected.getSteps (steps, null);
            prevleg = cifpSelected.approach.finalseg.getSteps (steps, prevleg);
            mapIndex = steps.size ();
            cifpSelected.approach.missedseg.getSteps (steps, prevleg);

            // make array from the list
            cifpSteps = steps.toArray (new CIFPStep[steps.size()]);
            for (int di = cifpSteps.length; -- di >= 0;) cifpSteps[di].drawIndex = di;

            // this array will have the displayable strings
            cifpTextLines = new String[cifpSteps.length+1];

            // need to initialize first position
            currentStep = -1;

            // remember when selection was made
            selectedTime = SystemClock.uptimeMillis ();

            // fill in initial state with whatever location was last received from GPS
            UpdateState ();
        } catch (Exception e) {
            errorMessage ("error selecting CIFP " + airport.ident + "." +
                    cifpSelected.approach.appid + "." + cifpSelected.segid, e);
            cifpSelected   = null;
            drawTextEnable = false;
            cifpSteps      = null;
            cifpTextLines  = null;
        }
    }

    /**
     * Search database for CIFP segments for this approach.
     * There might be several named transition segments that the pilot can directly select.
     * Then there is a ~f~ final approach segment and a ~m~ missed approach segment.
     * We might also get several separate approaches, like and ILS and a LOC for a
     * plate named ILS OR LOC.
     * Output:
     *  cifpApproaches = filled in
     */
    private void GetCIFPSegments ()
    {
        cifpApproaches = new TreeMap<> ();

        // Parse full approach plate name
        String fullname = plateid.replace ("IAP-", "").replace ("-", " -").replace ("DME", " DME");
        fullname = fullname.replace ("ILSLOC", "ILS LOC").replace ("VORTACAN", "VOR TACAN");

        boolean sawdme = false;  // these get set if corresponding keyword seen
        boolean sawgls = false;
        boolean sawgps = false;
        boolean sawils = false;
        boolean sawlbc = false;
        boolean sawlda = false;
        boolean sawloc = false;
        boolean sawndb = false;
        boolean sawrnv = false;
        boolean sawsdf = false;
        boolean sawvor = false;

        String runway = null;  // either twodigitsletter string or -letter string
        String variant = "";   // either null or 'Y', 'Z' etc

        String[] fullwords = fullname.split (" ");
        for (String word : fullwords) {
            if (word.equals ("")) continue;

            // an actual runway number 01..36 maybe followed by C, L, R
            if ((word.charAt (0) >= '0') && (word.charAt (0) <= '3')) {
                runway = word;
                continue;
            }

            // an hyphen followed by a letter such as -A, -B, -C is circling-only approach
            if (word.charAt (0) == '-') {
                runway = word;
                continue;
            }

            // a single letter such as W, X, Y, Z is an approach variant
            if ((word.length () == 1) && (word.charAt (0) <= 'Z') && (word.charAt (0) >= 'S')) {
                variant = word;
                continue;
            }

            // 'BC' is for localizer back-course
            if (word.equals ("BC")) {
                sawlbc = sawloc;
                sawloc = false;
                continue;
            }

            // all others simple keyword match
            if (word.equals ("DME"))   sawdme = true;
            if (word.equals ("GLS"))   sawgls = true;
            if (word.equals ("GPS"))   sawgps = true;
            if (word.equals ("ILS"))   sawils = true;
            if (word.equals ("LDA"))   sawlda = true;
            if (word.equals ("LOC"))   sawloc = true;
            if (word.equals ("NDB"))   sawndb = true;
            if (word.equals ("SDF"))   sawsdf = true;
            if (word.equals ("VOR"))   sawvor = true;
            if (word.equals ("(GPS)")) sawrnv = true;
        }

        // scan the database for this airport and hopefully find airport's icaoid
        // then gather up all CIFP segments for things that seem to match plate name
        int expdate28 = MaintView.GetPlatesExpDate (airport.state);
        String dbname = "plates_" + expdate28 + ".db";
        try {
            SQLiteDBs sqldb = SQLiteDBs.open (dbname);
            Cursor result1 = sqldb.query (
                    "iapcifps", columns_cp_misc,
                    "cp_icaoid=?", new String[] { airport.ident },
                    null, null, null, null);
            try {
                if (result1.moveToFirst ()) do {
                    String appid = result1.getString (0);
                    String segid = result1.getString (1);

                    try {
                        String apptype = null;
                        char appcode;
                        boolean rwmatch;

                        // if circling-only approach, convert 3-letter code to 1-letter code
                        // also see if -A in displayed plate id matches -A in database record approach id
                        if (runway.startsWith ("-")) {
                            String appid3 = appid.substring (0, 3);
                            appcode = 0;
                            rwmatch = circlingcodes.containsKey (appid3);
                            if (rwmatch) {
                                appcode = circlingcodes.get (appid3);
                                rwmatch = appid.substring (3).equals (runway);
                            }
                        } else {
                            // appid : <1-letter-code><runway><variant>
                            //   1-letter-code = eg, 'D' for vor/dme
                            //   runway = two digits
                            //   variant = 'Z', 'Y', etc or missing
                            //     (variant might be preceded by an hyphen)

                            // straight-in capable, get 1-letter code
                            appcode = appid.charAt (0);

                            // also see if runway and variant match
                            String appidnd = appid.replace ("-", "");
                            rwmatch = appidnd.substring (1).equals (runway + variant);
                        }

                        if (rwmatch) {
                            // see if the approach type matches
                            // p150 Table 5-9 Runway Dependent Procedure Ident
                            switch (appcode) {
                                case 'B': {  // localizer back course
                                    if (sawlbc) apptype = "LOC-BC";
                                    break;
                                }
                                case 'D': {  // vor with dme required
                                    if (sawvor && sawdme) apptype = "VOR/DME";
                                    break;
                                }
                                case 'I': {  // ils
                                    if (sawils) apptype = "ILS";
                                    break;
                                }
                                case 'J': {  // gnss landing system
                                    if (sawgls) apptype = "GLS";
                                    break;
                                }
                                case 'L': {  // localizer
                                    if (sawloc) apptype = "LOC";
                                    break;
                                }
                                case 'N': {  // ndb
                                    if (sawndb) apptype = "NDB";
                                    break;
                                }
                                case 'P': {  // gps
                                    if (sawgps) apptype = "GPS";
                                    break;
                                }
                                case 'Q': {  // ndb with dme required
                                    if (sawndb && sawdme) apptype = "NDB/DME";
                                    break;
                                }
                                case 'R': {  // rnav (gps)
                                    if (sawrnv) apptype = "RNAV/GPS";
                                    break;
                                }
                                case 'S':    // vor has dme but not required
                                case 'V': {  // vor
                                    if (sawvor) apptype = "VOR";
                                    break;
                                }
                                case 'U': {  // sdf
                                    if (sawsdf) apptype = "SDF";
                                    break;
                                }
                                case 'X': {  // lda
                                    if (sawlda) apptype = "LDA";
                                    break;
                                }
                            }
                        }

                        // if it looks like approach type, runway and variant match,
                        // add segment to list of matching segments.
                        // note that we may get more than one approach to match this
                        // plate, such as if the plate is 'ILS OR LOC ...', we get
                        // ILS and LOC segments matching.
                        if (apptype != null) {

                            // see if we already know about approach this segment is for
                            // if not, create new approach object
                            CIFPApproach cifpapp = cifpApproaches.get (appid);
                            if (cifpapp == null) {
                                StringBuilder name = new StringBuilder ();
                                name.append (apptype);
                                if (!variant.equals ("")) {
                                    name.append (' ');
                                    name.append (variant);
                                }
                                if (!runway.startsWith ("-")) {
                                    name.append (' ');
                                }
                                name.append (runway);

                                cifpapp = new CIFPApproach ();
                                cifpapp.appid = appid;
                                cifpapp.name = name.toString ();
                                cifpapp.type = apptype;
                                cifpApproaches.put (appid, cifpapp);
                            }

                            if (segid.equals (CIFPSEG_REFNV)) {

                                // these aren't really segments, but is the navaid for the approach
                                // it tells us what localizer, ndb, vor etc the approach is based on
                                cifpapp.refnavwp = plateView.FindWaypoint (result1.getString (2));
                            } else {

                                // real segments, get array of leg strings
                                String[] semis = result1.getString (2).split (";");

                                // make sure segment has at least one leg
                                int nlegs = semis.length;
                                if (nlegs <= 0) continue;

                                // create segment object and associated leg objects
                                CIFPSegment cifpseg = new CIFPSegment ();
                                cifpseg.approach    = cifpapp;
                                cifpseg.segid       = segid;
                                cifpseg.legs        = new CIFPLeg[nlegs];
                                for (int i = 0; i < nlegs; i ++) {
                                    makeLeg (cifpseg, i, semis[i]);
                                }

                                // add segment to approach
                                switch (segid) {
                                    case CIFPSEG_FINAL: {
                                        cifpapp.finalseg = cifpseg;
                                        break;
                                    }
                                    case CIFPSEG_MISSD: {
                                        cifpapp.missedseg = cifpseg;
                                        break;
                                    }
                                    default: {
                                        cifpapp.transsegs.put (segid, cifpseg);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        errorMessage ("error processing CIFP record " + airport.ident + "." +
                                appid + "." + segid, e);
                    }
                } while (result1.moveToNext ());
            } finally {
                result1.close ();
            }
        } catch (Exception e) {
            errorMessage ("error reading " + dbname, e);
        }

        // every approach must have a finalseg and a missedseg
        // remove any that don't have both
        for (Iterator<String> it = cifpApproaches.keySet ().iterator (); it.hasNext ();) {
            String appid = it.next ();
            CIFPApproach approach = cifpApproaches.get (appid);
            if ((approach.finalseg == null) || (approach.missedseg == null)) {
                it.remove ();
            }
        }

        // second-phase init
        for (CIFPApproach cifpapp : cifpApproaches.values ()) {
            for (CIFPSegment cifpseg : cifpapp.transsegs.values ()) {
                cifpseg.init2 ();
            }
            cifpapp.finalseg.init2 ();
            cifpapp.missedseg.init2 ();
        }
    }

    /**
     * One per approach associated with this plate.
     * Usually there is just one of these, but there can be more as
     * in one for ILS and one for LOC for plates named 'ILS OR LOC ...'.
     */
    public class CIFPApproach {
        public String appid;       // eg, 'I05Z', 'L16'
        public String name;        // eg, 'ILS Z RWY 05'
        public String type;        // eg, 'ILS', 'LOC' etc
        public int typeWidth;      // width of type string on SelectButton
        public Waypoint refnavwp;  // which navaid the approach uses (or null if none/not known)
        public TreeMap<String,CIFPSegment> transsegs = new TreeMap<> ();
        public CIFPSegment finalseg;
        public CIFPSegment missedseg;
    }

    /**
     * One per segment associated with an approach.
     * Every approach must have at least a final and missed segment.
     * They usually have several transition segments as well.
     */
    public class CIFPSegment {
        public  CIFPApproach approach;
        public  String segid;
        private String name;
        public  CIFPLeg[] legs;

        // second-phase init
        public void init2 ()
        {
            for (CIFPLeg leg : legs) leg.init2 ();
        }

        // get steps for all legs in this segment
        public CIFPLeg getSteps (LinkedList<CIFPStep> steps, CIFPLeg prevleg) throws Exception
        {
            for (CIFPLeg leg : legs) {
                if (!leg.isRuntLeg (prevleg) || !prevleg.mergeAltitude (leg)) {
                    leg.getSteps (steps);
                    prevleg = leg;
                }
            }
            return prevleg;
        }

        // scan the segment for an FAF waypoint
        // only final segments have a FAF
        // return null if none found
        public CIFPLeg getFafLeg ()
        {
            for (CIFPLeg leg : legs) {
                if (leg.parms.containsKey ("faf")) return leg;
            }
            return null;
        }

        /**
         * If this is the final approach segment, return the FAF waypoint
         */
        public Waypoint getFafWaypt ()
        {
            CIFPLeg fafleg = getFafLeg ();
            if (fafleg == null) return null;
            return fafleg.getFafWaypt ();
        }

        /**
         * If this is the final segment, return the runway waypoint
         * For circling-only approaches, this is the missed approach point
         * All final segments end with a CF leg indicating the runway or MAP
         */
        public Waypoint getRwyWaypt ()
        {
            if (!segid.equals (CIFPSEG_FINAL)) return null;
            int rwylegno = legs.length - 1;
            return legs[rwylegno].getRwyWaypt ();
        }

        /**
         * Get long segment name
         *   "KBVY LOC 16/(rv)"
         */
        public String getName ()
        {
            if (name == null) {
                name = airport.ident + " " +
                        approach.name + "/" +
                        segid;
            }
            return name;
        }
    }

    /**
     * Make a CIFP leg object from the database leg string.
     */
    private void makeLeg (CIFPSegment seg, int idx, String legstr) throws Exception
    {
        String[] parmary = legstr.split (",");
        int nparms = parmary.length;

        CIFPLeg leg;
        switch (parmary[0]) {
            case "AF": leg = new CIFPLeg_AF (); break;
            case "CA": leg = new CIFPLeg_CA (); break;
            case "CD": leg = new CIFPLeg_CD (); break;
            case "CF": leg = new CIFPLeg_CF (); break;
            case "CI": leg = new CIFPLeg_CI (); break;
            case "CR": leg = new CIFPLeg_CR (); break;
            case "FC": leg = new CIFPLeg_FC (); break;
            case "HF": leg = new CIFPLeg_HF (); break;
            case "HM": leg = new CIFPLeg_HM (); break;
            case "PI": leg = new CIFPLeg_PI (); break;
            case "RF": leg = new CIFPLeg_RF (); break;
            default: throw new Exception ("unknown leg type " + legstr);
        }

        leg.legstr = legstr;
        leg.pathterm = parmary[0];
        leg.parms = new HashMap<> ();
        for (int i = 0; ++ i < nparms;) {
            String parm = parmary[i];
            int j = parm.indexOf ('=');
            if (j < 0) leg.parms.put (parm, null);
            else {
                String name = parm.substring (0, j);
                leg.parms.put (name, parm.substring (++ j));
            }
        }

        leg.segment = seg;
        leg.legidx  = idx;
        seg.legs[idx] = leg;

        leg.init1 ();
    }

    /**
     * A step is a single maneuver, eg, turn heading xxx or fly to xxxxx.
     * It can be described with one short line of text.
     */
    private abstract class CIFPStep {
        public float begptalt;  // where airplane begins going into step
        public float begpthdg;
        public int   begptbmy;
        public int   begptbmx;
        public float endptalt;   // where airplane ends up at end of step
        public float endpthdg;
        public int   endptbmx;
        public int   endptbmy;
        public int   drawIndex;  // index in cifpSteps[] array
        public int   acrftdist;  // distance aircraft is from dots

        public String filetdir;  // 'L<-' or 'R->' (null if undefined)
        public float  filetitc;  // initial true course
        public float  filetftc;  // finish true course
        public int    filetbmx;  // starting bitmap x,y
        public int    filetbmy;

        // get text line describing the step
        //  input:
        //   curposalt,curposhdg,curposalt,curposlon
        public abstract String getTextLine () throws Exception;

        // draw the step
        //  input:
        //   curposalt,curposhdg,curposlat,curposlon
        //   nmperdot
        //   dotsPaint
        //  output:
        //   curposalt,curposhdg,curposlat,curposlon
        public abstract void drawStepDots () throws Exception;

        // draw fillet from end of previous step to beginning of this step
        // called only when a previous step is current to show pilot how to turn to this step
        public void drawFillet ()
        {
            // for most steps, we can assume they are a straight line of some sort
            // so assume their initial course is the same as the course from the
            // beginning to the end of the step
            defaultDrawFillet (begptbmx, begptbmy,
                    trueCourse (begptbmx, begptbmy, endptbmx, endptbmy));
        }

        // see if this step is a runt
        // normally see if it ends very close to where it begins
        // but things like holds end where they begin and aren't ever runts
        // runts are too small to reliably detect aircraft positioned on them
        // ...so we don't make them current, and also are hard to calculate a
        // true course entry and exit as they may actually just be a single point
        public boolean isRuntStep ()
        {
            return Mathf.hypot (endptbmx - begptbmx, endptbmy - begptbmy) < runtbmpix;
        }

        // get OBS setting for VirtNav dial (true degrees)
        public abstract float getVNOBSTrue ();
        // get waypoint for VirtNav dial needle deflection and DME
        public abstract String getVNWaypoint (LatLon ll);

        // get number of seconds until we reach the beginning of the fillet at the end of this step
        // called only when this step is current
        public int numSecToFillet ()
        {
            // see if fillet info filled in
            if (filetdir == null) return Integer.MAX_VALUE;

            // get course line from aircraft to beginning of fillet
            float tctoturn   = trueCourse (acraftbmx, acraftbmy, filetbmx, filetbmy);

            // get distance from aircraft to beginning of fillet
            float disttoturn = Mathf.hypot (acraftbmx - filetbmx, acraftbmy - filetbmy);

            // get component of that distance aligned with fillet start heading
            // will be negative if we are past beginning of fillet
            disttoturn *= Mathf.cosdeg (tctoturn - filetitc);

            // return that as number of seconds at current speed
            return Math.round (disttoturn / bmpixpersec);
        }
    }

    /**
     * Draw solid fillet from current CIFP step course line to next CIFP step course line
     * @param nextpointx = starting point of next CIFP step course line (currentStep+n)
     * @param nextpointy = starting point of next CIFP step course line (currentStep+n)
     * @param nextrucrs  = true course of next CIFP step course line (currentStep+n)
     */
    private void defaultDrawFillet (int nextpointx, int nextpointy, float nextrucrs)
    {
        // see how much we are turning by
        // normalize to range -180..+180
        // neg for left; pos for right
        CIFPStep curstep = cifpSteps[currentStep];
        float curtrucrs  = curstep.endpthdg;
        float turnbydeg  = nextrucrs - curtrucrs;
        while (turnbydeg < -180.0F) turnbydeg += 360.0F;
        while (turnbydeg >= 180.0F) turnbydeg -= 360.0F;

        // only bother if turning more than 15deg or less than 165deg
        if ((Math.abs (turnbydeg) > MINFILLET) && (Math.abs (turnbydeg) < MAXFILLET)) {

            // compute some point ahead of airplane on its current course
            float acraftbmx_ahead = curstep.endptbmx + Mathf.sindeg (curtrucrs) * 1024.0F;
            float acraftbmy_ahead = curstep.endptbmy - Mathf.cosdeg (curtrucrs) * 1024.0F;

            // compute some point ahead of next point along its initial course
            float nextbmx_ahead = nextpointx + Mathf.sindeg (nextrucrs) * 1024.0F;
            float nextbmy_ahead = nextpointy - Mathf.cosdeg (nextrucrs) * 1024.0F;

            // find intersection of those two lines, giving us the elbow of the turn
            float elbow_bmx = Lib.lineIntersectX (
                    curstep.endptbmx, curstep.endptbmy, acraftbmx_ahead, acraftbmy_ahead,
                    nextpointx, nextpointy, nextbmx_ahead, nextbmy_ahead);
            float elbow_bmy = Lib.lineIntersectY (
                    curstep.endptbmx, curstep.endptbmy, acraftbmx_ahead, acraftbmy_ahead,
                    nextpointx, nextpointy, nextbmx_ahead, nextbmy_ahead);

            // find true course from elbow to center of circle
            float elbow2centercrs;
            if (turnbydeg < 0.0F) {
                // turning left
                elbow2centercrs = curtrucrs - 90.0F + turnbydeg / 2.0F;
            } else {
                // turning right
                elbow2centercrs = curtrucrs + 90.0F + turnbydeg / 2.0F;
            }

            // find center of circle by going from elbow along that course
            float circleradius_bmp = stdcirdradsec * bmpixpersec;
            float center2elbow_bmp = circleradius_bmp / Mathf.cosdeg (turnbydeg / 2.0F);
            float center_bmx = elbow_bmx + Mathf.sindeg (elbow2centercrs) * center2elbow_bmp;
            float center_bmy = elbow_bmy - Mathf.cosdeg (elbow2centercrs) * center2elbow_bmp;

            // sweep is number of degrees for arc clockwise from start
            float sweep = Math.abs (turnbydeg);

            // start is angle clockwise from East to start at
            //  0=E; 90=S; 180=W; 270=N
            float start;
            if (turnbydeg < 0.0F) {
                // turning left
                start = nextrucrs;
            } else {
                // turning right
                start = curtrucrs + 180.0F;
            }

            drawFilletArc (center_bmx, center_bmy, circleradius_bmp,
                    start, sweep, turnbydeg < 0.0F);
        }
    }

    /**
     * These are the legs given in the database between semi-colons.
     * They are roughly equivalent to the FAA-supplied CIFP 132-column records,
     * but are munged a bit by ParseCifp.java in the server.
     * Every segment must have at least one leg.
     */
    private abstract class CIFPLeg {
        public CIFPSegment segment;
        public HashMap<String,String> parms;
        public int legidx;
        public String legstr;
        public String pathterm;

        private char altlet;
        private int  alt1ft;
        private int  alt2ft;

        public abstract void init1 () throws Exception;
        public boolean isRuntLeg (CIFPLeg prevleg) { return false; }
        public abstract void init2 ();

        public abstract void getSteps (LinkedList<CIFPStep> steps) throws Exception;

        /**
         * Get fix for various purposes.
         * These are overridden by the child classes that need to implement them.
         */
        // these assumptions are checked by ParseCifp.java
        // FAFs are always type CF
        public Waypoint getFafWaypt () { throw new RuntimeException ("getFafWaypt not implemented for " + pathterm); }
        // final segments end with CF at the runway or circling fix
        public Waypoint getRwyWaypt () { throw new RuntimeException ("getRwyWaypt not implemented for " + pathterm); }

        /**
         * Convert course parameter given in magnetic tenths of a degree
         * to a true course in degrees.
         */
        protected float getTCDeg (String key, Waypoint navwp)
        {
            float mc = Integer.parseInt (parms.get (key)) / 10.0F;
            if (navwp == null) navwp = airport;
            return mc - navwp.GetMagVar (curposalt);
        }
        protected float getTCDeg (int mci, Waypoint navwp)
        {
            float mc = mci / 10.0F;
            if (navwp == null) navwp = airport;
            return mc - navwp.GetMagVar (curposalt);
        }

        /**
         * Append magnetic course to string buffer.
         * Value is tenths of a degree magnetic.
         */
        protected void appCourse (StringBuilder sb, String key)
        {
            appCourse (sb, Integer.parseInt (parms.get (key)));
        }
        protected void appCourse (StringBuilder sb, int val)
        {
            val = ((val + 5) / 10 + 359) % 360 + 1;
            if (val <  10) sb.append ('0');
            if (val < 100) sb.append ('0');
            sb.append (val);
            sb.append ('\u00B0');
        }

        /**
         * Append a true course as magnetic.
         * Value is true degrees.
         */
        protected void appTrueAsMag (StringBuilder sb, float tru)
        {
            int mag = Math.round (tru + aptmagvar);
            mag = (mag + 359) % 360 + 1;
            if (mag <  10) sb.append ('0');
            if (mag < 100) sb.append ('0');
            sb.append (mag);
            sb.append ('\u00B0');
        }

        /**
         * Append turn direction symbol to a string buffer.
         */
        protected void appendTurn (StringBuilder sb, char td)
        {
            switch (td) {
                case 'L': {
                    sb.append ("L\u21B6");  // curly left arrow
                    break;
                }
                case 'R': {
                    sb.append ("R\u21B7");  // curly right arrow
                    break;
                }
                default: throw new RuntimeException ("bad turn dir " + td);
            }
        }

        /**
         * If the other leg is otherwise redundant with this leg,
         * absorb the other leg's altitude specifications.
         * @return true if merge successful
         */
        protected boolean mergeAltitude (CIFPLeg othrleg)
        {
            altlet = 0;

            String othrstr = othrleg.parms.get ("a");
            // it's easy to absorb nothing
            if ((othrstr == null) || othrstr.equals ("")) return true;

            String thisstr = parms.get ("a");
            // it's easy to absorb other if we have nothing
            if ((thisstr == null) || thisstr.equals ("")) {
                parms.put ("a", othrstr);
                return true;
            }

            // parse the strings
            char othrlet = othrstr.charAt (0);
            char thislet = thisstr.charAt (0);
            int othrval1, othrval2;
            int thisval1, thisval2;
            try {
                int i = othrstr.indexOf (':');
                if (i < 0) {
                    othrval1 = othrval2 = Integer.parseInt (othrstr.substring (1));
                } else {
                    othrval1 = Integer.parseInt (othrstr.substring (1, i));
                    othrval2 = Integer.parseInt (othrstr.substring (++ i));
                }
                int j = thisstr.indexOf (':');
                if (j < 0) {
                    thisval1 = thisval2 = Integer.parseInt (thisstr.substring (1));
                } else {
                    thisval1 = Integer.parseInt (thisstr.substring (1, j));
                    thisval2 = Integer.parseInt (thisstr.substring (++ j));
                }
            } catch (NumberFormatException nfe) {
                return false;
            }

            // if exact match, the they are already merged
            if ((othrval1 == thisval1) && (othrval2 == thisval2) && (othrlet == thislet)) return true;

            // if both specify a different exact altitude, fail
            if ((othrlet == '=') && (thislet == '=')) return false;

            // if both specify +, use higher altitude
            if ((othrlet == '+') && (thislet == '+')) {
                parms.put ("a", "+" + Math.max (othrval1, thisval1));
                return true;
            }

            // if both specify -, use lower altitude
            if ((othrlet == '-') && (thislet == '-')) {
                parms.put ("a", "-" + Math.min (othrval1, thisval1));
            }

            // if one says + and the other =, and the = is within the + range, use the =
            if ((othrlet == '=') && (thislet == '+')) {
                if (othrval1 >= thisval1) {
                    parms.put ("a", othrstr);
                    return true;
                }
                return false;
            }
            if ((thislet == '=') && (othrlet == '+')) {
                return thisval1 >= othrval1;
            }

            // first one says + and second one says J
            // with alt1 values equal, it's a match
            // this removes redundant ZELKA on BED ILS 11 starting at ZELKA hold
            if ((thislet == '+') && (othrlet == 'J') && (thisval1 == othrval1)) {
                return true;
            }

            // we don't handle any other cases for now
            return false;
        }

        /**
         * Append the altitude parameter to a string buffer.
         */
        protected void appendAlt (StringBuilder sb)
        {
            // decode associated altitude
            String alt = parms.get ("a");
            if ((alt != null) && !alt.equals ("")) {
                // there are either 1 or 2 values
                String a1, a2;
                int i = alt.indexOf (':');
                if (i < 0) {
                    a1 = alt.substring (1);
                    a2 = null;
                } else {
                    a1 = alt.substring (1, i);
                    a2 = alt.substring (++ i);
                }

                // the first character says what it is
                switch (alt.charAt (0)) {

                    // cross fix at a1 altitude
                    case '@': {
                        sb.append (" =");
                        appendAlt (sb, a1);
                        break;
                    }

                    // cross fix at or above a1 altitude
                    case '+': {
                        sb.append (" \u2265");  // >=
                        appendAlt (sb, a1);
                        break;
                    }

                    // cross fix at or below a1 altitude
                    case '-': {
                        sb.append (" \u2264");  // <=
                        appendAlt (sb, a1);
                        break;
                    }

                    // cross fix between a1 and a2 altitudes (a1 <= a2)
                    case 'B': {
                        sb.append (" ");
                        sb.append (a1);
                        sb.append ("..");
                        appendAlt (sb, a2);
                        break;
                    }

                    // approach fix at a2 and intercept glide slope
                    // when at fix and on glide slope, altitude should be equal to a1
                    case 'G': {  // ORL ILS 25 at CBOYD
                        sb.append (" =");
                        appendAlt (sb, a2);
                        if (a1.equals (a2)) {
                            sb.append (" (gs)");
                        } else {
                            sb.append (" (gs ");
                            appendAlt (sb, a1);
                            sb.append (')');
                        }
                        break;
                    }

                    // approach fix at a1 and intercept glide slope
                    // when at fix and on glide slope, altitude should be equal to a2
                    case 'H': {  // MHT ILS 6 at FITZY
                        sb.append (" =");
                        appendAlt (sb, a1);
                        if (a1.equals (a2)) {
                            sb.append (" (gs)");
                        } else {
                            sb.append (" (gs ");
                            appendAlt (sb, a2);
                            sb.append (')');
                        }
                        break;
                    }

                    // cross fix at a1 altitude
                    // subsequently descend to a2 (but that is mentioned in next leg anyway)
                    case 'I': {  // ORL ILS 25 at MARYB
                        sb.append (" =");
                        appendAlt (sb, a1);
                        break;
                    }

                    // cross fix at min of a1 altitude
                    // subsequent descent to a2 (but that is mentioned in next leg anyway)
                    case 'J': {  // MHT ILS 6 at JAAZZ; BED ILS 11 at ZELKA
                        sb.append (" \u2265");  // >=
                        appendAlt (sb, a1);
                        break;
                    }

                    // along glide path for precision approach such as GPS LPV
                    // a1 gives non-precision minimum altitude at this fix
                    // a2 gives on-glideslope exact altitude at this fix
                    case 'V': {  // BVY GPS 27 at MAIOU; altone=non-prec; alttwo=precision
                        sb.append (" \u2265");   // non-precision descent altitude
                        appendAlt (sb, a1);
                        sb.append (" gs ");     // precision on glidepath
                        appendAlt (sb, a2);     // (a bit higher than lims[0])
                        break;
                    }

                    default: {
                        sb.append (" alt=");
                        sb.append (alt);
                        break;
                    }
                }
            }
        }

        /**
         * Append altitude to string buffer.
         */
        protected void appendAlt (StringBuilder sb, String alt)
        {
            sb.append (alt);
            sb.append ('\'');
        }

        /**
         * Append a number of tenths to string buffer.
         */
        protected void appTenth (StringBuilder sb, String key)
        {
            appTenth (sb, Integer.parseInt (parms.get (key)));
        }
        protected void appTenth (StringBuilder sb, int val)
        {
            sb.append (val / 10);
            sb.append ('.');
            sb.append (val % 10);
        }

        /**
         * Update curposalt altitude at the end of the leg.
         */
        protected void updateAlt ()
        {
            float newalt = getAltitude ();
            if (!Float.isNaN (newalt)) {
                curposalt = newalt;
            }
        }

        /**
         * Get altitude we should be at at the end of the leg.
         * For FAF and runway legs, this should be the on-glideslope altitude.
         */
        public float getAltitude ()
        {
            // decode associated altitude
            if (altlet == 0) {
                String alt = parms.get ("a");
                altlet = '?';
                if ((alt != null) && !alt.equals ("")) {
                    altlet = alt.charAt (0);
                    // there are either 1 or 2 values
                    int i = alt.indexOf (':');
                    if (i < 0) {
                        alt1ft = alt2ft = Integer.parseInt (alt.substring (1));
                    } else {
                        alt1ft = Integer.parseInt (alt.substring (1, i));
                        alt2ft = Integer.parseInt (alt.substring (++ i));
                    }
                }
            }

            // the first character says what it is
            switch (altlet) {

                // cross fix at alt1ft altitude
                case '@': {
                    return alt1ft;
                }

                // cross fix at or above alt1ft altitude
                case '+': {
                    return alt1ft;
                }

                // cross fix at or below alt1ft altitude
                case '-': {
                    return alt1ft;
                }

                // cross fix between alt1ft and alt2ft altitudes (alt1ft <= alt2ft)
                case 'B': {
                    return (alt1ft + alt2ft) / 2.0F;
                }

                // approach fix at alt2ft and intercept glide slope
                // when at fix and on glide slope, altitude should be equal to alt1ft
                case 'G': {  // ORL ILS 25 at CBOYD
                    return alt1ft;
                }

                // approach fix at alt1ft and intercept glide slope
                // when at fix and on glide slope, altitude should be equal to alt2ft
                case 'H': {  // MHT ILS 6 at FITZY
                    return alt2ft;
                }

                // cross fix at alt1ft altitude
                // subsequently descend to alt2ft (but that is mentioned in next leg anyway)
                case 'I': {  // ORL ILS 25 at MARYB
                    return alt1ft;
                }

                // cross fix at min of alt1ft altitude
                // subsequent descent to alt2ft (but that is mentioned in next leg anyway)
                case 'J': {  // MHT ILS 6 at JAAZZ
                    return alt1ft;
                }

                // along glide path for precision approach such as GPS LPV
                // alt1ft gives non-precision minimum altitude at this fix
                // alt2ft gives on-glideslope exact altitude at this fix
                case 'V': {  // BVY GPS 27 at MAIOU; altone=non-prec; alttwo=precision
                    return alt2ft;
                }

                default: return Float.NaN;
            }
        }
    }

    /**
     * DME Arc
     */
    private class CIFPLeg_AF extends CIFPLeg {
        public float arcradius, begradtru, endradtru;
        public float endinghdg;
        public int   endingbmx, endingbmy;
        public Waypoint navwp;

        private Point navpt = new Point ();

        private final CIFPStep afstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appTenth (sb, "nm");
                sb.append ("nm arc ");
                sb.append (navwp.ident);
                sb.append (' ');
                appCourse (sb, "beg");
                sb.append ('\u2192');
                appCourse (sb, "end");
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                // draw dots around the radius
                drawStepDotArc (navpt.x, navpt.y, arcradius,
                        Math.min (begradtru, endradtru) - 90.0F,
                        Math.abs (endradtru - begradtru));

                // return ending alt,hdg,bmx,bmy
                updateAlt ();
                curposhdg = endinghdg;
                curposbmx = endingbmx;
                curposbmy = endingbmy;
            }

            // draw fillet showing where/how to turn to enter the DME arc.
            // called when this DME arc is next-to-be-current (ignoring runts).
            @Override
            public void drawFillet ()
            {
                //TODO account for little bit of curve entering the arc
                int bmx = navpt.x + Math.round (Mathf.sindeg (begradtru) * arcradius);
                int bmy = navpt.y - Math.round (Mathf.cosdeg (begradtru) * arcradius);
                float hdg = endinghdg - endradtru + begradtru;
                defaultDrawFillet (bmx, bmy, hdg);
            }

            // return what the OBS should be at this point (true)
            // just use inbound radial from aircraft to navwp
            // step it by DMEARCSTEPDEG degrees relative to end-of-arc radial
            @Override
            public float getVNOBSTrue ()
            {
                float tc = Lib.LatLonTC (wairToNow.currentGPSLat, wairToNow.currentGPSLon,
                        navwp.lat, navwp.lon);
                tc -= endradtru;
                while (tc < 0.0F) tc += 360.0F;
                int tcstep = Math.round (tc / DMEARCSTEPDEG);
                return tcstep * DMEARCSTEPDEG + endradtru;
            }

            // return waypoint at center of arc
            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = navwp.lat;
                ll.lon = navwp.lon;
                return navwp.ident;
            }
        };

        public void init1 () throws Exception
        {
            // get parameters
            navwp = findWaypoint (parms.get ("nav"), navpt);
            begradtru = getTCDeg ("beg", navwp);
            endradtru = getTCDeg ("end", navwp);
            float rnm = Integer.parseInt (parms.get ("nm")) / 10.0F;
            arcradius = rnm * bmpixpernm;

            // adjust radials to be within 180.0 of each other
            while (endradtru < begradtru - 180) endradtru += 360;
            while (endradtru > begradtru + 180) begradtru += 360;

            // compute what heading will be at end of arc
            endinghdg = endradtru;
            if (begradtru < endradtru) endinghdg +=  90;
            if (begradtru > endradtru) endinghdg += 270;
            while (endinghdg >= 360) endinghdg -= 360;

            // compute the endpoint of the dme arc
            endingbmx = navpt.x + Math.round (Mathf.sindeg (endradtru) * arcradius);
            endingbmy = navpt.y - Math.round (Mathf.cosdeg (endradtru) * arcradius);
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (afstep);
        }
    }

    /**
     * Fly given course to altitude.
     */
    private class CIFPLeg_CA extends CIFPLeg {
        private float altitude, maxdist;
        private float ochdgtru;
        private float climbegalt;
        private int climbegbmx, climbegbmy;
        private int endbmx, endbmy;

        private final CIFPStep castep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appCourse (sb, "mc");
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                if (currentStep < drawIndex) {

                    // not at this step yet, draw dots assuming CLIMBFT_NM
                    if (maxdist == 0.0F) {
                        float oldalt = curposalt;
                        updateAlt ();
                        float newalt = curposalt;
                        if (newalt > oldalt) {
                            maxdist = (newalt - oldalt) / CLIMBFT_NM;
                        }
                        if (maxdist < 1.0F) maxdist = 1.0F;
                        maxdist *= bmpixpernm;
                        endbmx = curposbmx + Math.round (Mathf.sindeg (ochdgtru) * maxdist);
                        endbmy = curposbmy - Math.round (Mathf.cosdeg (ochdgtru) * maxdist);
                    }
                } else {

                    // this step is current, draw dots using actual climb rate
                    float curgpsaltft = wairToNow.currentGPSAlt * Lib.FtPerM;
                    if (Float.isNaN (climbegalt)) {

                        // first time here, save starting point
                        climbegalt = curgpsaltft;
                        climbegbmx = acraftbmx;
                        climbegbmy = acraftbmy;
                    } else {

                        // subsequent, distance and altitude since first time
                        // .. then calc average distance per altitude so far
                        float distsofar = Mathf.hypot (climbegbmx - acraftbmx, climbegbmy - acraftbmy);
                        float altsofar = curgpsaltft - climbegalt;
                        float distperalt = distsofar / altsofar;

                        // get total distance since first time needed to climb to target altitude
                        float totaldist = distperalt * (altitude - climbegalt);
                        if (totaldist < bmpixpernm) totaldist = bmpixpernm;
                        if (totaldist > maxdist)    totaldist = maxdist;

                        // calculate end point that distance on target heading
                        // from proper starting point so it is aligned with chart drawing
                        // so they don't slam into tower
                        endbmx = curposbmx + Math.round (Mathf.sindeg (ochdgtru) * totaldist);
                        endbmy = curposbmy - Math.round (Mathf.cosdeg (ochdgtru) * totaldist);
                    }
                }

                // draw dots to the end point
                drawStepCourseToPoint (endbmx, endbmy);
            }

            // set VirtNav OBS to what heading they should be holding in climb
            @Override
            public float getVNOBSTrue ()
            {
                return ochdgtru;
            }

            // return waypoint at end of climb for VirtNav dial
            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = plateView.BitmapXY2Lat (endbmx, endbmy);
                ll.lon = plateView.BitmapXY2Lon (endbmx, endbmy);
                return "climb";
            }
        };

        public void init1 ()
        {
            ochdgtru = getTCDeg ("mc", null);
            altitude = getAltitude ();
            climbegalt = Float.NaN;
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (castep);
        }
    }

    /**
     * Fly course to dme from a navaid.
     */
    private class CIFPLeg_CD extends CIFPLeg {
        private float dmebmp, dmenm, ochdg;
        private Point navpt = new Point ();
        private String vnwpid;
        private Waypoint navwp;

        private final CIFPStep cdstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appCourse (sb, "mc");
                sb.append ('\u2192');
                appTenth (sb, "nm");
                sb.append ("nm ");
                sb.append (parms.get ("nav"));
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                curposhdg = ochdg;
                drawStepCourseToInterceptDist (navpt, dmebmp);
                updateAlt ();
            }

            @Override
            public float getVNOBSTrue ()
            {
                return ochdg;
            }

            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = plateView.BitmapXY2Lat (endptbmx, endptbmy);
                ll.lon = plateView.BitmapXY2Lon (endptbmx, endptbmy);
                if (vnwpid == null) vnwpid = navwp.ident + "[" + Lib.FloatNTZ (dmenm);
                return vnwpid;
            }
        };

        public void init1 () throws Exception
        {
            dmenm  = Integer.parseInt (parms.get ("nm")) / 10.0F;
            dmebmp = dmenm * bmpixpernm;
            navwp  = findWaypoint (parms.get ("nav"), navpt);
            ochdg  = getTCDeg ("mc", navwp);
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (cdstep);
        }
    }

    /**
     * Fly straight line to waypoint.
     */
    private class CIFPLeg_CF extends CIFPLeg {
        public float trucrs;
        public Waypoint navwp;
        private Point navpt = new Point ();

        private final CIFPStep cfstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                float nm = Mathf.hypot (curposbmx - navpt.x, curposbmy - navpt.y) / bmpixpernm;
                if (nm >= RUNTNM) {
                    float tc = trueCourse (curposbmx, curposbmy, navpt.x, navpt.y);
                    appTrueAsMag (sb, tc);
                    sb.append ('\u2192');
                }
                sb.append (navwp.ident);
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                drawStepCourseToPoint (navpt.x, navpt.y);
                updateAlt ();
            }

            @Override
            public float getVNOBSTrue ()
            {
                return trueCourse (begptbmx, begptbmy, navpt.x, navpt.y);
            }

            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = navwp.lat;
                ll.lon = navwp.lon;
                return navwp.ident;
            }
        };

        public void init1 () throws Exception
        {
            String wpid = parms.get ("wp");
            navwp  = findWaypoint (wpid, navpt);
            trucrs = parms.containsKey ("mc") ? getTCDeg ("mc", navwp) : Float.NaN;
        }

        public boolean isRuntLeg (CIFPLeg prevleg)
        {
            // if a CF follows an AF that ends on this fix,
            // the CF is redundant
            if (prevleg instanceof CIFPLeg_AF) {
                CIFPLeg_AF prevaf = (CIFPLeg_AF) prevleg;
                float distpix = Mathf.hypot (prevaf.endingbmx - navpt.x, prevaf.endingbmy - navpt.y);
                if (distpix < runtbmpix) return true;
            }

            // if a CF follows a CF with a matching fix,
            // the second CF is redundant
            if (prevleg instanceof CIFPLeg_CF) {
                CIFPLeg_CF prevcf = (CIFPLeg_CF) prevleg;
                if ((prevcf.navpt.x == navpt.x) &&
                        (prevcf.navpt.y == navpt.y)) return true;
            }

            // if a CF follows an Hold with a matching fix,
            // the CF is redundant
            if (prevleg instanceof CIFPLeg_Hold) {
                CIFPLeg_Hold prevhold = (CIFPLeg_Hold) prevleg;
                if ((prevhold.navpt.x == navpt.x) &&
                        (prevhold.navpt.y == navpt.y)) return true;
            }

            // if this CF follows an hold and the hold is to the
            // approach's FAF alingned with the runway, and this CF is outside the
            // FAF, the CF is redundant (or really just bad)
            // eg, KLWM ILS 05 from Hold:WOBMU -> CF:TEKWS -> CF:WOBMU(faf)
            if (prevleg instanceof CIFPLeg_Hold) {
                CIFPLeg_Hold prevhold = (CIFPLeg_Hold) prevleg;
                CIFPLeg fafleg = segment.approach.finalseg.getFafLeg ();
                if (fafleg instanceof CIFPLeg_CF) {
                    CIFPLeg_CF fafcf = (CIFPLeg_CF) fafleg;
                    if ((fafcf.navwp.lat == prevhold.navwp.lat) && (fafcf.navwp.lon == prevhold.navwp.lon)) {
                        float final_tc    = Lib.LatLonTC (fafcf.navwp.lat, fafcf.navwp.lon, airport.lat, airport.lon);
                        float this_to_apt = Lib.LatLonDist (navwp.lat, navwp.lon, airport.lat, airport.lon);
                        float faf_to_arpt = Lib.LatLonDist (fafcf.navwp.lat, fafcf.navwp.lon, airport.lat, airport.lon);
                        if ((this_to_apt > faf_to_arpt) && (angleDiffU (final_tc - prevhold.inbound) < 30.0F)) return true;
                    }
                }
            }

            // if this CF follows a PI and the PI is to the
            // approach's FAF aligned with the runway, and this CF is outside the
            // FAF, the CF is redundant (or really just bad)
            // eg, KPVD ILS 05 from PI:CUTSI -> CF:KENTE -> CF:CUTSI(faf)
            //     note the PT barb is inside KENTE so pt is based on CUTSI
            if (prevleg instanceof CIFPLeg_PI) {
                CIFPLeg_PI prevpi = (CIFPLeg_PI) prevleg;
                CIFPLeg fafleg = segment.approach.finalseg.getFafLeg ();
                if (fafleg instanceof CIFPLeg_CF) {
                    CIFPLeg_CF fafcf = (CIFPLeg_CF) fafleg;
                    if ((fafcf.navwp.lat == prevpi.navwp.lat) && (fafcf.navwp.lon == prevpi.navwp.lon)) {
                        float final_tc    = Lib.LatLonTC (fafcf.navwp.lat, fafcf.navwp.lon, airport.lat, airport.lon);
                        float this_to_apt = Lib.LatLonDist (navwp.lat, navwp.lon, airport.lat, airport.lon);
                        float faf_to_arpt = Lib.LatLonDist (fafcf.navwp.lat, fafcf.navwp.lon, airport.lat, airport.lon);
                        if ((this_to_apt > faf_to_arpt) && (angleDiffU (final_tc - prevpi.inbound) < 30.0F)) return true;
                    }
                }
            }

            // if a CF follows an RF that ends on this fix,
            // the CF is redundant
            //   PAKT ILS Z 11 at PUTIY
            if (prevleg instanceof CIFPLeg_RF) {
                CIFPLeg_RF prevrf = (CIFPLeg_RF) prevleg;
                float distpix = Mathf.hypot (prevrf.endpt.x - navpt.x, prevrf.endpt.y - navpt.y);
                if (distpix < runtbmpix) return true;
            }

            return false;
        }

        public void init2 ()
        { }

        public Waypoint getFafWaypt ()
        {
            return navwp;
        }

        public Waypoint getRwyWaypt ()
        {
            return navwp;
        }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (cfstep);
        }
    }

    /**
     * Fly course to intercept next leg.
     * ...in practice, followed by AF, CA, CF
     */
    private class CIFPLeg_CI extends CIFPLeg {
        private CIFPLeg nextleg;
        private float trucrs;

        private final CIFPStep cistep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appCourse (sb, "mc");
                sb.append (" to intercept...");
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                // if next leg is AF, it has a navaid and a DME arc that we intercept.
                // so draw dots from current position along our course until we intercept
                // the AF leg's DME arc.
                if (nextleg instanceof CIFPLeg_AF) {
                    CIFPLeg_AF nextaf = (CIFPLeg_AF) nextleg;
                    curposhdg = trucrs;
                    drawStepCourseToInterceptDist (nextaf.navpt, nextaf.arcradius);
                }

                // if next leg is CF, it has a navaid and a radial that we intercept.
                // so draw dots from current position along our course until we intercept
                // the CF leg's radial.
                if (nextleg instanceof CIFPLeg_CF) {
                    CIFPLeg_CF nextcf = (CIFPLeg_CF) nextleg;
                    if (!Float.isNaN (nextcf.trucrs)) {
                        curposhdg = trucrs;
                        drawStepCourseToInterceptRadial (nextcf.navpt, nextcf.trucrs);
                    }
                }

                updateAlt ();
            }

            @Override
            public float getVNOBSTrue ()
            {
                return trucrs;
            }

            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = plateView.BitmapXY2Lat (endptbmx, endptbmy);
                ll.lon = plateView.BitmapXY2Lon (endptbmx, endptbmy);
                return "intcp";
            }
        };

        public void init1 ()
        {
            trucrs = getTCDeg ("mc", null);
        }

        public void init2 ()
        {
            nextleg = segment.legs[legidx+1];
        }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (cistep);
        }
    }

    /**
     * Fly course to intercept radial from navaid.
     */
    private class CIFPLeg_CR extends CIFPLeg {
        private float ochdg;
        private float radial;
        private Point navpt = new Point ();
        private Waypoint navwp;

        private final CIFPStep crstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appCourse (sb, "mc");
                sb.append ('\u2192');
                appCourse (sb, "rad");
                sb.append (" ");
                sb.append (parms.get ("nav"));
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                curposhdg = ochdg;
                drawStepCourseToInterceptRadial (navpt, radial);
                updateAlt ();
            }

            @Override
            public float getVNOBSTrue ()
            {
                return ochdg;
            }

            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = plateView.BitmapXY2Lat (endptbmx, endptbmy);
                ll.lon = plateView.BitmapXY2Lon (endptbmx, endptbmy);
                return navwp.ident + "/" + Lib.FloatNTZ (radial);
            }
        };

        public void init1 () throws Exception
        {
            navwp  = findWaypoint (parms.get ("nav"), navpt);
            ochdg  = getTCDeg ("mc",  navwp);
            radial = getTCDeg ("rad", navwp);
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (crstep);
        }
    }

    /**
     * Fly given course for given distance.
     * Always preceded by CF which gives our start point as a waypoint.
     */
    private class CIFPLeg_FC extends CIFPLeg {
        private boolean calcd;
        private float distbmp;
        private float ochdg;
        private int endbmx, endbmy;

        private final CIFPStep fcstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appCourse (sb, "mc");
                sb.append (" ");
                appTenth (sb, "nm");
                sb.append ("nm");
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                if (!calcd) {
                    endbmx = curposbmx + Math.round (Mathf.sindeg (ochdg) * distbmp);
                    endbmy = curposbmy - Math.round (Mathf.cosdeg (ochdg) * distbmp);
                    calcd  = true;
                }
                drawStepCourseToPoint (endbmx, endbmy);
            }

            // get what the VirtNav OBS dial should be set to
            @Override
            public float getVNOBSTrue ()
            {
                return ochdg;
            }

            // get what the VirtNav needle and DME are set to
            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = plateView.BitmapXY2Lat (endptbmx, endptbmy);
                ll.lon = plateView.BitmapXY2Lon (endptbmx, endptbmy);
                return "ahead";
            }
        };

        public void init1 () throws Exception
        {
            ochdg   = getTCDeg ("mc", null);
            distbmp = Integer.parseInt (parms.get ("nm")) / 10.0F * bmpixpernm;
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (fcstep);
        }
    }

    /**
     * Various holds.
     */
    // hold until at fix
    // - just once around until reach the fix
    private class CIFPLeg_HF extends CIFPLeg_Hold {
        public CIFPLeg_HF ()
        {
            msg = "hold ";
        }
    }
    // hold indefinitely
    // - currently only used at end of missed segment, ie, end of approach
    //   so we can assume there is no next step
    private class CIFPLeg_HM extends CIFPLeg_Hold {
        public CIFPLeg_HM ()
        {
            msg = "hold ";
        }
    }

    // should be approx 35.3 degrees for one-minute legs and 3deg/sec turns
    private final static float holddiagdegs =
            Mathf.toDegrees (Mathf.atan2 (stdcirdradsec, HOLDLEGSEC)) * 2.0F;

    private class CIFPLeg_Hold extends CIFPLeg {
        protected String msg;
        private char turndir;
        private float diaghdg;  // heading along diagonal away from navwp
        private float exithdg;  // heading when exiting hold from over navwp
        private float inbound;
        private Point navpt = new Point ();
        private Waypoint navwp;

        private final CIFPStep holdstep = new CIFPStep () {
            private float tc_acraft_to_navwp = Float.NaN;
                                               // incoming (entry) aircraft heading relative to inbound
                                               //  -180..-70 : parallel entry
                                               //  -70..+110 : direct entry
                                               // +110..+180 : teardrop entry
            private float directctrbmx;        // for direct entry, center of displaced near-end turn
            private float directctrbmy;
            private float directinboundintersectbmx;
            private float directinboundintersectbmy;
            private int directoutboundintersectbmx;
            private int directoutboundintersectbmy;

            // get text line describing the hold
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                sb.append (msg);
                appCourse (sb, "rad");
                sb.append ('\u2192');
                sb.append (parms.get ("wp"));
                sb.append (' ');
                appendTurn (sb, parms.get ("td").charAt (0));
                appendAlt (sb);
                return sb.toString ();
            }

            // draw dots for the hold, excluding any fillet arcs
            // if the hold is current, makes all dots sensitive to keep hold current
            // ...as long as aircraft follows the dots
            // if the hold is next to be current, makes minimal dots sensitive so it
            // ...becomes current only when appropriate
            // if further in future than next, no dots are made sensitive in case it
            // ...overlaps some other course line
            @Override
            public void drawStepDots ()
            {
                if (currentStep >= drawIndex) {
                    drawCurrentDots ();
                } else if (!Float.isNaN (tc_acraft_to_navwp)) {
                    drawEntryDots ();
                } else {
                    drawInsensitiveOvalDots ();
                }

                // we exit the hold over the navwp
                // and either on inbound heading or diagonal
                curposbmx = navpt.x;
                curposbmy = navpt.y;
                curposhdg = exithdg;

                // update what altitude they should be at when at navwp
                updateAlt ();
            }

            // draw fillet showing where/how to turn to enter the hold.
            // called when this hold is next-to-be-current (ignoring runts).
            @Override
            public void drawFillet ()
            {
                // get description of step leading into the hold
                CIFPStep curstep = cifpSteps[currentStep];

                // see which direction the aircraft is heading at the end of that step
                // that gets it to navwp
                tc_acraft_to_navwp = curstep.endpthdg;

                // make it relative to inbound leg
                tc_acraft_to_navwp -= inbound;
                while (tc_acraft_to_navwp < -180.0F) tc_acraft_to_navwp += 360.0F;
                while (tc_acraft_to_navwp >= 180.0F) tc_acraft_to_navwp -= 360.0F;

                // negative means we are on the side of the hold
                // positive means we are outside the hold side
                //    0 = direct entry
                // +180 = teardrop entry
                // -180 = parallel entry
                if (turndir == 'L') tc_acraft_to_navwp = - tc_acraft_to_navwp;

                // check for parallel entry
                // supply a normal fillet to transition onto the inbound but in outbound direction
                if (tc_acraft_to_navwp < -70.0F) {
                    defaultDrawFillet (navpt.x, navpt.y, inbound + 180.0F);
                    return;
                }

                // check for teardrop entry
                // supply a normal fillet to transition onto the diagonal going away from navpt
                if (tc_acraft_to_navwp > 110.0F) {
                    defaultDrawFillet (navpt.x, navpt.y, diaghdg);
                    return;
                }

                // tc_acraft_to_navwp is between -70 and +110
                // comments assume range is -90 to +90 but otherwise work ok

                // direct entry
                // move the center of the outbound turn circle so the circle is tangential to aircraft course line
                // test case KBED GPS 23 JENDI starting near KBVY
                //  JENDI (iaf) is direct entry coming from outside
                //  WHYBE (map) is direct entry crossing over

                // the radius is always the same, ie, 60/PI * bmpixpersec for standard rate turn

                float radius = stdcirdradsec * bmpixpersec;

                // the circle is always tangential to:
                // * the aircraft course line
                // * the inbound and outbound course lines

                // for the case where tc_acraft_to_navwp == 0, ie, directly aligned with inbound course,
                // we want the circle center to be where it normally should be

                // for the case where tc_acraft_to_navwp == 90, ie, perpendicular coming from outside,
                // we want the circle pushed into the hold one radius toward the far end of the hold,
                // effectively shrinking the hold a little
                //  KBED GPS 23 starting near KBVY going to JENDI

                // for the case where tc_acraft_to_navwp = -90, ie, perpendicular crossing over the hold,
                // we want the circle pushed outof the hold one radius away from the far end of the hold,
                // effectively stretching the hold a little
                //  KBED GPS 23 missed approach at WHYBE

                float dist_to_push_out = - Mathf.tan (Mathf.toRadians (tc_acraft_to_navwp / 2.0F)) * radius;

                // compute where the displaced turn circle intersects the inbound course line
                // if tc_acraft_to_navwp =   0, it would be right at navpt.
                // if tc_acraft_to_navwp =  90, it is one radius inward, shrinking the hold
                // if tc_acraft_to_navwp = -90, it is one radius outward, stretching the hold
                directinboundintersectbmx = navpt.x + Mathf.sindeg (inbound) * dist_to_push_out;
                directinboundintersectbmy = navpt.y - Mathf.cosdeg (inbound) * dist_to_push_out;

                // compute number of degrees for arc
                // if tc_acraft_to_navwp =   0, ie, coming directly along inbound line, a 180 turn is required
                // if tc_acraft_to_navwp =  90, ie, coming from outside the hold, a 90 deg turn is required
                // if tc_acraft_to_navwp = -90, ie, crossing over the hold, a 270 deg turn is required
                float sweep = 180.0F - tc_acraft_to_navwp;

                // compute center of circle
                // also compute counter-clockwise-most point of the arc (degrees clockwise of due East)
                directctrbmx = Float.NaN;
                directctrbmy = Float.NaN;
                float start = Float.NaN;

                if (turndir == 'L') {
                    // turning left, go left from inboundline to find where center goes
                    directctrbmx = directinboundintersectbmx + Mathf.sindeg (inbound - 90.0F) * radius;
                    directctrbmy = directinboundintersectbmy - Mathf.cosdeg (inbound - 90.0F) * radius;

                    // if inbound is due north, start arc due west of center
                    start = inbound + 180.0F;
                }

                if (turndir == 'R') {
                    // turning right, go right from inboundline to find where center goes
                    directctrbmx = directinboundintersectbmx + Mathf.sindeg (inbound + 90.0F) * radius;
                    directctrbmy = directinboundintersectbmy - Mathf.cosdeg (inbound + 90.0F) * radius;

                    // if inbound is due north, end arc due east of center
                    // then back up by sweep amount to see where to start
                    start = inbound - sweep;
                }

                // point where fillet arc intersects the extended outbound course line
                directoutboundintersectbmx = Math.round (directctrbmx * 2.0F - directinboundintersectbmx);
                directoutboundintersectbmy = Math.round (directctrbmy * 2.0F - directinboundintersectbmy);

                // draw the arc
                drawFilletArc (directctrbmx, directctrbmy, radius,
                        start, sweep, turndir == 'L');
            }

            // holds are never runts even though they start and end on the same spot
            @Override
            public boolean isRuntStep ()
            {
                return false;
            }

            // course line shown on VirtNavDial should always be
            // the inbound course to the navaid
            @Override
            public float getVNOBSTrue ()
            {
                //TODO vary this to show what heading should actually be
                //TODO depending on where aircraft is in the hold
                return inbound;
            }

            // show navigation to the hold's fix in VirtNavDial
            @Override
            public String getVNWaypoint (LatLon ll)
            {
                //TODO vary this to show displacement from ideal position
                //TODO depending on where aircraft is in the hold
                ll.lat = navwp.lat;
                ll.lon = navwp.lon;
                return navwp.ident;
            }

            /**
             * Our entry fillet hasn't been determined yet, so just draw
             * as a simple oval.  We are probably several steps along down
             * the road from where aircraft is currently located.  But it
             * is possible that we are the next-to-be-current step for one
             * drawing cycle as drawFillet() won't have been called the
             * first time.
             */
            private void drawInsensitiveOvalDots ()
            {
                // draw dots for complete hold pattern
                drawOvalDots ();

                // those dots must not trigger the hold to become current
                // (so say the aircraft is very far from the hold so stepper
                //  will think the aircraft is closer to the previous step)
                smallestAcraftDist = Integer.MAX_VALUE;
            }

            /**
             * Haven't entered the hold yet, draw dots showing hold entry.
             * Our drawFillet() has been called at least once before so we know lead-in fillet params.
             */
            private void drawEntryDots ()
            {
                // check for parallel entry
                if (tc_acraft_to_navwp < -70.0F) {

                    // draw the two turns and the outbound line
                    drawOvalExceptInbound ();

                    // those dots must not trigger hold to become current
                    smallestAcraftDist = Integer.MAX_VALUE;

                    // draw the inbound line, allow it to trigger hold to become current
                    curposhdg = inbound;
                    drawStepCourseToPoint (navpt.x, navpt.y);

                    // we exit the hold on the diagonal heading toward navpt
                    exithdg = diaghdg + 180.0F;
                    return;
                }

                // draw complete oval but don't let it trigger making hold current
                drawInsensitiveOvalDots ();

                // check for teardrop entry
                if (tc_acraft_to_navwp > 110.0F) {

                    // draw diagonal dots that can make hold current
                    drawDiagonalDots ();
                    return;
                }

                // direct entry

                // see how far the airplane is from the end of
                // the fillet arc that is on the outbound leg
                // (-4 in case of rounding errors, we want to take precedence over end
                // of fillet arc, ie, pretend airplane is 2 pixels closer to our point
                // than the actual end of the fillet arc)
                int dx = acraftbmx - directoutboundintersectbmx;
                int dy = acraftbmy - directoutboundintersectbmy;
                smallestAcraftDist = dx * dx + dy * dy - 4;

                // the only things that are sensitive at this point:
                //  1) dotted path from previous step (keeps that step current while aircraft is on it)
                //  2) fillet from previous step to this one (keeps previous step current while aircraft is on it)
                //  3) that one point we just measured above (makes this hold current as soon as aircraft passes by it)
            }

            /**
             * The hold is current.
             * Draw the oval and for teardrop/parallel, draw the diagonal.
             */
            private void drawCurrentDots ()
            {
                // draw sensitive oval to keep the hold current
                drawOvalDots ();

                // for parallel and teardrop entries, draw diagonal
                // they are sensitive as well to keep the hold current
                if ((tc_acraft_to_navwp < -70.0F) || (tc_acraft_to_navwp > 110.0F)) {
                    drawDiagonalDots ();
                }
            }

            // draw dots for the whole oval, assume they will be sensitive to making/keeping
            // the hold current
            private void drawOvalDots ()
            {
                drawOvalExceptInbound ();

                // fly inbound to the navwp
                curposhdg = inbound;
                drawStepCourseToPoint (navpt.x, navpt.y);
            }

            // draw near-by turn, outbound line and far-away turn back to inbound
            private void drawOvalExceptInbound ()
            {
                // assume we are at navaid on the inbound heading as given in 'radial'
                // draw the standard rate turn away from the navaid to the recip heading
                curposhdg = inbound;
                curposbmx = navpt.x;
                curposbmy = navpt.y;
                drawStepUTurn (turndir);

                // now fly outbound for a minute
                curposhdg = inbound + 180.0F;
                drawStepOneMinLine ();

                // draw the standard rate turn toward the navaid to the inbound heading
                drawStepUTurn (turndir);
            }

            // draw diagonal dots used by teardrop or parallel entry
            private void drawDiagonalDots ()
            {
                curposbmx = navpt.x;
                curposbmy = navpt.y;
                curposhdg = diaghdg;
                drawStepOneMinLine ();

                float cbmx = navpt.x;
                float cbmy = navpt.y;
                cbmx += Mathf.sindeg (inbound + 180.0F) * HOLDLEGSEC * bmpixpersec;
                cbmy -= Mathf.cosdeg (inbound + 180.0F) * HOLDLEGSEC * bmpixpersec;
                float radius = stdcirdradsec * bmpixpersec;
                float start;
                switch (turndir) {
                    case 'L': {
                        cbmx += Mathf.sindeg (inbound - 90.0F) * radius;
                        cbmy -= Mathf.cosdeg (inbound - 90.0F) * radius;
                        start = inbound + 180.0F;
                        break;
                    }
                    case 'R': {
                        cbmx += Mathf.sindeg (inbound + 90.0F) * radius;
                        cbmy -= Mathf.cosdeg (inbound + 90.0F) * radius;
                        start = inbound - holddiagdegs;
                        break;
                    }
                    default: throw new RuntimeException ();
                }
                drawStepDotArc (cbmx, cbmy, radius, start, holddiagdegs);
            }
        };

        public void init1 () throws Exception
        {
            navwp   = findWaypoint (parms.get ("wp"), navpt);
            inbound = getTCDeg ("rad", navwp);
            turndir = parms.get ("td").charAt (0);
            exithdg = inbound;

            if (turndir == 'L') diaghdg = inbound + 180.0F + holddiagdegs;
            if (turndir == 'R') diaghdg = inbound + 180.0F - holddiagdegs;
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (holdstep);
        }
    }

    /**
     * Procedure turn.
     * Has 3 steps (each is a line followed by a fillet turn):
     *   1) outbound from the beacon on one of the beacon's radials
     *   2) 45deg turn from that radial, still going outbound, with an 180deg turn
     *   3) go straight ahead until intercept beacon's radial inbound
     */
    private class CIFPLeg_PI extends CIFPLeg {
        private char dt;         // turn direction from "B" to "C" and from "C" to "D"
        private char td;         // turn direction from "A" to "B"
        private float a_tru, b_tru;
        private float extendnm;  // extend outbound leg from navwp this far beyond HOLDLEGSEC
        private int uturnbegbmx, uturnbegbmy;
        private int uturnendbmx, uturnendbmy;
        private int a_dir;       // outbound from navaid (magnetic)
        private int b_dir;       // turned 45deg from outbound course
        private int c_dir;       // turned 180deg to inbound 45deg leg
        private int d_dir;       // inboud course to navaid (after 45deg turn)
        private Point navpt = new Point ();
        public  float inbound;   // true course when turned inbound headed directly to fix
        public  Waypoint navwp;  // navigating using this beacon

        // line going outbound from beacon and for one minute past the timwp
        private final CIFPStep pistep1 = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                sb.append ("pt on ");
                sb.append (parms.get ("wp"));
                sb.append (' ');
                appCourse (sb, a_dir);
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                curposhdg = a_tru;
                curposbmx = navpt.x;
                curposbmy = navpt.y;

                float dist = extendnm * bmpixpernm + HOLDLEGSEC * bmpixpersec;
                int endbmx = curposbmx + Math.round (Mathf.sindeg (curposhdg) * dist);
                int endbmy = curposbmy - Math.round (Mathf.cosdeg (curposhdg) * dist);
                drawStepCourseToPoint (endbmx, endbmy);

                updateAlt ();
            }

            // VirtNav dial OBS should show outbound course
            @Override
            public float getVNOBSTrue ()
            {
                return a_tru;
            }

            // VirtNav waypoint should be point at end of outbound leg
            @Override
            public String getVNWaypoint (LatLon ll)
            {
                float distnm = HOLDLEGSEC * bmpixpersec / bmpixpernm + extendnm;
                ll.lat = Lib.LatHdgDist2Lat (navwp.lat, a_tru, distnm);
                ll.lon = Lib.LatLonHdgDist2Lon (navwp.lat, navwp.lon, a_tru, distnm);
                return "pt.ob";
            }
        };

        // outbound line on 45deg angle followed by 180deg turn
        private final CIFPStep pistep2 = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appendTurn (sb, dt);
                appCourse (sb, b_dir);
                return sb.toString ();
            }

            // draw fillet from pistep1 to pistep2
            // ...so user will get the 'turn in 10 9 8 7 6...' countdown message
            // called when pistep1 is current
            @Override
            public void drawFillet ()
            {
                // tell it where the step2 line starts and what its true course is
                defaultDrawFillet (begptbmx, begptbmy, b_tru);
            }

            @Override
            public void drawStepDots ()
            {
                // draw the one-minute outbound leg
                curposhdg = b_tru;
                drawStepOneMinLine ();

                // draw U-turn
                // save beginning and end points so pistep3 can overdraw it as a fillet
                uturnbegbmx = curposbmx;
                uturnbegbmy = curposbmy;
                drawStepUTurn (td);
                uturnendbmx = curposbmx;
                uturnendbmy = curposbmy;
            }

            // VirtNav dial OBS should show outbound 45deg course
            @Override
            public float getVNOBSTrue ()
            {
                return b_tru;
            }

            // VirtNav waypoint should be point at end of outbound 45deg leg
            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = plateView.BitmapXY2Lat (uturnbegbmx, uturnbegbmy);
                ll.lon = plateView.BitmapXY2Lon (uturnbegbmx, uturnbegbmy);
                return "ob.45";
            }
        };

        // line on 45-deg offset inbound
        private final CIFPStep pistep3 = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appendTurn (sb, td);
                appCourse (sb, c_dir);
                sb.append (" toward ");
                sb.append (parms.get ("wp"));
                return sb.toString ();
            }

            // draw 180-deg turn leading into this step as a fillet
            // ...so user will get the 'turn in 10 9 8 7 6...' countdown message
            // called when pistep2 is current
            @Override
            public void drawFillet ()
            {
                int cbmx = (uturnbegbmx + uturnendbmx) / 2;
                int cbmy = (uturnbegbmy + uturnendbmy) / 2;
                float radius = Mathf.hypot (uturnbegbmx - uturnendbmx, uturnbegbmy - uturnendbmy) / 2.0F;
                boolean left = td == 'L';
                float start = left ? (a_tru - 135.0F) : (a_tru + 135.0F);
                drawFilletArc (cbmx, cbmy, radius, start, 180.0F, left);
            }

            @Override
            public void drawStepDots ()
            {
                drawStepCourseToInterceptRadial (navpt, a_tru);
            }

            // VirtNav dial OBS should show inbound 45deg course
            @Override
            public float getVNOBSTrue ()
            {
                return b_tru + 180.0F;
            }

            // VirtNav waypoint should be point at end of inbound 45deg leg
            @Override
            public String getVNWaypoint (LatLon ll)
            {
                ll.lat = plateView.BitmapXY2Lat (endptbmx, endptbmy);
                ll.lon = plateView.BitmapXY2Lon (endptbmx, endptbmy);
                return "ib.45";
            }
        };

        public void init1 () throws Exception
        {
            // get magnetic courses for the 4 parts
            int toc = Integer.parseInt (parms.get ("toc"));
            td = parms.get ("td").charAt (0);
            b_dir = toc;  // after 45deg turn outbound
            switch (td) {
                case 'L': {
                    dt = 'R';
                    a_dir = (b_dir + 3150) % 3600;
                    break;
                }
                case 'R': {
                    dt = 'L';
                    a_dir = (b_dir +  450) % 3600;
                    break;
                }
                default: throw new RuntimeException ("bad PI turn dir " + td);
            }
            c_dir = (b_dir + 1800) % 3600;  // after 180deg turn
            d_dir = (a_dir + 1800) % 3600;  // after 45deg turn inbound

            // get beacon PT navigates by
            navwp = findWaypoint (parms.get ("wp"), navpt);

            // get true course values for the outbound leg from beacon
            // and for the first 45deg turn outbound
            a_tru = getTCDeg (a_dir, navwp);  // outbound from beacon
            b_tru = getTCDeg (b_dir, navwp);  // after 45deg turn outbound

            // also let anyone who cares what the inbound heading to beacon is
            // this is the TC after the final 45deg turn inbound
            inbound = getTCDeg (d_dir, navwp);
        }

        public void init2 ()
        {
            // see which is farther from the airport, the beacon or the FAF
            // that is the point we use for timing
            // this will select VELAN for the LWM VOR 23 approach
            CIFPLeg fafleg = segment.approach.finalseg.getFafLeg ();
            if (fafleg != null) {
                Waypoint fafwp = fafleg.getFafWaypt ();
                float dist_nav_to_apt = Lib.LatLonDist (navwp.lat, navwp.lon, airport.lat, airport.lon);
                float dist_faf_to_apt = Lib.LatLonDist (fafwp.lat, fafwp.lon, airport.lat, airport.lon);
                extendnm = dist_faf_to_apt - dist_nav_to_apt;
                if (extendnm < 0.0F) extendnm = 0.0F;
            }
        }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (pistep1);
            steps.addLast (pistep2);
            steps.addLast (pistep3);
        }
    }

    /**
     * Arc around a fix to a fix.
     *   PAKT ILS Z 11 from ANN
     */
    private class CIFPLeg_RF extends CIFPLeg {
        private char turndir;
        private float arcradbmp, arcradnm, endradtru;
        private Point ctrpt = new Point ();
        private Point endpt = new Point ();
        private Waypoint ctrwp, endwp;

        private final CIFPStep rfstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                sb.append ("arc ");
                sb.append (turndir);
                float stdradbmp = stdcirdradsec * bmpixpersec;
                String turnrt = Lib.FloatNTZ (stdradbmp / arcradbmp);
                if (!turnrt.equals ("0")) {
                    sb.append (' ');
                    if (!turnrt.equals ("1")) {
                        sb.append (turnrt);
                        sb.append ('\u00D7');  // multiply 'x' symbol
                    }
                    sb.append ("std");
                }
                sb.append ('\u2192');
                sb.append (endwp.ident);
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots () throws Exception
            {
                // calc what radial from centerpoint we are currently on
                float begradtru = trueCourse (ctrpt.x, ctrpt.y, curposbmx, curposbmy);

                // draw dots along arc
                switch (turndir) {
                    case 'L': {
                        if (begradtru < endradtru) begradtru += 360.0F;
                        drawStepDotArc (ctrpt.x, ctrpt.y, arcradbmp, endradtru - 90.0F, begradtru - endradtru);
                        curposhdg = endradtru - 90.0F;
                        break;
                    }
                    case 'R': {
                        if (begradtru > endradtru) begradtru -= 360.0F;
                        drawStepDotArc (ctrpt.x, ctrpt.y, arcradbmp, begradtru - 90.0F, endradtru - begradtru);
                        curposhdg = endradtru + 90.0F;
                        break;
                    }
                    default: throw new Exception ("bad turn dir " + turndir);
                }

                // return ending alt,hdg,lat,lon
                updateAlt ();
                curposbmx = endpt.x;
                curposbmy = endpt.y;
            }

            // draw fillet from end of incoming line to the beginning of this arc
            // this step is nex-to-be-current (ignoring runts).
            @Override
            public void drawFillet ()
            {
                // since we are an arc, no need to draw a lead-in fillet
                // also, this arc probably isn't standard rate, so don't overwrite as a fillet
            }

            // have VirtNav dial show pilot what heading they should be on now
            // ...assuming they are right on the arc
            @Override
            public float getVNOBSTrue ()
            {
                float tc_from_ctr_to_acrft = Lib.LatLonTC (ctrwp.lat, ctrwp.lon,
                        wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                return (turndir == 'L') ?
                        tc_from_ctr_to_acrft - 90.0F :
                        tc_from_ctr_to_acrft + 90.0F;
            }

            // make up a lat/lon so that VirtNav needle and DME works out
            @Override
            public String getVNWaypoint (LatLon ll)
            {
                // get true course from center of arc to where aircraft currently is
                float tc_from_ctr_to_acrft = Lib.LatLonTC (ctrwp.lat, ctrwp.lon,
                        wairToNow.currentGPSLat, wairToNow.currentGPSLon);

                // find out where on arc that line intersects
                float arc_lat = Lib.LatHdgDist2Lat (ctrwp.lat, tc_from_ctr_to_acrft, arcradnm);
                float arc_lon = Lib.LatLonHdgDist2Lon (ctrwp.lat, ctrwp.lon, tc_from_ctr_to_acrft, arcradnm);

                // find out how far the aircraft has to go to get to the desired end point
                float dist_to_go = Lib.LatLonDist (endwp.lat, endwp.lon,
                        wairToNow.currentGPSLat, wairToNow.currentGPSLon);

                // get where the nose would be pointed ideally if aircraft was on the arc
                float ideal_tc_ahead = (turndir == 'L') ?
                        tc_from_ctr_to_acrft - 90.0F :
                        tc_from_ctr_to_acrft + 90.0F;

                // make up a lat,lon ahead by the corresponding distance so that
                // needle will deflect to indicate where arc is and DME will show
                // how far airplane actually is from the end point
                ll.lat = Lib.LatHdgDist2Lat (arc_lat, ideal_tc_ahead, dist_to_go);
                ll.lon = Lib.LatLonHdgDist2Lon (arc_lat, arc_lon, ideal_tc_ahead, dist_to_go);

                // return name of end point in DME window
                return endwp.ident;
            }
        };

        public void init1 () throws Exception
        {
            ctrwp   = findWaypoint (parms.get ("cp"), ctrpt);
            endwp   = findWaypoint (parms.get ("ep"), endpt);
            turndir = parms.get ("td").charAt (0);

            // calc what radial from centerpoint we will end up on
            // and the corresponding distance
            endradtru = trueCourse (ctrpt.x, ctrpt.y, endpt.x, endpt.y);
            arcradbmp = Mathf.hypot (ctrpt.x - endpt.x, ctrpt.y - endpt.y);
            arcradnm  = Lib.LatLonDist (ctrwp.lat, ctrwp.lon, endwp.lat, endwp.lon);
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (rfstep);
        }
    }

    /**
     * Look for waypoint in database.
     * Throw exception if not found.
     */
    private Waypoint findWaypoint (String wayptid, Point bmxy) throws Exception
    {
        Waypoint waypt = plateView.FindWaypoint (wayptid);
        if (waypt == null) throw new Exception ("waypoint <" + wayptid + "> not found");
        bmxy.x = plateView.LatLon2BitmapX (waypt.lat, waypt.lon);
        bmxy.y = plateView.LatLon2BitmapY (waypt.lat, waypt.lon);
        return waypt;
    }

    /**
     * Draw step dot course from current position to given point.
     *  Input:
     *   endbmx,y = end point for dots on bitmap
     *   curposbmx,y = starting point for dots on bitmap
     *  Output:
     *   curposbmx,y = updated to endbmx,endbmy
     *   curposhdg = true course from starting point to end point
     */
    private void drawStepCourseToPoint (int endbmx, int endbmy)
    {
        drawStepDotLine (curposbmx, curposbmy, endbmx, endbmy);
        curposhdg = trueCourse (curposbmx, curposbmy, endbmx, endbmy);
        curposbmx = endbmx;
        curposbmy = endbmy;
    }

    /**
     * Draw step dots from current position and heading until intercepts
     * the given distance from the navaid waypoint.
     *  Input:
     *   navpt = bitmap X,Y of navaid
     *   radius = arc radius in bitmap bixels
     *   curposbmx,y = starting point
     *   curposhdg = current heading
     */
    private PointF dsctidpp = new PointF ();
    private PointF dsctidpm = new PointF ();
    private void drawStepCourseToInterceptDist (Point navpt, float radius)
    {
        // find possibly two points along course line
        float farendbmx = curposbmx + Mathf.sindeg (curposhdg) * 1024.0F;
        float farendbmy = curposbmy - Mathf.cosdeg (curposhdg) * 1024.0F;
        if (!Lib.lineIntersectCircle (curposbmx, curposbmy, farendbmx, farendbmy,
                navpt.x, navpt.y, radius, dsctidpp, dsctidpm)) {
            throw new IllegalArgumentException ();
        }

        // pick the one that is closest ahead
        float start_to_pp = trueCourse (curposbmx, curposbmy, dsctidpp.x, dsctidpp.y);
        float start_to_pm = trueCourse (curposbmx, curposbmy, dsctidpm.x, dsctidpm.y);
        int aheadx = 0;
        int aheady = 0;
        float bestdist = Float.MAX_VALUE;
        if ((angleDiffU (start_to_pp - curposhdg) < 90.0F) && (bestdist > start_to_pp)) {
            bestdist = start_to_pp;
            aheadx   = Math.round (dsctidpp.x);
            aheady   = Math.round (dsctidpp.y);
        }
        if ((angleDiffU (start_to_pm - curposhdg) < 90.0F) && (bestdist > start_to_pm)) {
            bestdist = start_to_pm;
            aheadx   = Math.round (dsctidpm.x);
            aheady   = Math.round (dsctidpm.y);
        }
        if (bestdist == Float.MAX_VALUE) throw new IllegalArgumentException ();

        // draw step dots to the closest one ahead
        drawStepDotLine (curposbmx, curposbmy, aheadx, aheady);
        curposbmx = aheadx;
        curposbmy = aheady;
    }

    /**
     * Draw step dots from current position and heading until intercepts
     * the given radial from the navaid waypoint.
     *  Input:
     *   navpt = navaid the radial is from
     *   radial = radial from that navaid to intercept (true degrees)
     *   curposbmx,y = current position
     *   curboxhdg = current heading
     *  Output:
     *   dots drawn
     *   curposbmx,y = updated to intersection point
     */
    private void drawStepCourseToInterceptRadial (Point navpt, float radial)
    {
        // compute some point way ahead of us
        float aheadbmx = curposbmx + Mathf.sindeg (curposhdg) * 1024.0F;
        float aheadbmy = curposbmy - Mathf.cosdeg (curposhdg) * 1024.0F;

        // compute some point way out on radial from navaid
        float nextbmx  = navpt.x + Mathf.sindeg (radial) * 1024.0F;
        float nextbmy  = navpt.y - Mathf.cosdeg (radial) * 1024.0F;

        // find where the two lines intersect
        float interx = Lib.lineIntersectX (curposbmx, curposbmy, aheadbmx, aheadbmy,
                navpt.x, navpt.y, nextbmx, nextbmy);
        float intery = Lib.lineIntersectY (curposbmx, curposbmy, aheadbmx, aheadbmy,
                navpt.x, navpt.y, nextbmx, nextbmy);

        // draw dots to that intersection and set new end point
        drawStepCourseToPoint (Math.round (interx), Math.round (intery));
    }

    /**
     * Draw a U-turn at standard rate starting at current position and heading.
     */
    private void drawStepUTurn (char td)
    {
        // get turn radius
        float radiusbmp = stdcirdradsec * bmpixpersec;

        // draw dots around the arc
        float hdgtoctr;
        switch (td) {
            case 'L': {
                hdgtoctr = curposhdg - 90.0F;
                break;
            }
            case 'R': {
                hdgtoctr = curposhdg + 90.0F;
                break;
            }
            default: throw new IllegalArgumentException ("td");
        }

        float ctrbmx = curposbmx + Mathf.sindeg (hdgtoctr) * radiusbmp;
        float ctrbmy = curposbmy - Mathf.cosdeg (hdgtoctr) * radiusbmp;
        drawStepDotArc (ctrbmx, ctrbmy, radiusbmp, curposhdg + 180.0F, 180.0F);

        curposbmx  = Math.round (ctrbmx * 2.0F) - curposbmx;
        curposbmy  = Math.round (ctrbmy * 2.0F) - curposbmy;
        curposhdg += 180.0F;
        if (curposhdg >= 360.0F) curposhdg -= 360.0F;
    }

    /**
     * Draw a straight-ahead path for one minute starting at current position.
     */
    private void drawStepOneMinLine ()
    {
        float dist = HOLDLEGSEC * bmpixpersec;
        int endbmx = curposbmx + Math.round (Mathf.sindeg (curposhdg) * dist);
        int endbmy = curposbmy - Math.round (Mathf.cosdeg (curposhdg) * dist);
        drawStepDotLine (curposbmx, curposbmy, endbmx, endbmy);
        curposbmx  = endbmx;
        curposbmy  = endbmy;
    }

    /**
     * Draw step dots in a line segment shape.
     * @param sx = start bitmap x
     * @param sy = start bitmap y
     * @param ex = end bitmap x
     * @param ey = end bitmap y
     */
    private void drawStepDotLine (int sx, int sy, int ex, int ey)
    {
        // draw dots
        int i = drawStepDotIndex (4);
        cifpDotBitmapXYs[i++] = ey;
        cifpDotBitmapXYs[i++] = ex;
        cifpDotBitmapXYs[i++] = sy;
        cifpDotBitmapXYs[i]   = sx;

        // see how close aircraft is to the line and save if closest in step so far
        int dist = Math.round (Lib.distToLineSeg (acraftbmx, acraftbmy, sx, sy, ex, ey));
        if (smallestAcraftDist > dist) smallestAcraftDist = dist;
    }

    /**
     * Draw step dots in an arc shape.
     * @param cbmx = arc center (bitmap X)
     * @param cbmy = arc center (bitmap Y)
     * @param radius = arc radius (bitmap pixels)
     * @param start = start radial (degrees clockwise from due EAST)
     * @param sweep = length of arc (degrees clockwise from start)
     */
    private void drawStepDotArc (float cbmx, float cbmy, float radius,
                                 float start, float sweep)
    {
        // draw dots
        int i = drawStepDotIndex (6);
        cifpDotBitmapXYs[i++] = sweep;
        cifpDotBitmapXYs[i++] = start;
        cifpDotBitmapXYs[i++] = cbmy;
        cifpDotBitmapXYs[i++] = cbmx;
        cifpDotBitmapXYs[i++] = radius;
        cifpDotBitmapXYs[i]   = Float.NaN;

        // see if aircraft is close to the fillet arc
        // this keeps the arc's step current while flying it
        int dist = Math.round (Lib.distOfPtFromArc (cbmx, cbmy, radius, start, sweep, acraftbmx, acraftbmy));
        if (smallestAcraftDist > dist) smallestAcraftDist = dist;
    }

    /**
     * Get next index in dicfDotBitmapXYs[] for drawing dotted lines/arcs
     */
    private int drawStepDotIndex (int n)
    {
        // make sure array is big enough to hold canvas x,y
        int i = cifpDotIndex;
        int length = cifpDotBitmapXYs.length;
        if (i + n > length) {
            int expand = length / 2 + n;
            float[] xa = new float[length+expand];
            System.arraycopy (cifpDotBitmapXYs, 0, xa, 0, i);
            cifpDotBitmapXYs = xa;
        }

        // return index where the stuff goes
        cifpDotIndex = i + n;
        return i;
    }

    /**
     * Draw fillet arc as part of the current step,
     * although it was computed by the next-to-current step
     */
    private void drawFilletArc (float cbmx, float cbmy, float radius,
                                float start, float sweep, boolean left)
    {
        filletCenterX = cbmx;
        filletCenterY = cbmy;
        filletRadius  = radius;
        filletStart   = start;
        filletSweep   = sweep;

        // see if aircraft is close to the fillet arc
        // this keeps the arc's step current while flying it
        int dist = Math.round (Lib.distOfPtFromArc (cbmx, cbmy, radius, start, sweep, acraftbmx, acraftbmy));
        CIFPStep step = cifpSteps[currentStep];
        if (step.acrftdist > dist) step.acrftdist = dist;

        // save start and end point information
        if (left) {
            step.filetdir = "L\u21B6";  // left curly arrow
            step.filetbmx = Math.round (cbmx + Mathf.cosdeg (start + sweep) * radius);
            step.filetbmy = Math.round (cbmy + Mathf.sindeg (start + sweep) * radius);
            step.filetitc = start + sweep;
            step.filetftc = start;
        } else {
            step.filetdir = "R\u21B7";  // right curly arrow
            step.filetbmx = Math.round (cbmx + Mathf.cosdeg (start) * radius);
            step.filetbmy = Math.round (cbmy + Mathf.sindeg (start) * radius);
            step.filetitc = start + 180.0F;
            step.filetftc = start + 180.0F + sweep;
        }
    }

    /**
     * Compute true course (degrees) from begin point to end point
     *   endbmx = begbmx + radius * sin heading  =>  sin heading = (endbmx - begbmx) / radius
     *   endbmy = begbmy - radius * cos heading  =>  cos heading = (begbmy - endbmy) / radius
     *   tan heading = sin heading / cos heading = (endbmx - begbmx) / (begbmy - endbmy)
     */
    private static float trueCourse (float begbmx, float begbmy, float endbmx, float endbmy)
    {
        return Mathf.toDegrees (Mathf.atan2 (endbmx - begbmx, begbmy - endbmy));
    }

    /**
     * Find unsigned difference of two angles.
     * @param diff = raw difference
     * @return difference in range 0..180
     */
    private static float angleDiffU (float diff)
    {
        diff = Math.abs (diff);
        while (diff >= 360.0F) diff -= 360.0F;
        if (diff > 180.0F) diff = 360.0F - diff;
        return diff;
    }

    /**
     * Display error message dialog box.
     * Also log the error.
     */
    private void errorMessage (String title, Exception e)
    {
        Log.e (TAG, "CIFP error: " + title, e);
        String message = e.getMessage ();
        if ((message == null) || message.equals ("")) message = e.getClass ().getName ();
        errorMessage (title, message);
    }

    private void errorMessage (String title, String message)
    {
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle (title);
        adb.setMessage (message);
        adb.setPositiveButton ("OK", null);
        adb.show ();
    }
}
