package com.nikoyuwono.toolbarpanel.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.nikoyuwono.toolbarpanel.ToolbarPanelLayout;

public class SampleActivity extends Activity {

    private ToolbarPanelLayout toolbarPanelLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);
        toolbarPanelLayout = (ToolbarPanelLayout) findViewById(
                R.id.sliding_down_toolbar_layout);
        TextView openButton = (TextView) findViewById(R.id.open_button);
        TextView closeButton = (TextView) findViewById(R.id.close_button);

        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarPanelLayout.openPanel();
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarPanelLayout.closePanel();
            }
        });
    }
}
