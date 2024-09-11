# Schedulis 环境部署文档

## 一、环境检查<a name="环境检查">

1. 请基于 Linux 操作系统操作（建议 CentOS）
2. 创建新用户 hadoop， 并为该用户赋予 root 权限，用于部署 Schedulis
3. 准备好 MySQL（版本5.5+） 的客户端和服务端
4. 请确保已安装并且正确配置 JDK（版本1.8+）
5. 配置集群各节点之间的免密码登录
6. 请准备一台已经正确安装和配置 Maven（版本 3.3 - 3.8.1） 和 Git 的机器，用来编译代码
7. 为需要部署的机器运行项目 bin 目录下的环境检测脚本 checkEnv.sh，确认基础环境已经准备完成。若是报错，请用户为部署节点准备好基础环境

## 二、获取项目文件并编译打包（如使用 Releases 中的zip 包则可跳过该步骤）<a name="编译打包">

1. 使用 Git 下载 Schedulis 项目文件 git clone https://github.com/WeBankFinTech/Schedulis.git
2. 下载 jobtypes 插件的依赖和配置，链接：https://share.weiyun.com/RgAiieMx 密码：det7rf（由于文件大小较大，所以放在网盘进行管理），下载时请注意对应版本号（Schedulis 
   jobtypes > Schedulis xxx(version) > jobtypes.zip）
3. 进入项目文件的根目录下，将第二步中下载的 jobtypes 文件解压后，得到 jobtypes 文件夹，将整个 jobtypes 文件夹放入项目 maven module（azkaban-jobtyope）的根目录，然后使用 
   Maven 
   来编译打包整个项目 `mvn clean install -Dmaven.test.skip=true`
   待整个项目编译打包成功后，用户可以在这两个模块(azkaban-web-server 和 azkaban-exec-server)各自的 target 目录下找到相应的 .ZIP 安装包(schedulis_***_web.zip 和 schedulis_***_exec.zip)。<font color="red">这里需要注意：打包完成后一定要确认安装包内是否有plugins目录，如发现安装包没有plugins，或者plugins为空，则分别进入 WebServer 和 ExecServer 目录，为它们单独再次编译即可,如果没有打包进来则无法使用插件</font>。
4. 编译打包后目录说明：
   1. schedulis_***_web.zip 对应 Schedulis 的 WebServer
      1. bin -- WebServer 的启停脚本
      2. conf -- WebServer 的相关配置
      3. lib -- WebServer 依赖库
      4. plugins -- WebServer 插件
      5. web -- WebServer 的 web 资源

   2. schedulis_***_exec.zip 对应 Schedulis 的 ExecutorServer
      1. bin -- Executor Server 的启停脚本
      2. conf -- Executor Server 的相关配置
      3. lib -- Executor Server 依赖库
      4. plugins -- Executor 插件


## 三、确定环境部署模式

Schedulis 提供了两种部署模式：

### 1. 普通版部署模式
普通版部署模式，即单个 WebServer 组合一个及以上 ExecutorServer 的环境部署模式。   

