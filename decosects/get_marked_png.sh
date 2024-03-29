#!/bin/bash -v
#
#  Get marked-up airport diagram png (debugging only)
#
#   $1 = ICAOID
#
set -e -x

icaoid=$1

cycles28=`./cureffdate -28 -x yyyymmdd`

faaid=`echo "select apt_faaid from airports where apt_icaoid='$icaoid';" | sqlite3 datums/waypoints_$cycles28.db`
stateid=`echo "select apt_state from airports where apt_icaoid='$icaoid';" | sqlite3 datums/waypoints_$cycles28.db`
echo faaid=$faaid
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
rm -f datums/statezips_$cycles28/$stateid.zip
./makestatezips.sh $cycles28 $stateid
