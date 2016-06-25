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
import android.graphics.Canvas;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.view.View;

/**
 * A chart, whether downloaded or not, that can be selected for display.
 */
public interface DisplayableChart {
    String GetSpacenameSansRev ();    // chart name without revision, eg, "New York SEC"
    boolean IsDownloaded ();
    View GetMenuSelector (@NonNull ChartView chartView);
    void UserSelected ();
    void CloseBitmaps ();

    // used by ChartView - loads asynchronously, called in GUI thread
    interface Invalidatable {
        void postInvalidate ();
    }
    void DrawOnCanvas (@NonNull PixelMapper pmap, @NonNull Canvas canvas, @NonNull Invalidatable inval, float canvasHdgRads);
    boolean LatLon2CanPixExact (float lat, float lon, @NonNull Point canpix);
    boolean CanPix2LatLonExact (float canpixx, float canpixy, @NonNull LatLon ll);

    // used by Chart3DView - loads synchronously, called in EarthSector.LoaderThread
    void GetL2StepLimits (int[] limits);
    Bitmap GetMacroBitmap (float slat, float nlat, float wlon, float elon);
    void LatLon2MacroBitmap (float lat, float lon, @NonNull Point mbmpix);
}
