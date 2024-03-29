## Schedulis 与业界现有调度系统的比较

| 模块 | 描述 | Schedulis | Azkaban | Dolphin | Airflow | Xxl-job |
| :----: | :----: |-------|-------|-------|-------|-------|  
| HA | 调度中心高可用 | 支持 | 不支持 | 支持 | 不支持 | 支持 |
| 资源管理 | 执行节点多租户 | 支持  | 不支持| 支持 | 不支持 | 不支持 |
| 运行视图 | 工作流结构图（DAG） | 清晰显示任务状态，任务类型，工作流状态，重试次数等关键信息 | 只显示工作流和任务状态 | 清晰显示任务状态，类型，重试次数，任务运行机器等关键信息 | 只显示任务状态 | 只显示任务状态 |
| WeDataSphere整合 | 对DataSphere Studio和Linkis的支持 | 与DSS和Linkis无缝对接 | 不支持 | 不支持 | 不支持 | 不支持 |
| 特色功能 | 循环执行 | 支持 | 不支持 | 不支持 | 不支持 | 不支持 |
|  | 灵活的工作流参数 | 支持不同级别多种设置方式的工作流参数 | 不支持 | 不支持| 不支持 | 不支持 |
|  | hive表数据到达检查 | 支持 | 不支持 | 不支持 | 不支持 | 不支持 |
|  | 工作流之间交互依赖 | 支持 | 不支持 | 不支持 | 不支持 | 不支持 |
|  | 失败策略 | <p>支持任务失败暂停、跳过、重试、超时处理 | <p>不支持失败策略 | <p>支持任务超时处理 | <p>支持部分任务重跑策略 | <p>支持任务超时处理和失败重试 |
| 系统管理 | 用户管理 | 支持 | 不支持 | 支持 | 支持 | 不支持 |
