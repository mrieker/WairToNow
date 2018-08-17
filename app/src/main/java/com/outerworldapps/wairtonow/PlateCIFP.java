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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Manage CIFP display on the IAP plates.
 */
public class PlateCIFP {
    public final static String TAG = "WairToNow";

    public final static boolean LINEPATHEFFECTWORKS = false;

    private final static int CLIMBFT_NM =   200;  // default climb rate
    private final static int MINSPEEDKT =    60;  // minimum speed knots
    private final static int HOLDLEGSEC =    60;  // number of seconds for hold legs
    private final static int STDDEGPERSEC =   3;  // standard turn degrees per second
    private final static int MINFILLET  =     6;  // min degrees turn for fillet
    private final static int MAXFILLET  =   174;  // max degrees turn for fillet
    private final static int TURNWRNSEC =    10;  // number of seconds to warn for turn
    private final static int SHOWAPPIDSEC =   5;  // show approach id when selected
    private final static double RUNTNM   =  0.5;  // legs shorter than this are runts
    private final static double TERMDEFLECT = 1.0;  // nav dial terminal mode deflection +- nm

    private static class NextTurn {
        public String direction;
        public double truecourse;
    }

    private final static double stdturnradsec = 180.0 / Math.PI / STDDEGPERSEC;

    private final static String CIFPSEG_FINAL = "~f~";
    private final static String CIFPSEG_MISSD = "~m~";
    private final static String CIFPSEG_REFNV = "~r~";
    private final static String CIFPSEG_RADAR = "(rv)";

    private final static char DIRTOARR = '\u2192';      // 'direct to' arrow
    private final static HashMap<String,Character> circlingcodes = makeCirclingCodes ();
    private final static String turnleft  = "L\u21B6";  // left curly arrow
    private final static String turnright = "R\u21B7";  // right curly arrow

    private AlertDialog selectMenu;
    private boolean   drawTextEnable;
    private boolean   gotDrawError;
    public  CIFPSegment cifpSelected;
    private CIFPStep[] cifpSteps;
    private DashPathEffect dotsEffect;
    private FilletArc filletArc = new FilletArc ();
    private double     acraftalt;    // aircraft altitude feet
    private double     acrafthdg;    // aircraft heading degrees
    private double     acraftbmx;    // aircraft bitmap x,y
    private double     acraftbmy;
    private double     aptmagvar;
    private double     bmpixpernm;
    private double     bmpixpersec;
    private double     cifpTextAscent, cifpTextHeight;
    private double     curposalt;    // altitude feet
    private double     curposbmx;
    private double     curposbmy;
    private double     curposhdg;    // heading degrees true
    private double     runtbmpix;
    private double     smallestAcraftDist;
    private double     stdturnradbmp;
    private double     vnFinalDist;
    private double     vnFinalTC;
    private double     vnGSFtPerNM;
    private double     vnRwyGSElev;
    private double[]   cifpDotBitmapXYs = new double[32];
    private GetCIFPSegments getCIFPSegments;
    private HashMap<String,SelectButton> selectButtons;
    private int       cifpDotIndex;
    private int       currentStep;
    private int       mapIndex;     // index of first step of MAP in cifpSteps[]
    private int       selectButtonHeight;
    private int       startCyans;   // start of cyans in cifpDotBitmapXYs[]
    private int       startGreens;  // start of greens in cifpDotBitmapXYs[]
    private long      cifpButtonDownAt = Long.MAX_VALUE;
    private long      lastGPSUpdateTime;
    private long      selectedTime;
    private NextTurn  nextTurn = new NextTurn ();
    private Paint     cifpBGPaint = new Paint ();
    private Paint     cifpTxPaint = new Paint ();
    private Paint     dotsPaint   = new Paint ();
    private Paint     selectButtonPaint = new Paint ();
    private Path      cifpButtonPath = new Path ();
    private PlateView.IAPRealPlateImage plateView;
    private PointD    dsctidpp = new PointD ();
    private PointD    dsctidpm = new PointD ();
    private PointD    vnFafPoint = new PointD ();
    private PointD    vnRwyPoint = new PointD ();
    private Rect      textBounds = new Rect ();
    private RectF     cifpButtonBounds = new RectF ();  // where CIFP dialog display button is on canvas
    private RectF     drawArcBox = new RectF ();
    private String    plateid;
    private String    turntoline;
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

    public PlateCIFP (WairToNow wtn, PlateView.IAPRealPlateImage pv, Waypoint.Airport apt, String pi)
    {
        wairToNow = wtn;
        plateView = pv;
        airport   = apt;
        plateid   = pi;

        float thick = wairToNow.thickLine;
        float thin  = wairToNow.thinLine;

        cifpBGPaint.setColor (Color.BLACK);
        cifpBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        cifpBGPaint.setStrokeWidth (thick);

        cifpTxPaint.setColor (Color.YELLOW);
        cifpTxPaint.setStyle (Paint.Style.FILL);
        cifpTxPaint.setStrokeWidth (3);
        cifpTxPaint.setTextSize (wairToNow.textSize * 1.125F);
        cifpTxPaint.setTextAlign (Paint.Align.RIGHT);
        cifpTxPaint.setTypeface (Typeface.MONOSPACE);

        dotsEffect = new DashPathEffect (new float[] { thin, thick }, 0);
        dotsPaint.setPathEffect (dotsEffect);
        dotsPaint.setStyle (Paint.Style.STROKE);
        dotsPaint.setStrokeCap (Paint.Cap.ROUND);
        dotsPaint.setStrokeWidth (thin);

        selectButtonPaint.setTextSize (wairToNow.textSize);

        cifpTxPaint.getTextBounds ("M", 0, 1, textBounds);
        cifpTextAscent = textBounds.height ();
        cifpTextHeight = cifpTxPaint.getTextSize ();

        aptmagvar = airport.GetMagVar (0.0);

        getCIFPSegments = new GetCIFPSegments ();
    }

