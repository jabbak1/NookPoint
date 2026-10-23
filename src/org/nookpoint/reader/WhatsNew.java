package org.nookpoint.reader;

import android.app.Activity;

/** Shows what changed once after an update. */
class WhatsNew {
    static void maybeShow(Activity a) {
        String v = Diag.version();
        if (v == null || v.equals("?")) return;
        String seen = Prefs.get("seen_version", null);
        Prefs.put("seen_version", v);
        if (v.equals(seen)) return;
        if (seen == null && Prefs.all().size() < 4) return;          // fresh install: nothing to explain yet
        Dlg.message(a, T.f("What's new in {0}", v), T.t("WHATS_NEW_BODY"), T.t("Close"), null, null, null);
    }
}
