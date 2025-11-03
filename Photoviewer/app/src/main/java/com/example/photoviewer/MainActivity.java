package com.example.photoviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;                      // onClick에 필요
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;         // ★ 추가
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    TextView textView;
    String site_url = "https://seungyun.pythonanywhere.com";
    CloadImage taskDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
    }

    // ----------------------------- [1] 이미지 다운로드 -----------------------------
    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(site_url + "/api/posts/");
        Toast.makeText(getApplicationContext(), "Download started", Toast.LENGTH_SHORT).show();
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                String apiUrl = urls[0];
                String token = "3d560b814c2bebf33cc6704356883724174f6b61";

                URL urlAPI = new URL(apiUrl);
                conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                Log.d("HTTP", "Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    is.close();

                    Log.d("HTTP", "JSON Response: " + result);

                    JSONArray aryJson = new JSONArray(result.toString());
                    for (int i = 0; i < aryJson.length(); i++) {
                        JSONObject post = aryJson.getJSONObject(i);
                        String imageUrl = post.getString("image");

                        // 상대경로면 절대경로로
                        if (!imageUrl.startsWith("http")) {
                            imageUrl = site_url + imageUrl;
                        }

                        Log.d("HTTP", "Image URL: " + imageUrl);

                        HttpURLConnection imgConn = (HttpURLConnection) new URL(imageUrl).openConnection();
                        imgConn.setConnectTimeout(3000);
                        imgConn.setReadTimeout(3000);
                        InputStream imgStream = imgConn.getInputStream();
                        Bitmap bitmap = BitmapFactory.decodeStream(imgStream);
                        if (bitmap != null) bitmapList.add(bitmap);
                        imgStream.close();
                        imgConn.disconnect();
                    }
                } else {
                    Log.e("HTTP", "Server responded with code: " + responseCode);
                }
            } catch (IOException | JSONException e) {
                Log.e("HTTP", "Error: " + e.getMessage(), e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            return bitmapList;
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            if (images.isEmpty()) {
                textView.setText("불러올 이미지가 없습니다.");
            } else {
                textView.setText("이미지 로드 성공!");
                RecyclerView recyclerView = findViewById(R.id.recyclerView);
                ImageAdapter adapter = new ImageAdapter(images);
                recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                recyclerView.setAdapter(adapter);
            }
        }
    }

    // ----------------------------- [2] 이미지 업로드 -----------------------------
    // 레이아웃의 android:onClick="onClickUpload" 와 매칭되는 '메서드'
    public void onClickUpload(View v) {
        new UploadImageTask().execute(site_url + "/api/posts/");
        Toast.makeText(getApplicationContext(), "Upload started", Toast.LENGTH_SHORT).show();
    }

    // 실제 업로드 수행하는 AsyncTask
    private class UploadImageTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            String apiUrl = urls[0];
            String token = "3d560b814c2bebf33cc6704356883724174f6b61";
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

            HttpURLConnection conn = null;
            DataOutputStream dos = null;

            try {
                // ✅ res/drawable 이미지 불러오기 (파일명: res/drawable/test_upload.jpg)
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.test_upload);
                if (bitmap == null) {
                    Log.e("UPLOAD", "리소스 이미지 로드 실패 (R.drawable.test_upload) — 파일명/경로 확인");
                    return false;
                }

                // ✅ 메모리에 JPEG로 변환
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                byte[] imageData = baos.toByteArray();
                baos.close();

                // ✅ 연결
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                dos = new DataOutputStream(conn.getOutputStream());

                // ✅ multipart/form-data 시작
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"test_upload.jpg\"\r\n");
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");

                // ✅ 바이너리 전송
                dos.write(imageData);
                dos.writeBytes("\r\n--" + boundary + "--\r\n");
                dos.flush();

                int code = conn.getResponseCode();
                Log.d("UPLOAD", "Response Code: " + code);

                // ✅ 응답 바디 로깅 (성공/실패 모두)
                InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
                    Log.d("UPLOAD", "Response Body:\n" + sb);
                    br.close();
                }

                return (code == HttpURLConnection.HTTP_CREATED || code == HttpURLConnection.HTTP_OK);

            } catch (Exception e) {
                Log.e("UPLOAD", "Error: " + e.getMessage(), e);
                return false;
            } finally {
                try { if (dos != null) dos.close(); } catch (Exception ignore) {}
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Toast.makeText(getApplicationContext(), "업로드 성공!", Toast.LENGTH_LONG).show();
                textView.setText("업로드 완료 ✅");
                onClickDownload(null); // 업로드 후 자동 새로고침
            } else {
                Toast.makeText(getApplicationContext(), "업로드 실패 ❌ (Logcat 확인)", Toast.LENGTH_LONG).show();
            }
        }
    }
    public void onClickGoUpload(View v) {
        Intent intent = new Intent(MainActivity.this, UploadActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 메인 복귀 시 자동 새로고침
        onClickDownload(null);
    }
}
