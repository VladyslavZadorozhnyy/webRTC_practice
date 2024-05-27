package com.example.webrtcpracticeapp.repository

import android.content.Context
import android.util.Log
import com.example.webrtcpracticeapp.client.FirebaseClient
import com.example.webrtcpracticeapp.client.WebRTCClient
import com.example.webrtcpracticeapp.utils.*
import com.example.webrtcpracticeapp.webrtc.MyPeerConnectionObserver
import com.google.gson.Gson
import org.webrtc.*
import org.webrtc.PeerConnection.PeerConnectionState

object MainRepository : WebRTCClient.Listener {
    var listener: Listener? = null
    private val gson = Gson()

    private var firebaseClient = FirebaseClient()

    private lateinit var webRTCClient: WebRTCClient

    private var currentUsername: String? = null

    private var remoteView: SurfaceViewRenderer? = null

    private var target: String = ""

    private fun updateCurrentUsername(username: String) { currentUsername = username }

    fun login(username: String, context: Context, callBack: SuccessCallback) {
        firebaseClient.login(context, username, object : SuccessCallback {
            override fun onSuccess() {
                updateCurrentUsername(username)
                webRTCClient = WebRTCClient(context, object : MyPeerConnectionObserver() {
                    override fun onAddStream(mediaStream: MediaStream) {
                        super.onAddStream(mediaStream)
                        try {
                            mediaStream.videoTracks[0].addSink(remoteView)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConnectionChange(newState: PeerConnectionState) {
                        Log.d("TAG", "onConnectionChange: $newState")
                        super.onConnectionChange(newState)
                        listener?.webrtcConnected()

                        if (newState == PeerConnectionState.CLOSED ||
                            newState == PeerConnectionState.DISCONNECTED
                        ) {
                            listener?.webrtcClosed()
                        }
                    }

                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                        super.onIceCandidate(iceCandidate)
                        webRTCClient.sendIceCandidate(iceCandidate, target)
                    }
                }, username)
                webRTCClient.listener = this@MainRepository
                callBack.onSuccess()
            }
        })
    }

    fun initLocalView(view: SurfaceViewRenderer) {
        webRTCClient.initLocalSurfaceView(view)
    }

    fun initRemoteView(view: SurfaceViewRenderer) {
        webRTCClient.initRemoteSurfaceView(view)
        remoteView = view
    }

    fun startCall(target: String?) {
        webRTCClient.call(target)
    }

    fun switchCamera() {
        webRTCClient.switchCamera()
    }

    fun toggleAudio(shouldBeMuted: Boolean) {
        webRTCClient.toggleAudio(shouldBeMuted)
    }

    fun toggleVideo(shouldBeMuted: Boolean) {
        webRTCClient.toggleVideo(shouldBeMuted)
    }

    fun sendCallRequest(target: String?, errorCallBack: ErrorCallback) {
        firebaseClient.sendMessageToOtherUser(
            DataModel(
                target!!,
                currentUsername!!,
                null,
                DataModelType.StartCall
            ), errorCallBack
        )
    }

    fun endCall() {
        webRTCClient.closeConnection()
    }

    fun subscribeForLatestEvent(callBack: NewEventCallback) {
        firebaseClient.observeIncomingLatestEvent(
            object : NewEventCallback {
                override fun onNewEventReceived(model: DataModel?) {
                    if (model == null) return

                    when (model.getType()) {
                        DataModelType.Offer -> {
                            target = model.sender
                            webRTCClient.onRemoteSessionReceived(
                                SessionDescription(SessionDescription.Type.OFFER, model.data)
                            )
                            webRTCClient.answer(model.sender)
                        }
                        DataModelType.Answer -> {
                            target = model.sender
                            webRTCClient.onRemoteSessionReceived(
                                SessionDescription(
                                    SessionDescription.Type.ANSWER, model.data
                                )
                            )
                        }
                        DataModelType.IceCandidate -> try {
                            val candidate: IceCandidate =
                                gson.fromJson(model.data, IceCandidate::class.java)
                            webRTCClient.addIceCandidate(candidate)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        DataModelType.StartCall -> {
                            target = model.sender
                            callBack.onNewEventReceived(model)
                        }
                    }
                }
            }
        )
    }

    override fun onTransferDataToOtherPeer(data: DataModel) {
        firebaseClient.sendMessageToOtherUser(data, object : ErrorCallback {
            override fun onError() {}
        })
    }

    interface Listener {
        fun webrtcConnected()
        fun webrtcClosed()
    }
}