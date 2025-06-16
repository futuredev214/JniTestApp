package kr.co.mirerotack.jniTest;

/**
 * USB Serial 데이터 수신 및 로그 출력 구현
 * USB Serial Data Reception and Log Output Implementation
 *
 * usb-serial-for-android 라이브러리 사용
 * Using https://github.com/felHR85/UsbSerial
 */

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "UartLog";           // 로그 태그 (Log tag)
    private static final int BAUD_RATE = 115200;                      // Parani-SD1000 보드레이트
    private static final int DATA_BIT = 8;                            // Parani-SD1000 기본 데이터 비트 (Default data bit)
    private static final int READ_TIMEOUT = 1000;                     // 읽기 타임아웃 (Read timeout)
    private static final int WRITE_TIMEOUT = 1000;                    // 쓰기 타임아웃 (Write timeout)

    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("kr.co.mirerotack.smartrtumobile.USB_PERMISSION".equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.d(TAG, "USB 권한 승인됨: " + device.getDeviceName());
                            // 여기서 USB 장치를 연결하거나 초기화
                        }
                    } else {
                        Log.w(TAG, "USB 권한 거부됨: " + device.getDeviceName());
                    }
                }
            }
        }
    };

    private UsbService usbService;
    private UsbManager usbManager;

    private FileInputStream inputStream = null;
    private FileOutputStream outputStream = null;
    private volatile boolean isReading = false;  // 로딩중인지 boolean 값
    private Thread readThread;                   // ?

    private TextView display;
    private EditText editText;
    private MyHandler mHandler;
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new MyHandler(this);

        display = (TextView) findViewById(R.id.textView1);
        editText = (EditText) findViewById(R.id.editText1);
        Button sendButton = (Button) findViewById(R.id.buttonSend);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String data = "";
                if (!editText.getText().toString().equals("")) {
                    data = editText.getText().toString();
                } else {
                    data = "send data";
                }
                if (usbService != null) { // if UsbService was correctly binded, Send data
                    usbService.write(data.getBytes());
                }
            }
        });

        usbManager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            Log.e(TAG, "UsbManager is null");
            return;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        try {
            usbConnect();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException");
            throw new RuntimeException(e);
        } catch (IOException e) {
            Log.e(TAG, "IOException");
            throw new RuntimeException(e);
        } catch (Exception e) {
            Log.e(TAG, "Exception [" + e.getCause() + "] " + e.getMessage());
            throw new RuntimeException(e);
        }
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    public void onPause() {
        super.onPause();
        stopReadingThread();

        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    /**
     * 스트림이 초기화 되지 않은 경우, 스트림 초기화
     * @throws IOException
     */
    private void initStream() throws IOException {
        if (inputStream != null && outputStream != null) {
            Log.w(TAG, "이미 in-out 스트림이 초기화 되어 있습니다.");
            return;
        }

        Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod 666 /dev/ttyUSB0"});
        File usbDevice = new File("/dev/ttyUSB0");

        inputStream = new FileInputStream(usbDevice);
        outputStream = new FileOutputStream(usbDevice);
        Log.d(TAG, "ttyUSB0 연결 성공 (File 기반 Stream 생성 성공");
    }

    private void releaseStream() throws IOException {
        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        }

        if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
        Log.d(TAG, "ttyUSB0 연결 해제 성공");
    }

    // 자체적으로 AT 명령어를 보내고 응답을 수신하는 메서드
    public String sendAtCommand(String command) {
        String TAG = "/dev/ttyUSB0 TX, RX";

        try {
            initStream();

            // 명령어 전송
            if (!command.endsWith("\r")) command += "\r";
            Log.d(TAG, "[TX] " + command.trim());
            outputStream.write(command.getBytes("UTF-8"));
            outputStream.flush();

            // 응답 수신 대기
            byte[] buffer = new byte[64];
            int len;
            StringBuilder responseBuilder = new StringBuilder();
            long startTime = System.currentTimeMillis();

            while ((System.currentTimeMillis() - startTime) < 2000) {
                if (inputStream.available() > 0) {
                    len = inputStream.read(buffer);
                    if (len > 0) {
                        String part = new String(buffer, 0, len, "UTF-8");
                        responseBuilder.append(part);
                        if (part.contains("OK") || part.contains("ERROR")) break;
                    }
                }
            }

            String response = responseBuilder.toString().trim();
            if (!response.isEmpty()) {
                Log.d(TAG, "[RX] " + response);
            } else {
                if (command == "ATO") {
                    Log.e(TAG, "[RX] 응답 없음(ATO 먹힘) == OK");
                    return null;
                } else {
                    Log.w(TAG, "2초 내에 응답 없음");
                    return null;
                }
            }

            return response;

        } catch (IOException e) {
            Log.e(TAG, "IO 예외 발생: " + e.getMessage());
            return null;

        }
//        finally {
//            try {
//                if (inputStream != null) inputStream.close();
//                if (outputStream != null) outputStream.close();
//                Log.d(TAG, "ttyUSB0 스트림 종료");
//            } catch (IOException e) {
//                Log.w(TAG, "스트림 닫기 실패: " + e.getMessage());
//            }
//        }
    }


    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    private void usbConnect() throws IOException {
        IntentFilter filter = new IntentFilter("kr.co.mirerotack.smartrtumobile.USB_PERMISSION");
        getApplicationContext().registerReceiver(usbReceiver, filter);

        initStream();
        Log.d(TAG, "File 객체 기반 USB 스트림 open 성공");

        Log.d(TAG, "연결 상태 확인을 위한 TX/RX 수행");

        // 1. 먼저 AT 명령어를 통해 호스트가 AT 명령어를 수신할 수 있는 상태인지 체크한다.
        String result = sendAtCommand("AT");    // AT 명령어를 통해서 현재 호스트와 RTU가 연결된 상태인지 체크

        // 1-1. 수신할 수 없는 상태인 경우, "+++" 명령을 통해 AT 명령어를 수신할 수 있도록 설정한다.
        if (result == null) {
            Log.d(TAG, "온라인 상태에서 명령 대기 상태로 변경 : +++");
            sendAtCommand("+++");          // +++을 통해서 mode를 변경하지 않고, AT 명령어에 대해 수신이 가능하도록 설정함
            waitForSecond(2);      // +++ 전후 1초 대기 권장
        }

        // 2. AT+BTINFO? 명령어를 통해서 현재 Client 장비가 Bluetooth로 연결되어 있는 상태인지 체크한다.
        // 예시) 0001950E5825,SD1000v2.0.4-0E5825,MODE3,CONNECT,0,0,NoFC
        result = sendAtCommand("AT+BTINFO?");

        // 3. 연결중인 상태 감지 가능 -> 리스너에게 전달
        if (result.contains("CONNECT")) {
            Log.d(TAG, "Bluetooth 연결 상태 : ON");
        } else { // 3-1. 연결 중이 아닌 상태
            Log.d(TAG, "Bluetooth 연결 상태 : OFF");
        }

        // 4. 처리가 완료된 경우 다시 온라인 모드로 변경하기 위해서 ATO 명령어 수행
        sendAtCommand("ATO");  // +++을 통해서 mode를 변경하지 않고, AT 명령어에 대해 수신이 가능하도록 설정함

        // startReadingThread();
    }

    private void startReadingThread() {
        isReading = true;
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "수신 스레드 시작됨");
                byte[] buffer = new byte[64];
                while (isReading) {
                    try {
                        int len = inputStream.read(buffer);
                        if (len > 0) {
                            String received = new String(buffer, 0, len, "UTF-8");
                            Log.d(TAG, "수신 데이터: " + received);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "데이터 수신 중 오류: " + e.getMessage());
                        break;
                    }
                }
                Log.w(TAG, "수신 스레드 종료됨");
            }
        });
        readThread.start();
    }

    private void stopReadingThread() {
        isReading = false;
        if (readThread != null && readThread.isAlive()) {
            try {
                readThread.join();

                // 의존성 제거
                releaseStream();
            } catch (InterruptedException e) {
                Log.w(TAG, "수신 스레드 중단 실패: " + e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // 전송용 메서드
    public void sendToUsb(String data) {
        if (outputStream == null) {
            Log.w(TAG, "전송 실패: outputStream이 null");
            return;
        }
        try {
            byte[] bytes = (data + "\r\n").getBytes("UTF-8");
            outputStream.write(bytes);
            outputStream.flush();
            Log.d(TAG, "전송 데이터: " + data);
        } catch (IOException e) {
            Log.e(TAG, "데이터 전송 중 오류: " + e.getMessage());
        }
    }

    public static void waitForSecond(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            // 인터럽트 발생 시 로그 출력
            android.util.Log.w("SleepUtil", "Sleep 인터럽트 발생: " + e.getMessage());
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
        }
    }

    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;
                    mActivity.get().display.append(data);
                    break;
                case UsbService.CTS_CHANGE:
                    Toast.makeText(mActivity.get(), "CTS_CHANGE",Toast.LENGTH_LONG).show();
                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(mActivity.get(), "DSR_CHANGE",Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
}