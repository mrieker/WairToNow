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

// cc -o cureffdate cureffdate.c
// export next28=1 # to pretend it is 28 days later
// ./cureffdate [-28] [-x] [ yyyy-mm-dd | mm-dd-yyyy | 'mmm dd yyyy' | yyyymmdd | airac ]

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static char const *const months[] = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

int main (int argc, char **argv)
{
    char const *env, *fmt;
    int cyclemonth, cyclen, cycleyear, exp, i, next28, nextyear;
    struct tm efftm, thentm;
    time_t cycletime, effbin, effday, nexttime, nowbin, nowcyc, nowday, thenbin, thenday;

    cyclen = 56;
    exp = 0;
    fmt = "yyyy-mm-dd";
    for (i = 0; ++ i < argc;) {
        if (strcasecmp (argv[i], "-28") == 0) cyclen = 28;
        if (strcasecmp (argv[i], "-x")  == 0) exp = 1;
        if (argv[i][0] != '-') fmt = argv[i];
    }

    env = getenv ("next28");
    if (env == NULL) {
        fprintf (stderr, "cureffdate: envar next28 not defined\n");
        abort ();
    }
    next28 = *env & 1;

    if (cyclen != 28) {
        fprintf (stderr, "curreffdate: must be 28-day cycle\n");
        abort ();
    }

    nowbin = time (NULL);
    if (next28) nowbin += 60*60*24*28;

    setenv ("TZ", "", 1);

    if (strcasecmp (fmt, "airac") == 0) {
        cycleyear  = 2014;          // cycle 1401 ...
        cyclemonth = 1;             // ... started at ...
        cycletime  = 1389258060;    // 2014-01-09@09:01:00 UTC'
        while ((nexttime = cycletime + 60*60*24*28) <= nowbin) {
            cycletime = nexttime;
            cyclemonth ++;
            nextyear = localtime (&nexttime)->tm_year + 1900;
            if (cycleyear < nextyear) {
                cycleyear  = nextyear;
                cyclemonth = 1;
            }
        }
        printf ("%.2d%.2d\n", cycleyear % 100, cyclemonth);
        return 0;
    }

    memset (&thentm, 0, sizeof thentm);
    thentm.tm_hour =  12;
    thentm.tm_mday =  25;  // 25th
    thentm.tm_mon  =   5;  // June
    thentm.tm_year = 115;  // 2015
    thenbin = mktime (&thentm);
    thenday = thenbin / (24 * 60 * 60);

    nowday = nowbin / (24 * 60 * 60);
    nowcyc = (nowday - thenday) / cyclen;

    effday = (nowcyc + exp) * cyclen + thenday;
    effbin = effday * (24 * 60 * 60) + 12 * 60 * 60;
    efftm  = *localtime (&effbin);

    if (strcasecmp (fmt, "yyyy-mm-dd") == 0) printf ("%.4d-%.2d-%.2d\n", efftm.tm_year + 1900, efftm.tm_mon + 1, efftm.tm_mday);
    if (strcasecmp (fmt, "mm-dd-yyyy") == 0) printf ("%.2d-%.2d-%.4d\n", efftm.tm_mon + 1, efftm.tm_mday, efftm.tm_year + 1900);
    if (strcasecmp (fmt, "mmm dd yyyy") == 0) {
        printf ("%s %.2d %.4d\n", months[efftm.tm_mon], efftm.tm_mday, efftm.tm_year + 1900);
    }
    if (strcasecmp (fmt, "yyyymmdd") == 0) printf ("%.4d%.2d%.2d\n",   efftm.tm_year + 1900, efftm.tm_mon + 1, efftm.tm_mday);

    return 0;
}
