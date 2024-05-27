package com.example.webrtcpracticeapp.client

import android.content.Context
import com.example.webrtcpracticeapp.utils.*
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import com.google.gson.Gson
import java.util.*

class FirebaseClient {
    private val gson = Gson()
    private lateinit var dbRef: DatabaseReference
    private var currentUsername: String? = null

    fun login(context: Context, username: String?, callBack: SuccessCallback) {
        FirebaseApp.initializeApp(context)
        dbRef = FirebaseDatabase.getInstance().reference
        dbRef.child(username!!).setValue("").addOnCompleteListener { task: Task<Void?>? ->
            currentUsername = username
            callBack.onSuccess()
        }
    }

    fun sendMessageToOtherUser(dataModel: DataModel, errorCallBack: ErrorCallback) {
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child(dataModel.target).exists()) {
                    dbRef.child(dataModel.target)
                        .child(LATEST_EVENT_FIELD_NAME)
                        .setValue(gson.toJson(dataModel))
                } else {
                    errorCallBack.onError()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                errorCallBack.onError()
            }
        })
    }

    fun observeIncomingLatestEvent(callBack: NewEventCallback) {
        dbRef.child(currentUsername!!).child(LATEST_EVENT_FIELD_NAME).addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val data = Objects.requireNonNull(snapshot.value).toString()
                        val dataModel: DataModel = gson.fromJson(data, DataModel::class.java)
                        callBack.onNewEventReceived(dataModel)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
        )
    }

    companion object {
        private const val LATEST_EVENT_FIELD_NAME = "latest_event"
    }
}