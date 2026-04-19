package com.inulute.mediumunlocker;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private HistoryManager historyManager;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private HistoryAdapter adapter;
    private boolean showingHistory = true;

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importFromUri(uri); }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyManager = HistoryManager.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.historyToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.history_menu);
        toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.historyListView);
        emptyView = findViewById(R.id.emptyView);

        TextInputEditText searchInput = findViewById(R.id.searchInput);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (adapter != null) adapter.filter(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);

        new ItemTouchHelper(new SwipeToDeleteCallback()).attachToRecyclerView(recyclerView);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                showingHistory = tab.getPosition() == 0;
                if (searchInput != null) searchInput.setText("");
                refreshList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private List<HistoryManager.HistoryItem> getCurrentList() {
        return showingHistory ? historyManager.getHistory() : historyManager.getBookmarks();
    }

    private void refreshList() {
        List<HistoryManager.HistoryItem> items = getCurrentList();
        if (items.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(showingHistory
                    ? "No history yet.\nUnlock an article to get started."
                    : "No bookmarks yet.\nBookmark articles while reading.");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter.setItems(items);
        }
    }

    private void openItem(HistoryManager.HistoryItem item) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("url", "https://freedium-mirror.cfd/" + item.originalUrl);
        intent.putExtra("originalUrl", item.originalUrl);
        startActivity(intent);
    }

    private void showItemOptions(HistoryManager.HistoryItem item) {
        String title = item.title.isEmpty() ? "Article" : item.title;
        String displayTitle = title.length() > 50 ? title.substring(0, 50) + "…" : title;
        boolean isBookmarked = historyManager.isBookmarked(item.originalUrl);

        String[] options = showingHistory
                ? new String[]{"Open Article", isBookmarked ? "Remove Bookmark" : "Add Bookmark", "Delete from History"}
                : new String[]{"Open Article", "Remove Bookmark"};

        new AlertDialog.Builder(this)
                .setTitle(displayTitle)
                .setItems(options, (dialog, which) -> {
                    if (showingHistory) {
                        switch (which) {
                            case 0: openItem(item); break;
                            case 1:
                                if (isBookmarked) {
                                    historyManager.removeBookmark(item.originalUrl);
                                    Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
                                } else {
                                    historyManager.addBookmark(item.title, item.originalUrl, item.freediumUrl);
                                    Toast.makeText(this, "Bookmarked!", Toast.LENGTH_SHORT).show();
                                }
                                refreshList();
                                break;
                            case 2:
                                historyManager.removeFromHistory(item.originalUrl);
                                refreshList();
                                break;
                        }
                    } else {
                        switch (which) {
                            case 0: openItem(item); break;
                            case 1:
                                historyManager.removeBookmark(item.originalUrl);
                                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
                                refreshList();
                                break;
                        }
                    }
                })
                .show();
    }

    private boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_export) { exportData(); return true; }
        else if (id == R.id.action_import) {
            importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
            return true;
        } else if (id == R.id.action_clear) { showClearDialog(); return true; }
        return false;
    }

    private void exportData() {
        String json = historyManager.exportToJson();
        if (json == null) { Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show(); return; }
        try {
            File file = new File(getCacheDir(), "medium_unlocker_history.json");
            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.close();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export History & Bookmarks"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importFromUri(Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getContentResolver().openInputStream(uri)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            reader.close();
            if (historyManager.importFromJson(sb.toString())) {
                Toast.makeText(this, "Import successful!", Toast.LENGTH_SHORT).show();
                refreshList();
            } else {
                Toast.makeText(this, "Invalid file format", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showClearDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Data")
                .setItems(new String[]{"Clear History", "Clear Bookmarks", "Clear All"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            historyManager.clearHistory();
                            historyManager.clearPositions();
                            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            historyManager.clearBookmarks();
                            Toast.makeText(this, "Bookmarks cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            historyManager.clearHistory();
                            historyManager.clearBookmarks();
                            historyManager.clearPositions();
                            Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== RecyclerView Adapter ====================

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<HistoryManager.HistoryItem> fullList = new ArrayList<>();
        List<HistoryManager.HistoryItem> displayList = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        private String currentFilter = "";

        void setItems(List<HistoryManager.HistoryItem> items) {
            fullList = new ArrayList<>(items);
            applyFilter(currentFilter);
        }

        void filter(String query) {
            currentFilter = query;
            applyFilter(query);
        }

        private void applyFilter(String query) {
            if (query == null || query.trim().isEmpty()) {
                displayList = new ArrayList<>(fullList);
            } else {
                String lower = query.toLowerCase(Locale.getDefault());
                displayList = new ArrayList<>();
                for (HistoryManager.HistoryItem item : fullList) {
                    if (item.title.toLowerCase(Locale.getDefault()).contains(lower)
                            || item.originalUrl.toLowerCase(Locale.getDefault()).contains(lower)) {
                        displayList.add(item);
                    }
                }
            }
            notifyDataSetChanged();
            emptyView.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(displayList.isEmpty() ? View.GONE : View.VISIBLE);
            if (displayList.isEmpty() && !currentFilter.isEmpty()) {
                emptyView.setText("No results for \"" + currentFilter + "\"");
            }
        }

        void removeItem(int position) {
            if (position < 0 || position >= displayList.size()) return;
            HistoryManager.HistoryItem removed = displayList.remove(position);
            // Also remove from fullList
            for (int i = 0; i < fullList.size(); i++) {
                if (fullList.get(i).originalUrl.equals(removed.originalUrl)) {
                    fullList.remove(i);
                    break;
                }
            }
            notifyItemRemoved(position);
            if (displayList.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                emptyView.setText(showingHistory ? "No history yet." : "No bookmarks yet.");
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryManager.HistoryItem item = displayList.get(position);
            String title = item.title.isEmpty() ? "Article" : item.title;
            holder.titleView.setText(title);

            String date = item.timestamp > 0 ? dateFormat.format(new Date(item.timestamp)) : "";
            String domain = extractDomain(item.originalUrl);
            holder.metaView.setText(domain.isEmpty() ? date : (date.isEmpty() ? domain : domain + " · " + date));

            if (holder.bookmarkIndicator != null) {
                holder.bookmarkIndicator.setVisibility(
                        showingHistory && historyManager.isBookmarked(item.originalUrl)
                                ? View.VISIBLE : View.GONE);
            }

            holder.itemView.setOnClickListener(v -> openItem(item));
            holder.itemView.setOnLongClickListener(v -> { showItemOptions(item); return true; });
        }

        @Override
        public int getItemCount() { return displayList.size(); }

        private String extractDomain(String url) {
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host == null) return "";
                return host.startsWith("www.") ? host.substring(4) : host;
            } catch (Exception e) { return ""; }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView titleView;
            TextView metaView;
            View bookmarkIndicator;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                titleView = itemView.findViewById(R.id.itemTitle);
                metaView = itemView.findViewById(R.id.itemMeta);
                bookmarkIndicator = itemView.findViewById(R.id.bookmarkIndicator);
            }
        }
    }

    // ==================== Swipe to Delete ====================

    private class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {
        private final Paint paint = new Paint();
        private final int deleteColor = Color.parseColor("#EF4444");

        SwipeToDeleteCallback() {
            super(0, ItemTouchHelper.LEFT);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv,
                              @NonNull RecyclerView.ViewHolder vh,
                              @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getAdapterPosition();
            if (position < 0 || position >= adapter.displayList.size()) return;
            HistoryManager.HistoryItem item = adapter.displayList.get(position);
            if (showingHistory) {
                historyManager.removeFromHistory(item.originalUrl);
            } else {
                historyManager.removeBookmark(item.originalUrl);
            }
            adapter.removeItem(position);
        }

        @Override
        public void onChildDraw(@NonNull Canvas c,
                                @NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder,
                                float dX, float dY,
                                int actionState, boolean isCurrentlyActive) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                View itemView = viewHolder.itemView;
                float swipeWidth = Math.abs(dX);
                int margin = (int) (16 * recyclerView.getResources().getDisplayMetrics().density);
                float cornerRadius = 20 * recyclerView.getResources().getDisplayMetrics().density;

                if (dX < 0) {
                    paint.setColor(deleteColor);
                    RectF background = new RectF(
                            itemView.getLeft() + margin + dX,
                            itemView.getTop(),
                            itemView.getRight() - margin,
                            itemView.getBottom()
                    );
                    c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

                    if (swipeWidth > 120) {
                        paint.setColor(Color.WHITE);
                        paint.setTextSize(36f);
                        paint.setTextAlign(Paint.Align.RIGHT);
                        float textY = itemView.getTop() + (itemView.getHeight() / 2f) + 12f;
                        c.drawText("Delete", itemView.getRight() - margin - 24f, textY, paint);
                    }
                }
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }
}
