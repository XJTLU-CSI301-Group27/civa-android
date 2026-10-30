package edu.xjtlu.civa;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PlaceholderFragment extends Fragment {
    private static final String TITLE = "title";
    private static final String ENTITY_ID = "entityId";

    public PlaceholderFragment() { super(R.layout.app_fragment_placeholder); }

    static PlaceholderFragment create(int title, String entityId) {
        PlaceholderFragment fragment = new PlaceholderFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(TITLE, title);
        arguments.putString(ENTITY_ID, entityId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((TextView) view.findViewById(R.id.app_title)).setText(requireArguments().getInt(TITLE));
    }
}
