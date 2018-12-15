package com.example.jcyds.servicedownload;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadTask extends AsyncTask<String,Integer,Integer> {

    public static final int TYPE_SUCCESS=0;
    public static final int TYPE_FAILED=1;
    public static final int TYPE_PAUSED=2;
    public static final int TYPE_CANNELED=3;

    private DownloadListener listener;
    private boolean isCanneled = false;
    private boolean isPaused = false;
    private int lastProgress;

    public DownloadTask(DownloadListener listener){
        this.listener = listener;
    }

    @Override
    protected Integer doInBackground(String... strings) {
        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;
        try{
//            下载文件长度
            long downloadLength = 0;
            String downloadUrl = strings[0];
//            获取文件名
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
//            获取文件下载目录
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
//            新文件命名
            file = new File(directory+fileName);
//            若文件已存在，则记录已存在的文件长度
            if (file.exists()){
                downloadLength = file.length();
            }
//            发送一个网络请求获取文件总长度
            long contentLength = getContentLength(downloadUrl);
            if (contentLength==0){
                return TYPE_FAILED;
            }else if (contentLength==downloadLength){
                return TYPE_SUCCESS;
            }

//            发送下载请求
            OkHttpClient client = new OkHttpClient();
//            构建一个头部带有已下载长度的请求
            Request request = new Request.Builder()
                    .addHeader("RANGE","bytes="+downloadLength+"-")
                    .url(downloadUrl)
                    .build();
            Response response = client.newCall(request).execute();
            if (response!=null){
//                输入流获取为相应体的字节流
                is = response.body().byteStream();
//                给已保存部分继续写入
                savedFile = new RandomAccessFile(file,"rw");
//                跳过已下载字节
                savedFile.seek(downloadLength);
                byte[] b = new byte[1024];
                int total =0;
                int len;
                while ((len=is.read(b))!= -1){
                    if (isCanneled){
                        return TYPE_CANNELED;
                    }else if (isPaused){
                        return TYPE_PAUSED;
                    }else {
                        total+=len;
                        savedFile.write(b,0,len);
//                        计算已下载比例
                        int progress = (int)((total+downloadLength)*100/contentLength);
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try{
                if (is!=null){
                    is.close();
                }
                if (savedFile!=null){
                    savedFile.close();
                }
                if (isCanneled&&file!=null){
                    file.delete();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return TYPE_FAILED;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];
        if (progress>lastProgress){
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    @Override
    protected void onPostExecute(Integer integer) {
        switch (integer){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            case TYPE_CANNELED:
                listener.onCanneled();
                break;
            default:
                break;
        }
    }

    public void pauseDownload(){
        isPaused = true;
    }

    public void cannelDownload(){
        isCanneled = true;
    }

    //    获取文件总长度的方法
    private long getContentLength(String downloadUrl) throws IOException{
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(downloadUrl)
                .build();
        Response response = client.newCall(request).execute();
        if (response!=null&&response.isSuccessful()){
            long contentLength = response.body().contentLength();
            response.body().close();
            return contentLength;
        }
        return 0;
    }

}