    /**
     * Mouse pressed on the plate somewhere.
     */
    public void GotMouseDown (double x, double y)
    {
        // check for button in lower right corner of plate
        if (cifpButtonBounds.contains ((float) x, (float) y)) {
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
                    if (bounds.contains ((float) x, (float) y)) {
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
        if (cifpApproaches == null) getCIFPSegments.load ();
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
     * Fill in NavDialView with current tracking info.
     * Also get status string saying what the step is doing.
     */
    public String getVNTracking (NavDialView navDial)
    {
        if (cifpSelected == null) return null;

        // set nav dial according to the current step
        // but if in a fillet, set nav dial according to next step
        cifpSteps[currentStep].getVNTracking (navDial);

        // default status string is corresponding text line
        String status = cifpTextLines[currentStep+1];

        // but if there is a 'turn to ... in ... sec', use it as status instead
        if ((turntoline != null) && turntoline.contains ("\u00B0")) status = turntoline;

        return status;
    }

    /**
     * A new GPS location was received, update state.
     */
    public void SetGPSLocation ()
    {
        // we might get double calls (via WaypointView and VirtNavView),
        // so reject redundant GPS updates
        if (lastGPSUpdateTime != wairToNow.currentGPSTime) {
            lastGPSUpdateTime = wairToNow.currentGPSTime;

            acraftalt = wairToNow.currentGPSAlt * Lib.FtPerM;
            acrafthdg = wairToNow.currentGPSHdg;
            acraftbmx = plateView.LatLon2BitmapX (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
            acraftbmy = plateView.LatLon2BitmapY (wairToNow.currentGPSLat, wairToNow.currentGPSLon);

            double spd = wairToNow.currentGPSSpd;

            // we need a minimum speed so we can draw dots
            if (spd < MINSPEEDKT / 3600.0 * Lib.MPerNM) {
                spd = MINSPEEDKT / 3600.0 * Lib.MPerNM;
            }

            // how many bitmap pixels we will cross in a second at our current speed
            bmpixpersec = bmpixpernm / Lib.MPerNM * spd;

            // radius of turns at standard rate
            stdturnradbmp = stdturnradsec * bmpixpersec;

            // make sure we have a segment selected for display
            if (cifpSelected != null) {
                UpdateState ();
            }
        }
    }

    // update state regardless of whether plate is visible or not
    // in case user comes back to plate later or the state is being
    // used by VirtNavView without viewing the plate itself
    private void UpdateState ()
    {
        // if first time through, save aircraft position as starting point
        // this sets the starting position where the initial line is drawn from
        if (currentStep < 0) {
            CIFPStep step0 = cifpSteps[0];
            step0.begptalt = acraftalt;
            step0.begpthdg = acrafthdg;
            step0.begptbmx = acraftbmx;
            step0.begptbmy = acraftbmy;
            currentStep = 0;
            Log.d (TAG, "PlateCIFP*: currentStep=0 <" + cifpSteps[0].getTextLine () + ">");
        }

        // gather text strings and locations of all dots
        cifpDotIndex = 0;
        startGreens  = Integer.MAX_VALUE;
        startCyans   = Integer.MAX_VALUE;
        int nsteps   = cifpSteps.length;
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
            // ie, once a step becomes current, its start point doesn't change
            // (with the exception of radar vector CIFPLeg_rv).
            // subsequent steps start where the previous step ended.
            // this is where the airplane is at the beginning of the step,
            // assuming the previous step was flown perfectly.
            if (i == currentStep) {
                curposalt = step.begptalt;
                curposhdg = step.begpthdg;
                curposbmx = step.begptbmx;
                curposbmy = step.begptbmy;
            } else {
                // the previous step may have changed its ending point
                // based on aircraft speed, etc, so set next step's
                // starting point
                step.begptalt = curposalt;
                step.begpthdg = curposhdg;
                step.begptbmx = curposbmx;
                step.begptbmy = curposbmy;
            }

            // compute dots that make up step and put in array
            // compute alt,hdg,lat,lon at end of this step
            // possibly alters step's internal state
            smallestAcraftDist = Double.MAX_VALUE;
            try {
                step.drawStepDots ();
            } catch (Throwable t) {
                if (!gotDrawError) {
                    Log.e (TAG, "error drawing CIFP path", t);
                    gotDrawError = true;
                }
            }
            step.acrftdist = smallestAcraftDist;

            // save the values saying where the step ended.
            // this is where the airplane is at the end of the step
            // assuming the step was flown perfectly.
            step.endptalt = curposalt;
            step.endpthdg = curposhdg;
            step.endptbmx = curposbmx;
            step.endptbmy = curposbmy;

            // get text string for text box
            cifpTextLines[i+1] = step.getTextLine ();
        }

        // draw fillet arc showing turn at end of current step to next step.
        // the next step actually determines the shape of the fillet arc,
        // but the fillet is considered part of the current step so the current step
        // stays magenta.  it's possible that the next step is a runt, in which case
        // we ignore it and use the one after that.
        filletArc.turndir = 0;
        filletArc.start   = Double.NaN;
        filletArc.startc  = Double.NaN;
        filletArc.stoptc  = Double.NaN;
        filletArc.sweep   = Double.NaN;
        for (int i = currentStep; ++ i < nsteps;) {
            CIFPStep ns = cifpSteps[i];
            if (!ns.isRuntStep ()) {
                ns.drawFillet ();
                if (filletArc.turndir != 0) {
                    if (Double.isNaN (filletArc.start) || Double.isNaN (filletArc.startc) ||
                            Double.isNaN (filletArc.stoptc) || Double.isNaN (filletArc.sweep)) {
                        throw new RuntimeException ("filletArc not filled in");
                    }
                    // it drew a fillet arc going from the current step to that next step (ns)
                    // see how far the airplane is from the fillet arc
                    double distfromarc = Lib.distOfPtFromArc (filletArc.x, filletArc.y,
                            stdturnradbmp, filletArc.start, filletArc.sweep, acraftbmx, acraftbmy);
                    // if the airplane is close to the fillet arc,
                    // mark airplane as being close to the current step
                    // in order to keep current step current until airplane
                    // finishes flying the whole fillet, ie, until airplane
                    // gets closer to actual next step path than it is to
                    // this fillet
                    CIFPStep cs = cifpSteps[currentStep];
                    if (cs.acrftdist > distfromarc) {
                        cs.acrftdist = distfromarc;
                    }
                }
                break;
            }
        }

        // warn pilot of next turn
        double bmptoturn = cifpSteps[currentStep].bmpToNextTurn (nextTurn);
        if (bmptoturn < 0.0) bmptoturn = 0.0;
        int sectoturn = (int) Math.round (bmptoturn / bmpixpersec);
        if ((sectoturn > TURNWRNSEC) || (nextTurn.direction == null)) {
            // it will be a while, display distance and time to turn
            double nm = bmptoturn / bmpixpernm;
            turntoline = String.format (Locale.US, "%.1fnm  %02d:%02d", nm, sectoturn / 60, sectoturn % 60);
        } else {
            // near or in the turn, display message saying to turn
            int maghdg = (int) ((Math.round (nextTurn.truecourse + aptmagvar) + 719) % 360) + 1;
            if (sectoturn > 0) {
                turntoline = String.format (Locale.US, "%s %03d\u00B0 in %d sec", nextTurn.direction, maghdg, sectoturn);
            } else {
                turntoline = String.format (Locale.US, "%s %03d\u00B0 now", nextTurn.direction, maghdg);
            }
        }

        // advance current step if aircraft is closer to dots in next step
        // than it is to dots in the current step.
        // but we must also skip any steps that are runts,
        // as we wouldn't be able to see any difference
        // in distance away from them and would get stuck.
        double csacrftdist = cifpSteps[currentStep].acrftdist;
        for (int i = currentStep; ++ i < nsteps;) {
            CIFPStep ns = cifpSteps[i];
            if (!ns.isRuntStep ()) {
                double nsacrftdist = ns.acrftdist;
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
     * The buttons appear on the plate at the location of the fix
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
                        LinkedList<Waypoint> waypts = Waypoint.GetWaypointsByIdent (seg.segid, wairToNow);
                        double bestdist = 200.0;
                        for (Waypoint wp : waypts) {
                            double dist = Lib.LatLonDist (wp.lat, wp.lon, airport.lat, airport.lon);
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
        public double bmpx, bmpy;  // bitmap pixel where button goes (waypoint location)
        public HashMap<String,RectF> appBounds = new HashMap<> ();  // indexed by app.type
        public int idwidth;       // width of segid string (canvas pixels)
        public String clicked;
        public String segid;      // "(rv)", "LADTI", etc
        public TreeMap<String,CIFPSegment> segments = new TreeMap<> ();  // indexed by app.type

        /**
         * Draw text strings for the waypoint that act as buttons
         * to select that waypoint as the starting point for the
         * approach.
         *
         * If there is just one approach for a given waypoint, just show
         * the waypoint name.  If there are more than one approach type
         * (such as ILS and LOC), show the approach types as well as the
         * waypoint name.
         */
        public void drawButton (Canvas canvas)
        {
            // get canvas X,Y where the waypoint is located
            double canx = plateView.BitmapX2CanvasX (bmpx);
            double cany = plateView.BitmapY2CanvasY (bmpy);

            if (segments.size () == 1) {
                String apptype = segments.keySet ().iterator ().next ();

                // draw black backgound rectangle to draw text on
                int totw = idwidth + selectButtonHeight;
                canvas.drawRect ((float) canx, (float) (cany - selectButtonHeight), (float) (canx + totw), (float) cany, cifpBGPaint);

                // draw waypoint id string on plate near its location
                selectButtonPaint.setColor (Color.GREEN);
                canx += selectButtonHeight / 2.0;
                canvas.drawText (segid, (float) canx, (float) cany, selectButtonPaint);

                // make the id string clickable
                RectF bounds = appBounds.get (apptype);
                bounds.set ((float) canx, (float) (cany - selectButtonHeight), (float) (canx + idwidth), (float) cany);
            } else {

                // draw black background rectangle to draw text on
                int totw = idwidth + selectButtonHeight;
                for (String apptype : segments.keySet ()) {
                    CIFPSegment seg = segments.get (apptype);
                    totw += seg.approach.typeWidth + selectButtonHeight;
                }
                canvas.drawRect ((float) canx, (float) (cany - selectButtonHeight), (float) (canx + totw), (float) cany, cifpBGPaint);

                // draw waypoint id string on plate near its location
                selectButtonPaint.setColor (Color.CYAN);
                canx += selectButtonHeight / 2.0;
                canvas.drawText (segid, (float) canx, (float) cany, selectButtonPaint);
                canx += idwidth + selectButtonHeight;

                // draw app id for each approach following the waypoint id
                // remember the boundary of the text strings so they can be clicked
                selectButtonPaint.setColor (Color.GREEN);
                for (String apptype : segments.keySet ()) {
                    int w = segments.get (apptype).approach.typeWidth;
                    RectF bounds = appBounds.get (apptype);
                    bounds.set ((float) canx, (float) (cany - selectButtonHeight), (float) (canx + w), (float) cany);
                    canvas.drawText (apptype, (float) canx, (float) cany, selectButtonPaint);
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
        // maybe draw transition segment select buttons over corresponding fix
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
                double x0 = cifpDotBitmapXYs[--i];
                int color = (i > startCyans) ? Color.CYAN :
                        (i > startGreens) ? Color.GREEN :
                                Color.MAGENTA;
                dotsPaint.setColor (color);
                if (!Double.isNaN (x0)) {
                    double y0 = cifpDotBitmapXYs[--i];
                    double x1 = cifpDotBitmapXYs[--i];
                    double y1 = cifpDotBitmapXYs[--i];
                    x0 = plateView.BitmapX2CanvasX (x0);
                    y0 = plateView.BitmapY2CanvasY (y0);
                    x1 = plateView.BitmapX2CanvasX (x1);
                    y1 = plateView.BitmapY2CanvasY (y1);
                    if (LINEPATHEFFECTWORKS) {
                        canvas.drawLine ((float) x0, (float) y0, (float) x1, (float) y1, dotsPaint);
                    } else {
                        dotsPaint.setPathEffect (null);
                        double len = Math.hypot (x1 - x0, y1 - y0);
                        double xperlen = (x1 - x0) / len;
                        double yperlen = (y1 - y0) / len;
                        double thick = wairToNow.thickLine;
                        double thin  = wairToNow.thinLine;
                        for (double t = 0; t < len; t += thick) {
                            double xa = x0 + xperlen * t;
                            double ya = y0 + yperlen * t;
                            t += thin;
                            if (t > len) t = len;
                            double xb = x0 + xperlen * t;
                            double yb = y0 + yperlen * t;
                            canvas.drawLine ((float) xa, (float) ya, (float) xb, (float) yb, dotsPaint);
                        }
                    }
                } else {
                    double radius = cifpDotBitmapXYs[--i];
                    double centx  = cifpDotBitmapXYs[--i];
                    double centy  = cifpDotBitmapXYs[--i];
                    double start  = cifpDotBitmapXYs[--i];
                    double sweep  = cifpDotBitmapXYs[--i];
                    drawArcBox.set (
                            (float) plateView.BitmapX2CanvasX (centx - radius),
                            (float) plateView.BitmapY2CanvasY (centy - radius),
                            (float) plateView.BitmapX2CanvasX (centx + radius),
                            (float) plateView.BitmapY2CanvasY (centy + radius));
                    dotsPaint.setPathEffect (dotsEffect);
                    canvas.drawArc (drawArcBox, (float) start, (float) sweep, false, dotsPaint);
                }
            }

            // draw fillet arc in magenta
            if (filletArc.turndir != 0) {
                dotsPaint.setPathEffect (dotsEffect);
                filletArc.draw (canvas, dotsPaint);
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
            String firstline;
            if (SystemClock.uptimeMillis () < selectedTime + SHOWAPPIDSEC * 1000) {
                // just selected approach, show its name for a few seconds
                firstline = cifpSelected.getName ();
            } else {
                // show how long to next turn
                firstline = turntoline;
            }
            cifpTextLines[currentStep] = firstline;

            // see how big the text box needs to be
            int textwidth = 0;
            int cifpx = canvasWidth  - (int) Math.ceil (cifpTextHeight / 2);
            int cifpy = canvasHeight - (int) Math.ceil (cifpTextHeight / 2);
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
                cifpy -= (int) Math.ceil (cifpTextHeight);
            }
            cifpButtonBounds.left = cifpx - (int) Math.ceil (textwidth);
            cifpButtonBounds.top  = (float) (cifpy - cifpTextAscent + cifpTextHeight);

            // draw black background rectangle where text goes
            canvas.drawRect (cifpButtonBounds, cifpBGPaint);

            // draw text on that background
            for (int i = currentStep; i <= nsteps; i ++) {
                if (i <= currentStep + 1) cifpTxPaint.setColor (Color.MAGENTA);
                  else if (i <= mapIndex) cifpTxPaint.setColor (Color.GREEN);
                                     else cifpTxPaint.setColor (Color.CYAN);
                cifpy += (int) Math.ceil (cifpTextHeight);
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
                        cifpSteps      = null;
                        mapIndex       = Integer.MAX_VALUE;
                        currentStep    = -1;
                        plateView.invalidate ();
                    }
                });
                adb.setNeutralButton ("RESTART", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialogInterface, int i)
                    {
                        CIFPSegment seg = cifpSelected;
                        cifpSelected    = null;
                        drawTextEnable  = false;
                        cifpTextLines   = null;
                        cifpSteps       = null;
                        mapIndex        = Integer.MAX_VALUE;
                        currentStep     = -1;
                        CIFPSegmentSelected (seg);
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

        // read approaches and segments from database
        if (bmpixpernm == 0.0) {
            bmpixpernm = plateView.LatLon2BitmapY (airport.lat, airport.lon) -
                    plateView.LatLon2BitmapY (airport.lat + 1.0 / Lib.NMPerDeg, airport.lon);
            if (bmpixpernm == 0.0) {
                errorMessage ("", "IAP plate not geo-referenced");
                return;
            }
            runtbmpix = bmpixpernm * RUNTNM;
        }
        getCIFPSegments.load ();
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
     * @param segment = transition segment selected
     */
    private void CIFPSegmentSelected (CIFPSegment segment)
    {
        // don't want the select buttons drawn any more
        selectButtons = null;

        // don't want the select menu shown any more
        Lib.dismiss (selectMenu);
        selectMenu = null;

        for (CIFPLeg leg : segment.legs) {
            if (leg.isOptional ()) return;
        }
        CIFPSegmentSelFinal (segment);
    }

    private void CIFPSegmentSelFinal (CIFPSegment segment)
    {
        try {
            cifpSelected = segment;

            // assume they want the text shown
            drawTextEnable = true;

            // get steps from transition segment
            LinkedList<CIFPStep> steps = new LinkedList<> ();
            CIFPLeg prevleg = cifpSelected.getSteps (steps, null);

            // get steps from final segment
            // but don't bother if it is a intermediate fix transition
            // ...that we derived from the final segment, cuz the derived
            //    transition already includes part of the final segment
            CIFPSegment finalseg = segment.approach.finalseg;
            if (!cifpSelected.hitfaf) prevleg = finalseg.getSteps (steps, prevleg);

            // get steps from missed segment
            mapIndex = steps.size ();
            cifpSelected.approach.missedseg.getSteps (steps, prevleg);

            // make array from the list
            cifpSteps = steps.toArray (new CIFPStep[steps.size()]);
            for (int di = cifpSteps.length; -- di >= 0;) {
                cifpSteps[di].drawIndex = di;
            }

            // this array will have the displayable strings
            cifpTextLines = new String[cifpSteps.length+1];

            // need to initialize first position
            currentStep = -1;

            // remember when selection was made
            selectedTime = SystemClock.uptimeMillis ();

            // get final approach parameters
            CIFPLeg fafleg = finalseg.getFafLeg ();
            if (fafleg == null) throw new Exception ("FAF leg missing");
            Waypoint vnFafWaypt = fafleg.getFafWaypt ();
            if (vnFafWaypt == null) throw new Exception ("FAF waypt missing");
            CIFPLeg rwyleg = finalseg.getRwyLeg ();
            if (rwyleg == null) throw new Exception ("RWY leg missing");
            Waypoint vnRwyWaypt = rwyleg.getRwyWaypt  ();
            if (vnRwyWaypt == null) throw new Exception ("MAP missing");
            vnFafPoint.x = plateView.LatLon2BitmapX (vnFafWaypt.lat, vnFafWaypt.lon);
            vnFafPoint.y = plateView.LatLon2BitmapY (vnFafWaypt.lat, vnFafWaypt.lon);
            vnRwyPoint.x = plateView.LatLon2BitmapX (vnRwyWaypt.lat, vnRwyWaypt.lon);
            vnRwyPoint.y = plateView.LatLon2BitmapY (vnRwyWaypt.lat, vnRwyWaypt.lon);
            vnRwyGSElev = rwyleg.getAltitude ();
            vnFinalDist = Lib.LatLonDist (vnFafWaypt.lat, vnFafWaypt.lon,
                    vnRwyWaypt.lat, vnRwyWaypt.lon);
            vnFinalTC = Lib.LatLonTC (vnFafWaypt.lat, vnFafWaypt.lon,
                    vnRwyWaypt.lat, vnRwyWaypt.lon);
            double fafgsel = fafleg.getAltitude ();
            vnGSFtPerNM = (fafgsel - vnRwyGSElev) / vnFinalDist;

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
    private class GetCIFPSegments {

        // results of parsing full approach name string
        // eg, "IAP-LWM VOR 23"
        private boolean sawdme = false;  // these get set if corresponding keyword seen
        private boolean sawgls = false;
        private boolean sawgps = false;
        private boolean sawils = false;
        private boolean sawlbc = false;
        private boolean sawlda = false;
        private boolean sawloc = false;
        private boolean sawndb = false;
        private boolean sawrnv = false;
        private boolean sawsdf = false;
        private boolean sawvor = false;

        private String runway = null;  // either twodigitsletter string or -letter string
        private String variant = "";   // either null or 'Y', 'Z' etc

        // results of parsing dmearcs.txt
        private HashMap<String,String> addedDMEArcs = new HashMap<> ();

        @SuppressWarnings("ConstantConditions")
        public GetCIFPSegments ()
        {
            // Parse full approach plate name
            String fullname = plateid.replace ("IAP-", "").replace ("-", " -").replace ("DME", " DME");
            fullname = fullname.replace ("ILSLOC", "ILS LOC").replace ("VORTACAN", "VOR TACAN");

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

            // Read file containing added DME arcs, select those for this airport and approach
            // Draw the DME arcs on the plate.
            // KLWM,S23,LWM[330@10;CF,wp=LWM[330@10,a=+2000,iaf;AF,nav=LWM,beg=3300,end=566,nm=100,a=+2000
            // KLWM,S23,LWM[120@10;CF,wp=LWM[120@10,a=+2000,iaf;AF,nav=LWM,beg=1200,end=566,nm=100,a=+2000
            // KSFM,S25,ENE[340@8;CF,wp=ENE[340@8,a=+2000,iaf;AF,nav=ENE,beg=3400,end=814,nm=80,a=+2000;CF,wp=ENE,mc=2614,a=+1700
            // KSFM,S25,ENE[180@8;CF,wp=ENE[180@8,a=+2000,iaf;AF,nav=ENE,beg=1800,end=814,nm=80,a=+2000;CF,wp=ENE,mc=2614,a=+1700
            // It can also be used to add arbitrary transition segments that are 'missing'.
            // If adding DME arc transition with DME arc already drawn by FAA, include 'faadrawn' in AF leg.
            File dmeArcs = new File (WairToNow.dbdir + "/dmearcs.txt");
            if (dmeArcs.exists ()) {
                try {
                    BufferedReader br = new BufferedReader (new FileReader (dmeArcs), 4096);
                    try {
                        for (String line; (line = br.readLine ()) != null;) {
                            line = line.trim ().replace (" ", "");
                            if (line.equals ("")) continue;
                            if (line.startsWith ("#")) continue;
                            int i = line.indexOf (';');
                            String idents = line.substring (0, i);
                            String[] parts = idents.split (",");  // "KSFM", "S25", "ENE[180@8"
                            if (parts[0].equals (airport.ident) && (appMatches (parts[1]) != null)) {
                                String legs = line.substring (++ i);
                                addedDMEArcs.put (parts[1] + "," + parts[2], legs);
                                drawDMEArc (legs);
                            }
                        }
                    } finally {
                        br.close ();
                    }
                } catch (IOException ioe) {
                    Log.w (TAG, "error reading " + dmeArcs.getPath () + ": " + ioe.getMessage ());
                }
            }
        }

        /**
         * Read database for CIFPs pertaining to the plate.
         * Load those records into cifpApproaches.
         * That gives us a list of transition segments for the plate.
         */
        public void load ()
        {
            cifpApproaches = new TreeMap<> ();

            // scan the database for this airport and hopefully find airport's icaoid
            // then gather up all CIFP segments for things that seem to match plate name
            int expdate28 = MaintView.GetPlatesExpDate (airport.state);
            String dbname = "plates_" + expdate28 + ".db";
            try {
                SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                @SuppressWarnings("ConstantConditions")
                Cursor result1 = sqldb.query (
                        "iapcifps", columns_cp_misc,
                        "cp_icaoid=?", new String[] { airport.ident },
                        null, null, null, null);
                try {
                    if (result1.moveToFirst ()) do {
                        String appid = result1.getString (0);
                        String segid = result1.getString (1);
                        String legs  = result1.getString (2);
                        ParseCIFPSegment (appid, segid, legs);
                    } while (result1.moveToNext ());
                } finally {
                    result1.close ();
                }
            } catch (Exception e) {
                errorMessage ("error reading " + dbname, e);
            }

            // maybe add some DME arcs to the diagramme
            // can also add other types of transition segments given in dmearcs.txt
            for (String appidsegid : addedDMEArcs.keySet ()) {
                String[] parts = appidsegid.split (",");
                ParseCIFPSegment (parts[0], parts[1], addedDMEArcs.get (appidsegid));
            }

            // every approach must have a finalseg and a missedseg
            // remove any that don't have both
            // also add a radar-vector transition segment for each approach
            // and add intermediate fixes as transitions
            //     cuz sometimes radar controllers do that
            //     they show up in menus in lower case
            //     the real transitions are upper case
            for (Iterator<String> it = cifpApproaches.keySet ().iterator (); it.hasNext ();) {
                String appid = it.next ();
                CIFPApproach approach = cifpApproaches.get (appid);
                if ((approach.finalseg == null) || (approach.missedseg == null)) {
                    it.remove ();
                } else {

                    // add intermediate fixes from transition segments
                    CIFPSegment[] realtranssegs = approach.transsegs.values ().toArray (
                            new CIFPSegment[approach.transsegs.values().size()]);
                    for (CIFPSegment transseg : realtranssegs) {
                        AddIntermediateSegments (transseg);
                    }

                    // some final segments have fixes outside the FAF
                    // eg, ASH GPS-14 has LODTI
                    // ...so add intermediate fixes from final segment
                    AddIntermediateSegments (approach.finalseg);

                    // add radar vector segment
                    CIFPSegment rvseg = new CIFPSegment ();
                    CIFPLeg_rv  rvleg = new CIFPLeg_rv  ();

                    rvseg.approach = approach;
                    rvseg.segid    = CIFPSEG_RADAR;
                    rvseg.legs     = new CIFPLeg[] { rvleg };

                    rvleg.segment  = rvseg;
                    rvleg.legidx   = 0;
                    rvleg.legstr   = "";
                    rvleg.pathterm = "rv";
                    rvleg.parms    = new HashMap<> ();
                    rvleg.init1 ();

                    approach.transsegs.put (CIFPSEG_RADAR, rvseg);
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

        // add intermediate segments derived from transition segment to its approach
        private void AddIntermediateSegments (CIFPSegment transseg)
        {
            // loop through possible intermediate fix names
            // they come from all CF legs in the transition segment
            for (String interfixuc : transseg.interfixes.keySet ()) {
                String interfixlc = interfixuc.toLowerCase (Locale.US);

                // make sure we don't already have transition with that name
                if ((transseg.approach.transsegs.get (interfixuc) == null) &&
                        (transseg.approach.transsegs.get (interfixlc) == null)) {

                    // try to create a new transition segment starting at that fix
                    try {
                        ParseCIFPSegment (transseg.approach, interfixlc, transseg.semis,
                                transseg.interfixes.get (interfixuc));
                    } catch (Exception e) {
                        Log.w (TAG, "error parsing " + airport.ident + " " +
                                transseg.approach.appid + " " + interfixlc, e);
                    }
                }
            }
        }

        /**
         * Parse a CIFP segment string and add segment to segments for this approach.
         * @param appid = approach id, eg, "S23" for VOR 23 approach
         * @param segid = segment id, eg "LWM" transition
         * @param legs = legs, semi-colon separated,
         *      eg, "CF,wp=LWM[330@10,a=+2000,iaf;AF,nav=LWM,beg=3300,end=566,nm=100,a=+2000"
         */
        private void ParseCIFPSegment (String appid, String segid, String legs)
        {
            try {
                // if it looks like approach type, runway and variant match,
                // add segment to list of matching segments.
                // note that we may get more than one approach to match this
                // plate, such as if the plate is 'ILS OR LOC ...', we get
                // ILS and LOC segments matching.
                String apptype = appMatches (appid);
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
                        cifpapp.refnavwp = plateView.FindWaypoint (legs);
                    } else {

                        // real segments, get array of leg strings
                        String[] semis = legs.split (";");

                        // make sure segment has at least one leg
                        if (semis.length > 0) {

                            // parse legs and add segment to approach
                            ParseCIFPSegment (cifpapp, segid, semis, 0);
                        }
                    }
                }
            } catch (Exception e) {
                errorMessage ("error processing CIFP record " + airport.ident + "." +
                        appid + "." + segid, e);
            }
        }

        /**
         * Add segment to approach.
         *  Input:
         *   cifpapp = approach to add segment to
         *   segid   = segment id (name of IAF or ~f~, ~m~, (rv))
         *   semis   = leg strings
         *      eg, CF,wp=LWM[330@10,a=+2000,iaf
         *          AF,nav=LWM,beg=3300,end=566,nm=100,a=+2000
         *   isemi   = which element in semis to start at
         *  Output:
         *   segment added to cifpapp
         */
        private void ParseCIFPSegment (CIFPApproach cifpapp, String segid, String[] semis, int ileg)
                throws Exception
        {
            // create segment object and associated leg objects
            int nlegs = semis.length - ileg;
            CIFPSegment cifpseg = new CIFPSegment ();
            cifpseg.approach    = cifpapp;
            cifpseg.segid       = segid;
            cifpseg.legs        = new CIFPLeg[nlegs];
            cifpseg.interfixes  = new HashMap<> ();
            cifpseg.semis       = semis;
            for (int i = 0; i < nlegs; i ++) {
                String fixid = makeLeg (cifpseg, i, semis[i+ileg]);

                // save fix name if it might be an intermediate fix or the faf
                // this lets us build transition segments beginning at ifs and the faf
                if (!cifpseg.hitfaf) {
                    if (fixid != null) cifpseg.interfixes.put (fixid, i + ileg);
                    cifpseg.hitfaf = cifpseg.legs[i].parms.containsKey ("faf");
                }
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

        /**
         * See if an approach matches the plate title string
         * @param appid = CIFP approach name, eg, "I05Z"
         * @return null: it doesn't match
         *         else: approach type, eg, "ILS"
         */
        private String appMatches (String appid)
        {
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
            return apptype;
        }

        /**
         * Draw a DME arc on the plate
         * @param legs = describes the DME arc, must contain an AF leg
         *      eg, CF,wp=LWM[330@10,a=+2000,iaf;AF,nav=LWM,beg=3300,end=566,nm=100,a=+2000
         */
        private void drawDMEArc (String legs)
        {
            String[] legsplit = legs.split (";");
            for (String leg : legsplit) {
                if (leg.startsWith ("AF,")) {
                    Waypoint nav = null;
                    double nm  = 0;
                    double beg = 0;
                    double end = 0;
                    boolean faadrawn = false;
                    String[] parms = leg.split (",");
                    for (String parm : parms) {
                        if (parm.startsWith ("nav=")) nav = plateView.FindWaypoint (parm.substring (4));
                        if (parm.startsWith ("beg=")) beg = Integer.parseInt (parm.substring (4)) / 10.0;
                        if (parm.startsWith ("end=")) end = Integer.parseInt (parm.substring (4)) / 10.0;
                        if (parm.startsWith ("nm="))  nm  = Integer.parseInt (parm.substring (3)) / 10.0;
                        if (parm.equals ("faadrawn")) faadrawn = true;
                    }
                    if ((nav != null) && !faadrawn) {
                        plateView.DrawDMEArc (nav, beg, end, nm);
                    }
                }
            }
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
     * We also add a radar-vector transition segment.
     * They usually have several other transition segments (from the database) as well.
     */
    public class CIFPSegment {
        public  CIFPApproach approach;
        public  String segid;
        private String name;
        public  CIFPLeg[] legs;
        public  HashMap<String,Integer> interfixes;
        public  String[] semis;
        public  boolean hitfaf;

        // second-phase init
        public void init2 ()
        {
            for (CIFPLeg leg : legs) leg.init2 ();
        }

        // get steps for all legs in this segment
        public CIFPLeg getSteps (LinkedList<CIFPStep> steps, CIFPLeg prevleg) throws Exception
        {
            int nlegs = legs.length;
            int ileg  = 0;

            // an ugliness for adding final steps to CIFPLeg_rv transition
            // - we must skip all legs that come before the FAF leg
            //   usually either 0 or 1 leg being skipped here
            if (prevleg instanceof CIFPLeg_rv) {
                for (; ileg < nlegs; ileg ++) {
                    if (legs[ileg].parms.containsKey ("faf")) break;
                }
            }

            // add steps from all the remaining legs
            for (; ileg < nlegs; ileg ++) {
                CIFPLeg leg = legs[ileg];
                if (!leg.isRuntLeg (prevleg) || !prevleg.mergeAltitude (leg)) {
                    leg.getSteps (steps);
                    prevleg = leg;
                }
            }

            // return the last leg we added steps from
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

        // assuming this is the final segment (which has the FAF),
        // get the leg ending over the numbers
        public CIFPLeg getRwyLeg ()
        {
            boolean hasfaf = false;
            CIFPLeg lastleg = null;
            for (CIFPLeg leg : legs) {
                if (leg.parms.containsKey ("faf")) hasfaf = true;
                if (hasfaf) lastleg = leg;
            }
            return lastleg;
        }

        /**
         * Get long segment name
         *   "KBVY LOC 16/(rv)"
         */
        public String getName ()
        {
            if (name == null) {
                name = airport.ident + ' ' +
                        approach.name + '/' +
                        segid;
            }
            return name;
        }
    }

    /**
     * Make a CIFP leg object from the database leg string.
     * Output:
     *  returns null: cannot be an intermediate fix
     *          else: might be an intermediate fix
     *                only CF cuz code assumes transition segments start with CF leg
     */
    private String makeLeg (CIFPSegment seg, int idx, String legstr) throws Exception
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

            // wairtonow specific
            case "td": leg = new CIFPLeg_td (); break;

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

        return leg.init1 ();
    }

    /**
     * A step is a single maneuver, eg, turn heading xxx or fly to xxxxx.
     * It can be described with one short line of text.
     */
    private abstract class CIFPStep {
        public char arcturndir;  // if step is an arc, arc turn direction ('L' or 'R'), else 0 for line
        public double arcctrbmx;  // if step is an arc, center of arc
        public double arcctrbmy;
        public double arcbegrad;  // if step is an arc, beginning radial from center (true)
        public double arcendrad;  // if step is an arc, ending radial from center (true)
        public double arcradbmp;  // if step is an arc, radius of arc

        public double begptalt;   // where airplane begins going into step
        public double begpthdg;
        public double begptbmx;
        public double begptbmy;
        public double endptalt;   // where airplane ends up at end of step
        public double endpthdg;
        public double endptbmx;
        public double endptbmy;

        public int   drawIndex;  // index in cifpSteps[] array
        public double acrftdist;  // distance aircraft is from dots

        // draw the step
        // may alter step's internal state (called only from UpdateState())
        //  input:
        //   curposalt,curposhdg,curposbmx,curposbmy
        //   begptalt,begpthdg,begptbmy,begptbmx
        //  output:
        //   curposalt,curposhdg,curposbmx,curposbmy
        public abstract void drawStepDots ();

        // get text line describing the step
        // must not alter step's internal state (in case called outside UpdateState())
        //  input:
        //   begptalt,begpthdg,begptbmy,begptbmx
        public abstract String getTextLine ();

        // draw fillet from end of previous step to beginning of this step
        // called only when a previous step is current to show pilot how to turn to this step
        // must not alter step's internal state (for simplicity)
        public void drawFillet ()
        {
            if (arcturndir == 0) {

                // step being fillet'd to (this step) is a line
                // ... starting at the given point and on the given heading
                drawFilletToLine (begptbmx, begptbmy, endpthdg);
            } else {

                // step being fillet'd to (this step) is an arc
                CIFPStep curstep = cifpSteps[currentStep];
                if (curstep.arcturndir == 0) {

                    // previous step is a line, so drawing fillet from line to arc
                    filletArc.calcFilletLineToArc (curstep.endptbmx, curstep.endptbmy, curstep.endpthdg,
                            arcctrbmx, arcctrbmy, arcendrad, arcradbmp, arcturndir);
                } else {
                    // arc-to-arc: doesn't happen, so don't bother drawing a fillet
                    Lib.Ignored ();
                }
            }
        }

        // see if this step is a runt
        // normally see if it ends very close to where it begins
        // but things like holds end where they begin and aren't ever runts
        // runts are too small to reliably detect aircraft positioned on them
        // ...so we don't make them current, and also are hard to calculate a
        // true course entry and exit as they may actually just be a single point
        // must not alter step's internal state (in case called outside UpdateState())
        public boolean isRuntStep ()
        {
            return Math.hypot (endptbmx - begptbmx, endptbmy - begptbmy) < runtbmpix;
        }

        // update a NavDialView with what is going on
        // return status string saying what is going on (or null for same as getTextLine())
        // must not alter step's internal state (cuz it is called outside UpdateState())
        public abstract void getVNTracking (NavDialView navDial);

        // get number of bitmap pixels until we reach the beginning of the turn at the end of this step
        // also get the direction and true course of the turn
        // called only when this step is current
        // must not alter step's internal state (in case called outside UpdateState())
        public double bmpToNextTurn (NextTurn nextTurn)
        {
            // assume no turn at end of step
            nextTurn.direction  = null;
            nextTurn.truecourse = Double.NaN;

            // see if fillet info filled in
            if (filletArc.turndir == 0) {
                // no next turn, just straight ahead to next point, return dist to next point
                double disttoend = Math.hypot (endptbmx - acraftbmx, endptbmy - acraftbmy);
                // point might be behind us, so project distance onto ideal course
                double tctoend = trueCourse (acraftbmx, acraftbmy, endptbmx, endptbmy);
                return disttoend * Mathf.cosdeg (tctoend - endpthdg);
            }

            // return next turn parameters
            if (filletArc.turndir == 'L') nextTurn.direction = turnleft;
            if (filletArc.turndir == 'R') nextTurn.direction = turnright;
            nextTurn.truecourse = filletArc.getEndTC ();

            // get course line from aircraft to beginning of fillet
            double filetbmx   = filletArc.getBegX ();
            double filetbmy   = filletArc.getBegY ();
            double tctoturn   = trueCourse (acraftbmx, acraftbmy, filetbmx, filetbmy);

            // get distance from aircraft to beginning of fillet
            double disttoturn = Math.hypot (acraftbmx - filetbmx, acraftbmy - filetbmy);

            // get component of that distance aligned with fillet start heading
            // will be negative if we are past beginning of fillet
            double filletbegtc = filletArc.getBegTC ();
            disttoturn *= Mathf.cosdeg (tctoturn - filletbegtc);
            return disttoturn;
        }

        /**
         * Fill in NavDialView with what is going on.
         * Assumes this step is a straight-line course from begptbmx,y -> endptbmx,y
         * @param navDial = what to fill in
         * @param wpid = destination waypoint ident (ident of endptbmx,y)
         */
        protected void getLinearVNTracking (NavDialView navDial, String wpid)
        {
            // maybe we are in fillet at end of the line segment
            if (!inEndFillet (navDial)) {

                // not in fillet, set obs dial to whatever course they should be flying in this step
                double obstrue = trueCourse (begptbmx, begptbmy, endptbmx, endptbmy);
                navDial.setObs (obstrue + aptmagvar);

                // see if this segment is on final approach course
                double obsfinaldiff = angleDiffU (obstrue - vnFinalTC);
                boolean onfinalapp = obsfinaldiff < 15.0;

                // see if runway numbers are ahead of us or behind
                double faf_to_rwy_x = vnRwyPoint.x - vnFafPoint.x;
                double faf_to_rwy_y = vnRwyPoint.y - vnFafPoint.y;
                double acf_to_rwy_x = vnRwyPoint.x - acraftbmx;
                double acf_to_rwy_y = vnRwyPoint.y - acraftbmy;
                boolean runwayahead = (faf_to_rwy_x * acf_to_rwy_x + faf_to_rwy_y * acf_to_rwy_y) > 0.0;

                // see how far away from FAF and runway numbers we are
                double disttofaf = Math.hypot (acraftbmx - vnFafPoint.x, acraftbmy - vnFafPoint.y) / bmpixpernm;
                double disttorwy = Math.hypot (acraftbmx - vnRwyPoint.x, acraftbmy - vnRwyPoint.y) / bmpixpernm;

                // get signed distance to FAF by projecting our position onto the final approach course
                double coursetofaf = trueCourse (acraftbmx, acraftbmy, vnFafPoint.x, vnFafPoint.y);
                double signeddisttofaf = disttofaf * Mathf.cosdeg (coursetofaf - vnFinalTC);

                // get needle sensitivity (in nm each side)
                // - FAF more than 2nm ahead: +- 1nm
                // - FAF less than 2nm ahead: +- 0.3nm to +- 1nm
                // - inside FAF runway ahead: +- 0.3nm
                // - runway behind: +- 1nm
                double locsens;
                if (!onfinalapp) locsens = 1.0;  // out doing a PT or something
                else if (signeddisttofaf > 2.0) locsens = 1.0;  // inbound but have a way to go
                else if (signeddisttofaf > 0.0) locsens = 0.3 + signeddisttofaf * 0.35;
                else if (runwayahead) locsens = 0.3;  // closing in on runway
                else locsens = 1.0;  // gone missed

                // set localizer needle deflection
                double deflection = lineDeflection (begptbmx, begptbmy, endptbmx, endptbmy);
                navDial.setDeflect (deflection / locsens);

                // maybe we are in glideslope mode
                //  * runway numbers must be ahead of us
                //  * we must be within 2nm of final approach centerline
                //  * we must be headed within 60deg of final approach course
                //  * we must be within 3nm of FAF or between FAF and runway
                if (runwayahead && onfinalapp &&
                        (Math.abs (deflection) < NavDialView.LOCDEFLECT * 2.0) &&
                        ((disttofaf < 3.0) || (disttorwy < vnFinalDist))) {

                    // on final approach, put dial in ILS mode
                    navDial.setMode (NavDialView.Mode.ILS);

                    // tell pilot what their descent rate should be to stay on glideslope
                    double nmpermin = wairToNow.currentGPSSpd / Lib.MPerNM * 60.0;
                    double descrate = vnGSFtPerNM * nmpermin;
                    navDial.gsfpm   = (int) - Math.round (descrate);

                    // get glideslope sensitivity
                    // - at FAF: +- 492ft
                    // - at 4.63nm inside FAF: +- 148ft
                    double gssens = 492.0 + signeddisttofaf / 4.63 * (492.0 - 148.0);
                    if (gssens < 148.0) gssens = 148.0;

                    // get height below on-glideslope path
                    double ongsalt = disttorwy * vnGSFtPerNM + vnRwyGSElev;
                    double belowongs = ongsalt - acraftalt;

                    // set glideslope needle deflection
                    navDial.setSlope (belowongs / gssens * NavDialView.GSDEFLECT);
                } else {

                    // not on final approach, put dial in LOC mode
                    // use LOC not VOR so dial can't be finger rotated
                    navDial.setMode (NavDialView.Mode.LOC);
                }
            }

            // set DME distance and identifier
            double dist = Math.hypot (acraftbmx - endptbmx, acraftbmy - endptbmy);
            navDial.setDistance (dist / bmpixpernm, wpid, false);
        }

        // if in fillet at end of step, use turn-to heading in obs
        // so user can see where they are turning to
        protected boolean inEndFillet (NavDialView navDial)
        {
            if ((bmpToNextTurn (nextTurn) <= 0.0) && !Double.isNaN (nextTurn.truecourse)) {
                navDial.setObs (nextTurn.truecourse + aptmagvar);
                return true;
            }
            return false;
        }
    }

    /**
     * Draw fillet from current CIFP step course to next CIFP step course line
     * @param nextpointx = starting point of next CIFP step course line (currentStep+n)
     * @param nextpointy = starting point of next CIFP step course line (currentStep+n)
     * @param nextrucrs  = true course of next CIFP step course line (currentStep+n)
     */
    private void drawFilletToLine (double nextpointx, double nextpointy, double nextrucrs)
    {
        CIFPStep curstep = cifpSteps[currentStep];
        if (curstep.arcradbmp == 0) {
            filletArc.calcFilletLineToLine (curstep.endptbmx, curstep.endptbmy, curstep.endpthdg,
                    nextpointx, nextpointy, nextrucrs);
        } else {
            filletArc.calcFilletArcToLine (
                    curstep.arcctrbmx, curstep.arcctrbmy, curstep.arcbegrad,
                    curstep.arcradbmp, curstep.arcturndir,
                    nextpointx, nextpointy, nextrucrs);
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

        public abstract String init1 () throws Exception;
        public boolean isRuntLeg (CIFPLeg prevleg) { return false; }
        public abstract void init2 ();
        public boolean isOptional () { return false; }
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
        protected double getTCDeg (String key, Waypoint navwp)
        {
            double mc = Integer.parseInt (parms.get (key)) / 10.0;
            if (navwp == null) navwp = airport;
            return mc - navwp.GetMagVar (curposalt);
        }
        protected double getTCDeg (int mci, Waypoint navwp)
        {
            double mc = mci / 10.0;
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
        protected void appTrueAsMag (StringBuilder sb, double tru)
        {
            int mag = ((int) Math.round (tru + aptmagvar)) % 360;
            if (mag <=  0) mag += 360;
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
                    sb.append (turnleft);
                    break;
                }
                case 'R': {
                    sb.append (turnright);
                    break;
                }
                default: throw new IllegalArgumentException ("bad turn dir " + td);
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
                        sb.append ('=');
                        appendAlt (sb, a1);
                        break;
                    }

                    // cross fix at or above a1 altitude
                    case '+': {
                        sb.append ('\u2265');  // >=
                        appendAlt (sb, a1);
                        break;
                    }

                    // cross fix at or below a1 altitude
                    case '-': {
                        sb.append ('\u2264');  // <=
                        appendAlt (sb, a1);
                        break;
                    }

                    // cross fix between a1 and a2 altitudes (a1 <= a2)
                    case 'B': {
                        sb.append (' ');
                        sb.append (a1);
                        sb.append ("..");
                        appendAlt (sb, a2);
                        break;
                    }

                    // approach fix at a2 and intercept glide slope
                    // when at fix and on glide slope, altitude should be equal to a1
                    case 'G': {  // ORL ILS 25 at CBOYD
                        sb.append ('=');
                        appendAlt (sb, a2);
                        if (a1.equals (a2)) {
                            sb.append ("(gs)");
                        } else {
                            sb.append ("(gs=");
                            appendAlt (sb, a1);
                            sb.append (')');
                        }
                        break;
                    }

                    // approach fix at a1 and intercept glide slope
                    // when at fix and on glide slope, altitude should be equal to a2
                    case 'H': {  // MHT ILS 6 at FITZY
                        sb.append ('=');
                        appendAlt (sb, a1);
                        if (a1.equals (a2)) {
                            sb.append ("(gs)");
                        } else {
                            sb.append ("(gs=");
                            appendAlt (sb, a2);
                            sb.append (')');
                        }
                        break;
                    }

                    // cross fix at a1 altitude
                    // subsequently descend to a2 (but that is mentioned in next leg anyway)
                    case 'I': {  // ORL ILS 25 at MARYB
                        sb.append ('=');
                        appendAlt (sb, a1);
                        break;
                    }

                    // cross fix at min of a1 altitude
                    // subsequent descent to a2 (but that is mentioned in next leg anyway)
                    case 'J': {  // MHT ILS 6 at JAAZZ; BED ILS 11 at ZELKA
                        sb.append ('\u2265');  // >=
                        appendAlt (sb, a1);
                        break;
                    }

                    // along glide path for precision approach such as GPS LPV
                    // a1 gives non-precision minimum altitude at this fix
                    // a2 gives on-glideslope exact altitude at this fix
                    case 'V': {  // BVY GPS 27 at MAIOU; altone=non-prec; alttwo=precision
                        sb.append ('\u2265');  // non-precision descent altitude
                        appendAlt (sb, a1);
                        sb.append ("(gs=");    // precision on glidepath
                        appendAlt (sb, a2);    // (a bit higher than lims[0])
                        sb.append (')');
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
        @SuppressWarnings("SameParameterValue")
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
            double newalt = getAltitude ();
            if (!Double.isNaN (newalt)) {
                curposalt = newalt;
            }
        }

        /**
         * Get altitude we should be at at the end of the leg.
         * For FAF and runway legs, this should be the on-glideslope altitude.
         */
        public double getAltitude ()
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
                    return (alt1ft + alt2ft) / 2.0;
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

                default: return Double.NaN;
            }
        }
    }

    /**
     * Holds info and methods for computing and drawing a fillet arc between steps.
     * Fillet arcs are assumed to be flown at standard rate turn.
     * Pilot will get a 10,9,8,... countdown coming up on a fillet.
     */
    private class FilletArc extends PointD {
        public char turndir;  // which direction the pilot is turning in the arc
        public double start;   // degrees clockwise from due East where ccwmost part of arc is
        public double sweep;   // degrees clockwise from start that arc sweeps
        public double startc;  // tc from center of fillet where the pilot starts
        public double stoptc;  // tc from center of fillet where the pilot stops

        private FilletArc[] fas;

        /**
         * Draw fillet arc on canvas.
         */
        public void draw (Canvas canvas, Paint paint)
        {
            drawArcBox.set (
                    (float) plateView.BitmapX2CanvasX (x - stdturnradbmp),
                    (float) plateView.BitmapY2CanvasY (y - stdturnradbmp),
                    (float) plateView.BitmapX2CanvasX (x + stdturnradbmp),
                    (float) plateView.BitmapY2CanvasY (y + stdturnradbmp));
            canvas.drawArc (drawArcBox, (float) start, (float) sweep, false, paint);
        }

        /**
         * Get arc starting x,y (where airplane starts on arc)
         */
        public double getBegX ()
        {
            return x + Mathf.sindeg (startc) * stdturnradbmp;
        }
        public double getBegY ()
        {
            return y - Mathf.cosdeg (startc) * stdturnradbmp;
        }

        /**
         * Get arc starting and ending true course, ie,
         * what heading a perfectly flown aircraft would be on.
         */
        public double getBegTC ()
        {
            double tc = Double.NaN;
            if (turndir == 'L') tc = startc - 90.0;
            if (turndir == 'R') tc = startc + 90.0;
            return tc;
        }
        public double getEndTC ()
        {
            double tc = Double.NaN;
            if (turndir == 'L') tc = stoptc - 90.0;
            if (turndir == 'R') tc = stoptc + 90.0;
            return tc;
        }

        /**
         * Compute fillet from an arc (such as a DME arc) to a line.
         *  Input:
         *   arccenterx,y = center of arc
         *   arcbegradtru = begin radial from arc center
         *   arcradius    = arc radius
         *   arcturndir   = 'L' or 'R'
         *   nextpointx,y = where next course line begins on or near the arc
         *   nexttrucrs   = true course out of nextpointx,y
         */
        public void calcFilletArcToLine (
                double arccenterx, double arccentery, double arcbegradtru,
                double arcradius, char arcturndir,
                double nextpointx, double nextpointy, double nexttrucrs)
        {
            calcFilletArcVsLine (
                    arccenterx, arccentery, arcbegradtru, arcradius, arcturndir,
                    nextpointx, nextpointy, nexttrucrs, true);
        }

        /**
         * Compute fillet from a line to an arc (such as a DME arc).
         *  Input:
         *   prevpointx,y = where previous course line ends on or near the arc
         *   prevtrucrs   = true course into prevpointx,y
         *   arccenterx,y = center of arc
         *   arcendradtru = end radial from arc center
         *   arcradius    = arc radius
         *   arcturndir   = 'L' or 'R'
         */
        public void calcFilletLineToArc (
                double prevpointx, double prevpointy, double prevtrucrs,
                double arccenterx, double arccentery, double arcendradtru,
                double arcradius, char arcturndir)
        {
            calcFilletArcVsLine (
                    arccenterx, arccentery, arcendradtru, arcradius, arcturndir,
                    prevpointx, prevpointy, prevtrucrs, false);
        }

        /**
         * Calculate the fillet between an arc and a line.
         *  Input:
         *   arccenterx,y = center of the arc
         *   arcfarradtru = radial from arc center to the far end of the arc
         *   arcradius = radius of the arc
         *   arcturndir = direction the aircraft flies the arc ('L' or 'R')
         *   linepointx,y = point of the line on or near the arc circle
         *   linetrucrs = true course of line that the aircraft flies the line
         *   a2l = true: arc->line; false: line->arc
         *   stdturnradbmp = fillet turn radius
         *  Output:
         *   turndir = 0: line does not intersect circle
         *             'L' or 'R' = direction aircraft flies fillet arc
         *   x,y = center of fillet
         *   start,sweep = start and sweep of the arc
         *   startc,stoptc = other description of start/stop of arc
         */
        private void calcFilletArcVsLine (
                double arccenterx, double arccentery, double arcfarradtru,
                double arcradius, char arcturndir,
                double linepointx, double linepointy, double linetrucrs,
                boolean a2l)
        {
            if ((arcturndir != 'L') && (arcturndir != 'R')) {
                throw new IllegalArgumentException ("arcturndir");
            }

            // four possible intersecting fillet circles turn in
            // direction that matches both the dme arc and the line
            // comments of left and right of line assume line is pointing northward (linetrucrs = 0)
            if (fas == null) {
                fas = new FilletArc[4];
                for (int i = 0; i < 4; i ++) fas[i] = new FilletArc ();
            }

            // start out saying none of the fillet circles are valid
            for (FilletArc fa : fas) fa.turndir = 0;

            // two of those fillets have centers on a circle of radius arcradius+stdturnradbmp (outside)
            // - they turn in opposite direction as the dme arc
            calc2FilletArcVsLine (0, arcradius + stdturnradbmp, (char) (arcturndir ^ 'L' ^ 'R'),
                    linepointx, linepointy, linetrucrs, arccenterx, arccentery);

            // the other two have centers on a circle of radius arcradius-stdturnradbmp (inside)
            // - they turn in same direction as the dme arc
            calc2FilletArcVsLine (2, arcradius - stdturnradbmp, arcturndir,
                    linepointx, linepointy, linetrucrs, arccenterx, arccentery);

            // calculate dme circle intercept for the fillet circles
            // also find fillet circle that gives shortest total flight distance
            FilletArc bestavecloop = null;
            FilletArc bestsansloop = null;
            double bestdistavecloop = Double.MAX_VALUE;
            double bestdistsansloop = Double.MAX_VALUE;
            for (int i = 0; i < 4; i ++) {
                FilletArc fa = fas[i];
                if (fa.turndir == 0) continue;

                // true course from fillet center to dme arc intersection point
                double daitc;
                if ((i & 2) == 0) {
                    // - fillet circle is outside the dme arc circle
                    daitc = trueCourse (fa.x, fa.y, arccenterx, arccentery);
                } else {
                    // - fillet circle is inside the dme arc circle
                    daitc = trueCourse (arccenterx, arccentery, fa.x, fa.y);
                }

                // if flying from arc to line, it is where aircraft starts flying the fillet arc
                // if flying from line to arc, it is where aircraft stops flying the fillet arc
                if (a2l) fa.startc = daitc;
                else fa.stoptc = daitc;

                // calculate sweep, ie, how many degrees of the fillet arc will be flown
                if (fa.turndir == 'L') {
                    // since the fillet is left turns, wrap start to be gt stop
                    while (fa.startc >= fa.stoptc + 360.0) fa.startc -= 360.0;
                    while (fa.startc < fa.stoptc) fa.startc += 360.0;
                    // calculate how many degrees aircraft will turn
                    fa.sweep = fa.startc - fa.stoptc;
                }
                if (fa.turndir == 'R') {
                    // since the fillet is right turns, wrap stop to be gt start
                    while (fa.stoptc >= fa.startc + 360.0) fa.stoptc -= 360.0;
                    while (fa.stoptc < fa.startc) fa.stoptc += 360.0;
                    // calculate how many degrees aircraft will turn
                    fa.sweep = fa.stoptc - fa.startc;
                }

                // see how far dme arc will be flown if we use this fillet
                double tc_dmecenter_to_intersect = trueCourse (arccenterx, arccentery, fa.x, fa.y);
                double dmearc_sweep = Double.NaN;
                if (arcturndir == 'L') {
                    dmearc_sweep = arcfarradtru - tc_dmecenter_to_intersect;
                }
                if (arcturndir == 'R') {
                    dmearc_sweep = tc_dmecenter_to_intersect - arcfarradtru;
                }
                if (!a2l) dmearc_sweep = - dmearc_sweep;
                while (dmearc_sweep <    0.0) dmearc_sweep += 360.0;
                while (dmearc_sweep >= 360.0) dmearc_sweep -= 360.0;
                double dist_around_dmearc = dmearc_sweep * arcradius;

                // see how far it is around this fillet
                double dist_around_fillet = fa.sweep * stdturnradbmp;

                // if shortest of all, use it
                double dist = dist_around_dmearc + dist_around_fillet;
                if (bestdistavecloop > dist) {
                    bestdistavecloop = dist;
                    bestavecloop   = fa;
                }
                // same except don't include loops 180deg or more
                if (fa.sweep < 180.0) {
                    if (bestdistsansloop > dist) {
                        bestdistsansloop = dist;
                        bestsansloop   = fa;
                    }
                }
            }

            // save fillet that gives shortest total flight distance
            turndir = 0;
            if (bestsansloop == null) bestsansloop = bestavecloop;
            if (bestsansloop != null) {
                x       = bestsansloop.x;
                y       = bestsansloop.y;
                turndir = bestsansloop.turndir;
                sweep   = bestsansloop.sweep;
                startc  = bestsansloop.startc;
                stoptc  = bestsansloop.stoptc;
                if (turndir == 'L') start = stoptc - 90.0;
                if (turndir == 'R') start = startc - 90.0;
            }
        }

        // Compute 2 fillet circles tangent to a line with centers on a given circle.
        // The fillet circle turn direction matches the original dme arc and the line.
        //  Input:
        //   i = where to put the 2 circles in the fas[] array
        //   circleradius = dme arc radius +- stdturnradbmp
        //   filturndir = direction the fillet circles will be flown to match dme arc
        //   linepointx,y = a point on the incoming course line on or near the arc
        //   linetrucrs = heading along the incoming course line
        //   arccenterx,y = dme arc centerpoint
        //  Output:
        //   fas[i+0],fas[i+1| = filled in
        //       .turndir = fillet circle turn direction 'L' or 'R'
        //       .startc = .stoptc = tc fron center of fillet to line intersection point
        //       .x,y = fillet circle center
        //  Note:
        //   the line may not intersect the circle so the 2 fas[] elements won't be filled in
        private void calc2FilletArcVsLine (int i, double circleradius, char filturndir,
                                           double linepointx, double linepointy, double linetrucrs,
                                           double arccenterx, double arccentery)
        {
            // displace the line one fillet radius to the line's left or to line's right
            // that will give us 2 fillet circles tangent to original line centered on the
            // outer or inner dme arc circle that turn in same direction as line is flown
            double linedispcrs = Double.NaN;
            if (filturndir == 'L') {
                // fillet circles are flown anticlockwise, so they must be to the
                // left (west) of a line that is being flown northbound
                linedispcrs = linetrucrs - 90.0;
            }
            if (filturndir == 'R') {
                // fillet circles are flown clockwise, so they must be to the
                // right (east) of a line that is being flown northbound
                linedispcrs = linetrucrs + 90.0;
            }

            // displace line point by displacement course one fillet radius perpendicular to line
            // ...one way or the other
            double line1pointx = linepointx + Mathf.sindeg (linedispcrs) * stdturnradbmp;
            double line1pointy = linepointy - Mathf.cosdeg (linedispcrs) * stdturnradbmp;

            // compute another point along that displaced line
            double line2pointx = line1pointx + Mathf.sindeg (linetrucrs) * 1024.0;
            double line2pointy = line1pointy - Mathf.cosdeg (linetrucrs) * 1024.0;

            // compute where that displaced line intersects the expanded or contracted dme arc circle
            FilletArc pp = fas[i];
            FilletArc pm = fas[i+1];
            if (Lib.lineIntersectCircle (line1pointx, line1pointy, line2pointx, line2pointy,
                        arccenterx, arccentery, circleradius, pp, pm)) {

                // get distance of fillet circle center from line endpoint on/near dme arc
                double dist_pp_to_arc = Math.hypot (pp.x - linepointx, pp.y - linepointy);
                double dist_pm_to_arc = Math.hypot (pm.x - linepointx, pm.y - linepointy);

                // pick the fillet circle closer to the line endpoint
                // the other one is on the other side of the dme arc
                FilletArc pc = (dist_pp_to_arc < dist_pm_to_arc) ? pp : pm;

                // mark the one we pick as being valid
                pc.turndir = filturndir;

                // calc true course from center of those circles to original line
                // this will point to where the intersection of the fillet circle and the original line is
                // it is the opposite heading of displacement
                linedispcrs += 180.0;
                while (linedispcrs < -180.0) linedispcrs += 360.0;
                while (linedispcrs >= 180.0) linedispcrs -= 360.0;
                pc.startc = linedispcrs;
                pc.stoptc = linedispcrs;
            }
        }

        /**
         * Compute fillet from one line to another line.
         *  Input:
         *   prevpointx,y = where previous course line ends
         *   prevtrucrs   = true course into prevpointx,y
         *   nextpointx,y = where next course line ends
         *   nexttrucrs   = true course from nextpointx,y
         */
        public void calcFilletLineToLine (
                double prevpointx, double prevpointy, double prevtrucrs,
                double nextpointx, double nextpointy, double nexttrucrs)
        {
            // see how much we are turning by
            // normalize to range -180..+180
            // neg for left; pos for right
            double turnbydeg = nexttrucrs - prevtrucrs;
            while (turnbydeg < -180.0) turnbydeg += 360.0;
            while (turnbydeg >= 180.0) turnbydeg -= 360.0;

            // if slight turn or hairpin turn, don't bother with fillet
            if ((Math.abs (turnbydeg) < MINFILLET) || (Math.abs (turnbydeg) > MAXFILLET)) {
                turndir = 0;
                return;
            }

            // compute some point ahead of airplane on its current course
            double acraftbmx_ahead = prevpointx + Mathf.sindeg (prevtrucrs) * 1024.0;
            double acraftbmy_ahead = prevpointy - Mathf.cosdeg (prevtrucrs) * 1024.0;

            // compute some point ahead of next point along its initial course
            double nextbmx_ahead = nextpointx + Mathf.sindeg (nexttrucrs) * 1024.0;
            double nextbmy_ahead = nextpointy - Mathf.cosdeg (nexttrucrs) * 1024.0;

            // find intersection of those two lines, giving us the elbow of the turn
            double elbow_bmx = Lib.lineIntersectX (
                    prevpointx, prevpointy, acraftbmx_ahead, acraftbmy_ahead,
                    nextpointx, nextpointy, nextbmx_ahead, nextbmy_ahead);
            double elbow_bmy = Lib.lineIntersectY (
                    prevpointx, prevpointy, acraftbmx_ahead, acraftbmy_ahead,
                    nextpointx, nextpointy, nextbmx_ahead, nextbmy_ahead);

            // find true course from elbow to center of circle
            double elbow2centercrs;
            if (turnbydeg < 0.0) {
                // turning left
                elbow2centercrs = prevtrucrs - 90.0 + turnbydeg / 2.0;
            } else {
                // turning right
                elbow2centercrs = prevtrucrs + 90.0 + turnbydeg / 2.0;
            }

            // find center of circle by going from elbow along that course
            double center2elbow_bmp = stdturnradbmp / Mathf.cosdeg (turnbydeg / 2.0);
            x = elbow_bmx + Mathf.sindeg (elbow2centercrs) * center2elbow_bmp;
            y = elbow_bmy - Mathf.cosdeg (elbow2centercrs) * center2elbow_bmp;

            // sweep is number of degrees for arc clockwise from start
            sweep = Math.abs (turnbydeg);

            // start is angle clockwise from East to start at
            //  0=E; 90=S; 180=W; 270=N
            if (turnbydeg < 0.0) {
                // turning left
                start   = nexttrucrs;
                startc  = prevtrucrs + 90.0;
                stoptc  = nexttrucrs + 90.0;
                turndir = 'L';
            } else {
                // turning right
                start   = prevtrucrs + 180.0;
                startc  = prevtrucrs - 90.0;
                stoptc  = nexttrucrs - 90.0;
                turndir = 'R';
            }
        }
    }

    /**
     * Compute OBS and deflection values for NavDialView.
     */
    // tc = true course of line segment headed in same direction as correctly-flown aircraft
    private double lineObsSetting (double tc)
    {
        return aptmagvar + tc;
    }
    // begbmx,y = beginning of line (behind correctly-flown aircraft)
    // endbmx,y = end of line (ahead of correctly-flown aircraft)
    private double lineDeflection (double begbmx, double begbmy, double endbmx, double endbmy)
    {
        double tcline = trueCourse (begbmx, begbmy, endbmx, endbmy);
        return lineDeflection (begbmx, begbmy, tcline);
    }
    // begbmx,y = beginning of line (behind correctly-flown aircraft)
    // tcline = true course of line segment headed in same direction as correctly-flown aircraft
    private double lineDeflection (double begbmx, double begbmy, double tcline)
    {
        // translate aircraft to put begbmx,y at 0,0
        double acbmx = acraftbmx - begbmx;
        double acbmy = acraftbmy - begbmy;

        // get true course from origin to aircraft
        double tcacft = trueCourse (0.0, 0.0, acbmx, acbmy);

        // get aircraft point with courseline rotated to 360deg (ie, up, north)
        double acftlen  = Math.hypot (acbmx, acbmy);
        double acrotbmx = Mathf.sindeg (tcacft - tcline) * acftlen;

        // acrotbmx < 0 : aircraft left of course by that many pixels, so deflect needle right
        // acrotbmx > 0 : aircraft right of course by that many pixels, so deflect needle left
        return - acrotbmx / bmpixpernm * NavDialView.LOCDEFLECT / TERMDEFLECT;
    }
    // ctrbmx,y = center of arc
    // turndir = turn direction of correctly-flown aircraft
    private double arcObsSetting (double ctrbmx, double ctrbmy, char turndir)
    {
        double obs = aptmagvar + trueCourse (ctrbmx, ctrbmy, acraftbmx, acraftbmy);
        if (turndir == 'L') obs -= 90.0;
        if (turndir == 'R') obs += 90.0;
        return obs;
    }
    // ctrbmx,y = center of arc
    // radius = radius of arc
    // turndir = turn direction of correctly-flown aircraft
    // returns = 0: flying on arc
    //         < 0: need to turn a little left to get on arc
    //         > 0: need to turn a little right to get on arc
    private double arcDeflection (double ctrbmx, double ctrbmy, double radius, char turndir)
    {
        // dist > 0: aircraft is a little outside the arc
        //      < 0: aircraft is a little inside the arc
        double dist = Math.hypot (acraftbmx - ctrbmx, acraftbmy - ctrbmy) - radius;

        if (turndir == 'L') dist = - dist;

        return dist / bmpixpernm * NavDialView.LOCDEFLECT / TERMDEFLECT;
    }

    /**
     * DME Arc
     */
    private class CIFPLeg_AF extends CIFPLeg {
        public double endingbmx, endingbmy;
        public double endinghdg;
        public Waypoint navwp;

        private PointD navpt = new PointD ();

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
                sb.append (DIRTOARR);
                appCourse (sb, "end");
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                // draw dots around the radius
                drawStepDotArc (navpt.x, navpt.y, arcradbmp,
                        Math.min (arcbegrad, arcendrad) - 90.0,
                        Math.abs (arcendrad - arcbegrad), true);

                // return ending alt,hdg,bmx,bmy
                updateAlt ();
                curposhdg = endinghdg;
                curposbmx = endingbmx;
                curposbmy = endingbmy;
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // check for being in fillet at end of DME arc
                if (!inEndFillet (navDial)) {

                    // never in glideslope mode for DME arc
                    navDial.setMode (NavDialView.Mode.LOC);

                    // set OBS based on position from center of arc
                    navDial.setObs (arcObsSetting (navpt.x, navpt.y, arcturndir));

                    // set deflection based on distance from arc
                    navDial.setDeflect (arcDeflection (navpt.x, navpt.y, arcradbmp, arcturndir));
                }

                // put navwp in DME window
                double distnav = Math.hypot (acraftbmx - navpt.x, acraftbmy - navpt.y);
                navDial.setDistance (distnav / bmpixpernm, navwp.ident, false);
            }
        };

        public String init1 () throws Exception
        {
            // get parameters
            navwp = findWaypoint (parms.get ("nav"), navpt);
            afstep.arcctrbmx = navpt.x;
            afstep.arcctrbmy = navpt.y;
            afstep.arcbegrad = getTCDeg ("beg", navwp);
            afstep.arcendrad = getTCDeg ("end", navwp);
            double rnm = Integer.parseInt (parms.get ("nm")) / 10.0;
            afstep.arcradbmp = rnm * bmpixpernm;

            // adjust radials to be within 180.0 of each other
            while (afstep.arcendrad < afstep.arcbegrad - 180) afstep.arcendrad += 360;
            while (afstep.arcendrad > afstep.arcbegrad + 180) afstep.arcbegrad += 360;

            // figure out which direction we are turning
            double diff = afstep.arcendrad - afstep.arcbegrad;
            while (diff < -180.0) diff += 360.0;
            while (diff >= 180.0) diff -= 360.0;
            afstep.arcturndir = (diff < 0.0) ? 'L' : 'R';

            // compute what heading will be at end of arc
            endinghdg = afstep.arcendrad;
            if (afstep.arcturndir == 'L') endinghdg += 270;
            if (afstep.arcturndir == 'R') endinghdg +=  90;
            while (endinghdg >= 360) endinghdg -= 360;

            // compute the endpoint of the dme arc
            endingbmx = navpt.x + Mathf.sindeg (afstep.arcendrad) * afstep.arcradbmp;
            endingbmy = navpt.y - Mathf.cosdeg (afstep.arcendrad) * afstep.arcradbmp;

            return null;
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
     * Usually used at beginning of missed approach to climb on runway heading before turning.
     */
    private class CIFPLeg_CA extends CIFPLeg {
        private double altitude, maxdist;
        private double ochdgtru;
        private double climbegalt;
        private double climbegbmx, climbegbmy;
        private double endbmx, endbmy;

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
                    if (maxdist == 0.0) {
                        double oldalt = curposalt;
                        updateAlt ();
                        double newalt = curposalt;
                        if (newalt > oldalt) {
                            maxdist = (newalt - oldalt) / CLIMBFT_NM;
                        }
                        if (maxdist < 1.0) maxdist = 1.0;
                        maxdist *= bmpixpernm;
                        endbmx = curposbmx + Mathf.sindeg (ochdgtru) * maxdist;
                        endbmy = curposbmy - Mathf.cosdeg (ochdgtru) * maxdist;
                    }
                } else {

                    // this step is current, draw dots using actual climb rate
                    double curgpsaltft = acraftalt;
                    if (Double.isNaN (climbegalt)) {

                        // first time here, save starting point
                        climbegalt = curgpsaltft;
                        climbegbmx = acraftbmx;
                        climbegbmy = acraftbmy;
                    } else {

                        // subsequent, distance and altitude since first time
                        // .. then calc average distance per altitude so far
                        double distsofar = Math.hypot (climbegbmx - acraftbmx, climbegbmy - acraftbmy);
                        double altsofar = curgpsaltft - climbegalt;
                        double distperalt = distsofar / altsofar;

                        // get total distance since first time needed to climb to target altitude
                        double totaldist = distperalt * (altitude - climbegalt);
                        if (totaldist < bmpixpernm) totaldist = bmpixpernm;
                        if (totaldist > maxdist)    totaldist = maxdist;

                        // extend that distance to put beginning of the fillet at that distance
                        // so they don't slam into tower
                        if (filletArc.turndir != 0) {
                            double filetbmx = filletArc.getBegX ();
                            double filetbmy = filletArc.getBegY ();
                            totaldist += Math.hypot (endbmx - filetbmx, endbmy - filetbmy);
                        }

                        // calculate end point that distance on target heading
                        // from proper starting point so it is aligned with chart drawing
                        endbmx = curposbmx + Mathf.sindeg (ochdgtru) * totaldist;
                        endbmy = curposbmy - Mathf.cosdeg (ochdgtru) * totaldist;
                    }
                }

                // draw dots to the end point
                drawStepCourseToPoint (endbmx, endbmy);
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                getLinearVNTracking (navDial, "climb");
            }
        };

        public String init1 ()
        {
            ochdgtru = getTCDeg ("mc", null);
            altitude = getAltitude ();
            climbegalt = Double.NaN;
            return null;
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
        private double dmebmp, dmenm, ochdg;
        private PointD navpt = new PointD ();
        private Waypoint navwp;

        private final CIFPStep cdstep = new CIFPStep () {
            private String vnwpid;

            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appCourse (sb, "mc");
                sb.append (DIRTOARR);
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

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                if (vnwpid == null) vnwpid = navwp.ident + "[" + Lib.DoubleNTZ (dmenm);
                getLinearVNTracking (navDial, vnwpid);
            }
        };

        public String init1 () throws Exception
        {
            dmenm  = Integer.parseInt (parms.get ("nm")) / 10.0;
            dmebmp = dmenm * bmpixpernm;
            navwp  = findWaypoint (parms.get ("nav"), navpt);
            ochdg  = getTCDeg ("mc", navwp);
            return null;
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
        public double trucrs;
        public Waypoint navwp;
        private PointD navpt = new PointD ();

        private final CIFPStep cfstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                double nm = Math.hypot (begptbmx - navpt.x, begptbmy - navpt.y) / bmpixpernm;
                if (nm >= RUNTNM) {
                    double tc = trueCourse (begptbmx, begptbmy, navpt.x, navpt.y);
                    appTrueAsMag (sb, tc);
                    sb.append (DIRTOARR);
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

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                getLinearVNTracking (navDial, navwp.ident);
            }
        };

        public String init1 () throws Exception
        {
            String wpid = parms.get ("wp");
            navwp  = findWaypoint (wpid, navpt);
            trucrs = parms.containsKey ("mc") ? getTCDeg ("mc", navwp) : Double.NaN;
            return navwp.ident;
        }

        public boolean isRuntLeg (CIFPLeg prevleg)
        {
            // if a CF follows an AF that ends on this fix,
            // the CF is redundant
            if (prevleg instanceof CIFPLeg_AF) {
                CIFPLeg_AF prevaf = (CIFPLeg_AF) prevleg;
                double distpix = Math.hypot (prevaf.endingbmx - navpt.x, prevaf.endingbmy - navpt.y);
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
                        double final_tc    = Lib.LatLonTC (fafcf.navwp.lat, fafcf.navwp.lon, airport.lat, airport.lon);
                        double this_to_apt = Lib.LatLonDist (navwp.lat, navwp.lon, airport.lat, airport.lon);
                        double faf_to_arpt = Lib.LatLonDist (fafcf.navwp.lat, fafcf.navwp.lon, airport.lat, airport.lon);
                        if ((this_to_apt > faf_to_arpt) && (angleDiffU (final_tc - prevhold.inbound) < 30.0)) return true;
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
                        double final_tc    = Lib.LatLonTC (fafcf.navwp.lat, fafcf.navwp.lon, airport.lat, airport.lon);
                        double this_to_apt = Lib.LatLonDist (navwp.lat, navwp.lon, airport.lat, airport.lon);
                        double faf_to_arpt = Lib.LatLonDist (fafcf.navwp.lat, fafcf.navwp.lon, airport.lat, airport.lon);
                        if ((this_to_apt > faf_to_arpt) && (angleDiffU (final_tc - prevpi.inbound) < 30.0)) return true;
                    }
                }
            }

            // if a CF follows an RF that ends on this fix,
            // the CF is redundant
            //   PAKT ILS Z 11 at PUTIY
            if (prevleg instanceof CIFPLeg_RF) {
                CIFPLeg_RF prevrf = (CIFPLeg_RF) prevleg;
                double distpix = Math.hypot (prevrf.endpt.x - navpt.x, prevrf.endpt.y - navpt.y);
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
        private double trucrs;

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
                    drawStepCourseToInterceptDist (nextaf.navpt, nextaf.afstep.arcradbmp);
                }

                // if next leg is CF, it has a navaid and a radial that we intercept.
                // so draw dots from current position along our course until we intercept
                // the CF leg's radial.
                if (nextleg instanceof CIFPLeg_CF) {
                    CIFPLeg_CF nextcf = (CIFPLeg_CF) nextleg;
                    if (!Double.isNaN (nextcf.trucrs)) {
                        drawStepCourseToInterceptRadial (trucrs, nextcf.navpt, nextcf.trucrs);
                    }
                }

                updateAlt ();
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                getLinearVNTracking (navDial, "intcp");
            }
        };

        public String init1 ()
        {
            trucrs = getTCDeg ("mc", null);
            return null;
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
        private double ochdg;
        private double radial;
        private PointD navpt = new PointD ();
        private Waypoint navwp;

        private final CIFPStep crstep = new CIFPStep () {
            private String vnwpid;

            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                appCourse (sb, "mc");
                sb.append (DIRTOARR);
                appCourse (sb, "rad");
                sb.append (" ");
                sb.append (parms.get ("nav"));
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                drawStepCourseToInterceptRadial (ochdg, navpt, radial);
                updateAlt ();
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                if (vnwpid == null) vnwpid = navwp.ident + '/' + Lib.DoubleNTZ (radial) + '\u00B0';
                getLinearVNTracking (navDial, vnwpid);
            }
        };

        public String init1 () throws Exception
        {
            navwp  = findWaypoint (parms.get ("nav"), navpt);
            ochdg  = getTCDeg ("mc",  navwp);
            radial = getTCDeg ("rad", navwp);
            return null;
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
        private double distbmp;
        private double endbmx, endbmy;
        private double ochdg;

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
                    endbmx = curposbmx + Mathf.sindeg (ochdg) * distbmp;
                    endbmy = curposbmy - Mathf.cosdeg (ochdg) * distbmp;
                    calcd  = true;
                }
                drawStepCourseToPoint (endbmx, endbmy);
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                getLinearVNTracking (navDial, "ahead");
            }
        };

        public String init1 () throws Exception
        {
            ochdg   = getTCDeg ("mc", null);
            distbmp = Integer.parseInt (parms.get ("nm")) / 10.0 * bmpixpernm;
            return null;
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
        private boolean flyHold;

        /**
         * Hold in place of procedure turn.
         * Ask pilot if they want to do the hold.
         * @return true: pilot was prompted, come back when pilot answers prompt
         *         else: pilot wasn't prompted, continue processing
         */
        @Override
        public boolean isOptional ()
        {
            // maybe we can tell if PT is required or should be skipped
            // SFM-VOR-25/ENE:
            //   from PSM: hold required
            //   from PWM: hold skipped
            //   from IZG: prompt
            int rc = checkOptionalPT (segment, legidx, navpt, inbound);

            ////// don't prompt for hardcore teardrop/parallel/direct entry
            ////// do prompt for borderline cases
            ////if (rc >= 0) {
            ////    flyHold = rc > 0;  // force hold for hardcore teardrop/parallel entry
            ////                       // skip hold for hardcore direct entry
            ////    return false;
            ////}

            // don't prompt for hardcore teardrop/parallel entry
            // do prompt for hardcore direct entry and borderline
            if (rc > 0) {
                flyHold = true;  // force hold for hardcore teardrop/parallel entry
                return false;
            }

            // prompt pilot
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Optional leg");
            adb.setMessage ("Fly Hold at " + parms.get ("wp") + "?");
            adb.setPositiveButton ("Yes", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    flyHold = true;
                    CIFPSegmentSelFinal (segment);
                }
            });
            adb.setNeutralButton ("No", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    flyHold = false;
                    CIFPSegmentSelFinal (segment);
                }
            });
            adb.setNegativeButton ("Cancel", null);
            adb.show ();
            return true;
        }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            if (flyHold) {
                steps.addLast (hstep1);
                steps.addLast (hstep2);
            }
        }
    }
    // hold indefinitely
    // - currently only used at end of missed segment, ie, end of approach
    //   so we can assume there is no next step
    private class CIFPLeg_HM extends CIFPLeg_Hold {
        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (hstep1);
            steps.addLast (hstep2);
            steps.addLast (hstep3);
        }
    }

    // starts right at fix and is tangential to circle at far end of hold
    // should be approx 35.3 degrees for one-minute legs and 3deg/sec turns
    private final static double teardropdiag =
            Math.toDegrees (Math.atan (stdturnradsec / HOLDLEGSEC)) * 2.0;

    // tangential to both circles of hold
    // should be approx 39.6 degrees for one-minute legs and 3deg/sec turns
    private final static double paralleldiag =
            Math.toDegrees (Math.asin (stdturnradsec / HOLDLEGSEC * 2.0));

    private abstract class CIFPLeg_Hold extends CIFPLeg {
        protected double inbound, inboundsin, inboundcos; // heading inbound to navwp
        protected PointD navpt = new PointD ();  // navaid/fix the hold is based on
        protected Waypoint navwp;

        private char turndir;
        private double parahdg;    // heading along parallel diagonal toward navwp (two curls)
        private double tearhdg;    // heading along teardrop diagonal away from navwp (one curl)
        private String samedir, oppodir;

        private double tc_acraft_to_navwp = Double.NaN;
                                            // incoming (entry) aircraft heading relative to inbound
                                            //  -180..-70 : parallel entry
                                            //  -70..+110 : direct entry
                                            // +110..+180 : teardrop entry
        private double holdleglen;
        private double farctrbmx;
        private double farctrbmy;
        private double nearctrbmx;
        private double nearctrbmy;
        private double inboundfarbmx;
        private double inboundfarbmy;
        private double outboundfarbmx;
        private double outboundfarbmy;
        private double outboundnearbmx;
        private double outboundnearbmy;

        private double parallelfarstart;
        private double paralleldiagfarx;
        private double paralleldiagfary;
        private double paralleldiagnearx;
        private double paralleldiagneary;
        private boolean parallelhitfararc;

        private double teardropfarstart;
        private double teardropdiagfarx;
        private double teardropdiagfary;
        private boolean teardrophitfararc;

        private double directctrbmx;         // for direct entry, center of displaced near-end turn
        private double directctrbmy;
        private double directinboundintersectbmx;
        private double directinboundintersectbmy;
        private double directoutboundintersectbmx;
        private double directoutboundintersectbmy;
        private double directstart;
        private double directsweep;
        private double directstartc;
        private double directstoptc;
        private double directbegarcx;
        private double directbegarcy;

        private boolean step3neararcenab;
        private boolean step3outboundenab;
        private boolean step3fararcenab;
        private boolean step3inboundenab;

        /**
         * Start at the navwp, go outbound then turn inbound.
         * - parallel: inbound radial opposite direction, far-end turn to diagonal
         * - teardrop: diagonal radial, far-end turn to inbound
         * - direct: near-end turn to outbound line, outbound line, far-end turn to inbound
         */
        protected final CIFPStep hstep1 = new CIFPStep () {
            private double dist0, dist1, dist2, dist3;
            private long starttime;

            // get text line describing the hold
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                if (tc_acraft_to_navwp < -70.0) {
                    // parallel: from navwp -> reverse inbound -> reverse turn
                    sb.append ("par hold ");
                    sb.append (navwp.ident);
                    sb.append (' ');
                    appTrueAsMag (sb, inbound + 180.0);
                    sb.append (DIRTOARR);
                    appendTurn (sb, (char) ('L' ^ 'R' ^ turndir));
                } else if (tc_acraft_to_navwp > 110.0) {
                    // teardrop: from navwp -> diagonal -> turn inbound
                    sb.append ("tear hold ");
                    sb.append (navwp.ident);
                    sb.append (' ');
                    appTrueAsMag (sb, tearhdg);
                    sb.append (DIRTOARR);
                    appendTurn (sb, turndir);
                } else {
                    // direct: from navwp -> near-end turn -> outbound line -> turn inbound
                    sb.append ("dir hold ");
                    sb.append (navwp.ident);
                    sb.append (DIRTOARR);
                    appendTurn (sb, turndir);
                }
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
                // if this step is in the future, compute entry type
                // entry type can change up to the point where the hold becomes current
                if ((currentStep < drawIndex) || Double.isNaN (tc_acraft_to_navwp)) {

                    // see which direction the aircraft is heading coming into this step
                    // that gets it to navwp, relative to hold's inbound leg
                    tc_acraft_to_navwp = curposhdg - inbound;
                    while (tc_acraft_to_navwp < -180.0) tc_acraft_to_navwp += 360.0;
                    while (tc_acraft_to_navwp >= 180.0) tc_acraft_to_navwp -= 360.0;

                    // negative means we are on the side of the hold
                    // positive means we are outside the hold side
                    //    0 = direct entry
                    // +179 = teardrop entry
                    // -179 = parallel entry
                    if (turndir == 'L') tc_acraft_to_navwp = -tc_acraft_to_navwp;
                }

                // assume step 3 should draw the whole oval for now
                // disable whatever is drawn in step 1 and step 2
                step3neararcenab  = true;
                step3outboundenab = true;
                step3fararcenab   = true;
                step3inboundenab  = true;

                // see if this is current step or next-to-be-current step
                boolean curr = (drawIndex == currentStep);
                boolean next = (drawIndex == currentStep + 1);

                // if teardrop or parallel and we have flown partway down the initial leg,
                // and aircraft is much closer to the wrong leg, switch to the other entry type
                if (curr && ((tc_acraft_to_navwp < -70.0) || (tc_acraft_to_navwp > 110.0))) {
                    if (starttime == 0) starttime = wairToNow.currentGPSTime;
                    if (wairToNow.currentGPSTime - starttime > HOLDLEGSEC * 250) {
                        starttime = Long.MAX_VALUE;
                        computeTeardrop ();
                        double dist_inradial = Lib.distToLineSeg (acraftbmx, acraftbmy,
                                navpt.x, navpt.y, inboundfarbmx, inboundfarbmy);
                        double dist_teardiag = Lib.distToLineSeg (acraftbmx, acraftbmy,
                                navpt.x, navpt.y, teardropdiagfarx, teardropdiagfary);
                        if (tc_acraft_to_navwp < 0.0) {
                            if (dist_teardiag * 2.0 < dist_inradial) {
                                tc_acraft_to_navwp =  200.0;
                                Log.d ("WairToNow", "PlateCIFP*: switch to teardrop");
                            } else {
                                Log.d ("WairToNow", "PlateCIFP*: parallel confirmed");
                            }
                        } else {
                            if (dist_inradial * 2.0 < dist_teardiag) {
                                tc_acraft_to_navwp = -200.0;
                                Log.d ("WairToNow", "PlateCIFP*: switch to parallel");
                            } else {
                                Log.d ("WairToNow", "PlateCIFP*: teardrop confirmed");
                            }
                        }
                    }
                }

                // draw differently depending on how hold is entered
                computeCommon ();
                if (tc_acraft_to_navwp < -70.0) {
                    computeParallel ();

                    // draw inbound radial dots
                    // sensitive only when next so as to start the hold
                    // ...or when current to stay in the hold when on the line
                    dist1 = drawStepDotLine (navpt.x, navpt.y, inboundfarbmx, inboundfarbmy, next | curr);

                    // draw parallel far turn dots
                    // sensitive only when step is current to keep step current when on the far turn
                    dist2 = drawStepDotArc (farctrbmx, farctrbmy, stdturnradbmp, parallelfarstart, paralleldiag + 180.0, curr);

                    // end up at far end of diagonal headed along diagonal
                    curposbmx = paralleldiagfarx;
                    curposbmy = paralleldiagfary;
                    curposhdg = parahdg;

                    // if we are settled in as to which way we are going, see if we have tagged up with far arc
                    parallelhitfararc |= (dist2 < dist1) && (starttime == Long.MAX_VALUE);

                    // tell step 3 not to bother drawing inbound radial or far arc
                    step3inboundenab = false;
                    step3fararcenab  = false;
                } else if (tc_acraft_to_navwp > 110.0) {
                    computeTeardrop ();

                    // draw teardrop diagonal dots
                    // sensitive only when next so as to start the hold
                    // ...or when current to stay in the hold when on the line
                    dist1 = drawStepDotLine (navpt.x, navpt.y, teardropdiagfarx, teardropdiagfary, next | curr);

                    // draw teardrop far-end turn dots
                    // sensitive only when step is current to keep step current when on the far turn
                    dist2 = drawStepDotArc (farctrbmx, farctrbmy, stdturnradbmp, teardropfarstart, teardropdiag + 180.0, curr);

                    // end up at far end of inbound radial headed toward navwp
                    curposbmx = inboundfarbmx;
                    curposbmy = inboundfarbmy;
                    curposhdg = inbound;

                    // if we are settled in as to which way we are going, see if we have tagged up with far arc
                    teardrophitfararc |= (dist2 < dist1) && (starttime == Long.MAX_VALUE);

                    // tell step 3 not to bother drawing far arc
                    step3fararcenab = false;
                } else {
                    computeDirect ();

                    // draw little segment from fix to beginning of arc
                    // ...for those entries coming from outside the hold
                    dist0 = Double.MAX_VALUE;
                    if (tc_acraft_to_navwp > 0.0) {
                        dist0 = drawStepDotLine (navpt.x, navpt.y, directbegarcx, directbegarcy, next | curr);
                    }

                    // draw direct displaced near turn dots
                    // sensitive only when next so as to start the hold
                    // ...or when current to stay in the hold when on the line
                    dist1 = drawStepDotArc (directctrbmx, directctrbmy, stdturnradbmp, directstart, directsweep, next | curr);

                    // draw direct outbound line dots
                    // sensitive only when step is current to keep step current when on the outbound line
                    // must not be sensitive earlier or it will trip on a cross-over direct entry
                    dist2 = drawStepDotLine (directoutboundintersectbmx, directoutboundintersectbmy,
                            outboundfarbmx, outboundfarbmy, curr);

                    // draw normal far turn dots
                    // sensitive only when step is current to keep step current when on the far turn
                    dist3 = drawStepDotArc (farctrbmx, farctrbmy, stdturnradbmp, inbound, 180.0, curr);

                    // end up at far end of inbound radial headed toward navwp
                    curposbmx = inboundfarbmx;
                    curposbmy = inboundfarbmy;
                    curposhdg = inbound;

                    step3neararcenab  = false;
                    step3outboundenab = false;
                    step3fararcenab   = false;
                }

                // update what altitude they should be at when at navwp
                updateAlt ();
            }

            // draw fillet showing where/how to turn to enter this step.
            // called when this step is next-to-be-current (ignoring runts).
            @Override
            public void drawFillet ()
            {
                // check for parallel entry
                // supply a normal fillet to transition onto the inbound but in outbound direction
                if (tc_acraft_to_navwp < -70.0) {
                    drawFilletToLine (navpt.x, navpt.y, inbound + 180.0);
                    return;
                }

                // check for teardrop entry
                // supply a normal fillet to transition onto the diagonal going away from navpt
                if (tc_acraft_to_navwp > 110.0) {
                    drawFilletToLine (navpt.x, navpt.y, tearhdg);
                    return;
                }

                // must be direct entry, the displaced near-end turn is the fillet
                filletArc.x = directctrbmx;
                filletArc.y = directctrbmy;
                filletArc.start = directstart;
                filletArc.sweep = directsweep;
                filletArc.startc = directstartc;
                filletArc.stoptc = directstoptc;
                filletArc.turndir = turndir;
            }

            // holds are never runts even though they start and end on the same spot
            @Override
            public boolean isRuntStep ()
            {
                return false;
            }

            // get number of bitmap pixels to next turn
            // called only when this step is current
            @Override
            public double bmpToNextTurn (NextTurn nextTurn)
            {
                // check for parallel entry
                if (tc_acraft_to_navwp < -70.0) {

                    // only turn we do is to the diagonal inbound to navwp
                    nextTurn.direction  = oppodir;
                    nextTurn.truecourse = parahdg;

                    // if along inbound line, num sec to turn is how long to far end of inbound line
                    if (dist1 < dist2) {
                        return Math.hypot (acraftbmx - inboundfarbmx, acraftbmy - inboundfarbmy);
                    }

                    // otherwise, we're already in the turn
                    return 0.0;
                }

                // check for teardrop entry
                if (tc_acraft_to_navwp > 110.0) {

                    // only turn we do is to inbound radial to navwp
                    nextTurn.direction  = samedir;
                    nextTurn.truecourse = inbound;

                    // if along teardrop diagonal, num sec to turn is how long to far end of diagonal
                    if (dist1 < dist2) {
                        return Math.hypot (acraftbmx - teardropdiagfarx, acraftbmy - teardropdiagfary);
                    }

                    // otherwise, we're already in the turn
                    return 0.0;
                }

                // direct entry

                nextTurn.direction  = samedir;
                nextTurn.truecourse = inbound + 180.0;

                // - see if between navwp and near-end arc
                //   if so, return distance to reach the beginning of the arc
                if ((dist0 < dist1) && (dist0 < dist2) && (dist0 < dist3)) {
                    // how many pixels to go...
                    return Math.hypot (acraftbmx - directbegarcx, acraftbmy - directbegarcy);
                }

                // - see if on the near-end arc
                //   if so, we're already in the turn
                if ((dist1 < dist2) && (dist1 < dist3)) {
                    return 0.0;
                }

                nextTurn.truecourse = inbound;

                // - see if on the outbound line
                //   if so, return distance to reach the end of outbound line
                if (dist2 < dist3) {
                    return Math.hypot (acraftbmx - outboundfarbmx, acraftbmy - outboundfarbmy);
                }

                // - must be on the far end arc somewhere already turning inbound
                return 0.0;
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // never on glideslope in hold
                navDial.setMode (NavDialView.Mode.LOC);

                // DME dist and fix is always navwp
                double distnav = Math.hypot (acraftbmx - navpt.x, acraftbmy - navpt.y);
                navDial.setDistance (distnav / bmpixpernm, navwp.ident, false);

                // check for parallel entry
                if (tc_acraft_to_navwp < -70.0) {

                    // if along inbound line, obs is outbound course
                    if (dist1 < dist2) {
                        navDial.setObs (lineObsSetting (inbound + 180.0));
                        navDial.setDeflect (lineDeflection (navpt.x, navpt.y, inbound + 180.0));
                        return;
                    }

                    // somewhere in far-end turn, obs is right-angle to course from turn center
                    char oppdir = (char) ('L' ^ 'R' ^ turndir);  // going the opposite way
                    navDial.setObs (arcObsSetting (farctrbmx, farctrbmy, oppdir));
                    navDial.setDeflect (arcDeflection (farctrbmx, farctrbmy, stdturnradbmp, oppdir));
                    return;
                }

                // check for teardrop entry
                if (tc_acraft_to_navwp > 110.0) {

                    // if along teardrop diagonal, heading is outbound along diagonal
                    if (dist1 < dist2) {
                        navDial.setObs (lineObsSetting (tearhdg));
                        navDial.setDeflect (lineDeflection (navpt.x, navpt.y, tearhdg));
                        return;
                    }

                    // somewhere in far-end turn, obs is right-angle to course from turn center
                    navDial.setObs (arcObsSetting (farctrbmx, farctrbmy, turndir));
                    navDial.setDeflect (arcDeflection (farctrbmx, farctrbmy, stdturnradbmp, turndir));
                    return;
                }

                // direct entry

                // - see if between navwp and near-end arc
                //   if so, return heading to reach the beginning of the arc
                if ((dist0 < dist1) && (dist0 < dist2) && (dist0 < dist3)) {
                    navDial.setObs (lineObsSetting (trueCourse (navpt.x, navpt.y, directbegarcx, directbegarcy)));
                    navDial.setDeflect (lineDeflection (navpt.x, navpt.y, directbegarcx, directbegarcy));
                    return;
                }

                // - see if on the displaced near-end arc
                //   if so, give heading to continue on displaced near-end arc
                if ((dist1 < dist2) && (dist1 < dist3)) {
                    navDial.setObs (arcObsSetting (directctrbmx, directctrbmy, turndir));
                    navDial.setDeflect (arcDeflection (directctrbmx, directctrbmy, stdturnradbmp, turndir));
                    return;
                }

                // - see if on the outbound line
                //   if so, give heading to stay on outbound line
                if (dist2 < dist3) {
                    navDial.setObs (lineObsSetting (inbound + 180.0));
                    navDial.setDeflect (lineDeflection (outboundnearbmx, outboundnearbmy,
                            outboundfarbmx, outboundfarbmy));
                    return;
                }

                // somewhere in far-end turn, obs is right-angle to course from turn center
                navDial.setObs (arcObsSetting (farctrbmx, farctrbmy, turndir));
                navDial.setDeflect (arcDeflection (farctrbmx, farctrbmy, stdturnradbmp, turndir));
            }
        };

        /**
         * Start where hstep1 left off and get to navwp.
         * - parallel: head inbound along diagonal, make little turn at end to inbound heading over navwp
         * - teardrop and direct: head along inbound radial to over navwp
         */
        protected final CIFPStep hstep2 = new CIFPStep () {

            // get text line describing the hold
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                if (tc_acraft_to_navwp < -70.0) {
                    // parallel: inbound on diagonal
                    appTrueAsMag (sb, parahdg);
                    sb.append (DIRTOARR);
                    appendTurn (sb, turndir);
                    // outbound heading from navwp
                    appTrueAsMag (sb, inbound + 180.0);
                } else {
                    // teardrop, direct: inbound heading to navwp
                    appTrueAsMag (sb, inbound);
                }
                sb.append (DIRTOARR);
                sb.append (navwp.ident);
                appendAlt (sb);
                return sb.toString ();
            }

            // draw dots for the hold
            @Override
            public void drawStepDots ()
            {
                boolean curr = (drawIndex == currentStep);
                boolean next = (drawIndex == currentStep + 1);

                if (curr) {
                    step3neararcenab  = true;
                    step3outboundenab = true;
                    step3fararcenab   = true;
                    step3inboundenab  = true;
                }

                computeCommon ();
                if (tc_acraft_to_navwp < -70.0) {
                    computeParallel ();
                    // draw diagonal line dots
                    next &= parallelhitfararc;  // don't become current unless they got near the far arc
                                                // just in case entry was poorly flown
                    drawStepDotLine (paralleldiagfarx, paralleldiagfary, paralleldiagnearx, paralleldiagneary, next | curr);

                    // end up just inside navwp on parallel diag heading
                    // depend on fillet to next step to round it out to go over navwp
                    curposbmx = paralleldiagnearx;
                    curposbmy = paralleldiagneary;
                    curposhdg = parahdg;
                } else if (tc_acraft_to_navwp > 110.0) {
                    // teardrop, draw inbound line dots
                    next &= teardrophitfararc;  // don't become current unless they got near the far arc
                                                // just in case entry was poorly flown
                    drawStepDotLine (inboundfarbmx, inboundfarbmy, navpt.x, navpt.y, next | curr);

                    step3inboundenab = false;

                    // end up at navwp on inbound radial heading
                    curposbmx = navpt.x;
                    curposbmy = navpt.y;
                    curposhdg = inbound;
                } else {
                    // direct, draw inbound line dots
                    drawStepDotLine (inboundfarbmx, inboundfarbmy, navpt.x, navpt.y, next | curr);

                    step3inboundenab = false;

                    // end up at navwp on inbound radial heading
                    curposbmx = navpt.x;
                    curposbmy = navpt.y;
                    curposhdg = inbound;
                }

                // update what altitude they should be at when at navwp
                updateAlt ();
            }

            // draw fillet showing where/how to turn to enter this step.
            // hstep1 drew the fillet as part of its step
            @Override
            public void drawFillet ()
            { }

            // holds are never runts even though they start and end on the same spot
            @Override
            public boolean isRuntStep ()
            {
                return false;
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // DME dist and fix is always navwp
                double distnav = Math.hypot (acraftbmx - navpt.x, acraftbmy - navpt.y);
                navDial.setDistance (distnav / bmpixpernm, navwp.ident, false);

                // check for fillet at end of step
                // if there is a step 3, it filled this in to lead into step 3
                if (inEndFillet (navDial)) return;

                // parallel entry, give heading along diagonal
                if (tc_acraft_to_navwp < -70.0) {
                    navDial.setObs (lineObsSetting (parahdg));
                    navDial.setDeflect (lineDeflection (paralleldiagfarx, paralleldiagfary, parahdg));
                    return;
                }

                // teardrop or direct entry, give inbound radial heading
                navDial.setObs (lineObsSetting (inbound));
                navDial.setDeflect (lineDeflection (inboundfarbmx, inboundfarbmy, navpt.x, navpt.y));
            }
        };

