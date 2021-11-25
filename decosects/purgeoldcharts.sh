#!/bin/bash -v

function purgechart
{
    while read file
    do
        if [ "$file" == "${file%.zip}" ]
        then
            echo rm -rf "$file"
        else
            if [ "$file" == "${file%.wtn.zip}" ]
            then
                echo mv "$file" "purged-$file"
            else
                echo rm -rf "$file"
            fi
        fi
    done
}

function purgedatum
{
    while read file
    do
        if [ "${file:0:15}" == "datums/airports" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:15}" == "datums/airways_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:18}" == "datums/apdgeorefs_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:19}" == "datums/aptdiags_150" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:19}" == "datums/aptdiags_300" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:19}" == "datums/aptdiags_pdf" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:14}" == "datums/aptinfo" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/aptplates" ]
        then
            echo rm -rf "$file/gif_150"
            echo rm -rf "$file/giftemp"
            echo rm -rf "$file/pngtemp"
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:12}" == "datums/DDTPP" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:21}" == "datums/europlatecsvs_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:21}" == "datums/europlategeos_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:21}" == "datums/europlategifs_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:21}" == "datums/europlatelist_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:21}" == "datums/europlatepdfs_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:12}" == "datums/fixes" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:20}" == "datums/getaptplates_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/iapcifps_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:19}" == "datums/iapgeorefs2_" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:20}" == "datums/intersections" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:17}" == "datums/localizers" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:14}" == "datums/navaids" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:10}" == "datums/oa_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:20}" == "datums/obstructions_" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:17}" == "datums/ofmwaypts_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:12}" == "datums/ofmx_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:14}" == "datums/runways" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:17}" == "datums/statezips_" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/stations_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/waypoints" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:16}" == "datums/wayptabbs" ]
        then
            echo rm -rf "$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/AFF_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/APT_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:12}" == "datums/AWOS_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/AWY_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/DOF_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/FIX_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/ILS_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/NAV_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
        if [ "${file:0:11}" == "datums/TWR_" ]
        then
            echo mv "$file" "purged-$file"
            continue
        fi
    done
}

mkdir -p purged-charts
mkdir -p purged-datums

mono --debug PurgeOldCharts.exe charts | purgechart
mono --debug PurgeOldCharts.exe datums | purgedatum

