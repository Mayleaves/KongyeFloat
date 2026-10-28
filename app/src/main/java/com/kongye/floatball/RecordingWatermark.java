package com.kongye.floatball;

import android.graphics.*;
import org.json.*;

/** Source-relative text objects shared by the inline editor and the encoded video/GIF. */
final class RecordingWatermark {
    static JSONArray parse(String value){
        if(value==null||value.isBlank())return new JSONArray();
        try{return new JSONArray(value);}catch(JSONException ignored){}
        JSONArray result=new JSONArray();
        try{result.put(new JSONObject().put("text",value).put("x",.58).put("y",.95).put("size",.025).put("color",Color.WHITE));}catch(JSONException ignored){}
        return result;
    }
    static Bitmap render(String value,int width,int height){
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(3);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        JSONArray marks=parse(value);
        for(int i=0;i<marks.length();i++){JSONObject mark=marks.optJSONObject(i);if(mark==null)continue;
            paint.setColor(mark.optInt("color",Color.WHITE));paint.setTextSize(Math.max(1,(float)mark.optDouble("size",.025)*height));
            float x=(float)mark.optDouble("x",.5)*width,y=(float)mark.optDouble("y",.5)*height;
            for(String line:mark.optString("text","").split("\\n",-1)){canvas.drawText(line,x,y,paint);y+=paint.getFontSpacing();}
        }
        return bitmap;
    }
}
