package com.example.photoviewer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * UploadActivity.java
 * 새 게시물 작성 (제목 + 내용 + 이미지 + 작성자)
 */
public class UploadActivity extends AppCompatActivity {
    EditText inputTitle, inputText, inputAuthor;
    ImageView previewImage;
    Button btnSelect, btnPost;
    Uri selectedImageUri = null;

    String site_url = "http://10.0.2.2:8000";
    private static final int REQUEST_SELECT_IMAGE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        inputTitle = findViewById(R.id.inputTitle);
        inputText = findViewById(R.id.inputText);
        inputAuthor = findViewById(R.id.inputAuthor);
        previewImage = findViewById(R.id.previewImage);
        btnSelect = findViewById(R.id.btnSelectImage);
        btnPost = findViewById(R.id.btnPost);

        // ✅ 이미지 선택 버튼
        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_SELECT_IMAGE);
        });

        // ✅ 게시 버튼
        btnPost.setOnClickListener(v -> {
            String title = inputTitle.getText().toString().trim();
            String text = inputText.getText().toString().trim();
            String author = inputAuthor.getText().toString().trim();

            if (title.isEmpty() || text.isEmpty() || author.isEmpty()) {
                Toast.makeText(this, "제목, 내용, 작성자 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            new UploadPostTask(title, text, author, selectedImageUri).execute(site_url + "/api/posts/");
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            previewImage.setImageURI(selectedImageUri);
        }
    }

    private class UploadPostTask extends AsyncTask<String, Void, Boolean> {
        private final String title, text, author;
        private final Uri imageUri;

        UploadPostTask(String title, String text, String author, Uri imageUri) {
            this.title = title;
            this.text = text;
            this.author = author;
            this.imageUri = imageUri;
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            String apiUrl = urls[0];
            String token = "3d560b814c2bebf33cc6704356883724174f6b61";
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

            try {
                Bitmap bmp;
                if (imageUri != null) {
                    bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    Log.d("UPLOAD", "사용자 선택 이미지 사용");
                } else {
                    bmp = BitmapFactory.decodeResource(getResources(), R.drawable.test_upload);
                    Log.d("UPLOAD", "기본 이미지 사용 (drawable/test_upload.jpg)");
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                byte[] imgData = baos.toByteArray();

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

                // 🔸 author 필드
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"author\"\r\n\r\n");
                dos.writeBytes(author + "\r\n");

                // 🔸 title 필드
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"title\"\r\n\r\n");
                dos.writeBytes(title + "\r\n");

                // 🔸 text 필드
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"text\"\r\n\r\n");
                dos.writeBytes(text + "\r\n");

                // 🔸 image 필드
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"upload.jpg\"\r\n");
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                dos.write(imgData);
                dos.writeBytes("\r\n--" + boundary + "--\r\n");
                dos.flush();
                dos.close();

                int code = conn.getResponseCode();
                Log.d("UPLOAD", "Response Code: " + code);

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream()
                ));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                Log.d("UPLOAD", "Response Body:\n" + sb);
                br.close();
                conn.disconnect();

                return (code == 200 || code == 201);
            } catch (Exception e) {
                Log.e("UPLOAD", "Error: " + e.getMessage(), e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Toast.makeText(getApplicationContext(), "게시 성공!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(UploadActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(getApplicationContext(), "게시 실패 ❌", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
