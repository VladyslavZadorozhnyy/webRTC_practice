package com.example.webrtcpracticeapp.client

import android.content.Context
import com.example.webrtcpracticeapp.utils.DataModel
import com.example.webrtcpracticeapp.utils.DataModelType
import com.example.webrtcpracticeapp.webrtc.SdpObserverImpl
import com.google.gson.Gson
import org.webrtc.*
import org.webrtc.PeerConnectionFactory.InitializationOptions

class WebRTCClient(
    private var context: Context,
    private var observer: PeerConnection.Observer,
    private var username: String,
) {
    private val gson = Gson()
    var listener: Listener? = null

    private val eglBaseContext = EglBase.create().eglBaseContext

    private var peerConnection: PeerConnection? = null
    private var iceServer: PeerConnection.IceServer
    private var peerConnectionFactory: PeerConnectionFactory

    private var videoCapturer: CameraVideoCapturer? = null
    private var localVideoSource: VideoSource
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource

    private val localTrackId = "local_track"
    private val localStreamId = "local_stream"
    private var localMediaStream: MediaStream? = null
    private var mediaConstraints: MediaConstraints = MediaConstraints()

    init {
        initPeerConnectionFactory()
        peerConnectionFactory = createPeerConnectionFactory()!!
        iceServer =  PeerConnection.IceServer
            .builder("turn:a.relay.metered.ca:443?transport=tcp")
            .setUsername("83eebabf8b4cce9d5dbcb649")
            .setPassword("2D7JvfkOQtBdYW3R").createIceServer()

        peerConnection = createPeerConnection(observer)
        localVideoSource = peerConnectionFactory.createVideoSource(false)
        localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    private fun initPeerConnectionFactory() {
        val options = InitializationOptions.builder(context)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory? {
        val options = PeerConnectionFactory.Options()
        options.disableEncryption = false
        options.disableNetworkMonitor = false
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setOptions(options).createPeerConnectionFactory()
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(listOf(iceServer), observer)
    }

    fun initLocalSurfaceView(view: SurfaceViewRenderer) {
        initSurfaceViewRenderer(view)
        startLocalVideoStreaming(view)
    }

    private fun startLocalVideoStreaming(view: SurfaceViewRenderer) {
        val helper = SurfaceTextureHelper.create(
            Thread.currentThread().name, eglBaseContext
        )
        videoCapturer = getVideoCapturer()
        videoCapturer?.initialize(helper, context, localVideoSource.capturerObserver)
        videoCapturer?.startCapture(480, 360, 15)
        localVideoTrack = peerConnectionFactory.createVideoTrack(
            localTrackId + "_video", localVideoSource
        )
        localVideoTrack?.addSink(view)
        localAudioTrack =
            peerConnectionFactory.createAudioTrack(localTrackId + "_audio", localAudioSource)
        localMediaStream = peerConnectionFactory.createLocalMediaStream(localStreamId)
        localMediaStream?.addTrack(localVideoTrack)
        localMediaStream?.addTrack(localAudioTrack)
        peerConnection!!.addStream(localMediaStream)
    }

    private fun getVideoCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        for (device in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(device))
                return enumerator.createCapturer(device, null)
        }
        throw IllegalStateException("front facing camera not found")
    }

    fun initSurfaceViewRenderer(viewRenderer: SurfaceViewRenderer) {
        viewRenderer.setEnableHardwareScaler(true)
        viewRenderer.setMirror(true)
        viewRenderer.init(eglBaseContext, null)
    }

    fun initRemoteSurfaceView(view: SurfaceViewRenderer) { initSurfaceViewRenderer(view) }

    fun call(target: String?) {
        try {
            peerConnection?.createOffer(object : SdpObserverImpl() {
                override fun onCreateSuccess(description: SessionDescription?) {
                    super.onCreateSuccess(description)
                    peerConnection?.setLocalDescription(object : SdpObserverImpl() {
                        override fun onSetSuccess() {
                            super.onSetSuccess()
                            target?.let {
                                listener?.onTransferDataToOtherPeer(
                                    DataModel(
                                        it,
                                        username,
                                        description?.description,
                                        DataModelType.Offer
                                    )
                                )
                            }
                        }
                    }, description)
                }
            }, mediaConstraints)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun answer(target: String) {
        try {
            peerConnection?.createAnswer(object : SdpObserverImpl() {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    super.onCreateSuccess(sessionDescription)
                    peerConnection?.setLocalDescription(object : SdpObserverImpl() {
                        override fun onSetSuccess() {
                            super.onSetSuccess()
                            listener?.onTransferDataToOtherPeer(
                                DataModel(
                                    target,
                                    username,
                                    sessionDescription?.description,
                                    DataModelType.Answer
                                )
                            )
                        }
                    }, sessionDescription)
                }
            }, mediaConstraints)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpObserverImpl(), sessionDescription)
    }

    fun addIceCandidate(candidate: IceCandidate) { peerConnection?.addIceCandidate(candidate) }

    fun sendIceCandidate(iceCandidate: IceCandidate, target: String) {
        addIceCandidate(iceCandidate)
        listener?.onTransferDataToOtherPeer(
            DataModel(
                target,
                username,
                gson.toJson(iceCandidate),
                DataModelType.IceCandidate,
            )
        )
    }

    fun switchCamera() { videoCapturer?.switchCamera(null) }

    fun toggleVideo(muted: Boolean) { localVideoTrack?.setEnabled(muted) }

    fun toggleAudio(muted: Boolean) { localAudioTrack?.setEnabled(muted) }

    fun closeConnection() {
        try {
            localVideoTrack?.dispose()
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            peerConnection?.close()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    interface Listener {
        fun onTransferDataToOtherPeer(data: DataModel)
    }
}