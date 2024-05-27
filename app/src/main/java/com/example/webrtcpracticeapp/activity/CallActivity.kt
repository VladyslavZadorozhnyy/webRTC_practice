package com.example.webrtcpracticeapp.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.webrtcpracticeapp.R
import com.example.webrtcpracticeapp.databinding.ActivityCallBinding
import com.example.webrtcpracticeapp.repository.MainRepository
import com.example.webrtcpracticeapp.utils.DataModel
import com.example.webrtcpracticeapp.utils.DataModelType
import com.example.webrtcpracticeapp.utils.ErrorCallback
import com.example.webrtcpracticeapp.utils.NewEventCallback

class CallActivity : AppCompatActivity(), MainRepository.Listener {
    private val binding by lazy { ActivityCallBinding.inflate(layoutInflater) }

    private var isCameraMuted = false
    private var isMicrophoneMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        init()
    }

    private fun init() {
        binding.callBtn.setOnClickListener { v ->
            MainRepository.sendCallRequest(binding.targetUserNameEt.text.toString(), object : ErrorCallback {
                override fun onError() {
                    Toast.makeText(
                        this@CallActivity,
                        "couldn\'t find the target",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            })
        }
        MainRepository.initLocalView(binding.localView)
        MainRepository.initRemoteView(binding.remoteView)
        MainRepository.listener = this
        MainRepository.subscribeForLatestEvent(object: NewEventCallback {
            override fun onNewEventReceived(data: DataModel?) {
                if (data == null) return

                if (data.getType() == DataModelType.StartCall) {
                    runOnUiThread {
                        binding.incomingNameTV.text = data.sender + " is calling you"
                        binding.incomingCallLayout.visibility = View.VISIBLE
                        binding.acceptButton.setOnClickListener { v ->
                            MainRepository.startCall(data.sender)
                            binding.incomingCallLayout.visibility = View.GONE
                        }
                        binding.rejectButton.setOnClickListener { v ->
                            binding.incomingCallLayout.visibility = View.GONE
                        }
                    }
                }
            }
        })
        binding.switchCameraButton.setOnClickListener { MainRepository.switchCamera() }
        binding.micButton.setOnClickListener { v ->
            val micButtonIcon = if (isMicrophoneMuted) R.drawable.ic_baseline_mic_off_24 else R.drawable.ic_baseline_mic_24
            binding.micButton.setImageResource(micButtonIcon)

            MainRepository.toggleAudio(isMicrophoneMuted)
            isMicrophoneMuted = !isMicrophoneMuted
        }

        binding.videoButton.setOnClickListener {
            if (isCameraMuted) {
                binding.videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24)
            } else {
                binding.videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
            }
            MainRepository.toggleVideo(isCameraMuted)
            isCameraMuted = !isCameraMuted
        }

        binding.endCallButton.setOnClickListener { v ->
            MainRepository.endCall()
            finish()
        }
    }

    override fun webrtcConnected() {
        runOnUiThread {
            binding.incomingCallLayout.visibility = View.GONE
            binding.whoToCallLayout.visibility = View.GONE
            binding.callLayout.visibility = View.VISIBLE
        }
    }

    override fun webrtcClosed() {
        runOnUiThread { finish() }
    }
}