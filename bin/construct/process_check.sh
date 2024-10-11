# !/bin/bash
##########################################
# 2019.01.03 by paulsenzou 
# 根据参数校验进程存在性
# $1:进程名称(多个进程名用','隔开)
##########################################

process=$1

array=(${process//,/ })  
result=0;

for var in ${array[@]}
do
   num=`ps -ef | grep $var | grep -v grep|grep -v $0 | wc -l`
   echo "there is $num $var process running"
   if [ $num -lt 1 ];then
       echo "$var not exist"
       result=-1
   fi
done 

if [ $result -ne 0 ];then
    exit $result
fi
