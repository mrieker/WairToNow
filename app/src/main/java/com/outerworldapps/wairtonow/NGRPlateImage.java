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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.Log;

import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Display a non-georef multi-page plate image file.
 * Allows panning and zooming.
 */
@SuppressLint("ViewConstructor")
public class NGRPlateImage extends PlateImage {
    private Bitmap[] bitmaps;                    // one entry per plate page
    private int      bmHeight;                   // height of one image
    private int      bmWidth;                    // width of one image
    private int      numPages;                   // number of pages in plate
    private RectF    canTmpRect = new RectF ();  // same as canvasRect except shifted for various pages during onDraw()
    private String   filename;                   // name of gifs on flash, without ".p<pageno>" suffix

    public NGRPlateImage (WairToNow wtn, Waypoint.Airport apt, String pid, int exp, String fn)
    {
        super (wtn, apt, pid, exp);
        if (fn.startsWith ("gif_150/")) fn = fn.substring (8);
        filename = fn;

        /*
         * We are given the base name ending in .gif.
         * But the files are multiple pages ending in .gif.p<pageno>.
         * So count how many pages we actually have for this plate.
         */
        String fndotp = fn + ".p";
        int fndotplen = fndotp.length ();
        try {
            ZipFile zf = wairToNow.maintView.getCurentStateZipFile (airport.state);
            for (Enumeration<? extends ZipEntry> it = zf.entries (); it.hasMoreElements ();) {
                ZipEntry ze = it.nextElement ();
                String en = ze.getName ();
                if (en.startsWith (fndotp)) {
                    int p = Integer.parseInt (en.substring (fndotplen));
                    if (numPages < p) numPages = p;
                }
            }
        } catch (IOException ioe) {
            Log.w (TAG, "error scanning state " + airport.state + " zip file", ioe);
        }
        bitmaps = new Bitmap[numPages];
    }

    public int getNumPages ()
    {
        return numPages;
    }

    /**
     * Close any open bitmaps so we don't take up too much memory.
     */
    public void CloseBitmaps ()
    {
        for (int i = 0; i < numPages; i ++) {
            Bitmap bm = bitmaps[i];
            if (bm != null) bm.recycle ();
            bitmaps[i] = null;
        }
    }

    protected void GotMouseDown (double x, double y) { }
    protected void GotMouseUp (double x, double y) { }

    /**
     * Draw the plate image.
     */
    @SuppressLint("DrawAllocation")
    @Override  // View
    public void onDraw (Canvas canvas)
    {
        /*
         * Get single page's bitmap size.
         * Assume all pages are same size.
         */
        if (bmWidth == 0) {
            bitmaps[0] = OpenFirstPage (filename);
            if (bitmaps[0] == null) return;
            bmWidth  = bitmaps[0].getWidth  ();
            bmHeight = bitmaps[0].getHeight ();
        }

        /*
         * Loop through pages to see which are visible and draw them.
         */
        int canWidth  = getWidth  ();
        int canHeight = getHeight ();
        for (int vpi = 0; vpi < numPages; vpi ++) {
            int imageTop = vpi * bmHeight;
            int imageBot = imageTop + bmHeight;

            /*
             * See where it maps to on canvas and skip if not visible.
             */
            canTmpRect.top    = (float) BitmapY2CanvasY (imageTop) + vpi * 2;
            canTmpRect.bottom = (float) BitmapY2CanvasY (imageBot) + vpi * 2;
            if (canTmpRect.top    >= canHeight) continue;
            if (canTmpRect.bottom <= 0)         continue;

            for (int hpi = 0; vpi + hpi < numPages; hpi ++) {
                int imageLeft = hpi * bmWidth;
                int imageRite = imageLeft + bmWidth;

                /*
                 * See where it maps to on canvas and skip if not visible.
                 */
                canTmpRect.left  = (float) BitmapX2CanvasX (imageLeft) + hpi * 2;
                canTmpRect.right = (float) BitmapX2CanvasX (imageRite) + hpi * 2;
                if (canTmpRect.left  >= canWidth) continue;
                if (canTmpRect.right <= 0)        continue;

                /*
                 * Some/all of page's image is visible, make sure we have the bitmap loaded.
                 */
                int pi = vpi + hpi;
                Bitmap bm = bitmaps[pi];
                if (bm == null) {
                    String fn = filename + ".p" + (pi + 1);
                    try {
                        bm = readGif (fn);
                    } catch (Throwable e1) {
                        for (int i = 0; i < numPages; i ++) {
                            bm = bitmaps[i];
                            if (bm != null) bm.recycle ();
                            bitmaps[i] = null;
                        }
                        try {
                            bm = readGif (fn);
                        } catch (Throwable e2) {
                            Log.w (TAG, "NGRPlateImage.onDraw: bitmap load error", e2);
                            continue;
                        }
                    }
                    bitmaps[pi] = bm;
                }

                /*
                 * Display bitmap to canvas.
                 */
                canvas.drawBitmap (bm, null, canTmpRect, null);

                /*
                 * Draw hash if expired.
                 */
                DrawExpiredHash (canvas, canTmpRect);
            }
        }
    }
}
