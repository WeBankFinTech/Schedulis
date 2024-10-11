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

#create schedulis-config directory
#创建schedulis 配置文件目录
mkdir -p /appcom/config/schedulis-config/

#joint Hostname
#拼接hostname
all_nodes=${all_nodes//,/ }
for element in $all_nodes
do
echo $element |awk -F ':' '{print $1 " name="$2}'>>$v_hosts
done

#execute ansible-playbook to install schedulis web
#执行ansible-playbook脚本来安装schedulis web
/usr/bin/ansible-playbook -i ${v_hosts} --extra-vars version=${version} hdp_schedulis_deploy_web.yml

status=$?

if [ -f ${v_hosts} ];then
   rm -rf ${v_hosts}
fi

exit $status
