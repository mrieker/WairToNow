#!/bin/bash
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
#    datums/waypoints_$expdate.db.gz
#
#  Takes about 3 mins to run.
#

function getzip
{
    if [ ! -f datums/$1_$expdate.zip ]
    then
        rm -f $1.zip
        wget -nv --no-check-certificate https://nfdc.faa.gov/webContent/28DaySub/$effdate/$1.zip
        mv -f $1.zip datums/$1_$expdate.zip
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
    cc -o cureffdate cureffdate.c
fi

if [ GetAirportIDs.exe -ot GetAirportIDs.cs ]
then
    mcs -debug -out:GetAirportIDs.exe GetAirportIDs.cs
fi

if [ GetFixes.exe -ot GetFixes.cs ]
then
    mcs -debug -out:GetFixes.exe GetFixes.cs
fi

if [ WriteAirwaysCsv.exe -ot WriteAirwaysCsv.cs ]
then
    mcs -debug -out:WriteAirwaysCsv.exe WriteAirwaysCsv.cs
fi

if [ WriteLocalizersCsv.exe -ot WriteLocalizersCsv.cs ]
then
    mcs -debug -out:WriteLocalizersCsv.exe WriteLocalizersCsv.cs
fi

if [ WriteNavaidsCsv.exe -ot WriteNavaidsCsv.cs ]
then
    mcs -debug -out:WriteNavaidsCsv.exe WriteNavaidsCsv.cs
fi

if [ MakeWaypoints.exe -ot MakeWaypoints.cs ]
then
    mcs -debug -out:MakeWaypoints.exe MakeWaypoints.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll
fi

if [ MakeObstructions.exe -ot MakeObstructions.cs ]
then
    mcs -debug -out:MakeObstructions.exe MakeObstructions.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll
fi

#
#  See if we already have this 28-day cycle done
#
expdate=`./cureffdate -28 -x yyyymmdd`
if [ ! -f datums/waypoints_$expdate.db.gz ]
then

    #
    #  Fetch FAA data files
    #
    rm -rf AFF.txt APT.txt AWOS.txt AWY.txt FIX.txt ILS.txt NAV.txt TWR.txt aptinfo.tmp
    mkdir -p datums

    effdate=`./cureffdate -28`
    getzip AFF
    getzip APT
    getzip AWOS
    getzip AWY
    getzip FIX
    getzip ILS
    getzip NAV
    getzip TWR

    #
    #  Generate airport and runway info
    #
    unzip datums/AFF_$expdate.zip
    unzip datums/APT_$expdate.zip
    unzip datums/AWOS_$expdate.zip
    unzip datums/TWR_$expdate.zip
    mkdir aptinfo.tmp
    # - APT.txt must be first
    cat APT.txt AFF.txt AWOS.txt TWR.txt | mono --debug GetAirportIDs.exe airports.tmp runways.tmp aptinfo.tmp aptinfo.html
    rm -f APT.txt AFF.txt AWOS.txt TWR.txt

    #
    #  Generate airway info
    #
    unzip datums/AWY_$expdate.zip
    mono --debug WriteAirwaysCsv.exe < AWY.txt
    rm -f AWY.txt

    #
    #  Generate fix info
    #
    unzip datums/FIX_$expdate.zip
    mono --debug GetFixes.exe < FIX.txt | sort > fixes.csv
    rm -f FIX.txt

    #
    #  Generate localizer info
    #
    unzip datums/ILS_$expdate.zip
    mono --debug WriteLocalizersCsv.exe < ILS.txt | sort > localizers.csv
    rm -f ILS.txt

    #
    #  Generate navaid info
    #
    unzip datums/NAV_$expdate.zip
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

    #
    #  Generate SQLite databases for downloading
    #
    mono --debug MakeWaypoints.exe $expdate
    gzip datums/waypoints_$expdate.db
fi

#
# Get obstruction data file
#
if [ ! -f datums/obstructions_$expdate.db.gz ]
then

    #
    # Download from FAA
    #
    if [ ! -f datums/DOF_$expdate.zip ]
    then
        rm -f datums/DOF_$expdate.zip*
        wget -nv -O datums/DOF_$expdate.zip.tmp https://aeronav.faa.gov/Obst_Data/DAILY_DOF_DAT.ZIP
        mv datums/DOF_$expdate.zip.tmp datums/DOF_$expdate.zip
    fi

    #
    # Convert to SQLite database
    #
    unzip -q -p datums/DOF_$expdate.zip DOF.DAT | mono --debug MakeObstructions.exe datums/obstructions_$expdate.db
    gzip datums/obstructions_$expdate.db
fi
