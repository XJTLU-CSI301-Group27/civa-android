# C 初步原生冰箱界面

当前开发阶段：taskc_version5，采用已冻结的最终保留源码；相较第四版的差异及验证边界见 [版本记录](c-history/taskc_version5.md)。
第二版初步前端说明保留在 [taskc_version2](c-history/taskc_version2.md)。

## 查看方式

在 Android Studio 打开本版工程根目录，使用 JDK 17，等待 Gradle 同步完成。

- 静态参考：打开 `feature-ac/src/main/res/layout/ac_preview_inventory.xml`，选择 Design 或 Split。
  编辑器示例采用 tools 属性，不作为正式库存。XML 预览不会执行开关门或保存操作。
- 运行交互：配置可访问的后端，运行 app 到独立 Android 模拟器，登录并进入家庭后点击冰箱。
  不需要连接实体平板，但完整交互依赖真实后端和数据库。

历史版本验证应使用独立模拟器和独立 `_test` 数据库，避免覆盖日常预览的最终版本和数据。

## 页面交互

- 冷藏区分蛋奶、蔬菜、水果；冷冻区分肉类、海鲜。
- 冷藏和冷冻门可以分别开关，也可以一起开关。
- 点选食材批次查看数量、单位、位置、入库时间及到期日。
- 添加库存、消耗、丢弃、修正和编辑资料通过真实 InventoryApi 保存。
- 消耗显示预计余额；只有服务端成功后才刷新为新余额。
- 全部、未过期、临期、已过期筛选采用服务端规则。
- 同标准食材与单位合并为一张卡片；点开查看并选择不同入库日期、到期日的批次。
- 最后一次消耗先确认或返回修改；确认归零后在冰箱隐藏，后台批次和记录保留。
- 用尽后补货选中新批次，汇总数量随服务器余额刷新。
- 添加入口解释无家庭、加载和待确认状态；英文名称可直接输入，候选只辅助查找。
- 完整冰箱占首屏，上下等高，宽屏详情并排、窄屏独立详情。

## 单位和数据

数量输入同行提供 g / kg / pcs，液体场景另提供 ml；kg 换算到 G。
单位默认复用已匹配的标准食材；COUNT 苹果选 pcs，G 牛肉选 g，未知食材明确选择，不把旧 G 改成 COUNT。
卡片 G 显示克、ML 显示毫升、COUNT 通常显示个，COUNT 奶类显示瓶；不推测瓶数与毫升转换。
已有食材唯一匹配后复用标准 ID 与单位；未收录英文名称选择单位后经 createIngredient 创建并录入库存。
同名不同单位需明确选择，不自动改写旧库存。
入库时间使用服务端 createdAt，到期日允许未知。

## 调用链

InventoryFragment → InventoryViewModel → 共享 ApiCalls / InventoryApi → 第一版 C 后端 → PostgreSQL。
公共入口提案见 `contracts/changes/IC-20261122-C-UI-01.md`，状态为 PROPOSED。

## 验证

在根目录配置 JDK、SDK 和 Gradle 缓存后执行：

```powershell
.\gradlew.bat validateContract checkContracts assembleDebug testDebugUnitTest lint :api-client:testCandidateClient --offline --no-daemon
python -m unittest discover -s scripts -p 'test_*.py'
```

原阶段分组截图见 [taskc_version4](c-history/taskc_version4.md)；第五版输入和提示变化见 [taskc_version5](c-history/taskc_version5.md)。
本次没有重新安装运行；此前完整 UI 回归中的筛选切换检查未通过，不以构建和单元测试替代验收。
旧版 `scripts/test_inventory_ui.py` 要求已安装本版 APK 的独立模拟器、后端及相同 `_test` 库。
运行前必须确认目标设备和数据库；本次恢复未执行该脚本，不以历史截图替代当前 UI 回归。
