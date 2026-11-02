/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups V2, 2026-11-01. See LICENSE and NOTICE.
 */
package app.morphe.extension.youtube.groups;

import static app.morphe.extension.shared.StringRef.str;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import app.morphe.extension.shared.Utils;

/** Full-window native, recycled lists hosted in YouTube. Small dialogs are only editors/confirmations. */
@SuppressWarnings("deprecation")
public final class GroupsManagerDialog extends DialogFragment {
    private static final String TAG = "subscription_groups_manager_v2";
    private static final int IMPORT = 4208, EXPORT = 4209;
    public static void pick(Activity activity) { open(activity, "", true); }
    public static void show(Activity activity, String channel) {
        open(activity, channel, false);
    }
    private static void open(Activity activity, String channel, boolean selection) {
        if (activity == null || activity.isFinishing()) return;
        android.app.FragmentManager manager = activity.getFragmentManager();
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        GroupsManagerDialog fragment = new GroupsManagerDialog(); Bundle args = new Bundle();
        args.putString("channel", channel == null ? "" : channel); args.putBoolean("selection", selection);
        fragment.setArguments(args); fragment.show(manager, TAG);
    }
    private GroupService service;
    private RecentFeedSource source;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GroupService.Listener observer = this::render;
    private String mode = "groups", groupId = "", query = "", assignment = "", transfer = "";
    private boolean move, busy, membershipsOnly, assignmentStandalone, assignmentInitialized;
    private final Set<String> chosen = new LinkedHashSet<>(), edited = new HashSet<>();
    private GroupLibraryCodec.Import candidate;
    private LinearLayout root, footer;
    private TextView title, status, empty, add;
    private EditText search;
    private ListView list;
    private final Rows adapter = new Rows();
    private List<Object> rows = new ArrayList<>();
    private final Runnable searchUpdate = this::render;
    private String namesRequested = "";
    private boolean closeWhenStarted;
    private android.window.OnBackInvokedCallback predictiveBack;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setRetainInstance(true);
        service = GroupService.get(getActivity()); source = RecentFeedSource.get(getActivity()); service.observe(observer);
        if (state != null) {
            mode = state.getString("mode", "groups"); groupId = state.getString("group", ""); query = state.getString("query", "");
            assignment = state.getString("assignment", ""); transfer = state.getString("transfer", "");
            move = state.getBoolean("move"); membershipsOnly = state.getBoolean("membershipsOnly");
            assignmentStandalone = state.getBoolean("standalone");
            ArrayList<String> selected = state.getStringArrayList("chosen"), scope = state.getStringArrayList("edited");
            if (selected != null) chosen.addAll(selected); if (scope != null) edited.addAll(scope);
            assignmentInitialized = state.getBoolean("assignmentInitialized");
            if (mode.equals("import")) mode = "groups"; // An unconfirmed preview is intentionally not restored after process death.
        } else {
            assignment = getArguments().getString("channel", "");
            if (!assignment.isEmpty()) { mode = "assign"; assignmentStandalone = true; }
            else if (getArguments().getBoolean("selection")) mode = "select";
        }
    }
    @Override public Dialog onCreateDialog(Bundle state) {
        Activity activity = getActivity(); Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
        root = GroupUi.vertical(activity); root.setFitsSystemWindows(true);
        LinearLayout bar = new LinearLayout(activity); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = GroupUi.button(activity, "‹", false, this::back); back.setContentDescription(str("morphe_pockettube_v2_back"));
        bar.addView(back, new LinearLayout.LayoutParams(GroupUi.dp(activity, 48), -2));
        title = GroupUi.text(activity, "", 20); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setMaxLines(1); title.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        add = GroupUi.button(activity, "+", false, this::addAction); bar.addView(add);
        TextView menu = GroupUi.button(activity, "⋮", false, () -> menu(bar)); menu.setContentDescription(str("morphe_pockettube_v2_more")); bar.addView(menu);
        root.addView(bar);
        search = new EditText(activity); search.setSingleLine(true); search.setTextColor(GroupUi.foreground()); search.setHintTextColor(GroupUi.muted());
        search.setPadding(GroupUi.dp(activity, 16), GroupUi.dp(activity, 8), GroupUi.dp(activity, 16), GroupUi.dp(activity, 8));
        search.setText(query); root.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString(); main.removeCallbacks(searchUpdate); main.postDelayed(searchUpdate, 150);
            }
            public void afterTextChanged(Editable s) { }
        });
        status = GroupUi.text(activity, "", 13); status.setTextColor(GroupUi.muted());
        status.setPadding(GroupUi.dp(activity, 16), GroupUi.dp(activity, 4), GroupUi.dp(activity, 16), GroupUi.dp(activity, 4));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(status);
        FrameLayout body = new FrameLayout(activity); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        list = new ListView(activity); list.setDividerHeight(0); list.setAdapter(adapter); body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = GroupUi.text(activity, "", 16); empty.setGravity(Gravity.CENTER); empty.setPadding(24, 24, 24, 24);
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1)); list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, position, id) -> rowClick(rows.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> { actions(view, rows.get(position)); return true; });
        footer = new LinearLayout(activity); root.addView(footer); dialog.setContentView(root);
        dialog.setOnKeyListener((d, key, event) -> {
            if (key == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) { back(); return true; } return key == KeyEvent.KEYCODE_BACK;
        });
        render(); return dialog;
    }
    @Override public void onStart() {
        super.onStart();
        if (closeWhenStarted) { closeWhenStarted = false; dismissAllowingStateLoss(); return; }
        if (Build.VERSION.SDK_INT >= 33 && getDialog() != null && predictiveBack == null) {
            predictiveBack = this::back;
            getDialog().getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, predictiveBack);
        }
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(-1, -1);
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        render();
    }
    private void clearQuery() { query = ""; if (search != null) search.setText(""); }
    private void render() {
        if (root == null || !isAdded()) return;
        GroupLibrary data = service.library(); GroupLibrary.Entry selected = data.find(groupId);
        if ((mode.equals("channels") || mode.equals("target")) && selected == null) { mode = "groups"; groupId = ""; }
        if (mode.equals("assign") && service.ready() && !assignmentInitialized) {
            for (GroupLibrary.Entry group : data.groups) { edited.add(group.id); if (group.channels.contains(assignment)) chosen.add(group.id); }
            assignmentInitialized = true;
        }
        rows = new ArrayList<>(); String needle = query.toLowerCase(Locale.ROOT);
        if (mode.equals("options")) {
            for (String key : GroupOptions.ALL) if (GroupOptions.title(key).toLowerCase(Locale.ROOT).contains(needle)) rows.add(key);
        } else if (mode.equals("channels") && selected != null) {
            for (String id : selected.channels) if ((source.channelName(id) + " " + id).toLowerCase(Locale.ROOT).contains(needle)) rows.add(id);
            if (!namesRequested.equals(groupId)) {
                namesRequested = groupId;
                source.cachedNames(selected.channels, () -> main.post(() -> { if (root != null) render(); }));
            }
        } else {
            List<GroupLibrary.Entry> groups = candidate != null && mode.equals("import") ? candidate.library.groups : data.ordered(false);
            for (GroupLibrary.Entry group : groups) if (group.name.toLowerCase(Locale.ROOT).contains(needle)
                    && (!mode.equals("target") || !group.id.equals(groupId))) rows.add(group);
        }
        title.setText(mode.equals("channels") && selected != null ? selected.name : str("morphe_pockettube_v2_" + mode + "_title"));
        search.setHint(str(mode.equals("channels") ? "morphe_pockettube_v2_search_channels" : "morphe_pockettube_v2_search_groups"));
        String info = !service.ready() ? (service.error().isEmpty() ? str("morphe_pockettube_working") : service.error())
                : str("morphe_pockettube_v2_library_count", data.groups.size(), data.memberships);
        if (mode.equals("channels") && selected != null) info = str("morphe_pockettube_v2_channel_count", selected.channels.size());
        if (mode.equals("assign")) info = source.channelName(assignment) + " · " + assignment;
        if (mode.equals("target")) info = str(move ? "morphe_pockettube_v2_move_channel" : "morphe_pockettube_v2_copy_channel");
        if (mode.equals("import") && candidate != null) info = str("morphe_pockettube_v2_import_preview", candidate.library.groups.size(), candidate.library.memberships, candidate.duplicates);
        status.setText(busy ? str("morphe_pockettube_working") : info);
        empty.setText(str(mode.equals("channels") ? "morphe_pockettube_v2_no_channels" : "morphe_pockettube_v2_no_groups"));
        add.setVisibility(mode.equals("groups") || mode.equals("channels") ? View.VISIBLE : View.GONE);
        add.setContentDescription(str(mode.equals("channels") ? "morphe_pockettube_v2_add_channel" : "morphe_pockettube_v2_create"));
        add.setEnabled(service.ready() && !busy); list.setEnabled((service.ready() || mode.equals("import")) && !busy);
        footer.removeAllViews();
        if (mode.equals("import") && candidate != null) {
            footerButton("morphe_pockettube_v2_merge", () -> restore(true));
            footerButton("morphe_pockettube_replace", () -> restore(false));
            footerButton("morphe_pockettube_discard", this::back);
        } else if (mode.equals("assign")) {
            footerButton("morphe_pockettube_v2_save", () -> {
                Set<String> selectedIds = new HashSet<>(chosen), scope = new HashSet<>(edited); String id = assignment;
                service.change(previous -> previous.assign(id, selectedIds, scope), () -> {
                    if (assignmentStandalone) {
                        if (isResumed()) dismissAllowingStateLoss(); else closeWhenStarted = true;
                    } else { mode = groupId.isEmpty() ? "groups" : "channels"; clearQuery(); render(); }
                });
            });
        }
        adapter.notifyDataSetChanged();
    }
    private void footerButton(String key, Runnable action) {
        TextView button = GroupUi.button(getActivity(), str(key), false, action);
        button.setEnabled(!busy && (!key.equals("morphe_pockettube_v2_merge") || service.ready()));
        footer.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void rowClick(Object value) {
        if (busy) return;
        if (value instanceof GroupLibrary.Entry) {
            GroupLibrary.Entry group = (GroupLibrary.Entry) value;
            if (mode.equals("import")) return;
            if (mode.equals("select")) { GroupsSubscriptions.choose(group.id); dismiss(); return; }
            if (mode.equals("assign")) { if (!chosen.remove(group.id)) chosen.add(group.id); adapter.notifyDataSetChanged(); return; }
            if (mode.equals("target")) {
                String from = groupId, channel = transfer; boolean shouldMove = move;
                service.change(previous -> previous.transfer(from, group.id, channel, shouldMove), () -> { mode = "channels"; clearQuery(); render(); }); return;
            }
            groupId = group.id; mode = "channels"; clearQuery(); render();
        } else if (mode.equals("options")) GroupOptions.choose(getActivity(), service, (String) value);
        else if (mode.equals("channels")) { GroupUi.channel(getActivity(), (String) value); dismiss(); }
    }
    private void addAction() {
        if (mode.equals("channels")) editText("morphe_pockettube_v2_add_channel", "", input -> {
            String channel = input.trim();
            if (channel.startsWith("https://www.youtube.com/channel/")) channel = channel.substring("https://www.youtube.com/channel/".length()).split("[/?#]", 2)[0];
            String id = channel, group = groupId; service.change(previous -> previous.membership(group, id, true));
        });
        else editText("morphe_pockettube_v2_create", "", name -> service.change(previous -> previous.create(name)));
    }
    private void actions(View anchor, Object value) {
        if (mode.equals("import") || mode.equals("target") || mode.equals("assign") || mode.equals("select") || mode.equals("options") || busy) return;
        PopupMenu menu = new PopupMenu(getActivity(), anchor);
        if (value instanceof GroupLibrary.Entry) {
            GroupLibrary.Entry group = (GroupLibrary.Entry) value;
            menu.getMenu().add(0, 1, 0, str("morphe_pockettube_v2_rename"));
            menu.getMenu().add(0, 2, 1, str("morphe_pockettube_v2_icon"));
            menu.getMenu().add(0, 3, 2, str(group.pinned ? "morphe_pockettube_v2_unpin" : "morphe_pockettube_v2_pin"));
            menu.getMenu().add(0, 4, 3, str("morphe_pockettube_v2_move_up")); menu.getMenu().add(0, 5, 4, str("morphe_pockettube_v2_move_down"));
            menu.getMenu().add(0, 6, 5, str("morphe_pockettube_v2_delete"));
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: editText("morphe_pockettube_v2_rename", group.name, name -> service.change(previous -> {
                        GroupLibrary.Entry current = previous.find(group.id); if (current == null) return previous;
                        return previous.edit(group.id, name, current.icon, current.pinned);
                    })); break;
                    case 2: editText("morphe_pockettube_v2_icon", group.icon, icon -> service.change(previous -> {
                        GroupLibrary.Entry current = previous.find(group.id); if (current == null) return previous;
                        return previous.edit(group.id, current.name, icon, current.pinned);
                    })); break;
                    case 3: service.change(previous -> { GroupLibrary.Entry current = previous.find(group.id); return current == null ? previous : previous.edit(group.id, current.name, current.icon, !current.pinned); }); break;
                    case 4: service.change(previous -> previous.move(group.id, -1)); break;
                    case 5: service.change(previous -> previous.move(group.id, 1)); break;
                    case 6: new AlertDialog.Builder(getActivity()).setTitle(str("morphe_pockettube_v2_delete"))
                            .setMessage(str("morphe_pockettube_v2_delete_confirm", group.name))
                            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(str("morphe_pockettube_v2_delete"), (d, w) -> service.change(previous -> previous.delete(group.id))).show(); break;
                }
                return true;
            });
        } else {
            String channel = (String) value, group = groupId;
            menu.getMenu().add(0, 1, 0, str("morphe_pockettube_v2_assign")); menu.getMenu().add(0, 2, 1, str("morphe_pockettube_v2_copy_channel"));
            menu.getMenu().add(0, 3, 2, str("morphe_pockettube_v2_move_channel")); menu.getMenu().add(0, 4, 3, str("morphe_pockettube_v2_remove_channel"));
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) { assignment = channel; mode = "assign"; assignmentInitialized = assignmentStandalone = false; chosen.clear(); edited.clear(); }
                if (item.getItemId() == 2 || item.getItemId() == 3) { transfer = channel; mode = "target"; move = item.getItemId() == 3; }
                if (item.getItemId() == 4) service.change(previous -> previous.membership(group, channel, false));
                clearQuery(); render(); return true;
            });
        }
        menu.show();
    }
    private void menu(View anchor) {
        if (busy) return; PopupMenu menu = new PopupMenu(getActivity(), anchor);
        String[] labels = {"morphe_pockettube_import", "morphe_pockettube_v2_export", "morphe_pockettube_v2_export_memberships",
                "morphe_pockettube_v2_preferences", "morphe_pockettube_v2_about_feed", "morphe_pockettube_v2_reload"};
        for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, str(labels[i]));
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: pickImport(); break;
                case 1: pickExport(false); break;
                case 2: pickExport(true); break;
                case 3: mode = "options"; clearQuery(); render(); break;
                case 4: GroupOptions.aboutFeed(getActivity()); break;
                case 5: service.reload(); break;
            }
            return true;
        }); menu.show();
    }
    private void editText(String titleKey, String initial, Consumer<String> save) {
        EditText edit = new EditText(getActivity()); edit.setSingleLine(true); edit.setText(initial); edit.setTextColor(GroupUi.foreground());
        edit.setPadding(32, 16, 32, 16);
        new AlertDialog.Builder(getActivity()).setTitle(str(titleKey)).setView(edit)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(str("morphe_pockettube_v2_save"), (d, w) -> save.accept(edit.getText().toString())).show();
    }
    private void pickImport() {
        try { busy = true; render(); startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT); }
        catch (RuntimeException failure) { busy = false; render(); Utils.showToastLong(str("morphe_pockettube_picker_failed")); }
    }
    private void pickExport(boolean onlyMemberships) {
        if (!service.ready()) return;
        try {
            membershipsOnly = onlyMemberships; busy = true; render();
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, onlyMemberships ? "subscription-group-memberships.json" : "subscription-groups-v2-backup.json"), EXPORT);
        } catch (RuntimeException failure) { busy = false; render(); Utils.showToastLong(str("morphe_pockettube_picker_failed")); }
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMPORT && request != EXPORT) return; busy = false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) { render(); return; }
        busy = true; render();
        if (request == IMPORT) service.preview(data.getData(), preview -> {
            busy = false;
            if (preview != null) { candidate = preview; mode = "import"; clearQuery(); } render();
        });
        else service.export(data.getData(), membershipsOnly, success -> {
            busy = false; render();
            if (success && isAdded()) Utils.showToastLong(str("morphe_pockettube_v2_exported"));
        });
    }
    private void restore(boolean merge) {
        if (candidate == null || busy) return;
        // Replacement is destructive locally; require a separate explicit confirmation.
        if (!merge) {
            new AlertDialog.Builder(getActivity()).setTitle(str("morphe_pockettube_replace"))
                    .setMessage(str("morphe_pockettube_v2_replace_confirm"))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(str("morphe_pockettube_replace"), (d, w) -> commitImport(false)).show();
        } else commitImport(true);
    }
    private void commitImport(boolean merge) {
        busy = true; render(); service.restore(candidate, merge, success -> {
            busy = false;
            if (success) { candidate = null; mode = "groups"; groupId = ""; clearQuery(); } render();
        });
    }
    private void back() {
        if (mode.equals("groups") || mode.equals("select") || (mode.equals("assign") && assignmentStandalone)) { dismiss(); return; }
        if (mode.equals("target") || (mode.equals("assign") && !groupId.isEmpty())) mode = "channels"; else mode = "groups";
        candidate = null; clearQuery(); render();
    }
    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("mode", mode); out.putString("group", groupId); out.putString("query", query);
        out.putString("assignment", assignment); out.putString("transfer", transfer); out.putBoolean("move", move);
        out.putBoolean("membershipsOnly", membershipsOnly); out.putBoolean("standalone", assignmentStandalone);
        out.putBoolean("assignmentInitialized", assignmentInitialized); out.putStringArrayList("chosen", new ArrayList<>(chosen)); out.putStringArrayList("edited", new ArrayList<>(edited));
    }
    @Override public void onDestroyView() {
        if (Build.VERSION.SDK_INT >= 33 && predictiveBack != null && getDialog() != null)
            getDialog().getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(predictiveBack);
        predictiveBack = null;
        if (getDialog() != null && getRetainInstance()) getDialog().setDismissMessage(null);
        root = footer = null; title = status = empty = add = null; search = null; list = null; super.onDestroyView();
    }
    @Override public void onDestroy() { service.remove(observer); main.removeCallbacksAndMessages(null); super.onDestroy(); }

    private final class Rows extends BaseAdapter {
        public int getCount() { return rows.size(); }
        public Object getItem(int position) { return rows.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View recycled, ViewGroup parent) {
            Row row;
            if (recycled == null) { row = new Row(); recycled = row.layout; recycled.setTag(row); } else row = (Row) recycled.getTag();
            Object item = rows.get(position); boolean group = item instanceof GroupLibrary.Entry;
            row.check.setVisibility(mode.equals("assign") ? View.VISIBLE : View.GONE);
            row.toggle.setVisibility(mode.equals("options") && GroupPreferences.TOGGLES.contains(item) ? View.VISIBLE : View.GONE);
            row.actions.setVisibility(!mode.equals("assign") && !mode.equals("select") && !mode.equals("target") && !mode.equals("import") && !mode.equals("options") ? View.VISIBLE : View.GONE);
            if (group) {
                GroupLibrary.Entry entry = (GroupLibrary.Entry) item;
                row.name.setText(entry.name); row.details.setText(str("morphe_pockettube_v2_group_details", entry.channels.size(), entry.pinned ? str("morphe_pockettube_v2_pinned") : ""));
                row.icon.setText(entry.icon.isEmpty() ? GroupUi.initials(entry.name) : entry.icon); row.check.setChecked(chosen.contains(entry.id));
                row.actions.setContentDescription(str("morphe_pockettube_v2_actions_for", entry.name));
            } else if (mode.equals("options")) {
                String key = (String) item; row.name.setText(GroupOptions.title(key)); row.details.setText(GroupOptions.summary(service.library(), key));
                row.icon.setText("⚙"); row.toggle.setChecked(service.library().preferences.enabled(key));
            } else {
                String channel = (String) item; row.name.setText(source.channelName(channel)); row.details.setText(channel);
                row.icon.setText(source.channelName(channel).equals(channel) ? "◉" : GroupUi.initials(source.channelName(channel)));
                row.actions.setContentDescription(str("morphe_pockettube_v2_actions_for", channel));
            }
            row.actions.setOnClickListener(v -> actions(v, item)); return recycled;
        }
    }
    private final class Row {
        final LinearLayout layout = new LinearLayout(getActivity());
        final TextView icon = GroupUi.text(getActivity(), "", 24), name = GroupUi.text(getActivity(), "", 16), details = GroupUi.text(getActivity(), "", 12);
        final TextView actions = GroupUi.button(getActivity(), "⋮", false, () -> {});
        final CheckBox check = new CheckBox(getActivity()); final Switch toggle = new Switch(getActivity());
        Row() {
            layout.setGravity(Gravity.CENTER_VERTICAL); layout.setPadding(GroupUi.dp(getActivity(), 12), GroupUi.dp(getActivity(), 8), GroupUi.dp(getActivity(), 8), GroupUi.dp(getActivity(), 8));
            icon.setGravity(Gravity.CENTER); layout.addView(icon, new LinearLayout.LayoutParams(GroupUi.dp(getActivity(), 44), GroupUi.dp(getActivity(), 48)));
            LinearLayout labels = GroupUi.vertical(getActivity()); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END); details.setMaxLines(2); details.setTextColor(GroupUi.muted());
            labels.addView(name); labels.addView(details); layout.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            actions.setFocusable(false); layout.addView(actions); check.setClickable(false); check.setFocusable(false); toggle.setClickable(false); toggle.setFocusable(false);
            layout.addView(check); layout.addView(toggle);
        }
    }
}
