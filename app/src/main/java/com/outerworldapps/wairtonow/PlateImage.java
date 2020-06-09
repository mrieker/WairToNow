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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Display the plate image file.
 * Allows panning and zooming.
 */
public abstract class PlateImage extends View {
    public final static String TAG = "WairToNow";

    private final static int MAXZOOMIN = 16;
    private final static int MAXZOOMOUT = 8;

    private   ADVPanAndZoom advPanAndZoom;
    protected boolean       invcolors;           // invert plate colors (black<->white and everything else)
    private   double        unzoomedCanvasWidth;
    protected int           expdate;
    private   Paint         exppaint;
    private   Paint         invpaint;
    protected Rect  bitmapRect = new Rect ();    // biggest centered square completely filled by bitmap
    protected RectF canvasRect = new RectF ();   // initially biggest centered square completely filled by display area
                                                 // ... but gets panned/zoomed by user
    protected String plateid;
    protected WairToNow wairToNow;
    protected Waypoint.Airport airport;

    /**
     * Create a plate image
     * @param wtn = app that we are part of
     * @param apt = airport that plate image belongs to
     * @param pid = "APD-..." "IAP-..." etc
     * @param exp = plate expiration date yyyymmdd
     * @param fnm = plate image filename
     * @return view of the plate image
     */
    public static PlateImage Create (WairToNow wtn, Waypoint.Airport apt, String pid, int exp, String fnm, boolean ful)
    {
        if (pid.startsWith ("APD-")) {
            return new APDPlateImage (wtn, apt, pid, exp, fnm, ful);
        }
        if (pid.startsWith (IAPSynthPlateImage.prefix)) {
            return new IAPSynthPlateImage (wtn, apt, pid, exp, ful);
        }
        if (pid.startsWith ("IAP-")) {
            return new IAPRealPlateImage (wtn, apt, pid, exp, fnm, ful);
        }
        if (pid.startsWith ("RWY-")) {
            return new RWYPlateImage (wtn, apt, pid, exp);
        }
        return new NGRPlateImage (wtn, apt, pid, exp, fnm);
    }

    protected PlateImage (WairToNow wtn, Waypoint.Airport apt, String pid, int exp)
    {
        super (wtn);
        wairToNow = wtn;
        airport = apt;
        plateid = pid;
        expdate = exp;
    }

    public abstract void CloseBitmaps ();
    protected abstract void GotMouseDown (double x, double y);
    protected abstract void GotMouseUp (double x, double y);

    public void SetGPSLocation () { }

    // used by PlateCIFP,PlateDME
    public Waypoint FindWaypoint (String wayptid)
    {
        return airport.GetNearbyWaypoint (wayptid);
    }

    /**
     * Callback for mouse events on the image.
     * We use this for scrolling the map around.
     */
    @Override  // View
    public boolean onTouchEvent (@NonNull MotionEvent event)
    {
        if (advPanAndZoom == null) advPanAndZoom = new ADVPanAndZoom ();
        return advPanAndZoom.OnTouchEvent (event);
    }

    /**
     * Handle panning and zooming the display.
     */
    private class ADVPanAndZoom extends PanAndZoom {

        public ADVPanAndZoom ()
        {
            super (wairToNow);
        }

        public void MouseDown (double x, double y)
        {
            GotMouseDown (x, y);
        }

        public void MouseUp (double x, double y)
        {
            GotMouseUp (x, y);
        }

        public void Panning (double x, double y, double dx, double dy)
        {
            canvasRect.left   += dx;
            canvasRect.right  += dx;
            canvasRect.top    += dy;
            canvasRect.bottom += dy;
            invalidate ();
        }

        public void Scaling (double fx, double fy, double sf)
        {
            double canvasWidth = canvasRect.width () * sf;
            if ((canvasWidth > unzoomedCanvasWidth / MAXZOOMOUT) &&
                (canvasWidth < unzoomedCanvasWidth * MAXZOOMIN)) {
                canvasRect.left   = (float) ((canvasRect.left   - fx) * sf + fx);
                canvasRect.right  = (float) ((canvasRect.right  - fx) * sf + fx);
                canvasRect.top    = (float) ((canvasRect.top    - fy) * sf + fy);
                canvasRect.bottom = (float) ((canvasRect.bottom - fy) * sf + fy);
            }
            invalidate ();
        }
    }

