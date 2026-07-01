package com.example.offlineupi

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Fade in + subtle slide up animation
        val logo = findViewById<android.widget.ImageView>(R.id.splash_logo)
        val title = findViewById<android.widget.TextView>(R.id.splash_title)

        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 600; startOffset = 200 }
        val slideUp = TranslateAnimation(0f, 0f, 24f, 0f).apply { duration = 600; startOffset = 200 }

        logo.startAnimation(AnimationSet(true).apply {
            addAnimation(fadeIn); addAnimation(slideUp)
        })
        title.startAnimation(fadeIn)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1800)
    }
}
