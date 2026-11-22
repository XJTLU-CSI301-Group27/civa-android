package edu.xjtlu.civa.feature.ac.inventory;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
      model.draftQuantity = state.getString("draftQuantity", "");
      model.draftExpiry = state.getString("draftExpiry", "");
      model.draftIngredient = state.getInt("draftIngredient");
      model.draftLocation = state.getInt("draftLocation", 1);
    }
    binding.acAdd.setOnClickListener(v -> addFood());
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
    for (int i = 0; i < labels.length; i++) {
      String value = values[i];
      Chip chip = new Chip(requireContext());
      chip.setText(labels[i]);
      chip.setCheckable(true);
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
    if (model != null && !model.hasHousehold()) render();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle state) {
    super.onSaveInstanceState(state);
    if (model == null) return;
    state.putBoolean("cold", model.coldOpen);
    state.putBoolean("freeze", model.freezeOpen);
    state.putString("selected", model.selectedId);
    state.putString("filter", model.filter);
    state.putString("draftQuantity", model.draftQuantity);
    state.putString("draftExpiry", model.draftExpiry);
    state.putInt("draftIngredient", model.draftIngredient);
    state.putInt("draftLocation", model.draftLocation);
  }

  @Override
  public void onDestroyView() {
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
    binding.acLoading.setVisibility(model.loading ? View.VISIBLE : View.GONE);
    binding.acRecovery.setVisibility(model.pending != null ? View.VISIBLE : View.GONE);
    binding.acRetryWrite.setEnabled(!model.busy);
    binding.acAbandonWrite.setEnabled(!model.busy);
    binding.acAdd.setEnabled(
        !model.busy
            && !model.loading
            && !model.recovering
            && model.pending == null
            && model.hasHousehold());
    binding.acRefresh.setEnabled(!model.busy);
    for (int i = 0; i < binding.acFilters.getChildCount(); i++)
      binding.acFilters.getChildAt(i).setEnabled(!model.busy);
    boolean wide = getResources().getConfiguration().screenWidthDp >= 720;
    binding.acActions.setOrientation(
        getResources().getConfiguration().screenWidthDp < 400
            ? LinearLayout.VERTICAL
            : LinearLayout.HORIZONTAL);
    binding.acWorkspace.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    binding.acWorkspace.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    binding.acFridge.setLayoutParams(
        new LinearLayout.LayoutParams(
            wide
                ? dp(Math.min(480, getResources().getConfiguration().screenWidthDp - 340))
                : ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    binding.acDetail.setLayoutParams(
        new LinearLayout.LayoutParams(
            wide ? dp(260) : ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    buildFridge();
    buildDetail();
    updateOpenLabel();
  }

  private void buildFridge() {
    binding.acFridge.removeAllViews();
    coldFrame = compartment("冷藏室 · CHILL", new int[] {0, 1, 2}, false);
    freezeFrame = compartment("冷冻室 · FREEZE", new int[] {3, 4}, true);
    binding.acFridge.addView(coldFrame);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(10);
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
    binding.acFridge.addView(controls);
    List<InventoryBatch> elsewhere = new ArrayList<>();
    for (var batch : model.batches)
      if (InventoryPresentation.shelf(batch.getLocation()) < 0) elsewhere.add(batch);
    if (!elsewhere.isEmpty()) {
      binding.acFridge.addView(text("其他储存位置（未自动归类）", 14));
      for (var b : elsewhere) binding.acFridge.addView(foodTile(b));
    }
  }

  private FrameLayout compartment(String title, int[] shelves, boolean freezer) {
    FrameLayout frame = new FridgeCompartment(requireContext());
    frame.setBackground(round(R.color.ac_fridge_inside, 18));
    LinearLayout inside = column();
    inside.setPadding(dp(12), dp(15), dp(12), dp(12));
    inside.addView(text(title, 15));
    String[] names = {"蛋奶 · 每日补给", "蔬菜 · 留住鲜绿", "水果 · 一点甜", "肉类储备", "海鲜储备"};
    LinearLayout drawers = new LinearLayout(requireContext());
    if (freezer) inside.addView(drawers);
    for (int index : shelves) {
      LinearLayout area = freezer ? column() : inside;
      if (freezer) {
        area.setPadding(dp(5), 0, dp(5), 0);
        drawers.addView(area, new LinearLayout.LayoutParams(0, -2, 1));
      }
      TextView label = text(names[index], 12);
      label.setPadding(0, dp(12), 0, dp(5));
      area.addView(label);
      List<InventoryBatch> items = new ArrayList<>();
      for (var b : model.batches)
        if (InventoryPresentation.shelf(b.getLocation()) == index) items.add(b);
      if (items.isEmpty()) {
        TextView empty = text("暂无食材", 13);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(18), 0, dp(18));
        area.addView(empty);
      }
      if (freezer) {
        for (var batch : items) area.addView(frozenTile(batch));
      } else
        for (int offset = 0; offset < items.size(); offset += 3) {
          LinearLayout row = new LinearLayout(requireContext());
          for (int n = 0; n < 3; n++) {
            View tile =
                offset + n < items.size()
                    ? foodTile(items.get(offset + n))
                    : new View(requireContext());
            row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
          }
          area.addView(row);
        }
      View shelf = new View(requireContext());
      shelf.setBackgroundColor(color(R.color.ac_fridge_shelf));
      LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(4));
      line.topMargin = dp(6);
      area.addView(shelf, line);
    }
    frame.addView(inside, new FrameLayout.LayoutParams(-1, -2));
    LinearLayout door = column();
    door.setPadding(dp(24), dp(22), dp(24), dp(22));
    door.setBackground(round(R.color.ac_fridge_door, 18));
    TextView brand = text(freezer ? "冷冻室" : "civa", freezer ? 23 : 36);
    brand.setTypeface(Typeface.SERIF, Typeface.NORMAL);
    door.addView(brand);
    TextView hint = text(freezer ? "点击打开，备好每一餐 ↗" : "FRESH EVERY DAY\n\n点击打开冷藏门 ↗", 13);
    LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, 0, 1);
    door.addView(hint, hintLp);
    hint.setGravity(Gravity.BOTTOM);
    View handle = new View(requireContext());
    handle.setBackground(round(R.color.ac_fridge_inside, 8));
    LinearLayout.LayoutParams handleLp =
        new LinearLayout.LayoutParams(dp(8), dp(freezer ? 40 : 60));
    handleLp.gravity = Gravity.END;
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
    frame.setMinimumHeight(dp(freezer ? 210 : 450));
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
    tile.setPadding(dp(3), dp(7), dp(3), dp(7));
    tile.setMinimumHeight(dp(98));
    tile.setBackground(
        round(
            batch.getId().toString().equals(model.selectedId)
                ? R.color.ac_fridge_shelf
                : R.color.ac_fridge_inside,
            12));
    TextView emoji = text(InventoryPresentation.emoji(name), 30);
    tile.addView(emoji);
    TextView label = text(name, 12);
    label.setMaxLines(2);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    tile.addView(label);
    TextView qty = text(batch.getQuantity() + " " + unit, 13);
    qty.setTypeface(null, Typeface.BOLD);
    tile.addView(qty);
    String status =
        model.today() == null ? "家庭日期读取中" : InventoryPresentation.expiry(batch, model.today());
    TextView expiry = text(status, 11);
    expiry.setGravity(Gravity.CENTER);
    tile.addView(expiry);
    for (int i = 0; i < tile.getChildCount(); i++) {
      TextView child = (TextView) tile.getChildAt(i);
      child.setGravity(Gravity.CENTER);
      child.setPadding(0, 0, 0, 0);
    }
    tile.setContentDescription(name + "，剩余" + batch.getQuantity() + unit + "，" + status);
    tile.setClickable(true);
    tile.setFocusable(true);
    tile.setOnClickListener(v -> model.select(batch));
    return tile;
  }

  private View frozenTile(InventoryBatch batch) {
    String name = InventoryPresentation.name(batch, model.ingredient(batch));
    LinearLayout row = new LinearLayout(requireContext());
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(5), dp(5), dp(5), dp(5));
    row.setMinimumHeight(dp(64));
    row.setBackground(
        round(
            batch.getId().toString().equals(model.selectedId)
                ? R.color.ac_fridge_shelf
                : R.color.ac_fridge_inside,
            10));
    TextView emoji = text(InventoryPresentation.emoji(name), 26);
    row.addView(emoji, new LinearLayout.LayoutParams(dp(38), -2));
    LinearLayout labels = column();
    TextView title = text(name, 12);
    title.setMaxLines(2);
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
    row.setOnClickListener(v -> model.select(batch));
    return row;
  }

  private void buildDetail() {
    LinearLayout detail = binding.acDetail;
    detail.removeAllViews();
    detail.addView(text("冰箱里的小档案", 16));
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
      detail.addView(text("新鲜，有迹可循。\n\n打开冰箱门，点选食材查看批次、剩余数量及日期。", 15));
      return;
    }
    String name = InventoryPresentation.name(batch, model.ingredient(batch)),
        unit = InventoryPresentation.unit(batch.getUnit(), name);
    detail.addView(text(InventoryPresentation.emoji(name), 46));
    detail.addView(text(name, 22));
    detail.addView(text("剩余 " + batch.getQuantity() + " " + unit, 24));
    detail.addView(
        text(
            "储存位置  "
                + batch.getLocation()
                + "\n录入时间  "
                + batch.getCreatedAt()
                + "\n到期日期  "
                + (batch.getExpiresOn() == null ? "未知" : batch.getExpiresOn())
                + "\n批次编号  "
                + batch.getId().toString().substring(0, 8)
                + "\n版本  "
                + batch.getVersion(),
            13));
    detail.addView(
        text(
            model.today() == null
                ? "家庭日期未知，暂不能记录消耗"
                : InventoryPresentation.expiry(batch, model.today()),
            13));
    boolean editable = !model.busy && !model.loading && model.pending == null && !model.recovering;
    MaterialButton consume =
        button(new BigDecimal(batch.getQuantity()).signum() == 0 ? "已用完" : "记录消耗", false);
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
  }

  private void addFood() {
    if (model.ingredients.isEmpty()) {
      new MaterialAlertDialogBuilder(requireContext())
          .setTitle("暂无标准食材")
          .setMessage("请先刷新食材字典。若仍为空，需要 AC 在服务端维护标准食材；这里不会创建另一个食材 ID。已有停用食材仍可在库存中查看和消耗。")
          .setPositiveButton("知道了", null)
          .show();
      return;
    }
    List<Ingredient> options = new ArrayList<>(model.ingredients.values());
    LinearLayout form = column();
    form.setPadding(dp(20), dp(8), dp(20), dp(8));
    form.addView(plainText("标准食材（可搜索）", 14));
    AutoCompleteTextView picker = new AutoCompleteTextView(requireContext());
    picker.setSingleLine(true);
    picker.setThreshold(0);
    picker.setTextSize(16);
    picker.setMinimumHeight(dp(48));
    String[] names = new String[options.size()];
    for (int i = 0; i < options.size(); i++) {
      Ingredient item = options.get(i);
      names[i] =
          item.getName()
              + (item.getAliases() == null || item.getAliases().isEmpty()
                  ? ""
                  : "（" + android.text.TextUtils.join(" / ", item.getAliases()) + "）");
    }
    picker.setAdapter(
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names));
    form.addView(picker);
    final Ingredient[] selected = {
      options.get(Math.min(model.draftIngredient, options.size() - 1))
    };
    picker.setText(selected[0].getName(), false);
    picker.setOnClickListener(v -> picker.showDropDown());
    TextView unit =
        plainText(
            "标准单位：" + InventoryPresentation.unit(selected[0].getUnit(), selected[0].getName()), 13);
    form.addView(unit);
    picker.setOnItemClickListener(
        (parent, v, position, id) -> {
          String chosen = parent.getItemAtPosition(position).toString();
          for (int i = 0; i < options.size(); i++)
            if (names[i].equals(chosen)) {
              selected[0] = options.get(i);
              model.draftIngredient = i;
              picker.setText(selected[0].getName(), false);
              unit.setText(
                  "标准单位："
                      + InventoryPresentation.unit(selected[0].getUnit(), selected[0].getName()));
              break;
            }
        });
    TextInputEditText quantity = input(form, "剩余数量", model.draftQuantity, true),
        expiry = input(form, "到期日期 YYYY-MM-DD（可留空）", model.draftExpiry, false);
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
    watch(quantity, value -> model.draftQuantity = value);
    watch(expiry, value -> model.draftExpiry = value);
    form.addView(plainText("录入时间由服务器记录。单位来自食材标准；毫升不会按未知瓶容量换算。每次保存是独立批次。", 12));
    showForm(
        "把新鲜放进冰箱",
        form,
        () -> {
          if (!picker.getText().toString().equals(selected[0].getName()))
            throw new IllegalArgumentException("请从标准食材列表选择名称");
          model.create(
              selected[0],
              quantity.getText().toString(),
              InventoryPresentation.LOCATIONS[location.getSelectedItemPosition()],
              expiry.getText().toString());
          model.coldOpen = location.getSelectedItemPosition() < 3;
          model.freezeOpen = location.getSelectedItemPosition() >= 3;
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
                        Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG)
                            .show();
                      }
                    }));
    dialog.setOnDismissListener(ignored -> dialogs.remove(dialog));
    dialog.show();
  }

  private Spinner location(LinearLayout form, int selected) {
    form.addView(plainText("储存位置", 13));
    Spinner spinner = new Spinner(requireContext());
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
    return button;
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

  /** The overlay door must not stretch wrap-content compartments to the whole viewport. */
  private static final class FridgeCompartment extends FrameLayout {
    FridgeCompartment(android.content.Context context) {
      super(context);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      if (getChildCount() == 0) {
        super.onMeasure(widthSpec, heightSpec);
        return;
      }
      int width = MeasureSpec.getSize(widthSpec);
      View contents = getChildAt(0);
      contents.measure(
          MeasureSpec.makeMeasureSpec(
              Math.max(0, width - getPaddingLeft() - getPaddingRight()), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      int height =
          Math.max(
              getMinimumHeight(),
              contents.getMeasuredHeight() + getPaddingTop() + getPaddingBottom());
      super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
  }
}
