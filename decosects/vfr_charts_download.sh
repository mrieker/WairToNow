#!/bin/bash -v
#
# http://aeronav.faa.gov/index.asp?xml=aeronav/applications/VFR/chartlist_sect
# http://aeronav.faa.gov/index.asp?xml=aeronav/applications/VFR/chartlist_tac
# http://aeronav.faa.gov/index.asp?xml=aeronav/applications/VFR/chartlist_wac
# http://aeronav.faa.gov/index.asp?xml=aeronav/applications/VFR/grand_canyon
#
# http://aeronav.faa.gov/content/aeronav/sectional_files/New_York_86.zip
# http://aeronav.faa.gov/content/aeronav/tac_files/Boston_TAC_81.zip
# http://aeronav.faa.gov/content/aeronav/WAC_files/CF-19_42.zip
# http://aeronav.faa.gov/content/aeronav/Grand_Canyon_files/Grand_Canyon_3.zip
#
#  Takes about 35mins
#

set -e

function downloadgroup
{
    grep "http://aeronav.faa.gov/content/aeronav/$1_files/" chartlist_all.htm > charts.tmp1
    sed 's/.*href="//g' charts.tmp1 | grep -i [.]zip | grep -i -v _P[.]zip > charts.tmp2
    sed 's/".*//g' charts.tmp2 | downloadfiles
    rm charts.tmp*
}

function downloadfiles
{
    while read link
    do
        zipname=`basename $link`
        if [ ! -f $zipname ]
        then
            rm -rf zip.tmp
            if wget -q $link -O zip.tmp
            then
                mv -f zip.tmp $zipname
            else
                rm -f zip.tmp
            fi
        fi
        if [ -f $zipname ]
        then
            rm -rf zip.tmp
            mkdir zip.tmp
            unzip -d zip.tmp -o $zipname
            find zip.tmp -type f -exec mv {} . \;
            rm -rf zip.tmp
        fi
    done
}

mkdir -p charts
cd charts

wget -q http://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/vfr/ -O chartlist_all.htm
downloadgroup sectional
downloadgroup tac
downloadgroup wac
downloadgroup heli
##downloadgroup grand_canyon
rm -f Grand\ Canyon\ *\ Air\ Tour\ Operators.*
rm -f New\ York\ TAC\ VFR\ Planning\ Charts\ *.*

