package edu.xjtlu.civa.feature.bd;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import edu.xjtlu.civa.feature.bd.databinding.BdFragmentWorkspaceBinding;
import java.time.LocalDate;
import java.util.function.Consumer;

/** Small feature-local composition helpers, all colors and controls inherit the EF theme. */
public abstract class BdScreen extends Fragment {
  protected BdFragmentWorkspaceBinding binding;
  protected BdHost host;

  public BdScreen() {
    super(R.layout.bd_fragment_workspace);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    host = (BdHost) requireActivity();
    binding = BdFragmentWorkspaceBinding.bind(view);
    if (getResources().getConfiguration().screenWidthDp >= 600) {
      binding.bdPanes.setOrientation(LinearLayout.HORIZONTAL);
      binding.bdPrimary.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
      binding.bdSecondary.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
      binding.bdSecondary.setPadding(dp(16), 0, 0, 0);
    }
    start(state);
  }

  protected abstract void start(Bundle state);

  protected void title(String title, String subtitle, String message) {
    binding.bdTitle.setText(title);
    binding.bdSubtitle.setText(subtitle);
    binding.bdStatus.setText(message);
    binding.bdActions.removeAllViews();
    binding.bdPrimary.removeAllViews();
    binding.bdSecondary.removeAllViews();
  }

  protected LinearLayout column() {
    LinearLayout view = new LinearLayout(requireContext());
    view.setOrientation(LinearLayout.VERTICAL);
    view.setPadding(dp(12), dp(8), dp(12), dp(8));
    return view;
  }

  protected LinearLayout card(LinearLayout parent, String title) {
    MaterialCardView card = new MaterialCardView(requireContext());
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.setMargins(0, dp(6), 0, dp(6));
    parent.addView(card, params);
    LinearLayout content = column();
    card.addView(content);
    label(content, title, 18);
    return content;
  }

  protected TextView label(LinearLayout parent, String text, int size) {
    TextView view = new TextView(requireContext());
    view.setText(text);
    view.setTextSize(size);
    view.setPadding(0, dp(5), 0, dp(5));
    parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
    return view;
  }

  protected MaterialButton button(
      LinearLayout parent, String text, boolean enabled, Runnable action) {
    MaterialButton button = new MaterialButton(requireContext());
    button.setText(text);
    button.setEnabled(enabled);
    parent.addView(button, new LinearLayout.LayoutParams(-2, -2));
    button.setOnClickListener(v -> action.run());
    return button;
  }

  protected EditText input(LinearLayout parent, String caption, String value, int type) {
    label(parent, caption, 14);
    EditText input = new EditText(requireContext());
    input.setText(value);
    input.setHint(caption);
    input.setInputType(type);
    parent.addView(input, new LinearLayout.LayoutParams(-1, -2));
    return input;
  }

  protected Spinner spinner(LinearLayout parent, String caption, String[] values, int selected) {
    label(parent, caption, 14);
    Spinner spinner = new Spinner(requireContext());
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, values);
    spinner.setAdapter(adapter);
    spinner.setSelection(selected);
    parent.addView(spinner, new LinearLayout.LayoutParams(-1, -2));
    return spinner;
  }

  protected void pickDate(LocalDate current, Consumer<LocalDate> selected) {
    new DatePickerDialog(
            requireContext(),
            (picker, y, m, d) -> selected.accept(LocalDate.of(y, m + 1, d)),
            current.getYear(),
            current.getMonthValue() - 1,
            current.getDayOfMonth())
        .show();
  }

  protected AlertDialog form(
      String title, LinearLayout content, String confirm, Consumer<AlertDialog> save) {
    ScrollView scroll = new ScrollView(requireContext());
    scroll.addView(content);
    AlertDialog dialog =
        new AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton("取消", null)
            .setPositiveButton(confirm, null)
            .create();
    dialog.setOnShowListener(
        ignored ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> save.accept(dialog)));
    dialog.show();
    return dialog;
  }

  protected void notice(String text) {
    new AlertDialog.Builder(requireContext())
        .setMessage(text)
        .setPositiveButton("知道了", null)
        .show();
  }

  protected void abandon(Runnable action) {
    new AlertDialog.Builder(requireContext())
        .setMessage("先刷新核对是否已保存。移除本地待确认标记不会撤销服务端操作，确认继续？")
        .setNegativeButton("取消", null)
        .setPositiveButton("已核对，移除标记", (d, w) -> action.run())
        .show();
  }

  protected int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
