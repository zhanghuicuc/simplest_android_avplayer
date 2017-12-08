/**
 * this samples shows how to use MediaCodec and AudioTrack to build an android player, with avsync optimization
 * Author:
 * zhang hui <zhanghui9@le.com;zhanghuicuc@gmail.com>
 * LeEco BSP Multimedia / Communication University of China
 */
package com.example.zhanghui.avplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Button mPlayButton;
    private EditText mUrlEditText;
    private static final String DEFAULT_FILE_URL = "/sdcard/Sync-One2-Test-1080p-24-H_264_V.mp4";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUrlEditText = (EditText) findViewById(R.id.input_url_editText);
        mPlayButton = (Button) findViewById(R.id.play_button);
        mPlayButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
            String fileUrl = mUrlEditText.getText().toString().trim();
            if (fileUrl.equals("")) {
                Toast.makeText(MainActivity.this, "file url is null, will use default url", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class).setData(Uri.parse(DEFAULT_FILE_URL));
                startActivity(intent);
            } else {
                Intent intent = new Intent(getApplicationContext(), PlayerActivity.class).setData(Uri.parse(fileUrl));
                startActivity(intent);
            }
            }
        });
    }
}
