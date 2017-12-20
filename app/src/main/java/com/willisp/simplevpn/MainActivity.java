package com.willisp.simplevpn;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST = 0X0F;
    private static final String TAG = "SimpleVPN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn = (Button) findViewById(R.id.button);
        Log.i(TAG, "starting VPN");
        btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                changeVPNStatus();
            }
        });
    }

    private void changeVPNStatus() {
        Intent vpnIntent =SimpleVPNService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST);
        else
            onActivityResult(VPN_REQUEST, RESULT_OK, null);
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        if (request == VPN_REQUEST && result == RESULT_OK)
            startService(new Intent(this, SimpleVPNService.class));
    }
}
