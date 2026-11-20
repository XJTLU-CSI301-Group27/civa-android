package edu.xjtlu.civa.feature.bd.planning;

import edu.xjtlu.civa.api.model.*;
import edu.xjtlu.civa.core.data.*;
import edu.xjtlu.civa.feature.bd.*;
import java.time.LocalDate;
import java.util.*;

public final class PlanningViewModel extends BdModel {
  public LocalDate start = LocalDate.now(), selected = start;
  public final List<PlanSlot> slots = new ArrayList<>();
  public final List<Recipe> recipes = new ArrayList<>();
  public final Set<UUID> wishlist = new HashSet<>();
  public final Map<UUID, RecipeMatch> matches = new HashMap<>();
  public List<PlanSlotWrite> draft = new ArrayList<>();
  public Intent pending;
  public Preference preference;
  public boolean loaded, preferencesReady, catalogReady, matchesReady, wishlistReady;
  private final RecommendationEngine engine = new RecommendationEngine();
  private int loadGeneration;

  public PlanningViewModel(ApiProvider api, ApiCalls calls, SessionStore sessions) {
    super(api, calls, sessions);
  }

  public UUID home() {
    String id = sessions.currentHouseholdId();
    return id == null ? null : UUID.fromString(id);
  }

  public void load() {
    if (home() == null) {
      message = "先创建或加入家庭，再安排菜单";
      emit();
      return;
    }
    loaded = true;
    int generation = ++loadGeneration;
    message = "正在刷新计划与菜谱";
    emit();
    read(
        api.planning()
            .listPlanSlots(
                home(), start.minusDays(7).toString(), start.plusDays(13).toString(), 0, 100),
        page -> {
          if (generation != loadGeneration) return;
          slots.clear();
          slots.addAll(page.getItems());
          message = "已刷新。计划不会自动计入食记";
        });
    catalogReady = false;
    recipes.clear();
    loadRecipes(0, generation);
    // MVP random planning is explicitly unpersonalized. Preference-based ranking is deferred.
    recover();
  }

  private void loadRecipes(int page, int generation) {
    read(
        api.recipe().listRecipes(home(), null, null, null, false, page, 100),
        value -> {
          if (generation != loadGeneration) return;
          recipes.addAll(value.getItems());
          if ((long) (page + 1) * value.getSize() < value.getTotal())
            loadRecipes(page + 1, generation);
          else catalogReady = true;
        });
  }

  private void loadWishlist(int page) {
    read(
        api.recipe().listWishlist(page, 100),
        value -> {
          for (Recipe recipe : value.getItems()) wishlist.add(recipe.getId());
          if ((long) (page + 1) * value.getSize() < value.getTotal()) loadWishlist(page + 1);
          else wishlistReady = true;
        });
  }

  public PlanSlot slot(LocalDate date, PlanMealType meal) {
    for (PlanSlot slot : slots)
      if (slot.getDate().equals(date.toString()) && slot.getMealType() == meal) return slot;
    return null;
  }

  public String recipeName(UUID id) {
    for (Recipe recipe : recipes) if (recipe.getId().equals(id)) return recipe.getName();
    return "菜谱 " + id.toString().substring(0, 8);
  }

  public void edit(PlanMealType meal, List<PlanItem> items) {
    if (busy || pending != null) return;
    PlanSlot old = slot(selected, meal);
    if (old != null && Boolean.TRUE.equals(old.getLocked())) {
      message = "请先解锁该餐次";
      emit();
      return;
    }
    PlanSlotWrite write = new PlanSlotWrite();
    write.setDate(selected.toString());
    write.setMealType(meal);
    write.setExpectedVersion(old == null ? 0L : old.getVersion());
    write.setItems(items);
    draft = new ArrayList<>(Collections.singletonList(write));
    emit();
  }

  public void lock(PlanMealType meal) {
    if (pending != null || busy) return;
    PlanSlot old = slot(selected, meal);
    SetPlanLockRequest body = new SetPlanLockRequest();
    body.setLocked(old == null || !old.getLocked());
    body.setExpectedVersion(old == null ? 0L : old.getVersion());
    busy = true;
    emit();
    read(
        api.planning().setPlanSlotLock(home(), selected.toString(), meal, body),
        value -> {
          busy = false;
          replace(value);
          message = "餐次锁定状态已更新";
        });
  }

  public void generate(
      int days, List<PlanMealType> meals, String servings, int maxMinutes, boolean random) {
    if (busy || pending != null) return;
    if (!catalogReady || (!random && (!preferencesReady || !wishlistReady))) {
      message = "生成所需资料尚未成功读取，可先手动排餐";
      emit();
      return;
    }
    List<Recipe> candidates = engine.candidates(recipes, preference, maxMinutes);
    if (candidates.isEmpty()) {
      message = "没有符合我的偏好、忌口和时长条件的菜，请调整条件";
      draft.clear();
      emit();
      return;
    }
    if (random) {
      buildPreview(candidates, days, meals, servings, true);
      return;
    }
    matchesReady = false;
    matches.clear();
    busy = true;
    emit();
    loadMatches(candidates, 0, days, meals, servings);
  }

