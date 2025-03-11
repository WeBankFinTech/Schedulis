#!/usr/bin/env bash
# Common utils
set -o nounset   # exit the script if you try to use an uninitialised variable
set -o errexit   # exit the script if any statement returns a non-true return value

local_ip=$(hostname -I| grep -o -e '[0-9]\{1,3\}.[0-9]\{1,3\}.[0-9]\{1,3\}.[0-9]\{1,3\}' |head -n 1)

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
  max_attempt=3
  if [ -f ${install_dir}/currentpid ]
  then
    pid=`cat ${install_dir}/currentpid`
    [ "$pid" == "" ] && { echo "error: pid is empty."; return 1; }
  else
    echo "error: can not found ${install_dir}/currentpid."
    return 2
  fi

  (kill_process_with_retry "${pid}" "${process_name}" "${max_attempt}" && merge_web_status $install_dir 0)

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
     echo "ERROR: process name ${pname} with pid: ${pid} not found"
     exit 1
   fi

   for try in $(seq 1 $maxattempt); do
      echo "Killing $pname. [pid: $pid], attempt: $try"
      kill ${pid}
      sleep 5
      if is_process_running $pid; then
        echo "$pname is not dead [pid: $pid]"
        echo "sleeping for $sleeptime seconds before retry"
        sleep $sleeptime
      else
        echo "shutdown succeeded"
        return 0
      fi
   done

   echo "Error: unable to kill process for $maxattempt attempt(s), killing the process with -9"
   kill -9 $pid
   sleep $sleeptime

   if is_process_running $pid; then
      echo "$pname is not dead even after kill -9 [pid: $pid]"
      return 1
   else
    echo "shutdown succeeded"
    return 0
   fi
}

function merge_web_status {
  install_dir="$1"
  local operate_type="$2"
  local hostname=$(hostname)
  local port=$(get_prop $install_dir "mysql.port")
  local host=$(get_prop $install_dir "mysql.host")
  local database=$(get_prop $install_dir "mysql.database")
  local user=$(get_prop $install_dir "mysql.user")
  local password=$(get_prop $install_dir "mysql.password")
  local hastatus=$(get_prop $install_dir "webserver.ha.model")
  if [ "$hastatus" = "true" ]; then
    hastatus=1
  else
    hastatus=0
  fi

  local merge_sql
  if [[ $operate_type == 0 ]]; then
    merge_sql="insert into webservers (host_name,ip,ha_status,running_status,shutdown_time) values ('${hostname}','${local_ip}',${hastatus},'0',now()) ON DUPLICATE KEY UPDATE host_name=VALUES(host_name),ha_status=VALUES(ha_status),running_status=VALUES(running_status),shutdown_time=VALUES(shutdown_time)"
  else
    merge_sql="insert into webservers (host_name,ip,ha_status,running_status,start_time) values ('${hostname}','${local_ip}',${hastatus},'1',now()) ON DUPLICATE KEY UPDATE host_name=VALUES(host_name),ha_status=VALUES(ha_status),running_status=VALUES(running_status),start_time=VALUES(start_time)"
  fi
  
  local mysqlsec_prop=$install_dir/conf/mysqlsec.properties
  if [ -f "$mysqlsec_prop" ]; then
    mysqlsec --dpmc $mysqlsec_prop -h $host -P $port $database -e "$merge_sql"
  else
    mysql -h $host -u $user -P $port -p$password --default-character-set=utf8 $database -e "$merge_sql"
  fi

}

function decrypt_pwd {
  local bin_dir=${install_dir}/bin
  local lib_dir=${install_dir}/lib
  local password="$1"
  local privatekey="$2"
  local cpath=$lib_dir
  echo -e 'import bsp.encrypt.EncryptUtil;\npublic class CryptoUtils {\npublic static void main(String[] args){\ntry{\nSystem.out.println(EncryptUtil.decrypt(args[0], args[1]));\n}catch(Exception e){System.out.println(e.getMessage());\n}\n}\n}\n' >> ${bin_dir}/CryptoUtils.java
  for file in ${lib_dir}/encrypt-*.jar;
  do
    cpath=$cpath:$file
  done
  javac -cp $cpath ${bin_dir}/CryptoUtils.java -d ${bin_dir}/
  decpwd=$(java -cp ${bin_dir}:$cpath:${bin_dir}/CryptoUtils.class CryptoUtils $privatekey $password)
  rm -f ${install_dir}/bin/CryptoUtils.java
  rm -f ${install_dir}/bin/CryptoUtils.class
}

function get_prop {
  conf_file=${1}/conf/azkaban.properties
  [ -f ${conf_file} ] && grep -P "^\s*[^#]?${2}=.*$" ${conf_file} | cut -d'=' -f2
}

