#!/bin/bash -v
#
#  Go to FAA website and get list of airports
#  then get AFD-like info from the FAA and put
#  in .html files.
#
#  They have an 8-week cycle.
#
#  Outputs these files:
#
#    datums/aptinfo_$expdate/
#    datums/airports_$expdate.csv
#    datums/fixes_$expdate.csv
#    datums/navaids_$expdate.csv
#    datums/runways_$expdate.csv
#
#  Takes about 30 mins to run.
#

function splitaptinfo
{
    n=0
    while read aptid
    do
        n=$((n+1))
        echo $aptid >> airportids.tmp.$n
        if [ $n == $1 ]
        then
            n=0
        fi
    done
}

function getaptinfo
{
    # read 3 or 4 char FAA (not ICAO) airport id, eg, BVY, 2B2, 28MA
    while read aptid
    do
        rm -f $1.tmp
        subdir=${aptid:0:1}
        subnam=${aptid:1}
        if [ ! -f aptinfo/$subdir/$subnam.html.gz ]
        then
            wget -q --no-check-certificate "https://nfdc.faa.gov/nfdcApps/airportLookup/airportDisplay.jsp?category=nasr&airportId=$aptid" -O $1.tmp
            if grep -q "$aptid not found" $1.tmp
            then
                echo $aptid not found
            else
                mono --debug CleanArptInfoHtml.exe $1.tmp aptinfo/$subdir/$subnam.html
                gzip aptinfo/$subdir/$subnam.html
                ls -l aptinfo/$subdir/$subnam.html.gz
            fi
            rm -f $1.tmp
        fi
    done
    rm -f $1
}

cd `dirname $0`
set -e

if [ cureffdate -ot cureffdate.c ]
then
    cc -Wall -O2 -g -o cureffdate cureffdate.c
fi

if [ CleanArptInfoHtml.exe -ot CleanArptInfoHtml.cs ]
then
    gmcs -debug -out:CleanArptInfoHtml.exe CleanArptInfoHtml.cs
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

rm -f APT.zip APT.txt FIX.zip FIX.txt ILS.zip ILS.txt NAV.zip NAV.txt airportids.tmp.*
rm -rf aptinfo
mkdir -p aptinfo

effdate=`./cureffdate`
wget --no-check-certificate https://nfdc.faa.gov/webContent/56DaySub/$effdate/APT.zip
wget --no-check-certificate https://nfdc.faa.gov/webContent/56DaySub/$effdate/FIX.zip
wget --no-check-certificate https://nfdc.faa.gov/webContent/56DaySub/$effdate/ILS.zip
wget --no-check-certificate https://nfdc.faa.gov/webContent/56DaySub/$effdate/NAV.zip

unzip APT.zip
mono --debug GetAirportIDs.exe < APT.txt | sort -u | splitaptinfo 9
rm -f APT.zip APT.txt

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
unzip FIX.zip
mono --debug GetFixes.exe < FIX.txt | sort > fixes.csv
rm -f FIX.zip FIX.txt

#
#  Generate localizer info
#
unzip ILS.zip
mono --debug WriteLocalizersCsv.exe < ILS.txt | sort > localizers.csv
rm -f ILS.zip ILS.txt

#
#  Generate navaid info
#
unzip NAV.zip
mono --debug WriteNavaidsCsv.exe < NAV.txt | sort > navaids.csv
rm -f NAV.zip NAV.txt

#
#  Generate summary files named by the expiration date
#
expdate=`mono --debug WriteAirportsCsv.exe aptinfo`

rm -rf datums/aptinfo_$expdate datums/airports_$expdate.csv datums/fixes_$expdate.csv datums/localizers_$expdate.csv datums/navaids_$expdate.csv datums/runways_$expdate.csv

mv aptinfo        datums/aptinfo_$expdate
mv airports.csv   datums/airports_$expdate.csv
mv fixes.csv      datums/fixes_$expdate.csv
mv localizers.csv datums/localizers_$expdate.csv
mv navaids.csv    datums/navaids_$expdate.csv
mv runways.csv    datums/runways_$expdate.csv

echo $expdate > datums/aptinfo_expdate.dat

