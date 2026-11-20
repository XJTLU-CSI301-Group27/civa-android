package edu.xjtlu.civa.core.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import edu.xjtlu.civa.core.ui.databinding.CivaFragmentScreenBinding;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Shared screen scaffold: title, subtitle, action bar, status line and a one- or two-pane body.
 *
 * <p>This is the single place that decides spacing, cards, forms, dialogs and the wide-window
 * layout, so every feature screen looks the same and no feature grows its own copy. Colors and
 * typography still come from the shared theme; nothing here declares a second visual system.
 *
 * @param <H> the host type provided by the app shell (for example {@code AppServices})
 */
public abstract class CivaScreen<H> extends Fragment {
    protected CivaFragmentScreenBinding binding;
    protected H host;

    protected CivaScreen() {
        super(R.layout.civa_fragment_screen);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        //noinspection unchecked
        host = (H) requireActivity();
        binding = CivaFragmentScreenBinding.bind(view);
        if (getResources().getConfiguration().screenWidthDp >= 600) {
            binding.civaPanes.setOrientation(LinearLayout.HORIZONTAL);
            binding.civaPrimary.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            binding.civaSecondary.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            binding.civaSecondary.setPadding(dp(16), 0, 0, 0);
        }
        start(state);
    }

    protected abstract void start(Bundle state);

    protected void title(String title, String subtitle, String message) {
        binding.civaTitle.setText(title);
        binding.civaSubtitle.setText(subtitle);
        binding.civaStatus.setText(message);
        binding.civaActions.removeAllViews();
        binding.civaPrimary.removeAllViews();
        binding.civaSecondary.removeAllViews();
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
