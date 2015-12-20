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

/**
 * @brief Verify values given in outlines.txt file
 *        by reading the .tif file from the charts directory then
 *        writing out a .png file with the outline drawn in red.
 */

// yum install libgdiplus
// yum install libgdiplus-devel

// gmcs -debug -out:VerifyOutline.exe -reference:System.Drawing.dll VerifyOutline.cs ChartTiff.cs

// mono --debug VerifyOutline.exe 'ENR ELUS33 20151210'

using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

public struct Point {
    public int x, y;

    public Point (int xx, int yy) { x = xx; y = yy; }
}

public class VerifyOutline {
    private static ChartTiff chart;
    private static Point[] outline;

    public static void Main (string[] args)
    {
        string spacename = args[0];
        chart = new ChartTiff ("charts/", spacename);

        string spacenamenr = spacename.Substring (0, spacename.LastIndexOf (' '));
        StreamReader outFile = new StreamReader ("../webpages/outlines.txt");
        string outLine;
        while ((outLine = outFile.ReadLine ()) != null) {
            string[] parts = outLine.Split (new char[] { ':' });
            string namenr = parts[0].Trim ();
            if (namenr == spacenamenr) {
                parts = parts[1].Split (new char[] { '/' });
                if (parts.Length < 2) throw new Exception ("too few pairs");
                outline = new Point[parts.Length+1];
                int j = 0;
                foreach (string part in parts) {
                    string[] xystrs = part.Split (new char[] { ',' });
                    if (xystrs.Length != 2) throw new Exception ("bad x,y " + part);
                    Point p = new Point ();
                    p.x = int.Parse (xystrs[0].Trim ());
                    p.y = int.Parse (xystrs[1].Trim ());
                    outline[j++] = p;
                }
                /*if (j == 2) {
                    Point[] five = new Point[5];
                    five[0] = outline[0];
                    five[1] = new Point (outline[1].x, outline[0].y);
                    five[2] = outline[1];
                    five[3] = new Point (outline[0].x, outline[1].y);
                    outline = five;
                    j = 4;
                }*/
                outline[j] = outline[0];
                break;
            }
        }
        outFile.Close ();
        if (outline == null) throw new Exception ("no outlines.txt line for " + spacenamenr);

        /*
        for (int i = 0; ++ i < outline.Length;) {
            Point p = outline[i-1];
            Point q = outline[i];
            for (int dy = -2; dy <= 2; dy ++) {
                for (int dx = -2; dx <= 2; dx ++) {
                    DrawLine (p.x + dx, p.y + dy, q.x + dx, q.y + dy);
                }
            }
        }
        */

        for (int y = 0; y < chart.height; y ++) {
            for (int x = 0; x < chart.width; x ++) {
                if (outline.Length == 3) {
                    if ((x < outline[0].x) || (y < outline[0].y) || (x > outline[1].x) || (y > outline[1].y)) {
                        chart.bmp.SetPixel (x, y, Color.Yellow);
                    }
                } else {
                    if (PnPoly.wn (x, y, outline, outline.Length - 1) == 0) {
                        chart.bmp.SetPixel (x, y, Color.Yellow);
                    }
                }
            }
        }

        string undername = spacename.Replace (' ', '_');
        chart.bmp.Save (undername + ".tmp", System.Drawing.Imaging.ImageFormat.Png);
        File.Delete (undername + ".png");
        File.Move (undername + ".tmp", undername + ".png");
    }

    /**
     * @brief Draw a line to the bitmap, clipping as necessary.
     */
    private static void DrawLine (int x1, int y1, int x2, int y2)
    {
        int x, y;

        if (x2 != x1) {
            if (x2 < x1) {
                x = x1;
                y = y1;
                x1 = x2;
                y1 = y2;
                x2 = x;
                y2 = y;
            }
            for (x = x1; x <= x2; x ++) {
                y = (int)((float)(y2 - y1) / (float)(x2 - x1) * (float)(x - x1) + 0.5) + y1;
                SetPixel (x, y);
            }
        }

        if (y2 != y1) {
            if (y2 < y1) {
                y = y1;
                x = x1;
                y1 = y2;
                x1 = x2;
                y2 = y;
                x2 = x;
            }
            for (y = y1; y <= y2; y ++) {
                x = (int)((float)(x2 - x1) / (float)(y2 - y1) * (float)(y - y1) + 0.5) + x1;
                SetPixel (x, y);
            }
        }
    }

    /**
     * @brief Set pixel, clipping if necessary.
     */
    private static void SetPixel (int x, int y)
    {
        if (x < 0) return;
        if (x >= chart.width) return;
        if (y < 0) return;
        if (y >= chart.height) return;
        chart.bmp.SetPixel (x, y, Color.Red);
    }
}

public class PnPoly {

    // Copyright 2000 softSurfer, 2012 Dan Sunday
    // This code may be freely used, distributed and modified for any purpose
    // providing that this copyright notice is included with it.
    // SoftSurfer makes no warranty for this code, and cannot be held
    // liable for any real or imagined damage resulting from its use.
    // Users of this code must verify correctness for their application.

    // http://geomalgorithms.com/a03-_inclusion.html

    // isLeft(): tests if a point is Left|On|Right of an infinite line.
    //    Input:  three points P0, P1, and P2
    //    Return: >0 for P2 left of the line through P0 and P1
    //            =0 for P2  on the line
    //            <0 for P2  right of the line
    //    See: Algorithm 1 "Area of Triangles and Polygons"
    private static int isLeft( Point P0, Point P1, int X2, int Y2 )
    {
        return ( (P1.x - P0.x) * (Y2 - P0.y)
                - (X2 -  P0.x) * (P1.y - P0.y) );
    }

    // wn_PnPoly(): winding number test for a point in a polygon
    //      Input:   P = a point,
    //               V[] = vertex points of a polygon V[n+1] with V[n]=V[0]
    //      Return:  wn = the winding number (=0 only when P is outside)
    public static int wn( int X, int Y, Point[] V, int n )
    {
        int    wn = 0;    // the  winding number counter

        // loop through all edges of the polygon
        Point R = V[0];
        for (int i=0; ++i<=n;) {   // edge from V[i-1] to  V[i]
            Point S = V[i];
            if (R.y <= Y) {          // start y <= P.y
                if (S.y  > Y)      // an upward crossing
                     if (isLeft( R, S, X, Y) > 0)  // P left of  edge
                         ++wn;            // have  a valid up intersect
            }
            else {                        // start y > P.y (no test needed)
                if (S.y  <= Y)     // a downward crossing
                     if (isLeft( R, S, X, Y) < 0)  // P right of  edge
                         --wn;            // have  a valid down intersect
            }
            R = S;
        }
        return wn;
    }
}
