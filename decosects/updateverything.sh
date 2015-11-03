#!/bin/bash -v
#
#  Updates everything
#
echo STARTING UPDATE ; date
./get_aptinfo.sh
echo END GET_APTINFO.SH ; date
./get_aptplates.sh
echo END GET_APTPLATES.SH ; date
./decodeallplates.sh
echo END DECODEALLPLATES.SH ; date

./vfr_charts_download.sh
echo END VFR_CHARTS_DOWNLOAD.SH ; date
./ifr_charts_download.sh
echo END IFR_CHARTS_DOWNLOAD.SH ; date
./genallchartcsvs.sh
echo END GENALLCHARTCSVS.SH ; date
./readalltiffs.sh
echo END READALLTIFFS.SH ; date

## ./purgeoldcharts.sh

echo UPDATE COMPLETE
date

