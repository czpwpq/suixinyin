package org.nfcresearch.qpolllab.activation;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Handoff endpoint protected by INTERACT_ACROSS_USERS_FULL plus UID and one-time nonce. */
public final class InternalBridgeProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        int uid=Binder.getCallingUid();ActivationProof.requireSender(uid);
        try{if(InternalBackend.METHOD_DELIVER.equals(method))return InternalBackend.deliver(getContext(),extras,uid);if(InternalBackend.METHOD_FAILURE.equals(method))return InternalBackend.failure(extras,uid);throw new IllegalArgumentException("Unsupported internal activation operation");}
        catch(RuntimeException error){throw error;}catch(Exception error){throw new IllegalStateException("内置服务身份验证失败："+error.getMessage(),error);}
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
