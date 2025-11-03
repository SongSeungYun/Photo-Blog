package com.example.photoviewer;

import java.util.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import androidx.appcompat.app.AppCompatDelegate;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    TextView textView;
    Spinner spinnerSort;
    RecyclerView recyclerView;
    ImageAdapter adapter;

    String site_url = "https://seungyun.pythonanywhere.com";
    CloadImage taskDownload;

    List<PostItem> postList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ 다크모드 버튼
        Button btnTheme = findViewById(R.id.btnThemeToggle);
        btnTheme.setOnClickListener(v -> {
            int currentMode = AppCompatDelegate.getDefaultNightMode();

            if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                Toast.makeText(this, "☀️ 라이트 모드로 전환되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                Toast.makeText(this, "🌙 다크 모드로 전환되었습니다", Toast.LENGTH_SHORT).show();
            }
        });

        textView = findViewById(R.id.textView);
        spinnerSort = findViewById(R.id.spinnerSort);
        recyclerView = findViewById(R.id.recyclerView);
        Spinner spinnerFav = findViewById(R.id.spinnerFavorite); // ✅ 여기로 이동

        adapter = new ImageAdapter(MainActivity.this, postList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 🔹 정렬 스피너 동작
        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                sortPosts(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 🔹 즐겨찾기 필터 스피너 동작
        spinnerFav.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();

                if (selected.equals("전체 보기")) {
                    adapter.updateList(postList);
                } else if (selected.equals("즐겨찾기만 보기")) {
                    List<PostItem> favList = new ArrayList<>();
                    for (PostItem p : postList)
                        if (p.isFavorite())
                            favList.add(p);
                    adapter.updateList(favList);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
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

    private class CloadImage extends AsyncTask<String, Integer, List<PostItem>> {
        @Override
        protected List<PostItem> doInBackground(String... urls) {
            List<PostItem> resultList = new ArrayList<>();
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
                        String title = post.optString("title", "제목 없음");
                        String imageUrl = post.optString("image", "");
                        String createdDate = post.optString("created_date", "");

                        if (!imageUrl.startsWith("http")) {
                            imageUrl = site_url + imageUrl;
                        }

                        // ✅ 수정된 부분 (5개 인자 맞춤)
                        resultList.add(new PostItem("", title, "", imageUrl, createdDate));
                    }
                } else {
                    Log.e("HTTP", "Server responded with code: " + responseCode);
                }
            } catch (IOException | JSONException e) {
                Log.e("HTTP", "Error: " + e.getMessage(), e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            return resultList;
        }

        @Override
        protected void onPostExecute(List<PostItem> posts) {
            postList.clear();
            postList.addAll(posts);

            if (postList.isEmpty()) {
                textView.setText("불러올 게시글이 없습니다.");
            } else {
                textView.setText("총 " + postList.size() + "개의 게시글 로드됨");
                adapter.notifyDataSetChanged();
            }

            // 🔹 [작성자 필터 Spinner 설정]
            Set<String> authorSet = new HashSet<>();
            for (PostItem post : postList) {
                authorSet.add(post.getAuthor());
            }

            List<String> authorList = new ArrayList<>(authorSet);
            Collections.sort(authorList);
            authorList.add(0, "전체 보기");

            Spinner spinnerAuthor = findViewById(R.id.spinnerAuthor);
            ArrayAdapter<String> adapterAuthor = new ArrayAdapter<>(
                    MainActivity.this,
                    android.R.layout.simple_spinner_item,
                    authorList
            );
            adapterAuthor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAuthor.setAdapter(adapterAuthor);

            spinnerAuthor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedAuthor = parent.getItemAtPosition(position).toString();
                    List<PostItem> filteredList = new ArrayList<>();

                    if (selectedAuthor.equals("전체 보기")) {
                        filteredList.addAll(postList);
                    } else {
                        for (PostItem post : postList) {
                            if (post.getAuthor().equals(selectedAuthor)) {
                                filteredList.add(post);
                            }
                        }
                    }
                    adapter.updateList(filteredList);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    // ----------------------------- [2] 게시글 정렬 -----------------------------
    private void sortPosts(String criteria) {
        if (postList.isEmpty()) return;

        switch (criteria) {
            case "최신순":
                Collections.sort(postList, (a, b) -> b.getCreatedDate().compareTo(a.getCreatedDate()));
                break;
            case "오래된순":
                Collections.sort(postList, Comparator.comparing(PostItem::getCreatedDate));
                break;
            case "제목순":
                Collections.sort(postList, Comparator.comparing(PostItem::getTitle, String.CASE_INSENSITIVE_ORDER));
                break;
        }
        adapter.notifyDataSetChanged();
        Toast.makeText(this, criteria + "으로 정렬됨", Toast.LENGTH_SHORT).show();
    }

    // ----------------------------- [3] 테스트용 업로드 -----------------------------
    public void onClickUpload(View v) {
        new UploadImageTask().execute(site_url + "/api/posts/");
        Toast.makeText(getApplicationContext(), "Upload started", Toast.LENGTH_SHORT).show();
    }

    private class UploadImageTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            String apiUrl = urls[0];
            String token = "3d560b814c2bebf33cc6704356883724174f6b61";
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

            HttpURLConnection conn = null;
            DataOutputStream dos = null;

            try {
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.test_upload);
                if (bitmap == null) {
                    Log.e("UPLOAD", "리소스 이미지 로드 실패");
                    return false;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                byte[] imageData = baos.toByteArray();
                baos.close();

                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                dos = new DataOutputStream(conn.getOutputStream());
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"test_upload.jpg\"\r\n");
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                dos.write(imageData);
                dos.writeBytes("\r\n--" + boundary + "--\r\n");
                dos.flush();

                int code = conn.getResponseCode();
                Log.d("UPLOAD", "Response Code: " + code);
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
                onClickDownload(null);
            } else {
                Toast.makeText(getApplicationContext(), "업로드 실패 ❌", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ----------------------------- [4] 업로드 화면 이동 -----------------------------
    public void onClickGoUpload(View v) {
        Intent intent = new Intent(MainActivity.this, UploadActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        onClickDownload(null);
    }
}
