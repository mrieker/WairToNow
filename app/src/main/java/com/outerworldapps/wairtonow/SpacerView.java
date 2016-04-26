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
import android.view.View;

public class SpacerView extends View {
    public SpacerView (Context ctx)
    {
        super (ctx);
    }

    public SpacerView (Context ctx, AttributeSet attrs)
    {
        super (ctx, attrs);
    }

    public SpacerView (Context ctx, AttributeSet attrs, int defStyle)
    {
        super (ctx, attrs, defStyle);
    }

    @Override
    protected final void onMeasure (int widthMeasureSpec, int heightMeasureSpec)
    {
        setMeasuredDimension (10, 10);
    }

    @Override
    protected int getSuggestedMinimumHeight ()
    {
        return 10;
    }

    @Override
    protected int getSuggestedMinimumWidth ()
    {
        return 10;
    }
}
