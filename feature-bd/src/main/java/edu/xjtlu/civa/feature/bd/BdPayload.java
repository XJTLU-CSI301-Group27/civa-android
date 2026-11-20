package edu.xjtlu.civa.feature.bd;

import edu.xjtlu.civa.api.model.*;
import java.util.*;
import org.json.*;

/** Pending payload serialization only; HTTP serialization stays in the shared ApiProvider. */
public final class BdPayload {
  private BdPayload() {}

  public static String plan(PlanCommitRequest request) {
    try {
      JSONArray slots = new JSONArray();
      for (PlanSlotWrite slot : request.getSlots()) {
        JSONArray items = new JSONArray();
        for (PlanItem item : slot.getItems())
          items.put(
              new JSONObject()
                  .put("recipeId", item.getRecipeId())
                  .put("servings", item.getServings()));
        slots.put(
            new JSONObject()
                .put("date", slot.getDate())
                .put("mealType", slot.getMealType().toString())
                .put("expectedVersion", slot.getExpectedVersion())
                .put("items", items));
      }
      return new JSONObject().put("slots", slots).toString();
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static PlanCommitRequest plan(String payload) {
    try {
      JSONArray slots = new JSONObject(payload).getJSONArray("slots");
      List<PlanSlotWrite> result = new ArrayList<>();
      for (int i = 0; i < slots.length(); i++) {
        JSONObject slot = slots.getJSONObject(i);
        List<PlanItem> items = new ArrayList<>();
        JSONArray values = slot.getJSONArray("items");
        for (int n = 0; n < values.length(); n++) {
          JSONObject value = values.getJSONObject(n);
          PlanItem item = new PlanItem();
          item.setRecipeId(UUID.fromString(value.getString("recipeId")));
          item.setServings(value.getString("servings"));
          items.add(item);
        }
        PlanSlotWrite write = new PlanSlotWrite();
        write.setDate(slot.getString("date"));
        write.setMealType(PlanMealType.fromValue(slot.getString("mealType")));
        write.setExpectedVersion(slot.getLong("expectedVersion"));
        write.setItems(items);
        result.add(write);
      }
      PlanCommitRequest request = new PlanCommitRequest();
      request.setSlots(result);
      return request;
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String meal(CreateMealEntryRequest r) {
    try {
      return new JSONObject()
          .put("date", r.getDate())
          .put("mealType", r.getMealType().toString())
          .put("name", r.getName())
          .put("recipeId", nullable(r.getRecipeId()))
          .put("cookingEventId", nullable(r.getCookingEventId()))
          .put("amount", r.getAmount())
          .put("basis", r.getBasis().toString())
          .put("basisKcal", nullable(r.getBasisKcal()))
          .put("note", r.getNote())
          .put("imageId", nullable(r.getImageId()))
          .toString();
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static CreateMealEntryRequest meal(String payload) {
    try {
      JSONObject o = new JSONObject(payload);
      CreateMealEntryRequest r = new CreateMealEntryRequest();
      r.setDate(o.getString("date"));
      r.setMealType(EntryMealType.fromValue(o.getString("mealType")));
      r.setName(o.getString("name"));
      r.setRecipeId(uuid(o, "recipeId"));
      r.setCookingEventId(uuid(o, "cookingEventId"));
      r.setAmount(o.getString("amount"));
      r.setBasis(KcalBasis.fromValue(o.getString("basis")));
      r.setBasisKcal(o.isNull("basisKcal") ? null : o.getString("basisKcal"));
      r.setNote(o.getString("note"));
      r.setImageId(uuid(o, "imageId"));
      return r;
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static Object nullable(Object value) {
    return value == null ? JSONObject.NULL : value;
  }

  private static UUID uuid(JSONObject o, String key) throws JSONException {
    return o.isNull(key) ? null : UUID.fromString(o.getString(key));
  }
}