    /**
     * Open first plate bitmap image page file and map to view.
     */
    protected Bitmap OpenFirstPage (String filename)
    {
        Bitmap bm;
        try {
            bm = readGif (filename + ".p1");
        } catch (Throwable e) {
            Log.w (TAG, "onDraw: bitmap load error", e);
            return null;
        }

        // get bitmap dimensions
        int bmWidth  = bm.getWidth  ();
        int bmHeight = bm.getHeight ();

        // map to view
        SetBitmapMapping (bmWidth, bmHeight);
        return bm;
    }

    /**
     * Read gif bitmap from state-specific zip file
     * @param fn = name of gif file within the zip file
     */
    protected Bitmap readGif (String fn)
            throws IOException
    {
        if (fn.startsWith ("gif_150/")) fn = fn.substring (8);
        ZipFile zf = wairToNow.maintView.getStateZipFile (airport.state);
        if (zf == null) {
            throw new FileNotFoundException ("state " + airport.state + " not downloaded");
        }
        ZipEntry ze = zf.getEntry (fn);
        if (ze == null) {
            throw new FileNotFoundException ("gif " + fn + " not found in state " + airport.state);
        }
        InputStream is = zf.getInputStream (ze);
        try {
            Bitmap bm = BitmapFactory.decodeStream (is);
            if (bm == null) {
                throw new IOException ("bitmap " + fn + " corrupt in state " + airport.state);
            }
            return maybeInvertColors (bm);
        } finally {
            is.close ();
        }
    }

