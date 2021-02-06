#!/bin/bash
cd `dirname $0`
set -e -x

time mono --debug ReadEuroIAPPng.exe -stages \
    ReadEuroIAPPng_learnpngs/ED_AD_2_EDBC_4-2-1_en_2016-07-21.png \
    -csvoutfile ED_AD_2_EDBC_4-2-1_en_2016-07-21.csv \
    -csvoutid EDBC-ILS-26

time mono --debug ReadEuroIAPPng.exe -stages \
    ReadEuroIAPPng_learnpngs/ED_AD_2_EDBH_4-6-1_en_2019-04-25.png \
    -csvoutfile ED_AD_2_EDBH_4-6-1_en_2019-04-25.csv \
    -csvoutid EDBH-GPS-27

time mono --debug ReadEuroIAPPng.exe -stages \
    ReadEuroIAPPng_learnpngs/LA_AD_2_LATI_24-23_en_2016-12-08.png \
    -csvoutfile LA_AD_2_LATI_24-23_en_2016-12-08.csv \
    -csvoutid LATI-ILS-17

####time mono --debug ReadEuroIAPPng.exe -stages \
####    ReadEuroIAPPng_learnpngs/LO_AD_2_LOWG_24-6-1_en_2020-08-13.png \
####    -csvoutfile LO_AD_2_LOWG_24-6-1_en_2020-08-13.csv \
####    -csvoutid LOWG-NDB-35C

rm -f stage*.png

csvsedbcils26=`sed 's/.*,BIL,/BIL,/g' ED_AD_2_EDBC_4-2-1_en_2016-07-21.csv`
csvsedbhgps27=`sed 's/.*,BIL,/BIL,/g' ED_AD_2_EDBH_4-6-1_en_2019-04-25.csv`
csvslatiils17=`sed 's/.*,BIL,/BIL,/g' LA_AD_2_LATI_24-23_en_2016-12-08.csv`

rm -f datums/statezips_20210128/EUR-*.zip

rm -rf zz
mkdir zz
ln ReadEuroIAPPng_learnpngs/ED_AD_2_EDBC_4-2-1_en_2016-07-21.png zz/ED_AD_2_EDBC_4-2-1_en_2016-07-21.png.p1
ln ReadEuroIAPPng_learnpngs/ED_AD_2_EDBH_4-6-1_en_2019-04-25.png zz/ED_AD_2_EDBH_4-6-1_en_2019-04-25.png.p1
ln ReadEuroIAPPng_learnpngs/LA_AD_2_LATI_24-23_en_2016-12-08.png zz/LA_AD_2_LATI_24-23_en_2016-12-08.png.p1
cd zz
touch apdgeorefs.csv
touch iapcifps.csv
echo 'CSO,"IAP-ILS RWY 26",ED_AD_2_EDBC_4-2-1_en_2016-07-21.png'  > aptplates.csv
echo 'BBH,"IAP-GPS RWY 27",ED_AD_2_EDBH_4-6-1_en_2019-04-25.png' >> aptplates.csv
echo "EDBC,\"IAP-ILS RWY 26\",\"$csvsedbcils26\""  > iapgeorefs3.csv
echo "EDBH,\"IAP-GPS RWY 27\",\"$csvsedbhgps27\"" >> iapgeorefs3.csv
zip ../datums/statezips_20210128/EUR-ED.zip *.csv ED*.p1
echo 'TIA,"IAP-ILS RWY 17",LA_AD_2_LATI_24-23_en_2016-12-08.png'  > aptplates.csv
echo "LATI,\"IAP-ILS RWY 17\",\"$csvslatiils17\""  > iapgeorefs3.csv
zip ../datums/statezips_20210128/EUR-LA.zip *.csv LA*.p1
cd ..
rm -rf zz

cp /dev/null ~/nfs/EuroTest_waypts.sql
echo "UPDATE airports SET apt_state='EUR-ED' WHERE apt_icaoid LIKE 'ED__' AND (SUBSTR(apt_icaoid,3,1) BETWEEN 'A' AND 'Z') AND (SUBSTR(apt_icaoid,4,1) BETWEEN 'A' AND 'Z');" >> ~/nfs/EuroTest_waypts.sql
echo "UPDATE airports SET apt_state='EUR-LA' WHERE apt_icaoid LIKE 'LA__' AND (SUBSTR(apt_icaoid,3,1) BETWEEN 'A' AND 'Z') AND (SUBSTR(apt_icaoid,4,1) BETWEEN 'A' AND 'Z');" >> ~/nfs/EuroTest_waypts.sql
echo ".quit" >> ~/nfs/EuroTest_waypts.sql

##  adb shell sqlite3 /sdcard/Android/data/com.outerworldapps.wairtonow/files/nobudb/waypointsoa_20210128.db < ~/nfs/EuroTest_waypts.sql
##  adb shell sqlite3 /sdcard/Android/data/com.outerworldapps.wairtonow/files/nobudb/waypointsofm_20210128.db < ~/nfs/EuroTest_waypts.sql

