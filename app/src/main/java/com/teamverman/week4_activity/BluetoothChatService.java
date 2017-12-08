package com.teamverman.week4_activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by 김세훈 on 2017-12-08.
 */
public class BluetoothChatService {
    private static final String TAG = "BlueToothChatService";
    private static final String NAME_SECURE = "BluetoothChatSecure";

    private static final UUID MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private int mNewState;

    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    private static final int MESSAGE_STATE_CHANGE = 1;
    private static final int MESSAGE_READ = 2;
    private static final int MESSAGE_WRITE = 3;
    private static final int MESSAGE_DEVICE_NAME = 4;
    private static final int MESSAGE_TOAST = 5;

    private static final String DEVICE_NAME = "device_name";
    private static final String TOAST = "toast";

    public BluetoothChatService(Context context, Handler handler){
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mNewState = mState;
        mHandler = handler;
    }

    private synchronized void updateUserInterfaceTitle(){
        mState = getState();
        Log.e(TAG, "updateUserInterfaceTitle() "+mNewState+" ->" + mState);
        mNewState = mState;

        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();

    }

    public synchronized int getState(){
        return mState;
    }

    public  synchronized void start(){
        Log.e(TAG, "start");

        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if(mSecureAcceptThread == null){
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }

        updateUserInterfaceTitle();
    }

    public synchronized void connect(BluetoothDevice device, boolean secure){
        Log.e(TAG, device+"에 연결");

        if (mState == STATE_CONNECTING){
            if(mConnectThread != null){
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();

        updateUserInterfaceTitle();
    }

    public synchronized  void connected(BluetoothSocket socket, BluetoothDevice device, final String socketType){
        Log.e(TAG, "연결되었습니다");


        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if(mSecureAcceptThread != null){
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        updateUserInterfaceTitle();
    }

    public synchronized  void stop(){
        Log.d(TAG, "stop");

        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if(mSecureAcceptThread != null){
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        mState = STATE_NONE;
        updateUserInterfaceTitle();
    }

    public void write(byte[] out){
        ConnectedThread r;

        synchronized (this){
            if(mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(out);
    }

    private void connectionFailed(){
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "기기에 연결 할 수 없습니다.");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        updateUserInterfaceTitle();

        BluetoothChatService.this.start();
    }

    private void connectionLost(){
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "연결이 끊겼습니다.");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        updateUserInterfaceTitle();

        BluetoothChatService.this.start();

    }

    private class AcceptThread extends Thread{
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure){
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            try {
                if(secure)
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
            } catch (IOException e){
                Log.e(TAG, mSocketType+" 서버 소켓 생성 실패", e);
            }

            mmServerSocket = tmp;
            mState = STATE_LISTEN;
        }

        public void run(){
            Log.d(TAG, mSocketType+" mAcceptThread 실행"+this);
            setName("AcceptThread"+mSocketType);

            BluetoothSocket socket = null;

            while(mState!=STATE_CONNECTED){
                try{
                    socket = mmServerSocket.accept();
                } catch (IOException e){
                    Log.e(TAG, "Socekt type : "+mSocketType+" accept() failed", e);
                    break;
                }
                if(socket != null){
                    synchronized (BluetoothChatService.this){
                        switch(mState){
                            case STATE_LISTEN:{}
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice(), mSocketType);
                                break;
                            case STATE_NONE:{}
                            case STATE_CONNECTED:
                                try{
                                    socket.close();
                                } catch (IOException e){
                                    Log.e(TAG, "소켓을 닫을 수 없습니다.", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket type : "+mSocketType);
        }

        public void cancel(){
            Log.d(TAG, mSocketType+"소켓을 닫습니다 "+this);
            try{
                mmServerSocket.close();
            } catch (IOException e){
                Log.e(TAG, "서버의 "+mSocketType+"소켓을 닫는데 실패!", e);
            }
        }
    }


    private class ConnectThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure){
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            try {
                if(secure)
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
            } catch (IOException e){
                Log.e(TAG, mSocketType+" 서버 소켓 생성 실패", e);
            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, mSocketType + " mConnectThread 시작");
            setName("ConnectThread" + mSocketType);

            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, mSocketType + " 소켓을 닫는데 실패", e2);
                }
                connectionFailed();
                return;
            }

            synchronized (BluetoothChatService.this) {
                mConnectThread = null;
            }

            connected(mmSocket, mmDevice, mSocketType);
        }

        public  void cancel(){

            Log.d(TAG, mSocketType+"소켓을 닫습니다 "+this);
            try{
                mmSocket.close();
            } catch (IOException e){
                Log.e(TAG, "서버의 "+mSocketType+"소켓을 닫는데 실패!", e);
            }
        }
    }


    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInstream;
        private final OutputStream mmOutstream;

        public ConnectedThread(BluetoothSocket socket, String socketType){
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e){
                Log.e(TAG, "임시 소켓 생성 실패", e);
            }
            mmInstream = tmpIn;
            mmOutstream = tmpOut;
            mState = STATE_CONNECTED;
        }

        public void run() {
            Log.i(TAG, "mConnectedThread 시작");
            byte[] buffer = new byte[1024];
            int bytes;

            while(mState == STATE_CONNECTED){
                try{
                    bytes = mmInstream.read(buffer);
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e){
                    Log.e(TAG, "연결이 끊겼다헤", e);
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer){
            try {
                mmOutstream.write(buffer);
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e){
                Log.e(TAG, "데이터를 쓰는 중에 예외상황 발새애앵", e);
            }
        }

        public  void cancel(){

            try{
                mmSocket.close();
            } catch (IOException e){
                Log.e(TAG, "연결된 소켓을 닫는데 실패!", e);
            }
        }
    }




}