        /**
         * Draw entire oval for final part of an indefinite hold (HM).
         * Start at the navwp, go around normal oval pattern back to navwp.
         * Used only for indefinite hold (HM) at the end.
         */
        protected final CIFPStep hstep3 = new CIFPStep () {
            private double dist1, dist2, dist3, dist4;

            // get text line describing the hold
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                sb.append ("hold ");
                appTrueAsMag (sb, inbound);
                sb.append (DIRTOARR);
                sb.append (navwp.ident);
                sb.append (' ');
                appendTurn (sb, turndir);
                appendAlt (sb);
                return sb.toString ();
            }

            // draw dots for the hold
            @Override
            public void drawStepDots ()
            {
                boolean curr = (drawIndex == currentStep);
                boolean next = (drawIndex == currentStep + 1);

                if (curr) {
                    step3neararcenab  = true;
                    step3outboundenab = true;
                    step3fararcenab   = true;
                    step3inboundenab  = true;
                }

                computeCommon ();

                // draw near-end arc
                // but not if hstep2 drew it as a direct entry displaced near-end arc
                if (step3neararcenab) {
                    dist1 = drawStepDotArc (nearctrbmx, nearctrbmy, stdturnradbmp, inbound + 180.0, 180.0, next | curr);
                } else {
                    dist1 = Lib.distOfPtFromArc (nearctrbmx, nearctrbmy, stdturnradbmp, inbound + 180.0, 180.0, acraftbmx, acraftbmy);
                    if (next && (smallestAcraftDist > dist1)) smallestAcraftDist = dist1;
                }
                // draw outbound line
                if (step3outboundenab) {
                    dist2 = drawStepDotLine (outboundnearbmx, outboundnearbmy, outboundfarbmx, outboundfarbmy, curr);
                } else {
                    dist2 = Double.MAX_VALUE;
                }
                // draw far-end arc
                if (step3fararcenab) {
                    dist3 = drawStepDotArc (farctrbmx, farctrbmy, stdturnradbmp, inbound, 180.0, curr);
                } else {
                    dist3 = Double.MAX_VALUE;
                }
                // draw inbound radial
                if (step3inboundenab) {
                    dist4 = drawStepDotLine (inboundfarbmx, inboundfarbmy, navpt.x, navpt.y, curr);
                } else {
                    dist4 = Double.MAX_VALUE;
                }

                // end up over navwp on inbound heading
                curposbmx = inboundfarbmx;
                curposbmy = inboundfarbmy;
                curposhdg = inbound;

                // update what altitude they should be at when at navwp
                updateAlt ();
            }

