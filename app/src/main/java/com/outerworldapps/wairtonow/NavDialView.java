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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class NavDialView extends View {
    public final static double NODISTANCE = 999999.0;
    public final static double NOHEADING  = 999999.0;

    private final static int DMETEXTSIZE = 100;
    private final static int GSFPMTEXTSIZE = 80;

    public enum Mode {
        OFF, VOR, ADF, LOC, LOCBC, ILS
    }

    public interface DMEClickedListener {
        void dmeClicked ();
    }

    public interface OBSChangedListener {
        void obsChanged (double obs);
    }

    private final static double DIALRATIO  =  5;  // number of times drag finger to drag obs once
    public  final static double VORDEFLECT = 10;  // degrees each side for VOR mode deflection
    public  final static double LOCDEFLECT =  3;  // degrees each side for ILS/LOC mode deflection
    public  final static double GSDEFLECT  =  1;  // degrees each side for GS deflection

    private boolean dmeSlant;
    public  boolean hsiEnable;
    public  DMEClickedListener dmeClickedListener;
    public  double deflect;
    public  double degdiffnp;
    public  double distance;
    private double heading;
    private float  lastHeight, lastRotate, lastScale, lastWidth;
    private double obsSetting;
    public  double slope;
    private double touchDownOBS;
    private double touchDownX;
    private double touchDownY;
    public  int  gsfpm;
    private long touchDownTime;
    private Mode mode;
    public  OBSChangedListener obsChangedListener;
    private Paint adfNeedlePaint;
    private Paint dialBackPaint;
    private Paint dialFatPaint;
    private Paint dialMidPaint;
    private Paint dialTextPaint;
    private Paint dialThinPaint;
    private Paint dirArrowPaint;
    private Paint dmeDigitPaint;
    private Paint dmeIdentPaint;
    private Paint gsfpmTextPaint;
    private Paint innerRingPaint;
    private Paint obsArrowPaint;
    private Paint outerRingPaint;
    private Paint vorNeedlePaint;
    private Path adfNeedlePath;
    private Path frArrowPath;
    private Path obsArrowPath;
    private Path toArrowPath;
    private String dmeIdent;

    public NavDialView (Context ctx, AttributeSet attrs)
    {
        super (ctx, attrs);
        constructor ();
    }

    public NavDialView (Context ctx)
    {
        super (ctx);
        constructor ();
    }

    private void constructor ()
    {
        adfNeedlePaint = new Paint ();
        adfNeedlePaint.setColor (Color.GREEN);
        adfNeedlePaint.setStyle (Paint.Style.FILL);

        dialBackPaint = new Paint ();
        dialBackPaint.setColor (Color.DKGRAY);
        dialBackPaint.setStrokeWidth (175);
        dialBackPaint.setStyle (Paint.Style.STROKE);

        dialFatPaint = new Paint ();
        dialFatPaint.setColor (Color.WHITE);
        dialFatPaint.setStrokeWidth (25);

        dialMidPaint = new Paint ();
        dialMidPaint.setColor (Color.WHITE);
        dialMidPaint.setStrokeWidth (15);

        dialTextPaint = new Paint ();
        dialTextPaint.setColor (Color.WHITE);
        dialTextPaint.setStrokeWidth (10);
        dialTextPaint.setTextAlign (Paint.Align.CENTER);
        dialTextPaint.setTextSize (140);

        dialThinPaint = new Paint ();
        dialThinPaint.setColor (Color.WHITE);
        dialThinPaint.setStrokeWidth (10);

        dirArrowPaint = new Paint ();
        dirArrowPaint.setColor (Color.GREEN);
        dirArrowPaint.setStyle (Paint.Style.FILL_AND_STROKE);

        dmeDigitPaint = new Paint ();
        dmeDigitPaint.setColor (Color.rgb (255, 170, 0));
        dmeDigitPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        dmeDigitPaint.setStrokeWidth (12);

        dmeIdentPaint = new Paint ();
        dmeIdentPaint.setColor (Color.rgb (255, 170, 0));
        dmeIdentPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        dmeIdentPaint.setStrokeWidth (3);
        dmeIdentPaint.setTextSize (DMETEXTSIZE);

        gsfpmTextPaint = new Paint ();
        gsfpmTextPaint.setColor (Color.rgb (255, 170, 0));
        gsfpmTextPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        gsfpmTextPaint.setStrokeWidth (3);
        gsfpmTextPaint.setTextAlign (Paint.Align.RIGHT);
        gsfpmTextPaint.setTextSize (GSFPMTEXTSIZE);

        innerRingPaint = new Paint ();
        innerRingPaint.setColor (Color.WHITE);
        innerRingPaint.setStrokeWidth (10);
        innerRingPaint.setStyle (Paint.Style.STROKE);

        obsArrowPaint = new Paint ();
        obsArrowPaint.setColor (Color.YELLOW);
        obsArrowPaint.setStyle (Paint.Style.FILL_AND_STROKE);

        outerRingPaint = new Paint ();
        outerRingPaint.setColor (Color.GRAY);
        outerRingPaint.setStrokeWidth (50);
        outerRingPaint.setStyle (Paint.Style.STROKE);

        vorNeedlePaint = new Paint ();
        vorNeedlePaint.setColor (Color.WHITE);
        vorNeedlePaint.setStrokeWidth (25);

        adfNeedlePath = new Path ();
        adfNeedlePath.moveTo (  0, -604);
        adfNeedlePath.lineTo (-55, -473);
        adfNeedlePath.lineTo (-20, -473);
        adfNeedlePath.lineTo (-20,  604);
        adfNeedlePath.lineTo ( 20,  604);
        adfNeedlePath.lineTo ( 20, -473);
        adfNeedlePath.lineTo ( 55, -473);
        adfNeedlePath.lineTo (  0, -604);

        frArrowPath = new Path ();
        frArrowPath.moveTo (327,  92);
        frArrowPath.lineTo (400, 188);
        frArrowPath.lineTo (473,  92);

        obsArrowPath = new Path ();
        obsArrowPath.moveTo (-71, -518);
        obsArrowPath.lineTo (  0, -624);
        obsArrowPath.lineTo ( 71, -518);

        toArrowPath = new Path ();
        toArrowPath.moveTo (327,  -92);
        toArrowPath.lineTo (400, -188);
        toArrowPath.lineTo (473,  -92);

        distance = NODISTANCE;
        heading = NOHEADING;
        mode = Mode.OFF;
    }

    /**
     * Set operating mode.
     */
    public void setMode (Mode m)
    {
        mode = m;
        invalidate ();
    }

    public Mode getMode ()
    {
        return mode;
    }

    /**
     * See if the current mode supports HSI.
     */
    public boolean isHSIAble ()
    {
        return (mode == Mode.VOR) || (mode == Mode.ADF) || (mode == Mode.LOC) || (mode == Mode.LOCBC) || (mode == Mode.ILS);
    }

    /**
     * Set needle deflection.
     * @param d = VOR: deflection degrees
     *            ADF: relative bearing degrees
     *            LOC: deflection degrees
     *    < 0: deflect needle to left of center
     *    > 0: deflect needle to right of center
     */
    public void setDeflect (double d)
    {
        deflect = d;
        invalidate ();
    }

    /**
     * Set glideslope deviation if in Mode.ILS.
     * @param s = degrees
     */
    public void setSlope (double s)
    {
        slope = s;
        invalidate ();
    }

    /**
     * Set which heading we are actually going, relative to straight up on dial.
     * Tells where to put the airplane icon.
     */
    public void setHeading (double h)
    {
        heading = h;
        invalidate ();
    }

    /**
     * Set distance (DME) value.
     */
    public void setDistance (double d, String i, boolean s)
    {
        if (d < 0.0) throw new IllegalArgumentException ("d lt 0: " + d);
        distance = d;
        dmeIdent = i;
        dmeSlant = s;
        invalidate ();
    }

    /**
     * Set OBS setting.
     * @param obs = degrees
     */
    public void setObs (double obs)
    {
        while (obs < -180.0) obs += 360.0;
        while (obs >= 180.0) obs -= 360.0;
        obsSetting = obs;
        invalidate ();
    }

    /**
     * Get OBS setting.
     * @return value in range -180..179.999999
     */
    public double getObs ()
    {
        return obsSetting;
    }

    /**
     * Touch for turning the dial.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (@NonNull MotionEvent event)
    {
        switch (event.getActionMasked ()) {
            case MotionEvent.ACTION_DOWN: {
                touchDownX = event.getX ();
                touchDownY = event.getY ();
                touchDownOBS = obsSetting;
                touchDownTime = event.getEventTime ();
                break;
            }

            case MotionEvent.ACTION_UP: {
                if (mode != Mode.OFF) {
                    double x = event.getX ();
                    double y = event.getY ();
                    long  t = event.getEventTime ();
                    if ((t - touchDownTime < 500) && (dmeClickedListener != null) &&
                            withinDMEArea (touchDownX, touchDownY) && withinDMEArea (x, y)) {
                        dmeClickedListener.dmeClicked ();
                    }
                }
            }

            case MotionEvent.ACTION_MOVE: {
                if ((mode == Mode.VOR) || (mode == Mode.ADF)) {
                    double moveX    = event.getX ();
                    double moveY    = event.getY ();
                    double centerX  = getWidth ()  / 2.0;
                    double centerY  = getHeight () / 2.0;
                    double startHdg = Math.atan2 (touchDownX - centerX, touchDownY - centerY);
                    double moveHdg  = Math.atan2 (moveX - centerX, moveY - centerY);

                    // compute how many degrees finger moved since ACTION_DOWN
                    double degsMoved = Math.toDegrees (moveHdg - startHdg);
                    while (degsMoved < -180) degsMoved += 360;
                    while (degsMoved >= 180) degsMoved -= 360;

                    // compute how many degrees to turn dial based on that
                    degsMoved /= DIALRATIO;

                    // backward when HSI enabled and active
                    if (hsiEnable && (heading != NOHEADING)) {
                        degsMoved = - degsMoved;
                    }

                    // update the dial to the new heading
                    setObs (touchDownOBS + degsMoved);

                    // if turned a long way, pretend we just did a new finger down
                    // ...this lets us go round and round
                    if (Math.abs (degsMoved) > 90 / DIALRATIO) {
                        touchDownX = moveX;
                        touchDownY = moveY;
                        touchDownOBS = obsSetting;
                    }

                    // tell listener of new heading
                    if (obsChangedListener != null) {
                        obsChangedListener.obsChanged (obsSetting);
                    }
                }
                break;
            }
        }
        return true;
    }

    /**
     * Draw the nav widget.
     */
    @Override
    public void onDraw (Canvas canvas)
    {
        lastWidth  = getWidth ();
        lastHeight = getHeight ();
        lastScale  = Math.min (lastWidth, lastHeight) / (1000 * 2 + outerRingPaint.getStrokeWidth ());

        canvas.save ();

        // set up translation/scaling so that outer ring is radius 1000 centered at 0,0
        canvas.translate (lastWidth / 2, lastHeight / 2);
        canvas.scale (lastScale, lastScale);

        // ADF draws DME unrotated no matter what
        if (mode == Mode.ADF) drawDMEInfo (canvas, dmeDigitPaint, dmeIdentPaint);

        // maybe rotate whole mess for HSI mode
        canvas.save ();
        lastRotate = 0.0F;
        if (hsiEnable && (heading != NOHEADING) && isHSIAble ()) {
            lastRotate = (float) - heading;
            canvas.rotate (lastRotate);
        }

        // draw DME information if enabled
        if (mode != Mode.ADF) drawDMEInfo (canvas, dmeDigitPaint, dmeIdentPaint);

        // draw outer ring
        canvas.drawCircle (0, 0, 1000, outerRingPaint);

        // draw OBS arrow triangle
        canvas.drawPath (obsArrowPath, obsArrowPaint);

        // VOR/LOC-style deflection dots and needle
        if ((mode == Mode.VOR) || (mode == Mode.LOC) || (mode == Mode.LOCBC) || (mode == Mode.ILS)) {

            // draw inner ring
            canvas.drawCircle (0, 0, 412.0F / 5, innerRingPaint);

            // draw dots
            for (int ir = 1; ++ ir <= 5;) {
                float r = ir * 412.0F / 5;
                canvas.drawCircle ( r, 0, 412.0F / 20, innerRingPaint);
                canvas.drawCircle (-r, 0, 412.0F / 20, innerRingPaint);
                canvas.drawCircle (0,  r, 412.0F / 20, innerRingPaint);
                canvas.drawCircle (0, -r, 412.0F / 20, innerRingPaint);
            }

            // draw VOR/localizer needle
            double degdiff = deflect;
            while (degdiff >= 180) degdiff -= 360;
            while (degdiff < -180) degdiff += 360;
            if ((degdiff > -90) && (degdiff <= 90)) {
                if (mode == Mode.VOR) canvas.drawPath (frArrowPath, dirArrowPaint);
            } else {
                if (mode == Mode.VOR) canvas.drawPath (toArrowPath, dirArrowPaint);
                degdiff += 180;
                if (degdiff >= 180) degdiff -= 360;
                degdiff = - degdiff;
            }
            degdiffnp = degdiff;
            double maxdeflect = (mode == Mode.VOR) ? VORDEFLECT : LOCDEFLECT;
            double pegdeflect = maxdeflect * 1.2;
            if (degdiff >= pegdeflect) degdiff =  pegdeflect;
            if (degdiff < -pegdeflect) degdiff = -pegdeflect;
            double needleMidX = degdiff / maxdeflect * 412;
            double needleTopY = -412 * 1.2;
            double needleBotY =  412 * 1.2;
            canvas.drawLine ((float) needleMidX, (float) needleTopY, (float) needleMidX, (float) needleBotY, vorNeedlePaint);
        }

        // draw glideslope needle
        if (mode == Mode.ILS) {
            double maxdeflect = GSDEFLECT;
            double pegdeflect = maxdeflect * 1.2;
            if (slope >= pegdeflect) slope =  pegdeflect;
            if (slope < -pegdeflect) slope = -pegdeflect;
            double needleCentY = slope / maxdeflect * -412;
            double needleLeftX = -412 * 1.2;
            double needleRiteX =  412 * 1.2;
            canvas.drawLine ((float) needleLeftX, (float) needleCentY, (float) needleRiteX, (float) needleCentY, vorNeedlePaint);
        }

        // ADF-style needle
        if (mode == Mode.ADF) {
            canvas.save ();
            canvas.rotate ((float) deflect);
            canvas.drawPath (adfNeedlePath, adfNeedlePaint);
            canvas.restore ();
        }

        // cover up end of VOR-style needle in case it goes under dial
        canvas.drawCircle (0, 0, 718, dialBackPaint);

        // draw OBS dial
        canvas.save ();
        canvas.rotate ((float) - obsSetting);
        for (int deg = 0; deg < 360; deg += 5) {
            switch ((deg / 5) % 6) {
                case 0: {
                    // number and a thick line
                    canvas.drawText (Integer.toString (deg), 0, -823, dialTextPaint);
                    canvas.drawLine (0, -647, 0, -788, dialFatPaint);
                    break;
                }
                case 2:
                case 4: {
                    // a medium line
                    canvas.drawLine (0, -647, 0, -788, dialMidPaint);
                    break;
                }
                case 1:
                case 3:
                case 5: {
                    // a small thin line
                    canvas.drawLine (0, -647, 0, -718, dialThinPaint);
                    break;
                }
            }
            canvas.rotate (5.0F);
        }
        canvas.restore ();

        // heading arrow
        if (heading != NOHEADING) {
            Context ctx = getContext ();
            if (ctx instanceof WairToNow) {
                canvas.save ();
                canvas.rotate ((float) heading);
                canvas.translate (0, -725);
                ((WairToNow) ctx).DrawAirplaneSymbol (canvas, 140);
                canvas.restore ();
            }
        }

        // display glideslope feet-per-minute in upper right corner
        canvas.restore ();
        if ((mode == Mode.ILS) && (gsfpm != 0)) {
            canvas.drawText ("gs fpm", 970, -970 + GSFPMTEXTSIZE, gsfpmTextPaint);
            canvas.drawText (Integer.toString (gsfpm), 970, -970 + 2 * GSFPMTEXTSIZE, gsfpmTextPaint);
        }

        canvas.restore ();
    }

    // see if x,y is within area drawn by drawDMEInfo
    private boolean withinDMEArea (double x, double y)
    {
        x -= lastWidth / 2;
        y -= lastHeight / 2;
        x /= lastScale;
        y /= lastScale;
        double cr = Mathf.cosdeg (lastRotate);
        double sr = Mathf.sindeg (lastRotate);
        double xr = cr * x + sr * y;
        double yr = cr * y - sr * x;
        return (xr > -440) && (xr < -100) && (yr > 80) && (yr < 220 + DMETEXTSIZE);
    }

    // x range: -440..-100
    // y range: 80..220+DMETEXTSIZE
    private void drawDMEInfo (Canvas canvas, Paint digitPaint, Paint identPaint)
    {
        if (mode != Mode.OFF) {
            int x0 = -80;
            int idist10 = (int) Math.round (distance * 10.0);
            int idist1 = (int) Math.round (distance);
            if ((idist10 >= 0) && (idist10 <= 1999)) {
                x0 = drawDMEDigit (canvas, digitPaint, x0, idist10 % 10);
                x0 = drawDMEDot (canvas, digitPaint, x0);
                x0 = drawDMEDigit (canvas, digitPaint, x0, idist10 / 10 % 10);
                x0 = drawDMEDigit (canvas, digitPaint, x0, idist10 / 100 % 10);
                if (idist10 > 999) drawDMEDigit (canvas, digitPaint, x0, idist10 / 1000);
            } else if (idist1 <= 1999) {
                x0 = drawDMEDigit (canvas, digitPaint, x0, idist1 % 10);
                x0 = drawDMEDigit (canvas, digitPaint, x0, idist1 / 10 % 10);
                x0 = drawDMEDigit (canvas, digitPaint, x0, idist1 / 100 % 10);
                if (idist1 > 999) drawDMEDigit (canvas, digitPaint, x0, idist1 / 1000);
            } else {
                x0 = drawDMEDigit (canvas, digitPaint, x0, 10);
                x0 = drawDMEDigit (canvas, digitPaint, x0, 10);
                drawDMEDigit (canvas, digitPaint, x0, 10);
            }
            if (dmeIdent != null) {
                Waypoint.RNavParse rnavParse = new Waypoint.RNavParse (dmeIdent);
                if (rnavParse.rnavsuffix != null) {
                    identPaint.setTextAlign (Paint.Align.RIGHT);
                    canvas.drawText (rnavParse.baseident, -80, 220 + DMETEXTSIZE, identPaint);
                    identPaint.setTextAlign (Paint.Align.LEFT);
                    canvas.drawText (rnavParse.rnavsuffix, 80, 220 + DMETEXTSIZE, identPaint);
                } else {
                    identPaint.setTextAlign (Paint.Align.RIGHT);
                    canvas.drawText (rnavParse.baseident, -120, 220 + DMETEXTSIZE, identPaint);
                }
            }
        }
    }

    // 0..9: digits; 10: hyphen
    private final static byte[] sevensegs = { 0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7C, 0x07, 0x7F, 0x67, 0x40 };

    // x range : x0-120..x0-30
    // y range : 80..200
    private int drawDMEDigit (Canvas canvas, Paint digitPaint, int x0, int digit)
    {
        int y0 = 80;
        int ss = sevensegs[digit];

        x0 -= 120;

        int xs = dmeSlant ?  15 : 0;

        //      a - b
        //     /   /
        //    c - d
        //   /   /
        //  e - f

        int ax = x0 + 10;
        int bx = x0 + 90;
        int cx = ax - xs;
        int cy = y0 + 60;
        int dx = bx - xs;
        int dy = y0 + 60;
        int ex = cx - xs;
        int ey = y0 + 120;
        int fx = dx - xs;
        int fy = y0 + 120;

        if ((ss & 0x01) != 0) canvas.drawLine (ax, y0, bx, y0, digitPaint);  // top
        if ((ss & 0x02) != 0) canvas.drawLine (bx, y0, dx, dy, digitPaint);  // top right
        if ((ss & 0x04) != 0) canvas.drawLine (dx, dy, fx, fy, digitPaint);  // bot right
        if ((ss & 0x08) != 0) canvas.drawLine (ex, ey, fx, fy, digitPaint);  // bot
        if ((ss & 0x10) != 0) canvas.drawLine (cx, cy, ex, ey, digitPaint);  // bot left
        if ((ss & 0x20) != 0) canvas.drawLine (ax, y0, cx, cy, digitPaint);  // top left
        if ((ss & 0x40) != 0) canvas.drawLine (cx, cy, dx, dy, digitPaint);  // middle

        return x0;
    }

    private int drawDMEDot (Canvas canvas, Paint digitPaint, int x0)
    {
        int y0 = 80;
        x0 -= 30;
        int xb = dmeSlant ? 130 / 4 : 0;
        canvas.drawCircle (x0 - xb, y0 + 130, 5, digitPaint);
        return x0;
    }
}
