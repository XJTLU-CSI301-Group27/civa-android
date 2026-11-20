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
import java.math.BigDecimal;
import java.util.UUID;

public final class CookingFragment extends CivaScreen<AppServices> {
  private CookingViewModel model;

  public CookingFragment() {}

  public static CookingFragment create(String reference) {
    CookingFragment fragment = new CookingFragment();
    Bundle args = new Bundle();
    args.putString("bd_reference", reference);
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
                    return type.cast(
                        new CookingViewModel(host.api(), host.calls(), host.sessions()));
                  }
                })
            .get(CookingViewModel.class);
    if (!model.loaded) {
      try {
        String ref = getArguments() == null ? null : getArguments().getString("bd_reference");
        String[] parts = ref == null ? new String[0] : ref.split("\\|", -1);
        if (parts.length > 0) model.recipeId = UUID.fromString(parts[0]);
        if (parts.length > 1) model.servings = parts[1];
        if (parts.length > 2 && !parts[2].isEmpty()) model.slotId = UUID.fromString(parts[2]);
      } catch (IllegalArgumentException ex) {
        model.message = "无效菜谱，请从计划或菜谱进入";
      }
    }
    model.changed.observe(getViewLifecycleOwner(), ignored -> render());
    if (!model.loaded) model.load();
  }

  private void render() {
    if (binding == null) return;
    title("确认这次做饭", "做饭属于家庭记录，吃饭由每个人另行确认", model.message);
    button(binding.civaActions, "返回计划", true, () -> host.navigate(Route.PLAN, null));
    button(binding.civaActions, "我的食记", true, () -> host.navigate(Route.DIARY, null));
    LinearLayout form = card(binding.civaPrimary, "实际制作份数");
    label(form, model.recipeId == null ? "请先选择菜谱" : "菜谱 " + model.recipeId, 14);
    EditText servings =
        input(
            form,
            "制作多少份",
            model.servings,
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    servings.setEnabled(!model.busy && model.pending == null && model.saved == null);
    servings.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            model.servings = s.toString();
            model.preview = null;
          }

          public void afterTextChanged(Editable e) {}
        });
    button(
        form,
        "读取食材扣减预览",
        !model.busy && model.pending == null && model.saved == null && model.recipeId != null,
        () -> {
          try {
            if (!model.servings.matches("(0|[1-9][0-9]{0,14})(\\.[0-9]{1,3})?")
                || new BigDecimal(model.servings).signum() <= 0)
              throw new IllegalArgumentException();
            model.preview();
          } catch (IllegalArgumentException ex) {
            notice("请输入大于零、最多三位小数的份数");
          }
        });
    LinearLayout allocation = card(binding.civaSecondary, "食材分配与库存检查");
    if (model.preview == null)
      label(allocation, "先读取服务端预览，再确认实际使用的食材。AC 库存服务和 EF 事务协调器未接入时会显示明确错误。", 15);
    else {
      for (CookingAllocation value : model.preview.getAllocations())
        label(
            allocation,
            "批次 "
                + value.getBatchId().toString().substring(0, 8)
                + " · "
                + value.getQuantity()
                + " "
                + value.getUnit(),
            15);
      for (MatchLine shortage : model.preview.getShortages())
        label(allocation, "食材 " + shortage.getIngredientId() + "：" + shortage.getStatus(), 15);
      if (model.preview.getAllocations().isEmpty()) label(allocation, "本次无需扣减已跟踪库存。", 14);
      button(
          allocation,
          "确认做饭并扣库存",
          !model.busy
              && model.pending == null
              && model.saved == null
              && model.preview.getShortages().isEmpty(),
          model::confirm);
    }
    if (model.pending != null) {
      button(allocation, "核对后使用原请求重试", !model.busy, model::retryPending);
      button(allocation, "移除本地待确认标记", !model.busy, () -> abandon(model::abandon));
    }
    if (model.saved != null) {
      label(allocation, "已确认做饭，未自动增加个人食记或热量。", 16);
      button(
          allocation,
          "前往记录本人用餐",
          true,
          () -> host.navigate(Route.MEAL_EDIT, java.time.LocalDate.now().toString()));
    }
  }
}
