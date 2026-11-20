package edu.xjtlu.civa.feature.bd.planning;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import edu.xjtlu.civa.api.model.*;
import edu.xjtlu.civa.core.data.AppServices;
import edu.xjtlu.civa.core.ui.CivaScreen;
import edu.xjtlu.civa.core.ui.Route;
import edu.xjtlu.civa.feature.bd.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class PlanningFragment extends CivaScreen<AppServices> {
  private PlanningViewModel model;

  public PlanningFragment() {}

  @Override
  protected void start(Bundle state) {
    model =
        new ViewModelProvider(
                this,
                new ViewModelProvider.Factory() {
                  @NonNull
                  @Override
                  public <T extends ViewModel> T create(@NonNull Class<T> type) {
                    return type.cast(
                        new PlanningViewModel(host.api(), host.calls(), host.sessions()));
                  }
                })
            .get(PlanningViewModel.class);
    model.changed.observe(getViewLifecycleOwner(), ignored -> render());
    if (!model.loaded) model.load();
  }

  private void render() {
    if (binding == null) return;
    title(
        "今天想吃什么",
        "从 " + model.start + " 开始的两周菜单 · 自主安排与随机灵感",
        model.busy ? "正在处理，请稍候…" : model.message);
    button(
        binding.civaActions,
        "选日期",
        !model.busy,
        () ->
            pickDate(
                model.selected,
                date -> {
                  model.start = date;
                  model.selected = date;
                  model.draft.clear();
                  model.load();
                  model.emit();
                }));
    button(
        binding.civaActions,
        "今天",
        !model.busy,
        () -> {
          model.start = LocalDate.now();
          model.selected = model.start;
          model.draft.clear();
          model.load();
          model.emit();
        });
    button(binding.civaActions, "刷新", !model.busy, model::load);
    button(
        binding.civaActions,
        "生成菜单",
        !model.busy && model.pending == null && model.home() != null,
        this::generate);
    button(binding.civaActions, "家庭", true, () -> host.navigate(Route.HOUSEHOLD, null));
    if (model.pending != null) {
      LinearLayout pending = card(binding.civaPrimary, "有提交结果待确认");
      label(pending, "先刷新核对，再用相同请求重试。不要重复新建同一次安排。", 14);
      button(pending, "原请求重试", !model.busy, model::retryPending);
      button(pending, "核对并移除标记", !model.busy, () -> abandon(model::abandon));
    }
    LinearLayout days = card(binding.civaPrimary, "选择日期");
    HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
    LinearLayout strip = new LinearLayout(requireContext());
    scroll.addView(strip);
    days.addView(scroll);
    for (int i = 0; i < 14; i++) {
      LocalDate day = model.start.plusDays(i);
      button(
          strip,
          (day.equals(model.selected) ? "● " : "")
              + day.getMonthValue()
              + "/"
              + day.getDayOfMonth(),
          true,
          () -> {
            model.selected = day;
            model.emit();
          });
    }
    label(binding.civaPrimary, model.selected + " 的安排", 20);
    for (PlanMealType meal : PlanMealType.values()) {
      PlanSlot slot = model.slot(model.selected, meal);
      LinearLayout panel =
          card(
              binding.civaPrimary,
              mealName(meal) + (slot != null && slot.getLocked() ? " · 已锁定" : ""));
      List<PlanItem> items = slot == null ? Collections.emptyList() : slot.getItems();
      if (items.isEmpty()) label(panel, "还没有安排，给这一餐留一点期待。", 15);
      for (PlanItem item : items) {
        LinearLayout row = column();
        panel.addView(row);
        label(row, model.recipeName(item.getRecipeId()) + " · " + item.getServings() + " 份", 16);
        button(row, "查看菜谱", true, () -> showRecipe(item.getRecipeId()));
      }
      button(
          panel,
          "选菜 / 换菜 / 调整份数",
          !model.busy
              && model.pending == null
              && model.home() != null
              && (slot == null || !slot.getLocked()),
          () -> edit(meal));
      button(
          panel,
          slot != null && slot.getLocked() ? "解锁餐次" : "锁定餐次",
          !model.busy && model.pending == null && model.home() != null,
          () -> model.lock(meal));
    }
    LinearLayout preview = card(binding.civaSecondary, "待保存预览");
    if (model.draft.isEmpty()) label(preview, "选菜或生成后在这里确认。预览不会修改已保存的菜单。", 15);
    for (PlanSlotWrite slot : model.draft) {
      label(preview, slot.getDate() + " · " + mealName(slot.getMealType()), 16);
      if (slot.getItems().isEmpty()) label(preview, "清空该餐次", 14);
      for (PlanItem item : slot.getItems()) {
        label(
            preview, model.recipeName(item.getRecipeId()) + " × " + item.getServings() + " 份", 15);
        label(preview, model.reason(item.getRecipeId()), 13);
      }
    }
    button(
        preview,
        "确认保存菜单",
        !model.busy && model.pending == null && !model.draft.isEmpty(),
        model::save);
    button(
        preview,
        "放弃预览",
        !model.busy && model.pending == null,
        () -> {
          model.draft.clear();
          model.emit();
        });
    label(preview, "随机安排默认仅填空位，本版暂不匹配库存或个人忌口。计划不会自动计入食记。", 13);
  }

  private void generate() {
    LinearLayout content = column();
    Spinner days =
        spinner(content, "计划跨度", new String[] {"今天（1天）", "今明两天", "一周（7天）", "两周（14天）"}, 3);
    EditText servings =
        input(
            content,
            "每道菜份数",
            "1",
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    EditText minutes = input(content, "单道菜最多分钟", "60", InputType.TYPE_CLASS_NUMBER);
    List<CheckBox> checks = new ArrayList<>();
    for (String name : new String[] {"早餐", "午餐", "晚餐"}) {
      CheckBox check = new CheckBox(requireContext());
      check.setText(name);
      check.setChecked(true);
      content.addView(check);
      checks.add(check);
    }
    label(content, "从现有菜谱随机填入空餐次，不匹配库存或个人忌口。", 14);
    form(
        "随机安排未来餐食",
        content,
        "生成预览",
        dialog -> {
          try {
            List<PlanMealType> meals = new ArrayList<>();
            for (int i = 0; i < 3; i++)
              if (checks.get(i).isChecked()) meals.add(PlanMealType.values()[i]);
            String quantity = servings.getText().toString().trim();
            new BigDecimal(quantity);
            int limit = Integer.parseInt(minutes.getText().toString());
            if (limit < 1 || limit > 1440 || meals.isEmpty()) throw new IllegalArgumentException();
            model.generate(
                new int[] {1, 2, 7, 14}[days.getSelectedItemPosition()],
                meals,
                quantity,
                limit,
                true);
            dialog.dismiss();
          } catch (IllegalArgumentException error) {
            notice("请选择餐次，并填写有效份数和 1–1440 分钟");
          }
        });
  }

  private void edit(PlanMealType meal) {
    if (model.recipes.isEmpty()) {
      notice("暂时没有可用菜谱，请先由 AC 模块添加或完成接口联调");
      return;
    }
    PlanSlot slot = model.slot(model.selected, meal);
    List<PlanItem> selected = new ArrayList<>();
    if (slot != null) selected.addAll(slot.getItems());
    LinearLayout content = column();
    String[] names = model.recipes.stream().map(Recipe::getName).toArray(String[]::new);
    Spinner choice = spinner(content, "选择菜谱", names, 0);
    EditText quantity =
        input(content, "份数", "1", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    LinearLayout items = column();
    content.addView(items);
    Runnable show =
        () -> {
          items.removeAllViews();
          for (PlanItem item : new ArrayList<>(selected)) {
            label(
                items,
                model.recipeName(item.getRecipeId()) + " × " + item.getServings() + " 份",
                15);
            button(
                items,
                "移除",
                true,
                () -> {
                  selected.remove(item);
                  items.removeAllViews();
                  for (PlanItem remaining : selected)
                    label(
                        items,
                        model.recipeName(remaining.getRecipeId())
                            + " × "
                            + remaining.getServings()
                            + " 份",
                        15);
                });
          }
        };
    show.run();
    button(
        content,
        "加入 / 更新这道菜",
        true,
        () -> {
          try {
            String amount = quantity.getText().toString().trim();
            if (!amount.matches("(0|[1-9][0-9]{0,14})(\\.[0-9]{1,3})?")
                || new BigDecimal(amount).signum() <= 0) throw new IllegalArgumentException();
            Recipe recipe = model.recipes.get(choice.getSelectedItemPosition());
            selected.removeIf(item -> item.getRecipeId().equals(recipe.getId()));
            if (selected.size() >= 10) throw new IllegalArgumentException();
            PlanItem item = new PlanItem();
            item.setRecipeId(recipe.getId());
            item.setServings(amount);
            selected.add(item);
            show.run();
          } catch (IllegalArgumentException ex) {
            notice("份数需为大于零、最多三位小数；每餐最多十道菜");
          }
        });
    form(
        model.selected + " · " + mealName(meal),
        content,
        "查看保存预览",
        dialog -> {
          model.edit(meal, selected);
          dialog.dismiss();
        });
  }

  private static String mealName(PlanMealType meal) {
    return switch (meal) {
      case BREAKFAST -> "早餐";
      case LUNCH -> "午餐";
      case DINNER -> "晚餐";
    };
  }

  private void showRecipe(UUID id) {
    for (Recipe recipe : model.recipes)
      if (recipe.getId().equals(id)) {
        LinearLayout content = column();
        for (String step : recipe.getSteps()) label(content, step, 15);
        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(content);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(recipe.getName())
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show();
        return;
      }
    notice("尚未读取菜谱详情，请刷新后重试");
  }
}
