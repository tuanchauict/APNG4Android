package com.github.penfeizhou.animation.demo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.penfeizhou.animation.apng.APNGDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class ApngActivity : AppCompatActivity() {
    private var job: Job? = null

    @SuppressLint("SetTextI18n")
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

        val indicatorView = findViewById<Button>(R.id.indicator)
        indicatorView.setOnClickListener {
            Runtime.getRuntime().gc()
        }
        job = CoroutineScope(Dispatchers.Main).launch {
            flow {
                while (true) {
                    delay(200)
                    emit(Debug.getNativeHeapFreeSize() to Debug.getNativeHeapSize())
                }
            }
                .flowOn(Dispatchers.Default)
                .collect { (free, total) ->
                    indicatorView.text = "${formatNumber(total - free)} / ${formatNumber(total)}"
                }
        }
    }

    private fun formatNumber(value: Long): String =
        "$value".reversed().chunked(3).joinToString(",").reversed()

    override fun onDestroy() {
        job?.cancel()
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
