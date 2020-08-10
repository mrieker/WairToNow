#!/bin/bash -v
#
#  Get marked-up airport diagram png (debugging only)
#
#   $1 = ICAOID
#
set -e -x
if [ ReadArptDgmPng.exe -ot ReadArptDgmPng.cs ]
then
    mcs -debug -out:ReadArptDgmPng.exe -reference:Mono.Data.Sqlite.dll -reference:System.Data.dll \
        -reference:System.Drawing.dll ReadArptDgmPng.cs FindWaypoints.cs
fi

icaoid=$1

cycles28=`./cureffdate -28 -x yyyymmdd`

csvline=`grep "^$icaoid," datums/airports_$cycles28.csv`
faaid=${csvline#*,}
faaid=${faaid%%,*}
echo faaid=$faaid
stateid=${csvline%,*}
stateid=${stateid##*,}
echo stateid=$stateid

gifname=`grep "^$faaid,\"APD-AIRPORT" datums/aptplates_$cycles28/state/$stateid.csv`
echo gifname=$gifname
gifname=${gifname#*DIAGRAM\",}
gifname=${gifname%.gif}
pngname=datums/aptplates_$cycles28/pngtemp/$gifname.png.p1
echo pngname=$pngname

mono --debug ReadArptDgmPng.exe -stages -verbose -csvoutfile $icaoid.csv -csvoutid $icaoid -stages -markedpng ${icaoid}_marked.png $pngname
ls -l $icaoid.csv ${icaoid}_marked.png

grep -v "$icaoid," datums/apdgeorefs_$cycles28/$stateid.csv > apdgeorefs_$stateid.csv.old
sort $icaoid.csv apdgeorefs_$stateid.csv.old > apdgeorefs_$stateid.csv.new
mv apdgeorefs_$stateid.csv.new datums/apdgeorefs_$cycles28/$stateid.csv
