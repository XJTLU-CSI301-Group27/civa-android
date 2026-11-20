package edu.xjtlu.civa.feature.bd.records;

import edu.xjtlu.civa.api.model.*;
import edu.xjtlu.civa.core.data.*;
import edu.xjtlu.civa.core.ui.ApiError;
import edu.xjtlu.civa.feature.bd.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;

public final class CookingViewModel extends BdModel {
  public UUID recipeId, slotId;
  public String servings = "1";
  public CookingPreview preview;
  public CookingEvent saved;
  public Intent pending;
  public boolean loaded;

  public CookingViewModel(ApiProvider api, ApiCalls calls, SessionStore sessions) {
    super(api, calls, sessions);
  }

  public void load() {
    loaded = true;
    if (sessions.currentHouseholdId() == null) {
      message = "请先加入家庭";
      emit();
      return;
    }
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
                  if (value.operationId.equals("confirmCooking")
                      && Objects.equals(value.householdId, sessions.currentHouseholdId())) {
                    pending = new Intent(value.key, value.canonicalPayload);
                    message = "有做饭操作结果待确认，请先核对，勿重新创建";
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

  public void preview() {
    if (busy || pending != null || recipeId == null) return;
    CookingPreviewRequest body = new CookingPreviewRequest();
    body.setRecipeId(recipeId);
    body.setServings(servings);
    body.setPlanSlotId(slotId);
    busy = true;
    emit();
    read(
        api.records().previewCooking(home(), body),
        value -> {
          busy = false;
          preview = value;
          message = "请核对实际份数与食材分配；确认只记做饭，不记个人摄入";
        });
  }

  public void confirm() {
    if (busy || pending != null || preview == null || !preview.getShortages().isEmpty()) return;
    ConfirmCookingRequest body = new ConfirmCookingRequest();
    body.setRecipeId(recipeId);
    body.setRecipeVersion(preview.getRecipeVersion());
    body.setServings(preview.getServings());
    body.setPlanSlotId(preview.getPlanSlotId());
    body.setCookedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS).toString());
    body.setAllocations(preview.getAllocations());
    String key = UUID.randomUUID().toString(), payload = encode(body);
    pending = new Intent(key, payload);
    write(
        "confirmCooking",
        key,
        payload,
        api.records().confirmCooking(home(), UUID.fromString(key), body),
        value -> {
          pending = null;
          saved = value;
          message = "做饭已确认。个人实际吃了多少，请另记食记";
        });
  }

  public void retryPending() {
    if (busy || pending == null) return;
    Intent value = pending;
    retry(
        "confirmCooking",
        value.key,
        value.canonicalPayload,
        api.records()
            .confirmCooking(home(), UUID.fromString(value.key), decode(value.canonicalPayload)),
        event -> {
          pending = null;
          saved = event;
          message = "做饭结果已确认";
        });
  }

  public void abandon() {
    if (busy || pending == null) return;
    calls
        .pendingWrites()
        .confirm(
            sessions.currentUserId(),
            pending.key,
            new ApiCallback<Void>() {
              @Override
              public void onSuccess(Void ignored) {
                pending = null;
                preview = null;
                message = "本地标记已移除；请核对服务端库存和做饭记录，放弃不等于回滚";
                emit();
              }

              @Override
              public void onError(ApiError error) {
                fail(error);
              }
            });
  }

  private UUID home() {
    return UUID.fromString(sessions.currentHouseholdId());
  }

  private static String encode(ConfirmCookingRequest r) {
    try {
      JSONArray allocations = new JSONArray();
      for (CookingAllocation a : r.getAllocations())
        allocations.put(
            new JSONObject()
                .put("batchId", a.getBatchId())
                .put("expectedVersion", a.getExpectedVersion())
                .put("ingredientId", a.getIngredientId())
                .put("quantity", a.getQuantity())
                .put("unit", a.getUnit().toString()));
      return new JSONObject()
          .put("recipeId", r.getRecipeId())
          .put("recipeVersion", r.getRecipeVersion())
          .put("servings", r.getServings())
          .put("planSlotId", r.getPlanSlotId() == null ? JSONObject.NULL : r.getPlanSlotId())
          .put("cookedAt", r.getCookedAt())
          .put("allocations", allocations)
          .toString();
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static ConfirmCookingRequest decode(String payload) {
    try {
      JSONObject o = new JSONObject(payload);
      ConfirmCookingRequest r = new ConfirmCookingRequest();
      r.setRecipeId(UUID.fromString(o.getString("recipeId")));
      r.setRecipeVersion(o.getLong("recipeVersion"));
      r.setServings(o.getString("servings"));
      r.setPlanSlotId(o.isNull("planSlotId") ? null : UUID.fromString(o.getString("planSlotId")));
      r.setCookedAt(o.getString("cookedAt"));
      List<CookingAllocation> items = new ArrayList<>();
      JSONArray array = o.getJSONArray("allocations");
      for (int i = 0; i < array.length(); i++) {
        JSONObject v = array.getJSONObject(i);
        CookingAllocation a = new CookingAllocation();
        a.setBatchId(UUID.fromString(v.getString("batchId")));
        a.setExpectedVersion(v.getLong("expectedVersion"));
        a.setIngredientId(UUID.fromString(v.getString("ingredientId")));
        a.setQuantity(v.getString("quantity"));
        a.setUnit(Unit.fromValue(v.getString("unit")));
        items.add(a);
      }
      r.setAllocations(items);
      return r;
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
