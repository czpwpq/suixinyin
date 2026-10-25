package org.nfcresearch.qpolllab;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Shared document picker policy. Selection is a union, never ClipData OR getData. */
public final class ImagePicker {
    private ImagePicker() { }
    public static Intent request(boolean multiple) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }
    public static ArrayList<Uri> collect(Intent data,int maximum) {
        if(maximum<0)throw new IllegalArgumentException("可选图片数量无效");
        LinkedHashSet<Uri> values=new LinkedHashSet<>();
        if(data!=null){ClipData clip=data.getClipData();if(clip!=null)for(int i=0;i<clip.getItemCount();i++)add(values,clip.getItemAt(i).getUri());add(values,data.getData());}
        if(values.size()>maximum)throw new IllegalArgumentException("本次最多还能添加 "+maximum+" 张图片，请减少选择数量");
        return new ArrayList<>(values);
    }
    /** Run on a worker thread: verify actual image headers without allocating a decoded bitmap. */
    public static ArrayList<Uri> collect(ContentResolver resolver,Intent data,int maximum) throws IOException {
        if(resolver==null)throw new IllegalArgumentException("图片读取服务不可用");
        ArrayList<Uri> values=collect(data,maximum);
        for(Uri uri:values){
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            try(InputStream input=resolver.openInputStream(uri)){
                if(input==null)throw new IOException("无法打开所选图片");
                BitmapFactory.decodeStream(input,null,bounds);
            }
            if(bounds.outWidth<1||bounds.outHeight<1)throw new IOException("所选内容不是可识别的图片，请选择 PNG、JPEG 或 WebP 等格式");
        }
        return values;
    }
    private static void add(LinkedHashSet<Uri> values,Uri uri) {
        if(uri==null)return;
        if(!"content".equalsIgnoreCase(uri.getScheme()))throw new IllegalArgumentException("请通过系统照片或文件选择器选取图片");
        values.add(uri);
    }
}
