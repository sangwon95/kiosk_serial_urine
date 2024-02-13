package com.onwards.kiosk;

import static com.onwards.kiosk.bluetooth.BluetoothGattAttributes.UUID_SERVICE;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.onwards.kiosk.bluetooth.BluetoothGattAttributes;
import com.onwards.kiosk.bluetooth.BluetoothLeService;
import com.onwards.kiosk.bluetooth.UrineModel;
import com.onwards.kiosk.fragment.DevicesFragment;
import com.onwards.kiosk.fragment.TerminalFragment;
import com.onwards.kiosk.utils.Etc;

import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {

    private final String TAG = "MainActivity - 로그";
    private final static int BLUETOOTH_REQUEST_CODE = 100; // 블루투스 요청 액티비티 코드
    private ImageView btn_bluetooth;

    private final int REQUEST_BLUETOOTH_CODE = 1;
    private ProgressDialog progressDialogConnect;     // 프로그레스 bluetooth 연결
    private ProgressDialog progressInspectionDialog; // 프로그레스 연결된 디바이스 데이터 수집
    private BluetoothLeService mBluetoothLeService; // BluetoothLeService 클래스
    private String address; // Bluetooth Mac address;
    private boolean mConnected = false; // 연결 상태 확인



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn_bluetooth = findViewById(R.id.btn_bluetooth); // bluetooth 검색

        //getSupportFragmentManager().addOnBackStackChangedListener(this);

        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
        else
            onBackStackChanged();

        // 블루투스 퍼미션 확인
        mPermissionCheck();
    }

    @Override
    protected void onResume() {
        super.onResume();

        btn_bluetooth.setOnClickListener(view -> {
            startActivityForResult(new Intent(getApplicationContext(), BluetoothActivity.class), REQUEST_BLUETOOTH_CODE);
        });
    }
    /**
     * Intent 결과 함수
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult Execution");

        if (requestCode == REQUEST_BLUETOOTH_CODE) {
            if (resultCode == RESULT_OK) {

                assert data != null;
                String DeviceAddress = data.getStringExtra("DeviceAddress");
                Log.d(TAG, "DeviceAddress: " + DeviceAddress);

                if (DeviceAddress != null) {
                    address = DeviceAddress;
                    ConnectionDevice(); // 블루투스 연결 시도
                    Log.d(TAG, "ConnectionDevice_Intent Execution");
                }
            } else {  // RESULT_CANCEL
                Log.w(TAG,"ActivityResult Failed");
            }
        }
    }

    @Override
    public void onBackStackChanged() {
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            TerminalFragment terminal = (TerminalFragment)getSupportFragmentManager().findFragmentByTag("terminal");
            if (terminal != null)
                Log.d("MainActivity","USB device detected");
        }
        super.onNewIntent(intent);
    }



    /**
     * Bluetooth 퍼미션 체크
     */
    private void mPermissionCheck(){
        // 블루투스 지원 유무 확인
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        // SDK 23 check permission
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        }

        //  블루투스를 지원하지 않을 경우
        if (mBluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "블루투스를 지원하지 않는 단말기 입니다.", Toast.LENGTH_SHORT).show();
        }

        // 블루투스 BLE 를 지원하는지 확인
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE 를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // 블루투스 비 활성화일 경우
        if (!mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, BLUETOOTH_REQUEST_CODE);
        }
    }

    /**
     * 블루투스 연결 함수
     */
    private void ConnectionDevice() {
        Log.d(TAG, "ConnectionDevice Execution");
        progressDialogConnect = new ProgressDialog(MainActivity.this);
        progressDialogConnect.show();
        progressDialogConnect.setContentView(R.layout.progress_dialog_connect);
        progressDialogConnect.getWindow().setBackgroundDrawableResource(android.R.color.transparent); // 투명 컬러

        // IntentFilter Receiver 등록
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        // bindService -> Service 를 실행하고 결과를 Activity 의 UI에 반영해주는 기능, mServiceConnection 함수에서 연결 요청결과 값을 받는다.
        // gattServiceIntent 기반으로 Service를 실행시키고 요청 하게된다.
        // 세번째 인자는 바인딩 옵션을 설정하는 flags를 설정한다.
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    /**
     * Code to manage Service lifecycle.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                Log.d(TAG, "GATT Service Connection");
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(address);// 장치 연결
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            Log.d(TAG, "GATT Service Disconnection");
        }
    };

    /**
     * Receiver 등록
     * @return
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.EXTRA_DATA_TIME_OUT);
        return intentFilter;
    }


    /**
     * Broadcast Receiver 는 BluetoothLeService 로부터 연결상태와 데이터들을 받아오는 역활을 한다.
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "GATT Connection to server successful");
                mConnected = true;
                invalidateOptionsMenu();
            }

            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "GATT Server connection lost.");
                mConnected = false;
                invalidateOptionsMenu();
                unbindService(mServiceConnection);
                progressDialogConnect.dismiss();
                showConnectionFailureAlertDialog();
            }

            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "GATT Service found.");
                List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
                if (gattServices == null){
                    Toast.makeText(getApplicationContext(),"Gatt Services 존재 하지 않습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                for(BluetoothGattService service : gattServices)
                {
                    Log.d(TAG, "service :" + service.getUuid());
                    if(service.getUuid().toString().equals(UUID_SERVICE))
                    {
                        for(BluetoothGattCharacteristic characteristics : service.getCharacteristics())
                        {
                            Log.d(TAG, "characteristics :" + characteristics.getUuid());
                            if(characteristics.getUuid().toString().equals(BluetoothGattAttributes.UUID_URINE_ANALYZER))
                            {
                                Log.d(TAG, "writeCharacteristic: " + characteristics.getUuid());
                                progressDialogConnect.dismiss();
                                showSuccessAlertDialog(characteristics);
                            }
                        }
                    }
                }
            }

            /// 검사 완료 되고 데이터를 수집 했을 때
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String extraData = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, "BroadcastReceiver data: " + extraData);

                UrineModel urineModel = new UrineModel();
                urineModel.initialization(extraData);

                progressInspectionDialog.dismiss();

//                Intent resultIntent = new Intent(MainActivity.this, LatelyActivity.class);
//                resultIntent.putExtra("UrineModel", urineModel);
//
//                startActivity(resultIntent);
            }

            else if(BluetoothLeService.EXTRA_DATA_TIME_OUT.equals(action)) {
                Log.d(TAG, "GATT extra data time out.");
                progressDialogConnect.dismiss();
                progressInspectionDialog.dismiss();
                showFailureAlertDialogShow();
            }
        }
    };


    private void showConnectionFailureAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this,android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("유린기와 연결되지 않았습니다.").setMessage("재연결 하시겠습니까?");

        builder.setNegativeButton("아니오", (dialog, id) -> {
        });

        builder.setPositiveButton("예", (dialog, id) ->
                startActivityForResult(new Intent(getApplicationContext(), BluetoothActivity.class), REQUEST_BLUETOOTH_CODE));

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showSuccessAlertDialog(BluetoothGattCharacteristic gattWriteCharacteristic) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("유린기와 연결 상태").setMessage("데이터를 가져오겠습니까?");

        builder.setNegativeButton("아니오", (dialog, id) -> { });

        builder.setPositiveButton("예", (dialog, id) -> {
            bluetoothWrite(gattWriteCharacteristic);

            progressInspectionDialog = new ProgressDialog(MainActivity.this);
            progressInspectionDialog.show();
            progressInspectionDialog.setContentView(R.layout.progress_dialog);
            progressInspectionDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); // 투명 컬러
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showFailureAlertDialogShow() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this,android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("유린기에서 데이터 송신을 하지 않았습니다.");

        builder.setPositiveButton("확인", (dialog, id) -> { });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * 유린 분석기 [%TS] 데이터 전송
     */
    private void bluetoothWrite(BluetoothGattCharacteristic gattWriteCharacteristic) {
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            if (gattWriteCharacteristic != null) {
                final BluetoothGattCharacteristic characteristic = gattWriteCharacteristic;

                // 검사 시작 write
                byte[] data2 = Etc.hexStringToByteArray("2554530a");
                boolean writeResult = mBluetoothLeService.writeCharacteristic(characteristic, data2);

                Log.d(TAG, "writeCharacteristic : " + writeResult);
                Log.d(TAG, "readCharacteristic Execution: " + characteristic);
            }
            else {
                Log.d(TAG, "DataValue Null");
            }
        }, 500); //delay 2초?
    }
}
