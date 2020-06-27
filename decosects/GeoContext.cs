
// Data files downloaded from WMM model
// http://www.ngdc.noaa.gov/geomag/models.shtml

/****************************************************************************/
/*                                                                          */
/*       Disclaimer: This C version of the Geomagnetic Field                */
/*       Modeling software is being supplied to aid programmers in          */
/*       integrating spherical harmonic field modeling calculations         */
/*       into their code. It is being distributed unoffically. The          */
/*       National Geophysical Data Center does not support it as            */
/*       a data product or guarantee it's correctness. The limited          */
/*       testing done on this code seems to indicate that the results       */
/*       obtained from this program are compariable to those obtained       */
/*                                                                          */
/****************************************************************************/
/*                                                                          */
/*     In regards to the disclaimer, to the best of my knowlege and quick   */
/*     testing, this program, generates numaric output which is withing .1  */
/*     degress of the current fortran version.  However, it *is* a program  */
/*     and most likely contains bugs.                                       */
/*                                              dio  6-6-96                 */
/*                                                                          */
/****************************************************************************/
/*                                                                          */
/*     This is version 3.01 of the source code but still represents the     */
/*     geomag30 executable.                                                 */
/*                                              dio 9-17-96                 */
/*                                                                          */
/****************************************************************************/
/*                                                                          */
/*     Bug fix- the range of dates error.  There was a difference in the    */
/*     output between the last value on a range of dates and the individual */
/*     value for the same date.  Lets make this version 3.02                */
/*                                              ljh 11-20-98                */
/*                                                                          */
/****************************************************************************/
/*      Program was modified so that it can accept year 2000 models         */
/*      This required that the number of blank spaces on  model header      */
/*      records be decreased from four to three                             */
/*                                                                          */
/*      This program calculates the geomagnetic field values from           */
/*      a spherical harmonic model.  Inputs required by the user are:       */
/*      a spherical harmonic model data file, coordinate preference,        */
/*      elevation, date/range-step, latitude, and longitude.                */
/*                                                                          */
/*         Spherical Harmonic                                               */
/*         Model Data File       :  Name of the data file containing the    */
/*                                  spherical harmonic coefficients of      */
/*                                  the chosen model.  The model and path   */
/*                                  must be less than PATH chars.           */
/*                                                                          */
/*         Coordinate Preference :  Geodetic (measured from                 */
/*                                  the surface of the earth),              */
/*                                  or geocentric (measured from the        */
/*                                  center of the earth).                   */
/*                                                                          */
/*         Elevation             :  Elevation above sea level in kilometers.*/
/*                                  if geocentric coordinate preference is  */
/*                                  used then the elevation must be in the  */
/*                                  range of 6370.20 km - 6971.20 km as     */
/*                                  measured from the center of the earth.  */
/*                                  Enter elevation in kilometers in the    */
/*                                  form xxxx.xx.                           */
/*                                                                          */
/*         Date                  :  Date, in decimal years, for which to    */
/*                                  calculate the values of the magnetic    */
/*                                  field.  The date must be within the     */
/*                                  limits of the model chosen.             */
/*                                                                          */
/*         Latitude              :  Entered in decimal degrees in the       */
/*                                  form xxx.xxx.  Positive for northern    */
/*                                  hemisphere, negative for the southern   */
/*                                  hemisphere.                             */
/*                                                                          */
/*         Longitude             :  Entered in decimal degrees in the       */
/*                                  form xxx.xxx.  Positive for eastern     */
/*                                  hemisphere, negative for the western    */
/*                                  hemisphere.                             */
/*                                                                          */
/****************************************************************************/
/*                                                                          */
/*      Subroutines called :  degrees_to_decimal,julday,getshc,interpsh,    */
/*                            extrapsh,shval3,dihf,safegets                 */
/*                                                                          */
/****************************************************************************/

using System;

public class GeoContext {
    private static double atan2(double y, double x) { return (double)Math.Atan2(y, x); }
    private static double cos(double x) { return (double)Math.Cos(x); }
    private static double log(double x) { return (double)Math.Log(x); }
    private static double pow(double x, double y) { return (double)Math.Pow(x, y); }
    private static double sin(double x) { return (double)Math.Sin(x); }
    private static double sqrt(double x) { return (double)Math.Sqrt(x); }

    private static double PI = (double)Math.PI;

    private static double NaN = Double.NaN;
    private static int MAXDEG = 13;
    private static int MAXCOEFF = MAXDEG*(MAXDEG+2)+1;

    private double[] gh1 = new double[MAXCOEFF];
    private double[] gh2 = new double[MAXCOEFF];
    private double[] gha = new double[MAXCOEFF];
    private double[] ghb = new double[MAXCOEFF];
    private double d;
    private double x, y, z;
    private double xtemp,ztemp;

    private static GeoContext singleton = new GeoContext ();