  private void loadMatches(
      List<Recipe> candidates, int offset, int days, List<PlanMealType> meals, String servings) {
    int end = Math.min(offset + 100, candidates.size());
    List<MatchEntry> entries = new ArrayList<>();
    for (Recipe recipe : candidates.subList(offset, end)) {
      MatchEntry item = new MatchEntry();
      item.setRecipeId(recipe.getId());
      item.setServings(servings);
      entries.add(item);
    }
    MatchRequest body = new MatchRequest();
    body.setEntries(entries);
    read(
        api.inventory().matchRecipes(home(), body),
        values -> {
          for (RecipeMatch match : values) matches.put(match.getRecipeId(), match);
          if (end < candidates.size()) loadMatches(candidates, end, days, meals, servings);
          else {
            busy = false;
            matchesReady = true;
            List<PlanSlot> history = new ArrayList<>();
            for (PlanSlot slot : slots)
              if (LocalDate.parse(slot.getDate()).isBefore(start)) history.add(slot);
            buildPreview(
                engine.rank(candidates, wishlist, matches, history), days, meals, servings, false);
          }
        });
  }

  private void buildPreview(
      List<Recipe> candidates,
      int days,
      List<PlanMealType> meals,
      String servings,
      boolean random) {
    try {
      draft =
          engine.preview(
              start, days, meals, slots, candidates, servings, random, System.nanoTime());
      message = draft.isEmpty() ? "没有可填写的空餐次；已有内容和锁定餐次已保留" : "预览尚未保存；库存匹配不预留未来食材";
    } catch (IllegalArgumentException ex) {
      message = "请检查日期跨度与份数";
    }
    emit();
  }

  public String reason(UUID recipe) {
    for (Recipe r : recipes)
      if (r.getId().equals(recipe)) return engine.reason(r, wishlist, matches.get(recipe));
    return "";
  }

  public void save() {
    if (busy || pending != null || draft.isEmpty()) return;
    PlanCommitRequest body = new PlanCommitRequest();
    body.setSlots(new ArrayList<>(draft));
    String key = UUID.randomUUID().toString(), payload = BdPayload.plan(body);
    pending = new Intent(key, payload);
    write(
        "commitPlan",
        key,
        payload,
        api.planning().commitPlan(home(), UUID.fromString(key), body),
        values -> {
          pending = null;
          draft.clear();
          for (PlanSlot slot : values) replace(slot);
        });
  }

  public void retryPending() {
    if (pending == null || busy) return;
    Intent value = pending;
    retry(
        "commitPlan",
        value.key,
        value.canonicalPayload,
        api.planning()
            .commitPlan(home(), UUID.fromString(value.key), BdPayload.plan(value.canonicalPayload)),
        values -> {
          pending = null;
          draft.clear();
          for (PlanSlot slot : values) replace(slot);
        });
  }

  public void abandon() {
    if (pending == null || busy) return;
    String key = pending.key;
    calls
        .pendingWrites()
        .confirm(
            sessions.currentUserId(),
            key,
            new ApiCallback<Void>() {
              @Override
              public void onSuccess(Void ignored) {
                pending = null;
                draft.clear();
                message = "已移除本地待确认标记；这不撤销服务端操作，请刷新核对";
                emit();
              }

              @Override
              public void onError(edu.xjtlu.civa.core.ui.ApiError error) {
                fail(error);
              }
            });
  }

  private void recover() {
    String actor = sessions.currentUserId();
    calls
        .pendingWrites()
        .listPending(
            actor,
            new ApiCallback<List<PendingWrite>>() {
              @Override
              public void onSuccess(List<PendingWrite> values) {
                if (!Objects.equals(actor, sessions.currentUserId())) return;
                for (PendingWrite value : values)
                  if (value.operationId.equals("commitPlan")
                      && Objects.equals(value.householdId, sessions.currentHouseholdId())) {
                    pending = new Intent(value.key, value.canonicalPayload);
                    message = "有排餐提交结果待核对，可刷新后使用原请求重试";
                    break;
                  }
                emit();
              }

              @Override
              public void onError(edu.xjtlu.civa.core.ui.ApiError error) {
                fail(error);
              }
            });
  }

  private void replace(PlanSlot value) {
    slots.removeIf(
        slot ->
            slot.getDate().equals(value.getDate()) && slot.getMealType() == value.getMealType());
    slots.add(value);
  }
}
