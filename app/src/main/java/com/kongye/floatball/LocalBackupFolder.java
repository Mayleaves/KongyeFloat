package com.kongye.floatball;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Manages ZIP files inside the document directory selected by the user. */
final class LocalBackupFolder {
    record Entry(String name, Uri uri, long size, boolean deletable) {}
    private final ContentResolver resolver;
    private final Uri tree, directory;

    LocalBackupFolder(Context context, Uri tree) throws IOException {
        resolver = context.getContentResolver();
        if (tree == null || !DocumentsContract.isTreeUri(tree) || resolver.getPersistedUriPermissions().stream()
                .noneMatch(grant -> tree.equals(grant.getUri()) && grant.isReadPermission() && grant.isWritePermission()))
            throw new IOException("请重新选择本地备份目录并授权");
        this.tree = tree;
        directory = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    List<Entry> list() throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS};
        List<Entry> entries = new ArrayList<>();
        try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
            if (cursor == null) throw new IOException("无法读取本地备份目录");
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (name == null || !name.startsWith("KongyeFloat_") || !name.endsWith(".zip")
                        || DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(3))) continue;
                Uri file = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                boolean deletable = (cursor.getInt(4) & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0;
                entries.add(new Entry(name, file, cursor.isNull(2) ? -1 : cursor.getLong(2), deletable));
            }
        }
        entries.sort(Comparator.comparing(Entry::name).reversed());
        return List.copyOf(entries);
    }

    void save(Context context, boolean notes) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("备份已取消");
        Uri file = DocumentsContract.createDocument(resolver, directory, "application/zip", BackupStore.filename());
        if (file == null) throw new IOException("无法在此目录创建备份文件");
        try {
            BackupStore.export(context, file, notes);
            if (Thread.currentThread().isInterrupted()) throw new IOException("备份已取消");
        }
        catch (IOException | RuntimeException failure) {
            try { DocumentsContract.deleteDocument(resolver, file); }
            catch (IOException | RuntimeException ignored) { }
            throw failure;
        }
    }

    void delete(Entry entry) throws IOException {
        if (!entry.deletable() || !DocumentsContract.deleteDocument(resolver, entry.uri()))
            throw new IOException("无法删除这个备份，请检查目录权限");
    }
}
