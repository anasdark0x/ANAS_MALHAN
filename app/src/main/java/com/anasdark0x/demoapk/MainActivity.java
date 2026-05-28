package com.anasdark0x.demoapk;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(14, 18, 28));

        TextView title = new TextView(this);
        title.setText("Anas APK Demo");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("تم بناء هذا التطبيق تلقائياً عبر GitHub Actions من الموبايل.");
        subtitle.setTextColor(Color.rgb(210, 220, 235));
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 24, 0, 24);

        Button button = new Button(this);
        button.setText("جرّبني");
        button.setTextSize(18);
        button.setOnClickListener(v -> Toast.makeText(this, "التطبيق شغال يا معلم ✅", Toast.LENGTH_SHORT).show());

        root.addView(title);
        root.addView(subtitle);
        root.addView(button);
        setContentView(root);
    }
}