            // draw fillet showing where/how to turn to enter this step.
            @Override
            public void drawFillet ()
            {
                filletArc.turndir = turndir;  // the fillet turns same direction as hold
                filletArc.x = nearctrbmx;     // center of fillet is near arc's center
                filletArc.y = nearctrbmy;

                // if parallel entry, step 2 does not have a fillet leading to this step
                // so we must draw one
                if (tc_acraft_to_navwp < -70.0) {
                    // draw just the little piece going from the diagonal to navwp
                    // the rest of it (180deg turn to outbound) is drawn as part of our oval
                    // but we want the message to say the fillet goes all the way around the 180deg turn as well
                    // ...so the pilot doesn't see two 'turn to ...' one right after the other
                    filletArc.sweep = paralleldiag;
                    if (turndir == 'L') {
                        filletArc.start  = inbound;
                        filletArc.startc = inbound + 90.0 + paralleldiag;
                        filletArc.stoptc = inbound - 90.0;
                    }
                    if (turndir == 'R') {
                        filletArc.start  = inbound + 180.0 - paralleldiag;
                        filletArc.startc = inbound -  90.0 - paralleldiag;
                        filletArc.stoptc = inbound +  90.0;
                    }
                } else {
                    // teardrop or direct, step 2 last drew the inbound radial to the fix
                    // and the airplane is on the inbound heading ready to start 180deg turn
                    filletArc.sweep = 0.0;  // nothing to draw
                    if (turndir == 'L') {
                        filletArc.start  = inbound;
                        filletArc.startc = inbound + 90.0;
                        filletArc.stoptc = inbound - 90.0;
                    }
                    if (turndir == 'R') {
                        filletArc.start  = inbound + 180.0;
                        filletArc.startc = inbound -  90.0;
                        filletArc.stoptc = inbound +  90.0;
                    }
                }
            }

