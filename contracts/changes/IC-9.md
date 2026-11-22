---
id: IC-9
type: NORMAL
status: PROPOSED
allowedOperations: []
allowedSchemas: []
allowedSymbols: []
contractDigest: null
baseSha: 7508531c6495fe20ce9441a8e48459f0ea4dc84f
headSha: null
---
# IC-9 库存分配计算的跨组接入提案

提出组：AC/C。日期：2026-11-21（Asia/Shanghai）。关联：[Issue #9](https://github.com/XJTLU-CSI301-Group27/civa-android/issues/9)。

状态：PROPOSED，尚无 EF/BD 审批；不授权新公共接口。allowedSymbols 留空，待团队确定实际入口后按工具计算授权摘要。

## 已实现部分

七个已有 InventoryApi 操作直接复用候选 OpenAPI、生成 DTO 和现有表，没有新增 HTTP 路由或 wire 字段。
`InventoryCalculator` 和 `InventoryMatchingService` 均为包内类型；内部 preview 使用现有 `CookingPreview` / `CookingAllocation` / `MatchLine`，不创建竞争领域模型。

## 为什么需要后续公共入口

现有 `matchRecipes` 返回每道菜的可用量和覆盖率，不返回批次分配；`previewCooking` / `confirmCooking` 属于 D+E。
BD/EF 不能直接调用 C 的包内实现或复制分配算法，因此在接入 CookingCoordinator 前须确定唯一服务入口。

提议讨论的最小签名为：

```java
CookingPreview preview(UUID authenticatedActorId, UUID householdId,
                       UUID recipeId, String servings);
```

建议由独立的 `InventoryAllocationService` 暴露；此类型和签名尚未导出或加入 public-surface，不表示已批准。

- actor 必须来自可信的认证流程，不能接受客户端传来的任意用户 ID。
- 家庭活动成员才可读取；非成员 NOT_FOUND，被移除成员 HOUSEHOLD_ACCESS_REVOKED。
- 份数为大于零的现有十进制字符串；数量采用标准单位 G/ML/COUNT。
- 输出复用 CookingPreview：recipeVersion、servings、allocations、shortages、checkedAt；C 的单菜计算不处理 planSlotId。
- 只计算，不锁定未来库存、不生成烹饪记录、不写库存。
- 排除过期及零数量；按 expiresOn（未知最后）、createdAt、batchId 分配。
- shortages 非空时不能确认做饭。部分分配只用于说明可用库存，不是可直接执行的扣减许可。
- 新的公共层如何映射内部异常、使用何种线程/事务调用约定，须与 EF/BD 确认，不能让调用方依赖包内异常类型。

## 做饭提交边界

E 的唯一 CookingCoordinator 在一个写事务内重新核验权限、recipeVersion、batch expectedVersion、家庭、单位、有效期、需求总量和余额。
遵守 household → membership → recipe → inventory_batches 锁顺序，再原子写库存、变动记录、烹饪记录及幂等成功结果。
不能在 C 的预览事务之后另行独立扣减，也不能用预览替代提交时校验。

## 影响及验证

影响 C 的库存分配、D 的做饭确认和 E 的事务协调；不涉及个人食记，不新增数据库迁移。
现有纯算法及真实 PostgreSQL 测试验证了分配顺序、缺货、只读性和版本数据；跨组调用、最终做饭事务和界面尚未验收。
实际执行命令和结果见 `docs/C_HANDOFF.md`。审阅前保持包内实现，不注册公开服务或额外路由；拒绝提案时仍可保留独立库存功能与匹配接口。
