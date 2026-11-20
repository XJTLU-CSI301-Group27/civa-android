package edu.xjtlu.civa.feature.bd.records;

import edu.xjtlu.civa.api.model.*;
import edu.xjtlu.civa.core.data.*;
import edu.xjtlu.civa.core.ui.ApiError;
import edu.xjtlu.civa.feature.bd.*;
import java.time.*;
import java.util.*;

public final class DiaryViewModel extends BdModel {
  public LocalDate selected = LocalDate.now();
  public YearMonth month = YearMonth.from(selected);
  public final List<MealEntry> entries = new ArrayList<>();
  public final List<Recipe> recipes = new ArrayList<>();
  public final List<CookingEvent> cooked = new ArrayList<>();
  public PeriodSummary summary;
  public boolean loaded;
  public MealEntry saved;
  public Intent pending;
  private int entriesGeneration, summaryGeneration;

  public DiaryViewModel(ApiProvider api, ApiCalls calls, SessionStore sessions) {
    super(api, calls, sessions);
  }

  public void load() {
    loaded = true;
    loadEntries(0, new ArrayList<>(), ++entriesGeneration);
    loadSummary(month.atDay(1), month.atEndOfMonth());
    recover();
  }

  private void loadEntries(int page, List<MealEntry> accumulator, int generation) {
    YearMonth requested = month;
    read(
        api.records()
            .listMealEntries(
                requested.atDay(1).toString(), requested.atEndOfMonth().toString(), page, 100),
        value -> {
          if (!requested.equals(month) || generation != entriesGeneration) return;
          accumulator.addAll(value.getItems());
          if ((long) (page + 1) * value.getSize() < value.getTotal())
            loadEntries(page + 1, accumulator, generation);
          else {
            entries.clear();
            entries.addAll(accumulator);
            message = "实际用餐记录 · 计划和做饭不会自动计入";
          }
        });
  }

  public void loadSummary(LocalDate from, LocalDate to) {
    int generation = ++summaryGeneration;
    read(
        api.records().getMealSummary(from.toString(), to.toString()),
        value -> {
          if (generation == summaryGeneration) summary = value;
        });
  }

  public void select(LocalDate day) {
    selected = day;
    if (!YearMonth.from(day).equals(month)) {
      month = YearMonth.from(day);
      load();
    }
    emit();
  }

  public void changeMonth(int offset) {
    month = month.plusMonths(offset);
    selected = month.atDay(1);
    entries.clear();
    summary = null;
    load();
    emit();
  }

  public void loadSources() {
    if (sessions.currentHouseholdId() == null) return;
    recipes.clear();
    cooked.clear();
    loadRecipes(0);
    loadCooked(0);
  }

  private void loadRecipes(int page) {
    read(
        api.recipe()
            .listRecipes(
                UUID.fromString(sessions.currentHouseholdId()), null, null, null, false, page, 100),
        value -> {
          recipes.addAll(value.getItems());
          if ((long) (page + 1) * value.getSize() < value.getTotal()) loadRecipes(page + 1);
        });
  }

  private void loadCooked(int page) {
    read(
        api.records()
            .listCookingEvents(
                UUID.fromString(sessions.currentHouseholdId()), null, null, null, page, 100),
        value -> {
          cooked.addAll(value.getItems());
          if ((long) (page + 1) * value.getSize() < value.getTotal()) loadCooked(page + 1);
        });
  }

  public void save(CreateMealEntryRequest body) {
    if (busy || pending != null) return;
    String key = UUID.randomUUID().toString(), payload = BdPayload.meal(body);
    pending = new Intent(key, payload);
    write(
        "createMealEntry",
        key,
        payload,
        api.records().createMealEntry(UUID.fromString(key), body),
        value -> {
          pending = null;
          saved = value;
        });
  }

  public void update(MealEntry old, CreateMealEntryRequest body) {
    if (busy || pending != null) return;
    UpdateMealEntryRequest request = new UpdateMealEntryRequest();
    request.setDate(body.getDate());
    request.setMealType(body.getMealType());
    request.setName(body.getName());
    request.setRecipeId(body.getRecipeId());
    request.setCookingEventId(body.getCookingEventId());
    request.setAmount(body.getAmount());
    request.setBasis(body.getBasis());
    request.setBasisKcal(body.getBasisKcal());
    request.setNote(body.getNote());
    request.setImageId(body.getImageId());
    request.setExpectedVersion(old.getVersion());
    busy = true;
    emit();
    read(
        api.records().updateMealEntry(old.getId(), request),
        value -> {
          busy = false;
          saved = value;
        });
  }

  public void delete(MealEntry entry) {
    if (busy || pending != null) return;
    busy = true;
    emit();
    read(
        api.records().deleteMealEntry(entry.getId(), entry.getVersion()),
        ignored -> {
          busy = false;
          load();
        });
  }

  public void retryPending() {
    if (pending == null || busy) return;
    Intent value = pending;
    retry(
        "createMealEntry",
        value.key,
        value.canonicalPayload,
        api.records()
            .createMealEntry(UUID.fromString(value.key), BdPayload.meal(value.canonicalPayload)),
        entry -> {
          pending = null;
          saved = entry;
        });
  }

  public void abandon() {
    if (pending == null || busy) return;
    calls
        .pendingWrites()
        .confirm(
            sessions.currentUserId(),
            pending.key,
            new ApiCallback<Void>() {
              @Override
              public void onSuccess(Void ignored) {
                pending = null;
                message = "本地标记已移除；服务端操作没有被撤销，请刷新核对";
                emit();
              }

              @Override
              public void onError(ApiError error) {
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
                  if (value.operationId.equals("createMealEntry")) {
                    pending = new Intent(value.key, value.canonicalPayload);
                    message = "有食记提交结果待确认，请核对后用原请求重试";
                    break;
                  }
                emit();
              }

              @Override
              public void onError(ApiError error) {
                fail(error);
              }
            });
  }
}
