package edu.xjtlu.civa.feature.ac.inventory;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import edu.xjtlu.civa.api.model.*;
import edu.xjtlu.civa.core.ui.Route;
import edu.xjtlu.civa.feature.ac.R;
import edu.xjtlu.civa.feature.ac.databinding.AcFragmentInventoryBinding;
import java.math.BigDecimal;
import java.util.*;

/** Native Views implementation of the user-approved fridge interaction demo. */
public final class InventoryFragment extends Fragment {
  private AcFragmentInventoryBinding binding;
  private InventoryViewModel model;
  private final List<androidx.appcompat.app.AlertDialog> dialogs = new ArrayList<>();
  private FrameLayout coldFrame, freezeFrame;
  private View coldDoor, freezeDoor;
  private MaterialButton coldControl, freezeControl;
  private BottomSheetDialog detailDialog;
  private LinearLayout sheetDetail;
  private int workspaceHeight, workspaceWidth;
  private String presentedId;

  public InventoryFragment() {
    super(R.layout.ac_fragment_inventory);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    InventoryHost host = (InventoryHost) requireActivity();
    model =
        new ViewModelProvider(
                this,
                new ViewModelProvider.Factory() {
                  @NonNull
                  @Override
                  public <T extends ViewModel> T create(@NonNull Class<T> type) {
                    return type.cast(
                        new InventoryViewModel(host.api(), host.calls(), host.sessions()));
                  }
                })
            .get(InventoryViewModel.class);
    binding = AcFragmentInventoryBinding.bind(view);
    if (state != null) {
      model.coldOpen = state.getBoolean("cold", model.coldOpen);
      model.freezeOpen = state.getBoolean("freeze", model.freezeOpen);
      model.selectedId = state.getString("selected", model.selectedId);
      model.filter = state.getString("filter", model.filter);
      model.draftName = state.getString("draftName", "");
      model.draftUnitIndex = state.getInt("draftUnitIndex", 0);
      model.draftQuantity = state.getString("draftQuantity", "");
      model.draftExpiry = state.getString("draftExpiry", "");
      model.draftIngredient = state.getInt("draftIngredient");
      model.draftLocation = state.getInt("draftLocation", 1);
    }
    presentedId = model.selectedId;
    binding.acAdd.setOnClickListener(v -> requestAddFood());
    binding.acMessage.setOnClickListener(
        v -> {
          if (!model.hasHousehold()) requestAddFood();
          else
            new MaterialAlertDialogBuilder(requireContext())
                .setMessage(model.message)
                .setPositiveButton("知道了", null)
                .show();
        });
    binding.acWorkspace.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          if (r - l != workspaceWidth || b - t != workspaceHeight) {
            workspaceWidth = r - l;
            workspaceHeight = b - t;
            render();
          }
        });
    binding.acRefresh.setOnClickListener(v -> model.load());
    binding.acOpen.setOnClickListener(
        v -> {
          model.coldOpen = !(model.coldOpen && model.freezeOpen);
          model.freezeOpen = model.coldOpen;
          animateDoor(coldDoor, model.coldOpen);
          animateDoor(freezeDoor, model.freezeOpen);
          updateOpenLabel();
        });
    binding.acRetryWrite.setOnClickListener(v -> model.retry());
    binding.acAbandonWrite.setOnClickListener(
        v ->
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("结束待确认记录？")
                .setMessage("结束记录不会撤销服务器上的操作。请先刷新核对真实库存，确认后再进行新的操作，避免重复扣减。")
                .setNegativeButton("继续核对", null)
                .setPositiveButton("已核对，结束记录", (d, w) -> model.abandon())
                .show());
    String[] labels = {"全部", "未过期", "临期", "已过期"}, values = {"ALL", "VALID", "SOON", "EXPIRED"};
    binding.acFilters.removeAllViews();
    for (int i = 0; i < labels.length; i++) {
      String value = values[i];
      Chip chip = new Chip(requireContext());
      chip.setText(labels[i]);
      chip.setCheckable(true);
      chip.setTag(value);
      chip.setChipBackgroundColor(
          new ColorStateList(
              new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
              new int[] {color(R.color.ac_fridge_selected), color(R.color.ac_fridge_panel)}));
      chip.setTextColor(color(R.color.ac_fridge_ink));
      chip.setChipStrokeColor(ColorStateList.valueOf(color(R.color.ac_fridge_line)));
      chip.setChipStrokeWidth(dp(1));
      chip.setCheckedIconTint(ColorStateList.valueOf(color(R.color.ac_fridge_accent)));
      chip.setChecked(model.filter.equals(value));
      chip.setOnClickListener(
          v -> {
            for (int n = 0; n < binding.acFilters.getChildCount(); n++)
              ((Chip) binding.acFilters.getChildAt(n))
                  .setChecked(binding.acFilters.getChildAt(n) == chip);
            model.filter(value);
          });
      binding.acFilters.addView(chip);
    }
    model.revision.observe(getViewLifecycleOwner(), ignored -> render());
    model.load();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (model != null && model.scopeChanged()) model.load();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle state) {
    super.onSaveInstanceState(state);
    if (model == null) return;
    state.putBoolean("cold", model.coldOpen);
    state.putBoolean("freeze", model.freezeOpen);
    state.putString("selected", model.selectedId);
    state.putString("filter", model.filter);
    state.putString("draftName", model.draftName);
    state.putInt("draftUnitIndex", model.draftUnitIndex);
    state.putString("draftQuantity", model.draftQuantity);
    state.putString("draftExpiry", model.draftExpiry);
    state.putInt("draftIngredient", model.draftIngredient);
    state.putInt("draftLocation", model.draftLocation);
  }

  @Override
  public void onDestroyView() {
    if (detailDialog != null) detailDialog.dismiss();
    for (var dialog : new ArrayList<>(dialogs)) dialog.dismiss();
    dialogs.clear();
    coldDoor = null;
    freezeDoor = null;
    coldControl = null;
    freezeControl = null;
    binding = null;
    super.onDestroyView();
  }

  private void render() {
    if (binding == null) return;
    binding.acMessage.setText(model.message);
    binding.acMessage.setVisibility(
        model.message.isEmpty()
                || model.message.startsWith("已读取 ")
                || model.message.startsWith("当前筛选下暂无库存")
            ? View.GONE
            : View.VISIBLE);
    binding.acLoading.setVisibility(model.loading ? View.VISIBLE : View.GONE);
    binding.acRecovery.setVisibility(model.pending != null ? View.VISIBLE : View.GONE);
    binding.acRetryWrite.setEnabled(!model.busy);
    binding.acAbandonWrite.setEnabled(!model.busy);
    binding.acAdd.setEnabled(!model.busy);
    binding.acAdd.setText(model.busy ? "正在保存…" : getString(R.string.ac_add_food));
    binding.acRefresh.setEnabled(!model.busy);
    for (int i = 0; i < binding.acFilters.getChildCount(); i++) {
      Chip chip = (Chip) binding.acFilters.getChildAt(i);
      chip.setEnabled(!model.busy);
      chip.setChecked(model.filter.equals(chip.getTag()));
    }
    boolean wide = getResources().getConfiguration().screenWidthDp >= 720;
    binding.acRefresh.setTextColor(color(R.color.ac_fridge_muted));
    binding.acActions.setOrientation(LinearLayout.HORIZONTAL);
    binding.acWorkspace.setOrientation(LinearLayout.HORIZONTAL);
    binding.acWorkspace.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    binding.acFridge.setLayoutParams(
        new LinearLayout.LayoutParams(
            wide
                ? dp(Math.min(450, getResources().getConfiguration().screenWidthDp - 310))
                : ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    binding.acDetailScroll.setVisibility(wide ? View.VISIBLE : View.GONE);
    LinearLayout.LayoutParams detailParams =
        new LinearLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.MATCH_PARENT);
    detailParams.leftMargin = dp(8);
    binding.acDetailScroll.setLayoutParams(detailParams);
    buildFridge();
    buildDetail();
    if (sheetDetail != null) fillDetail(sheetDetail);
    if (!wide && model.selected() != null && !Objects.equals(presentedId, model.selectedId))
      showDetail();
    updateOpenLabel();
  }

  private void buildFridge() {
    binding.acFridge.removeAllViews();
    coldFrame = compartment("冷藏室 · CHILL", new int[] {0, 1, 2}, false);
    freezeFrame = compartment("冷冻室 · FREEZE", new int[] {3, 4}, true);
    coldFrame.setId(R.id.ac_cold_compartment);
    freezeFrame.setId(R.id.ac_frozen_compartment);
    binding.acFridge.addView(coldFrame, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
    lp.topMargin = dp(6);
    binding.acFridge.addView(freezeFrame, lp);
    LinearLayout controls = new LinearLayout(requireContext());
    controls.setGravity(Gravity.CENTER);
    MaterialButton cold = button("冷藏门 · " + (model.coldOpen ? "已打开" : "关闭"), true),
        freeze = button("冷冻门 · " + (model.freezeOpen ? "已打开" : "关闭"), true);
    coldControl = cold;
    freezeControl = freeze;
    cold.setOnClickListener(
        v -> {
          model.coldOpen = !model.coldOpen;
          animateDoor(coldDoor, model.coldOpen);
          cold.setText("冷藏门 · " + (model.coldOpen ? "已打开" : "关闭"));
          updateOpenLabel();
        });
    freeze.setOnClickListener(
        v -> {
          model.freezeOpen = !model.freezeOpen;
          animateDoor(freezeDoor, model.freezeOpen);
          freeze.setText("冷冻门 · " + (model.freezeOpen ? "已打开" : "关闭"));
          updateOpenLabel();
        });
    controls.addView(cold, new LinearLayout.LayoutParams(0, -2, 1));
    controls.addView(freeze, new LinearLayout.LayoutParams(0, -2, 1));
    binding.acFridge.addView(controls, new LinearLayout.LayoutParams(-1, dp(40)));
    List<InventoryBatch> elsewhere = new ArrayList<>();
    for (var batch : model.batches)
      if (InventoryPresentation.shelf(batch.getLocation()) < 0) elsewhere.add(batch);
    if (!elsewhere.isEmpty()) {
      TextView other = text("其他位置 · " + elsewhere.size() + "批  ›", 12);
      other.setOnClickListener(v -> showBatches("其他储存位置", elsewhere));
      binding.acFridge.addView(other, new LinearLayout.LayoutParams(-1, dp(32)));
    }
  }

  private FrameLayout compartment(String title, int[] shelves, boolean freezer) {
    FrameLayout frame = new FrameLayout(requireContext());
    GradientDrawable lining =
        gradient(R.color.ac_fridge_line, R.color.ac_fridge_panel, R.color.ac_fridge_line, 20);
    lining.setColors(
        new int[] {
          color(R.color.ac_fridge_line),
          color(R.color.ac_fridge_panel),
          color(R.color.ac_fridge_panel),
          color(R.color.ac_fridge_panel),
          color(R.color.ac_fridge_panel),
          color(R.color.ac_fridge_panel),
          color(R.color.ac_fridge_panel),
          color(R.color.ac_fridge_panel),
          color(R.color.ac_fridge_line)
        });
    frame.setBackground(lining);
    frame.setClipToOutline(true);
    LinearLayout inside = column();
    inside.setPadding(dp(8), dp(4), dp(8), dp(4));
    LinearLayout sectionTitle = new LinearLayout(requireContext());
    sectionTitle.setGravity(Gravity.CENTER_VERTICAL);
    sectionTitle.addView(text(title, 11), new LinearLayout.LayoutParams(0, -2, 1));
    TextView temperature = text(freezer ? "−18°C  ●" : "4°C  ●", 12);
    temperature.setTextColor(color(R.color.ac_fridge_accent));
    sectionTitle.addView(temperature);
    inside.addView(sectionTitle);
    String[] names = {"蛋奶 · 每日补给", "蔬菜 · 留住鲜绿", "水果 · 一点甜", "肉类储备", "海鲜储备"};
    LinearLayout drawers = new LinearLayout(requireContext());
    if (freezer) inside.addView(drawers, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int index : shelves) {
      LinearLayout area = column();
      if (freezer) {
        area.setPadding(dp(7), 0, dp(7), dp(6));
        GradientDrawable drawer = round(R.color.ac_fridge_drawer, 12);
        drawer.setStroke(dp(1), color(R.color.ac_fridge_line));
        area.setBackground(drawer);
        LinearLayout.LayoutParams drawerParams = new LinearLayout.LayoutParams(0, -1, 1);
        drawerParams.leftMargin = dp(index == 4 ? 8 : 0);
        drawers.addView(area, drawerParams);
      } else {
        inside.addView(area, new LinearLayout.LayoutParams(-1, 0, 1));
      }
      TextView label = text(names[index], 11);
      label.setTextColor(color(R.color.ac_fridge_muted));
      label.setPadding(0, dp(2), 0, 0);
      List<InventoryBatch> items = new ArrayList<>();
      for (var b : model.batches)
        if (InventoryPresentation.shelf(b.getLocation()) == index) items.add(b);
      label.setText(names[index] + (items.size() > 3 ? "  · " + items.size() + "批 ›" : ""));
      label.setOnClickListener(v -> showBatches(names[index], items));
      label.setContentDescription(names[index] + "，查看全部批次");
      area.addView(label);
      if (items.isEmpty()) {
        TextView empty = text("暂无食材", 13);
        empty.setGravity(Gravity.CENTER);
        area.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
      }
      if (freezer) {
        for (var batch : items.subList(0, Math.min(3, items.size())))
          area.addView(frozenTile(batch), new LinearLayout.LayoutParams(-1, 0, 1));
      } else if (!items.isEmpty()) {
        LinearLayout row = new LinearLayout(requireContext());
        for (int n = 0; n < 3; n++) {
          View tile = n < items.size() ? foodTile(items.get(n)) : new View(requireContext());
          row.addView(tile, new LinearLayout.LayoutParams(0, -1, 1));
        }
        area.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
      }
      View shelf = new View(requireContext());
      shelf.setBackgroundColor(color(R.color.ac_fridge_shelf));
      LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(3));
      line.topMargin = dp(2);
      if (freezer || index != 2) area.addView(shelf, line);
    }
    frame.addView(inside, new FrameLayout.LayoutParams(-1, -1));
    FrameLayout door = new FrameLayout(requireContext());
    door.setPadding(dp(26), dp(26), dp(26), dp(26));
    door.setBackground(
        gradient(
            R.color.ac_fridge_door_light, R.color.ac_fridge_door, R.color.ac_fridge_door_dark, 20));
    TextView brand = text(freezer ? "冷冻室   −18°C" : "civa", freezer ? 16 : 36);
    brand.setTypeface(Typeface.SERIF, Typeface.NORMAL);
    door.addView(brand, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
    if (!freezer) {
      TextView note = text("FRESH EVERY DAY", 9);
      note.setLetterSpacing(.18f);
      note.setAlpha(.6f);
      FrameLayout.LayoutParams noteParams = new FrameLayout.LayoutParams(-2, -2);
      noteParams.topMargin = dp(52);
      door.addView(note, noteParams);
      TextView display = text("4°\n冷藏", 24);
      display.setGravity(Gravity.CENTER);
      door.addView(display, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
    }
    TextView hint = text(freezer ? "点击打开，备好每一餐 ↗" : "点击打开冷藏门    ↗", 12);
    door.addView(hint, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
    View handle = new View(requireContext());
    handle.setBackground(
        gradient(
            R.color.ac_fridge_accent, R.color.ac_fridge_panel, R.color.ac_fridge_door_light, 7));
    handle.setElevation(dp(3));
    FrameLayout.LayoutParams handleLp =
        new FrameLayout.LayoutParams(
            dp(10), dp(freezer ? 65 : 78), Gravity.END | Gravity.CENTER_VERTICAL);
    door.addView(handle, handleLp);
    frame.addView(door, new FrameLayout.LayoutParams(-1, -1));
    door.setContentDescription(freezer ? "打开冷冻门" : "打开冷藏门");
    door.setClickable(true);
    door.setFocusable(true);
    boolean open = freezer ? model.freezeOpen : model.coldOpen;
    door.setVisibility(open ? View.GONE : View.VISIBLE);
    inside.setImportantForAccessibility(
        open
            ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    door.setOnClickListener(
        v -> {
          if (freezer) model.freezeOpen = true;
          else model.coldOpen = true;
          animateDoor(door, true);
          updateOpenLabel();
        });
    if (freezer) freezeDoor = door;
    else coldDoor = door;
    return frame;
  }

  private void animateDoor(View door, boolean open) {
    if (door == null) return;
    door.animate().cancel();
    door.setCameraDistance(dp(1800));
    door.setPivotX(0);
    door.setVisibility(View.VISIBLE);
    door.setClickable(!open);
    door.setFocusable(!open);
    View inside = ((ViewGroup) door.getParent()).getChildAt(0);
    inside.setImportantForAccessibility(
        open
            ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    door.animate()
        .rotationY(open ? -95 : 0)
        .alpha(open ? 0 : 1)
        .setDuration(450)
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator a) {
                if (open) door.setVisibility(View.GONE);
              }
            })
        .start();
  }

  private void updateOpenLabel() {
    if (binding != null)
      binding.acOpen.setText(model.coldOpen && model.freezeOpen ? "关上冰箱" : "打开冰箱");
    if (coldControl != null) coldControl.setText("冷藏门 · " + (model.coldOpen ? "已打开" : "关闭"));
    if (freezeControl != null) freezeControl.setText("冷冻门 · " + (model.freezeOpen ? "已打开" : "关闭"));
  }

  private View foodTile(InventoryBatch batch) {
    Ingredient ingredient = model.ingredient(batch);
    String name = InventoryPresentation.name(batch, ingredient),
        unit = InventoryPresentation.unit(batch.getUnit(), name);
    LinearLayout tile = column();
    tile.setGravity(Gravity.CENTER);
    tile.setPadding(dp(2), 0, dp(2), 0);
    tile.setBackground(foodBackground(batch, 12));
    TextView emoji = text(InventoryPresentation.emoji(name), compact() ? 22 : 30);
    if (!tiny()) tile.addView(emoji);
    TextView label = text(name, 12);
    label.setMaxLines(1);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    tile.addView(label);
    TextView qty = text(batch.getQuantity() + " " + unit, 13);
    qty.setTypeface(null, Typeface.BOLD);
    tile.addView(qty);
    String status =
        model.today() == null ? "家庭日期读取中" : InventoryPresentation.expiry(batch, model.today());
    TextView expiry = text(status, 11);
    expiry.setTextColor(color(expiryColor(batch)));
    expiry.setGravity(Gravity.CENTER);
    if (!compact()) tile.addView(expiry);
    else label.setTextColor(color(expiryColor(batch)));
    for (int i = 0; i < tile.getChildCount(); i++) {
      TextView child = (TextView) tile.getChildAt(i);
      child.setGravity(Gravity.CENTER);
      child.setPadding(0, 0, 0, 0);
    }
    tile.setContentDescription(name + "，剩余" + batch.getQuantity() + unit + "，" + status);
    tile.setClickable(true);
    tile.setFocusable(true);
    tile.setOnClickListener(v -> selectBatch(batch));
    return tile;
  }

  private View frozenTile(InventoryBatch batch) {
    String name = InventoryPresentation.name(batch, model.ingredient(batch));
    LinearLayout row = new LinearLayout(requireContext());
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(3), 0, dp(3), 0);
    row.setBackground(foodBackground(batch, 10));
    TextView emoji = text(InventoryPresentation.emoji(name), compact() ? 20 : 26);
    row.addView(emoji, new LinearLayout.LayoutParams(dp(compact() ? 28 : 38), -2));
    LinearLayout labels = column();
    TextView title = text(name, 12);
    title.setMaxLines(1);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    labels.addView(title);
    TextView qty =
        text(batch.getQuantity() + " " + InventoryPresentation.unit(batch.getUnit(), name), 13);
    labels.addView(qty);
    title.setPadding(0, 0, 0, 0);
    qty.setPadding(0, 0, 0, 0);
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    row.setContentDescription(
        name + "，剩余" + batch.getQuantity() + InventoryPresentation.unit(batch.getUnit(), name));
    row.setClickable(true);
    row.setFocusable(true);
    row.setOnClickListener(v -> selectBatch(batch));
    return row;
  }

  private void buildDetail() {
    fillDetail(binding.acDetail);
  }

  private void fillDetail(LinearLayout detail) {
    detail.removeAllViews();
    TextView kicker = text("冰箱里的小档案", 12);
    kicker.setTextColor(color(R.color.ac_fridge_muted));
    kicker.setLetterSpacing(.12f);
    detail.addView(kicker);
    View rule = new View(requireContext());
    rule.setBackgroundColor(color(R.color.ac_fridge_line));
    LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, dp(1));
    ruleParams.topMargin = dp(12);
    ruleParams.bottomMargin = dp(16);
    detail.addView(rule, ruleParams);
    if (!model.hasHousehold()) {
      detail.addView(text("请先创建或加入家庭，再管理共享库存。", 14));
      MaterialButton home = button("前往家庭", false);
      home.setOnClickListener(
          v -> ((InventoryHost) requireActivity()).navigate(Route.HOUSEHOLD, null));
      detail.addView(home);
      return;
    }
    InventoryBatch batch = model.selected();
    if (batch == null) {
      TextView mark = text("✳", 48);
      mark.setTextColor(color(R.color.ac_fridge_accent));
      detail.addView(mark);
      detail.addView(text("新鲜，有迹可循。", 21));
      TextView prompt = text("打开冰箱门，点一下食材，查看剩余数量和存放日期。", 13);
      prompt.setTextColor(color(R.color.ac_fridge_muted));
      prompt.setLineSpacing(dp(5), 1);
      detail.addView(prompt);
      detail.addView(text("冷藏   蛋奶 / 蔬菜 / 水果\n\n冷冻   肉类 / 海鲜", 12));
      detail.addView(text("● 正常    ● 临期    ● 已过期", 11));
      return;
    }
    String name = InventoryPresentation.name(batch, model.ingredient(batch)),
        unit = InventoryPresentation.unit(batch.getUnit(), name);
    detail.addView(text(InventoryPresentation.emoji(name), compact() ? 36 : 62));
    detail.addView(text(name, 22));
    detail.addView(text("剩余 " + batch.getQuantity() + " " + unit, 29));
    property(detail, "储存位置", batch.getLocation());
    property(detail, "录入日期", displayDate(batch.getCreatedAt()));
    property(detail, "到期日期", batch.getExpiresOn() == null ? "未知" : batch.getExpiresOn());
    property(detail, "库存批次", batch.getId().toString().substring(0, 8));
    TextView expiry =
        text(
            model.today() == null
                ? "家庭日期未知，暂不能记录消耗"
                : InventoryPresentation.expiry(batch, model.today()),
            12);
    expiry.setTextColor(color(expiryColor(batch)));
    expiry.setPadding(dp(12), dp(12), dp(12), dp(12));
    expiry.setBackground(round(expiryBackground(batch), 9));
    LinearLayout.LayoutParams expiryParams = new LinearLayout.LayoutParams(-1, -2);
    expiryParams.topMargin = dp(16);
    expiryParams.bottomMargin = dp(14);
    detail.addView(expiry, expiryParams);
    boolean editable = !model.busy && !model.loading && model.pending == null && !model.recovering;
    MaterialButton consume =
        button(
            new BigDecimal(batch.getQuantity()).signum() == 0
                ? "已用完"
                : model.today() != null && InventoryPresentation.expired(batch, model.today())
                    ? "已过期，请检查处理"
                    : "记录消耗",
            false);
    consume.setEnabled(
        editable
            && new BigDecimal(batch.getQuantity()).signum() > 0
            && model.today() != null
            && !InventoryPresentation.expired(batch, model.today()));
    consume.setOnClickListener(v -> quantityDialog(batch, ChangeReason.CONSUME));
    detail.addView(consume);
    MaterialButton discard = button("记录丢弃", true);
    discard.setEnabled(editable && new BigDecimal(batch.getQuantity()).signum() > 0);
    discard.setOnClickListener(v -> quantityDialog(batch, ChangeReason.DISCARD));
    detail.addView(discard);
    MaterialButton correction = button("库存修正", true);
    correction.setEnabled(editable);
    correction.setOnClickListener(v -> quantityDialog(batch, ChangeReason.CORRECT));
    detail.addView(correction);
    MaterialButton edit = button("修改位置 / 到期日", true);
    edit.setEnabled(editable);
    edit.setOnClickListener(v -> editDialog(batch));
    detail.addView(edit);
    if (model.latestMovement != null) {
      InventoryMovement movement = model.latestMovement;
      String kind =
          switch (movement.getReason()) {
            case CONSUME -> "最近消耗";
            case DISCARD -> "最近丢弃";
            case CORRECT -> "最近修正";
            case COOK -> "最近做饭扣减";
          };
      TextView recent =
          text(
              kind
                  + "："
                  + movement.getBeforeQuantity()
                  + " → "
                  + movement.getAfterQuantity()
                  + " "
                  + unit
                  + "\n"
                  + displayDate(movement.getCreatedAt()),
              12);
      recent.setId(R.id.ac_recent_movement);
      recent.setTextColor(color(R.color.ac_fridge_muted));
      detail.addView(recent);
    } else if (!model.movementMessage.isEmpty()) {
      detail.addView(text(model.movementMessage, 12));
    }
  }

  private boolean compact() {
    return workspaceHeight > 0
        && workspaceHeight / getResources().getDisplayMetrics().density < 800;
  }

  private boolean tiny() {
    return workspaceHeight > 0
        && workspaceHeight / getResources().getDisplayMetrics().density < 500;
  }

  private void selectBatch(InventoryBatch batch) {
    model.select(batch);
    if (getResources().getConfiguration().screenWidthDp < 720) showDetail();
  }

  private void showDetail() {
    if (detailDialog != null || binding == null) return;
    presentedId = model.selectedId;
    sheetDetail = column();
    sheetDetail.setPadding(dp(20), dp(8), dp(20), dp(16));
    fillDetail(sheetDetail);
    ScrollView scroll = new ScrollView(requireContext());
    scroll.addView(sheetDetail);
    detailDialog = new BottomSheetDialog(requireContext());
    detailDialog.setContentView(scroll);
    detailDialog.setOnDismissListener(
        d -> {
          detailDialog = null;
          sheetDetail = null;
        });
    detailDialog.show();
    detailDialog
        .getBehavior()
        .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
  }

  private void showBatches(String title, List<InventoryBatch> batches) {
    if (batches.isEmpty()) {
      new MaterialAlertDialogBuilder(requireContext())
          .setTitle(title)
          .setMessage("这一层暂无食材，点击顶部添加食材即可录入。")
          .setPositiveButton("知道了", null)
          .show();
      return;
    }
    String[] choices = new String[batches.size()];
    for (int i = 0; i < batches.size(); i++) {
      InventoryBatch batch = batches.get(i);
      String name = InventoryPresentation.name(batch, model.ingredient(batch));
      choices[i] =
          name
              + " · "
              + batch.getQuantity()
              + " "
              + InventoryPresentation.unit(batch.getUnit(), name)
              + "\n到期 "
              + (batch.getExpiresOn() == null ? "未知" : batch.getExpiresOn())
              + " · 批次 "
              + batch.getId().toString().substring(0, 8);
    }
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setItems(choices, (d, which) -> selectBatch(batches.get(which)))
        .setNegativeButton("关闭", null)
        .show();
  }

  private void requestAddFood() {
    String reason =
        !model.hasHousehold()
            ? "冰箱库存按家庭保存。请先创建或加入家庭，再添加食材。"
            : model.loading
                ? "正在读取库存，请稍候再试。"
                : model.recovering
                    ? "正在核对上次的操作，请稍候再试。"
                    : model.pending != null ? "上次操作的结果尚未确认。请先核对并重试原请求，避免重复添加。" : null;
    if (reason != null) {
      var notice =
          new MaterialAlertDialogBuilder(requireContext())
              .setTitle(!model.hasHousehold() ? "先设置家庭" : "请稍候")
              .setMessage(reason)
              .setNegativeButton("取消", null);
      if (!model.hasHousehold())
        notice.setPositiveButton(
            "前往家庭", (d, w) -> ((InventoryHost) requireActivity()).navigate(Route.HOUSEHOLD, null));
      else notice.setPositiveButton("知道了", null);
      notice.show();
      return;
    }
    addFood();
  }

  private void property(LinearLayout detail, String label, String value) {
    LinearLayout row = new LinearLayout(requireContext());
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView key = text(label, 12);
    key.setTextColor(color(R.color.ac_fridge_muted));
    row.addView(key, new LinearLayout.LayoutParams(dp(74), -2));
    TextView content = text(value == null ? "未知" : value, 12);
    content.setGravity(Gravity.END);
    row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
    detail.addView(row);
  }

  private GradientDrawable foodBackground(InventoryBatch batch, int radius) {
    GradientDrawable shape = round(R.color.ac_fridge_selected, radius);
    if (batch.getId().toString().equals(model.selectedId)) {
      shape.setStroke(dp(1), color(R.color.ac_fridge_accent));
    } else {
      shape.setColor(android.graphics.Color.TRANSPARENT);
    }
    return shape;
  }

  private String displayDate(String timestamp) {
    if (timestamp == null) return "未知";
    try {
      return java.time.OffsetDateTime.parse(timestamp)
          .atZoneSameInstant(java.time.ZoneId.of(model.zoneId))
          .toLocalDate()
          .toString();
    } catch (java.time.DateTimeException | NullPointerException invalid) {
      return timestamp;
    }
  }

  private int expiryColor(InventoryBatch batch) {
    if (model.today() == null || batch.getExpiresOn() == null) return R.color.ac_fridge_muted;
    if (InventoryPresentation.expired(batch, model.today())) return R.color.ac_fridge_expired;
    if (java.time.LocalDate.parse(batch.getExpiresOn()).isAfter(model.today().plusDays(3)))
      return R.color.ac_fridge_muted;
    return R.color.ac_fridge_soon;
  }

  private int expiryBackground(InventoryBatch batch) {
    int color = expiryColor(batch);
    return color == R.color.ac_fridge_expired
        ? R.color.ac_fridge_expired_background
        : color == R.color.ac_fridge_soon
            ? R.color.ac_fridge_soon_background
            : R.color.ac_fridge_selected;
  }

  private void addFood() {
    Set<UUID> usedAtHome = new HashSet<>();
    for (InventoryBatch batch : model.batches) usedAtHome.add(batch.getIngredientId());
    IngredientChoices choices = new IngredientChoices(model.ingredients.values(), usedAtHome);
    LinearLayout form = column();
    form.setPadding(dp(20), dp(8), dp(20), dp(8));
    TextInputLayout nameLayout = new TextInputLayout(requireContext());
    nameLayout.setHint("Ingredient name，例如 beef");
    TextInputEditText picker = new TextInputEditText(nameLayout.getContext());
    picker.setId(R.id.ac_food_name);
    picker.setSingleLine(true);
    picker.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    picker.setText(model.draftName);
    nameLayout.addView(picker, new LinearLayout.LayoutParams(-1, -2));
    form.addView(nameLayout, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout matches = column();
    form.addView(matches);
    final Ingredient[] selected = {choices.exact(model.draftName)};
    final boolean[] choosing = {false};
    TextView unit = plainText("", 13);
    form.addView(unit);
    Spinner newUnit = new Spinner(requireContext());
    newUnit.setId(R.id.ac_unit_input);
    newUnit.setMinimumHeight(dp(48));
    newUnit.setAdapter(
        new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"选择新食材单位", "克 (g)", "个 / 瓶 (count)", "毫升 (ml)"}));
    newUnit.setSelection(model.draftUnitIndex);
    newUnit.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            model.draftUnitIndex = pos;
          }

          @Override
          public void onNothingSelected(AdapterView<?> p) {}
        });
    form.addView(newUnit);
    TextInputEditText quantity = input(form, "剩余数量", model.draftQuantity, true);
    TextInputEditText expiry = input(form, "到期日期 YYYY-MM-DD（可留空）", model.draftExpiry, false);
    quantity.setId(R.id.ac_quantity_input);
    expiry.setId(R.id.ac_expiry_input);
    Spinner location = location(form, model.draftLocation);
    location.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            model.draftLocation = pos;
          }

          @Override
          public void onNothingSelected(AdapterView<?> p) {}
        });
    Runnable updateUnit =
        () -> {
          unit.setVisibility(selected[0] == null ? View.GONE : View.VISIBLE);
          newUnit.setVisibility(selected[0] == null ? View.VISIBLE : View.GONE);
          if (selected[0] != null)
            unit.setText(
                "单位：" + InventoryPresentation.unit(selected[0].getUnit(), selected[0].getName()));
        };
    java.util.function.Consumer<Ingredient> choose =
        item -> {
          selected[0] = item;
          choosing[0] = true;
          picker.setText(item.getName());
          picker.setSelection(picker.length());
          choosing[0] = false;
          model.draftName = item.getName();
          location.setSelection(InventoryPresentation.suggestedShelf(item.getName()));
          updateUnit.run();
          matches.removeAllViews();
          quantity.requestFocus();
        };
    Runnable showMatches =
        () -> {
          matches.removeAllViews();
          String query = picker.getText().toString();
          if (query.trim().isEmpty()) return;
          if (choices.exact(query) != null) return;
          List<Ingredient> results = choices.search(query);
          if (results.isEmpty()) {
            matches.addView(plainText("新食材：选择单位后即可直接保存。", 12));
            return;
          }
          for (Ingredient item : results.subList(0, Math.min(3, results.size()))) {
            MaterialButton candidate = button(IngredientChoices.label(item), true);
            candidate.setOnClickListener(v -> choose.accept(item));
            matches.addView(candidate, new LinearLayout.LayoutParams(-1, dp(44)));
          }
          if (results.size() > 3) matches.addView(plainText("继续输入名称可缩小范围。", 12));
        };
    watch(
        picker,
        value -> {
          boolean different = !Objects.equals(model.draftName, value);
          model.draftName = value;
          if (choosing[0]) return;
          Ingredient exact = choices.exact(value);
          if (exact != null && (selected[0] == null || !exact.getId().equals(selected[0].getId())))
            location.setSelection(InventoryPresentation.suggestedShelf(exact.getName()));
          selected[0] = exact;
          if (different && exact == null) newUnit.setSelection(0);
          updateUnit.run();
          showMatches.run();
        });
    updateUnit.run();
    showMatches.run();
    watch(quantity, value -> model.draftQuantity = value);
    watch(expiry, value -> model.draftExpiry = value);
    form.addView(plainText("每次添加为独立批次，录入日期自动记录。", 12));
    showForm(
        "把新鲜放进冰箱",
        form,
        () -> {
          String typed = picker.getText().toString();
          Ingredient ingredient = selected[0];
          if (ingredient == null
              || !IngredientChoices.normalize(typed)
                  .equals(IngredientChoices.normalize(ingredient.getName())))
            ingredient = choices.exact(typed);
          if (ingredient == null) {
            Unit[] units = {null, Unit.G, Unit.COUNT, Unit.ML};
            Unit chosenUnit = units[newUnit.getSelectedItemPosition()];
            Ingredient existing = chosenUnit == null ? null : choices.exact(typed, chosenUnit);
            if (existing != null)
              model.create(
                  existing,
                  quantity.getText().toString(),
                  InventoryPresentation.LOCATIONS[location.getSelectedItemPosition()],
                  expiry.getText().toString());
            else
              model.createNamed(
                  typed,
                  chosenUnit,
                  quantity.getText().toString(),
                  InventoryPresentation.LOCATIONS[location.getSelectedItemPosition()],
                  expiry.getText().toString());
          } else {
            model.create(
                ingredient,
                quantity.getText().toString(),
                InventoryPresentation.LOCATIONS[location.getSelectedItemPosition()],
                expiry.getText().toString());
          }
          model.draftName = "";
          model.draftUnitIndex = 0;
          model.draftQuantity = "";
          model.draftExpiry = "";
        });
  }

  private void quantityDialog(InventoryBatch batch, ChangeReason reason) {
    String unit =
        InventoryPresentation.unit(
            batch.getUnit(), InventoryPresentation.name(batch, model.ingredient(batch)));
    boolean correction = reason == ChangeReason.CORRECT;
    String title = correction ? "修正库存" : reason == ChangeReason.DISCARD ? "记录丢弃" : "记录消耗";
    LinearLayout form = column();
    form.setPadding(dp(20), dp(8), dp(20), dp(8));
    form.addView(plainText("当前剩余 " + batch.getQuantity() + " " + unit + " · 此操作仅作用于当前批次", 14));
    TextInputEditText amount =
        input(form, correction ? "修正后的剩余数量（" + unit + "）" : "本次用量（" + unit + "）", "", true);
    amount.setId(R.id.ac_quantity_input);
    TextView preview = plainText("请输入数量后核对余额", 14);
    form.addView(preview);
    watch(
        amount,
        value -> {
          try {
            String after =
                correction
                    ? InventoryPresentation.number(InventoryPresentation.quantity(value))
                    : InventoryPresentation.after(batch.getQuantity(), value);
            preview.setText("确认后剩余 " + after + " " + unit);
            amount.setError(null);
          } catch (IllegalArgumentException error) {
            preview.setText("请输入合法数量，不能超过当前剩余量");
          }
        });
    if (!correction) {
      MaterialButton all = button("全部用完", true);
      all.setOnClickListener(v -> amount.setText(batch.getQuantity()));
      form.addView(all);
    }
    showForm(title, form, () -> model.change(batch, amount.getText().toString(), reason));
  }

  private void editDialog(InventoryBatch batch) {
    LinearLayout form = column();
    form.setPadding(dp(20), dp(8), dp(20), dp(8));
    int index = -1;
    for (int i = 0; i < InventoryPresentation.LOCATIONS.length; i++)
      if (InventoryPresentation.LOCATIONS[i].equals(batch.getLocation())) index = i;
    Spinner location = location(form, index < 0 ? 1 : index);
    if (index < 0) {
      String[] options = Arrays.copyOf(InventoryPresentation.LOCATIONS, 6);
      options[5] = batch.getLocation();
      location.setAdapter(
          new ArrayAdapter<>(
              requireContext(), android.R.layout.simple_spinner_dropdown_item, options));
      location.setSelection(5);
    }
    TextInputEditText expiry =
        input(
            form,
            "到期日期 YYYY-MM-DD（可留空）",
            batch.getExpiresOn() == null ? "" : batch.getExpiresOn(),
            false);
    expiry.setId(R.id.ac_expiry_input);
    showForm(
        "修改批次资料",
        form,
        () ->
            model.update(
                batch,
                location.getSelectedItemPosition() == 5
                    ? batch.getLocation()
                    : InventoryPresentation.LOCATIONS[location.getSelectedItemPosition()],
                expiry.getText().toString()));
  }

  private void showForm(String title, LinearLayout form, Runnable submit) {
    TextView validation = plainText("", 13);
    validation.setId(R.id.ac_form_error);
    validation.setTextColor(color(R.color.ac_fridge_expired));
    validation.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    validation.setVisibility(View.GONE);
    form.addView(validation);
    ScrollView scroll = new ScrollView(requireContext());
    scroll.addView(form);
    var dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认保存", null)
            .create();
    dialogs.add(dialog);
    dialog.setOnShowListener(
        ignored ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        submit.run();
                        dialog.dismiss();
                      } catch (IllegalArgumentException error) {
                        validation.setText(error.getMessage());
                        validation.setVisibility(View.VISIBLE);
                      }
                    }));
    dialog.setOnDismissListener(ignored -> dialogs.remove(dialog));
    dialog.show();
  }

  private Spinner location(LinearLayout form, int selected) {
    form.addView(plainText("储存位置", 13));
    Spinner spinner = new Spinner(requireContext());
    spinner.setId(R.id.ac_location_input);
    spinner.setMinimumHeight(dp(48));
    spinner.setAdapter(
        new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            InventoryPresentation.LOCATIONS));
    spinner.setSelection(Math.max(0, selected));
    form.addView(spinner);
    return spinner;
  }

  private TextInputEditText input(LinearLayout form, String label, String value, boolean numeric) {
    TextInputLayout layout = new TextInputLayout(requireContext());
    layout.setHint(label);
    TextInputEditText edit = new TextInputEditText(layout.getContext());
    edit.setSingleLine(true);
    edit.setInputType(
        numeric
            ? android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            : android.text.InputType.TYPE_CLASS_TEXT);
    edit.setText(value);
    layout.addView(edit, new LinearLayout.LayoutParams(-1, -2));
    form.addView(layout, new LinearLayout.LayoutParams(-1, -2));
    return edit;
  }

  private void watch(EditText edit, java.util.function.Consumer<String> change) {
    edit.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            change.accept(s.toString());
          }

          @Override
          public void afterTextChanged(Editable e) {}
        });
  }

  private LinearLayout column() {
    LinearLayout column = new LinearLayout(requireContext());
    column.setOrientation(LinearLayout.VERTICAL);
    return column;
  }

  private TextView text(String value, int size) {
    TextView text = plainText(value, size);
    text.setTextColor(color(R.color.ac_fridge_ink));
    return text;
  }

  private TextView plainText(String value, int size) {
    TextView text = new TextView(requireContext());
    text.setText(value);
    text.setTextSize(size);
    text.setPadding(0, dp(5), 0, dp(5));
    return text;
  }

  private MaterialButton button(String label, boolean outline) {
    MaterialButton button =
        new MaterialButton(
            requireContext(),
            null,
            outline
                ? com.google.android.material.R.attr.materialButtonOutlinedStyle
                : com.google.android.material.R.attr.materialButtonStyle);
    button.setText(label);
    button.setAllCaps(false);
    button.setMinimumHeight(dp(48));
    button.setTextSize(12);
    button.setCornerRadius(dp(28));
    button.setElevation(0);
    button.setStateListAnimator(null);
    button.setStrokeWidth(outline ? dp(1) : 0);
    button.setStrokeColor(ColorStateList.valueOf(color(R.color.ac_fridge_line)));
    button.setBackgroundTintList(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {
              color(R.color.ac_fridge_line),
              color(outline ? R.color.ac_fridge_panel : R.color.ac_fridge_ink)
            }));
    button.setTextColor(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {
              color(R.color.ac_fridge_muted),
              color(outline ? R.color.ac_fridge_ink : R.color.ac_fridge_background)
            }));
    return button;
  }

  private GradientDrawable gradient(int start, int center, int end, int radius) {
    GradientDrawable shape =
        new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {color(start), color(center), color(end)});
    shape.setCornerRadius(dp(radius));
    shape.setStroke(dp(1), color(R.color.ac_fridge_line));
    return shape;
  }

  private GradientDrawable round(int resource, int radius) {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(color(resource));
    shape.setCornerRadius(dp(radius));
    return shape;
  }

  private int color(int id) {
    return androidx.core.content.ContextCompat.getColor(requireContext(), id);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
