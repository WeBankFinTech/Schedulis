# Schedulis 版本升级手册
## 1. 新版本 schedulis_xxx_web.zip, schedulis_xxx_exec.zip 准备
具体步骤可参考 《[Schedulis 环境部署文档](./schedulis_deploy_cn.md)》 第二节 “获取项目文件并编译打包（如使用 Releases 中的zip 包则可跳过该步骤）”
## 2. 配置修改
将配置文件更改为要升级版本的配置，建议通过与旧版本的配置进行对比修改，避免多次修改配置。
## 3. 停止旧版本服务
1. 进入旧版本 WebServer 的目录下（schedulis_version_web 目录下），执行
    ```shell
    # 停止 WebServer
    bin/shutdown-web.sh
    ```
2. 进入旧版本 Executor 服务的目录下（schedulis_version_exec 目录下），执行
    ```shell
    # 停止 Executor 服务
    bin/shutdown-exec.sh
    ```
## 4. 数据库升级
运行新版本的数据库脚本，脚本位置：项目文件根目录下的 bin/construct 目录中的数据库初始化脚本 hdp\_schedulis\_deploy\_script.sql。
在 MySQL 中相应的 database 中，将前面复制过来的数据库脚本导入数据库，例：
```shell
mysql> use schedulis; 

# 初始化 Database
#eg: source hdp_schedulis_deploy_script.sql

mysql> source 脚本存放目录/hdp_schedulis_deploy_script.sql
```

## 5. 启动新版本服务
1. 进入新版本 Executor 的目录下（schedulis_version_exec 目录下），执行
    ```shell
    # 启动 Executor 服务
    bin/start-exec.sh
    ```
2. 进入新版本 WebServer 服务的目录下（schedulis_version_web 目录下），执行
    ```shell
    # 启动 WebServer
    bin/start-web.sh
    ```