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
// ./cureffdate [-x] [ yyyy-mm-dd | mm-dd-yyyy | 'mmm dd yyyy' ]

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static char const *const months[] = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

int main (int argc, char **argv)
{
    int exp;
    struct tm efftm, thentm;
    time_t effbin, effday, nowbin, nowcyc, nowday, thenbin, thenday;

    exp = 0;
    if ((argc > 1) && (strcasecmp (argv[1], "-x") == 0)) {
        exp = 1;
        -- argc;
        argv ++;
    }

    setenv ("TZ", "", 1);

    memset (&thentm, 0, sizeof thentm);
    thentm.tm_hour =  12;
    thentm.tm_mday =  25;  // 25th
    thentm.tm_mon  =   5;  // June
    thentm.tm_year = 115;  // 2015
    thenbin = mktime (&thentm);
    thenday = thenbin / (24 * 60 * 60);

    nowbin = time (NULL);
    nowday = nowbin / (24 * 60 * 60);
    nowcyc = (nowday - thenday) / 56;

    effday = (nowcyc + exp) * 56 + thenday;
    effbin = effday * (24 * 60 * 60) + 12 * 60 * 60;
    efftm  = *localtime (&effbin);

    if ((argc < 2) || (strcasecmp (argv[1], "yyyy-mm-dd") == 0)) printf ("%.4d-%.2d-%.2d\n", efftm.tm_year + 1900, efftm.tm_mon + 1, efftm.tm_mday);
    if ((argc > 1) && (strcasecmp (argv[1], "mm-dd-yyyy") == 0)) printf ("%.2d-%.2d-%.4d\n", efftm.tm_mon + 1, efftm.tm_mday, efftm.tm_year + 1900);
    if ((argc > 1) && (strcasecmp (argv[1], "mmm dd yyyy") == 0)) {
        printf ("%s %.2d %.4d\n", months[efftm.tm_mon], efftm.tm_mday, efftm.tm_year + 1900);
    }

    return 0;
}
