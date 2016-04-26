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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.Button;

/**
 * Same as Button but can have colored ring drawn around the perimeter.
 */
public class RingedButton extends Button {
    private Paint ringPaint;

    public RingedButton (Context ctx)
    {
        super (ctx);
    }

    public void setRingColor (int color)
    {
        if (ringPaint == null) {
            ringPaint = new Paint ();
            ringPaint.setStrokeWidth (5);
            ringPaint.setStyle (Paint.Style.STROKE);
        }
        ringPaint.setColor (color);
        invalidate ();
    }

    @Override
    public void onDraw (Canvas canvas)
    {
        super.onDraw (canvas);
        if (ringPaint != null) {
            float cw = getWidth ();
            float ch = getHeight ();
            float pb = getPaddingBottom ();
            float pl = getPaddingLeft ();
            float pr = getPaddingRight ();
            float pt = getPaddingTop ();
            canvas.drawRect (pl / 2, pt / 2, cw - pr / 2, ch - pb / 2, ringPaint);
        }
    }
}
