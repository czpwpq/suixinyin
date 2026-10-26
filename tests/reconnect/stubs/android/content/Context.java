package android.content;
import java.util.HashMap;
import java.util.Map;
/** Persistent storage boundary only; a new helper can reopen the same saved preferences. */
public final class Context {
    public static final int MODE_PRIVATE=0;
    private final Map<String,MemoryPreferences> files=new HashMap<>();
    public SharedPreferences getSharedPreferences(String name,int mode){
        MemoryPreferences result=files.get(name);
        if(result==null){result=new MemoryPreferences();files.put(name,result);}
        return result;
    }
    private static final class MemoryPreferences implements SharedPreferences {
        private final Map<String,Object> values=new HashMap<>();
        public boolean getBoolean(String key,boolean fallback){Object value=values.get(key);return value instanceof Boolean?(Boolean)value:fallback;}
        public int getInt(String key,int fallback){Object value=values.get(key);return value instanceof Integer?(Integer)value:fallback;}
        public String getString(String key,String fallback){Object value=values.get(key);return value instanceof String?(String)value:fallback;}
        public Editor edit(){return new Editor(){
            private final Map<String,Object> changed=new HashMap<>();
            public Editor putBoolean(String key,boolean value){changed.put(key,value);return this;}
            public Editor putInt(String key,int value){changed.put(key,value);return this;}
            public Editor putString(String key,String value){changed.put(key,value);return this;}
            public void apply(){values.putAll(changed);}
        };}
    }
}
