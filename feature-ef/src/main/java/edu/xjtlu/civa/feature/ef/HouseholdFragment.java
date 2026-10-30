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
import edu.xjtlu.civa.feature.ef.databinding.EfFragmentHouseholdBinding;
import java.util.function.Consumer;

public final class HouseholdFragment extends Fragment {
    private HouseholdViewModel model;
    private EfFragmentHouseholdBinding binding;

    public HouseholdFragment() { super(R.layout.ef_fragment_household); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        HouseholdHost host = (HouseholdHost) requireActivity();
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                if (type != HouseholdViewModel.class) throw new IllegalArgumentException("Unknown ViewModel");
                return type.cast(new HouseholdViewModel(host.api(), host.calls(), host.authRepository(), host.sessions()));
            }
        }).get(HouseholdViewModel.class);
        binding = EfFragmentHouseholdBinding.bind(view);
        binding.efHouseholdName.setText(model.name);
        binding.efHouseholdCode.setText(model.code);
        binding.efHouseholdName.addTextChangedListener(watcher(value -> model.name = value));
        binding.efHouseholdCode.addTextChangedListener(watcher(value -> model.code = value));
        model.recover();
        binding.efCreate.setOnClickListener(ignored -> model.create());
        binding.efJoin.setOnClickListener(ignored -> model.join());
        binding.efAbandon.setOnClickListener(ignored -> model.abandon());
        model.recovering.observe(getViewLifecycleOwner(), value -> {
            boolean recovering = value != null && !value.isEmpty();
            binding.efAbandon.setVisibility(recovering ? View.VISIBLE : View.GONE);
            if (recovering) {
                binding.efHouseholdName.setText(model.name);
                binding.efHouseholdCode.setText(model.code);
                binding.efHouseholdMessage.setVisibility(View.VISIBLE);
                binding.efHouseholdMessage.setText(R.string.ef_recovered);
            }
        });
        model.loading.observe(getViewLifecycleOwner(), value -> {
            boolean busy = Boolean.TRUE.equals(value);
            binding.efCreate.setEnabled(!busy);
            binding.efJoin.setEnabled(!busy);
            binding.efCreate.setText(busy ? R.string.ef_busy : R.string.ef_create_household);
        });
        model.message.observe(getViewLifecycleOwner(), value -> {
            binding.efHouseholdMessage.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
            if (value != null && !value.isEmpty()) binding.efHouseholdMessage.setText(messageResource(value));
        });
        model.ready.observe(getViewLifecycleOwner(), value -> {
            if (Boolean.TRUE.equals(value) && isResumed()) host.onHouseholdReady();
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (model != null && Boolean.TRUE.equals(model.ready.getValue())) ((HouseholdHost) requireActivity()).onHouseholdReady();
    }

    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }

    private static int messageResource(String code) {
        return switch (code) {
            case "VALIDATION_ERROR" -> R.string.ef_invalid_input;
            case "ALREADY_IN_HOUSEHOLD" -> R.string.ef_already_in_household;
            case "INVALID_INVITATION" -> R.string.ef_invalid_invitation;
            case "LOCAL_STORAGE_ERROR" -> R.string.ef_generic_error;
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
