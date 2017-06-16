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

public class Vector3 {
    public double x, y, z;

    public void set (Vector3 v)
    {
        x = v.x;
        y = v.y;
        z = v.z;
    }

    public Vector3 cross (Vector3 that)
    {
        Vector3 norm = new Vector3 ();
        norm.x = this.y * that.z - this.z * that.y;
        norm.y = this.z * that.x - this.x * that.z;
        norm.z = this.x * that.y - this.y * that.x;
        return norm;
    }

    public double dot (Vector3 that)
    {
        return this.x * that.x + this.y * that.y + this.z * that.z;
    }

    public double length ()
    {
        return Math.sqrt (dot (this));
    }

    public Vector3 minus (Vector3 that)
    {
        Vector3 diff = new Vector3 ();
        diff.x = this.x - that.x;
        diff.y = this.y - that.y;
        diff.z = this.z - that.z;
        return diff;
    }

    public Vector3 plus (Vector3 that)
    {
        Vector3 sum = new Vector3 ();
        sum.x = this.x + that.x;
        sum.y = this.y + that.y;
        sum.z = this.z + that.z;
        return sum;
    }

    public Vector3 rotate (Vector3 axis, double rads)
    {
        double[] mat = new double[24];

        MatrixD.setRotateM (mat, 0, Math.toDegrees (rads),
                axis.x, axis.y, axis.z);

        mat[16] = this.x;
        mat[17] = this.y;
        mat[18] = this.z;
        mat[19] = 1.0F;

        MatrixD.multiplyMV (mat, 20, mat, 0, mat, 16);

        Vector3 rot = new Vector3 ();
        rot.x = mat[20] / mat[23];
        rot.y = mat[21] / mat[23];
        rot.z = mat[22] / mat[23];

        return rot;
    }

    @Override
    public String toString ()
    {
        return x + "," + y + "," + z;
    }
}
