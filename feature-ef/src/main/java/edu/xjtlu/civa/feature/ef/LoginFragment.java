package edu.xjtlu.civa.feature.ef;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import edu.xjtlu.civa.feature.ef.databinding.EfFragmentLoginBinding;
import java.util.function.Consumer;

public final class LoginFragment extends Fragment {
    private LoginViewModel model;
    private EfFragmentLoginBinding binding;

    public LoginFragment() { super(R.layout.ef_fragment_login); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        LoginHost host = (LoginHost) requireActivity();
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                if (type != LoginViewModel.class) throw new IllegalArgumentException("Unknown ViewModel");
                return type.cast(new LoginViewModel(host.authRepository()));
            }
        }).get(LoginViewModel.class);
        binding = EfFragmentLoginBinding.bind(view);
        binding.efUsername.setText(model.username);
        binding.efPassword.setText(model.password);
        binding.efDisplayName.setText(model.displayName);
        binding.efUsername.addTextChangedListener(watcher(value -> model.username = value));
        binding.efPassword.addTextChangedListener(watcher(value -> model.password = value));
        binding.efDisplayName.addTextChangedListener(watcher(value -> model.displayName = value));
        binding.efSubmit.setOnClickListener(ignored -> model.submit());
        binding.efSwitch.setOnClickListener(ignored -> {
            if (model.isPending()) return;
            model.registerMode = !model.registerMode;
            model.message.setValue("");
            renderMode();
        });
        model.loading.observe(getViewLifecycleOwner(), value -> {
            boolean busy = Boolean.TRUE.equals(value);
            binding.efSubmit.setEnabled(!busy);
            binding.efSwitch.setEnabled(!busy);
            binding.efSubmit.setText(busy ? R.string.ef_busy : model.registerMode ? R.string.ef_register : R.string.ef_login);
        });
        model.message.observe(getViewLifecycleOwner(), value -> {
            renderMode();
            binding.efMessage.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
            if (value != null && !value.isEmpty()) binding.efMessage.setText(messageResource(value));
        });
        model.authenticated.observe(getViewLifecycleOwner(), value -> {
            if (Boolean.TRUE.equals(value) && isResumed()) host.onAuthenticated();
        });
        renderMode();
    }

    @Override public void onResume() {
        super.onResume();
        if (model != null && Boolean.TRUE.equals(model.authenticated.getValue())) ((LoginHost) requireActivity()).onAuthenticated();
    }

    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }

    private void renderMode() {
        binding.efNameLayout.setVisibility(model.registerMode ? View.VISIBLE : View.GONE);
        binding.efSwitch.setText(model.registerMode ? R.string.ef_to_login : R.string.ef_to_register);
        binding.efSubmit.setText(model.isPending() ? R.string.ef_busy : model.registerMode ? R.string.ef_register : R.string.ef_login);
    }

    private static int messageResource(String code) {
        return switch (code) {
            case "REGISTERED" -> R.string.ef_registered;
            case "INVALID_CREDENTIALS" -> R.string.ef_invalid_credentials;
            case "USERNAME_TAKEN" -> R.string.ef_username_taken;
            case "VALIDATION_ERROR" -> R.string.ef_invalid_input;
            case "RATE_LIMITED" -> R.string.ef_rate_limited;
            case "NETWORK_ERROR", "TIMEOUT" -> R.string.ef_network_error;
            default -> R.string.ef_generic_error;
        };
    }

    private static TextWatcher watcher(Consumer<String> change) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { change.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }
}
