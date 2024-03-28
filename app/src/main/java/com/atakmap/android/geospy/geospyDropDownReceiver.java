package com.atakmap.android.geospy;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.geospy.plugin.R;
import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class geospyDropDownReceiver extends DropDownReceiver implements
        DropDown.OnStateListener {

    public static final String TAG = "geospyDropDownReceiver";
    public static final String SHOW_PLUGIN = "com.atakmap.android.geospy.SHOW_PLUGIN";
    private final Context pluginContext;
    private final Context appContext;
    private final MapView mapView;
    private final View mainView;
    private Button pickBtn = null;
    private SharedPreferences prefs = null;
    private ImageView imageView = null;
    private String b64Image = null;
    private String payload = null;

    /**************************** CONSTRUCTOR *****************************/

    public geospyDropDownReceiver(final MapView mapView, final Context context) {
        super(mapView);
        this.pluginContext = context;
        this.appContext = mapView.getContext();
        this.mapView = mapView;

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.mainView = inflater.inflate(R.layout.main_layout, null);
        pickBtn = mainView.findViewById(R.id.pickBtn);
        imageView = mainView.findViewById(R.id.imageView);
        prefs = PreferenceManager.getDefaultSharedPreferences(mapView.getContext().getApplicationContext());

        pickBtn.setOnClickListener(v -> {

            String API = prefs.getString("plugin_geospy_api", "");
            if (API.isEmpty()) {
                Log.e(TAG, "API key is empty");
                Toast.makeText(mapView.getContext(), "API key is empty", Toast.LENGTH_SHORT).show();
                return;
            }

            ImportFileBrowserDialog.show(
                    "Select Image",
                    "/sdcard/",
                    new String[]{"*"},
                    new ImportFileBrowserDialog.DialogDismissed() {
                        @Override
                        public void onFileSelected(final File file) {
                            if (FileSystemUtils.isFile(file)) {
                                Toast.makeText(MapView._mapView.getContext(), "Uploading file: " + file.getName(), Toast.LENGTH_LONG).show();
                                Bitmap ThumbImage = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(file.getAbsolutePath()), 512, 512);
                                imageView.setImageBitmap(ThumbImage);
                                try {
                                    b64Image = Base64.getEncoder().encodeToString(FileSystemUtils.read(file));
                                    payload = "{\"inputs\": {\"image\": \"" + b64Image + "\"}, \"top_k\": 5}";
                                } catch (IOException e) {
                                    Toast.makeText(MapView._mapView.getContext(), "Failed to read file", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                new Thread(() -> {
                                    try {
                                        OkHttpClient client = new OkHttpClient.Builder()
                                                .connectTimeout(0, TimeUnit.MILLISECONDS)
                                                .writeTimeout(0, TimeUnit.MILLISECONDS)
                                                .readTimeout(0, TimeUnit.MILLISECONDS)
                                                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                                                .build();

                                        MediaType mediaType = MediaType.parse("application/json");
                                        RequestBody body = RequestBody.create(mediaType, payload);
                                        Request request = new Request.Builder()
                                                .url("https://dev.geospy.ai/predict")
                                                .post(body)
                                                .addHeader("Authorization", "Bearer ".concat(API))
                                                .addHeader("Content-Type", "application/json")
                                                .build();

                                        Response response = client.newCall(request).execute();

                                        Log.d(TAG, response.toString());
                                        Log.d(TAG, response.body().toString());

                                        JSONObject jsonObject = new JSONObject(response.body().string());

                                        if (jsonObject.getInt("status") == 200) {
                                            try {
                                                JSONArray predictions = jsonObject.getJSONArray("geo_predictions");
                                                for (int i = 0; i < predictions.length(); i++) {
                                                    JSONObject j = predictions.getJSONObject(i);

                                                    JSONArray coordinates = j.getJSONArray("coordinates");
                                                    double lat = coordinates.getDouble(0);
                                                    double lng = coordinates.getDouble(1);
                                                    double score = j.getDouble("score");
                                                    double similarity = j.getDouble("similarity_score_1km");

                                                    CotEvent cotEvent = new CotEvent();

                                                    CoordinatedTime time = new CoordinatedTime();
                                                    cotEvent.setTime(time);
                                                    cotEvent.setStart(time);
                                                    cotEvent.setStale(time.addDays(30));

                                                    cotEvent.setUID(UUID.randomUUID().toString());

                                                    cotEvent.setType("a-f-G-U-C");

                                                    cotEvent.setHow("m-g");

                                                    CotPoint cotPoint = new CotPoint(lat, lng, CotPoint.UNKNOWN, CotPoint.UNKNOWN, CotPoint.UNKNOWN);
                                                    cotEvent.setPoint(cotPoint);

                                                    CotDetail cotDetail = new CotDetail("detail");
                                                    cotEvent.setDetail(cotDetail);

                                                    CotDetail cotRemark = new CotDetail("remarks");
                                                    cotRemark.setAttribute("source", "GeoSpy.ai");
                                                    cotRemark.setInnerText(String.format(Locale.US, "Score: %f\nSimilarity Score 1km: %f", score, similarity));
                                                    cotDetail.addChild(cotRemark);

                                                    if (cotEvent.isValid())
                                                        CotMapComponent.getInternalDispatcher().dispatch(cotEvent);
                                                    else
                                                        Log.e(TAG, "cotEvent was not valid");

                                                    Log.d(TAG, cotEvent.toString());
                                                    mapView.getMapController().panTo(new GeoPoint(lat, lng), true);
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, String.format("Failed to create CotEvent"));
                                                e.printStackTrace();
                                            }
                                        } else {
                                            Log.e(TAG, String.format("Got response code: %d ", jsonObject.getInt("status")));
                                        }
                                    } catch (Exception ex) {
                                        Log.d(TAG, "Failed to get response from GeoSpy");
                                        ex.printStackTrace();
                                    }
                                }).start();
                            }
                        }

                        @Override
                        public void onDialogClosed() {
                            Toast.makeText(MapView._mapView.getContext(), "No file selected!", Toast.LENGTH_LONG).show();
                        }
                    }, getMapView().getContext());
            });
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(SHOW_PLUGIN)) {
            showDropDown(mainView, HALF_WIDTH, FULL_HEIGHT,
                    FULL_WIDTH, HALF_HEIGHT, false, this);
        }
    }

    @Override
    public void disposeImpl() {

    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }
}
