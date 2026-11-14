package com.example.data.network

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?
): String {
    val authObj = JSONObject()
    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    if (currentUser != null) {
        authObj.put("userId", currentUser.uid)
        authObj.put("email", currentUser.email ?: JSONObject.NULL)
        authObj.put("emailVerified", currentUser.isEmailVerified)
        authObj.put("isAnonymous", currentUser.isAnonymous)
        val providersArr = JSONArray()
        currentUser.providerData.forEach { info ->
            val pObj = JSONObject()
            pObj.put("providerId", info.providerId)
            pObj.put("displayName", info.displayName ?: JSONObject.NULL)
            pObj.put("email", info.email ?: JSONObject.NULL)
            pObj.put("photoUrl", info.photoUrl?.toString() ?: JSONObject.NULL)
            providersArr.put(pObj)
        }
        authObj.put("providerInfo", providersArr)
    } else {
        authObj.put("userId", JSONObject.NULL)
        authObj.put("providerInfo", JSONArray())
    }

    val errorObj = JSONObject()
    errorObj.put("error", exception.message ?: exception.toString())
    errorObj.put("operationType", operationType.name.lowercase())
    errorObj.put("path", path ?: JSONObject.NULL)
    errorObj.put("authInfo", authObj)

    val jsonString = errorObj.toString()
    runCatching { Log.e("FirestoreError", jsonString, exception) }
    return jsonString
}