- 点我进入[Schedulis 普通版环境手动部署](#普通版)

- 点我进入[Schedulis 普通版环境自动化部署](#自动化)（比较适合节点较多的情况）

***

### 2. HA 部署模式
HA 部署模式，即多个 WebServer 组合一个及以上 ExecutorServer 的环境部署模式，通过 Nginx 实现 WebServer 间的负载均衡    
点我进入[Schedulis HA 环境部署](#HA)

***

## 四、Schedulis 普通版环境部署 <a name="普通版">

### 一）、复制、解压安装包

1. 使用已创建的 hadoop 用户将以下文件复制到需要部署的 Executor 或者 WebServer 服务器:    
    - Executor 或者 WebServer 安装包 
    - 项目文件根目录下的 bin/construct 目录中的数据库初始化脚本 hdp\_schedulis\_deploy\_script.sql    
2. 将安装包解压到合适的安装目录下，譬如：/appcom/Install/AzkabanInstall， 并确认安装的根目录 /appcom 以及其下子目录的属主为 hadoop 用户， 且赋予 775 权限（/appcom/Install/AzkabanInstall/ 为默认安装目录，建议创建该路径并将其作为安装路径，可避免一些路径的修改）

### 二）、初始化数据库

在 MySQL 中相应的 database 中（也可新建一个），将前面复制过来的数据库初始化脚本导入数据库

```shell
#连接 MySQL 服务端
#eg: mysql -uroot -p12345，其中，username ： root, password: 12345

mysql -uUserName -pPassword -hIP --default-character-set=utf8
```

```sql
#创建一个 Database(按需执行)

mysql> create database schedulis;
mysql> use schedulis; 

# 初始化 Database
#eg: source hdp_schedulis_deploy_script.sql

mysql> source 脚本存放目录/hdp_schedulis_deploy_script.sql
```

### 三）、修改配置<a name="配置">
部署时，建议将配置文件放置统一位置（如：`/appcom/config/schedulis-config/`）进行管理，并将项目 `bin/config` 目录下的目录全部复制到新建的`schedulis-config`目录（如果此时没有源码，可以通过网盘下载链接：https://share.weiyun.com/Rt0cDWO3 密码：i8k7yw）,
再通过软链的方式链接至服务下的 `conf` 
目录下的各个配置文件，方便后续的升级以及重新部署。 
例如：

```shell
# Executor 服务配置软链
ln -sf /appcom/Install/AzkabanInstall/schedulis_{{version}}_exec /appcom/Install/AzkabanInstall/schedulis-exec;
ln -sf /appcom/config/schedulis-config/schedulis-exec/exec_azkaban.properties /appcom/Install/AzkabanInstall/schedulis-exec/conf/azkaban.properties;
ln -sf /appcom/config/schedulis-config/schedulis-exec/common.properties /appcom/Install/AzkabanInstall/schedulis-exec/plugins/jobtypes/common.properties;
ln -sf /appcom/config/schedulis-config/schedulis-exec/commonprivate.properties /appcom/Install/AzkabanInstall/schedulis-exec/plugins/jobtypes/commonprivate.properties;
ln -sf /appcom/config/schedulis-config/schedulis-exec/exec_plugin.properties /appcom/Install/AzkabanInstall/schedulis-exec/plugins/alerter/WebankIMS/conf/plugin.properties;

# WebServer 配置软链
ln -sf /appcom/Install/AzkabanInstall/schedulis_{{version_new}}_web /appcom/Install/AzkabanInstall/schedulis-web;
ln -sf /appcom/config/schedulis-config/schedulis-web/web_azkaban.properties /appcom/Install/AzkabanInstall/schedulis-web/conf/azkaban.properties;
ln -sf /appcom/config/schedulis-config/schedulis-web/web_plugin_ims.properties /appcom/Install/AzkabanInstall/schedulis-web/plugins/alerter/WebankIMS/conf/plugin.properties;
ln -sf /appcom/config/schedulis-config/schedulis-web/web_plugin_system.properties /appcom/Install/AzkabanInstall/schedulis-web/plugins/viewer/system/conf/plugin.properties;
```



为了hive、spark任务能正常执行，需要Executor所在机器安装好Hadoop、Hive、Spark，并将Hadoop、Hive、Spark的安装路径配置软链

```shell
# 配置软链
ln -sf {{HADOOP_HOME}} /appcom/Install/hadoop;
ln -sf {{HIVE_HOME}} /appcom/Install/hive;
ln -sf {{SPARK_HOME}} /appcom/Install/spark;
```



#### 1. 修改 host.properties 文件

此配置文件存放的路径请参考或者修改 ExecServer 安装包下的 bin/internal/internal-start-executor.sh 文件中的 KEY 值 hostConf     
该文件记录的是 Executor 端的所有执行节点 Hostname 和 ServerId， 需保持每台执行机器上的该文件内容一致

示例：

```shell
vi /appcom/config/schedulis-config/host.properties
```
文件内容如下：    
```
executor1_hostname=1
executor2_hostname=2
executor3_hostname=3
```

其中executor1_hostname，executor2_hostname，executor3_hostname 为Executor节点所在机器的真实主机名。

#### 2. Executor Server 配置修改<a name="exec-config">

##### 执行包修改（自动化部署无需执行该步骤）

项目文件根目录下的 bin/construct 目录中任务执行依赖的包 execute-as-user ，复制到 Executor Server 的 lib 下（schedulis_xxx_exec/lib/），并且更新权限
```
sudo chown root execute-as-user
sudo chmod 6050 execute-as-user
```

##### conf/azkaban.properties

此配置文件是 ExecServer 的核心配置文件， 该配置文件存放在 ExecServer 安装包下的 conf 目录下，主要修改配置如下：

```properties
#项目 MySQL 服务端地址（密码用 Java base64 加密）
mysql.port=
mysql.host=
mysql.database=
mysql.user=
mysql.password=
mysql.numconnections=100

#Executor Server 默认端口为 12321，如有冲突可修改
executor.port=12321

#此 server id 请参考《1. 修改 host.properties》 中的 host.properties，改配置会在服务启动的时候自动从host.properties中拉取
executor.server.id=

#Web Sever url相关配置，port 需与 WebServer 的 conf/azkaban.properties 中的 jetty.port 一致，eg: http://localhost:8081
azkaban.webserver.url=http://webserver_ip:webserver_port
```

##### conf/global.properties

该配置文件存放在 ExecServer 安装包下的 conf 目录下，该配置文件主要存放一些 Executor 的全局属性，无需修改

##### plugins/jobtypes/commonprivate.properties

此配置文件存放于 ExecServer 安装包下的 plugins/jobtypes 目录下   
此配置文件主要设置程序启动所需要加载的一些 lib 和 classpath

```
#以下四项配置指向对应组件的安装目录，请将它们修改成相应的组件安装目录
hadoop.home=
hadoop.conf.dir=
hive.home=
spark.home=

#azkaban.native.lib 请修改成 ExecServer 安装目录下 lib 的所在绝对路径
execute.as.user=true
azkaban.native.lib=

```

##### plugins/jobtypes/common.properties

此配置文件存放于 ExecServer 安装包下的 plugins/jobtypes 目录下    
此配置文件主要是设置 DataChecker 和 EventChecker 插件的数据库地址，如不需要这两个插件可不用配置
```
#配置集群 Hive 的元数据库（密码用 Java base64 加密）
job.datachecker.jdo.option.name="job"
job.datachecker.jdo.option.url=jdbc:mysql://host:3306/db_name?useUnicode=true&amp;characterEncoding=UTF-8
job.datachecker.jdo.option.username=[username]
job.datachecker.jdo.option.password=[password]

#配置 Schedulis 的数据库地址（密码用 Java base64 加密）
msg.eventchecker.jdo.option.name="msg"
msg.eventchecker.jdo.option.url=jdbc:mysql://host:3306/db_name?useUnicode=true&characterEncoding=UTF-8
msg.eventchecker.jdo.option.username=[username]
msg.eventchecker.jdo.option.password=[password]


#此部分依赖于第三方脱敏服务mask，暂未开源，将配置写为和job类型一样即可（密码用 Java base64 加密） 

bdp.datachecker.jdo.option.name="bdp"
bdp.datachecker.jdo.option.url=jdbc:mysql://host:3306/db_name?useUnicode=true&amp;characterEncoding=UTF-8
bdp.datachecker.jdo.option.username=[username]
bdp.datachecker.jdo.option.password=[password]


```

##### plugins/alerter/WeBankIMS/conf/plugin.properties

此配置文件存放在 ExecServer 安装包下的 plugins/alerter/WeBankIMS/conf 目录下    
该配置文件主要是设置告警插件地址， 请用户基于自己公司的告警系统来设置    
此部分依赖于第三方告警服务，如不需要可跳过配置

```
# webank alerter settings
alert.type=WeBankAlerter
alarm.server=
alarm.port=
alarm.subSystemID=
alarm.alertTitle=schedulis Aleter Message
alarm.alerterWay=1,2,3
alarm.reciver=
alarm.toEcc=0
```

##### plugins/jobtypes/linkis/plugin.properties

若用户安装了 Linkis（[Linkis 插件安装](#Linkis 安装)），则修改此配置文件来对接 Linkis，该配置文件存放在 ExecServer 安装包下的 plugins/jobtypes/linkis 目录下，并配置gateway地址和token
```
#将该值修改为 Linkis 的gateway地址
wds.linkis.gateway.url=
#此处的token需要和Linkis管理台中已配置的token保持一致
wds.linkis.client.flow.author.user.token=
```

##### plugins/jobtypes/linkis/private.properties

若用户安装了 Linkis（[Linkis 插件安装](#Linkis 安装)），该配置文件存放在 ExecServer 安装包下的 plugins/jobtypes/linkis 目录下，主要是设置 jobtype 所需的 lib 
所在位置

```properties
#将该值修改为 Linkis 插件包下的 lib 目录
jobtype.lib.dir=
```

#### 3. Web Server 配置文件修改<a name="web-config">

##### conf/azkaban.properties

此配置文件是 WebServer 的核心配置文件， 该配置文件存放在 WebServer 安装包下的 conf 目录下，主要修改的配置如下：

```
#项目 MySQL 配置（密码用 base64 加密）
database.type=mysql
mysql.port=
mysql.host=
mysql.database=
mysql.user=
mysql.password=
mysql.numconnections=100

#项目 web 端访问的端口
jetty.port=

# LDAP 登录校验开关（如不需要 LDAP 校验可关闭）
ladp.switch=false
# LDAP 地址配置
ladp.ip=ldap_ip
ladp.port=ldap_port
```

#### 4. 修改日志存放目录（按需修改）

Schedulis 项目的日志默认存放路径为 /appcom/logs/azkaban, 目录下存放的就是 Executor 和 Web 两个服务相关的日志   
若选择使用默认存放路径，则需要按要求将所需路径提前创建出来， 确认文件属主为 hadoop，赋予 775 权限；若要使用自定义的日志存放路径，则需要创建好自定义路径，并修改 ExecServer 和 WebServer 安装包的以下文件：  

1. Executor 下的 bin/internal/internal-start-executor.sh 和 Web 下的 bin/internal/internal-start-web.sh 文件中的 KEY 值 logFile， 设为自定义日志存放路径, 以及在两个文件中关于 “Set the log4j configuration file” 中的 -Dlog4j.log.dir 也修改为自定义的日志路径 
2. 两个服务中的 bin/internal/util.sh 文件中的 KEY 值 shutdownFile，改为自定义日志路径

### 四）、启动

上述步骤完成后，就可以启动了

1. 进入 ExecutorServer 安装包路径，注意不要进到 bin 目录下，执行

   `bin/start-exec.sh`

2. 进入 WebServer 安装包路径，注意不要进到 bin 目录下，执行

   `bin/start-web.sh`

此时若得到提示信息说启动成功，则可以进入验证环节了；若是出错，请查看日志文件，并按需先查看 QA 章节

***

## 五、Schedulis HA 环境部署 <a name="HA">

### 一）、复制、解压安装包

具体步骤同[普通版环境部署](#普通版)

### 二）、 初始化数据库

具体步骤同[普通版环境部署](#普通版)

### 三）、修改配置
- ExecServer 配置文件请参考[普通版部署模式 Executor Server 部分](#exec-config)

- WebServer 配置文件在参考[普通版部署模式](#web-config)的基础上修改如下属性：

  conf/azkaban.properties

```
webserver.ha.model=true
# 所有 web server 的访问 URL，使用逗号分隔
azkaban.all.web.url=
```

### 四）、Nginx 配置修改
- 在安装了 Nginx 的节点中，对 nginx.conf 文件增加以下配置，这里特别注意因为WebServer暂时还没有做分布式session，所以nginx要配置为ip_hash的方式：
```
http {
    log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                      '$status $body_bytes_sent "$http_referer" '
                      '"$http_user_agent" "$http_x_forwarded_for"';

    access_log  /var/log/nginx/access.log  main;

    sendfile            on;
    tcp_nopush          on;
    tcp_nodelay         on;
    keepalive_timeout   65;
    types_hash_max_size 2048;

    include             /etc/nginx/mime.types;
    default_type        application/octet-stream;

    # Load modular configuration files from the /etc/nginx/conf.d directory.
    # See http://nginx.org/en/docs/ngx_core_module.html#include
    # for more information.
    include /etc/nginx/conf.d/*.conf;

    # 将下面server的IP地址修改为WebServer部署节点的IP, 端口号则参考WebServer azkaban.properties中jetty对应的端口
    # 为upstream下的server组命名，此处为"servernode"
    upstream servernode {
        server webServer1:port1;
        server webServer2:port2;
        ip_hash;
    }
	
    # 将下面 server_name 的 IP 地址修改为 Nginx 所在节点的 IP, 端口号为 listen 中定义的端口
    # proxy_pass http://后面为upstream下的server组名称，此处为"servernode"
    # 代表用户可以通过server_name中定义的IP和端口，间接访问到upstream中定义的server组
    server {
        listen  port;
        server_name  nginxIp:port;
        charset utf-8;
        location / {
           proxy_pass http://servernode;
           index  index.html index.htm;
           client_max_body_size    100m;
        }

    }

}
```
- 修改了配置文件后，需要测试配置文件是否存在语法错误，文件中的配置指令需要以分号结尾：
```
# 若已经配置环境变量
nginx -t

# 若没有配置环境变量，则找到相应的nginx脚本所在位置，eg:
/usr/sbin/nginx -t
```
- 若测试后没有报错，则需要重启 Nginx，使配置文件生效:
```
# 若已经配置环境变量
nginx -s reload

# 若没有配置环境变量，则找到相应的nginx脚本所在位置，eg
/usr/sbin/nginx -s reload
```
### 五）、启动

上述步骤完成后，就可以启动了

1. 进入 ExecutorServer 安装包路径，注意不要进到 bin 目录下，执行

   `bin/start-exec.sh`

2. 进入 WebServer 安装包路径，注意不要进到 bin 目录下，执行

   `bin/start-web.sh`

此时若得到提示信息说启动成功，则可以进入验证环节了；若是出错，请查看日志文件，并按需先查看 QA 章节

***

## 六、Schedulis 自动化环境部署 <a name="自动化">
自动化安装比较适合节点较多的情况下的快速配置。

### 一）、使用前置
- 自动化安装依赖ansible，请在 “[一、环境检查](#环境检查)” 的基础上安装ansible
- 自动化部署目前仅支持普通模式
- 安装目录：/appcom/Install/AzkabanInstall
- 配置文件目录：/appcom/config/schedulis-config

### 二）、准备自动化部署脚本
新建 ```/appcom/Install/AzkabanInstall/schedulisdeploy``` 目录,并将项目bin目录下的construct目录和material目录以及下面的文件放入新建的schedulisdeploy目录

### 三）、获取项目文件并编译
编译步骤请参考 “[二、获取项目文件并编译打包](#编译打包)”，将编译后的包放入 ```/appcom/Install/AzkabanInstall/schedulisdeploy/material```

此处需要注意安装包格式
- WebServer：schedulis_version_web
- ExecServer：schedulis_version_exec

### 四）、修改配置

1. 新建 ```/appcom/config/schedulis-config``` 目录，用于集中管理配置，并将项目bin/config目录下的目录全部复制到新建的schedulis-config目录
2. 修改schedulis-exec下的配置，其下为ExecServer的配置文件，具体配置请参考[普通版部署模式](#exec-config)
3. 修改schedulis-web下的配置，其下为WebServer的配置文件，具体配置请参考[普通版部署模式](#web-config)


### 五）、自动化环境搭建
进入 /appcom/Install/AzkabanInstall/schedulisdeploy/construct/ 目录

#### 1. Executor 搭建

```
1.hdp_schedulis_deploy_exec.sh 参数1 参数2
参数1：所部署节点的ip:主机名
参数2：版本号

例： （在10.255.10.99，10.255.10.97部署1.5.0版本）：
# 在需要部署 Executor 的节点上运行以下脚本
sudo sh 1.hdp_schedulis_deploy_exec.sh 10.255.10.99:bdphdp02jobs05，10.255.10.97:bdphdp02jobs04 1.5.0
```

#### 2. WebServer 搭建

```
2.hdp_schedulis_deploy_web.sh 参数1 参数2
参数1：所部署节点的ip:主机名
参数2：版本号

例： （在10.255.10.99部署1.5.0版本）：
# 在部署 WebServer 的节点上运行以下脚本
sudo sh 2.hdp_schedulis_deploy_web.sh 10.255.10.99:bdphdp02jobs05 1.5.0
```

#### 3. 初始化数据库
```
3.hdp_schedulis_deploy_script.sh 参数1 参数2 参数3 参数4 参数5 参数6
参数1：执行脚本版本号
参数2：mysql服务ip地址
参数3：mysql服务端口号
参数4：数据库名称
参数5：数据库用户名
参数6：数据库密码(base64加密)

例： （初始化部署1.5.0版本数据库）：
sudo sh 3.hdp_schedulis_deploy_script.sh 1.5.0 10.255.0.76 3306 schedulisdb root 123456
```

#### 4. 进程启动

```
4.hdp_schedulis_start.sh 参数1 参数2
参数1：服务所在机器IP
参数2：启动类型(exec or web)

例： （在10.255.10.99启动web服务）：
# 在部署 Executor 或者 WebServer 的节点上运行以下脚本来启动服务
sudo sh 4.hdp_schedulis_start.sh 10.255.10.99 web

```

#### 5. 进程校验
```
5.hdp_schedulis_start.sh 参数1 参数2
参数1：exec服务ip
参数2：web服务ip

例： （在10.255.10.99 校验 web 和 exec 是否存在）：
sh 5.hdp_schedulis_process_check.sh 10.255.10.99 10.255.10.99

```

## 七、测试验证
1. 若是单 WebServer 部署模式，则在浏览器中输入 http://webserver_ip:webserver_port    
2. 若是多 WebServer 部署模式，则在浏览器中输入 http://nginx_ip:nginx_port   
3. 在跳出的登录界面输入默认的用户名和密码    
username : superadmin    
pwd : Abcd1234    
4. 成功登录后，请参考用户使用手册，自己创建一个项目并上传测试运行
5. 运行成功，恭喜 Schedulis 成功安装了

## 八、Linkis 插件安装<a name="Linkis 安装">

### 1. 自动化部署安装

[Schedulis Linkis JobType 安装](https://github.com/WeBankFinTech/DataSphereStudio-Doc/blob/main/zh_CN/安装部署/Schedulis_Linkis_JobType安装文档.md)

### 2. 手动安装

1. 下载 linkis 插件的依赖和配置，链接：https://share.weiyun.com/RgAiieMx 密码：det7rf（由于文件大小较大，所以放在网盘进行管理），下载时请注意对应版本号（Schedulis
   jobtypes > Schedulis xxx(version) > linkis-jobtype-xxx.zip）
2. 将 linkis-jobtype-xxx.zip 放至 `schedulis_version_exec/plugins/jobtypes/`目录下并解压得到 linkis 文件夹
3. 修改 plugin.properties，private.properties 配置

## 九、邮件告警配置

1. 修改 WebServer 的 conf/azkaban.properties

   ```
   # mail settings
   # 邮件地址
   mail.sender=azkaban@ptbird.cn
   mail.host=[#Mail_host]
   # 用户名
   mail.user=azkaban
   # 开启 IMAP/SMTP 服务获取的授权码
   mail.password=hadoop
   # 邮件地址
   job.failure.email=
   job.success.email=
   
   # 服务器名
   jetty.hostname=192.168.217.136
   ```

2. 修改 WebServer 以及 Executor Server的 plugins/alerter/webankIMS/conf/plugin.properties

   ```
   alerter.class=com.webank.wedatasphere.schedulis.WeBankAlerter
   # 邮件的用户名
   alerter.name=azkaban
   ```

## 十、QA 环节
1. 如何查看自己本机 Hostname ?   
  命令行输入  ```hostname```

2. 为什么先启动了 Webserver 再启动 Executorserver，没有报错，但在浏览器连接时却提示无法连接？       
  可以使用 jps 命令确认 Webserver 进程是否启动了。一般情况下，建议先启动 ExecutorServer，再 WebServer。否则有可能 WebServer 先启动又被关掉。

3. 两个服务关于 MySQL 的配置中密码已经使用了 base64 加密，日志中还是无法提示连接 MySQL?    
  请注意区分 Linux 下的 base64 加密与 Java base64 加密，我们使用的是后者。

4. 两个服务使用相应的 shutdown 脚本总是提示找不到相应的 Pid?    
  若要关闭两个服务的话，请手动 Kill 掉相应的进程，并删除相应的 currentpid 文件。

5. 怎么重启服务？    
  请参考4，将服务关闭再将服务开启。

6. 为什么 ExecutorServer 显示 Connection Failed，而修改配置后再启动，却提示 Already Started?    
  此处请先将相应的 currentpid 文件删除，再重新启动。

7. 为什么报错了，相应的日志文件没有更新？    
  请先确认配置的日志文件路径是否正确，再将日志文件属主修改为 hadoop 用户，并赋予 775 权限。

8. 上传项目文件后，系统报错？    
  请确认 WebServer 安装包路径的 lib 目录下是否存在 common-lang3.jar，若没有请手动添加。

9. 为什么报错了，却找不到相应的日志文件？    
  请确认已经正确配置日志文件路径。详情请参考参数配置中的修改日志存放路径。

10. 为什么在 Maven 编译的时候会出现 systemPath 不是绝对路径？     
    首先确认是否已经设置了 MAVEN_HOME 的环境变量，并且确认是否已经刷新环境变量文件           
    若是上面步骤都已完成，可以在编译的时候传入参数      
    ```mvn install -Denv.MAVEN_HOME=dir of local repository set in settings.xml```

11. 编译时出现错误 “Could not find artifactor xxx”?     
    请确保 Maven下 conf/settigs.xml 或者用户的 settings.xml 是否有正确配置镜像地址和远程仓库地址

12. 项目文件路径和安装包路径有什么区别？    
    项目文件路径是 Git 下载下来后项目文件存放的地址；安装包路径是使用 Maven 编译后将安装包解压后存放的地址；数据库初始化脚本存于项目文件路径下；其他的参数配置文件都在安装包路径下

13. 为什么使用 Executor 启动脚本启动 Executor 时，先是提示启动成功，后面又一直出现更新数据库失败的提示？    
    请耐心等待，直到确认已经全部失败后，再查看日志确认具体报错原因。

14. 为什么在启动 Executor 的时候，先是提示启动成功，后面就一直卡在更新数据库失败的提示很久，失败次数也没有更新？    
    对于这种情况，很大原因是数据库连接时出现了问题，请停止启动进程，并查看日志确认错误原因。按需查看 QA 章节。

15. schedulis点击用户参数和系统管理tab页为什么无法跳转？

    该问题的原因是schedulis中没有hadoop用户，在schedulis中创建hadoop用户即可。
