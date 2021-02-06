#!/bin/bash
#
#  Restore datums files from purged-datums files
#  Just rm -rf datums/*_expdate* when done with files
#
#  $1 = expdate yyyymmdd
#
cd `dirname $0`
set -e -x

expdate=$1
if [ "$expdate" == "" ]
then
    echo no expdate given
    exit 1
fi

make

# airports_$expdate.csv
# aptinfo_$expdate
# runways_$expdate.csv
rm -rf APT.txt AFF.txt AWOS.txt TWR.txt stations.txt aptinfo.tmp
unzip purged-datums/AFF_$expdate.zip
unzip purged-datums/APT_$expdate.zip
unzip purged-datums/AWOS_$expdate.zip
unzip purged-datums/TWR_$expdate.zip
mkdir aptinfo.tmp
gzip -c purged-datums/stations_$expdate.gz > stations.txt
rm -rf datums/airports_$expdate.csv datums/runways_$expdate.csv datums/aptinfo_$expdate
cat APT.txt AFF.txt AWOS.txt TWR.txt | mono --debug GetAirportIDs.exe airports.tmp runways.tmp aptinfo.tmp stations.txt
rm -rf APT.txt AFF.txt AWOS.txt TWR.txt stations.txt datums/aptinfo_$expdate
mv -f airports.tmp datums/airports_$expdate.csv
mv -f runways.tmp datums/runways_$expdate.csv
ls aptinfo.tmp | while read htmlname
do
    firstlet=${htmlname:0:1}
    restname=${htmlname:1}
    mkdir -p datums/aptinfo_$expdate/$firstlet
    gzip -c aptinfo.tmp/$htmlname > datums/aptinfo_$expdate/$firstlet/$restname.gz
    set +x
done
set -x
rm -rf aptinfo.tmp

# airways_$expdate.csv
# fixes_$expdate.csv
# intersections_$expdate.csv
# localizers_$expdate.csv
# navaids_$expdate.csv
rm -rf AWY.txt FIX.txt ILS.txt NAV.txt
unzip purged-datums/AWY_$expdate.zip
unzip purged-datums/FIX_$expdate.zip
unzip purged-datums/ILS_$expdate.zip
unzip purged-datums/NAV_$expdate.zip
mono --debug WriteAirwaysCsv.exe < AWY.txt
mono --debug GetFixes.exe < FIX.txt | sort > fixes.csv
mono --debug WriteLocalizersCsv.exe < ILS.txt | sort > localizers.csv
mono --debug WriteNavaidsCsv.exe < NAV.txt | sort > navaids.csv
rm -rf AWY.txt FIX.txt ILS.txt NAV.txt
mv -f airways.csv datums/airways_$expdate.csv
mv -f fixes.csv datums/fixes_$expdate.csv
mv -f intersections.csv datums/intersections_$expdate.csv
mv -f localizers.csv datums/localizers_$expdate.csv
mv -f navaids.csv datums/navaids_$expdate.csv

# aptplates_$expdate
mkdir -p datums/aptplates_$expdate/pdftemp
unzip -n -q -d datums/aptplates_$expdate/pdftemp purged-datums/DDTPPA_$expdate.zip
unzip -n -q -d datums/aptplates_$expdate/pdftemp purged-datums/DDTPPB_$expdate.zip
unzip -n -q -d datums/aptplates_$expdate/pdftemp purged-datums/DDTPPC_$expdate.zip
unzip -n -q -d datums/aptplates_$expdate/pdftemp purged-datums/DDTPPD_$expdate.zip
zcat purged-datums/DDTPPE_$expdate.xml.gz > datums/aptplates_$expdate/pdftemp/d-TPP_Metafile.xml
find datums/aptplates_$expdate/pdftemp -name \*.pdf -exec ./get_aptplates.sh renamepdf {} \;

rm -rf aptplates.tmp
mkdir aptplates.tmp
export CLASSPATH=ProcessPlates.jar
java ProcessPlates 12 datums/aptplates_$expdate/pdftemp/d-TPP_Metafile.xml datums/airports_$expdate.csv datums/aptplates_$expdate

mkdir -p datums/aptplates_$expdate/state
ls aptplates.tmp/*.1 | while read tmppath
do
    stateid=${tmppath#aptplates.tmp/}
    stateid=${stateid%.1}
    sort -u aptplates.tmp/$stateid.* > datums/aptplates_$expdate/state/$stateid.csv
done
rm -rf aptplates.tmp

# apdgeorefs_$expdate
# iapgeorefs2_$expdate
./decodeallplates.sh $expdate

# obstructions_$expdate.db.gz
rm -f datums/obstructions_$expdate.db*
unzip -q -p purged-datums/DOF_$expdate.zip DOF.DAT | mono --debug MakeObstructions.exe datums/obstructions_$expdate.db
gzip datums/obstructions_$expdate.db

# waypoints_$expdate.db.gz
# waypointsoa_$expdate.db.gz
# waypointsofm_$expdate.db.gz
# wayptabbs_$expdate.db.gz
# wayptabbsoa_$expdate.db.gz
rm -rf datums/waypoints*_$expdate.db* datums/wayptabbs*_$expdate.db* datums/oa_$expdate
cp -a purged-datums/oa_$expdate datums/
cp -a purged-datums/ofmwaypts_$expdate.xml.gz datums/
mono --debug MakeWaypoints.exe $expdate datums/waypoints_$expdate.db    0 0
mono --debug MakeWaypoints.exe $expdate datums/waypointsoa_$expdate.db  0 1
mono --debug MakeWaypoints.exe $expdate datums/waypointsofm_$expdate.db 0 2
mono --debug MakeWaypoints.exe $expdate datums/wayptabbs_$expdate.db    1 0
mono --debug MakeWaypoints.exe $expdate datums/wayptabbsoa_$expdate.db  1 1
gzip datums/waypoints_$expdate.db
gzip datums/waypointsoa_$expdate.db
gzip datums/waypointsofm_$expdate.db
gzip datums/wayptabbs_$expdate.db
gzip datums/wayptabbsoa_$expdate.db

# iapcifps_$expdate
export CLASSPATH=ParseCifp.jar
rm -rf datums/iapcifps_$expdate datums/iapcifps_$expdate.tmp
mkdir -p datums/iapcifps_$expdate.tmp
zcat purged-datums/iapcifps_$expdate.gz | java ParseCifp $expdate datums/iapcifps_$expdate.tmp
mv datums/iapcifps_$expdate.tmp datums/iapcifps_$expdate

# statezips_$expdate
rm -rf datums/statezips_$expdate
./makestatezips.sh $expdate
echo done
