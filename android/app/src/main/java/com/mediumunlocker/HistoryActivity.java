package com.inulute.mediumunlocker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private HistoryManager historyManager;
    private ListView listView;
    private TextView emptyView;
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
        listView = findViewById(R.id.historyListView);
        emptyView = findViewById(R.id.emptyView);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                showingHistory = tab.getPosition() == 0;
                refreshList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            List<HistoryManager.HistoryItem> items = getCurrentList();
            if (position < items.size()) openItem(items.get(position));
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            List<HistoryManager.HistoryItem> items = getCurrentList();
            if (position < items.size()) showItemOptions(items.get(position));
            return true;
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
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(showingHistory ? "No history yet.\nUnlock an article to get started." : "No bookmarks yet.\nBookmark articles while reading.");
        } else {
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            listView.setAdapter(new HistoryAdapter(items));
        }
    }

    private void openItem(HistoryManager.HistoryItem item) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("url", item.freediumUrl.isEmpty() ? item.originalUrl : item.freediumUrl);
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
        if (id == R.id.action_export) {
            exportData();
            return true;
        } else if (id == R.id.action_import) {
            importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
            return true;
        } else if (id == R.id.action_clear) {
            showClearDialog();
            return true;
        }
        return false;
    }

    private void exportData() {
        String json = historyManager.exportToJson();
        if (json == null) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
            return;
        }
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
                            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            historyManager.clearBookmarks();
                            Toast.makeText(this, "Bookmarks cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            historyManager.clearHistory();
                            historyManager.clearBookmarks();
                            Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== Adapter ====================

    private class HistoryAdapter extends BaseAdapter {
        private final List<HistoryManager.HistoryItem> items;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        HistoryAdapter(List<HistoryManager.HistoryItem> items) {
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int pos) { return items.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_history, parent, false);
            }
            HistoryManager.HistoryItem item = items.get(position);

            TextView titleView = convertView.findViewById(R.id.itemTitle);
            TextView metaView = convertView.findViewById(R.id.itemMeta);
            View bookmarkIndicator = convertView.findViewById(R.id.bookmarkIndicator);

            String title = item.title.isEmpty() ? "Article" : item.title;
            titleView.setText(title);

            String date = item.timestamp > 0 ? dateFormat.format(new Date(item.timestamp)) : "";
            String domain = extractDomain(item.originalUrl);
            metaView.setText(domain.isEmpty() ? date : (date.isEmpty() ? domain : domain + " · " + date));

            if (bookmarkIndicator != null) {
                bookmarkIndicator.setVisibility(
                        showingHistory && historyManager.isBookmarked(item.originalUrl)
                                ? View.VISIBLE : View.GONE);
            }

            return convertView;
        }

        private String extractDomain(String url) {
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host == null) return "";
                return host.startsWith("www.") ? host.substring(4) : host;
            } catch (Exception e) {
                return "";
            }
        }
    }
}
