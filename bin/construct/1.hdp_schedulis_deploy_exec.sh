#generate ansible-playbook inventory
#生成ansible-playbook 执行清单
all_nodes=$1
version=$2

UUID=${cat /proc/sys/kernel/random/uuid}
mkdir -p /data/change/client
v_hosts=/data/change/client/hdp_client.hosts${UUID}
echo >$v_hosts
cat >$v_hosts <<EOF
[all:vars]
ansible_ssh_port=[#ssh_port]
ansible_ssh_pass=[#ssh_password]

[all_nodes]
EOF

#create schedulis-config directory and host.properties
#创建schedulis 配置文件目录 和 host属性文件
mkdir -p /appcom/config/schedulis-config/
touch /appcom/config/schedulis-config/host.properties

#corresponding relations between IP and Hostname
#拼接IP和Hostname的对应关系
all_nodes=${all_nodes//,/ }
for element in $all_nodes
do
echo $element |awk -F ':' '{print $1 " name="$2}'>>$v_hosts
hmnum=`echo $element |awk -F ":" '{print $2}'`
#get the last two char of the hostname
num=`echo ${hmnum: -2}`
echo "${hmnum}=${num}" >> /appcom/config/schedulis-config/host.properties
done

#execute ansible-playbook to install schedulis executor
#执行ansible-playbook脚本来安装executor
/usr/bin/ansible-playbook -i ${v_hosts} --extra-vars version=${version} hdp_schedulis_deploy_exec.yml

status=$?
if [ -f ${v_hosts} ];then
   rm -rf ${v_hosts}
fi

#remove host.properties file after installing
#在安装结束后删除host.properties文件
rm -rf /appcom/config/schedulis-config/host.properties

exit $status
