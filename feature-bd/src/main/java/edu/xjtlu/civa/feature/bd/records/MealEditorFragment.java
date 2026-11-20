package edu.xjtlu.civa.feature.bd.records;

import android.os.Bundle;
import android.text.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import edu.xjtlu.civa.api.model.*;
import edu.xjtlu.civa.core.data.AppServices;
import edu.xjtlu.civa.core.ui.CivaScreen;
import edu.xjtlu.civa.core.ui.Route;
import edu.xjtlu.civa.feature.bd.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

public final class MealEditorFragment extends CivaScreen<AppServices> {
  private DiaryViewModel model;
  private Draft draft;
  private MealEntry original;
  private boolean built;

  public MealEditorFragment() {}

  public static MealEditorFragment create(String reference) {
    MealEditorFragment fragment = new MealEditorFragment();
    Bundle args = new Bundle();
    args.putString("bd_reference", reference);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  protected void start(Bundle state) {
    built = false;
    draft = new ViewModelProvider(this).get(Draft.class);
    String ref = getArguments() == null ? null : getArguments().getString("bd_reference");
    if (!draft.initialized) {
      String[] parts =
          ref == null ? new String[] {LocalDate.now().toString()} : ref.split("\\|", -1);
      draft.date = parts[0];
      draft.entryId = parts.length > 1 ? parts[1] : null;
      draft.initialized = true;
    }
    model =
        new ViewModelProvider(
                this,
                new ViewModelProvider.Factory() {
                  @NonNull
                  @Override
                  public <T extends ViewModel> T create(@NonNull Class<T> type) {
                    return type.cast(new DiaryViewModel(host.api(), host.calls(), host.sessions()));
                  }
                })
            .get(DiaryViewModel.class);
    model.changed.observe(
        getViewLifecycleOwner(),
        ignored -> {
          if (binding == null) return;
          if (model.saved != null) {
            host.navigate(Route.DIARY, model.saved.getDate());
            return;
          }
          if (draft.entryId != null && original == null) {
            for (MealEntry entry : model.entries)
              if (entry.getId().toString().equals(draft.entryId)) {
                original = entry;
                if (!draft.loadedEntry) {
                  draft.load(entry);
                  draft.loadedEntry = true;
                }
              }
          }
          if (!built && (draft.entryId == null || original != null)) build();
          else if (!built) showLoading();
          binding.civaStatus.setText(model.message);
          enableForm();
        });
    if (!model.loaded) {
      model.selected = LocalDate.parse(draft.date);
      model.month = YearMonth.from(model.selected);
      model.load();
      model.loadSources();
    }
    if (draft.entryId != null && original == null) {
      showLoading();
    } else if (!built) build();
  }

  private void showLoading() {
    title("编辑食记", "原记录加载完成后才能编辑", model.message);
    button(binding.civaActions, "返回食记", true, () -> host.navigate(Route.DIARY, draft.date));
    button(binding.civaActions, "重试读取", !model.busy, model::load);
    label(binding.civaPrimary, "如果记录已被删除，请返回日历刷新；未读取原记录时不会误建新记录。", 15);
  }

  private void build() {
    built = true;
    title(draft.entryId == null ? "记录这一餐" : "编辑这条食记", "只记录本人实际摄入 · 不扣减原材料", model.message);
    button(binding.civaActions, "返回食记", true, () -> host.navigate(Route.DIARY, null));
    button(binding.civaActions, "保存", !model.busy && model.pending == null, this::save);
    LinearLayout form = card(binding.civaPrimary, "实际用餐");
    EditText date =
        field(
            form, "日期 YYYY-MM-DD", draft.date, InputType.TYPE_CLASS_DATETIME, v -> draft.date = v);
    button(
        form,
        "选择日期",
        true,
        () -> {
          LocalDate initial;
          try {
            initial = LocalDate.parse(draft.date);
          } catch (RuntimeException ex) {
            initial = LocalDate.now();
          }
          pickDate(
              initial,
              value -> {
                draft.date = value.toString();
                date.setText(draft.date);
              });
        });
    Spinner meal = spinner(form, "餐次", new String[] {"早餐", "午餐", "晚餐", "加餐"}, draft.meal);
    choose(meal, value -> draft.meal = value);
    field(form, "吃了什么", draft.name, InputType.TYPE_CLASS_TEXT, v -> draft.name = v);
    field(
        form,
        "实际份量",
        draft.amount,
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
        v -> draft.amount = v);
    field(
        form,
        "备注",
        draft.note,
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
        v -> draft.note = v);
    label(form, "首版先记日期、菜名、份量和备注，热量功能后续加入。", 13);
    button(form, "保存食记", true, () -> save());
    button(form, "使用原请求重试", true, () -> model.retryPending());
    button(form, "核对后移除待确认标记", true, () -> abandon(model::abandon));
    LinearLayout sources = card(binding.civaSecondary, "从现有资料预填");
    button(sources, "选择家庭菜谱", true, () -> selectRecipe());
    button(
        sources,
        "记为外食 / 清除菜谱关联",
        true,
        () -> {
          draft.recipe = null;
          draft.cooking = null;
          build();
          notice("已清除关联，可自行填写外食名称");
        });
    label(sources, "先选择资料再填写实际份量；选择后仍须自己确认保存。照片上传依赖 EF 媒体接口，本版先保留已有图片关联。", 14);
    if (draft.recipe != null) label(sources, "已关联菜谱 " + draft.recipe, 13);
  }

  private void save() {
    if (model.busy || model.pending != null) {
      notice("有提交待确认，请核对或使用原请求重试");
      return;
    }
    try {
      LocalDate date = LocalDate.parse(draft.date);
      if (!date.toString().equals(draft.date) || date.isAfter(LocalDate.now()))
        throw new IllegalArgumentException();
      if (draft.name.trim().isEmpty()) throw new IllegalArgumentException();
      BigDecimal amount = new BigDecimal(draft.amount);
      if (amount.signum() <= 0 || amount.scale() > 3) throw new IllegalArgumentException();
      KcalBasis basis = KcalBasis.values()[draft.basis];
      if (basis != KcalBasis.UNKNOWN) {
        BigDecimal kcal = new BigDecimal(draft.kcal);
        if (kcal.signum() < 0
            || kcal.scale() > 3
            || (basis == KcalBasis.MANUAL && amount.compareTo(BigDecimal.ONE) != 0))
          throw new IllegalArgumentException();
      }
      CreateMealEntryRequest body = new CreateMealEntryRequest();
      body.setDate(draft.date);
      body.setMealType(EntryMealType.values()[draft.meal]);
      body.setName(draft.name.trim());
      body.setAmount(draft.amount);
      body.setBasis(basis);
      body.setBasisKcal(basis == KcalBasis.UNKNOWN ? null : draft.kcal);
      body.setNote(draft.note);
      body.setRecipeId(draft.recipe);
      body.setCookingEventId(draft.cooking);
      body.setImageId(draft.image);
      if (original == null) model.save(body);
      else model.update(original, body);
    } catch (IllegalArgumentException | java.time.DateTimeException ex) {
      notice("请填写有效日期、菜名和大于零且最多三位小数的份量；未来日期请使用计划。");
    }
  }

  private void selectRecipe() {
    if (model.recipes.isEmpty()) {
      notice("没有可用菜谱，或 AC 接口尚未就绪；可以直接手填外食");
      return;
    }
    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle("选择菜谱")
        .setItems(
            model.recipes.stream().map(Recipe::getName).toArray(String[]::new),
            (dialog, which) -> {
              Recipe r = model.recipes.get(which);
              prefill(r.getId(), null, r.getName(), r.getTotalKcal(), r.getYieldServings());
            })
        .show();
  }

  private void selectCooked() {
    if (model.cooked.isEmpty()) {
      notice("暂无实际烹饪记录");
      return;
    }
    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle("选择做过的菜")
        .setItems(
            model.cooked.stream()
                .map(r -> r.getRecipe().getRecipeName() + " · " + r.getCookedAt().substring(0, 10))
                .toArray(String[]::new),
            (dialog, which) -> {
              CookingEvent e = model.cooked.get(which);
              prefill(
                  e.getRecipe().getRecipeId(),
                  e.getId(),
                  e.getRecipe().getRecipeName(),
                  e.getRecipe().getTotalKcal(),
                  e.getRecipe().getYieldServings());
            })
        .show();
  }

  private void prefill(UUID recipe, UUID cooking, String name, String kcal, String yield) {
    draft.recipe = recipe;
    draft.cooking = cooking;
    draft.name = name;
    draft.basis = 3;
    draft.kcal = "";
    build();
  }

  private EditText field(
      LinearLayout parent, String caption, String value, int type, Consumer<String> changed) {
    EditText field = input(parent, caption, value, type);
    field.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            changed.accept(s.toString());
          }

          public void afterTextChanged(Editable text) {}
        });
    return field;
  }

  private void choose(Spinner spinner, java.util.function.IntConsumer action) {
    spinner.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onItemSelected(
              android.widget.AdapterView<?> p, android.view.View v, int position, long id) {
            action.accept(position);
          }

          public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
  }

  private void enableForm() {
    if (binding == null) return;
    enabled(binding.civaPrimary, !model.busy && model.pending == null);
  }

  private void enabled(android.view.View view, boolean value) {
    view.setEnabled(value);
    if (view instanceof android.view.ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.View child = group.getChildAt(i);
        if (child instanceof com.google.android.material.button.MaterialButton button
            && (button.getText().toString().contains("原请求")
                || button.getText().toString().contains("待确认")))
          child.setEnabled(!model.busy && model.pending != null);
        else enabled(child, value);
      }
  }

  public static final class Draft extends ViewModel {
    boolean initialized, loadedEntry;
    String entryId, date, name = "", amount = "1", kcal = "", note = "";
    int meal = 1, basis = 3;
    UUID recipe, cooking, image;

    public Draft() {}

    void load(MealEntry e) {
      date = e.getDate();
      name = e.getName();
      amount = e.getAmount();
      kcal = e.getBasisKcal() == null ? "" : e.getBasisKcal();
      note = e.getNote();
      meal = e.getMealType().ordinal();
      basis = e.getBasis().ordinal();
      recipe = e.getRecipeId();
      cooking = e.getCookingEventId();
      image = e.getImageId();
    }
  }
}
