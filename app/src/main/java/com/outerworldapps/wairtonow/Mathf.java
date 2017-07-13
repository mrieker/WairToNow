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

public class Mathf {
    public final static float PI = (float)Math.PI;

    public static float atan2 (double y, double x) { return (float)Math.atan2 (y, x); }
    public static float hypot (double x, double y) { return (float)Math.hypot (x, y); }

    public static float acos (double x) { return (float)Math.acos (x); }
    public static float asin (double x) { return (float)Math.asin (x); }
    public static float atan (double x) { return (float)Math.atan (x); }
    public static float cos  (double x) { return (float)Math.cos (x); }
    public static float sin  (double x) { return (float)Math.sin (x); }
    public static float sqrt (double x) { return (float)Math.sqrt (x); }
    public static float tan  (double x) { return (float)Math.tan (x); }

    public static float cosdeg (double x) { return (float)Math.cos (x / 180.0 * Math.PI); }
    public static float sindeg (double x) { return (float)Math.sin (x / 180.0 * Math.PI); }

    public static float toDegrees (double r) { return (float) Math.toDegrees (r); }
    public static float toRadians (double d) { return (float) Math.toRadians (d); }

    public static float log10 (double x) { return (float) Math.log10 (x); }

    public static float ceil  (double f) { return (float) Math.ceil  (f); }
    public static float floor (double f) { return (float) Math.floor (f); }
}
