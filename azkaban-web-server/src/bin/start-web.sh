#!/bin/bash

script_dir=$(dirname $0)

cycle_stop=$1

#weapmEnabled=$1
#if [ ${weapmEnabled}'x' == 1'x' ]; then
#  echo "start app with weapm-agent."
#  weapmEnabled = weapm
#fi

${script_dir}/internal/internal-start-web.sh ${cycle_stop}
