package com.kongye.floatball;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only completed, app-private APK files can be granted read access to the system installer. */
public final class UpdateProvider extends ContentProvider {
    static final String AUTHORITY="com.kongye.floatball.update";
    static Uri uri(File file){return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.getName()).build();}
    private File file(Uri uri)throws FileNotFoundException{
        String name=uri.getLastPathSegment();if(!AUTHORITY.equals(uri.getAuthority())||uri.getPathSegments().size()!=1||name==null||!name.matches("[a-f0-9-]{36}\\.apk"))throw new FileNotFoundException();File folder=new File(getContext().getCacheDir(),"updates"),file=new File(folder,name);
        try{if(!file.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile())||!file.isFile())throw new FileNotFoundException();}catch(IOException e){throw new FileNotFoundException();}return file;
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File file=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?"KongyeFloat-update.apk":OpenableColumns.SIZE.equals(columns[i])?file.length():null;cursor.addRow(values);return cursor;}catch(FileNotFoundException e){return null;}}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}@Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}@Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
