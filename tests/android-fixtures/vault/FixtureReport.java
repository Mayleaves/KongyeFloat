package com.kongye.fixture;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.Bundle;
/** Only booleans leave the generated login fixture. */
public class FixtureReport extends ContentProvider {
    public boolean onCreate(){return true;}
    public Bundle call(String method,String arg,Bundle args){Bundle b=new Bundle();FixtureLogin page=FixtureLogin.current;if(method.equals("change")&&page!=null)page.runOnUiThread(()->((android.widget.TextView)page.findViewById(page.getResources().getIdentifier("url_bar","id",page.getPackageName()))).setText("https://other.example.net/signin"));b.putBoolean("account",page!=null&&"fixture-user".contentEquals(page.account.getText()));b.putBoolean("password",page!=null&&"Fixture!2053".contentEquals(page.password.getText()));return b;}
    public Cursor query(Uri u,String[] p,String s,String[] a,String sort){return null;}public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){return null;}public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
