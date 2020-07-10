#!/bin/bash
set -e

function listzip
{
    while read stzip
    do
        echo @$stzip
        unzip -l datums/statezips_$1/$stzip
    done
}

function writetemp
{
    stzip=__
    while read zipline
    do
        if [ "${zipline:0:1}" == "@" ]
        then
            stzip=${zipline:1}
        else
            decodezipline $stzip $zipline
        fi
    done
}

function decodezipline
{
    gif=$5
    if [ "${gif/[.]gif[.]p/}" != "$gif" ]
    then
        echo $1 $5
    fi
}

cd `dirname $0`
rm -f /tmp/cmpstatezips_*.$3
currdate=$1
prevdate=$2
ls datums/statezips_$currdate | listzip $currdate | writetemp | sort > /tmp/cmpstatezips_$currdate.$3
ls datums/statezips_$prevdate | listzip $prevdate | writetemp | sort > /tmp/cmpstatezips_$prevdate.$3
ls -l /tmp/cmpstatezips_*.$3
echo diff /tmp/cmpstatezips_$currdate.$3 /tmp/cmpstatezips_$prevdate.$3
diff /tmp/cmpstatezips_$currdate.$3 /tmp/cmpstatezips_$prevdate.$3
rm -f /tmp/cmpstatezips_*.$3
