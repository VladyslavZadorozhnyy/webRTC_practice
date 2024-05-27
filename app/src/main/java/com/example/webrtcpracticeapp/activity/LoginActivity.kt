package com.example.webrtcpracticeapp.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.webrtcpracticeapp.databinding.ActivityLoginBinding
import com.example.webrtcpracticeapp.repository.MainRepository
import com.example.webrtcpracticeapp.utils.SuccessCallback
import com.google.firebase.FirebaseApp
import com.permissionx.guolindev.PermissionX

class LoginActivity : AppCompatActivity() {
    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        init()
    }

    private fun init() {
        binding.enterBtn.setOnClickListener {
            PermissionX.init(this)
                .permissions(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
                .request { allGranted: Boolean, _: List<String?>?, _: List<String?>? ->
                    if (allGranted) {
                        MainRepository.login(binding.username.text.toString(), applicationContext, object : SuccessCallback {
                            override fun onSuccess() {
                                startActivity(Intent(this@LoginActivity, CallActivity::class.java)
                                )
                            }
                        })
                    }
                }
        }
    }
}