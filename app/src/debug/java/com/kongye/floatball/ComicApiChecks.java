package com.kongye.floatball;
import java.util.*;
import org.json.*;
final class ComicApiChecks {
    static JSONObject run()throws Exception{
        int passed=0;
        String valid="[{\"id\":1,\"text\":\"第二句\"},{\"id\":0,\"text\":\"第一句\"}]";
        if(!ComicApi.parse(valid,2).equals(List.of("第一句","第二句")))throw new AssertionError("id order");passed++;
        if(ComicApi.parse("```json\n"+valid+"\n```",2).size()!=2)throw new AssertionError("fenced JSON");passed++;
        for(String invalid:new String[]{"[]","[{\"id\":0,\"text\":\"第一句\"}]","[{\"id\":0,\"text\":\"a\"},{\"id\":0,\"text\":\"b\"}]","[{\"id\":0.5,\"text\":\"a\"},{\"id\":1,\"text\":\"b\"}]","[{\"id\":0,\"text\":\" \"},{\"id\":1,\"text\":\"b\"}]","not JSON"}){
            boolean rejected=false;try{ComicApi.parse(invalid,2);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("incomplete reply accepted");passed++;
        }
        new ComicApiSettings.Config("https://example.com/v1","test-model","test-key-not-a-credential").validate();passed++;
        for(String endpoint:new String[]{"http://example.com/v1","https://user:password@example.com/v1","https://example.com/v1?key=bad"}){
            boolean rejected=false;try{new ComicApiSettings.Config(endpoint,"test-model","test-key-not-a-credential").validate();}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("invalid endpoint");passed++;
        }
        return new JSONObject().put("passed",passed).put("network_requests",0).put("phase","complete");
    }
}
