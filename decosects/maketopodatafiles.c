
// write out the datums/topo files containing elevation data
// each file is named:
//    datums/topo/latdegrees/londegrees
// each file contains 3600 shorts in a 60x60 grid of minutes
// each short is an elevation in metres

// cc -g -Wall -O2 -o maketopodatafiles maketopodatafiles.c
// wget http://www.ngdc.noaa.gov/mgg/global/relief/ETOPO1/data/ice_surface/grid_registered/xyz/ETOPO1_Ice_g_int.xyz.gz
// mkdir datums/topo
// zcat ETOPO1_Ice_g_int.xyz.gz | ./maketopodatafiles
// rm ETOPO1_Ice_g_int.xyz.gz

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

int main ()
{
    // lon lat elev(metres)

    short *elevs = malloc (60 * 180 * 60 * 360 * sizeof *elevs);

    char line[256];
    char namebuf[32];
    double lat, lon;
    FILE *binfile;
    int count, elev, ilat, ilon, jlatdeg, jlatmin, jlondeg, maxelev, minelev;

    for (count = 0; count < 60 * 180 * 60 * 360; count ++) {
        elevs[count] = 0x8000;  // Topography.INVALID_ELEV
    }

    count   = 0;
    maxelev = -999999999;
    minelev =  999999999;

    while (fgets (line, sizeof line, stdin) != NULL) {
        sscanf (line, "%lf %lf %d", &lon, &lat, &elev);
        ilon = ((int) (lon * 32768 / 180.0)) & 65535;
        ilat = ((int) (lat * 32768 /  90.0)) & 65535;
        ilon = (int) ((lon + 180.0) * 60.0 + 0.5);
        ilat = (int) ((lat +  90.0) * 60.0 + 0.5);
        if ((ilat >= 0) && (ilat < 180*60)) {
            ilon %= 360*60;
            elevs[ilat*360*60+ilon] = elev;
            if (maxelev < elev) maxelev = elev;
            if (minelev > elev) minelev = elev;
        }
        if (++ count % 1000000 == 0) {
            printf ("count=%9d/233280000\n", count);
            fflush (stdout);
        }
    }

    printf ("elev range %d .. %d\n", minelev, maxelev);
    fflush (stdout);

    for (jlatdeg = -90; jlatdeg < 90; jlatdeg ++) {
        sprintf (namebuf, "datums/topo/%d", jlatdeg);
        mkdir (namebuf, 0777);
        for (jlondeg = -180; jlondeg < 180; jlondeg ++) {
            sprintf (namebuf, "datums/topo/%d/%d", jlatdeg, jlondeg);
            binfile = fopen (namebuf, "w");
            if (binfile == NULL) {
                fprintf (stderr, "error creating %s: %s\n", namebuf, strerror (errno));
                exit (1);
            }
            for (jlatmin = 0; jlatmin < 60; jlatmin ++) {
                lat  = jlatdeg + jlatmin / 60.0;
                lon  = jlondeg;

                ilat = ((int) (lat * 32768 /  90.0)) & 65535;
                ilon = ((int) (lon * 32768 / 180.0)) & 65535;

                ilat = (int) ((lat +  90.0) * 60.0 + 0.5);
                ilon = (int) ((lon + 180.0) * 60.0 + 0.5);

                fwrite (&elevs[ilat*360*60+ilon], sizeof *elevs, 60, binfile);
            }
            fclose (binfile);
        }
    }

    return 0;
}
