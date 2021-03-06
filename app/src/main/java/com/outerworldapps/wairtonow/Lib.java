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

import android.content.DialogInterface;
import android.hardware.GeomagneticField;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

public class Lib {
    public final static String[] nullarrayString = new String[0];

    // fine-tuned constants
    public static final double FtPerM    = 3.28084;
    public static final double FtPerNM   = 6076.12;
    public static final double KtPerMPS  = 1.94384;
    public static final double NMPerDeg  = 60.0;
    public static final double MMPerIn   = 25.4;    // millimetres per inch
    public static final double SMPerNM   = 1.15078;
    public static final int    MPerNM    = 1852;    // metres per naut mile

    public static final TimeZone tzUtc = TimeZone.getTimeZone ("UTC");

    private static ThreadLocal<SimpleDateFormat> utcfmt = new ThreadLocal<> ();

    /**
     * Split a comma-separated value string into its various substrings.
     * @param line = comma-separated line
     * @return array of the various substrings
     */
    public static String[] QuotedCSVSplit (String line)
    {
        int len = line.length ();
        ArrayList<String> cols = new ArrayList<> (len + 1);
        boolean quoted = false;
        boolean escapd = false;
        StringBuilder sb = new StringBuilder (len);
        for (int i = 0;; i ++) {
            char c = 0;
            if (i < len) c = line.charAt (i);
            if (!escapd && (c == '"')) {
                quoted = !quoted;
                continue;
            }
            if (!escapd && (c == '\\')) {
                escapd = true;
                continue;
            }
            if ((!escapd && !quoted && (c == ',')) || (c == 0)) {
                cols.add (sb.toString ());
                if (c == 0) break;
                sb.delete (0, sb.length ());
                continue;
            }
            if (escapd && (c == 'n')) c = '\n';
            if (escapd && (c == 'z')) c = 0;
            sb.append (c);
            escapd = false;
        }
        String[] array = new String[cols.size()];
        return cols.toArray (array);
    }

    /**
     * Create a quoted string suitable for QuotedCSVSplit().
     */
    @SuppressWarnings("unused")
    public static String QuotedString (String unquoted)
    {
        int len = unquoted.length ();
        StringBuilder sb = new StringBuilder (len + 2);
        sb.append ('"');
        for (int i = 0; i < len; i ++) {
            char c = unquoted.charAt (i);
            switch (c) {
                case '\\': {
                    sb.append ("\\\\");
                    break;
                }
                case '\n': {
                    sb.append ("\\n");
                    break;
                }
                case 0: {
                    sb.append ("\\z");
                    break;
                }
                case '"': {
                    sb.append ("\\\"");
                    break;
                }
                default: {
                    sb.append (c);
                    break;
                }
            }
        }
        sb.append ('"');
        return sb.toString ();
    }

    /**
     * Return westmost longitude, normalized to -180..+179.999999999
     */
    public static double Westmost (double lon1, double lon2)
    {
        // make sure lon2 comes at or after lon1 by less than 360deg
        while (lon2 < lon1) lon2 += 360.0;
        while (lon2 - 360.0 >= lon1) lon2 -= 360.0;

        // if lon2 is east of lon1 by at most 180deg, lon1 is westmost
        // if lon2 is east of lon1 by more than 180, lon2 is westmost
        double westmost = (lon2 - lon1 < 180.0) ? lon1 : lon2;

        return NormalLon (westmost);
    }

    /**
     * Return eastmost longitude, normalized to -180..+179.999999999
     */
    public static double Eastmost (double lon1, double lon2)
    {
        // make sure lon2 comes at or after lon1 by less than 360deg
        while (lon2 < lon1) lon2 += 360.0;
        while (lon2 - 360.0 >= lon1) lon2 -= 360.0;

        // if lon2 is east of lon1 by at most 180deg, lon2 is eastmost
        // if lon2 is east of lon1 by more than 180, lon1 is eastmost
        double eastmost = (lon2 - lon1 < 180.0) ? lon2 : lon1;

        return NormalLon (eastmost);
    }

    /**
     * Normalize a longitude in range -180.0..+179.999999999
     */
    public static double NormalLon (double lon)
    {
        while (lon < -180.0) lon += 360.0;
        while (lon >= 180.0) lon -= 360.0;
        return lon;
    }

    /**
     * Given two normalized longitudes, find the normalized average longitude.
     */
    public static double AvgLons (double lona, double lonb)
    {
        if (lonb - lona > 180.0) lona += 360.0;
        if (lona - lonb > 180.0) lonb += 360.0;
        return NormalLon ((lona + lonb) / 2.0);
    }

    /**
     * See if the two latitude ranges overlap.
     * @param aSouthLat = first range south edge
     * @param aNorthLat = first range north edge
     * @param bSouthLat = second range south edge
     * @param bNorthLat = second range north edge
     * @return whether or not they overlap
     */
    public static boolean LatOverlap (double aSouthLat, double aNorthLat, double bSouthLat, double bNorthLat)
    {
        double aSouthLat1 = Math.min (aSouthLat, aNorthLat);
        double aNorthLat1 = Math.max (aSouthLat, aNorthLat);
        double bSouthLat1 = Math.min (bSouthLat, bNorthLat);
        double bNorthLat1 = Math.max (bSouthLat, bNorthLat);
        return (aNorthLat1 > bSouthLat1) && (bNorthLat1 > aSouthLat1);
    }

    public static boolean LatOverlap (double aSouthLat, double aNorthLat, double singleLat)
    {
        double aSouthLat1 = Math.min (aSouthLat, aNorthLat);
        double aNorthLat1 = Math.max (aSouthLat, aNorthLat);
        return (aNorthLat1 > singleLat) && (singleLat > aSouthLat1);
    }