    protected Bitmap maybeInvertColors (Bitmap bm)
    {
        invcolors = wairToNow.optionsView.invPlaColOption.checkBox.isChecked ();
        if (invcolors) {
            if (invpaint == null) invpaint = makeInvertPaint ();
            int h = bm.getHeight ();
            int w = bm.getWidth ();
            Bitmap newbm = Bitmap.createBitmap (w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas (newbm);
            canvas.drawBitmap (bm, 0, 0, invpaint);
            bm.recycle ();
            bm = newbm;
        }
        return bm;
    }

    // adapted from https://developer.android.com/reference/android/graphics/ColorMatrix
    private static Paint makeInvertPaint ()
    {
        ColorMatrix matrixinvert = new ColorMatrix ();
        matrixinvert.set (new float [] {
                -1.0F, 0.0F, 0.0F, 0.0F, 255.0F,
                0.0F, -1.0F, 0.0F, 0.0F, 255.0F,
                0.0F, 0.0F, -1.0F, 0.0F, 255.0F,
                0.0F, 0.0F, 0.0F, 1.0F, 0.0F
        });
        ColorMatrixColorFilter cmcf = new ColorMatrixColorFilter (matrixinvert);
        Paint invpaint = new Paint ();
        invpaint.setColorFilter (cmcf);
        return invpaint;
    }

    /**
     * Set up initial mapping of bitmap->view such that bitmap fills display area.
     * The bitmaps are typically a little taller than the display area, in which case
     * the top and bottom margins of the bitmap will be chopped off.
     */
    protected void SetBitmapMapping (int bmWidth, int bmHeight)
    {
        // get largest square centered on bitmap that is completely filled with something
        // ie, part of bitmap around two of the sides might be trimmed off
        if (bmHeight > bmWidth) {
            bitmapRect.left   = 0;
            bitmapRect.right  = bmWidth;
            bitmapRect.top    = (bmHeight - bmWidth) / 2;
            bitmapRect.bottom = (bmHeight + bmWidth) / 2;
        } else {
            bitmapRect.left   = (bmWidth - bmHeight) / 2;
            bitmapRect.right  = (bmWidth + bmHeight) / 2;
            bitmapRect.top    = 0;
            bitmapRect.bottom = bmHeight;
        }

        // get drawring area dimensions in pixels
        int canWidth  = getWidth  ();
        int canHeight = getHeight ();

        // get drawring area dimensions in inches
        double xdpi = wairToNow.dotsPerInchX;
        double ydpi = wairToNow.dotsPerInchY;
        double canInWidth  = canWidth  / xdpi;
        double canInHeight = canHeight / ydpi;

        // get largest square centered on canvas that can be seen in total
        // ie, there might be unused parts of two of the canvas' margins
        if (canInHeight > canInWidth) {
            canvasRect.left   = 0;
            canvasRect.right  = canWidth;
            canvasRect.top    = (float) (ydpi * (canInHeight - canInWidth) / 2);
            canvasRect.bottom = (float) (ydpi * (canInHeight + canInWidth) / 2);
        } else {
            canvasRect.left   = (float) (xdpi * (canInWidth - canInHeight) / 2);
            canvasRect.right  = (float) (xdpi * (canInWidth + canInHeight) / 2);
            canvasRect.top    = 0;
            canvasRect.bottom = canHeight;
        }
        unzoomedCanvasWidth = canvasRect.width ();
    }

    /**
     * Set up initial mapping of bitmap->view such that whole bitmap is visible.
     */
    protected void SetBitmapMapping2 (int bmWidth, int bmHeight, int canWidth, int canHeight)
    {
        // get smallest square centered on bitmap that contains whole bitmap
        // ie, there might be unfilled margins on the sides
        if (bmHeight < bmWidth) {
            bitmapRect.left   = 0;
            bitmapRect.right  = bmWidth;
            bitmapRect.top    = (bmHeight - bmWidth) / 2;
            bitmapRect.bottom = (bmHeight + bmWidth) / 2;
        } else {
            bitmapRect.left   = (bmWidth - bmHeight) / 2;
            bitmapRect.right  = (bmWidth + bmHeight) / 2;
            bitmapRect.top    = 0;
            bitmapRect.bottom = bmHeight;
        }

        // get drawring area dimensions in inches
        double xdpi = wairToNow.dotsPerInchX;
        double ydpi = wairToNow.dotsPerInchY;
        double canInWidth  = canWidth  / xdpi;
        double canInHeight = canHeight / ydpi;

        // get largest square centered on canvas that can be seen in total
        // ie, there might be unused parts of two of the canvas' margins
        if (canInHeight > canInWidth) {
            canvasRect.left   = 0;
            canvasRect.right  = canWidth;
            canvasRect.top    = (float) (ydpi * (canInHeight - canInWidth) / 2);
            canvasRect.bottom = (float) (ydpi * (canInHeight + canInWidth) / 2);
        } else {
            canvasRect.left   = (float) (xdpi * (canInWidth - canInHeight) / 2);
            canvasRect.right  = (float) (xdpi * (canInWidth + canInHeight) / 2);
            canvasRect.top    = 0;
            canvasRect.bottom = canHeight;
        }
        unzoomedCanvasWidth = canvasRect.width ();
    }

    /**
     * bitmap <=> view mapping.
     */
    protected double BitmapX2CanvasX (double bitmapX)
    {
        double nx = (bitmapX - bitmapRect.left) / (double) bitmapRect.width ();
        return nx * canvasRect.width () + canvasRect.left;
    }
    protected double BitmapY2CanvasY (double bitmapY)
    {
        double ny = (bitmapY - bitmapRect.top) / (double) bitmapRect.height ();
        return ny * canvasRect.height () + canvasRect.top;
    }

    protected double CanvasX2BitmapX (double canvasX)
    {
        double nx = (canvasX - canvasRect.left) / canvasRect.width ();
        return nx * bitmapRect.width () + bitmapRect.left;
    }
    protected double CanvasY2BitmapY (double canvasY)
    {
        double ny = (canvasY - canvasRect.top) / canvasRect.height ();
        return ny * bitmapRect.height () + bitmapRect.top;
    }

    /**
     * If plate is expired, draw an hash on top of it.
     * @param canvas = where to draw hash
     * @param outline = outline of plate on canvas
     */
    protected void DrawExpiredHash (Canvas canvas, RectF outline)
    {
        if (expdate < MaintView.deaddate) {
            if (exppaint == null) {
                exppaint = new Paint ();
                exppaint.setColor (Color.RED);
                exppaint.setStrokeWidth (2);
            }
            canvas.save ();
            canvas.clipRect (outline);
            double ch = outline.height ();
            double step = wairToNow.dotsPerInch * 0.75;
            for (double i = outline.left - ch; i < outline.right; i += step) {
                canvas.drawLine ((float) i, outline.top, (float) (ch + i), outline.bottom, exppaint);
                canvas.drawLine ((float) i, outline.bottom, (float) (ch + i), outline.top, exppaint);
            }
            canvas.restore ();
        }
    }
}
