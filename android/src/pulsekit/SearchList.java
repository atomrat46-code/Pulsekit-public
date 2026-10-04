package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * A list dialog with a search box above it (StyleDb.search: "rock" shows Rock, Hard Rock, Blues
 * Rock...). With a button label it is a single-choice list confirmed by that button (Style
 * database); without one, tapping a name picks it (Params' Choose). Indices are into the full list.
 */
final class SearchList {
    interface Pick {
        void picked(int index);
    }

    private SearchList() {}

    /** `marked` is underlined (the current style), `initial` checked; -1 for neither. */
    static AlertDialog show(Activity activity, String title, final String[] items, final int marked, int initial, String button, final Pick pick) {
        final boolean single = button != null;
        final List<String> shown = new ArrayList<String>();
        final List<Integer> map = new ArrayList<Integer>();
        for (int i = 0; i < items.length; i++) {
            shown.add(items[i]);
            map.add(Integer.valueOf(i));
        }
        final int[] chosen = {initial >= 0 ? initial : 0};
        final ArrayAdapter<String> rows = new ArrayAdapter<String>(activity,
            single ? android.R.layout.select_dialog_singlechoice : android.R.layout.select_dialog_item, android.R.id.text1, shown) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                TextView tv = (TextView) view.findViewById(android.R.id.text1);
                if (tv != null) {
                    int flags = tv.getPaintFlags();
                    if (position < map.size() && map.get(position).intValue() == marked) flags |= android.graphics.Paint.UNDERLINE_TEXT_FLAG;
                    else flags &= ~android.graphics.Paint.UNDERLINE_TEXT_FLAG;
                    tv.setPaintFlags(flags);
                }
                return view;
            }
        };
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * density);
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(pad, pad, pad, Math.round(4 * density));
        TextView t = new TextView(activity);
        t.setText(title);
        t.setTextSize(20);
        head.addView(t);
        final EditText search = new EditText(activity);
        search.setHint("Search");
        search.setSingleLine(true);
        search.setTag("list-search");
        head.addView(search);
        AlertDialog.Builder b = new AlertDialog.Builder(activity).setCustomTitle(head);
        if (single) {
            b.setSingleChoiceItems(rows, chosen[0], new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which >= 0 && which < map.size()) chosen[0] = map.get(which).intValue();
                }
            });
            b.setPositiveButton(button, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    pick.picked(chosen[0]);
                }
            });
        } else {
            b.setAdapter(rows, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which >= 0 && which < map.size()) pick.picked(map.get(which).intValue());
                }
            });
        }
        b.setNegativeButton("Cancel", null);
        final AlertDialog dialog = b.create();
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                int[] found = StyleDb.search(items, s.toString());
                shown.clear();
                map.clear();
                for (int i : found) {
                    shown.add(items[i]);
                    map.add(Integer.valueOf(i));
                }
                rows.notifyDataSetChanged();
                ListView list = dialog.getListView();
                if (list == null) return;
                list.clearChoices();
                int at = map.indexOf(Integer.valueOf(chosen[0]));
                if (single && at >= 0) list.setItemChecked(at, true);
                list.setSelection(0);
            }
        });
        final int first = initial;
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                ListView list = dialog.getListView();
                if (list != null && first >= 0) list.setSelection(first);
            }
        });
        dialog.show();
        return dialog;
    }
}