    /**
     * See if the two longitude ranges overlap, no matter what their wrapping is.
     * @param aWestLon = first range west edge
     * @param aEastLon = first range east edge
     * @param bWestLon = second range west edge
     * @param bEastLon = second range east edge
     * @return whether or not they overlap
     */
    public static boolean LonOverlap (double aWestLon, double aEastLon, double bWestLon, double bEastLon)
    {
        // make sure all values in range -180.0..+179.999999999
        double aWestLon1 = Lib.Westmost (aWestLon, aEastLon);
        double aEastLon1 = Lib.Eastmost (aWestLon, aEastLon);
        double bWestLon1 = Lib.Westmost (bWestLon, bEastLon);
        double bEastLon1 = Lib.Eastmost (bWestLon, bEastLon);

        // set up secondary segments that hold same segments
        double aWestLon2 = aWestLon1;
        double aEastLon2 = aEastLon1;
        double bWestLon2 = bWestLon1;
        double bEastLon2 = bEastLon1;

        // if a segment wraps, split it into two segments
        if (aEastLon1 < aWestLon1) {
            aWestLon2 = -180.0;
            aEastLon1 =  180.0;
        }

        // likewise with b segment
        if (bEastLon1 < bWestLon1) {
            bWestLon2 = -180.0;
            bEastLon1 =  180.0;
        }

        // all {a,b}East{1,2} are .gt. corresponding {a,b}West{1.2}
        // so now we can test for direct overlap
        return  ((aEastLon1 > bWestLon1) && (bEastLon1 > aWestLon1)) ||  // a1,b1 overlap
                ((aEastLon1 > bWestLon2) && (bEastLon2 > aWestLon1)) ||  // a1,b2 overlap
                ((aEastLon2 > bWestLon1) && (bEastLon1 > aWestLon2)) ||  // a2,b1 overlap
                ((aEastLon2 > bWestLon2) && (bEastLon2 > aWestLon2));    // a2,b2 overlap
    }

    public static boolean LonOverlap (double aWestLon, double aEastLon, double singleLon)
    {
        // make sure all values in range -180.0..+179.999999999
        double aWestLon1  = Westmost (aWestLon, aEastLon);
        double aEastLon1  = Eastmost (aWestLon, aEastLon);
        double singleLon1 = NormalLon (singleLon);

        // set up secondary segment that holds same segment
        double aWestLon2 = aWestLon1;
        double aEastLon2 = aEastLon1;

        // if a segment wraps, split it into two segments
        if (aEastLon1 < aWestLon1) {
            aWestLon2 = -180.0;
            aEastLon1 =  180.0;
        }

        // all aEast{1,2} are .gt. corresponding aWest{1.2}
        // so now we can test for direct overlap
        return  ((aEastLon1 > singleLon1) && (singleLon1 > aWestLon1)) ||
                ((aEastLon2 > singleLon1) && (singleLon1 > aWestLon2));
    }

    /**
     * Compute great-circle distance between two lat/lon co-ordinates
     * @return distance between two points (in nm)
     */
    public static double LatLonDist (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        return Math.toDegrees (LatLonDist_rad (srcLat, srcLon, dstLat, dstLon)) * NMPerDeg;
    }
    public static double LatLonDist_rad (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_distance
        double sLat = Math.toRadians (srcLat);
        double sLon = Math.toRadians (srcLon);
        double fLat = Math.toRadians (dstLat);
        double fLon = Math.toRadians (dstLon);
        double dLon = fLon - sLon;
        double t1   = Sq (Math.cos (fLat) * Math.sin (dLon));
        double t2   = Sq (Math.cos (sLat) * Math.sin (fLat) - Math.sin (sLat) * Math.cos (fLat) * Math.cos (dLon));
        double t3   = Math.sin (sLat) * Math.sin (fLat);
        double t4   = Math.cos (sLat) * Math.cos (fLat) * Math.cos (dLon);
        return Math.atan2 (Math.sqrt (t1 + t2), t3 + t4);
    }

    private static double Sq (double x) { return x*x; }

    /**
     * Compute great-circle true course from one lat/lon to another lat/lon
     * @return true course (in degrees) at source point (-180..+179.999999)
     */
    public static double LatLonTC (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        return Math.toDegrees (LatLonTC_rad (srcLat, srcLon, dstLat, dstLon));
    }
    public static double LatLonTC_rad (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_navigation
        double sLat = Math.toRadians (srcLat);
        double sLon = Math.toRadians (srcLon);
        double fLat = Math.toRadians (dstLat);
        double fLon = Math.toRadians (dstLon);
        double dLon = fLon - sLon;
        double t1 = Math.cos (sLat) * Math.tan (fLat);
        double t2 = Math.sin (sLat) * Math.cos (dLon);
        return Math.atan2 (Math.sin (dLon), t1 - t2);
    }

    /**
     * Compute new lat/lon given old lat/lon, heading (degrees), distance (nautical miles)
     * http://stackoverflow.com/questions/7222382/get-lat-long-given-current-point-distance-and-bearing
     */
    public static double LatHdgDist2Lat (double latdeg, double hdgdeg, double distnm)
    {
        double distrad = Math.toRadians (distnm / NMPerDeg);
        double latrad  = Math.toRadians (latdeg);
        double hdgrad  = Math.toRadians (hdgdeg);

        latrad = Math.asin (Math.sin (latrad) * Math.cos (distrad) + Math.cos (latrad) * Math.sin (distrad) * Math.cos (hdgrad));
        return Math.toDegrees (latrad);
    }
    public static double LatLonHdgDist2Lon (double latdeg, double londeg, double hdgdeg, double distnm)
    {
        double distrad = Math.toRadians (distnm / NMPerDeg);
        double latrad  = Math.toRadians (latdeg);
        double hdgrad  = Math.toRadians (hdgdeg);

        double newlatrad = Math.asin (Math.sin (latrad) * Math.cos (distrad) + Math.cos (latrad) * Math.sin (distrad) * Math.cos (hdgrad));
        double lonrad = Math.atan2 (Math.sin (hdgrad) * Math.sin (distrad) * Math.cos (latrad), Math.cos (distrad) - Math.sin (latrad) * Math.sin (newlatrad));
        return NormalLon (Math.toDegrees (lonrad) + londeg);
    }

