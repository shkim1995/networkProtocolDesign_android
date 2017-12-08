package com.teamverman.week4_activity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;


public class BluetoothChatFragment extends Fragment {

    private static final String TAG = "BlueToothChatFrag";

    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    private String mConnectedDeviceName = null;
    private ArrayAdapter<String> mConversationArrayAdapter;
    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothChatService mChatService = null;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(mBluetoothAdapter==null){
            //FragmentActivity
            Activity activity = getActivity();
            Toast.makeText(activity, "블루투스를 지원하지 않는 기기입니다", Toast.LENGTH_SHORT).show();
            activity.finish();
        }
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.secure_connect_scan:{
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.discoverable:{
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

    public void onStart(){
        super.onStart();

        if(!mBluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else if(mChatService == null){
            setupChat();
        }
    }

    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    public void onViewCreated(View view, @Nullable Bundle savedInstanceState){
        mConversationView = (ListView)view.findViewById(R.id.in);
        mOutEditText = (EditText)view.findViewById(R.id.edit_text_out);
        mSendButton = (Button)view.findViewById(R.id.button_send);
    }

    private void ensureDiscoverable(){
        if(mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data){
        switch(requestCode){

            case REQUEST_CONNECT_DEVICE_SECURE:
                if(resultCode == Activity.RESULT_OK){
                    connectDevice(data, true);
                }
                break;

            case REQUEST_ENABLE_BT:
                if(resultCode == Activity.RESULT_OK){
                    setupChat();
                } else{
                    Log.e(TAG, "블루투스가 활성화 되지 않았습니다.");
                    Toast.makeText(getActivity(), "블루투스가 활성화 되지 않았습니다.", Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }
    private void setStatus(int resId){
        //FragmentActivity -> Activity
        Activity activity = getActivity();
        if(null == activity){
            return;
        }

        final android.app.ActionBar actionBar = activity.getActionBar();
        if(actionBar == null){
            return;
        }
        actionBar.setSubtitle(resId);
    }

    private void setStatus(CharSequence subTitle){
        //FragmentActivity
        Activity activity = getActivity();
        if(null==activity){
            return ;
        }
        final android.app.ActionBar actionBar = activity.getActionBar();
        if(actionBar == null){
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    private  void connectDevice(Intent data, boolean secure) {
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        mChatService.connect(device, secure);
    }

    private  void setupChat(){
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);
        if(mConversationArrayAdapter==null) Log.e("SADSDSD", "ASDSAD");
        mConversationView.setAdapter(mConversationArrayAdapter);

        mOutEditText.setOnEditorActionListener(mWriteListener);

        mSendButton.setOnClickListener( new View.OnClickListener(){
            public void onClick(View v){
                View view = getView();
                if(null != view){
                    TextView tetView = (TextView)view.findViewById(R.id.edit_text_out);
                    String message = tetView.getText().toString();
                    sendMessage(message);
                }
            }
        });
        mChatService = new BluetoothChatService(getActivity(), mHandler);
        mOutStringBuffer = new StringBuffer("");
    }

    private void sendMessage(String msg){
        if(mChatService.getState() != BluetoothChatService.STATE_CONNECTED){
            Toast.makeText(getActivity(), "연결된 기기 없음", Toast.LENGTH_SHORT).show();
            return;
        }

        if(msg.length()>0){
            byte[] send = msg.getBytes();
            mChatService.write(send);

            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener(){
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event){
            if(actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP){
                String msg = view.getText().toString();
                sendMessage(msg);
            }
            return true;
        }
    };

    private final Handler mHandler = new Handler(){
        public void handleMessage(Message msg){
            //FragmentActivity
            Activity activity = getActivity();
            switch (msg.what){
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1){
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(mConnectedDeviceName+"에 연결된듯");
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus("연결중...");
                            break;
                        case BluetoothChatService.STATE_LISTEN:{}
                        case BluetoothChatService.STATE_NONE:
                            setStatus("연결 없음");
                            break;
                    }
                    break;

                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[])msg.obj;
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("나:  "+writeMessage);
                    break;

                case MESSAGE_READ:
                    byte[] readBuf = (byte[])msg.obj;
                    String readMsg = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName+": "+readMsg);
                    break;

                case MESSAGE_DEVICE_NAME:

                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    if(null != activity){
                        Toast.makeText(activity, mConnectedDeviceName+"에 연결된듯", Toast.LENGTH_SHORT).show();

                    }
                    break;

                case MESSAGE_TOAST:
                    if(null != activity){
                        Toast.makeText(activity, msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();

                    }
                    break;
            }
        }
    };

    public void onDestroy(){
        super.onDestroy();
        if(mChatService != null){
            mChatService.stop();
        }
    }

    public void onResume(){
        super.onResume();

        if(mChatService != null){
            if(mChatService.getState() == BluetoothChatService.STATE_NONE){
                mChatService.start();
            }
        }
    }
}
