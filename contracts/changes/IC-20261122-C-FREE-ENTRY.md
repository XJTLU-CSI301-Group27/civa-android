---
id: IC-20261122-C-FREE-ENTRY
type: NORMAL
status: PROPOSED
allowedOperations: [createIngredient]
allowedSchemas: [CreateIngredientRequest]
allowedSymbols: []
contractDigest: null
baseSha: 7508531c6495fe20ce9441a8e48459f0ea4dc84f
headSha: null
---
# 任意英文食材名称录入：标准食材创建接口提案

需求：所有食材允许直接输入英文名称，不强制从列表选择；已有 beef / egg 别名复用标准食材及单位。
原 InventoryApi 只有 listIngredients；CreateInventoryBatchRequest 必须引用真实 ingredientId，不能伪造 ID 或借用位置字段保存名称。

本提案扩展唯一 OpenAPI 候选，使用生成器生成客户端与服务端；复用已有标准食材模型，不迁移历史库存。
本地实现与验证不代表 A/EF 团队完成合并审阅，提案状态仍为 PROPOSED。

## 提议的唯一流程

1. 食材输入使用普通文本框，不抢焦点、不弹出覆盖表单的大列表。
2. 已有英文名称或别名唯一匹配时，直接复用其标准 ID 和单位；不要求点击候选。
3. 未收录的新英文名，由用户选择 G（克）、ML（毫升）或 COUNT（个/瓶等计数）；不猜测未知单位。
4. 先创建或查询复用标准食材，再用现有 createInventoryBatch 保存库存。创建成功、库存保存失败时不伪称库存已保存；保留食材资料供重试，不删除别的模块可用资料。

## 拟新增契约

- POST /api/v1/ingredients，候选操作名 createIngredient。
- 请求：name（英文食材名，去首尾空白，1–120字符），unit（复用现有 Unit：G/ML/COUNT）；Idempotency-Key 必填。
- 返回：201 或复用记录时200；返回现有 Ingredient DTO，不新建第二套食材模型。
- 调用者：已登录且属于有效家庭的成员。食材资料仍属现有全局标准字典，可由其他登录用户查询；库存只写当前家庭。这项全局共享和创建权限需要 AC/EF 确认。
- 名称/别名按既有 IngredientNames 规则规范化。若已有同一名称/别名且单位一致，返回现有标准食材；不同单位或别名指向有冲突时返回明确的409，不修改旧食材单位。
- 相同 key 和内容重复提交只产生一条结果；相同 key 不同内容返回409。并发创建同一名称采用统一锁和查重，不增加重复标准食材。
- 复用 ingredients、ingredient_aliases、现有幂等组件；不修改已有库存或菜谱引用，不因本提案自动合并历史重复 ID，也不擅自做数据库迁移。

## 影响与验收

影响 AC/C 录入页、AC/A 共用字典、API客户端/服务端生成代码及 EF 的创建权限与幂等装配。本地实现更新唯一 OpenAPI 候选，通过生成器生成代码并接入页面；服务端新增4项真实数据库验收已通过。现有接口签名和单位枚举不变。

页面允许输入任意新名称；已有名称/别名自动匹配并同步单位，未知名称选择单位后创建。候选只作辅助，不强制点选；不显示测试别名或重复名称。同名不同单位保持可区分，不擅自改写旧库存 ID。

必须验证：已有 beef/egg 直接输入保存与单位正确；未收录的新英文名加单位可保存；忽略候选列表仍可提交；同名/别名复用ID；单位冲突与并发冲突；重复请求不重复创建/扣库存；创建后库存提交失败能明确重试；不同家庭库存不混用。
