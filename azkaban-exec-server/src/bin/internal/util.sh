#!/usr/bin/env bash
# Common utils

shutdownlogFile=/appcom/logs/azkaban/executorServerShutdownLog__`date +%F+%T`.out
ALL_PROCESS_PID=""


#---
# is_process_running: Checks if a process is running
# args:               Process ID of running proccess
# returns:            returns 0 if process is running, 1 if not found
#---
function is_process_running {
  local  pid=$1
  kill -0 $pid > /dev/null 2>&1 #exit code ($?) is 0 if pid is running, 1 if not running
  local  status=$?              #because we are returning exit code, can use with if & no [ bracket
  return $status
}

#---
# args:               Process name of a running process to shutdown, install directory
# returns:            returns 0 if success, 1 otherwise
#---
function common_shutdown {
  process_name="$1"
  install_dir="$2"
  max_attempt=5
  pid=`cat ${install_dir}/currentpid`

  kill_process_with_retry "${pid}" "${process_name}" "${max_attempt}"
  afterKill
  if [[ $? == 0 ]]; then
    rm -f ${install_dir}/currentpid
    return 0
  else
    return 1
  fi
}

#---
# kill_process_with_retry: Checks and attempts to kill the running process
# args:                    PID, process name, number of kill attempts
# returns:                 returns 0 if kill succeds or nothing to kill, 1 if kill fails
# exception:               If passed a non-existant pid, function will forcefully exit
#---
function kill_process_with_retry {
   local pid="$1"
   local pname="$2"
   local maxattempt="$3"
   local sleeptime=5

   if ! is_process_running $pid ; then
     LOG ERROR "process name ${pname} with pid: ${pid} not found"
     exit 1
   fi
   getAllProcessPid $pid

   for try in $(seq 1 $maxattempt); do
      LOG INFO "Killing $pname. [pid: $pid], attempt: $try, sleep time: 30s"
      kill -15 ${pid}
      sleep 30
      if is_process_running $pid; then
        LOG INFO "$pname is not dead [pid: $pid]"
        LOG INFO "sleeping for $sleeptime seconds before retry"
        sleep $sleeptime
      else
        LOG INFO "shutdown succeeded"
        killAllProcess
        return 0
      fi
   done

   LOG "ERROR" "unable to kill process for $maxattempt attempt(s), killing the process with -9"
   killAllProcess
   sleep $sleeptime

   if is_process_running $pid; then
      LOG INFO "$pname is not dead even after kill -9 [pid: $pid]"
      return 1
   else
    LOG INFO "shutdown succeeded"
    return 0
   fi
}

function LOG(){
  currentTime=`date "+%Y-%m-%d %H:%M:%S.%3N"`
  if [ "$1" == "DEBUG" ]
  then
    echo "$currentTime [${1}] ($$) $2" >> $shutdownlogFile
  else
    echo "$currentTime [${1}] ($$) $2" | tee -a $shutdownlogFile
  fi
}

function afterKill() {
  LOG "INFO" "check process status"
  executeAsUserPids=`ps -ef | grep 'execute-as-user' | grep -v grep | awk '{print($2)}'`
  azkabanJobtype=`ps -ef | grep 'azkaban.jobtype' | grep -v grep | awk '{print($2)}'`
  LOG DEBUG "executeAsUserPids : $executeAsUserPids"
  LOG DEBUG "azkabanJobtype : $azkabanJobtype"
}


function getAllProcessPid() {
  LOG INFO "get all process pid..."
  ALL_PROCESS_PID="`pstree -p $1 | grep -oE '\([0-9]+\)' | grep -oE '[0-9]+'`"
}


function killAllProcess() {
  LOG INFO "killing all process..."
  LOG DEBUG "kill process pid: $ALL_PROCESS_PID"
  [ "$ALL_PROCESS_PID" != "" ] && sudo kill -9 $ALL_PROCESS_PID > /dev/null 2>&1
  return 0
}
