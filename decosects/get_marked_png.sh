#!/bin/bash
#
#  Get marked-up airprot diagram png (debugging only)
#
#   $1 = FAAID
#
set -e
if [ ReadArptDgmPng.exe -ot ReadArptDgmPng.cs ]
then
    mcs -debug -out:ReadArptDgmPng.exe -reference:System.Drawing.dll ReadArptDgmPng.cs
fi

faaid=$1
cycles28=`./cureffdate -28 -x yyyymmdd`

icaoid=`grep ",$faaid," datums/airports_$cycles28.csv`
icaoid=${icaoid%%,*}
echo icaoid=$icaoid

gifname=`grep "^$faaid,\"APD-AIRPORT" datums/aptplates_$cycles28/state/*.csv`
echo gifname=$gifname
gifname=${gifname#*gif_150/}
gifname=${gifname%.gif}
pngname=datums/aptplates_$cycles28/pngtemp/$gifname.png.p1
echo pngname=$pngname

mono --debug ReadArptDgmPng.exe -stages -verbose -csvoutfile $faaid.csv -csvoutid $icaoid -markedpng ${faaid}_marked.png $pngname
ls -l ${faaid}_marked.png
