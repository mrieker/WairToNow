//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.LinearLayout;

@SuppressLint("ViewConstructor")
public class RingedBox extends LinearLayout {
    private float textSize;
    private Paint ringPaint;
    private Paint textPaint;
    private RectF roundrect;
    public String ringText;

    public RingedBox (WairToNow ctx)
    {
        super (ctx);
        setOrientation (HORIZONTAL);
        textSize = ctx.textSize;
        roundrect = new RectF ();
        setPadding (7, 15, 7, Math.round (textSize));
    }

    public void setRingColor (int color)
    {
        if (ringPaint == null) {
            ringPaint = new Paint ();
            ringPaint.setStrokeWidth (5);
            ringPaint.setStyle (Paint.Style.STROKE);

            textPaint = new Paint ();
            textPaint.setStrokeWidth (1);
            textPaint.setStyle (Paint.Style.FILL);
            textPaint.setTextAlign (Paint.Align.CENTER);
            textPaint.setTextSize (textSize);
        }
        ringPaint.setColor (color);
        textPaint.setColor (color);
        invalidate ();
    }

    @Override
    public void dispatchDraw (Canvas canvas)
    {
        super.dispatchDraw (canvas);
        if (ringPaint != null) {
            float cw = getWidth ();
            float ch = getHeight ();
            roundrect.set (0, 0, cw - 1, ch - 1);
            canvas.drawRoundRect (roundrect, 25, 25, ringPaint);
            if (ringText != null) {
                canvas.drawText (ringText, cw / 2, ch - 13, textPaint);
            }
        }
    }
}
