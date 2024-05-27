package com.example.webrtcpracticeapp.utils

class DataModel(
    var target: String,
    var sender: String,
    var data: String?,
    type: DataModelType
) {
    private var type: DataModelType

    init {
        this.type = type
    }

    fun getType(): DataModelType {
        return type
    }

    fun setType(type: DataModelType) {
        this.type = type
    }
}