    /**
     * Given a course from beg to end and a current position cur, find what the on-course heading is at the
     * point on the course adjacent to the current position
     * @param beglatdeg = course beginning latitude
     * @param beglondeg = course beginning longitude
     * @param endlatdeg = course ending latitude
     * @param endlondeg = course ending longitude
     * @param curlatdeg = current position latitude
     * @param curlondeg = current position longitude
     * @return on-course true heading (degrees)
     */
    public static double GCOnCourseHdg (double beglatdeg, double beglondeg, double endlatdeg, double endlondeg, double curlatdeg, double curlondeg)
    {
        // convert arguments to radians
        // rotate to make endlon = 0
        double beglatrad = Math.toRadians (beglatdeg);
        double beglonrad = Math.toRadians (beglondeg - endlondeg);
        double endlatrad = Math.toRadians (endlatdeg);
        //// double endlonrad = Math.toRadians (endlondeg - endlondeg);
        double curlatrad = Math.toRadians (curlatdeg);
        double curlonrad = Math.toRadians (curlondeg - endlondeg);

        double beglatcos = Math.cos (beglatrad);
        double endlatcos = Math.cos (endlatrad);
        double curlatcos = Math.cos (curlatrad);

        // find points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        double begX = Math.cos (beglonrad) * beglatcos;
        double begY = Math.sin (beglonrad) * beglatcos;
        double begZ = Math.sin (beglatrad);
        //noinspection UnnecessaryLocalVariable
        double endX = endlatcos;  //// Math.cos (endlonrad) * endlatcos;
        //// double endY = Math.sin (endlonrad) * endlatcos;
        double endZ = Math.sin (endlatrad);
        double curX = Math.cos (curlonrad) * curlatcos;
        double curY = Math.sin (curlonrad) * curlatcos;
        double curZ = Math.sin (curlatrad);

        // compute normal to plane containing course, ie, containing beg, end, origin = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        double courseNormalX = begY * endZ; //// begY * endZ - begZ * endY;
        double courseNormalY = begZ * endX - begX * endZ;
        double courseNormalZ = - begY * endX; //// begX * endY - begY * endX;

        // compute normal to plane containing that normal, cur and origin = cur cross courseNormal
        double currentNormalX = curY * courseNormalZ - curZ * courseNormalY;
        double currentNormalY = curZ * courseNormalX - curX * courseNormalZ;
        double currentNormalZ = curX * courseNormalY - curY * courseNormalX;

        // we now have two planes that are perpendicular, both passing through the origin
        // one plane contains the course arc from beg to end
        // the other plane contains the current position cur
        // find one of the two points where the planes intersect along the course line, ie, on the normalized sphere
        double intersectX = courseNormalY * currentNormalZ - courseNormalZ * currentNormalY;
        double intersectY = courseNormalZ * currentNormalX - courseNormalX * currentNormalZ;
        double intersectZ = courseNormalX * currentNormalY - courseNormalY * currentNormalX;
        double intersectM = Math.sqrt (intersectX * intersectX + intersectY * intersectY + intersectZ * intersectZ);

        // normal to plane from intersection to north pole (0,0,1)
        //noinspection UnnecessaryLocalVariable,SuspiciousNameCombination
        double norintnplX =   intersectY;
        double norintnplY = - intersectX;

        // dot product of course normal with normal to northpole gives cos of the heading at intersectXYZ to endXYZ
        double costheta = (courseNormalX * norintnplX + courseNormalY * norintnplY) * intersectM;

        // cross of course normal with normal to northpole gives vector pointing directly to (or away from) intersection point
        // its magnitude is the sin of the heading
        double towardintX = - courseNormalZ * norintnplY;
        double towardintY =   courseNormalZ * norintnplX;
        double towardintZ = courseNormalX * norintnplY - courseNormalY * norintnplX;

        // dot that with vector to intersection is the sin of the heading
        double sintheta = towardintX * intersectX + towardintY * intersectY + towardintZ * intersectZ;

        return Math.toDegrees (Math.atan2 (sintheta, costheta));
    }

    /**
     * Given a course from beg to end and a current position cur, find out how many miles a given
     * current point is off course
     * @param beglatdeg = course beginning latitude
     * @param beglondeg = course beginning longitude
     * @param endlatdeg = course ending latitude
     * @param endlondeg = course ending longitude
     * @param curlatdeg = current position latitude
     * @param curlondeg = current position longitude
     * @return off-course distance (nm) (pos: need to turn right; neg: need to turn left)
     */
    public static double GCOffCourseDist (double beglatdeg, double beglondeg, double endlatdeg, double endlondeg, double curlatdeg, double curlondeg)
    {
        // http://williams.best.vwh.net/avform.htm Cross track error
        double crs_AB  = LatLonTC_rad   (beglatdeg, beglondeg, endlatdeg, endlondeg);
        double crs_AD  = LatLonTC_rad   (beglatdeg, beglondeg, curlatdeg, curlondeg);
        double dist_AD = LatLonDist_rad (beglatdeg, beglondeg, curlatdeg, curlondeg);
        double xtd     = Math.asin (Math.sin (dist_AD) * Math.sin (crs_AD - crs_AB));

        return Math.toDegrees (xtd) * NMPerDeg;
    }

