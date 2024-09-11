schedulis_jdbc_ip=$2
schedulis_jdbc_port=$3
schedulis_jdbc_db=$4
schedulis_jdbc_username=$5
schedulis_jdbc_passwd=$(echo $6 |base64 -d)
sqlscript_version=$1

result_check(){
  if [ "$?" -ne "0" ];then
    echo $1
    exit 1
  fi;
}

#check mysql connection
#校验mysql连接的正确性
mysql -h $schedulis_jdbc_ip -u $schedulis_jdbc_username -P $schedulis_jdbc_port -p$schedulis_jdbc_passwd << EOF
exit
EOF

result_check "can not connect mysql connection,please check config!"

#make sure script exists
#确定脚本文件存在与否
if [ ! -f "hdp_schedulis_deploy_script.sql" ];then
    echo "hdp_schedulis_deploy_script.sql not exists!"
    exit 1
fi;

#start init database
#开始初始化数据库
echo "start init database"
mysql -h $schedulis_jdbc_ip -u $schedulis_jdbc_username -P $schedulis_jdbc_port -p$schedulis_jdbc_passwd << EOF
CREATE DATABASE if not exists $schedulis_jdbc_db DEFAULT CHARACTER SET utf8;
USE $schedulis_jdbc_db;
EOF

result_check "init schedulis database failed!"

#start init data
#初始化数据
echo "start init data"
mysql -h $schedulis_jdbc_ip -u $schedulis_jdbc_username -P $schedulis_jdbc_port -p$schedulis_jdbc_passwd $schedulis_jdbc_db < hdp_schedulis_deploy_script.sql

result_check "init schedulis data failed!"

echo "init schedulis database success!"