    /**
     * Get magnetic variation at a point in space and time
     * @param sdate = year
     * @param latitude = radians
     * @param longitude = radians
     * @param elev = kilometres msl
     * @return radians
     */
    public static double magnetic_variation(double sdate, double latitude, double longitude, double elev)
    {
        //synchronized (singleton) {
            int i;

            for (i = 0; i < GeoModel.GEO_MODELS.Length - 1; i++)
                if (sdate < GeoModel.GEO_MODELS[i].yrmax)
                    break;

            GeoModel pmodel0 = GeoModel.GEO_MODELS[i];
            GeoModel pmodel1 = (pmodel0.smax2 == 0) ? GeoModel.GEO_MODELS[i+1] : null;
            singleton.geomag(pmodel0, pmodel1, sdate, latitude, longitude, elev);

            return -singleton.d;
        //}
    }

    private void geomag(GeoModel pmodel0, GeoModel pmodel1, double sdate,
                        double latitude, double longitude, double elev)
    {
        int nmax;

        /** This will compute everything needed for 1 point in time. **/

        if (pmodel1 != null) {
            getshc(1, pmodel0.data, pmodel0.smax1, this.gh1);
            getshc(1, pmodel1.data, pmodel1.smax1, this.gh2);
                   interpsh(sdate, pmodel0.yrmin, pmodel0.smax1,
                                            pmodel1.yrmin, pmodel1.smax1, this.gha);
            nmax = interpsh(sdate+1, pmodel0.yrmin, pmodel0.smax1,
                                            pmodel1.yrmin, pmodel1.smax1, this.ghb);
        } else {
            getshc(1, pmodel0.data, pmodel0.smax1, this.gh1);
            getshc(0, pmodel0.data, pmodel0.smax2, this.gh2);
                   extrapsh(sdate, pmodel0.yrmin, pmodel0.smax1, pmodel0.smax2, this.gha);
            nmax = extrapsh(sdate+1, pmodel0.yrmin, pmodel0.smax1, pmodel0.smax2, this.ghb);
        }

        /* Do the first calculations */
        shval3(latitude, longitude, elev / 1000.0, nmax, 3);

        dihf3();
        shval3(latitude, longitude, elev / 1000.0, nmax, 4);

        /** Above will compute everything for 1 point in time.  **/
    }

    /****************************************************************************/
    /*                                                                          */
    /*                           Subroutine getshc                              */
    /*                                                                          */
    /****************************************************************************/
    /*                                                                          */
    /*     Reads spherical harmonic coefficients from the specified             */
    /*     model into an array.                                                 */
    /*                                                                          */
    /*     Input:                                                               */
    /*           iflag      - Flag for SV equal to ) or not equal to 0          */
    /*                        for designated read statements                    */
    /*           strec      - Starting record number to read from model         */
    /*           nmax_of_gh - Maximum degree and order of model                 */
    /*                                                                          */
    /*     Output:                                                              */
    /*           gh1 or 2   - Schmidt quasi-normal internal spherical           */
    /*                        harmonic coefficients                             */
    /*                                                                          */
    /*     FORTRAN                                                              */
    /*           Bill Flanagan                                                  */
    /*           NOAA CORPS, DESDIS, NGDC, 325 Broadway, Boulder CO.  80301     */
    /*                                                                          */
    /*     C                                                                    */
    /*           C. H. Shaffer                                                  */
    /*           Lockheed Missiles and Space Company, Sunnyvale CA              */
    /*           August 15, 1988                                                */
    /*                                                                          */
    /****************************************************************************/

    private static void getshc(int iflag, GeoData[] pdata, int nmax_of_gh, double[] pgh)
    {
        int ii,mm,nn,pdi;
        double g,hh;

        ii = 0;
        pdi = 0;

        for ( nn = 1; nn <= nmax_of_gh; ++nn) {
            for (mm = 0; mm <= nn; ++mm) {
                if (iflag == 1) {
                    g = pdata[pdi].x;
                    hh = pdata[pdi].y;
                }
                else {
                    g = pdata[pdi].z;
                    hh = pdata[pdi].t;
                }

                pdi++;

                ii++;
                pgh[ii] = g;

                if (mm != 0) {
                    ii++;
                    pgh[ii] = hh;
                }
            }
        }
    }

    /****************************************************************************/
    /*                                                                          */
    /*                           Subroutine extrapsh                            */
    /*                                                                          */
    /****************************************************************************/
    /*                                                                          */
    /*     Extrapolates linearly a spherical harmonic model with a              */
    /*     rate-of-change model.                                                */
    /*                                                                          */
    /*     Input:                                                               */
    /*           date     - date of resulting model (in decimal year)           */
    /*           dte1     - date of base model                                  */
    /*           nmax1    - maximum degree and order of base model              */
    /*           gh1      - Schmidt quasi-normal internal spherical             */
    /*                      harmonic coefficients of base model                 */
    /*           nmax2    - maximum degree and order of rate-of-change model    */
    /*           gh2      - Schmidt quasi-normal internal spherical             */
    /*                      harmonic coefficients of rate-of-change model       */
    /*                                                                          */
    /*     Output:                                                              */
    /*           gha or b - Schmidt quasi-normal internal spherical             */
    /*                    harmonic coefficients                                 */
    /*           nmax   - maximum degree and order of resulting model           */
    /*                                                                          */
    /*     FORTRAN                                                              */
    /*           A. Zunde                                                       */
    /*           USGS, MS 964, box 25046 Federal Center, Denver, CO.  80225     */
    /*                                                                          */
    /*     C                                                                    */
    /*           C. H. Shaffer                                                  */
    /*           Lockheed Missiles and Space Company, Sunnyvale CA              */
    /*           August 16, 1988                                                */
    /*                                                                          */
    /****************************************************************************/