    /**
     * Given two points that define one course and a point and heading that define another course,
     * return point where the second course intersects with the first.
     * @return true iff intersection point is ahead of p2 on the given heading
     */
    public static boolean GCIntersect (double lat1a, double lon1a, double lat1b, double lon1b,
                                       double lat2, double lon2, double hdg2, LatLon out)
    {
        // convert arguments to radians
        double lat1arad = Math.toRadians (lat1a);
        double lon1arad = Math.toRadians (lon1a);
        double lat1brad = Math.toRadians (lat1b);
        double lon1brad = Math.toRadians (lon1b);
        double lat2arad = Math.toRadians (lat2);
        double lon2arad = Math.toRadians (lon2);
        double hdg2rad  = Math.toRadians (hdg2);

        // make a 2nd point on the second course
        // http://stackoverflow.com/questions/2954337/great-circle-rhumb-line-intersection
        double lat2brad = Math.asin (Math.cos (lat2arad) * Math.cos (hdg2rad));
        double lon2brad = Math.atan2 (Math.sin (hdg2rad) * Math.cos (lat2arad),
                -Math.sin (lat2arad) * Math.sin (lat2brad)) + lon2arad;

        // find points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        double x1a = Math.cos (lon1arad) * Math.cos (lat1arad);
        double y1a = Math.sin (lon1arad) * Math.cos (lat1arad);
        double z1a = Math.sin (lat1arad);
        double x1b = Math.cos (lon1brad) * Math.cos (lat1brad);
        double y1b = Math.sin (lon1brad) * Math.cos (lat1brad);
        double z1b = Math.sin (lat1brad);
        double x2a = Math.cos (lon2arad) * Math.cos (lat2arad);
        double y2a = Math.sin (lon2arad) * Math.cos (lat2arad);
        double z2a = Math.sin (lat2arad);
        double x2b = Math.cos (lon2brad) * Math.cos (lat2brad);
        double y2b = Math.sin (lon2brad) * Math.cos (lat2brad);
        double z2b = Math.sin (lat2brad);

        // compute normals to planes containing the courses
        //   normal = point_a cross point_b
        double x1n = y1a * z1b - z1a * y1b;
        double y1n = z1a * x1b - x1a * z1b;
        double z1n = x1a * y1b - y1a * x1b;
        double x2n = y2a * z2b - z2a * y2b;
        double y2n = z2a * x2b - x2a * z2b;
        double z2n = x2a * y2b - y2a * x2b;

        // line of intersection is cross product of those two
        double xint = y1n * z2n - z1n * y2n;
        double yint = z1n * x2n - x1n * z2n;
        double zint = x1n * y2n - y1n * x2n;

        // get lat/lon at one end of the line
        double lata = Math.toDegrees (Math.atan2 (zint, Math.hypot (xint, yint)));
        double lona = Math.toDegrees (Math.atan2 (yint, xint));

        // get lat/lon at other end of line
        double latb = -lata;
        double lonb = NormalLon (lona + 180.0);

        // return whichever is closest to original given p2
        double dista = LatLonDist (lat2, lon2, lata, lona);
        double distb = LatLonDist (lat2, lon2, latb, lonb);

        if (dista < distb) {
            out.lat = lata;
            out.lon = lona;
        } else {
            out.lat = latb;
            out.lon = lonb;
        }

        // return whether point is ahead (true) or behind (false)
        double tc = LatLonTC (lat2, lon2, out.lat, out.lon);
        double tcdiff = Math.abs (tc - hdg2);
        while (tcdiff >= 360.0) tcdiff -= 360.0;
        if (tcdiff > 180.0) tcdiff = 360.0 - tcdiff;
        return tcdiff < 90.0;
    }

    /**
     * Compute magnetic variation at a given point.
     * @param lat = latitude of variation (degrees)
     * @param lon = longitude of variation (degrees)
     * @param alt = altitude (metres MSL)
     * @return amount to add to a true course to get a magnetic course (degrees)
     *         ie, >0 on east coast, <0 on west coast
     */
    public static double MagVariation (double lat, double lon, double alt)
    {
        return MagVariation (lat, lon, alt, System.currentTimeMillis ());
    }
    public static double MagVariation (double lat, double lon, double alt, long time)
    {
        GeomagneticField gmf = new GeomagneticField ((float) lat, (float) lon, (float) alt, time);
        return - gmf.getDeclination();
    }

    /**
     * Perform matrix multiply:  pr[r][c] = lh[r][c] * rh[r][c]
     */
    @SuppressWarnings("unused")
    public static void MatMul (double[][] pr, double[][] lh, double[][] rh)
    {
        int prRows = pr.length;
        int prCols = pr[0].length;
        int lhRows = lh.length;
        int lhCols = lh[0].length;
        int rhRows = rh.length;
        int rhCols = rh[0].length;

        if (prRows != lhRows) throw new RuntimeException ();
        if (prCols != rhCols) throw new RuntimeException ();
        if (lhCols != rhRows) throw new RuntimeException ();

        for (int i = 0; i < prRows; i ++) {
            double[] pr_i_ = pr[i];
            double[] lh_i_ = lh[i];
            for (int j = 0; j < prCols; j ++) {
                double sum = 0;
                for (int k = 0; k < lhCols; k ++) {
                    sum += lh_i_[k] * rh[k][j];
                }
                pr_i_[j] = sum;
            }
        }
    }

    /**
     * Row reduce the given matrix.
     */
    public static void RowReduce (double[][] T)
    {
        double pivot;
        int trows = T.length;
        int tcols = T[0].length;

        for (int row = 0; row < trows; row ++) {
            double[] T_row_ = T[row];

            /*
             * Make this row's major diagonal colum one by
             * swapping it with a row below that has the
             * largest value in this row's major diagonal
             * column, then dividing the row by that number.
             */
            pivot = T_row_[row];
            int bestRow = row;
            for (int swapRow = row; ++ swapRow < trows;) {
                double swapPivot = T[swapRow][row];
                if (Math.abs (pivot) < Math.abs (swapPivot)) {
                    pivot   = swapPivot;
                    bestRow = swapRow;
                }
            }
            if (pivot == 0.0) throw new ArithmeticException ("matrix not invertible");
            if (bestRow != row) {
                double[] tmp = T_row_;
                T[row] = T_row_ = T[bestRow];
                T[bestRow] = tmp;
            }
            if (pivot != 1.0) {
                for (int col = row; col < tcols; col ++) {
                    T_row_[col] /= pivot;
                }
            }

            /*
             * Subtract this row from all below it such that we zero out
             * this row's major diagonal column in all rows below.
             */
            for (int rr = row; ++ rr < trows;) {
                double[] T_rr_ = T[rr];
                pivot = T_rr_[row];
                if (pivot != 0.0) {
                    for (int cc = row; cc < tcols; cc ++) {
                        T_rr_[cc] -= pivot * T_row_[cc];
                    }
                }
            }
        }

        for (int row = trows; -- row >= 0;) {
            double[] T_row_ = T[row];
            for (int rr = row; -- rr >= 0;) {
                double[] T_rr_ = T[rr];
                pivot = T_rr_[row];
                if (pivot != 0.0) {
                    for (int cc = row; cc < tcols; cc ++) {
                        T_rr_[cc] -= pivot * T_row_[cc];
                    }
                }
            }
        }
    }

