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
import android.view.View;
import android.widget.LinearLayout;

/**
 * This view presents the plate to the user (apt diagram, iap, sid, star, etc),
 * and allows scaling and translation by finger gestures.
 * APDs and IAPs have georef info so can present the current position.
 */
@SuppressLint("ViewConstructor")
public class PlateView extends LinearLayout implements WairToNow.CanBeMainView {
    public  PlateImage plateImage;      // displays the plate image bitmap
    private WairToNow wairToNow;
    private WaypointView waypointView;  // airport waypoint page we are part of

    public PlateView (WaypointView wv, String fn, Waypoint.Airport aw, String pd, int ex, boolean fu)
    {
        super (wv.wairToNow);
        waypointView = wv;
        wairToNow = wv.wairToNow;
        construct (fn, aw, pd, ex, fu);
    }
    private void construct (String fn, Waypoint.Airport aw, String pd, int ex, boolean fu)
    {
        plateImage = PlateImage.Create (wairToNow, aw, pd, ex, fn, fu);

        plateImage.setLayoutParams (new LayoutParams (LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView (plateImage);
    }

    /**
     * New GPS location received, update georef'd plate.
     */
    public void SetGPSLocation ()
    {
        plateImage.SetGPSLocation ();
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return waypointView.GetTabName ();
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return wairToNow.optionsView.powerLockOption.checkBox.isChecked ();
    }

    /**
     * Close any open bitmaps so we don't take up too much memory.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        plateImage.CloseBitmaps ();
    }

    /**
     * Maybe we should keep screen powered up while plate is being displayed.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    { }

    @Override  // CanBeMainView
    public void OrientationChanged ()
    { }

    /**
     * If back arrow pressed when viewing a plate,
     * show the waypoint view page.
     */
    @Override  // WairToNow.GetBackPage
    public View GetBackPage ()
    {
        return waypointView;
    }

    /**
     * Tab re-clicked, switch back to search screen.
     */
    @Override
    public void ReClicked ()
    {
        waypointView.selectedPlateView = null;
        wairToNow.SetCurrentTab (waypointView);
    }
}
