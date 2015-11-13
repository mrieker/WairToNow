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
#    datums/aptinfo_$expdate/
#    datums/airports_$expdate.csv
#    datums/fixes_$expdate.csv
#    datums/localizers_$expdate.csv
#    datums/navaids_$expdate.csv
#    datums/runways_$expdate.csv
#
#  Takes about 30 mins to run.
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

function splitaptinfo
{
    n=0
    while read faaid
    do
        n=$((n+1))
        echo $faaid >> airportids.tmp.$n
        if [ $n == $1 ]
        then
            n=0
        fi
    done
}

function getaptinfo
{
    # read 3 or 4 char FAA (not ICAO) airport id, eg, BVY, 2B2, 28MA
    while read faaid
    do
        rm -f $1.tmp
        subdir=${faaid:0:1}
        subnam=${faaid:1}
        if [ ! -f aptinfo_$expdate/$subdir/$subnam.html.gz ]
        then
            wget -q --no-check-certificate "https://nfdc.faa.gov/nfdcApps/services/airportLookup/airportDisplay.jsp?airportId=$faaid" -O $1-rawhtml.tmp
            if grep -q "$faaid not found" $1-rawhtml.tmp
            then
                echo $faaid not found
            else
                mkdir -p aptinfo_$expdate/$subdir
                CLASSPATH=.:jsoup-1.8.3.jar java ParseAirportHtml $1-rawhtml.tmp aptinfo_$expdate/$subdir/$subnam.html
                gzip aptinfo_$expdate/$subdir/$subnam.html
                ls -l aptinfo_$expdate/$subdir/$subnam.html.gz
            fi
            rm -f $1-*.tmp
        fi
    done
    rm -f $1
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

#
#  See if we akready have this 56-day cycle done
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
rm -f APT.zip APT.txt FIX.zip FIX.txt ILS.zip ILS.txt NAV.zip NAV.txt TWR.zip TWR.txt airportids.tmp.*
mkdir -p aptinfo_$expdate
mkdir -p datums

effdate=`./cureffdate`
getzip APT
getzip FIX
getzip ILS
getzip NAV
getzip TWR

#
#  Generate airport and runway info
#
unzip datums/APT.$expdate.zip
unzip datums/TWR.$expdate.zip
cat APT.txt TWR.txt | mono --debug GetAirportIDs.exe airports.tmp runways.tmp | sort -u | splitaptinfo 9
##rm -f APT.zip APT.txt TWR.zip TWR.txt

getaptinfo airportids.tmp.1 < airportids.tmp.1 &
getaptinfo airportids.tmp.2 < airportids.tmp.2 &
getaptinfo airportids.tmp.3 < airportids.tmp.3 &
getaptinfo airportids.tmp.4 < airportids.tmp.4 &
getaptinfo airportids.tmp.5 < airportids.tmp.5 &
getaptinfo airportids.tmp.6 < airportids.tmp.6 &
getaptinfo airportids.tmp.7 < airportids.tmp.7 &
getaptinfo airportids.tmp.8 < airportids.tmp.8 &
getaptinfo airportids.tmp.9 < airportids.tmp.9 &

while [ "`echo airportids.tmp.*`" != 'airportids.tmp.*' ]
do
    sleep 10
done

#
#  Generate fix info
#
unzip datums/FIX.$expdate.zip
mono --debug GetFixes.exe < FIX.txt | sort > fixes.csv
##rm -f FIX.zip FIX.txt

#
#  Generate localizer info
#
unzip datums/ILS.$expdate.zip
mono --debug WriteLocalizersCsv.exe < ILS.txt | sort > localizers.csv
##rm -f ILS.zip ILS.txt

#
#  Generate navaid info
#
unzip datums/NAV.$expdate.zip
mono --debug WriteNavaidsCsv.exe < NAV.txt | sort > navaids.csv
##rm -f NAV.zip NAV.txt

#
#  Generate summary files named by the expiration date
#
rm -rf datums/aptinfo_$expdate datums/airports_$expdate.csv datums/fixes_$expdate.csv datums/localizers_$expdate.csv datums/navaids_$expdate.csv datums/runways_$expdate.csv

mv aptinfo_$expdate datums/aptinfo_$expdate
sort airports.tmp > datums/airports_$expdate.csv
mv fixes.csv        datums/fixes_$expdate.csv
mv localizers.csv   datums/localizers_$expdate.csv
mv navaids.csv      datums/navaids_$expdate.csv
sort runways.tmp  > datums/runways_$expdate.csv

rm -f airports.tmp runways.tmp

echo $expdate > datums/aptinfo_expdate.dat

