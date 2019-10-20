package com.kircherelectronics.gyroscopeexplorer.activity;

import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.kircherelectronics.gyroscopeexplorer.R;

public class DasboardActivity extends AppCompatActivity {
    private ImageView btnTitan;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dasboard);

       btnTitan = findViewById(R.id.btntitan);
       btnTitan.setOnClickListener(new View.OnClickListener(){
           public void onClick (View v ){

           }

       });


    }
}
