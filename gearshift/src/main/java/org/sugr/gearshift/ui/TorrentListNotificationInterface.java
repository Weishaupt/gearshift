package org.sugr.gearshift.ui;

import android.database.Cursor;

public interface TorrentListNotificationInterface {
    public void notifyTorrentListChanged(
        Cursor cursor, int error, boolean added, boolean removed,
        boolean statusChanged, boolean metadataNeededa, boolean connected);
}