    /**
     * Find intersection of line A and line B.
     */
    public static double lineIntersectX (double ax0, double ay0, double ax1, double ay1, double bx0, double by0, double bx1, double by1)
    {
        // (y - y0) / (x - x0) = (y1 - y0) / (x1 - x0)
        // (y - y0) = (y1 - y0) / (x1 - x0) * (x - x0)
        // y = (y1 - y0) / (x1 - x0) * (x - x0) + y0

        // (ay1 - ay0) / (ax1 - ax0) * (x - ax0) + ay0 = (by1 - by0) / (bx1 - bx0) * (x - bx0) + by0
        // (ay1 - ay0) / (ax1 - ax0) * x - (ay1 - ay0) / (ax1 - ax0) * ax0 + ay0 = (by1 - by0) / (bx1 - bx0) * x - (by1 - by0) / (bx1 - bx0) * bx0 + by0
        // [(ay1 - ay0) / (ax1 - ax0) - (by1 - by0) / (bx1 - bx0)] * x = (ay1 - ay0) / (ax1 - ax0) * ax0 - (by1 - by0) / (bx1 - bx0) * bx0 + by0 - ay0
        // [(ay1 - ay0) - (by1 - by0) / (bx1 - bx0) * (ax1 - ax0)] * x = (ay1 - ay0) * ax0 - (by1 - by0) / (bx1 - bx0) * (ax1 - ax0) * bx0 + (by0 - ay0) * (ax1 - ax0)
        // [(ay1 - ay0) * (bx1 - bx0) - (by1 - by0) * (ax1 - ax0)] * x = (ay1 - ay0) * (bx1 - bx0) * ax0 - (by1 - by0) * (ax1 - ax0) * bx0 + (by0 - ay0) * (ax1 - ax0) * (bx1 - bx0)
        // x = [(ay1 - ay0) * (bx1 - bx0) * ax0 - (by1 - by0) * (ax1 - ax0) * bx0 + (by0 - ay0) * (ax1 - ax0) * (bx1 - bx0)] / [(ay1 - ay0) * (bx1 - bx0) - (by1 - by0) * (ax1 - ax0)]

        double ax1_ax0 = ax1 - ax0;
        double ay1_ay0 = ay1 - ay0;
        double bx1_bx0 = bx1 - bx0;
        double by1_by0 = by1 - by0;
        return (ay1_ay0 * bx1_bx0 * ax0 - by1_by0 * ax1_ax0 * bx0 + (by0 - ay0) * ax1_ax0 * bx1_bx0) / (ay1_ay0 * bx1_bx0 - by1_by0 * ax1_ax0);
    }
    public static double lineIntersectY (double ax0, double ay0, double ax1, double ay1, double bx0, double by0, double bx1, double by1)
    {
        double ax1_ax0 = ax1 - ax0;
        double ay1_ay0 = ay1 - ay0;
        double bx1_bx0 = bx1 - bx0;
        double by1_by0 = by1 - by0;
        return (ax1_ax0 * by1_by0 * ay0 - bx1_bx0 * ay1_ay0 * by0 + (bx0 - ax0) * ay1_ay0 * by1_by0) / (ax1_ax0 * by1_by0 - bx1_bx0 * ay1_ay0);
    }

    /**
     * Find center of circle of radius r what is tangent to
     * line segments p1p0 and p2p0.
     */
    public static void circleTangentToTwoLineSegs (double x1, double y1, double x0, double y0, double x2, double y2, double r, PointD pt)
    {
        final double[] v = new double[12];
        final int VY = 0;
        final int VX = 4;
        final int VD = 8;

        // make two endpoints relative to common point to simplify calculations
        // this makes each segment have an endpoint at the origin
        x1 -= x0;
        y1 -= y0;
        x2 -= x0;
        y2 -= y0;

        // convert p1p0 segment to form a1*x + b1*y = 0
        //  y * x1 = x * y1
        //  y * x1 - x * y1 = 0
        double a1 = - y1;
        double b1 = x1;

        // make sqrt (a1*a1 + b1*b1) = 1
        double d1 = Math.hypot (a1, b1);
        a1      /= d1;
        b1      /= d1;

        // likewise with p2p0
        double a2 = - y2;
        double b2 = x2;
        double d2 = Math.hypot (a2, b2);
        a2      /= d2;
        b2      /= d2;

        // distance of point x,y from line a,b: |ax + by|
        // two equations, two unknowns x,y:
        //  +- r = a1*x + b1*y   +- r = a2*x + b2*y
        //  +- r1 - b1*y = a1*x
        //  (+- r1 - b1*y) / a1 = x = (+- r2 - b2*y) / a2
        //  (+- r1 - b1*y) * a2 = (+- r2 - b2*y) * a1
        //  +- r1 * a2 - b1*y * a2 = +- r2 * a1 - b2*y * a1
        //  b2*y * a1 - b1*y * a2 = -+ r1 * a2 +- r2 * a1
        //  y * (b2 * a1 - b1 * a2) = -+ r1 * a2 +- r2 * a1
        //  y = (-+ r1 * a2 +- r2 * a1) / (b2 * a1 - b1 * a2)
        //  y = (-+ a2 +- a1) * r / (b2 * a1 - b1 * a2)
        double q = r / (b2 * a1 - b1 * a2);
        //noinspection PointlessArithmeticExpression
        v[VY+0] = (  a2 + a1) * q;
        v[VY+1] = (  a2 - a1) * q;
        v[VY+2] = (- a2 + a1) * q;
        v[VY+3] = (- a2 - a1) * q;

        // similar for matching x's
        // the q is same as for y except negative
        //noinspection PointlessArithmeticExpression
        v[VX+0] = (- b2 - b1) * q;
        v[VX+1] = (- b2 + b1) * q;
        v[VX+2] = (  b2 - b1) * q;
        v[VX+3] = (  b2 + b1) * q;

        // pick two points closest to p2
        int j = 0;
        for (int i = 0; i < 4; i ++) {
            v[VD+i] = Sq (v[VX+i] - x2) + Sq (v[VY+i] - y2);
            if (v[VD+j] > v[VD+i]) j = i;
        }
        int k = j ^ 1;
        for (int i = ~ j & 2;;) {
            if (v[VD+k] > v[VD+i]) k = i;
            if ((++ i & 1) == 0) break;
        }

        // pick which of those two is closest to p1
        double dj = Sq (v[VX+j] - x1) + Sq (v[VY+j] - y1);
        double dk = Sq (v[VX+k] - x1) + Sq (v[VY+k] - y1);
        if (dj > dk) j = k;

        // return selected point relocated back into position
        pt.x = v[VX+j] + x0;
        pt.y = v[VY+j] + y0;
    }

