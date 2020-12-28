#!/bin/bash
#
#  Reads list of IFR enroute charts from
#  https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/ifr/
#  and downloads the images and info
#
#  They have a 56-day cycle
#
#  Takes about 5 minutes to run
#

set -e

#
# Get all area charts from the one zip file
#
function getareacharts
{
    while read tmpname
    do
        tmpnamenotif=${tmpname%.tif}                    # eg, ENR_A02_DEN
        if [ "$tmpname" != "$tmpnamenotif" ]
        then
            spacename="${tmpnamenotif//_/ } $revcode"   # eg, ENR A02 DEN 20151210
            if [ ! -f "$spacename.tfw" ] ; then find zip.tmp -iname $tmpnamenotif.tfwx -exec mv {} "$spacename.tfw" \; ; fi
            if [ ! -f "$spacename.tif" ] ; then find zip.tmp -iname $tmpnamenotif.tif  -exec mv {} "$spacename.tif" \; ; fi
            if [ ! -f "$spacename.htm" ] ; then find zip.tmp -iname $tmpnamenotif.htm  -exec mv {} "$spacename.htm" \; ; fi
        fi
    done
}

#
# Download zip files and unzip them.
#
# downloads enr_l33.zip      => ENR_ELUS33_yyyymmdd.zip just like a sectional
#    unzips ENR_L33.tfwx     => ENR ELUS33 yyyymmdd.tfw just like a sectional
#    unzips ENR_L33.tif      => ENR ELUS33 yyyymmdd.tif just like a sectional
#    unzips ENR_L33_tif.htm  => ENR ELUS33 yyyymmdd.htm just like a sectional
#
function scanifrchartshtm
{
    # make a numeric revision code similar to VFR charts, eg, 20150430
    # GenChartsCSV.exe assumes this is the start date of the 56-day cycle
    revcode=${effdate_mm:6:4}${effdate_mm:0:2}${effdate_mm:3:2}

    # url to download zip file
    while read zipurl
    do
        # chart name, eg, ELUS1
        read chartname

        # make our zip name similar to VFR charts, eg ENR_ELUS1_20150430.zip
        zipname=ENR_${chartname}_$revcode.zip

        ##echo "   zipurl=$zipurl"
        ##echo "chartname=$chartname"
        ##echo "  revcode=$revcode"
        ##echo "  zipname=$zipname"

        echo $chartname $revcode $zipurl

        # if we don't have the zip file, download it
        if [ ! -f $zipname ]
        then
            rm -rf zip.tmp
            if wget -nv $zipurl -O zip.tmp
            then
                mv -f zip.tmp $zipname
            else
                rm -f zip.tmp
            fi
        fi

        # if we got the zip file, unzip it
        # but rename the parts similar to VFR charts
        if [ -f $zipname ]
        then
            rm -rf zip.tmp
            mkdir zip.tmp
            unzip -q -d zip.tmp -o $zipname
            if [ "$chartname" == "ELUS6" ]
            then
                spaceNname="ENR ELUS6N $revcode"        # eg, ENR ELUS6N 20150430
                if [ ! -f "$spaceNname.tfw" ] ; then find zip.tmp -iname ENR_L06N.tfwx    -exec mv {} "$spaceNname.tfw" \; ; fi
                if [ ! -f "$spaceNname.tif" ] ; then find zip.tmp -iname ENR_L06N.tif     -exec mv {} "$spaceNname.tif" \; ; fi
                if [ ! -f "$spaceNname.htm" ] ; then find zip.tmp -iname ENR_L06N_tif.htm -exec mv {} "$spaceNname.htm" \; ; fi
                spaceSname="ENR ELUS6S $revcode"        # eg, ENR ELUS6S 20150430
                if [ ! -f "$spaceSname.tfw" ] ; then find zip.tmp -iname ENR_L06S.tfwx    -exec mv {} "$spaceSname.tfw" \; ; fi
                if [ ! -f "$spaceSname.tif" ] ; then find zip.tmp -iname ENR_L06S.tif     -exec mv {} "$spaceSname.tif" \; ; fi
                if [ ! -f "$spaceSname.htm" ] ; then find zip.tmp -iname ENR_L06S_tif.htm -exec mv {} "$spaceSname.htm" \; ; fi
            elif [ "$chartname" == "ELAK2" ]
            then
                spaceCname="ENR ELAK2C $revcode"        # eg, ENR ELAK2C 20150430
                if [ ! -f "$spaceCname.tfw" ] ; then find zip.tmp -iname ENR_AKL02C.tfwx    -exec mv {} "$spaceCname.tfw" \; ; fi
                if [ ! -f "$spaceCname.tif" ] ; then find zip.tmp -iname ENR_AKL02C.tif     -exec mv {} "$spaceCname.tif" \; ; fi
                if [ ! -f "$spaceCname.htm" ] ; then find zip.tmp -iname ENR_AKL02C_tif.htm -exec mv {} "$spaceCname.htm" \; ; fi
                spaceEname="ENR ELAK2E $revcode"        # eg, ENR ELAK2E 20150430
                if [ ! -f "$spaceEname.tfw" ] ; then find zip.tmp -iname ENR_AKL02E.tfwx    -exec mv {} "$spaceEname.tfw" \; ; fi
                if [ ! -f "$spaceEname.tif" ] ; then find zip.tmp -iname ENR_AKL02E.tif     -exec mv {} "$spaceEname.tif" \; ; fi
                if [ ! -f "$spaceEname.htm" ] ; then find zip.tmp -iname ENR_AKL02E_tif.htm -exec mv {} "$spaceEname.htm" \; ; fi
                spaceWname="ENR ELAK2W $revcode"        # eg, ENR ELAK2W 20150430
                if [ ! -f "$spaceWname.tfw" ] ; then find zip.tmp -iname ENR_AKL02W.tfwx    -exec mv {} "$spaceWname.tfw" \; ; fi
                if [ ! -f "$spaceWname.tif" ] ; then find zip.tmp -iname ENR_AKL02W.tif     -exec mv {} "$spaceWname.tif" \; ; fi
                if [ ! -f "$spaceWname.htm" ] ; then find zip.tmp -iname ENR_AKL02W_tif.htm -exec mv {} "$spaceWname.htm" \; ; fi
            elif [ "$chartname" == "EPHI1" ]
            then
                space_name="ENR EPHI1 $revcode"
                if [ ! -f "$space_name.tfw" ] ; then find zip.tmp -iname ENR_P01.tfwx    -exec mv {} "$space_name.tfw" \; ; fi
                if [ ! -f "$space_name.tif" ] ; then find zip.tmp -iname ENR_P01.tif     -exec mv {} "$space_name.tif" \; ; fi
                if [ ! -f "$space_name.htm" ] ; then find zip.tmp -iname ENR_P01_tif.htm -exec mv {} "$space_name.htm" \; ; fi
                spaceGname="ENR EPHI1-GUA $revcode"
                if [ ! -f "$spaceGname.tfw" ] ; then find zip.tmp -iname ENR_P01_GUA.tfwx    -exec mv {} "$spaceGname.tfw" \; ; fi
                if [ ! -f "$spaceGname.tif" ] ; then find zip.tmp -iname ENR_P01_GUA.tif     -exec mv {} "$spaceGname.tif" \; ; fi
                if [ ! -f "$spaceGname.htm" ] ; then find zip.tmp -iname ENR_P01_GUA_tif.htm -exec mv {} "$spaceGname.htm" \; ; fi
            elif [ "${chartname:0:4}" == "Area" ]
            then
                ls zip.tmp | getareacharts
            else
                zipurlbase=`basename $zipurl`           # eg, enr_l01.zip
                zipurlbase=${zipurlbase%%.zip}
                spacename="ENR $chartname $revcode"     # eg, ENR ELUS1 20150430
                if [ ! -f "$spacename.tfw" ] ; then find zip.tmp -iname $zipurlbase.tfwx      -exec mv {} "$spacename.tfw" \; ; fi
                if [ ! -f "$spacename.tif" ] ; then find zip.tmp -iname $zipurlbase.tif       -exec mv {} "$spacename.tif" \; ; fi
                if [ ! -f "$spacename.htm" ] ; then find zip.tmp -iname ${zipurlbase}_tif.htm -exec mv {} "$spacename.htm" \; ; fi
            fi
            rm -rf zip.tmp
        fi
    done
}

effdate_mm=`./cureffdate -28 'mm-dd-yyyy'`
effdate_mmm=`./cureffdate -28 'mmm dd yyyy'`
expdate_mmm=`./cureffdate -28 -x 'mmm dd yyyy'`

echo effdate_mm $effdate_mm
echo effdate_mmm $effdate_mmm
echo expdate_mmm $expdate_mmm

mkdir -p charts
cd charts

wget -nv https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/ifr/ -O ifrcharts.htm

mono --debug ../GetIFRChartNames.exe $effdate_mm < ifrcharts.htm | scanifrchartshtm

