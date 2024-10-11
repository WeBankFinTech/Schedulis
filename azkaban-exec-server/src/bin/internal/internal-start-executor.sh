#!/bin/bash

verify_java_home() {
  if [ -z "$JAVA_HOME" ]; then
    cat 1>&2 <<EOF
+======================================================================+
|      Error: JAVA_HOME is not set and Java could not be found         |
+----------------------------------------------------------------------+
EOF
    exit 1
  fi

  echo "JAVA_HOME=$JAVA_HOME"
}


verify_java_home
export PATH=$JAVA_HOME/bin:$JAVA_HOME/jre/bin:$PATH

azkaban_dir=$(dirname $0)/../..

# Specifies location of azkaban.properties, log4j.properties files
# Change if necessary
conf=$azkaban_dir/conf

hostConf=/appcom/config/schedulis-config/host.properties

logFile=/appcom/logs/azkaban/executorServerLog__`date +%F+%T`.out


function loadClasspath(){
  LOG INFO "setting java CLASSPATH..."
  for file in $azkaban_dir/lib/*.jar;
  do
    CLASSPATH=$CLASSPATH:$file
  done

  for file in $azkaban_dir/extlib/*.jar;
  do
    CLASSPATH=$CLASSPATH:$file
  done

  for file in $azkaban_dir/plugins/*/*.jar;
  do
    CLASSPATH=$CLASSPATH:$file
  done

  if [ "$HADOOP_HOME" != "" ]; then
    LOG INFO "Using Hadoop from $HADOOP_HOME"
    CLASSPATH=$CLASSPATH:$HADOOP_HOME/conf:$HADOOP_HOME/*
    JAVA_LIB_PATH="-Djava.library.path=$HADOOP_HOME/lib/native/Linux-amd64-64"
  else
    LOG WARN "HADOOP_HOME is not set. Hadoop job types will not run properly."
  fi

  if [ "$HIVE_HOME" != "" ]; then
    LOG INFO "Using Hive from $HIVE_HOME"
    CLASSPATH=$CLASSPATH:$HIVE_HOME/conf:$HIVE_HOME/lib/*
  fi

  CLASSPATH=${CLASSPATH}`find $HADOOP_HOME/share/hadoop/common/ -name "*.jar" | grep -v sources | awk '{sum=(sum":"$1)} END {print(sum)}'`
  CLASSPATH=${CLASSPATH}`find $HADOOP_HOME/share/hadoop/yarn/ -name "*.jar" | grep -v sources | awk '{sum=(sum":"$1)} END {print(sum)}'`
  CLASSPATH=${CLASSPATH}`find $HADOOP_HOME/share/hadoop/hdfs/ -name "*.jar" | grep -v sources | awk '{sum=(sum":"$1)} END {print(sum)}'`
  CLASSPATH=${CLASSPATH}`find $HADOOP_HOME/share/hadoop/mapreduce/ -name "*.jar" | grep -v sources | awk '{sum=(sum":"$1)} END {print(sum)}'`

  CLASSPATH=$CLASSPATH:$HADOOP_CONF_DIR
  echo "app home: $azkaban_dir" >> $logFile
  echo "CLASSPATH: $CLASSPATH" >> $logFile
  
}

function javaOption(){
  LOG INFO "setting java option..."
  if [[ -z "$tmpdir" ]]; then
    tmpdir=/tmp
  fi

  if [[ -z "$AZKABAN_OPTS" ]]; then
    AZKABAN_OPTS="-Xmx8G"
  fi
  # Set the log4j configuration file
  if [ -f $conf/log4j2.xml ]; then
    AZKABAN_OPTS="$AZKABAN_OPTS -Dlog4j.configurationFile=$conf/log4j2.xml"
  else
    LOG ERROR "$conf/log4j2.xml file doesn't exist."
    return 1
  fi
  executorport=`cat $conf/azkaban.properties | grep executor.port | awk -F '=' '{print($NF)}'`
  AZKABAN_OPTS="$AZKABAN_OPTS -server -Dcom.sun.management.jmxremote -Djava.io.tmpdir=$tmpdir -Dexecutorport=$executorport -Dserverpath=$azkaban_dir"

  #AZKABAN_OPTS="$AZKABAN_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
  
}

function start(){
  LOG INFO "Starting AzkabanExecutorServer on port $executorport ..."
  java $AZKABAN_OPTS $JAVA_LIB_PATH -cp $CLASSPATH azkaban.execapp.AzkabanExecutorServer -conf $conf $@ >> $logFile 2>&1 &
  echo $! > $azkaban_dir/currentpid
  sleep 3s
  processName=`jps|grep AzkabanExecutorServer`
  if [ ! -n "$processName" ]
  then
      LOG INFO "AzkabanExecutorServer startup failed"
      return 1
  else
      return 0
  fi
}

function genServerId(){
  LOG INFO "setting executor id..."
  [ -f $hostConf ] || { LOG ERROR "$hostConf doesn't exist."; return 1; }
  [ -f $azkaban_dir/conf/azkaban.properties ] || { LOG ERROR "$azkaban_dir/conf/azkaban.properties doesn't exist."; return 1; }
  serverName=`hostname`
  serverId=`grep -E "^$serverName" $hostConf | awk -F '=' '{print($NF)}'`
  [ "$serverId" == "" ] && { LOG ERROR "can not found server Id in $hostConf"; return 2; }
  line=`grep -En "^executor.server.id" $azkaban_dir/conf/azkaban.properties | awk -F ":" '{print($1)}'`
  [ "$line" == "" ] && { LOG ERROR "can not found executor.server.id in $azkaban_dir/conf/azkaban.properties"; return 3; }
  sed -i --follow-symlinks "${line}c executor.server.id=$serverId" $azkaban_dir/conf/azkaban.properties
}

function getValue(){
  sp='='
  [ "$3" != "" ] && sp=$3
  grep -E "^$1" $2 | awk -F "$sp" '{print($NF)}' | sed 's/[\r\n]//g'
}

function updataExecutorStatus(){
  LOG INFO "start update executor status..."
  LOCAL_HOSTNAME="`hostname`"
  local azkabanConf=$azkaban_dir/conf/azkaban.properties
  EXECUTOR_PORT=`getValue "executor.port" $azkabanConf`
  start_finish=0
  runtime=0
  while [[ $start_finish != 1 ]]; do
      result=`curl -POST http://${LOCAL_HOSTNAME}:${EXECUTOR_PORT}/executor -d action=activate`
      LOG INFO " exectue result: ${result}"
      [[ "${result}" =~ .*success.* ]] && { break; }
      sleep 3s
      runtime=$(( $runtime + 1 ))
      LOG INFO "It has been run： ${runtime}  and will exit after 10 times。"
      if [ ${runtime} -gt 10 ]
      then
          LOG ERROR "update executor status time out."
          return 1
      fi
  done
  LOG INFO "update executor success."
  LOG INFO "AzkabanExecutorServer started successfully."
}

function preCheck(){
  LOG INFO "checking AzkabanExecutorServer status..."
  processName=`jps|grep AzkabanExecutorServer`
  if [ -n "$processName" ]
  then
      LOG INFO "AzkabanExecutorServer already started."
      return 1
  else
      return 0
  fi
}

main(){
  preCheck || return 0
  loadClasspath || { LOG ERROR "load classpath , failed." ; return 1; }
  javaOption || { LOG ERROR "setting java option , failed." ; return 2; }
  genServerId || { LOG ERROR "gen Server Id, failed." ; return 3; }
  start $* || { LOG ERROR "start AzkabanExecutorServer , failed." ; return 4; }
  updataExecutorStatus || { LOG ERROR "updata Executor Status , failed." ; return 5; }
}

function LOG(){
  currentTime=`date "+%Y-%m-%d %H:%M:%S.%3N"`
  echo "$currentTime [${1}] ($$) $2" | tee -a $logFile
}

main $*

#todo: 记录执行用户的ip和退出码到操作系统日志
exit $?