    /**
     * Distance of a point from an arc
     * @param centx = center of arc
     * @param centy = center of arc
     * @param radius = arc radius
     * @param start = start of arc (degrees clockwise from due EAST)
     * @param sweep = length of arc (degrees clockwise from start)
     * @param ptx = point to get distance of
     * @param pty = point to get distance of
     * @return distance of point from the arc
     */
    public static double distOfPtFromArc (double centx, double centy, double radius, double start, double sweep, double ptx, double pty)
    {
        if (sweep < 0.0) {
            start += sweep;
            sweep = -sweep;
        }
        double end = start + sweep;

        // make point relative to arc center
        ptx -= centx;
        pty -= centy;

        // see if point is within arc wedge
        // if so, distance is distance difference along radial
        double point_radial_from_center = Math.toDegrees (Math.atan2 (pty, ptx));
        while (point_radial_from_center < start) point_radial_from_center += 360.0;
        while (point_radial_from_center - 360.0 >= start) point_radial_from_center -= 360.0;
        if (point_radial_from_center <= end) {
            double point_dist_from_center = Math.hypot (ptx, pty);
            return Math.abs (point_dist_from_center - radius);
        }

        // get distance of point from each arc endpoint
        double start_x =   Mathf.cosdeg (start) * radius;
        double start_y = - Mathf.sindeg (start) * radius;
        double point_dist_from_start = Math.hypot (ptx - start_x, pty - start_y);
        double end_x   =   Mathf.cosdeg (end) * radius;
        double end_y   = - Mathf.sindeg (end) * radius;
        double point_dist_from_end   = Math.hypot (ptx - end_x,   pty - end_y);

        // result is the minimum of those two distances
        return Math.min (point_dist_from_start, point_dist_from_end);
    }

    /**
     * Get distance from given point to given line segment.
     * @param x0,y0 = point to check for
     * @param x1,y1/x2,y2 = line segment
     * @return how far away from line segment point is
     */
    public static double distToLineSeg (double x0, double y0, double x1, double y1, double x2, double y2)
    {
        double dx = x2 - x1;
        double dy = y2 - y1;

        double r;
        if (Math.abs (dx) > Math.abs (dy)) {

            // primarily horizontal

            double mhor = dy / dx;

            // calculate intersection point

            // (y - y1) / (x - x1) = mhor  =>  y - y1 = (x - x1) * mhor  =>  y = (x - x1) * mhor + y1
            // (x0 - x) / (y - y0) = mhor  =>  y - y0 = (x0 - x) / mhor  =>  y = (x0 - x) / mhor + y0

            // (x - x1) * mhor + y1 = (x0 - x) / mhor + y0
            // (x - x1) * mhor^2 + y1 * mhor = (x0 - x) + y0 * mhor
            // x * mhor^2 - x1 * mhor^2 + y1 * mhor = x0 - x + y0 * mhor
            // x * (mhor^2 + 1) - x1 * mhor^2 + y1 * mhor = x0 + y0 * mhor
            // x * (mhor^2 + 1) = x0 + y0 * mhor + x1 * mhor^2 - y1 * mhor
            // x * (mhor^2 + 1) = x1 * mhor^2 + y0 * mhor - y1 * mhor + x0
            // x * (mhor^2 + 1) = x1 * mhor^2 + (y0 - y1) * mhor + x0

            double x = (x1 * mhor * mhor + (y0 - y1) * mhor + x0) / (mhor * mhor + 1);

            // where intercept is on line relative to P1 and P2
            // r=0.0: right on P1; r=1.0: right on P2

            r = (x - x1) / dx;
        } else {

            // primarily vertical

            double mver = dx / dy;

            // calculate intersection point

            // (x - x1) / (y - y1) = mver  =>  x - x1 = (y - y1) * mver  =>  x = (y - y1) * mver + x1
            // (y0 - y) / (x - x0) = mver  =>  x - x0 = (y0 - y) / mver  =>  x = (y0 - y) / mver + x0

            // (y - y1) * mver + x1 = (y0 - y) / mver + x0
            // (y - y1) * mver^2 + x1 * mver = (y0 - y) + x0 * mver
            // y * mver^2 - y1 * mver^2 + x1 * mver = y0 - y + x0 * mver
            // y * (mver^2 + 1) - y1 * mver^2 + x1 * mver = y0 + x0 * mver
            // y * (mver^2 + 1) = y0 + x0 * mver + y1 * mver^2 - x1 * mver
            // y * (mver^2 + 1) = y1 * mver^2 + x0 * mver - x1 * mver + y0
            // y * (mver^2 + 1) = y1 * mver^2 + (x0 - x1) * mver + y0

            double y = (y1 * mver * mver + (x0 - x1) * mver + y0) / (mver * mver + 1);

            // where intercept is on line relative to P1 and P2
            // r=0.0: right on P1; r=1.0: right on P2

            r = (y - y1) / dy;
        }

        if (r <= 0.0) return Math.hypot (x0 - x1, y0 - y1);
        if (r >= 1.0) return Math.hypot (x0 - x2, y0 - y2);

        // https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line
        return Math.abs (dy * x0 - dx * y0 + x2 * y1 - y2 * x1) / Math.hypot (dy, dx);
    }

