#!/bin/bash -v

function purgechart
{
    while read file
    do
        if [ "$file" == "${file%.zip}" ]
        then
            rm -rf "$file"
        else
            mv "$file" "purged-$file"
        fi
    done
}

function purgedatum
{
    while read file
    do
        if [ "${file:0:15}" == "datums/airports" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:15}" == "datums/airways_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:18}" == "datums/apdgeorefs_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:19}" == "datums/aptdiags_150" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:19}" == "datums/aptdiags_300" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:19}" == "datums/aptdiags_pdf" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:14}" == "datums/aptinfo" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/aptplates" ]
        then
            rm -rf "$file/gif_150"
            rm -rf "$file/giftemp"
            rm -rf "$file/pngtemp"
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:12}" == "datums/DDTPP" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:12}" == "datums/fixes" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:20}" == "datums/getaptplates_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/iapcifps_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:19}" == "datums/iapgeorefs2_" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:20}" == "datums/intersections" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:17}" == "datums/localizers" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:14}" == "datums/navaids" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:20}" == "datums/obstructions_" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:14}" == "datums/runways" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:17}" == "datums/statezips_" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/waypoints" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/wayptabbs" ]
        then
            rm -rf "$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/AFF_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/APT_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:12}" == "datums/AWOS_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/AWY_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/DOF_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/FIX_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/ILS_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/NAV_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/TWR_" ]
        then
            mv "$file" "purged-$file"
            continue
        fi
    done
}

if [ PurgeOldCharts.exe -ot PurgeOldCharts.cs ]
then
    mcs -debug -out:PurgeOldCharts.exe PurgeOldCharts.cs
fi

mkdir -p purged-charts
mkdir -p purged-datums

mono --debug PurgeOldCharts.exe charts | purgechart
mono --debug PurgeOldCharts.exe datums | purgedatum

