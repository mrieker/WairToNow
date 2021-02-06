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

import android.graphics.Canvas;
import android.os.SystemClock;

/*
 * A real or synthetic IAP plate.
 */
public abstract class IAPPlateImage extends GRPlateImage {
    protected boolean    full;
    protected double     iapTrueUpRads;
    protected long       plateLoadedUptime;
    protected PlateCIFP  plateCIFP;
    protected PlateDME   plateDME;
    private   PlateTimer plateTimer;

    protected IAPPlateImage (WairToNow wtn, Waypoint.Airport apt, String pid, int exp, boolean ful)
    {
        super (wtn, apt, pid, exp);
        full       = ful;
        plateDME   = new PlateDME (wairToNow, this, airport.ident, plateid);
        plateTimer = new PlateTimer (wairToNow, this);
        plateLoadedUptime = SystemClock.uptimeMillis ();
    }

    // maybe we know what navaid is being used by the plate
    public abstract Waypoint GetRefNavWaypt ();

    public abstract void ReadCIFPs ();

    public abstract void DrawDMEArc (Waypoint nav, double beg, double end, double nm);

    protected void DrawCommon (Canvas canvas)
    {
        /*
         * Draw any CIFP, enabled DMEs and timer.
         */
        if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
            if (plateCIFP != null) plateCIFP.DrawCIFP (canvas);
            plateDME.DrawDMEs (canvas, wairToNow.currentGPSLat, wairToNow.currentGPSLon,
                    wairToNow.currentGPSAlt, wairToNow.currentGPSTime,
                    wairToNow.currentMagVar);
        }
        plateTimer.DrawTimer (canvas);

        /*
         * Draw airplane and current location info if we have enough georeference info.
         */
        DrawLocationArrow (canvas, iapTrueUpRads, false);
        wairToNow.currentCloud.DrawIt (canvas);

        /*
         * Draw GPS Available box around plate border.
         */
        if (full && !wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
            wairToNow.drawGPSAvailable (canvas, this);
        }
    }

    protected void GotMouseDown (double x, double y)
    {
        plateDME.GotMouseDown (x, y);
        plateTimer.GotMouseDown (x, y);
        if (plateCIFP != null) plateCIFP.GotMouseDown (x, y);
        wairToNow.currentCloud.MouseDown ((int) Math.round (x), (int) Math.round (y),
                System.currentTimeMillis ());
    }

    protected void GotMouseUp (double x, double y)
    {
        plateDME.GotMouseUp ();
        if (plateCIFP != null) plateCIFP.GotMouseUp ();
        wairToNow.currentCloud.MouseUp ((int) Math.round (x), (int) Math.round (y),
                System.currentTimeMillis ());
    }

    @Override  // PlateImage
    public void SetGPSLocation ()
    {
        super.SetGPSLocation ();
        if (plateCIFP != null) plateCIFP.SetGPSLocation ();
    }

    public abstract void ShadeCirclingAreaWork ();
}
