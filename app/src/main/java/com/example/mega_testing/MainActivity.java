package com.example.mega_testing;
//




import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

//import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONObject;

import java.io.*;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.*;
import retrofit2.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public class MainActivity extends AppCompatActivity {
    public TextView URItextView;
    public TextView id_textView;

    public String URI="";
    public String FILENAME="";
    public String NODEID="";


    private static final int PICK_FILE = 100;
    private static final String SERVER_URL = "https://mega-cloud-storage-bridge.onrender.com/";
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        id_textView=findViewById(R.id.node_id);
        URItextView=findViewById(R.id.download_uri);;
        WakeUpServer();
//
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE && resultCode == RESULT_OK) {
            uploadFile(data.getData());
        }
    }

    // ==============================
    // 📤 UPLOAD FILE
    // ==============================
    private void uploadFile(Uri uri) {
        try {

//            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
//                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
//                return;
//            }

            String uid = "uid1";
            String folder = "folder2";

            InputStream inputStream = getContentResolver().openInputStream(uri);

            // 🔥 Get real file name
            String fileName = "file_" + System.currentTimeMillis();

            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }

            // Ensure extension
            if (!fileName.contains(".")) {
                String mime = getContentResolver().getType(uri);
                if (mime != null) {
                    String ext = android.webkit.MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(mime);
                    if (ext != null) fileName += "." + ext;
                }
            }

            Log.d("FILE_NAME", fileName);

            File file = new File(getCacheDir(), fileName);
            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int len;

            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }

            outputStream.close();
            inputStream.close();

            String type = getContentResolver().getType(uri);

            RequestBody requestFile =
                    RequestBody.create(MediaType.parse(type != null ? type : "*/*"), file);

            MultipartBody.Part filePart =
                    MultipartBody.Part.createFormData("file", file.getName(), requestFile);

            // 🔥 UID + FOLDER
            RequestBody uidPart = RequestBody.create(
                    MediaType.parse("text/plain"), uid
            );

            RequestBody folderPart = RequestBody.create(
                    MediaType.parse("text/plain"), folder
            );

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.MINUTES)
                    .readTimeout(2, TimeUnit.MINUTES)
                    .writeTimeout(2, TimeUnit.MINUTES)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(SERVER_URL)
                    .client(client)
                    .build();

            ApiService api = retrofit.create(ApiService.class);

            api.uploadFile(uidPart, folderPart, filePart).enqueue(new Callback<ResponseBody>() {

                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try {
                        String json = response.body().string();
                        Log.d("SERVER_RESPONSE", json);

                        JSONObject obj = new JSONObject(json);

                        String fileName = obj.getString("fileName");
                        String nodeId = obj.getString("nodeId"); // 🔥 IMPORTANT

                        Log.d("NODE_ID", nodeId);

                        // 🔥 Encode filename
                        String encodedFileName = URLEncoder.encode(fileName, "UTF-8");

                        String downloadUrl = SERVER_URL + "download/"
                                + nodeId + "/"
                                + encodedFileName;
                        Log.d("DOWNLOAD_URL",downloadUrl+" "+fileName);
                        URI=downloadUrl;
                        String message=String.format("File Name:%s\n\nNode ID:%s\n\nDownload Url:%s",fileName,nodeId,downloadUrl);
                        NODEID=nodeId;
                        FILENAME=fileName;
//                        URItextView.setText(URI);
                        id_textView.setText(message);
//                        downloadFile(downloadUrl, fileName);

                    } catch (Exception e) {
                        Log.e("ERROR", e.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e("UPLOAD_ERROR", t.toString());
                }
            });

        } catch (Exception e) {
            Log.e("FILE_ERROR", e.getMessage());
        }
    }

    // ==============================
    // ⬇️ DOWNLOAD FILE
    // ==============================
    private void downloadFile(String url, String fileName) {
        if(!NODEID.equals("") && !FILENAME.equals("")){
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new okhttp3.Callback() {

                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    Log.e("DOWNLOAD", e.getMessage());
                }

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                    try {
                        InputStream inputStream = response.body().byteStream();

                        String mimeType = URLConnection.guessContentTypeFromName(fileName);
                        if (mimeType == null) mimeType = "*/*";

                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                        Uri uri = getContentResolver().insert(
                                MediaStore.Files.getContentUri("external"),
                                values
                        );

                        OutputStream outputStream = getContentResolver().openOutputStream(uri);

                        byte[] buffer = new byte[4096];
                        int len;

                        while ((len = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, len);
                        }

                        outputStream.close();
                        inputStream.close();

                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this,
                                        "Downloaded: " + fileName,
                                        Toast.LENGTH_LONG).show()
                        );

                    } catch (Exception e) {
                        Log.e("DOWNLOAD_ERROR", e.getMessage());
                    }
                }
            });
        }

    }

    private void DeleteFile(String nodeId) {

        if(!NODEID.equals("") && !FILENAME.equals("")){
            String url = SERVER_URL + "delete/" + nodeId;

            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(url)
                    .delete()
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {

                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    Log.e("DELETE", "Error: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                    Log.d("DELETE", "File deleted successfully");
                    NODEID="";
                    FILENAME="";
                    id_textView.setText("");
//                Toast.makeText(MainActivity.this, "File deleted successfully", Toast.LENGTH_SHORT).show();
                }
            });
        }


    }
    public void WakeUpServer(){

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(SERVER_URL + "wakeup")
                .get()
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                Log.e("WAKEUP", e.getMessage());
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                Log.d("WAKEUP",
                        "Server awake: " + response.code());
                response.close();
            }
        });







    }


    // ==============================
    // 🌐 API
    // ==============================
    public interface ApiService {
        @Multipart
        @POST("upload")
        Call<ResponseBody> uploadFile(
                @Part("uid") RequestBody uid,
                @Part("folder") RequestBody folder,
                @Part MultipartBody.Part file
        );
    }

    public void UPLOAD(View view){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE);
    }
    public void DOWNLOAD(View view){
        downloadFile(URI,FILENAME);
    }
    public void DELETE(View view){
        DeleteFile(NODEID);
    }


}