    private int extrapsh(double date, double dte1, int nmax1,
                         int nmax2, double[] pgh)
    {
        int   nmax;
        int   k, l;
        int   ii;
        double factor;

        factor = date - dte1;
        if (nmax1 == nmax2) {
            k =  nmax1 * (nmax1 + 2);
            nmax = nmax1;
        }
        else if (nmax1 > nmax2) {
            k = nmax2 * (nmax2 + 2);
            l = nmax1 * (nmax1 + 2);

            for ( ii = k + 1; ii <= l; ++ii)
                pgh[ii] = this.gh1[ii];

            nmax = nmax1;
        }
        else {
            k = nmax1 * (nmax1 + 2);
            l = nmax2 * (nmax2 + 2);

            for ( ii = k + 1; ii <= l; ++ii)
                pgh[ii] = factor * this.gh2[ii];

            nmax = nmax2;
        }

        for ( ii = 1; ii <= k; ++ii)
            pgh[ii] = this.gh1[ii] + factor * this.gh2[ii];

         return nmax;
    }

    /****************************************************************************/
    /*                                                                          */
    /*                           Subroutine interpsh                            */
    /*                                                                          */
    /****************************************************************************/
    /*                                                                          */
    /*     Interpolates linearly, in time, between two spherical harmonic       */
    /*     models.                                                              */
    /*                                                                          */
    /*     Input:                                                               */
    /*           date     - date of resulting model (in decimal year)           */
    /*           dte1     - date of earlier model                               */
    /*           nmax1    - maximum degree and order of earlier model           */
    /*           gh1      - Schmidt quasi-normal internal spherical             */
    /*                      harmonic coefficients of earlier model              */
    /*           dte2     - date of later model                                 */
    /*           nmax2    - maximum degree and order of later model             */
    /*           gh2      - Schmidt quasi-normal internal spherical             */
    /*                      harmonic coefficients of internal model             */
    /*                                                                          */
    /*     Output:                                                              */
    /*           gha or b - coefficients of resulting model                     */
    /*           nmax     - maximum degree and order of resulting model         */
    /*                                                                          */
    /*     FORTRAN                                                              */
    /*           A. Zunde                                                       */
    /*           USGS, MS 964, box 25046 Federal Center, Denver, CO.  80225     */
    /*                                                                          */
    /*     C                                                                    */
    /*           C. H. Shaffer                                                  */
    /*           Lockheed Missiles and Space Company, Sunnyvale CA              */
    /*           August 17, 1988                                                */
    /*                                                                          */
    /****************************************************************************/

    private int interpsh(double date, double dte1, int nmax1,
                         double dte2, int nmax2, double[] pgh)
    {
        int   nmax;
        int   k, l;
        int   ii;
        double factor;

        factor = (date - dte1) / (dte2 - dte1);
        if (nmax1 == nmax2) {
            k =  nmax1 * (nmax1 + 2);
            nmax = nmax1;
        }
        else if (nmax1 > nmax2) {
            k = nmax2 * (nmax2 + 2);
            l = nmax1 * (nmax1 + 2);

            for ( ii = k + 1; ii <= l; ++ii)
                pgh[ii] = this.gh1[ii] + factor * (-this.gh1[ii]);

            nmax = nmax1;
        }
        else {
            k = nmax1 * (nmax1 + 2);
            l = nmax2 * (nmax2 + 2);
            for ( ii = k + 1; ii <= l; ++ii)
                pgh[ii] = factor * this.gh2[ii];

            nmax = nmax2;
        }

        for ( ii = 1; ii <= k; ++ii)
            pgh[ii] = this.gh1[ii] + factor * (this.gh2[ii] - this.gh1[ii]);

         return nmax;
    }

    /****************************************************************************/
    /*                                                                          */
    /*                           Subroutine shval3                              */
    /*                                                                          */
    /****************************************************************************/
    /*                                                                          */
    /*     Calculates field components from spherical harmonic (sh)             */
    /*     models.                                                              */
    /*                                                                          */
    /*     Input:                                                               */
    /*           latitude  - north latitude, in radians                         */
    /*           longitude - east longitude, in radians                         */
    /*           elev      - elevation above mean sea level                     */
    /*           a2,b2     - squares of semi-major and semi-minor axes of       */
    /*                       the reference spheroid used for transforming       */
    /*                       between geodetic and geocentric coordinates        */
    /*                       or components                                      */
    /*           nmax      - maximum degree and order of coefficients           */
    /*                                                                          */
    /*     Output:                                                              */
    /*           x         - northward component                                */
    /*           y         - eastward component                                 */
    /*           z         - vertically-downward component                      */
    /*                                                                          */
    /*     based on subroutine 'igrf' by D. R. Barraclough and S. R. C. Malin,  */
    /*     report no. 71/1, institute of geological sciences, U.K.              */
    /*                                                                          */
    /*     FORTRAN                                                              */
    /*           Norman W. Peddie                                               */
    /*           USGS, MS 964, box 25046 Federal Center, Denver, CO.  80225     */
    /*                                                                          */
    /*     C                                                                    */
    /*           C. H. Shaffer                                                  */
    /*           Lockheed Missiles and Space Company, Sunnyvale CA              */
    /*           August 17, 1988                                                */
    /*                                                                          */
    /****************************************************************************/

