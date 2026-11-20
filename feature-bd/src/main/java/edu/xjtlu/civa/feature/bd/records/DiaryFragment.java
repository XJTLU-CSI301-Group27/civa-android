package edu.xjtlu.civa.feature.bd.records;

import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import edu.xjtlu.civa.api.model.*;
import edu.xjtlu.civa.core.ui.Route;
import edu.xjtlu.civa.feature.bd.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;

public final class DiaryFragment extends BdScreen {
  private DiaryViewModel model;

  public DiaryFragment() {}

  public static DiaryFragment create(String date) {
    DiaryFragment fragment = new DiaryFragment();
    Bundle args = new Bundle();
    args.putString("bd_date", date);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  protected void start(Bundle state) {
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
    model.changed.observe(getViewLifecycleOwner(), ignored -> render());
    if (!model.loaded) {
      String date = getArguments() == null ? null : getArguments().getString("bd_date");
      if (date != null) {
        try {
          model.selected = LocalDate.parse(date);
          model.month = YearMonth.from(model.selected);
        } catch (java.time.DateTimeException ignored) {
          model.message = "日期无效，已返回今天";
        }
      }
      model.load();
    }
  }

  private void render() {
    if (binding == null) return;
    title("我的饮食手帐", "每天的味道，都可以回来看看", model.message);
    button(binding.bdActions, "刷新", !model.busy, model::load);
    button(binding.bdActions, "回到今天", !model.busy, () -> model.select(LocalDate.now()));
    button(binding.bdActions, "选择日期", !model.busy, () -> pickDate(model.selected, model::select));
    button(binding.bdActions, "餐食计划", true, () -> host.navigate(Route.PLAN, null));
    LinearLayout calendar =
        card(binding.bdPrimary, model.month.getYear() + " 年 " + model.month.getMonthValue() + " 月");
    LinearLayout nav = new LinearLayout(requireContext());
    calendar.addView(nav);
    button(nav, "上个月", !model.busy, () -> model.changeMonth(-1));
    button(nav, "下个月", !model.busy, () -> model.changeMonth(1));
    GridLayout grid = new GridLayout(requireContext());
    grid.setColumnCount(7);
    calendar.addView(grid);
    for (String day : new String[] {"一", "二", "三", "四", "五", "六", "日"})
      cell(grid, day, null, false);
    int leading = model.month.atDay(1).getDayOfWeek().getValue() - 1;
    for (int i = 0; i < leading; i++) cell(grid, "", null, false);
    for (int day = 1; day <= model.month.lengthOfMonth(); day++) {
      LocalDate date = model.month.atDay(day);
      boolean marked =
          model.entries.stream().anyMatch(entry -> entry.getDate().equals(date.toString()));
      cell(grid, Integer.toString(day) + (marked ? " ·" : ""), date, date.equals(model.selected));
    }
    label(calendar, "· 表示有实际用餐记录", 13);
    LinearLayout details = card(binding.bdSecondary, model.selected + " · 实际用餐");
    button(
        details,
        "记录这一餐 / 补录",
        !model.busy && model.pending == null && !model.selected.isAfter(LocalDate.now()),
        () -> host.navigate(Route.MEAL_EDIT, model.selected.toString()));
    boolean found = false;
    for (EntryMealType meal : EntryMealType.values()) {
      boolean heading = false;
      for (MealEntry entry : model.entries)
        if (entry.getDate().equals(model.selected.toString()) && entry.getMealType() == meal) {
          found = true;
          if (!heading) {
            label(details, name(meal), 18);
            heading = true;
          }
          LinearLayout row = card(details, entry.getName());
          label(row, "实际份量 " + entry.getAmount(), 15);
          if (!entry.getNote().isEmpty()) label(row, entry.getNote(), 14);
          button(
              row,
              "编辑",
              !model.busy && model.pending == null,
              () -> host.navigate(Route.MEAL_EDIT, entry.getDate() + "|" + entry.getId()));
          button(
              row,
              "删除",
              !model.busy && model.pending == null,
              () ->
                  new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                      .setMessage("删除这条个人食记？不会恢复库存。")
                      .setNegativeButton("取消", null)
                      .setPositiveButton("删除", (d, w) -> model.delete(entry))
                      .show());
        }
    }
    if (!found)
      label(
          details,
          model.selected.isAfter(LocalDate.now()) ? "未来餐食请前往计划安排。" : "这一天还没有记录，可以补记一顿饭。",
          15);
    if (model.pending != null) {
      LinearLayout pending = card(binding.bdSecondary, "有提交结果待确认");
      button(pending, "使用原请求重试", !model.busy, model::retryPending);
      button(pending, "核对后移除本地标记", !model.busy, () -> abandon(model::abandon));
    }
    LinearLayout review = card(binding.bdPrimary, "饮食回顾");
    LocalDate monday = model.selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    button(review, "查看所选日期这一周", true, () -> model.loadSummary(monday, monday.plusDays(6)));
    button(
        review,
        "查看本月",
        true,
        () -> model.loadSummary(model.month.atDay(1), model.month.atEndOfMonth()));
    if (model.summary != null) {
      label(review, model.summary.getFrom() + " — " + model.summary.getTo(), 14);
      int count = 0;
      for (DaySummary day : model.summary.getDays()) count += day.getEntryCount();
      label(review, "共记录 " + count + " 条用餐", 22);
    }
  }

  private void cell(GridLayout grid, String text, LocalDate date, boolean selected) {
    TextView view = new TextView(requireContext());
    view.setText(text);
    view.setGravity(android.view.Gravity.CENTER);
    view.setTextSize(14);
    view.setMinHeight(dp(48));
    view.setPadding(0, dp(10), 0, dp(10));
    view.setTypeface(
        null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    params.width = 0;
    params.height = -2;
    params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
    grid.addView(view, params);
    if (date != null) {
      view.setContentDescription(date + (text.contains("·") ? " 有用餐记录" : ""));
      view.setOnClickListener(v -> model.select(date));
    }
  }

  private static String name(EntryMealType meal) {
    return switch (meal) {
      case BREAKFAST -> "早餐";
      case LUNCH -> "午餐";
      case DINNER -> "晚餐";
      case SNACK -> "加餐";
    };
  }
}
