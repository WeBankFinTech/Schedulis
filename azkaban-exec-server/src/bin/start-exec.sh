#!/bin/bash

script_dir=$(dirname $0)

${script_dir}/shutdown-exec.sh

# pass along command line arguments to the internal launch script.
#add  weapm-agent script


${script_dir}/internal/internal-start-executor.sh "$@"