    double[] sl = new double[14];
    double[] cl = new double[14];
    double[] p = new double[119];
    double[] q = new double[119];

    private int shval3(double flat, double flon, double elev, int nmax, int gh)
    {
        double earths_radius = 6371.2;
        double slat;
        double clat;
        double ratio;
        double aa, bb, cc, dd;
        double sd;
        double cd;
        double r;
        double a2;
        double b2;
        double rr = 0.0;
        double fm,fn = 0.0;
        double[] sl = this.sl;
        double[] cl = this.cl;
        double[] p  = this.p;
        double[] q  = this.q;
        int ii,j,k,l,m,n;
        int npq;
        int ios;
        a2 = 40680925.0;
        b2 = 40408588.0;
        ios = 0;
        slat = sin( flat );
        if (PI / 2 - flat < 0.000017)
            aa = (PI / 2) - 0.000017;  /* ~300 ft. from North pole  */
        else if (PI / 2 + flat < 0.000017)
            aa = -(PI / 2 - 0.000017); /* ~300 ft. from South pole */
        else
            aa = flat;

        clat = cos( aa );
        sl[1] = sin( flon );
        cl[1] = cos( flon );

        if (gh == 3) {
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }
        else {
            this.xtemp = 0;
            this.ztemp = 0;
        }

        l = 1;
        n = 0;
        m = 1;
        npq = (nmax * (nmax + 3)) / 2;

        aa = a2 * clat * clat;
        bb = b2 * slat * slat;
        cc = aa + bb;
        dd = sqrt( cc );
        r = sqrt( elev * (elev + 2.0 * dd) + (a2 * aa + b2 * bb) / cc );
        cd = (elev + dd) / r;
        sd = (a2 - b2) / dd * slat * clat / r;
        aa = slat;
        slat = slat * cd - clat * sd;
        clat = clat * cd + aa * sd;

        ratio = earths_radius / r;
        aa = sqrt( 3.0 );
        p[1] = 2.0 * slat;
        p[2] = 2.0 * clat;
        p[3] = 4.5 * slat * slat - 1.5;
        p[4] = 3.0 * aa * clat * slat;
        q[1] = -clat;
        q[2] = slat;
        q[3] = -3.0 * clat * slat;
        q[4] = aa * (slat * slat - clat * clat);

        for ( k = 1; k <= npq; ++k) {
            if (n < m) {
                m = 0;
                n = n + 1;
                rr = pow(ratio, n + 2);
                fn = (double) n;
            }

            fm = (double) m;
            if (k >= 5) {
                if (m == n) {
                    aa = sqrt( 1.0 - 0.5/fm );
                    j = k - n - 1;
                    p[k] = (1.0 + 1.0/fm) * aa * clat * p[j];
                    q[k] = aa * (clat * q[j] + slat/fm * p[j]);
                    sl[m] = sl[m-1] * cl[1] + cl[m-1] * sl[1];
                    cl[m] = cl[m-1] * cl[1] - sl[m-1] * sl[1];
                }
                else {
                    aa = sqrt( fn*fn - fm*fm );
                    bb = sqrt (((fn - 1.0)*(fn-1.0)) - (fm * fm)) / aa;
                    cc = (2.0 * fn - 1.0)/aa;
                    ii = k - n;
                    j = k - 2 * n + 1;
                    p[k] = (fn + 1.0) * (cc * slat/fn * p[ii] - bb/(fn - 1.0) * p[j]);
                    q[k] = cc * (slat * q[ii] - clat/fn * p[ii]) - bb * q[j];
                }
            }

            if (gh == 3) {
                aa = rr * this.gha[l];
            }
            else {
                aa = rr * this.ghb[l];
            }

            if (m == 0) {
                if (gh == 3) {
                    this.x += aa * q[k];
                    this.z -= aa * p[k];
                }
                else {
                    this.xtemp += aa * q[k];
                    this.ztemp -= aa * p[k];
                }

                l = l + 1;
            }
            else if (gh == 3) {
                bb = rr * this.gha[l+1];
                cc = aa * cl[m] + bb * sl[m];
                this.x += cc * q[k];
                this.z -= cc * p[k];
                if (clat > 0) {
                    this.y += (aa * sl[m] - bb * cl[m]) *
                        fm * p[k]/((fn + 1.0) * clat);
                }
                else {
                    this.y += (aa * sl[m] - bb * cl[m]) * q[k] * slat;
                }
                l = l + 2;
            }
            else {
                bb = rr * this.ghb[l+1];
                cc = aa * cl[m] + bb * sl[m];
                this.xtemp += cc * q[k];
                this.ztemp -= cc * p[k];
                l = l + 2;
            }

            m = m + 1;
        }

        if (gh == 3) {
            aa = this.x;
            this.x = this.x * cd + this.z * sd;
            this.z = this.z * cd - aa * sd;
        }
        else {
            aa = this.xtemp;
            this.xtemp = this.xtemp * cd + this.ztemp * sd;
            this.ztemp = this.ztemp * cd - aa * sd;
        }

        return(ios);
    }