            // holds are never runts even though they start and end on the same spot
            @Override
            public boolean isRuntStep ()
            {
                return false;
            }

            // get number of bitmap pixels to next turn
            // called only when this step is current
            @Override
            public double bmpToNextTurn (NextTurn nextTurn)
            {
                nextTurn.direction = samedir;

                // if on near arc, we are already in the turn
                if ((dist1 < dist2) && (dist1 < dist3) && (dist1 < dist4)) {
                    nextTurn.truecourse = inbound + 180.0;
                    return 0.0;
                }

                // if on outbound line, next turn is the far arc
                if ((dist2 < dist3) && (dist2 < dist4)) {
                    nextTurn.truecourse = inbound;
                    return Math.hypot (outboundfarbmx - acraftbmx, outboundfarbmy - acraftbmy);
                }

                // if on far arc, we are already in the turn
                if (dist3 < dist4) {
                    nextTurn.truecourse = inbound;
                    return 0.0;
                }

                // on inbound radial, next turn is the near arc
                nextTurn.truecourse = inbound + 180.0;
                return Math.hypot (navpt.x - acraftbmx, navpt.y - acraftbmy);
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // DME dist and fix is always navwp
                double distnav = Math.hypot (acraftbmx - navpt.x, acraftbmy - navpt.y);
                navDial.setDistance (distnav / bmpixpernm, navwp.ident, false);

                // see if on near arc
                if ((dist1 < dist2) && (dist1 < dist3) && (dist1 < dist4)) {
                    navDial.setObs (arcObsSetting (nearctrbmx, nearctrbmy, turndir));
                    navDial.setDeflect (arcDeflection (nearctrbmx, nearctrbmy, stdturnradbmp, turndir));
                    return;
                }

                // see if on outbound line
                if ((dist2 < dist3) && (dist2 < dist4)) {
                    navDial.setObs (lineObsSetting (inbound + 180.0));
                    navDial.setDeflect (lineDeflection (outboundnearbmx, outboundnearbmy,
                            outboundfarbmx, outboundfarbmy));
                    return;
                }

                // see if on far arc
                if (dist3 < dist4) {
                    navDial.setObs (arcObsSetting (farctrbmx, farctrbmy, turndir));
                    navDial.setDeflect (arcDeflection (farctrbmx, farctrbmy, stdturnradbmp, turndir));
                    return;
                }

                // on inbound radial
                navDial.setObs (lineObsSetting (inbound));
                navDial.setDeflect (lineDeflection (inboundfarbmx, inboundfarbmy, navpt.x, navpt.y));
            }
        };

        public String init1 () throws Exception
        {
            navwp   = findWaypoint (parms.get ("wp"), navpt);
            inbound = getTCDeg ("rad", navwp);
            turndir = parms.get ("td").charAt (0);
            if ((turndir != 'L') && (turndir != 'R')) throw new Exception ("bad turn dir " + turndir);
            if (turndir == 'L') {
                parahdg = inbound + paralleldiag;
                tearhdg = inbound + 180.0 + teardropdiag;
                samedir = turnleft;
                oppodir = turnright;
            }
            if (turndir == 'R') {
                parahdg = inbound - paralleldiag;
                tearhdg = inbound + 180.0 - teardropdiag;
                samedir = turnright;
                oppodir = turnleft;
            }
            inboundsin = Mathf.sindeg (inbound);
            inboundcos = Mathf.cosdeg (inbound);
            return null;
        }

        public void init2 ()
        { }

        // Compute parameters common to all entry types
        private void computeCommon ()
        {
            // length of normal inbound/outbound lines
            holdleglen = HOLDLEGSEC * bmpixpersec;

            // far end of inbound radial
            inboundfarbmx = navpt.x + Mathf.sindeg (inbound + 180.0) * holdleglen;
            inboundfarbmy = navpt.y - Mathf.cosdeg (inbound + 180.0) * holdleglen;

            // center of near-end arc circle
            double dx = Double.NaN;
            double dy = Double.NaN;
            if (turndir == 'L') {
                dx = Mathf.sindeg (inbound - 90.0) * stdturnradbmp;
                dy = Mathf.cosdeg (inbound - 90.0) * stdturnradbmp;
            }
            if (turndir == 'R') {
                dx = Mathf.sindeg (inbound + 90.0) * stdturnradbmp;
                dy = Mathf.cosdeg (inbound + 90.0) * stdturnradbmp;
            }
            nearctrbmx = navpt.x + dx;
            nearctrbmy = navpt.y - dy;

            // center of far-end arc circle
            farctrbmx = inboundfarbmx + dx;
            farctrbmy = inboundfarbmy - dy;

            // endpoints of outbound line
            outboundnearbmx = navpt.x + dx * 2.0;
            outboundnearbmy = navpt.y - dy * 2.0;
            outboundfarbmx  = inboundfarbmx + dx * 2.0;
            outboundfarbmy  = inboundfarbmy - dy * 2.0;
        }

        // Compute parallel-entry parameters
        private void computeParallel ()
        {
            // short distance from navwp along inbound radial where parallel diagonal intersects inbound radial
            double nearlen = Math.tan (Math.toRadians (paralleldiag / 2.0)) * stdturnradbmp;

            // where parallel diagonal intersects inbound radial
            paralleldiagnearx = navpt.x + Mathf.sindeg (inbound + 180.0) * nearlen;
            paralleldiagneary = navpt.y - Mathf.cosdeg (inbound + 180.0) * nearlen;

            if (turndir == 'L') {
                paralleldiagfarx = farctrbmx + Mathf.sindeg (inbound - 90.0 + paralleldiag) * stdturnradbmp;
                paralleldiagfary = farctrbmy - Mathf.cosdeg (inbound - 90.0 + paralleldiag) * stdturnradbmp;
                parallelfarstart = inbound;  // long arc, far end of inbound to far end of diagonal
            }
            if (turndir == 'R') {
                paralleldiagfarx = farctrbmx + Mathf.sindeg (inbound + 90.0 - paralleldiag) * stdturnradbmp;
                paralleldiagfary = farctrbmy - Mathf.cosdeg (inbound + 90.0 - paralleldiag) * stdturnradbmp;
                parallelfarstart = inbound - paralleldiag;  // long arc, far end of diagonal to far end of inbound
            }
        }

        // Compute teardrop-entry parameters
        private void computeTeardrop ()
        {
            if (turndir == 'L') {
                teardropdiagfarx = farctrbmx + Mathf.sindeg (inbound - 90.0 + teardropdiag) * stdturnradbmp;
                teardropdiagfary = farctrbmy - Mathf.cosdeg (inbound - 90.0 + teardropdiag) * stdturnradbmp;
                teardropfarstart = inbound;
            }
            if (turndir == 'R') {
                teardropdiagfarx = farctrbmx + Mathf.sindeg (inbound + 90.0 - teardropdiag) * stdturnradbmp;
                teardropdiagfary = farctrbmy - Mathf.cosdeg (inbound + 90.0 - teardropdiag) * stdturnradbmp;
                teardropfarstart = inbound - teardropdiag;
            }
        }

        // Compute direct-entry parameters
        private void computeDirect ()
        {
            // direct entry - tc_acraft_to_navwp is between -70 and +110
            // comments assume range is -90 to +90 but otherwise work ok

            // move the center of the outbound turn circle so the circle is tangential to aircraft course line
            // test case KBED GPS 23 JENDI starting near KBVY
            //  JENDI (iaf) is direct entry coming from outside
            //  WHYBE (map) is direct entry crossing over

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

            double dist_to_push_out = - Math.tan (Math.toRadians (tc_acraft_to_navwp / 2.0)) * stdturnradbmp;

            // compute where the displaced turn circle intersects the inbound course line
            // if tc_acraft_to_navwp =   0, it would be right at navpt.
            // if tc_acraft_to_navwp =  90, it is one radius inward, shrinking the hold
            // if tc_acraft_to_navwp = -90, it is one radius outward, stretching the hold
            directinboundintersectbmx = navpt.x + inboundsin * dist_to_push_out;
            directinboundintersectbmy = navpt.y - inboundcos * dist_to_push_out;

            // compute number of degrees for arc
            // if tc_acraft_to_navwp =   0, ie, coming directly along inbound line, a 180 turn is required
            // if tc_acraft_to_navwp =  90, ie, coming from outside the hold, a 90 deg turn is required
            // if tc_acraft_to_navwp = -90, ie, crossing over the hold, a 270 deg turn is required
            directsweep = 180.0 - tc_acraft_to_navwp;

            // compute center of circle
            // also compute counter-clockwise-most point of the arc (degrees clockwise of due East)
            directctrbmx = Double.NaN;
            directctrbmy = Double.NaN;
            directstart = Double.NaN;

            if (turndir == 'L') {
                // turning left, go left from inboundline to find where center goes
                directctrbmx = directinboundintersectbmx - inboundcos * stdturnradbmp;
                directctrbmy = directinboundintersectbmy - inboundsin * stdturnradbmp;

                // if inbound is due north, start arc due west of center
                directstart = inbound + 180.0;

                // tc from center of arc to where aircraft starts and stops flying arc
                directstartc = inbound + tc_acraft_to_navwp + 90.0;
                directstoptc = inbound - 90.0;
            }

            if (turndir == 'R') {
                // turning right, go right from inboundline to find where center goes
                directctrbmx = directinboundintersectbmx + inboundcos * stdturnradbmp;
                directctrbmy = directinboundintersectbmy + inboundsin * stdturnradbmp;

                // if inbound is due north, end arc due east of center
                // then back up by sweep amount to see where to start
                directstart = inbound - directsweep;

                // tc from center of arc to where aircraft starts and stops flying arc
                directstartc = inbound + tc_acraft_to_navwp - 90.0;
                directstoptc = inbound + 90.0;
            }

            // point where fillet arc intersects the extended outbound course line
            directoutboundintersectbmx = directctrbmx * 2.0 - directinboundintersectbmx;
            directoutboundintersectbmy = directctrbmy * 2.0 - directinboundintersectbmy;

            // compute start of near arc where airplane first intercepts it
            if (turndir == 'L') {
                directbegarcx = directctrbmx + Mathf.sindeg (inbound - tc_acraft_to_navwp + 90.0) * stdturnradbmp;
                directbegarcy = directctrbmy - Mathf.cosdeg (inbound - tc_acraft_to_navwp + 90.0) * stdturnradbmp;
            }
            if (turndir == 'R') {
                directbegarcx = directctrbmx + Mathf.sindeg (inbound + tc_acraft_to_navwp - 90.0) * stdturnradbmp;
                directbegarcy = directctrbmy - Mathf.cosdeg (inbound + tc_acraft_to_navwp - 90.0) * stdturnradbmp;
            }
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
        private boolean flyPT;
        private char td;         // 180deg and last 45deg turn direction
        private double a_tru, b_tru, c_tru;
        private double extendnm;  // extend outbound leg from navwp this far beyond HOLDLEGSEC
        public  double inbound;   // true course when turned inbound headed directly to fix
        private int a_dir;       // outbound from navaid (magnetic)
        private int b_dir;       // turned 45deg from outbound course
        private int c_dir;       // turned 180deg to inbound 45deg leg
        private int d_dir;       // inboud course to navaid (after 45deg turn)
        private PointD extpt = new PointD ();
        private PointD navpt = new PointD ();
        private String turndir;  // 180deg and last 45deg turn direction
        private Waypoint extwp;  // either navwp or FAF waypoint, whichever is farther from airport
        public  Waypoint navwp;  // navigating using this beacon

        // line going outbound from beacon and for one minute past the extended navwp
        private final CIFPStep pistep1 = new CIFPStep () {
            private String textline;

            @Override
            public String getTextLine ()
            {
                if (textline == null) {
                    StringBuilder sb = new StringBuilder ();
                    sb.append ("pt ");
                    sb.append (navwp.ident);
                    sb.append (DIRTOARR);
                    appCourse (sb, a_dir);
                    appendAlt (sb);
                    textline = sb.toString ();
                }
                return textline;
            }

            @Override
            public void drawStepDots ()
            {
                curposhdg = a_tru;
                curposbmx = navpt.x;
                curposbmy = navpt.y;

                double dist = extendnm * bmpixpernm + HOLDLEGSEC * bmpixpersec;
                double endbmx = curposbmx + Mathf.sindeg (curposhdg) * dist;
                double endbmy = curposbmy - Mathf.cosdeg (curposhdg) * dist;
                drawStepCourseToPoint (endbmx, endbmy);

                updateAlt ();
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // maybe it is time to start turning 45deg outbound
                if (!inEndFillet (navDial)) {

                    // if not, fly on outbound heading
                    navDial.setMode (NavDialView.Mode.LOC);
                    navDial.setObs (lineObsSetting (a_tru));
                    navDial.setDeflect (lineDeflection (navpt.x, navpt.y, a_tru));
                }

                // give DME from navwp
                double dist = Math.hypot (acraftbmx - navpt.x, acraftbmy - navpt.y);
                navDial.setDistance (dist / bmpixpernm, navwp.ident, false);
            }
        };

        // outbound line on 45deg angle
        private final CIFPStep pistep2 = new CIFPStep () {
            private String textline;

            @Override
            public String getTextLine ()
            {
                if (textline == null) {
                    StringBuilder sb = new StringBuilder ();
                    sb.append ("pt 45out ");
                    appCourse (sb, b_dir);
                    appendAlt (sb);
                    textline = sb.toString ();
                }
                return textline;
            }

            // number of bitmap pixels to pistep3 turn
            @Override
            public double bmpToNextTurn (NextTurn nextTurn)
            {
                nextTurn.direction = turndir;  // what direction to turn in pistep3
                nextTurn.truecourse = c_tru;   // what heading to turn to in pistep3
                return Math.hypot (acraftbmx - endptbmx, acraftbmy - endptbmy);
            }

            @Override
            public void drawStepDots ()
            {
                // draw the one-minute 45deg outbound leg
                curposhdg = b_tru;
                drawStepOneMinLine ();
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // never on glideslope flying outbound
                navDial.setMode (NavDialView.Mode.LOC);

                // distance from extended waypoint
                double dist = Math.hypot (acraftbmx - extpt.x, acraftbmy - extpt.y);
                navDial.setDistance (dist / bmpixpernm, extwp.ident, false);

                // OBS set to 45deg outbound heading
                navDial.setObs (lineObsSetting (b_tru));
                navDial.setDeflect (lineDeflection (begptbmx, begptbmy, b_tru));
            }
        };

        // 180deg turn
        private final CIFPStep pistep3 = new CIFPStep () {
            private String textline;

            @Override
            public String getTextLine ()
            {
                if (textline == null) {
                    StringBuilder sb = new StringBuilder ();
                    sb.append ("pt 180 ");
                    appendTurn (sb, td);
                    appCourse (sb, c_dir);
                    appendAlt (sb);
                    textline = sb.toString ();
                }
                return textline;
            }

            // no fillet at end of this step cuz this step is the fillet
            @Override
            public void drawFillet ()
            { }

            @Override
            public void drawStepDots ()
            {
                drawStepUTurn (td);
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // never in glideslope mode during 180deg turn
                navDial.setMode (NavDialView.Mode.LOC);

                // distance to extended waypoint
                double dist = Math.hypot (acraftbmx - extpt.x, acraftbmy - extpt.y);
                navDial.setDistance (dist / bmpixpernm, extwp.ident, false);

                // heading aircraft should be on at this point in the turn
                double uturnctrbmx = (begptbmx + endptbmx) / 2.0;
                double uturnctrbmy = (begptbmy + endptbmy) / 2.0;
                navDial.setObs (arcObsSetting (uturnctrbmx, uturnctrbmy, td));
                navDial.setDeflect (arcDeflection (uturnctrbmx, uturnctrbmy, stdturnradbmp, td));
            }
        };

        // line on 45-deg offset inbound
        private final CIFPStep pistep4 = new CIFPStep () {
            private String textline;

            @Override
            public String getTextLine ()
            {
                if (textline == null) {
                    StringBuilder sb = new StringBuilder ();
                    sb.append ("pt 45in ");
                    appCourse (sb, c_dir);
                    appendAlt (sb);
                    textline = sb.toString ();
                }
                return textline;
            }

            @Override
            public void drawStepDots ()
            {
                drawStepCourseToInterceptRadial (curposhdg, navpt, a_tru);
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // maybe have entered fillet at end of 45deg inbound line
                if (!inEndFillet (navDial)) {

                    // not, flying the inbound 45deg line
                    navDial.setMode (NavDialView.Mode.LOC);
                    navDial.setObs (lineObsSetting (c_tru));
                    navDial.setDeflect (lineDeflection (begptbmx, begptbmy, c_tru));
                }

                double dist = Math.hypot (acraftbmx - extpt.x, acraftbmy - extpt.y);
                navDial.setDistance (dist / bmpixpernm, extwp.ident, false);
            }
        };

        public String init1 () throws Exception
        {
            // get magnetic courses for the 4 parts
            int toc = Integer.parseInt (parms.get ("toc"));
            td = parms.get ("td").charAt (0);
            b_dir = toc;  // after 45deg turn outbound
            switch (td) {
                case 'L': {
                    turndir = turnleft;
                    a_dir = (b_dir + 3150) % 3600;
                    break;
                }
                case 'R': {
                    turndir = turnright;
                    a_dir = (b_dir +  450) % 3600;
                    break;
                }
                default: throw new IllegalArgumentException ("bad PI turn dir " + td);
            }
            c_dir = (b_dir + 1800) % 3600;  // after 180deg turn
            d_dir = (a_dir + 1800) % 3600;  // after 45deg turn inbound

            // get beacon PT navigates by
            navwp = findWaypoint (parms.get ("wp"), navpt);

            // get true course values for the outbound leg from beacon
            // and for the first 45deg turn outbound
            a_tru = getTCDeg (a_dir, navwp);  // outbound from beacon
            b_tru = getTCDeg (b_dir, navwp);  // after 45deg turn outbound
            c_tru = getTCDeg (c_dir, navwp);  // after 180deg turn

            // also let anyone who cares what the inbound heading to beacon is
            // this is the TC after the final 45deg turn inbound
            inbound = getTCDeg (d_dir, navwp);

            return null;
        }

        public void init2 ()
        {
            // see which is farther from the airport, the PT's beacon or the FAF
            // that is the point we use for timing
            // this will select VELAN for the LWM VOR 23 approach
            CIFPLeg fafleg = segment.approach.finalseg.getFafLeg ();
            if (fafleg != null) {
                Waypoint fafwp = fafleg.getFafWaypt ();
                double dist_nav_to_apt = Lib.LatLonDist (navwp.lat, navwp.lon, airport.lat, airport.lon);
                double dist_faf_to_apt = Lib.LatLonDist (fafwp.lat, fafwp.lon, airport.lat, airport.lon);
                extendnm = dist_faf_to_apt - dist_nav_to_apt;
                if (extendnm > 0.0) {
                    extwp = fafwp;
                    extpt.x = plateView.LatLon2BitmapX (fafwp.lat, fafwp.lon);
                    extpt.y = plateView.LatLon2BitmapY (fafwp.lat, fafwp.lon);
                    return;
                }
            }
            extendnm = 0.0;
            extwp = navwp;
            extpt = navpt;
        }

        /**
         * Give pilot the option to skip the procedure turn.
         */
        @Override
        public boolean isOptional ()
        {
            // in such a case the PT is required or we would end up with an hairpin turn at VELAN
            // ...going along the path LWM -> VELAN -> LWM -> RW23
            // ...so we need to fly LWM -> VELAN -> PT -> LWM -> RW23
            // even from over PSM, still requires PT, but would be better off using (rv) transition
            if (extendnm > 0.0) {
                flyPT = true;
                return false;
            }

            // maybe we can tell if PT is required or should be skipped
            // GDM-VOR-A with PT on the VOR a couple miles west of airport
            // ...whether prompt or not depends on aircraft current position
            //    if airplane east of VOR (eg, over KBED), must do PT: GDM -> PT -> GDM -> KGDM
            //    if airplane west of VOR (eg, over KORE), skip the PT: GDM -> KGDM
            //    if airplane north or south of VOR (eg, over KEEN), prompt pilot
            // FMH-ILS-23/MVY should force PT regardless of where aircraft is
            // ...path is MVY -> BOMDE -> PT -> BOMDE -> RW23
            int rc = checkOptionalPT (segment, legidx, navpt, inbound);

            ////// don't prompt for hardcore teardrop/parallel/direct entry
            ////// do prompt for borderline cases
            ////if (rc >= 0) {
            ////    flyPT = rc > 0;  // force hold for hardcore teardrop/parallel entry
            ////                     // skip hold for hardcore direct entry
            ////    return false;
            ////}

            // don't prompt for hardcore teardrop/parallel entry
            // do prompt for hardcore direct entry and borderline
            if (rc > 0) {
                flyPT = true;  // force hold for hardcore teardrop/parallel entry
                return false;
            }

            // we can't easily tell if PT is required or not so ask the pilot
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Optional leg");
            adb.setMessage ("Fly Procedure Turn at " + parms.get ("wp") + "?");
            adb.setPositiveButton ("Yes", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    flyPT = true;
                    CIFPSegmentSelFinal (segment);
                }
            });
            adb.setNeutralButton ("No", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    flyPT = false;
                    CIFPSegmentSelFinal (segment);
                }
            });
            adb.setNegativeButton ("Cancel", null);
            adb.show ();
            return true;
        }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            if (flyPT) {
                steps.addLast (pistep1);
                steps.addLast (pistep2);
                steps.addLast (pistep3);
                steps.addLast (pistep4);
            }
        }
    }

    /**
     * Arc around a fix to a fix.
     *   PAKT ILS Z 11 from ANN
     */
    private class CIFPLeg_RF extends CIFPLeg {
        private char turndir;
        private double arcradius, endradtru;
        private PointD ctrpt = new PointD ();
        private PointD endpt = new PointD ();
        private Waypoint endwp;

        private final CIFPStep rfstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                sb.append ("arc ");
                sb.append (turndir);
                String turnrt = Lib.DoubleNTZ (stdturnradbmp / arcradius);
                if (!turnrt.equals ("0")) {
                    sb.append (' ');
                    if (!turnrt.equals ("1")) {
                        sb.append (turnrt);
                        sb.append ('\u00D7');  // multiply 'x' symbol
                    }
                    sb.append ("std");
                }
                sb.append (DIRTOARR);
                sb.append (endwp.ident);
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                // calc what radial from centerpoint we are currently on
                double begradtru = trueCourse (ctrpt.x, ctrpt.y, curposbmx, curposbmy);

                // draw dots along arc
                if (turndir == 'L') {
                    if (begradtru < endradtru) begradtru += 360.0;
                    drawStepDotArc (ctrpt.x, ctrpt.y, arcradius, endradtru - 90.0, begradtru - endradtru, true);
                    curposhdg = endradtru - 90.0;
                }
                if (turndir == 'R') {
                    if (begradtru > endradtru) begradtru -= 360.0;
                    drawStepDotArc (ctrpt.x, ctrpt.y, arcradius, begradtru - 90.0, endradtru - begradtru, true);
                    curposhdg = endradtru + 90.0;
                }

                // return ending alt,hdg,lat,lon
                updateAlt ();
                curposbmx = endpt.x;
                curposbmy = endpt.y;
            }

            // draw fillet from end of incoming line to the beginning of this arc
            // this step is next-to-be-current (ignoring runts).
            @Override
            public void drawFillet ()
            {
                // since we are an arc, no need to draw a lead-in fillet
                // also, this arc probably isn't standard rate, so don't overwrite as a fillet
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // never a glideslope on the arc
                navDial.setMode (NavDialView.Mode.LOC);

                // get distance from aircraft to end point for the DME
                double enddist = Math.hypot (endpt.x - acraftbmx, endpt.y - acraftbmy);
                navDial.setDistance (enddist / bmpixpernm, endwp.ident, false);

                // set obs and deflection depending on where we are along the arc
                navDial.setObs (arcObsSetting (ctrpt.x, ctrpt.y, turndir));
                navDial.setDeflect (arcDeflection (ctrpt.x, ctrpt.y, arcradius, turndir));
            }
        };

        public String init1 () throws Exception
        {
            findWaypoint (parms.get ("cp"), ctrpt);
            endwp   = findWaypoint (parms.get ("ep"), endpt);
            turndir = parms.get ("td").charAt (0);
            if ((turndir != 'L') && (turndir != 'R')) {
                throw new IllegalArgumentException ("bad turn direction " + turndir);
            }

            // calc what radial from centerpoint we will end up on
            // and the corresponding distance
            endradtru = trueCourse (ctrpt.x, ctrpt.y, endpt.x, endpt.y);
            arcradius = Math.hypot (ctrpt.x - endpt.x, ctrpt.y - endpt.y);

            return null;
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (rfstep);
        }
    }

    /**
     * WairToNow specific - Fly radar vectors to intercept next leg.
     * This should only be in the (rv) segment and should be the only leg therein.
     * So we can assume the next leg is at beginning of final approach segment
     * which is always a CF.
     */
    private class CIFPLeg_rv extends CIFPLeg {
        private boolean virtnavdialmode = false;
        private double curtc = Double.NaN;
        private double virtnavdiallastobs;

        // draw the radar vector line
        // goes from where radar controller gave vector
        // heading is what radar controller gave for heading
        // (so we use what airplane is flying or what virtnav dial says)
        // and goes to point intersecting final approach course
        // - assumes the next leg is the FAF leg
        //   the CIFP data often has some other point outside the FAF
        //   listed in the final approach segment so it has to be left out
        //   see CIFPSegment::getSteps()
        private final CIFPStep rvstep = new CIFPStep () {
            private boolean behind;

            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                sb.append ("radar vector ");
                if (!Double.isNaN (curtc)) appTrueAsMag (sb, curtc);
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                if (!virtnavdialmode && (bmpToNextTurn (nextTurn) > 0.0)) {

                    // assume we should be flying starting at where we actually are
                    begptbmx = curposbmx = acraftbmx;
                    begptbmy = curposbmy = acraftbmy;

                    // assume we should be flying what we are actually flying
                    // calculate new intersection point
                    calcIntersection (acrafthdg);
                }

                // draw from curposbmx,y to intersection point
                if (behind) {

                    // final approach course is behind aircraft, don't draw any line
                    // don't go on to next step cuz we are being vectored away from final approach course
                    smallestAcraftDist = 0.0;

                    // pretend like we drew a line to the FAF so next step (the FAF) doesn't draw anything
                    // pretend like we drew a line ending on FAC heading so we don't get a stray fillet
                    curposhdg = vnFinalTC;
                    curposbmx = vnFafPoint.x;
                    curposbmy = vnFafPoint.y;
                } else {

                    // draw dotted line along vectoring path ahead to final approach course intersection point
                    // when we are closer to final approach course than to this vectoring line,
                    // we will switch over to the final approach course
                    drawStepCourseToPoint (endptbmx, endptbmy);
                }

                updateAlt ();
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // maybe we are in fillet at end of the line segment
                if (!inEndFillet (navDial)) {

                    // not, if first time here, set initial obs = aircraft heading
                    if (!virtnavdialmode) {
                        navDial.setObs (acrafthdg + aptmagvar);
                        virtnavdialmode = true;
                        virtnavdiallastobs = navDial.getObs () + 180.0;
                    }

                    // use VOR mode to allow finger turning the dial to what the vector is
                    navDial.setMode (NavDialView.Mode.VOR);

                    // if nav dial turned, assume it's a new vector and reset course line
                    if (angleDiffU (virtnavdiallastobs - navDial.getObs ()) >= 0.125) {
                        virtnavdiallastobs = navDial.getObs ();

                        // new starting point to leg
                        begptbmx = acraftbmx;
                        begptbmy = acraftbmy;

                        // get true course corresponding to the new obs setting
                        double icepthdg = virtnavdiallastobs - aptmagvar;

                        // calculate new intersection point
                        calcIntersection (icepthdg);
                    }

                    // set VOR needle deflection based on aircraft current position
                    double deflection = lineDeflection (begptbmx, begptbmy, endptbmx, endptbmy);
                    navDial.setDeflect (deflection * (NavDialView.VORDEFLECT / NavDialView.LOCDEFLECT));
                }

                // set DME distance and identifier
                double dist = Math.hypot (acraftbmx - endptbmx, acraftbmy - endptbmy);
                navDial.setDistance (behind ? -1.0 : (dist / bmpixpernm), "radar", false);
            }

            // calculate where current radar vector intersects final approach course
            // input:
            //  begptbmx,y = starting point of radar vector (ie, where airplane was when vector given)
            //  iceptc = radar vector true course
            // output:
            //  endptbmx,y = ending point (somewhere on final approach course line)
            private void calcIntersection (double iceptc)
            {
                curtc = iceptc;

                // compute some point way ahead of us
                double aheadbmx = begptbmx + Mathf.sindeg (iceptc) * 4096.0;
                double aheadbmy = begptbmy - Mathf.cosdeg (iceptc) * 4096.0;

                // find where the two lines intersect giving the end of this segment
                endptbmx = Lib.lineIntersectX (begptbmx, begptbmy, aheadbmx, aheadbmy,
                        vnFafPoint.x, vnFafPoint.y, vnRwyPoint.x, vnRwyPoint.y);
                endptbmy = Lib.lineIntersectY (begptbmx, begptbmy, aheadbmx, aheadbmy,
                        vnFafPoint.x, vnFafPoint.y, vnRwyPoint.x, vnRwyPoint.y);

                // if intersection is behind aircraft, just use the point way ahead of us
                double dotprod = (aheadbmx - begptbmx) * (endptbmx - begptbmx) +
                        (aheadbmy - begptbmy) * (endptbmy - begptbmy);
                behind = (dotprod < 0.0);
            }
        };

        public String init1 ()
        {
            return null;
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (rvstep);
        }
    }

    /**
     * WairToNow specific - Turn (standard rate) direct to a waypoint.
     * Useful when the waypoint is directly behind the aircraft at start of missed approach.
     *    td=L or R direction to turn
     *    wp=waypoint to turn to (probably next leg is a CF to the waypoint)
     *  optional:
     *    a=altitude
     */
    private class CIFPLeg_td extends CIFPLeg {
        private char turnchr;
        private String turnstr;
        private double centerbmx, centerbmy;
        private double tc_from_arc_to_nav;
        private Waypoint navwp;
        private PointD navpt = new PointD ();

        // standard rate turn until fix dead ahead
        private final CIFPStep tdstep = new CIFPStep () {
            @Override
            public String getTextLine ()
            {
                StringBuilder sb = new StringBuilder ();
                sb.append (turnstr);
                appTrueAsMag (sb, tc_from_arc_to_nav);
                appendAlt (sb);
                return sb.toString ();
            }

            @Override
            public void drawStepDots ()
            {
                // calculate center of standard rate turn in the desired direction
                double tctoctr = Double.NaN;
                switch (turnchr) {
                    case 'L': {
                        tctoctr = curposhdg - 90.0;
                        break;
                    }
                    case 'R': {
                        tctoctr = curposhdg + 90.0;
                        break;
                    }
                }
                centerbmx = curposbmx + stdturnradbmp * Mathf.sindeg (tctoctr);
                centerbmy = curposbmy - stdturnradbmp * Mathf.cosdeg (tctoctr);

                // calculate ccw-most end of the turn, degrees cw from due east (start)
                // and calculate number of degrees of turning (sweep)
                // - distance from center of arc to waypoint we are headed to
                double dist_from_ctr_to_nav = Math.hypot (navpt.x - centerbmx, navpt.y - centerbmy);
                // - true course from center of arc to waypoint we are headed to
                double tc_from_ctr_to_nav = trueCourse (centerbmx, centerbmy, navpt.x, navpt.y);
                // - little bit of extra turning needed from perimeter of arc to get to waypoint
                double extra = Math.toDegrees (Math.asin (stdturnradbmp / dist_from_ctr_to_nav));

                switch (turnchr) {
                    case 'L': {
                        tc_from_arc_to_nav = tc_from_ctr_to_nav - extra;
                        while (tc_from_arc_to_nav > curposhdg) tc_from_arc_to_nav -= 360.0;
                        double start = tc_from_arc_to_nav + 180.0;
                        double sweep = curposhdg - tc_from_arc_to_nav;
                        drawStepDotArc (centerbmx, centerbmy, stdturnradbmp, start, sweep, true);

                        // also calculate where the arc will end
                        curposhdg = tc_from_arc_to_nav;
                        curposbmx = centerbmx + stdturnradbmp * Mathf.cosdeg (tc_from_arc_to_nav);
                        curposbmy = centerbmy + stdturnradbmp * Mathf.sindeg (tc_from_arc_to_nav);
                        break;
                    }
                    case 'R': {
                        tc_from_arc_to_nav = tc_from_ctr_to_nav + extra;
                        while (tc_from_arc_to_nav < curposhdg) tc_from_arc_to_nav += 360.0;
                        double start = curposhdg + 180.0;
                        double sweep = tc_from_arc_to_nav - curposhdg;
                        drawStepDotArc (centerbmx, centerbmy, stdturnradbmp, start, sweep, true);

                        // also calculate where the arc will end
                        curposhdg = tc_from_arc_to_nav;
                        curposbmx = centerbmx - stdturnradbmp * Mathf.cosdeg (tc_from_arc_to_nav);
                        curposbmy = centerbmy - stdturnradbmp * Mathf.sindeg (tc_from_arc_to_nav);
                        break;
                    }
                }

                updateAlt ();
            }

            // draw fillet from end of incoming line to the beginning of this arc
            // this step is next-to-be-current (ignoring runts).
            @Override
            public void drawFillet ()
            {
                // this step is essentially the fillet between the previous step
                // and the next step, so no need to draw a lead-in fillet
            }

            // update nav dial with current state
            @Override
            public void getVNTracking (NavDialView navDial)
            {
                // never in glideslope mode during turn
                navDial.setMode (NavDialView.Mode.LOC);

                // distance to waypoint
                double dist = Math.hypot (acraftbmx - navpt.x, acraftbmy - navpt.y);
                navDial.setDistance (dist / bmpixpernm, navwp.ident, false);

                // heading aircraft should be on at this point in the turn
                navDial.setObs (arcObsSetting (centerbmx, centerbmy, turnchr));
                navDial.setDeflect (arcDeflection (centerbmx, centerbmy, stdturnradbmp, turnchr));
            }
        };

        public String init1 () throws Exception
        {
            String wpid = parms.get ("wp");
            navwp   = findWaypoint (wpid, navpt);
            turnchr = parms.get ("td").charAt (0);
            switch (turnchr) {
                case 'L': {
                    turnstr = turnleft;
                    break;
                }
                case 'R': {
                    turnstr = turnright;
                    break;
                }
                default: throw new Exception ("bad turndir " + turnchr);
            }
            return null;
        }

        public void init2 ()
        { }

        public void getSteps (LinkedList<CIFPStep> steps)
        {
            steps.addLast (tdstep);
        }
    }

    /**
     * The HF (hold in place of PT) and PI (procedure turn) legs can be optional.
     * We force it to be skipped if executing it would require an hairpin turn entering.
     * We force it to be executed if skipping it would require an hairpin turn at the fix.
     * Otherwise we prompt the pilot to see if they want to do it.
     *
     * @param segment = which transition segment the HF/PI leg is part of
     * @param legidx = which leg of the segment the HF/PI is
     * @param navpt = bitmap X,Y of waypoint the HF/PI is based on
     * @param inbound = true course inbound toward navpt (usually same as runway true course)
     * @return <0: borderline, prompt pilot for action to take
     *         >0: hardcore teardrop/parallel, HF/PI is required as to do otherwise would require an hairpin turn
     *         =0: hardcore direct entry, HF/PI is skipped as to do otherwise would require an hairpin turn
     */
    private int checkOptionalPT (CIFPSegment segment, int legidx, PointD navpt, double inbound)
    {
        // if segment has a beacon before the PT coming from same direction as the runway
        // what would give the pilot an hairpin turn to make the turn to final,
        // the PT is required
        try  {
            PointD befpt = new PointD ();
            for (int i = legidx; i >= 0;) {

                // get preceding point in segment
                // if run out of points, use aircraft current position
                if (-- i >= 0) {
                    // get preceding point
                    // if something other than a CF, prompt pilot
                    CIFPLeg beforeleg = segment.legs[i];
                    if (!(beforeleg instanceof CIFPLeg_CF)) break;
                    // CF, get the waypoint
                    String befwp = ((CIFPLeg_CF) beforeleg).parms.get ("wp");
                    findWaypoint (befwp, befpt);
                } else {
                    // no more preceding CFs, use aircraft current position
                    befpt.x = acraftbmx;
                    befpt.y = acraftbmy;
                }

                // if runt distance away, ignore it
                // we are almost always preceded by CF at same waypoint
                // also ignore airplane if it is right on top of fix (ie, prompt pilot)
                double nm_from_me_to_bef = Math.hypot (befpt.x - navpt.x, befpt.y - navpt.y) / bmpixpernm;
                if (nm_from_me_to_bef < RUNTNM) continue;

                // get true course from me to before point and to airport
                // if they are both the same direction, the PT is required as it would be an hairpin turn
                double tc_from_me_to_bef = trueCourse (navpt.x, navpt.y, befpt.x, befpt.y);
                double tc_diff = angleDiffU (tc_from_me_to_bef - inbound);
                if (tc_diff < 60.0) return 1;

                // if they are in opposite directions, the PT is not required as it would be an hairpin turn
                if (tc_diff > 150.0) return 0;

                // prompt pilot
                break;
            }
        } catch (Exception e) {
            // if findWaypoint() throws up, assume have to prompt pilot
            Lib.Ignored ();
        }
        return -1;
    }

    /**
     * Look for waypoint in database.
     * Throw exception if not found.
     */
    private Waypoint findWaypoint (String wayptid, PointD bmxy) throws Exception
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
    private void drawStepCourseToPoint (double endbmx, double endbmy)
    {
        drawStepDotLine (curposbmx, curposbmy, endbmx, endbmy, true);
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
    private void drawStepCourseToInterceptDist (PointD navpt, double radius)
    {
        // find possibly two points along course line
        double farendbmx = curposbmx + Mathf.sindeg (curposhdg) * 1024.0;
        double farendbmy = curposbmy - Mathf.cosdeg (curposhdg) * 1024.0;
        if (!Lib.lineIntersectCircle (curposbmx, curposbmy, farendbmx, farendbmy,
                navpt.x, navpt.y, radius, dsctidpp, dsctidpm)) {
            throw new IllegalArgumentException ();
        }

        // pick the one that is closest ahead
        double start_to_pp = trueCourse (curposbmx, curposbmy, dsctidpp.x, dsctidpp.y);
        double start_to_pm = trueCourse (curposbmx, curposbmy, dsctidpm.x, dsctidpm.y);
        double aheadx = Double.NaN;
        double aheady = Double.NaN;
        double bestdist = Double.MAX_VALUE;
        if ((angleDiffU (start_to_pp - curposhdg) < 90.0) && (bestdist > start_to_pp)) {
            bestdist = start_to_pp;
            aheadx   = dsctidpp.x;
            aheady   = dsctidpp.y;
        }
        if ((angleDiffU (start_to_pm - curposhdg) < 90.0) && (bestdist > start_to_pm)) {
            bestdist = start_to_pm;
            aheadx   = dsctidpm.x;
            aheady   = dsctidpm.y;
        }
        if (bestdist == Double.MAX_VALUE) throw new IllegalArgumentException ();

        // draw step dots to the closest one ahead
        drawStepDotLine (curposbmx, curposbmy, aheadx, aheady, true);
        curposbmx = aheadx;
        curposbmy = aheady;
    }

    /**
     * Draw step dots from current position and heading until intercepts
     * the given radial from the navaid waypoint.
     *  Input:
     *   icepthdg = flying this heading to intercept the radial
     *   navpt = navaid the radial is from
     *   radial = radial from that navaid to intercept (true degrees)
     *   curposbmx,y = current position
     *   curposhdg = current heading
     *  Output:
     *   dots drawn
     *   curposbmx,y = updated to intersection point
     */
    private void drawStepCourseToInterceptRadial (double icepthdg, PointD navpt, double radial)
    {
        // compute some point way ahead of us
        double aheadbmx = curposbmx + Mathf.sindeg (icepthdg) * 1024.0;
        double aheadbmy = curposbmy - Mathf.cosdeg (icepthdg) * 1024.0;

        // compute some point way out on radial from navaid
        double nextbmx  = navpt.x + Mathf.sindeg (radial) * 1024.0;
        double nextbmy  = navpt.y - Mathf.cosdeg (radial) * 1024.0;

        // find where the two lines intersect
        double iceptx = Lib.lineIntersectX (curposbmx, curposbmy, aheadbmx, aheadbmy,
                navpt.x, navpt.y, nextbmx, nextbmy);
        double icepty = Lib.lineIntersectY (curposbmx, curposbmy, aheadbmx, aheadbmy,
                navpt.x, navpt.y, nextbmx, nextbmy);

        // if the intercept is behind aircraft after it turns on intercept course,
        // re-calculate intercept by flying straight ahead instead of turning on intercept.
        // eg, KDAW VOR-A intercepting course to CON on missed approach
        //   ;CA,mc=3564,a=+722           curposhdg=356.4-aptmagvar
        //   ;CI,mc=2700                  icepthdg=270.0-aptmagvar << trying to draw westbound line
        //   ;CF,wp=CON,mc=2750,a=+3500   radial=275.0-aptmagvar
        // iceptx,y ends up east of curposbmx,y instead of west cuz the CA line doesn't
        // go far enough, so just stay on CA's heading to intercept the CF line
        if (icepthdg != curposhdg) {
            double hdg_cur_to_icept = trueCourse (curposbmx, curposbmy, iceptx, icepty);
            if (angleDiffU (hdg_cur_to_icept - icepthdg) >= 90.0) {
                drawStepCourseToInterceptRadial (curposhdg, navpt, radial);
                return;
            }
        }

        // draw dots to that intersection and set new end point
        drawStepCourseToPoint (iceptx, icepty);
    }

    /**
     * Draw a U-turn at standard rate starting at current position and heading.
     * @return proximity of aircraft to the arc
     */
    @SuppressWarnings("UnusedReturnValue")
    private double drawStepUTurn (char td)
    {
        double hdgtoctr = Double.NaN;
        if (td == 'L') hdgtoctr = curposhdg - 90.0;
        if (td == 'R') hdgtoctr = curposhdg + 90.0;

        double ctrbmx = curposbmx + Mathf.sindeg (hdgtoctr) * stdturnradbmp;
        double ctrbmy = curposbmy - Mathf.cosdeg (hdgtoctr) * stdturnradbmp;
        double dist = drawStepDotArc (ctrbmx, ctrbmy, stdturnradbmp, curposhdg + 180.0, 180.0, true);

        curposbmx  = ctrbmx * 2.0 - curposbmx;
        curposbmy  = ctrbmy * 2.0 - curposbmy;
        curposhdg += 180.0;
        if (curposhdg >= 360.0) curposhdg -= 360.0;

        return dist;
    }

    /**
     * Draw a straight-ahead path for one minute starting at current position.
     * @return proximity of aircraft to the line
     */
    @SuppressWarnings("UnusedReturnValue")
    private double drawStepOneMinLine ()
    {
        double dist   = HOLDLEGSEC * bmpixpersec;
        double endbmx = curposbmx + Mathf.sindeg (curposhdg) * dist;
        double endbmy = curposbmy - Mathf.cosdeg (curposhdg) * dist;
        dist = drawStepDotLine (curposbmx, curposbmy, endbmx, endbmy, true);
        curposbmx    = endbmx;
        curposbmy    = endbmy;
        return dist;
    }

    /**
     * Draw step dots in a line segment shape.
     * @param sx = start bitmap x
     * @param sy = start bitmap y
     * @param ex = end bitmap x
     * @param ey = end bitmap y
     * @param sens = sense aircraft proximity to line segment, update smallestAcraftDist
     */
    private double drawStepDotLine (double sx, double sy, double ex, double ey, boolean sens)
    {
        if (Double.isNaN (sx) || Double.isNaN (sy) || Double.isNaN (ex) || Double.isNaN (ey)) {
            throw new IllegalArgumentException ("isNaN");
        }

        // draw dots
        int i = drawStepDotIndex (4);
        cifpDotBitmapXYs[i++] = ey;
        cifpDotBitmapXYs[i++] = ex;
        cifpDotBitmapXYs[i++] = sy;
        cifpDotBitmapXYs[i]   = sx;

        // see how close aircraft is to the line and save if closest in step so far
        double dist = Lib.distToLineSeg (acraftbmx, acraftbmy, sx, sy, ex, ey);
        if (sens && (smallestAcraftDist > dist)) smallestAcraftDist = dist;
        return dist;
    }

    /**
     * Draw step dots in an arc shape.
     * @param cbmx = arc center (bitmap X)
     * @param cbmy = arc center (bitmap Y)
     * @param radius = arc radius (bitmap pixels)
     * @param start = start radial (degrees clockwise from due EAST)
     * @param sweep = length of arc (degrees clockwise from start)
     * @param sens = sense aircraft proximity to arc, update smallestAcraftDist
     */
    private double drawStepDotArc (double cbmx, double cbmy, double radius,
                                  double start, double sweep, boolean sens)
    {
        if (Double.isNaN (cbmx) || Double.isNaN (cbmy) || Double.isNaN (radius) ||
                Double.isNaN (start) || Double.isNaN (sweep)) {
            throw new IllegalArgumentException ("isNaN");
        }

        // draw dots
        int i = drawStepDotIndex (6);
        cifpDotBitmapXYs[i++] = sweep;
        cifpDotBitmapXYs[i++] = start;
        cifpDotBitmapXYs[i++] = cbmy;
        cifpDotBitmapXYs[i++] = cbmx;
        cifpDotBitmapXYs[i++] = radius;
        cifpDotBitmapXYs[i]   = Double.NaN;

        // see if aircraft is close to the fillet arc
        // this keeps the arc's step current while flying it
        double dist = Lib.distOfPtFromArc (cbmx, cbmy, radius, start, sweep, acraftbmx, acraftbmy);
        if (sens && (smallestAcraftDist > dist)) smallestAcraftDist = dist;
        return dist;
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
            double[] xa = new double[length+expand];
            System.arraycopy (cifpDotBitmapXYs, 0, xa, 0, i);
            cifpDotBitmapXYs = xa;
        }

        // return index where the stuff goes
        cifpDotIndex = i + n;
        return i;
    }

    /**
     * Compute true course (degrees) from begin point to end point
     *   endbmx = begbmx + radius * sin heading  =>  sin heading = (endbmx - begbmx) / radius
     *   endbmy = begbmy - radius * cos heading  =>  cos heading = (begbmy - endbmy) / radius
     *   tan heading = sin heading / cos heading = (endbmx - begbmx) / (begbmy - endbmy)
     */
    private static double trueCourse (double begbmx, double begbmy, double endbmx, double endbmy)
    {
        return Math.toDegrees (Math.atan2 (endbmx - begbmx, begbmy - endbmy));
    }

    /**
     * Find unsigned difference of two angles.
     * @param diff = raw difference
     * @return difference in range 0..180
     */
    private static double angleDiffU (double diff)
    {
        diff = Math.abs (diff);
        while (diff >= 360.0) diff -= 360.0;
        if (diff > 180.0) diff = 360.0 - diff;
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
