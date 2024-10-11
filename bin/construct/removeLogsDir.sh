#!/bin/bash

set -x

sudo find /data/bdp/tmp/bdp-data -mtime +7  -type f -exec rm -f {} \;
sudo find /data/bdp/tmp/bdp-data -atime +7  -type d -empty | xargs rmdir 
sudo find /data/bdp-job/bdp-tss-job/logs -mtime +8  -type f -exec rm -f {} \;
sudo find /data/bdp/logs/bdp-tss -mtime +8  -type f -exec rm -f {} \;
sudo find /appcom/logs/bdp-tss -mtime +8  -type f -exec rm -f {} \;
sudo find /appcom/logs/azkaban -mtime +7  -type f -exec rm -f {} \;
sudo find /appcom/logs/lineage/hive/ -mtime +3 -type f -name "*log*" -exec rm -f {} \; >> /appcom/logs/lineage/hive/removeLogFile.log 2>&1
sudo find /appcom/logs/lineage/sqoop/ -mtime +3 -type f -name "*log*" -exec rm -f {} \; >> /appcom/logs/lineage/sqoop/removeLogFile.log 2>&1
sudo find /appcom/logs/lineage/spark/ -mtime +3 -type f -name "*spark*log*" -exec rm -f {} \; >> /appcom/logs/lineage/spark/removeLogFile.log 2>&1