    /****************************************************************************/
    /*                                                                          */
    /*                           Subroutine dihf                                */
    /*                                                                          */
    /****************************************************************************/
    /*                                                                          */
    /*     Computes the geomagnetic d, i, h, and f from x, y, and z.            */
    /*                                                                          */
    /*     Input:                                                               */
    /*           x  - northward component                                       */
    /*           y  - eastward component                                        */
    /*           z  - vertically-downward component                             */
    /*                                                                          */
    /*     Output:                                                              */
    /*           d  - declination                                               */
    /*           i  - inclination                                               */
    /*           h  - horizontal intensity                                      */
    /*           f  - total intensity                                           */
    /*                                                                          */
    /*     FORTRAN                                                              */
    /*           A. Zunde                                                       */
    /*           USGS, MS 964, box 25046 Federal Center, Denver, CO.  80225     */
    /*                                                                          */
    /*     C                                                                    */
    /*           C. H. Shaffer                                                  */
    /*           Lockheed Missiles and Space Company, Sunnyvale CA              */
    /*           August 22, 1988                                                */
    /*                                                                          */
    /****************************************************************************/

    private void dihf3()
    {
        int j;
        double sn;
        double h2;
        double hpx;

        double x = this.x, y = this.y, z = this.z;
        double d = 0, h, f;

        sn = 0.0001;

        for (j = 1; j <= 1; ++j) {
            h2 = x*x + y*y;
            h = sqrt(h2);       /* calculate horizontal intensity */
            f = sqrt(h2 + z*z);      /* calculate total intensity */
            if (f < sn) {
                d = NaN;        /* If d cannot be determined, set equal to 999.0         */
            }
            else {
                if (h < sn) {
                    d = NaN;
                }
                else {
                    hpx = h + x;
                    if (hpx < sn)
                        d = PI;
                    else
                        d = 2.0 * atan2(y, hpx);
                }
            }
        }

        this.d = d;
    }

    /*
     * http://www.wayforward.net/avGPS/libav/av_geo.h
     * Copyright(c) 2005, 2008 Wayforward LLC
     *
     * Rather than having the geo-magnetic model in separate files, as
     * provided by NGDC, we code the data directly in C.
     *
     * ---
     *
     * This program is free software; you can redistribute it and/or modify
     * it under the terms of the GNU General Public License as published by
     * the Free Software Foundation; either version 2 of the License, or
     * (at your option) any later version.
     *
     * This program is distributed in the hope that it will be useful,
     * but WITHOUT ANY WARRANTY; without even the implied warranty of
     * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     * GNU General Public License for more details.
     *
     * You should have received a copy of the GNU General Public License
     * along with this program; if not, write to the Free Software
     * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
     * USA
     */
    public class GeoModel {

        // http://www.ngdc.noaa.gov/geomag/WMM/soft.shtml

