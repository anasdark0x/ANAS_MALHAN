package com.example.anastips;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSplash();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                showHome();
            }
        }, 2500);
    }

    private void showSplash() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(15, 23, 42));

        TextView title = new TextView(this);
        title.setText("anas malhan");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        root.addView(title, params);
        setContentView(root);
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 36);
        root.setBackgroundColor(Color.rgb(241, 245, 249));
        scroll.addView(root);

        TextView header = new TextView(this);
        header.setText("لوحة النصائح التعليمية");
        header.setTextColor(Color.rgb(15, 23, 42));
        header.setTextSize(28);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.RIGHT);
        root.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText("أهلاً بك يا anas malhan — هذه بعض النصائح المفيدة للدراسة والتعلم.");
        subtitle.setTextColor(Color.rgb(71, 85, 105));
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, 10, 0, 24);
        root.addView(subtitle);

        String[] tips = new String[] {
                "حدد هدفاً صغيراً قبل كل جلسة دراسة.",
                "راجع الدرس بعد الانتهاء منه مباشرة لمدة خمس دقائق.",
                "اكتب الملاحظات بيدك أو بأسلوبك الخاص لتثبيت المعلومة.",
                "خذ استراحة قصيرة كل 25 إلى 30 دقيقة.",
                "استخدم الأمثلة العملية بدل الحفظ فقط.",
                "نم جيداً، فالتركيز يبدأ من الراحة.",
                "اختبر نفسك بأسئلة قصيرة بعد كل موضوع."
        };

        for (int i = 0; i < tips.length; i++) {
            TextView card = new TextView(this);
            card.setText((i + 1) + ". " + tips[i]);
            card.setTextSize(18);
            card.setTextColor(Color.rgb(30, 41, 59));
            card.setGravity(Gravity.RIGHT);
            card.setPadding(24, 22, 24, 22);
            card.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 16);
            root.addView(card, cardParams);
        }

        setContentView(scroll);
    }
}
