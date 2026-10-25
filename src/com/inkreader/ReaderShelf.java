package com.inkreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Embedded book shelf for the main Reading tab. Book data stays in the app's private storage. */
public final class ReaderShelf {
    private static final int REQUEST_IMPORT=1801;
    public final LinearLayout view;
    private final Activity activity;private final ReaderDb db;private final ListView list;
    private final View empty;private final BookAdapter adapter=new BookAdapter();
    private final List<AlertDialog> dialogs=new ArrayList<>();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile boolean closed,importing;private String sort;
    public ReaderShelf(Activity activity){
        this.activity=activity;Ui.migrateCompactDefaults(activity);db=new ReaderDb(activity);
        sort=Ui.prefs(activity).getString("bookshelf-sort",ReaderDb.SORT_RECENT);
        view=column();view.setBackgroundColor(Ui.PAPER);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=Ui.title(activity,"本地书架",22,Ui.INK);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button ordering=button("排序",false);ordering.setOnClickListener(v->chooseSort());header.addView(ordering);
        Button settings=button("阅读设置",false);settings.setOnClickListener(v->activity.startActivity(new Intent(activity,SettingsActivity.class)));LinearLayout.LayoutParams settingParams=new LinearLayout.LayoutParams(-2,-2);settingParams.leftMargin=dp(6);header.addView(settings,settingParams);view.addView(header);
        TextView subtitle=Ui.text(activity,12,Ui.MUTED,false);subtitle.setText("TXT · 本地章节、书签与阅读进度");subtitle.setPadding(0,dp(5),0,dp(12));view.addView(subtitle);
        FrameLayout content=new FrameLayout(activity);list=new ListView(activity);list.setDivider(null);list.setDividerHeight(0);list.setClipToPadding(false);list.setPadding(0,dp(2),0,dp(10));list.setAdapter(adapter);content.addView(list,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout blank=column();blank.setGravity(Gravity.CENTER);TextView symbol=Ui.title(activity,"▤",52,Ui.INK);blank.addView(symbol);blank.addView(Ui.title(activity,"添加你的第一本书",20,Ui.INK));TextView help=Ui.text(activity,13,Ui.MUTED,false);help.setText("支持 UTF-8、UTF-16 和 GB18030\n单本最多32 MiB · 支持多选导入");help.setGravity(Gravity.CENTER);help.setPadding(0,dp(10),0,0);blank.addView(help);empty=blank;content.addView(blank,new FrameLayout.LayoutParams(-1,-1));view.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        Button add=button("＋ 导入 TXT",true);add.setTextSize(15);add.setOnClickListener(v->pickFiles());LinearLayout.LayoutParams addParams=new LinearLayout.LayoutParams(-1,dp(48));addParams.setMargins(0,dp(7),0,dp(10));view.addView(add,addParams);
        list.setOnItemClickListener((p,v,index,id)->{if(!closed&&!importing)activity.startActivity(new Intent(activity,ReaderActivity.class).putExtra("bookId",adapter.books.get(index).id));});
        list.setOnItemLongClickListener((p,v,index,id)->{if(!closed&&!importing)confirmDelete(adapter.books.get(index));return true;});reload();
    }
    private int dp(float value){return Ui.dp(activity,value);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private Button button(String label,boolean primary){Button b=Ui.button(activity,label);b.setTextColor(primary?Ui.PAPER_CARD:Ui.INK);android.graphics.drawable.GradientDrawable shape=Ui.round(activity,primary?Ui.INK:Ui.PAPER_CARD,10);if(!primary)shape.setStroke(dp(1),0xffe5e5e1);b.setBackground(shape);return b;}
    public boolean isImporting(){return importing;}
    public void onResume(){if(!closed&&!importing)reload();}
    private void reload(){if(closed)return;adapter.books.clear();adapter.books.addAll(db.listBooks(sort));adapter.notifyDataSetChanged();empty.setVisibility(adapter.books.isEmpty()?View.VISIBLE:View.GONE);list.setVisibility(adapter.books.isEmpty()?View.GONE:View.VISIBLE);}
    private void chooseSort(){if(closed||importing)return;AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("书架排序").setSingleChoiceItems(new String[]{"最近阅读","书名"},ReaderDb.SORT_TITLE.equals(sort)?1:0,(d,n)->{if(closed)return;sort=n==1?ReaderDb.SORT_TITLE:ReaderDb.SORT_RECENT;Ui.prefs(activity).edit().putString("bookshelf-sort",sort).apply();d.dismiss();reload();}).setNegativeButton("取消",null).create();dialogs.add(dialog);dialog.show();}
    private void confirmDelete(Book book){AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("删除《"+book.title+"》？").setMessage("删除本机书库中的文件、章节、书签和进度；原始导入文件不受影响。").setNegativeButton("取消",null).setPositiveButton("删除",(d,n)->{if(closed||importing)return;String path=db.deleteBook(book.id);if(path!=null)try{File file=new File(path),root=new File(activity.getFilesDir(),"books");if(file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)&&file.isFile()&&!file.delete())Toast.makeText(activity,"书籍已移除，本地文件清理失败",Toast.LENGTH_LONG).show();}catch(Exception error){Toast.makeText(activity,"书籍已移除，本地文件清理失败",Toast.LENGTH_LONG).show();}reload();}).create();dialogs.add(dialog);dialog.show();}
    private void pickFiles(){if(closed||importing)return;try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/*","application/octet-stream"});activity.startActivityForResult(intent,REQUEST_IMPORT);}catch(android.content.ActivityNotFoundException error){Toast.makeText(activity,"没有可用的文件选择器",Toast.LENGTH_LONG).show();}}
    public boolean onResult(int request,int result,Intent data){if(request!=REQUEST_IMPORT)return false;if(closed||importing||result!=Activity.RESULT_OK||data==null)return true;LinkedHashSet<Uri> uris=new LinkedHashSet<>();ClipData clip=data.getClipData();if(clip!=null)for(int i=0;i<clip.getItemCount()&&uris.size()<100;i++){Uri uri=clip.getItemAt(i).getUri();if(uri!=null)uris.add(uri);}else if(data.getData()!=null)uris.add(data.getData());if(!uris.isEmpty())importFiles(new ArrayList<>(uris));return true;}
    private void importFiles(List<Uri> uris){importing=true;cancelled.set(false);ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(true);bar.setProgressTintList(ColorStateList.valueOf(Ui.INK));TextView status=Ui.text(activity,14,Ui.INK,false);status.setText("准备导入 "+uris.size()+" 个文件");LinearLayout box=column();box.setPadding(dp(22),dp(8),dp(22),0);box.addView(status);box.addView(bar);AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("导入 TXT").setView(box).setNegativeButton("取消",(d,n)->cancelled.set(true)).setCancelable(false).create();dialogs.add(dialog);dialog.show();
        new Thread(()->{int success=0,duplicates=0,failed=0;StringBuilder summary=new StringBuilder();try{TxtImporter importer=new TxtImporter(activity,db);for(int i=0;i<uris.size();i++){if(cancelled.get()||closed)break;final int fileIndex=i+1;try{TxtImporter.Result result=importer.importUri(uris.get(i),new TxtImporter.Progress(){public void update(int percent,String message){activity.runOnUiThread(()->{if(closed)return;bar.setIndeterminate(percent<0);if(percent>=0)bar.setProgress(percent);status.setText(fileIndex+" / "+uris.size()+" · "+message);});}public boolean cancelled(){return cancelled.get()||closed;}});if(result.success)success++;else if(result.duplicate)duplicates++;summary.append(result.message).append('\n');}catch(InterruptedException stopped){summary.append("已取消剩余导入\n");break;}catch(Throwable error){failed++;summary.append("导入失败：").append(error.getMessage()).append('\n');}}}finally{importing=false;if(closed){db.close();return;}}final String result="成功 "+success+" 本"+(duplicates>0?" · 重复 "+duplicates+" 本":"")+(failed>0?" · 失败 "+failed+" 本":"")+"\n\n"+summary;activity.runOnUiThread(()->{if(closed)return;if(dialog.isShowing())dialog.dismiss();reload();AlertDialog report=new AlertDialog.Builder(activity).setTitle("导入完成").setMessage(result).setPositiveButton("知道了",null).create();dialogs.add(report);report.show();});},"reader-txt-import").start();
    }
    public void destroy(){if(closed)return;closed=true;cancelled.set(true);for(AlertDialog dialog:dialogs)if(dialog.isShowing())dialog.dismiss();dialogs.clear();if(!importing)db.close();}
    private final class BookAdapter extends BaseAdapter{
        final List<Book> books=new ArrayList<>();public int getCount(){return books.size();}public Book getItem(int n){return books.get(n);}public long getItemId(int n){return books.get(n).id;}
        public View getView(int position,View reusable,ViewGroup parent){LinearLayout outer=column();outer.setPadding(0,dp(4),0,dp(5));LinearLayout card=column();card.setPadding(dp(16),dp(13),dp(16),dp(13));android.graphics.drawable.GradientDrawable bg=Ui.round(activity,Ui.PAPER_CARD,14);bg.setStroke(dp(1),0xffe5e5e1);card.setBackground(bg);outer.addView(card,new LinearLayout.LayoutParams(-1,-2));Book book=books.get(position);TextView title=Ui.title(activity,book.title,18,Ui.INK);title.setMaxLines(2);card.addView(title);TextView meta=Ui.text(activity,12,Ui.MUTED,false);meta.setText(book.author+" · "+book.totalChapters+" 章 · "+book.progressText());card.addView(meta);ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress(Math.round(book.progress*1000));progress.setProgressTintList(ColorStateList.valueOf(Ui.INK));progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xffeeefec));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(3));lp.topMargin=dp(8);card.addView(progress,lp);return outer;}
    }
}
