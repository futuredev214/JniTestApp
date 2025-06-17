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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "UartLog";           // 로그 태그 (Log tag)
    private Boolean isConnected = false;
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

    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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
                isConnected = false;

//                String data = "";
//                if (!editText.getText().toString().equals("")) {
//                    data = editText.getText().toString();
//                } else {
//                    data = "send data";
//                }
//                if (usbService != null) { // if UsbService was correctly binded, Send data
//                    usbService.write(data.getBytes());
//                }
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

        // 스트림이 초기화 되지 않은 경우, 스트림 초기화
        if (inputStream == null || outputStream == null) {
            try {
                initStream();
            } catch (IOException e) {
                Log.e(TAG, "스트림 초기화 실패");
                throw new RuntimeException(e);
            }
        }

        try {
            scheduler.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    if (isConnected) {
                        Log.d(TAG, "이미 연결됨");
                        return;
                    }

                    if (checkBluetoothConnected()) {
                        try {
                            Log.d(TAG, "1-1. [TX] : C");
                            String message = "C\r";
                            outputStream.write(message.getBytes("UTF-8"));
                            outputStream.flush();

                            isConnected = true;
                            Log.d(TAG, "클라이언트에 READY 전송");
                        } catch (IOException e) {
                            Log.e(TAG, "송신 실패", e);
                        }
                    } else {
                        isConnected = false;
                    }

                    // sendAtCommand("ATO");
                    // waitForSecond(1);
                }
            }, 0, 2, TimeUnit.SECONDS);  // 2초마다 체크

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
     * in-out 스트림 객체 초기화
     * @throws IOException
     */
    private void initStream() throws IOException {
        Runtime.getRuntime().exec(new String[]{"ssu", "-c", "chmod 666 /dev/ttyUSB0"});
        waitForSecond(1);

        File usbDevice = new File("/dev/ttyUSB0");

        inputStream = new FileInputStream(usbDevice);
        outputStream = new FileOutputStream(usbDevice);
        Log.d(TAG, "ttyUSB0 연결 성공 (File 기반 Stream 생성 성공)");
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
            // 명령어 전송
            if (!command.endsWith("\r")) command += "\r";
            Log.d(TAG, "\n[TX] " + command.trim());
            outputStream.write(command.getBytes("UTF-8"));
            outputStream.flush();

            // 응답 수신 대기
            byte[] buffer = new byte[64];
            int len;
            StringBuilder responseBuilder = new StringBuilder();
            long startTime = System.currentTimeMillis();

            while ((System.currentTimeMillis() - startTime) < 10_000) {
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
                // == 는 String 객체 주소값 비교여서 equals 써야함.
                Log.d(TAG, "command : " + command);
                if (command.equals("ATO\r")) {
                    Log.e(TAG, "[RX] 응답 없음(ATO 먹힘) == OK");
                    return null;
                } else {
                    Log.w(TAG, "10초 내에 응답 없음");
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

        if (checkBluetoothConnected()) {
            // 클라이언트의 연결이 끊기지 않게 데이터를 1회성으로 전송해야 한다.
            String message = "Hello from RTU!\r\n";
            outputStream.write(message.getBytes("UTF-8"));
            outputStream.flush();

            startReadingThread();
        }
        // startReadingThread();
    }

    public boolean checkBluetoothConnected() {
        Log.d(TAG, "연결 상태 확인을 위한 TX/RX 수행");

        // 1. AT 명령어 수신 가능 여부 테스트
        String result = sendAtCommand("AT");

        // 1-1. 수신 불가 시, 명령모드 진입 시도
        if (result == null || !result.contains("OK")) {
            Log.d(TAG, "온라인 상태에서 명령 대기 상태로 변경: +++");
            sendAtCommand("+++");
            waitForSecond(1);  // +++ 전후 대기 필수
        }

        // 이미 연결 상태로 표시되어 있으면 재확인 생략
        if (isConnected) {
            Log.d(TAG, "이미 연결된 상태로 간주함");
            return true;
        }

        // 2. 블루투스 연결 상태 질의
        result = sendAtCommand("AT+BTINFO?");
        if (result != null && result.contains("CONNECT")) {
            Log.d(TAG, "Bluetooth 연결 상태: ON");
            isConnected = true;
        } else {
            Log.d(TAG, "Bluetooth 연결 상태: OFF");
            isConnected = false;
        }

        // 3. 데이터 송수신을 위해 다시 온라인 모드로 전환
        sendAtCommand("ATO");
        waitForSecond(1);

        return isConnected;
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