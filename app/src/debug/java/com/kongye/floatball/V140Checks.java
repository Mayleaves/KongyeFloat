package com.kongye.floatball;
import android.content.*;import android.view.*;import org.json.*;
/** Only coordinates of our own ball and orbit; never screen pixels. */
final class V140Checks {
 static void run(Context c,String command)throws Exception{
  if(command.equals("manage")){MainActivity page=V133Checks.top() instanceof MainActivity m?m:null;if(page==null)throw new IllegalStateException("Main not visible");V126Checks.watch(page);page.startActivity(new Intent(page,QuickSettingsActivity.class));return;}
  JSONObject result=new JSONObject();FloatService service=FloatService.instance;
  if(service!=null){View ball=(View)V126Checks.get(service,"ball");if(ball!=null)result.put("ball",box(ball));Object dock=V126Checks.get(service,"quickDock");View root=(View)V126Checks.get(dock,"root");if(command.equals("manage_state")){android.app.Activity page=V126Checks.page;if(page instanceof QuickSettingsActivity){ViewGroup content=page.findViewById(android.R.id.content);root=content.getChildAt(0);}}
   if(root!=null){result.put("root",box(root));JSONArray slots=new JSONArray();for(int i=0;i<13;i++)slots.put(box(((ViewGroup)root).getChildAt(i)));result.put("slots",slots);}
  }
  Prefs.of(c).edit().putString("qa_v140_state",result.toString()).commit();
 }
 static JSONObject box(View view)throws JSONException{int[] at=new int[2];view.getLocationOnScreen(at);return new JSONObject().put("x",at[0]).put("y",at[1]).put("width",view.getWidth()).put("height",view.getHeight()).put("cx",at[0]+view.getWidth()/2f).put("cy",at[1]+view.getHeight()/2f);}
}