        private static GeoData[] WMM2020 = new GeoData[] {
                new GeoData(  -29404.5,       0.0,        6.7,        0.0 ),
                new GeoData(   -1450.7,    4652.9,        7.7,      -25.1 ),
                new GeoData(   -2500.0,       0.0,      -11.5,        0.0 ),
                new GeoData(    2982.0,   -2991.6,       -7.1,      -30.2 ),
                new GeoData(    1676.8,    -734.8,       -2.2,      -23.9 ),
                new GeoData(    1363.9,       0.0,        2.8,        0.0 ),
                new GeoData(   -2381.0,     -82.2,       -6.2,        5.7 ),
                new GeoData(    1236.2,     241.8,        3.4,       -1.0 ),
                new GeoData(     525.7,    -542.9,      -12.2,        1.1 ),
                new GeoData(     903.1,       0.0,       -1.1,        0.0 ),
                new GeoData(     809.4,     282.0,       -1.6,        0.2 ),
                new GeoData(      86.2,    -158.4,       -6.0,        6.9 ),
                new GeoData(    -309.4,     199.8,        5.4,        3.7 ),
                new GeoData(      47.9,    -350.1,       -5.5,       -5.6 ),
                new GeoData(    -234.4,       0.0,       -0.3,        0.0 ),
                new GeoData(     363.1,      47.7,        0.6,        0.1 ),
                new GeoData(     187.8,     208.4,       -0.7,        2.5 ),
                new GeoData(    -140.7,    -121.3,        0.1,       -0.9 ),
                new GeoData(    -151.2,      32.2,        1.2,        3.0 ),
                new GeoData(      13.7,      99.1,        1.0,        0.5 ),
                new GeoData(      65.9,       0.0,       -0.6,        0.0 ),
                new GeoData(      65.6,     -19.1,       -0.4,        0.1 ),
                new GeoData(      73.0,      25.0,        0.5,       -1.8 ),
                new GeoData(    -121.5,      52.7,        1.4,       -1.4 ),
                new GeoData(     -36.2,     -64.4,       -1.4,        0.9 ),
                new GeoData(      13.5,       9.0,       -0.0,        0.1 ),
                new GeoData(     -64.7,      68.1,        0.8,        1.0 ),
                new GeoData(      80.6,       0.0,       -0.1,        0.0 ),
                new GeoData(     -76.8,     -51.4,       -0.3,        0.5 ),
                new GeoData(      -8.3,     -16.8,       -0.1,        0.6 ),
                new GeoData(      56.5,       2.3,        0.7,       -0.7 ),
                new GeoData(      15.8,      23.5,        0.2,       -0.2 ),
                new GeoData(       6.4,      -2.2,       -0.5,       -1.2 ),
                new GeoData(      -7.2,     -27.2,       -0.8,        0.2 ),
                new GeoData(       9.8,      -1.9,        1.0,        0.3 ),
                new GeoData(      23.6,       0.0,       -0.1,        0.0 ),
                new GeoData(       9.8,       8.4,        0.1,       -0.3 ),
                new GeoData(     -17.5,     -15.3,       -0.1,        0.7 ),
                new GeoData(      -0.4,      12.8,        0.5,       -0.2 ),
                new GeoData(     -21.1,     -11.8,       -0.1,        0.5 ),
                new GeoData(      15.3,      14.9,        0.4,       -0.3 ),
                new GeoData(      13.7,       3.6,        0.5,       -0.5 ),
                new GeoData(     -16.5,      -6.9,        0.0,        0.4 ),
                new GeoData(      -0.3,       2.8,        0.4,        0.1 ),
                new GeoData(       5.0,       0.0,       -0.1,        0.0 ),
                new GeoData(       8.2,     -23.3,       -0.2,       -0.3 ),
                new GeoData(       2.9,      11.1,       -0.0,        0.2 ),
                new GeoData(      -1.4,       9.8,        0.4,       -0.4 ),
                new GeoData(      -1.1,      -5.1,       -0.3,        0.4 ),
                new GeoData(     -13.3,      -6.2,       -0.0,        0.1 ),
                new GeoData(       1.1,       7.8,        0.3,       -0.0 ),
                new GeoData(       8.9,       0.4,       -0.0,       -0.2 ),
                new GeoData(      -9.3,      -1.5,       -0.0,        0.5 ),
                new GeoData(     -11.9,       9.7,       -0.4,        0.2 ),
                new GeoData(      -1.9,       0.0,        0.0,        0.0 ),
                new GeoData(      -6.2,       3.4,       -0.0,       -0.0 ),
                new GeoData(      -0.1,      -0.2,       -0.0,        0.1 ),
                new GeoData(       1.7,       3.5,        0.2,       -0.3 ),
                new GeoData(      -0.9,       4.8,       -0.1,        0.1 ),
                new GeoData(       0.6,      -8.6,       -0.2,       -0.2 ),
                new GeoData(      -0.9,      -0.1,       -0.0,        0.1 ),
                new GeoData(       1.9,      -4.2,       -0.1,       -0.0 ),
                new GeoData(       1.4,      -3.4,       -0.2,       -0.1 ),
                new GeoData(      -2.4,      -0.1,       -0.1,        0.2 ),
                new GeoData(      -3.9,      -8.8,       -0.0,       -0.0 ),
                new GeoData(       3.0,       0.0,       -0.0,        0.0 ),
                new GeoData(      -1.4,      -0.0,       -0.1,       -0.0 ),
                new GeoData(      -2.5,       2.6,       -0.0,        0.1 ),
                new GeoData(       2.4,      -0.5,        0.0,        0.0 ),
                new GeoData(      -0.9,      -0.4,       -0.0,        0.2 ),
                new GeoData(       0.3,       0.6,       -0.1,       -0.0 ),
                new GeoData(      -0.7,      -0.2,        0.0,        0.0 ),
                new GeoData(      -0.1,      -1.7,       -0.0,        0.1 ),
                new GeoData(       1.4,      -1.6,       -0.1,       -0.0 ),
                new GeoData(      -0.6,      -3.0,       -0.1,       -0.1 ),
                new GeoData(       0.2,      -2.0,       -0.1,        0.0 ),
                new GeoData(       3.1,      -2.6,       -0.1,       -0.0 ),
                new GeoData(      -2.0,       0.0,        0.0,        0.0 ),
                new GeoData(      -0.1,      -1.2,       -0.0,       -0.0 ),
                new GeoData(       0.5,       0.5,       -0.0,        0.0 ),
                new GeoData(       1.3,       1.3,        0.0,       -0.1 ),
                new GeoData(      -1.2,      -1.8,       -0.0,        0.1 ),
                new GeoData(       0.7,       0.1,       -0.0,       -0.0 ),
                new GeoData(       0.3,       0.7,        0.0,        0.0 ),
                new GeoData(       0.5,      -0.1,       -0.0,       -0.0 ),
                new GeoData(      -0.2,       0.6,        0.0,        0.1 ),
                new GeoData(      -0.5,       0.2,       -0.0,       -0.0 ),
                new GeoData(       0.1,      -0.9,       -0.0,       -0.0 ),
                new GeoData(      -1.1,      -0.0,       -0.0,        0.0 ),
                new GeoData(      -0.3,       0.5,       -0.1,       -0.1 )
        };

