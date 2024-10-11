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
cycle_stop=$1

# Specifies location of azkaban.properties, log4j.properties files
# Change if necessary
conf=$azkaban_dir/conf
logFile=/appcom/logs/azkaban/webServerLog_`date +%F+%T`.out


function preCheck(){
  LOG INFO "checking AzkabanWebServer status..."
  processName=`jps|grep AzkabanWebServer`
  if [ -n "$processName" ]
  then
      LOG INFO "AzkabanWebServer already started."
      return 1
  else
      return 0
  fi
}


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

  echo "app home: $azkaban_dir" >> $logFile
  echo "classpath: $CLASSPATH" >> $logFile
}

function javaOption(){
  LOG INFO "setting java option..."
  if [[ -z "$tmpdir" ]]; then
    tmpdir=/tmp
  fi

  if [[ -z "$AZKABAN_OPTS" ]]; then
    AZKABAN_OPTS="-Xmx16G -Xloggc:/appcom/logs/azkaban/gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintHeapAtGC"
  fi
  # Set the log4j configuration file
  if [ -f $conf/log4j2.xml ]; then
    AZKABAN_OPTS="$AZKABAN_OPTS -Dlog4j.configurationFile=$conf/log4j2.xml"
  else
    LOG ERROR "$conf/log4j.properties file doesn't exist."
    return 1
  fi

  executorport=`cat $conf/azkaban.properties | grep executor.port | awk -F '=' '{print($NF)}'`

  AZKABAN_OPTS="$AZKABAN_OPTS -server -Dcom.sun.management.jmxremote -Djava.io.tmpdir=$tmpdir -Dexecutorport=$executorport -Dserverpath=$azkaban_dir"

  #AZKABAN_OPTS="$AZKABAN_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006"

}

function start(){
    LOG INFO "starting AzkabanWebServer..."
    java $AZKABAN_OPTS $JAVA_LIB_PATH -cp $CLASSPATH azkaban.webapp.AzkabanWebServer -conf $conf $@ >> $logFile 2>&1 &
    echo $! > $azkaban_dir/currentpid
    sleep 3s
    processName=`jps|grep AzkabanWebServer`
    if [ ! -n "$processName" ]
    then
        LOG INFO "AzkabanWebServer startup failed"
        return 1
    else
        return 0
    fi
}

function LOG(){
  currentTime=`date "+%Y-%m-%d %H:%M:%S.%3N"`
  echo "$currentTime [${1}] ($$) $2" | tee -a $logFile
}

main(){
  preCheck || return 0
  loadClasspath || { LOG ERROR "load classpath , failed." ; return 1; }
  javaOption || { LOG ERROR "setting javaOption , failed." ; return 2; }
  start $* || { LOG ERROR "start AzkabanWebServer , failed." ; return 3; }

}

main $*

ret=$?
#todo: 记录执行用户的ip和退出码到操作系统日志
exit $ret



