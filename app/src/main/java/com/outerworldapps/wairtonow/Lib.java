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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.GeomagneticField;
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

    // fine-tuned constants
    public static final double FtPerM    = 3.28084;
    public static final double KtPerMPS  = 1.94384;
    public static final double NMPerDeg  = 60.0;
    public static final double MMPerIn   = 25.4;    // millimetres per inch
    public static final double SMPerNM   = 1.15078;
    public static final int   MPerNM    = 1852;     // metres per naut mile

    private static ThreadLocal<SimpleDateFormat> utcfmt = new ThreadLocal<> ();
    private static Rect drawBoundedStringBounds = new Rect ();

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
            aEastLon2 = aEastLon1;
            aWestLon2 = -180.0;
            aEastLon1 =  180.0;
        }

        // likewise with b segment
        if (bEastLon1 < bWestLon1) {
            bEastLon2 = bEastLon1;
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
            aEastLon2 = aEastLon1;
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
        double sLat = srcLat / 180 * Math.PI;
        double sLon = srcLon / 180 * Math.PI;
        double fLat = dstLat / 180 * Math.PI;
        double fLon = dstLon / 180 * Math.PI;
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
        return LatLonTC_rad (srcLat, srcLon, dstLat, dstLon) * 180.0 / Math.PI;
    }
    public static double LatLonTC_rad (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_navigation
        double sLat = srcLat / 180 * Math.PI;
        double sLon = srcLon / 180 * Math.PI;
        double fLat = dstLat / 180 * Math.PI;
        double fLon = dstLon / 180 * Math.PI;
        double dLon = fLon - sLon;
        double t1 = Math.cos (sLat) * Math.tan (fLat);
        double t2 = Math.sin(sLat) * Math.cos(dLon);
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
     * Given a longitude along a course, what is corresponding latitude?
     * Used for plotting courses that are primarily east/west.
     * @return latitude in range -90 to +90
     */
    public static double GCLon2Lat (double beglatdeg, double beglondeg, double endlatdeg, double endlondeg, double londeg)
    {
        // code below assumes westbound course
        while (beglondeg < -180.0) beglondeg += 360.0;
        while (beglondeg >= 180.0) beglondeg -= 360.0;
        while (endlondeg < -180.0) endlondeg += 360.0;
        while (endlondeg >= 180.0) endlondeg -= 360.0;
        if (endlondeg < beglondeg) endlondeg += 360.0;
        if (endlondeg - beglondeg < 180.0) {
            double lat = beglatdeg;
            double lon = beglondeg;
            beglatdeg = endlatdeg;
            beglondeg = endlondeg;
            endlatdeg = lat;
            endlondeg = lon;
        }

        // convert arguments to radians
        double beglatrad = Math.toRadians (beglatdeg);
        double beglonrad = Math.toRadians (beglondeg);
        double endlatrad = Math.toRadians (endlatdeg);
        double endlonrad = Math.toRadians (endlondeg);
        double lonrad    = Math.toRadians (londeg);

        // find course beg and end points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        double begX = Math.cos (beglonrad) * Math.cos (beglatrad);
        double begY = Math.sin (beglonrad) * Math.cos (beglatrad);
        double begZ = Math.sin (beglatrad);
        double endX = Math.cos (endlonrad) * Math.cos (endlatrad);
        double endY = Math.sin (endlonrad) * Math.cos (endlatrad);
        double endZ = Math.sin (endlatrad);

        // compute normal to plane containing course = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        double norX = begY * endZ - begZ * endY;
        double norY = begZ * endX - begX * endZ;
        double norZ = begX * endY - begY * endX;

        // compute normal to plane containing the given line of longitude
        // note that the plane crosses through the center of the earth, ie, (0,0,0)
        // if longitude =   0 deg => longitude plane is y=0 plane => (0,-1,0)
        // if longitude =  90 deg => longitude plane is x=0 plane => (1,0,0)
        // if longitude = 180 deg => (0,1,0)
        // if longitude = -90 deg => (-1,0,0)
        // equation of plane containing requested longitude: lonX * x + lonY * y + lonZ * z = 0
        double lonX =   Math.sin (lonrad);
        double lonY = - Math.cos (lonrad);
        double lonZ = 0.0;

        // find intersection line = lon cross nor
        // note that the line passes through the center of the earth, ie, (0,0,0)
        // equation of line of intersection: t * (intX, intY, intZ)
        double intX = lonY * norZ - lonZ * norY;
        double intY = lonZ * norX - lonX * norZ;
        double intZ = lonX * norY - lonY * norX;
        double intR = Math.sqrt (intX * intX + intY * intY + intZ * intZ);

        // find corresponding latitude
        double latrad = Math.asin (intZ / intR);
        return Math.toDegrees (latrad);
    }

    /**
     * Given a latitude along a course, what is corresponding longitude?
     * Used for plotting courses that are primarily north/south.
     * Note that primarily east/west courses can have two solutions that we don't handle.
     * @return longitude in range -180 to +180
     */
    public static double GCLat2Lon (double beglatdeg, double beglondeg, double endlatdeg, double endlondeg, double latdeg)
    {
        // code below assumes southbound course
        if (endlatdeg > beglatdeg) {
            double lat = beglatdeg;
            double lon = beglondeg;
            beglatdeg = endlatdeg;
            beglondeg = endlondeg;
            endlatdeg = lat;
            endlondeg = lon;
        }

        // convert arguments to radians
        double beglatrad = Math.toRadians (beglatdeg);
        double beglonrad = Math.toRadians (beglondeg);
        double endlatrad = Math.toRadians (endlatdeg);
        double endlonrad = Math.toRadians (endlondeg);
        double latrad    = Math.toRadians (latdeg);

        // find course beg and end points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        double begX = Math.cos (beglonrad) * Math.cos (beglatrad);
        double begY = Math.sin (beglonrad) * Math.cos (beglatrad);
        double begZ = Math.sin (beglatrad);
        double endX = Math.cos (endlonrad) * Math.cos (endlatrad);
        double endY = Math.sin (endlonrad) * Math.cos (endlatrad);
        double endZ = Math.sin (endlatrad);

        // compute normal to plane containing course = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        double norX = begY * endZ - begZ * endY;
        double norY = begZ * endX - begX * endZ;
        double norZ = begX * endY - begY * endX;

        // find points on plane that are at the given latitude
        // equation of intersection line: norX * x + norY * Y + norZ * latZ = 0
        double latZ = Math.sin (latrad);

        // find the two points along that line that are on the normalized sphere
        // ie, those that intersect x**2 + y**2 + latZ**2 = 1

        //   norX * x + norY * y + norZ * latZ = 0  =>  norX * x = -(norY * y + norZ * latZ)
        //                                          =>  (norX * x)**2 = (norY * y + norZ * latZ)**2

        //   x**2 + y**2 + latZ**2 = 1.0  =>  x**2 = 1 - y**2 - latZ**2
        //                                =>  (norX * x)**2 = norX**2 * (1 - y**2 - latZ**2)

        //   (norY * y + norZ * latZ)**2 = norX**2 * (1 - y**2 - latZ**2)

        //   norY**2 * y**2 + norZ**2 * latZ**2 + 2 * norY * y * norZ * latZ = norX**2 - norX**2 * y**2 - norX**2 * latZ**2

        //   (norY**2 + norX**2) * y**2 + (2 * norY * norZ * latZ) * y + norZ**2 * latZ**2 + norX**2 * latZ**2 - norX**2 = 0
        //   (norY**2 + norX**2) * y**2 + (2 * norY * norZ * latZ) * y + norZ**2 * latZ**2 + norX**2 * (latZ**2 - 1) = 0

        double a  = norY * norY + norX * norX;
        double b  = 2 * norY * norZ * latZ;
        double c  = norZ * norZ * latZ * latZ + norX * norX * (latZ * latZ - 1);
        double d  = Math.sqrt (b * b - 4 * a * c);

        double y1 = (-b + d) / (2 * a);
        double y2 = (-b - d) / (2 * a);

        double x1_times_norX = -(norY * y1 + norZ * latZ);
        double x2_times_norX = -(norY * y2 + norZ * latZ);

        // find corresponding longitudes
        double londeg1 = Math.toDegrees (Math.atan2 (y1 * norX, x1_times_norX));
        double londeg2 = Math.toDegrees (Math.atan2 (y2 * norX, x2_times_norX));

        // find distances from beg->pt1->end and beg->pt2->end
        double dist1 = LatLonDist_rad (beglatdeg, beglondeg, latdeg, londeg1) +
                       LatLonDist_rad (latdeg, londeg1, endlatdeg, endlondeg);
        double dist2 = LatLonDist_rad (beglatdeg, beglondeg, latdeg, londeg2) +
                       LatLonDist_rad (latdeg, londeg2, endlatdeg, endlondeg);

        // use whichever one is the shortest
        return (dist1 < dist2) ? londeg1 : londeg2;
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
        double beglatrad = Math.toRadians (beglatdeg);
        double beglonrad = Math.toRadians (beglondeg);
        double endlatrad = Math.toRadians (endlatdeg);
        double endlonrad = Math.toRadians (endlondeg);
        double curlatrad = Math.toRadians (curlatdeg);
        double curlonrad = Math.toRadians (curlondeg);

        // find points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        double begX = Math.cos (beglonrad) * Math.cos (beglatrad);
        double begY = Math.sin (beglonrad) * Math.cos (beglatrad);
        double begZ = Math.sin (beglatrad);
        double endX = Math.cos (endlonrad) * Math.cos (endlatrad);
        double endY = Math.sin (endlonrad) * Math.cos (endlatrad);
        double endZ = Math.sin (endlatrad);
        double curX = Math.cos (curlonrad) * Math.cos (curlatrad);
        double curY = Math.sin (curlonrad) * Math.cos (curlatrad);
        double curZ = Math.sin (curlatrad);

        // compute normal to plane containing course, ie, containing beg, end, origin = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        double courseNormalX = begY * endZ - begZ * endY;
        double courseNormalY = begZ * endX - begX * endZ;
        double courseNormalZ = begX * endY - begY * endX;

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

        // find lat/lon of the intersection point
        double intersectLat = Math.toDegrees (Math.atan2 (intersectZ, Math.sqrt (intersectX * intersectX + intersectY * intersectY)));
        double intersectLon = Math.toDegrees (Math.atan2 (intersectY, intersectX));

        // find distance from intersection point to start and end points
        double distInt2End = LatLonDist_rad (intersectLat, intersectLon, endlatdeg, endlondeg);
        double distInt2Beg = LatLonDist_rad (intersectLat, intersectLon, beglatdeg, beglondeg);

        // if closer to start point, return true course from intersection point to end point
        if (distInt2End > distInt2Beg) {
            return LatLonTC (intersectLat, intersectLon, endlatdeg, endlondeg);
        }

        // but/and if closer to endpoint, return reciprocal of tc from intersection to start point
        double tc = LatLonTC (intersectLat, intersectLon, beglatdeg, beglondeg) + 180.0;
        if (tc >= 180.0) tc -= 360.0;
        return tc;
    }

    /**
     * Given a course from beg to end and a current poistion cur, find out how many miles a given
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
        GeomagneticField gmf = new GeomagneticField ((float) lat, (float) lon, (float) alt, System.currentTimeMillis());
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
        String str = Integer.toString (val);
        if (dpt > 0) {
            while (str.length () <= dpt) str = "0" + str;
            int len = str.length ();
            str = str.substring (0, len - dpt) + "." + str.substring (len - dpt);
            while (str.charAt (str.length () - 1) == '0') str = str.substring (0, str.length () -1);
            if (str.charAt (str.length () - 1) == '.') str = str.substring (0, str.length () - 1);
        }
        return str;
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
            diststr = Integer.toString (distwhole) + "." + Integer.toString (dist100ths + 100).substring (1);
        } else if (dist < 1000) {
            int dist10ths = (int)(dist * 10.0 + 0.5);
            int distwhole = dist10ths / 10;
            dist10ths %= 10;
            diststr = Integer.toString (distwhole) + "." + Integer.toString (dist10ths);
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
     * Read a line from the given input stream
     * @param is = stream to read from
     * @return string up to but not including \n
     */
    public static String ReadStreamLine (InputStream is)
            throws IOException
    {
        int c;
        String s = "";
        while ((c = is.read ()) != '\n') {
            if (c < 0) throw new IOException ("EOF reading stream");
            s += (char)c;
        }
        return s;
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
    public static String DoubleNTZ (double f)
    {
        String s = String.format (Locale.US, "%.1f", f);
        if (s.endsWith (".0")) s = s.substring (0, s.length () - 2);
        return s;
    }

    /**
     * Convert millisecond time to printable string.
     */
    public static String TimeStringUTC (long timems)
    {
        SimpleDateFormat sdf = utcfmt.get ();
        if (sdf == null) {
            sdf = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS 'UTC'''", Locale.US);
            sdf.setTimeZone (TimeZone.getTimeZone ("UTC"));
            utcfmt.set (sdf);
        }
        return sdf.format (timems);
    }

    /**
     * Draw a string and keep track of its bounds.
     * *** UI THREAD ONLY ***
     * @param canvas = canvas to draw string on
     * @param overallBounds = keeps track of bounds of several strings, start with 0,0,0,0
     * @param paint = paint used to draw string
     * @param cx = string's X center
     * @param ty = string's Y top
     * @param st = string
     */
    public static void DrawBoundedString (Canvas canvas, Rect overallBounds, Paint paint, int cx, int ty, String st)
    {
        if (st != null) {
            canvas.drawText (st, cx, ty, paint);

            if ((overallBounds.left == 0) && (overallBounds.right == 0)) {
                paint.getTextBounds (st, 0, st.length (), overallBounds);
                overallBounds.offset (cx - overallBounds.width () / 2, ty);
            } else {
                paint.getTextBounds (st, 0, st.length (), drawBoundedStringBounds);
                drawBoundedStringBounds.offset (cx - drawBoundedStringBounds.width () / 2, ty);
                overallBounds.union (drawBoundedStringBounds);
            }
        }
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
     * Various compiler warnings.
     */
    public static void Ignored () { }
    public static void Ignored (@SuppressWarnings("UnusedParameters") boolean x) { }
    public static void Ignored (@SuppressWarnings("UnusedParameters") double x) { }
    public static void Ignored (@SuppressWarnings("UnusedParameters") int x) { }
    public static void Ignored (@SuppressWarnings("UnusedParameters") long x) { }
}
