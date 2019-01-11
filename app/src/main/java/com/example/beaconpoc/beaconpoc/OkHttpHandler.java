package com.example.beaconpoc.beaconpoc;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpHandler extends AsyncTask<String, Void, String> {

    OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON   = MediaType.parse("application/json; charset=utf-8");

    private Context mContext;

    public OkHttpHandler (Context context){
        mContext = context;
    }


    @Override
    protected String doInBackground(String... params) {

        Request.Builder builder = new Request.Builder();

        RequestBody body = RequestBody.create(JSON, params[1]);
        builder.url(params[0]).post(body);

        Request request = builder.build();

        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        Toast.makeText(mContext, "POST result:" + s, Toast.LENGTH_SHORT).show();
    }
}