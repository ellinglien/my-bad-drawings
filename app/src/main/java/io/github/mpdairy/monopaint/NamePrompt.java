package io.github.mpdairy.monopaint;

import android.app.AlertDialog;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The name prompt, for a new notebook and a rename ({@link NotebookNames}): a dialog as the app's others are, its field
 * holding the name with all of it selected and the keyboard up, so typing replaces it; OK and Cancel. A name refused is said by the field ("Name taken") and the prompt stays.
 */
final class NamePrompt {
    private NamePrompt() {}

    /** Shows the prompt titled {@code title}; {@code chosen} gets the name taken (as stored), once, unless Cancel. */
    static AlertDialog show(PaintActivity app, String title, NotebookNames names, Predicate<String> taken, Consumer<String> chosen) {
        LinearLayout column = LabUi.column(app);
        column.setPadding(app.dp(24), app.dp(8), app.dp(24), app.dp(8));
        EditText field = Theme.style(new EditText(app), TypeStyle.BODY);
        field.setSingleLine(); field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setBackgroundColor(Theme.PAPER); field.setPadding(app.dp(12), app.dp(10), app.dp(12), app.dp(10));
        field.setMinHeight(app.dp(48));
        field.setText(names.field());
        column.addView(field, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(app).setTitle(title).setView(column)
                .setPositiveButton(NotebookNames.OK, null).setNegativeButton("Cancel", null).create();
        // The keyboard up with the prompt, the dialog above it.
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show(); app.compactDialog(dialog);
        Consumer<String> take = name -> {
            String refused = names.refused(name, taken);
            if (refused != null) { field.setError(refused); return; }
            dialog.dismiss();
            chosen.accept(name);
        };
        // Set after show(): a button given a listener beforehand always dismisses, losing the typed name on an error.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> take.accept(names.stored(field.getText().toString())));
        field.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            take.accept(names.stored(field.getText().toString())); return true;
        });
        // compactDialog may move the content into a turned frame: focus and select once it is in place.
        InputMethodManager keyboard = (InputMethodManager) app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        field.requestFocus(); field.selectAll();
        field.post(() -> {
            field.requestFocus(); field.selectAll();
            if (keyboard != null) keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        });
        // The keyboard goes with the prompt, whichever way it closes.
        dialog.setOnDismissListener(d -> { if (keyboard != null) keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0); });
        return dialog;
    }
}
