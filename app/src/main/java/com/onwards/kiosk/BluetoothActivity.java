
package com.onwards.kiosk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bluetooth 주변 검색 및 연결 클래스
 *
 * @author 박상원
 * @version 1.1
 * @see <pre>
 *
 * </pre>
 * @since 2020. 08. 05
 */

public class BluetoothActivity extends AppCompatActivity {

    private final static String TAG = BluetoothActivity.class.getSimpleName();

    private ProgressBar mProgressBar;
    private ImageView btn_search_bluetooth;
    private ListView listDevice;

    private List<Map<String, String>> dataDevice;  // 연결 가능한 Bluetooth 리스트
    private List<BluetoothDevice> bluetoothDevices;

    private SimpleAdapter adapterDevice;
    private BluetoothAdapter mBluetoothAdapter;

    private final static int BLUETOOTH_REQUEST_CODE = 100; // 블루투스 요청 액티비티 코드

    private String address;// Bluetooth Mac Address

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        //progressBar -상단 블루투스 검색
        mProgressBar = findViewById(R.id.pb_bluetooth);
        mProgressBar.getIndeterminateDrawable().setColorFilter(getResources().getColor(R.color.colorProgressBar), android.graphics.PorterDuff.Mode.SRC_IN);

        //Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_baseline_arrow_back);


        //ListView
        listDevice = findViewById(R.id.listDevice);

        //Adapter1
        dataDevice = new ArrayList<>();
        adapterDevice = new SimpleAdapter(BluetoothActivity.this, dataDevice, android.R.layout.simple_list_item_2, new String[]{"name", "address"}, new int[]{android.R.id.text1, android.R.id.text2});
        listDevice.setAdapter(adapterDevice);

        //검색된 블루투스 디바이스 데이터
        bluetoothDevices = new ArrayList<>();

        //블루투스 검색 시작 버튼
        btn_search_bluetooth = findViewById(R.id.btn_search_bluetooth);

        // Receiver_1
        IntentFilter stateFilter = new IntentFilter();
        stateFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); //BluetoothAdapter.ACTION_STATE_CHANGED : 블루투스 상태변화 액션

        // Receiver_2
        IntentFilter searchFilter = new IntentFilter();
        searchFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);  // 블루투스 검색 시작
        searchFilter.addAction(BluetoothDevice.ACTION_FOUND);               // 블루투스 디바이스 찾음
        searchFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); // 블루투스 검색 종료
        searchFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBluetoothSearchReceiver, searchFilter);

        // Receiver_3
        IntentFilter scanmodeFilter = new IntentFilter();
        scanmodeFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(mBluetoothScanModeReceiver, scanmodeFilter);

        btn_search_bluetooth.setColorFilter(Color.parseColor("#265ed7"));
    }


    @Override
    public void onStart() {
        super.onStart();
        //SDK 23 check permission
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(
                BluetoothActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(BluetoothActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        }

        // 블루투스 지원 유무 확인
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null)
        {
            Toast.makeText(BluetoothActivity.this, "블루투스를 지원하지 않는 단말기 입니다.", Toast.LENGTH_SHORT).show();
        }

        //블루투스 BLE를 지원하는지 확인
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Toast.makeText(this, "Bluetooth Ble를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!mBluetoothAdapter.isEnabled()) { // 비 활성화일 경우

            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, BLUETOOTH_REQUEST_CODE);
        }
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onResume() {
        super.onResume();

        // 버튼 상태변경
        btn_search_bluetooth.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        btn_search_bluetooth.setEnabled(false);

        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        mBluetoothAdapter.startDiscovery();// 블루투스 검색 시작


        /**
         * 수동 블루투스 검색 버튼
         */
        btn_search_bluetooth.setOnClickListener(view ->
        {
            btn_search_bluetooth.setVisibility(View.INVISIBLE);
            mProgressBar.setVisibility(View.VISIBLE);
            btn_search_bluetooth.setEnabled(false);

            if (mBluetoothAdapter.isDiscovering())
            {
                mBluetoothAdapter.cancelDiscovery();
            }
            mBluetoothAdapter.startDiscovery();
        });


        /**
         * 검색된 주변 디바이스 리스트(클릭 이벤트)
         */
        listDevice.setOnItemClickListener((adapterView, view, position, id) ->
        {
            BluetoothDevice device = bluetoothDevices.get(position);
            if (device == null)
            {
                Toast.makeText(BluetoothActivity.this, "선택된 디바이스가 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
            }
            else
            {
                address = device.getAddress();
                Log.d(TAG,  "Bluetooth Ble Mac Address:" + address);

                // Mac Address 저장
                SharedPreferences sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("DeviceAddress", address);
                editor.commit();

                Intent intent = new Intent(); // Mac Address 획득시 메인 화면으로 이동후 연결 진행
                intent.putExtra("DeviceAddress", address);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }


    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }


    @Override
    public void onDestroy() {
        unregisterReceiver(mBluetoothSearchReceiver);
        unregisterReceiver(mBluetoothScanModeReceiver);
        super.onDestroy();
    }



    /**
     * 블루투스 검색결과 BroadcastReceiver
     */
    private final BroadcastReceiver mBluetoothSearchReceiver = new BroadcastReceiver()
    {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    Log.v("TAG", "ACTION_DISCOVERY_STARTED");
                    dataDevice.clear();
                    bluetoothDevices.clear();
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    Log.v("TAG", "ACTION_FOUND");
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (device.getName() != null) // 불분명한 Name 필터링
                    {
                        //데이터 저장
                        Map map = new HashMap();
                        map.put("name", device.getName());        // 블루투스 디바이스의 이름
                        map.put("address", device.getAddress());  // 블루투스 디바이스의 MAC 주소
                        if (!dataDevice.contains(map))
                        {
                            dataDevice.add(map);
                        }
                        adapterDevice.notifyDataSetChanged(); // 리스트 목록갱신
                        Log.v(TAG, " serachList notifyDataSetChanged");
                        bluetoothDevices.add(device); // 블루투스 디바이스 저장
                    }
                    break;

                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    Log.v(TAG, "ACTION_DISCOVERY_FINISHED");

                    btn_search_bluetooth.setVisibility(View.VISIBLE);
                    mProgressBar.setVisibility(View.INVISIBLE);
                    btn_search_bluetooth.setEnabled(true);


                    if (dataDevice.isEmpty())
                    {
                        btn_search_bluetooth.setVisibility(View.VISIBLE);
                        mProgressBar.setVisibility(View.INVISIBLE);
                        btn_search_bluetooth.setEnabled(true);

                        //리스트 목록갱신
                        adapterDevice.notifyDataSetChanged();
                        Log.v(TAG, " search List notifyDataSetChanged");
                    }
                    break;
            }
        }
    };

    /**
     * 블루투스 검색응답 모드 BroadcastReceiver
     */
    BroadcastReceiver mBluetoothScanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
            switch (state) {
                case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                case BluetoothAdapter.SCAN_MODE_NONE:
                    break;
                case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
            }
        }
    };

    /**
     * 블루투스 활성화에 대한 응답 함수
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case BLUETOOTH_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) { }
                else {
                    Toast.makeText(BluetoothActivity.this, "블루투스를 활성화해야 합니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
        }
    }

    /**
     * 주변기기 검색 퍼미션 응답 함수
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 0:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "주변기기를 검색할 수 있습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "주변기기 검색을 하지 못합니다.", Toast.LENGTH_LONG).show();
                }
        }
    }

    /**
     * //Toolbar의 back키 동작
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                finish();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

}