        public static GeoModel[] GEO_MODELS = new GeoModel[] {
            new GeoModel( 2020.0, 2025.0,   -1.0,  600.0,  12,  12,   0, WMM2020 )
        };

        public double yrmin, yrmax;
        public double altmin, altmax;

        public int smax1, smax2, smax3;

        public GeoData[] data;

        public GeoModel (double yyrmin, double yyrmax, double aaltmin, double aaltmax,
                         int ssmax1, int ssmax2, int ssmax3, GeoData[] ddata)
        {
            yrmin  = yyrmin;
            yrmax  = yyrmax;
            altmin = aaltmin;
            altmax = aaltmax;
            smax1  = ssmax1;
            smax2  = ssmax2;
            smax3  = ssmax3;
            data   = ddata;
        }
    }

    public class GeoData {
        public double x, y, z, t;

        public GeoData (double xx, double yy, double zz, double tt)
        {
            x = xx;
            y = yy;
            z = zz;
            t = tt;
        }
    }

    // try the test values
    /*
    public static void main (String[] args)
    {
        // WMM2020COF/WMM2020_TEST_VALUES.txt
        //  # Field 1: Date
        //  # Field 2: Height above ellipsoid (km)
        //  # Field 3: geodetic latitude (deg)
        //  # Field 4: geodetic longitude (deg)
        //  # Field 5: declination (deg)
        double[][] testrows = new double[][] {
            new double[] { 2020.0,    28,    89,   -121, -112.41 },
            new double[] { 2020.0,    48,    80,    -96,  -37.40 },
            new double[] { 2020.0,    54,    82,     87,   51.30 },
            new double[] { 2020.0,    65,    43,     93,    0.71 },
            new double[] { 2020.0,    51,   -33,    109,   -5.78 },
            new double[] { 2020.0,    39,   -59,     -8,  -15.79 },
            new double[] { 2020.0,     3,   -50,   -103,   28.10 },
            new double[] { 2020.0,    94,   -29,   -110,   15.82 },
            new double[] { 2020.0,    66,    14,    143,    0.12 },
            new double[] { 2020.0,    18,     0,     21,    1.05 },
            new double[] { 2020.5,     6,   -36,   -137,   20.16 },
            new double[] { 2020.5,    63,    26,     81,    0.43 },
            new double[] { 2020.5,    69,    38,   -144,   13.39 },
            new double[] { 2020.5,    50,   -70,   -133,   57.40 },
            new double[] { 2020.5,     8,   -52,    -75,   15.39 },
            new double[] { 2020.5,     8,   -66,     17,  -32.56 },
            new double[] { 2020.5,    22,   -37,    140,    9.15 },
            new double[] { 2020.5,    40,   -12,   -129,   10.83 },
            new double[] { 2020.5,    44,    33,   -118,   11.46 },
            new double[] { 2020.5,    50,   -81,    -67,   28.65 },
            new double[] { 2021.0,    74,   -57,      3,  -22.29 },
            new double[] { 2021.0,    46,   -24,   -122,   14.02 },
            new double[] { 2021.0,    69,    23,     63,    1.08 },
            new double[] { 2021.0,    33,    -3,   -147,    9.74 },
            new double[] { 2021.0,    47,   -72,    -22,   -6.05 },
            new double[] { 2021.0,    62,   -14,     99,   -1.71 },
            new double[] { 2021.0,    83,    86,    -46,  -36.71 },
            new double[] { 2021.0,    82,   -64,     87,  -80.81 },
            new double[] { 2021.0,    34,   -19,     43,  -14.32 },
            new double[] { 2021.0,    56,   -81,     40,  -59.03 },
            new double[] { 2021.5,    14,     0,     80,   -3.41 },
            new double[] { 2021.5,    12,   -82,    -68,   30.36 },
            new double[] { 2021.5,    44,   -46,    -42,  -11.54 },
            new double[] { 2021.5,    43,    17,     52,    1.23 },
            new double[] { 2021.5,    64,    10,     78,   -1.71 },
            new double[] { 2021.5,    12,    33,   -145,   12.36 },
            new double[] { 2021.5,    12,   -79,    115, -136.34 },
            new double[] { 2021.5,    14,   -33,   -114,   18.10 },
            new double[] { 2021.5,    19,    29,     66,    2.13 },
            new double[] { 2021.5,    86,   -11,    167,   10.11 },
            new double[] { 2022.0,    37,   -66,     -5,  -16.99 },
            new double[] { 2022.0,    67,    72,   -115,   15.47 },
            new double[] { 2022.0,    44,    22,    174,    6.56 },
            new double[] { 2022.0,    54,    54,    178,    1.43 },
            new double[] { 2022.0,    57,   -43,     50,  -47.43 },
            new double[] { 2022.0,    44,   -43,   -111,   24.32 },
            new double[] { 2022.0,    12,   -63,    178,   57.08 },
            new double[] { 2022.0,    38,    27,   -169,    8.76 },
            new double[] { 2022.0,    61,    59,    -77,  -17.63 },
            new double[] { 2022.0,    67,   -47,    -32,  -14.09 },
            new double[] { 2022.5,     8,    62,     53,   18.95 },
            new double[] { 2022.5,    77,   -68,     -7,  -15.94 },
            new double[] { 2022.5,    98,    -5,    159,    7.79 },
            new double[] { 2022.5,    34,   -29,   -107,   15.68 },
            new double[] { 2022.5,    60,    27,     65,    1.78 },
            new double[] { 2022.5,    73,   -72,     95, -101.49 },
            new double[] { 2022.5,    96,   -46,    -85,   18.38 },
            new double[] { 2022.5,     0,   -13,    -59,  -16.65 },
            new double[] { 2022.5,    16,    66,   -178,    1.92 },
            new double[] { 2022.5,    72,   -87,     38,  -64.66 },
            new double[] { 2023.0,    49,    20,    167,    5.20 },
            new double[] { 2023.0,    71,     5,    -13,   -7.26 },
            new double[] { 2023.0,    95,    14,     65,   -0.56 },
            new double[] { 2023.0,    86,   -85,    -79,   41.76 },
            new double[] { 2023.0,    30,   -36,    -64,   -3.87 },
            new double[] { 2023.0,    75,    79,    125,  -14.54 },
            new double[] { 2023.0,    21,     6,    -32,  -15.22 },
            new double[] { 2023.0,     1,   -76,    -75,   30.36 },
            new double[] { 2023.0,    45,   -46,    -41,  -11.94 },
            new double[] { 2023.0,    11,   -22,    -21,  -24.12 },
            new double[] { 2023.5,    28,    54,   -120,   16.20 },
            new double[] { 2023.5,    68,   -58,    156,   40.48 },
            new double[] { 2023.5,    39,   -65,    -88,   29.86 },
            new double[] { 2023.5,    27,   -23,     81,  -13.98 },
            new double[] { 2023.5,    11,    34,      0,    1.08 },
            new double[] { 2023.5,    72,   -62,     65,  -66.98 },
            new double[] { 2023.5,    55,    86,     70,   61.19 },
            new double[] { 2023.5,    59,    32,    163,    0.36 },
            new double[] { 2023.5,    65,    48,    148,   -9.39 },
            new double[] { 2023.5,    95,    30,     28,    4.49 },
            new double[] { 2024.0,    95,   -60,    -59,    8.86 },
            new double[] { 2024.0,    95,   -70,     42,  -54.29 },
            new double[] { 2024.0,    50,    87,   -154,  -82.22 },
            new double[] { 2024.0,    58,    32,     19,    3.94 },
            new double[] { 2024.0,    57,    34,    -13,   -2.62 },
            new double[] { 2024.0,    38,   -76,     49,  -63.51 },
            new double[] { 2024.0,    49,   -50,   -179,   31.57 },
            new double[] { 2024.0,    90,   -55,   -171,   38.07 },
            new double[] { 2024.0,    41,    42,    -19,   -5.00 },
            new double[] { 2024.0,    19,    46,    -22,   -6.60 },
            new double[] { 2024.5,    31,    13,   -132,    9.21 },
            new double[] { 2024.5,    93,    -2,    158,    7.16 },
            new double[] { 2024.5,    51,   -76,     40,  -55.63 },
            new double[] { 2024.5,    64,    22,   -132,   10.52 },
            new double[] { 2024.5,    26,   -65,     55,  -62.60 },
            new double[] { 2024.5,    66,   -21,     32,  -13.34 },
            new double[] { 2024.5,    18,     9,   -172,    9.39 },
            new double[] { 2024.5,    63,    88,     26,   29.81 },
            new double[] { 2024.5,    33,    17,      5,    0.61 },
            new double[] { 2024.5,    77,   -18,    138,    4.63 } };

        for (double[] testrow : testrows) {
            double computed = Math.toDegrees (magnetic_variation (testrow[0], Math.toRadians (testrow[2]), Math.toRadians (testrow[3]), testrow[1]));
            double shouldbe = - testrow[4];
            double diff = (computed - shouldbe) * 100.0 / shouldbe;
            System.out.format ("%6.1f lat=%4.0f lon=%4.0f  =>  computed=%6.2f  shouldbe=%6.2f  diff=%3.0f%n",
                    testrow[0], testrow[2], testrow[3], computed, shouldbe, diff);
        }
    }
    */
}