    /**
     * Find where a line intersects a circle.
     */
    public static boolean lineIntersectCircle (double x0, double y0, double x1, double y1,
                                               double xc, double yc, double r, PointD pp, PointD pm)
    {
        // make line relative to circle center
        x0 -= xc;
        y0 -= yc;
        x1 -= xc;
        y1 -= yc;

        // get deltas for line
        double dx = x1 - x0;
        double dy = y1 - y0;

        double xp, yp, xm, ym;
        if (Math.abs (dy) < Math.abs (dx)) {
            // line primarily horizontal

            double m  = dy / dx;

            // line: (x - x0) * dy = dy * (y - y0)
            //       y = m * (x - x0) + y0

            // circle: x**2 + y**2 = r**2

            // x**2 + [m * (x - x0) + y0]**2 = r**2
            // x**2 + [m * (x - x0)]**2 + y0**2 + 2 * m * (x - x0) * y0 = r**2
            // x**2 + m**2 * (x - x0)**2 + y0**2 + 2 * m * x * y0 - 2 * m * x0 * y0 = r**2
            // x**2 + m**2 * [x**2 - 2*x*x0 + x0**2] + y0**2 + 2 * m * x * y0 - 2 * m * x0 * y0 = r**2
            // [m**2 + 1]*x**2 + [-m**2*2*x0 + 2*m*y0]*x + m**2 * x0**2 + y0**2 - 2 * m * x0 * y0 - r**2 = 0

            //  a = m**2 + 1
            //  b = 2*m*y0 - m**2*2*x0
            //  c = m**2 * x0**2 + y0**2 - 2*m*x0*y0 - r**2

            double a = m * m + 1;
            double b = 2 * m * y0 - m * m * 2 * x0;
            double c = m * m * x0 * x0 + y0 * y0 - 2 * m * x0 * y0 - r * r;
            double d = b * b - 4 * a * c;
            if (d < 0.0) return false;
            xp = (-b + Math.sqrt (d)) / 2 / a;
            xm = (-b - Math.sqrt (d)) / 2 / a;
            yp = m * (xp - x0) + y0;
            ym = m * (xm - x0) + y0;
        } else {
            // line primarily vertical

            double m = dx / dy;
            double a = m * m + 1;
            double b = 2 * m * x0 - m * m * 2 * y0;
            double c = m * m * y0 * y0 + x0 * x0 - 2 * m * y0 * x0 - r * r;
            double d = b * b - 4 * a * c;
            if (d < 0.0) return false;
            yp = (-b + Math.sqrt (d)) / 2 / a;
            ym = (-b - Math.sqrt (d)) / 2 / a;
            xp = m * (yp - y0) + x0;
            xm = m * (ym - y0) + x0;
        }

        // return values relocated back
        pp.x = xp + xc;
        pp.y = yp + yc;
        pm.x = xm + xc;
        pm.y = ym + yc;
        return true;
    }

    /**
     * Convert a value to corresponding string.
     * @param val = integer version of value
     * @param dpt = number of decimal places it contains
     * @return string to put in editing box
     */
    public static String ValToString (int val, int dpt)
    {
        if (val == 0) return "0";  // happens a lot
        StringBuilder sb = new StringBuilder (12);
        sb.append (val);
        if (dpt > 0) {
            int neg = (val < 0) ? 1 : 0;
            int len = sb.length ();
            while (len - neg <= dpt) {
                sb.insert (neg, '0');
                len ++;
            }
            sb.insert (len - dpt, '.');
            while (sb.charAt (len) == '0') {
                sb.deleteCharAt (len --);
            }
            if (sb.charAt (len) == '.') {
                sb.deleteCharAt (len);
            }
        }
        return sb.toString ();
    }

    /**
     * Convert a nm distance to displayable string.
     */
    public static String DistString (double dist, boolean sm)
    {
        if (sm) dist *= SMPerNM;
        String diststr;
        if (dist < 100) {
            int dist100ths = (int)(dist * 100.0 + 0.5);
            int distwhole  = dist100ths / 100;
            dist100ths %= 100;
            diststr = distwhole + "." + Integer.toString (dist100ths + 100).substring (1);
        } else if (dist < 1000) {
            int dist10ths = (int)(dist * 10.0 + 0.5);
            int distwhole = dist10ths / 10;
            dist10ths %= 10;
            diststr = distwhole + "." + dist10ths;
        } else {
            int distwhole = (int)(dist + 0.5);
            diststr = Integer.toString (distwhole);
        }
        return diststr + (sm ? " sm" : " nm");
    }

    /**
     * Convert a kt speed to displayable string.
     */
    public static String SpeedString (double speed, boolean mph)
    {
        String suffix = " kts";
        if (mph) {
            speed *= SMPerNM;
            suffix = " mph";
        }
        if (speed >= 99.5) {
            speed += 0.5;
            return (int)speed + suffix;
        }
        speed += 0.05;
        int speedint = (int)speed;
        speed -= speedint;
        speed *= 10;
        int speeddec = (int)speed;
        String speedstr = speedint + "." + speeddec + suffix;
        if (speedstr.equals ("1.0 kts")) speedstr = "1.0 kt";
        return speedstr;
    }

    /**
     * Rename a file, throwing an exception on error.
     */
    public static void RenameFile (String oldname, String newname)
            throws IOException
    {
        if (!new File (oldname).renameTo (new File (newname))) {
            throw new IOException ("error renaming " + oldname + " => " + newname);
        }
    }

    /**
     * Delete the given file.
     * If it is a directory, delete everything in it first.
     */
    public static void RecursiveDelete (File f)
    {
        if (f.isDirectory ()) {
            File[] fs = f.listFiles ();
            assert fs != null;
            for (File x : fs) RecursiveDelete (x);
        }
        Ignored (f.delete ());
    }

