#!/bin/bash -v
#
#  Go to FAA website and get list of airports
#  then get AFD-like info from the FAA and put
#  in .html files.
#
#  Gets all waypoint database info (airports, fixes, localizers, navaids, runways)
#
#  They have an 8-week cycle.
#
#  Outputs these files:
#
#    datums/aptinfo_$expdate/...
#    datums/airports_$expdate.csv
#    datums/fixes_$expdate.csv
#    datums/localizers_$expdate.csv
#    datums/navaids_$expdate.csv
#    datums/runways_$expdate.csv
#
#  Takes about 2 mins to run.
#

function getzip
{
    if [ ! -f datums/$1.$expdate.zip ]
    then
        rm -f $1.zip
        wget --no-check-certificate https://nfdc.faa.gov/webContent/56DaySub/$effdate/$1.zip
        mv -f $1.zip datums/$1.$expdate.zip
    fi
}

function moveaptinfofiles
{
    while read htmlname
    do
        firstchar=${htmlname:0:1}
        restchars=${htmlname:1}
        mkdir -p $1/$firstchar
        gzip -c aptinfo.tmp/$htmlname > $1/$firstchar/$restchars.gz
    done
}

#
#  Script starts here
#
cd `dirname $0`
set -e

#
#  Compile needed utilities
#
if [ cureffdate -ot cureffdate.c ]
then
    cc -Wall -O2 -g -o cureffdate cureffdate.c
fi

if [ ! -f jsoup-1.8.3.jar ]
then
    wget http://jsoup.org/packages/jsoup-1.8.3.jar
fi

if [ ParseAirportHtml.class -ot ParseAirportHtml.java ]
then
    rm -f ParseAirportHtml*.class
    CLASSPATH=.:jsoup-1.8.3.jar javac ParseAirportHtml.java Lib.java
fi

if [ GetAirportIDs.exe -ot GetAirportIDs.cs ]
then
    gmcs -debug -out:GetAirportIDs.exe GetAirportIDs.cs
fi

if [ GetFixes.exe -ot GetFixes.cs ]
then
    gmcs -debug -out:GetFixes.exe GetFixes.cs
fi

if [ WriteAirportsCsv.exe -ot WriteAirportsCsv.cs ]
then
    gmcs -debug -out:WriteAirportsCsv.exe WriteAirportsCsv.cs
fi

if [ WriteLocalizersCsv.exe -ot WriteLocalizersCsv.cs ]
then
    gmcs -debug -out:WriteLocalizersCsv.exe WriteLocalizersCsv.cs
fi

if [ WriteNavaidsCsv.exe -ot WriteNavaidsCsv.cs ]
then
    gmcs -debug -out:WriteNavaidsCsv.exe WriteNavaidsCsv.cs
fi

if [ MakeWaypoints.exe -ot MakeWaypoints.cs ]
then
    gmcs -debug -out:MakeWaypoints.exe MakeWaypoints.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll
fi

#
#  See if we already have this 56-day cycle done
#
expdate=`./cureffdate -x yyyy-mm-dd`
expdate=${expdate//-/}
haveexp=''
if [ -f datums/aptinfo_expdate.dat ]
then
    haveexp=`cat datums/aptinfo_expdate.dat`
fi
if [ "$expdate" == "$haveexp" ]
then
    exit
fi

#
#  Fetch FAA data files
#
rm -rf APT.txt AWY.txt FIX.txt ILS.txt NAV.txt TWR.txt aptinfo.tmp
mkdir -p datums

effdate=`./cureffdate`
getzip APT
getzip AWY
getzip FIX
getzip ILS
getzip NAV
getzip TWR

#
#  Generate airport and runway info
#
unzip datums/APT.$expdate.zip
unzip datums/TWR.$expdate.zip
mkdir aptinfo.tmp
cat APT.txt TWR.txt | mono --debug GetAirportIDs.exe airports.tmp runways.tmp aptinfo.tmp aptinfo.html
rm -f APT.txt TWR.txt

#
#  Generate airway info
#
unzip datums/AWY.$expdate.zip
mono --debug WriteAirwaysCsv.exe < AWY.txt
rm -f AWY.txt

#
#  Generate fix info
#
unzip datums/FIX.$expdate.zip
mono --debug GetFixes.exe < FIX.txt | sort > fixes.csv
rm -f FIX.txt

#
#  Generate localizer info
#
unzip datums/ILS.$expdate.zip
mono --debug WriteLocalizersCsv.exe < ILS.txt | sort > localizers.csv
rm -f ILS.txt

#
#  Generate navaid info
#
unzip datums/NAV.$expdate.zip
mono --debug WriteNavaidsCsv.exe < NAV.txt | sort > navaids.csv
rm -f NAV.txt

#
#  Generate summary files named by the expiration date
#
rm -rf datums/aptinfo_$expdate datums/airports_$expdate.csv datums/fixes_$expdate.csv datums/intersections_$expdate.csv
rm -rf datums/localizers_$expdate.csv datums/navaids_$expdate.csv datums/runways_$expdate.csv

ls aptinfo.tmp | moveaptinfofiles datums/aptinfo_$expdate
sort airports.tmp >  datums/airports_$expdate.csv
mv airways.csv       datums/airways_$expdate.csv
mv fixes.csv         datums/fixes_$expdate.csv
mv intersections.csv datums/intersections_$expdate.csv
mv localizers.csv    datums/localizers_$expdate.csv
mv navaids.csv       datums/navaids_$expdate.csv
sort runways.tmp  >  datums/runways_$expdate.csv

rm -rf aptinfo.tmp airports.tmp runways.tmp

mono --debug MakeWaypoints.exe $expdate
gzip datums/waypoints_$expdate.db

echo $expdate > datums/aptinfo_expdate.dat

