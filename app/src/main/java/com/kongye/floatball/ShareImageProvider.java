package com.kongye.floatball;
import android.content.*;
import android.database.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** Grant-only, read-only PNGs in private cache. Sharing never inserts a MediaStore row. */
public class ShareImageProvider extends ContentProvider {
    static Uri prepare(Context c,Bitmap image)throws IOException{
        File folder=new File(c.getCacheDir(),"shared-images");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cache unavailable");
        File[] files=folder.listFiles();long cutoff=System.currentTimeMillis()-24L*60*60*1000;if(files!=null)for(File f:files)if(f.isFile()&&f.getName().matches("[a-f0-9-]{36}\\.(png|mp4|gif)")&&f.lastModified()<cutoff)f.delete();
        File output=new File(folder,UUID.randomUUID()+".png");try(OutputStream out=new FileOutputStream(output)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG encoding failed");}catch(IOException e){output.delete();throw e;}
        Uri uri=new Uri.Builder().scheme("content").authority("com.kongye.floatball.share").appendPath(output.getName()).build();
        Prefs.of(c).edit().putString("last_share_uri",uri.toString()).putBoolean("last_share_private_cache",true).apply();return uri;
    }
    static Uri prepareMedia(Context c,File source)throws IOException{String ext=source.getName().endsWith(".gif")?"gif":"mp4";File dir=new File(c.getCacheDir(),"shared-images");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cache unavailable");File dest=new File(dir,UUID.randomUUID()+"."+ext);try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(dest)){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)out.write(block,0,n);}catch(IOException e){dest.delete();throw e;}Uri uri=new Uri.Builder().scheme("content").authority("com.kongye.floatball.share").appendPath(dest.getName()).build();Prefs.of(c).edit().putString("last_share_uri",uri.toString()).putBoolean("last_share_private_cache",true).apply();return uri;}
    private File file(Uri uri)throws FileNotFoundException{String name=uri.getLastPathSegment();if(!"com.kongye.floatball.share".equals(uri.getAuthority())||uri.getPathSegments().size()!=1||name==null||!name.matches("[a-f0-9-]{36}\\.(png|mp4|gif)"))throw new FileNotFoundException();File folder=new File(getContext().getCacheDir(),"shared-images"),f=new File(folder,name);try{if(!f.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile()))throw new FileNotFoundException();}catch(IOException e){throw new FileNotFoundException();}return f;}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){String name=uri.getLastPathSegment();return name!=null&&name.endsWith(".mp4")?"video/mp4":name!=null&&name.endsWith(".gif")?"image/gif":"image/png";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?"Kongye-share."+(f.getName().endsWith(".mp4")?"mp4":f.getName().endsWith(".gif")?"gif":"png"):OpenableColumns.SIZE.equals(columns[i])?f.length():null;cursor.addRow(row);return cursor;}catch(FileNotFoundException e){return null;}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
