#!/bin/bash
#
# http://aeronav.faa.gov/content/aeronav/sectional_files/New_York_86.zip
# http://aeronav.faa.gov/content/aeronav/tac_files/Boston_TAC_81.zip
# http://aeronav.faa.gov/content/aeronav/WAC_files/CF-19_42.zip
# http://aeronav.faa.gov/content/aeronav/Grand_Canyon_files/Grand_Canyon_3.zip
#
#  Takes about 15mins
#

set -e

function downloadgroup
{
    java ParseChartList $effdate $1 < ../ParseChartList.dat | downloadfiles
}

function downloadfiles
{
    # link = "https://aeronav.faa.gov/visual/02-25-2021/heli_files/Boston_Heli.zip"
    # link = "https://aeronav.faa.gov/visual/02-25-2021/sectional-files/New_York.zip"
    # link = "https://aeronav.faa.gov/visual/02-25-2021/tac-files/Boston_TAC.zip"
    while read link
    do
        zipname=`basename $link .zip`_$effdate.zip  # New_York_20210225.zip
        if [ ! -f $zipname ]
        then
            rm -rf zip.tmp
            if wget -nv $link -O zip.tmp
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
            find zip.tmp -type f | renamefiles $effdate
            rm -rf zip.tmp
        fi
    done
}

# rename each file found in the zip to include yyyymmdd effdate on the end
function renamefiles
{
    while read tmppath                      # eg "zip.tmp/New York SEC.htm"
    do
        suff=.${tmppath##*.}                # eg ".htm"
        name=`basename "$tmppath" $suff`    # eg "New York SEC"
        mv -f "$tmppath" "$name $1$suff"    # eg "New York SEC 20210225.htm"
    done
}

cd `dirname $0`
pwd=`pwd`

effdate=`./cureffdate -28 yyyymmdd`

export CLASSPATH=$pwd/ParseChartList.jar

mkdir -p charts
cd charts

downloadgroup sectional-files
downloadgroup tac-files
downloadgroup heli_files
downloadgroup grand_canyon_files
downloadgroup Caribbean
ls -l *Planning*
rm -f New\ York\ TAC\ VFR\ Planning\ Charts\ *.*

