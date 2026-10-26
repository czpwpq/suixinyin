package android.content;
public interface SharedPreferences {
    boolean getBoolean(String key,boolean fallback);
    int getInt(String key,int fallback);
    String getString(String key,String fallback);
    Editor edit();
    interface Editor {
        Editor putBoolean(String key,boolean value);
        Editor putInt(String key,int value);
        Editor putString(String key,String value);
        void apply();
    }
}