    /**
     * Read a line from the given input stream
     * @param is = stream to read from
     * @return string up to but not including \n
     */
    public static String ReadStreamLine (InputStream is)
            throws IOException
    {
        int c;
        StringBuilder s = new StringBuilder ();
        while ((c = is.read ()) != '\n') {
            if (c < 0) throw new IOException ("EOF reading stream");
            s.append ((char) c);
        }
        return s.toString ();
    }

    /**
     * Write a given number of bytes from the input stream to the file
     * @param is = stream to read bytes from
     * @param bytelen = number of bytes to copy
     * @param filename = name of file to write to
     */
    public static void WriteStreamToFile (InputStream is, int bytelen, String filename)
            throws IOException
    {
        Ignored (new File (filename.substring (0, filename.lastIndexOf ('/') + 1)).mkdirs ());
        FileOutputStream os = new FileOutputStream (filename);
        try {
            byte[] buf = new byte[4096];
            while (bytelen > 0) {
                int rc = buf.length;
                if (rc > bytelen) rc = bytelen;
                rc = is.read (buf, 0, rc);
                if (rc <= 0) throw new IOException ("EOF reading " + filename);
                os.write (buf, 0, rc);
                bytelen -= rc;
            }
        } finally {
            os.close ();
        }
    }

    /**
     * Double to string, strip trailing ".0" if any
     */
    public static String DoubleNTZ (double floatval)
    {
        return DoubleNTZ (floatval, 1);
    }
    public static String DoubleNTZ (double floatval, int decimals)
    {
        if (Double.isNaN (floatval)) return "NaN";
        double absvalue = Math.abs (floatval);
        if (absvalue >= 1.0E20) return Double.toString (floatval);
        int shiftedleft = 0;
        while (-- decimals >= 0) {
            double g = absvalue * 10.0;
            if (g >= 1.0E20) break;
            absvalue = g;
            shiftedleft ++;
        }
        long longval = Math.round (absvalue);
        while ((shiftedleft > 0) && (longval % 10 == 0)) {
            longval /= 10;
            -- shiftedleft;
        }
        char[] str = new char[24];
        int i = str.length;
        do {
            str[--i] = (char) (longval % 10 + '0');
            longval /= 10;
            if (-- shiftedleft == 0) str[--i] = '.';
        } while ((longval > 0) || (shiftedleft >= 0));
        if (floatval < 0) str[--i] = '-';
        return new String (str, i, str.length - i);
    }

    /**
     * Convert millisecond time to printable string.
     */
    public static String TimeStringUTC (long timems)
    {
        SimpleDateFormat sdf = utcfmt.get ();
        if (sdf == null) {
            sdf = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS 'UTC'''", Locale.US);
            sdf.setTimeZone (Lib.tzUtc);
            utcfmt.set (sdf);
        }
        return sdf.format (timems);
    }

    /**
     * Heading to string.
     */
    public static String Hdg2Str (double hdg)
    {
        int ihdg = (int) Math.round (hdg) % 360;
        while (ihdg <= 0) ihdg += 360;
        return Integer.toString (ihdg + 1000).substring (1) + '\u00B0';
    }

    /**
     * Dismiss a dialog box.
     */
    public static void dismiss (DialogInterface dialog)
    {
        if (dialog != null) {
            try {
                dialog.dismiss ();
            } catch (Exception e) {
                // got IllegalArgumentException in PhoneWindow$DecorView
                // ... not attached to window manager
                Log.w ("WairToNow", "exception dismissing dialog", e);
            }
        }
    }

    /**
     * Convert a byte array to hexadecimal string.
     */
    private final static String hexbytes = "0123456789abcdef";
    public static @NonNull String bytesToHex (@NonNull byte[] bytes)
    {
        int len = bytes.length;
        char[] hex = new char[len*2];
        for (int i = 0; i < len; i ++) {
            byte b = bytes[i];
            hex[i*2] = hexbytes.charAt ((b >> 4) & 15);
            hex[i*2+1] = hexbytes.charAt (b & 15);
        }
        return new String (hex);
    }

    /**
     * Handle 3-letter timezone names as well as full strings.
     */
    public static TimeZone getTimeZone (String tzname)
    {
        TimeZone lcltz = null;
        if (tzname != null) {
            switch (tzname) {
                case "EDT": {
                    lcltz = TimeZone.getTimeZone ("GMT-04:00");
                    break;
                }
                case "EST":
                case "CDT": {
                    lcltz = TimeZone.getTimeZone ("GMT-05:00");
                    break;
                }
                case "CST":
                case "MDT": {
                    lcltz = TimeZone.getTimeZone ("GMT-06:00");
                    break;
                }
                case "MST":
                case "PDT": {
                    lcltz = TimeZone.getTimeZone ("GMT-07:00");
                    break;
                }
                case "PST": {
                    lcltz = TimeZone.getTimeZone ("GMT-08:00");
                    break;
                }
                case "Guam": {
                    lcltz = TimeZone.getTimeZone ("Pacific/Guam");
                    break;
                }
                default: {
                    lcltz = TimeZone.getTimeZone (tzname);
                    break;
                }
            }
        }
        return lcltz;
    }

    // simplified time zone name
    public static String simpTZName (String tzname)
    {
        switch (tzname) {
            case "America/New_York": return "Eastern";
            case "America/Chicago": return "Central";
            case "America/Denver": return "Mountain";
            case "America/Los_Angeles": return "Pacific";
        }
        if (tzname.startsWith ("America/")) return tzname.substring (8);
        if (tzname.startsWith ("Pacific/")) return tzname.substring (8);
        return tzname;
    }

    /**
     * Various compiler warnings.
     */
    public static void Ignored () { }
    public static void Ignored (@SuppressWarnings("UnusedParameters") boolean x) { }
    public static void Ignored (@SuppressWarnings("UnusedParameters") double x) { }
    public static void Ignored (@SuppressWarnings("UnusedParameters") int x) { }
    public static void Ignored (@SuppressWarnings("UnusedParameters") long x) { }
}
