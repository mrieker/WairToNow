package com.outerworldapps.wairtonow;


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
    private static double isLeft( PointD P0, PointD P1, double X2, double Y2 )
    {
        return ( (P1.x - P0.x) * (Y2 - P0.y)
                - (X2 -  P0.x) * (P1.y - P0.y) );
    }

    // wn_PnPoly(): winding number test for a point in a polygon
    //      Input:   P = a point,
    //               V[] = vertex points of a polygon V[n+1] with V[n]=V[0]
    //      Return:  wn = the winding number (=0 only when P is outside)
    public static int wn( double X, double Y, PointD[] V, int n )
    {
        int    wn = 0;    // the  winding number counter

        // loop through all edges of the polygon
        PointD R = V[0];
        for (int i=0; ++i<=n;) {   // edge from V[i-1] to  V[i]
            PointD S = V[i];
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

    // https://www.wikihow.com/Calculate-the-Area-of-a-Polygon
    public static double area (PointD[] V, int n)
    {
        double prodxy = 0.0;
        double prodyx = 0.0;
        for (int i = 0; i < n; i ++) {
            prodxy += V[i].x * V[i+1].y;
            prodyx += V[i].y * V[i+1].x;
        }
        return Math.abs (prodxy - prodyx) / 2.0;
    }
}
