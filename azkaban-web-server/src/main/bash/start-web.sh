#!/bin/bash

script_dir=$(dirname $0)

cycle_stop=$1

${script_dir}/internal/internal-start-web.sh ${cycle_stop}
