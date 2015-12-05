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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

/**
 * Just like HorizontalScrollView but jumps in discrete steps.
 */
public class DetentHorizontalScrollView extends HorizontalScrollView {
    private float delta = 16.0F;
    private float downx;

    public DetentHorizontalScrollView (Context ctx)
    {
        super (ctx);
    }

    public DetentHorizontalScrollView (Context ctx, AttributeSet attrs)
    {
        super (ctx, attrs);
    }

    public DetentHorizontalScrollView (Context ctx, AttributeSet attrs, int defStyle)
    {
        super (ctx, attrs, defStyle);
    }

    public void setDetentSize (float d)
    {
        delta = d;
    }

    @Override
    public boolean onTouchEvent (MotionEvent me)
    {
        int action = me.getAction ();
        switch (action & MotionEvent.ACTION_MASK) {

            // save where finger went down
            case MotionEvent.ACTION_DOWN: {
                downx = me.getX ();
                break;
            }

            // filter out moves less than delta away from down position
            // otherwise reset down position so we get another detent from here
            // and let scroller process event which should jump to this spot
            case MotionEvent.ACTION_MOVE: {
                float x = me.getX ();
                if (Math.abs (x - downx) < delta) return true;
                downx = x;
                break;
            }

            // tell scroller to cancel so it won't try to fling
            case MotionEvent.ACTION_UP: {
                me.setAction (MotionEvent.ACTION_CANCEL);
                break;
            }
        }

        return super.onTouchEvent (me);
    }
}
