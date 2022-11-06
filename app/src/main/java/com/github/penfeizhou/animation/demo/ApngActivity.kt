package com.github.penfeizhou.animation.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.penfeizhou.animation.apng.APNGDrawable

class ApngActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apng)

        val imageView = findViewById<ImageView>(R.id.imageView)
        val drawable = APNGDrawable.fromAsset(this, "demo.png")
        imageView.setImageDrawable(drawable)

        findViewById<View>(R.id.btnStart).setOnClickListener {
            drawable.start()
        }

        findViewById<View>(R.id.btnStop).setOnClickListener {
            drawable.stop()
        }

        findViewById<View>(R.id.btnPause).setOnClickListener {
            drawable.pause()
        }

        findViewById<View>(R.id.btnResume).setOnClickListener {
            drawable.resume()
        }

        findViewById<View>(R.id.btnReset).setOnClickListener {
            drawable.reset()
        }
    }

    override fun onDestroy() {
        val imageView = findViewById<ImageView>(R.id.imageView)
        (imageView.drawable as? APNGDrawable)?.stop()
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, ApngActivity::class.java)
            context.startActivity(intent)
        }
    }
}