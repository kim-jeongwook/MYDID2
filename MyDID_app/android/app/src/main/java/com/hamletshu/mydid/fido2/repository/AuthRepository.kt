/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hamletshu.mydid.fido2.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.hamletshu.mydid.fido2.api.ApiException
import com.hamletshu.mydid.fido2.api.AuthApi
import com.hamletshu.mydid.fido2.api.Credential
import com.hamletshu.mydid.fido2.toBase64
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.Fido2ApiClient
import com.google.android.gms.fido.fido2.Fido2PendingIntent
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAttestationResponse
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 싱글톤 패턴으로 뷰모델마다 동일한 Repository 인스턴스로 접근하여 데이터를 로드하도록 도와줍
 * API, 로컬 데이터 저장소 및 FIDO2 API와 함께 작동합니다.
 * Works with the API, the local data store, and FIDO2 API.
 */
class AuthRepository(
    private val api: AuthApi,
    private val prefs: SharedPreferences,
    private val executor: Executor
) {

    companion object {
        private const val TAG = "AuthRepository"

        //SharedPreferences의 키
        // Keys for SharedPreferences
        private const val PREFS_NAME = "auth"
        private const val PREF_USERNAME = "username"
        private const val PREF_TOKEN = "token"
        private const val PREF_CREDENTIALS = "credentials"
        private const val PREF_LOCAL_CREDENTIAL_ID = "local_credential_id"

        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(
                    AuthApi(),
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                    Executors.newFixedThreadPool(64)
                ).also { instance = it }
            }
        }
    }

    private var fido2ApiClient: Fido2ApiClient? = null

    fun setFido2APiClient(client: Fido2ApiClient?) {
        fido2ApiClient = client
    }

    private val signInStateListeners = mutableListOf<(SignInState) -> Unit>()

    /**
     * 요청과 응답 API 사이에 기억해야 할 일시적인 challenge를 저장합니다
     * 자격 증명 등록 및 로그인을 요청합니다.
     * Stores a temporary challenge that needs to be memorized between request and response API
     * calls for credential registration and sign-in.
     */
    private var lastKnownChallenge: String? = null

    private fun invokeSignInStateListeners(state: SignInState) {
        val listeners = signInStateListeners.toList() // Copy
        for (listener in listeners) {
            listener(state)
        }
    }

    /**
     * 사용자의 현재 로그인 상태를 반환합니다. UI는 이를 사용하여 화면을 탐색합니다.
     * Returns the current sign-in state of the user. The UI uses this to navigate between screens.
     */
    fun getSignInState(): LiveData<SignInState> {
        return object : LiveData<SignInState>() {

            private val listener = { state: SignInState ->
                postValue(state)
            }

            init {
                val username = prefs.getString(PREF_USERNAME, null)
                val token = prefs.getString(PREF_TOKEN, null)
                value = when {
                    username.isNullOrBlank() -> SignInState.SignedOut
                    token.isNullOrBlank() -> SignInState.SigningIn(username)
                    else -> SignInState.SignedIn(username, token)
                }
            }

            override fun onActive() {
                signInStateListeners.add(listener)
            }

            override fun onInactive() {
                signInStateListeners.remove(listener)
            }
        }
    }

    /**
     * * 사용자 이름을 서버로 보냅니다. 성공하면 로그인 상태가 진행됩니다.
     * Sends the username to the server. If it succeeds, the sign-in state will proceed to
     * [SignInState.SigningIn].
     */
    fun username(username: String, sending: MutableLiveData<Boolean>) {
        executor.execute {
            sending.postValue(true)
            try {
                val result = api.username(username)
                prefs.edit(commit = true) {
                    putString(PREF_USERNAME, result)
                }
                invokeSignInStateListeners(SignInState.SigningIn(username))
            } finally {
                sending.postValue(false)
            }
        }
    }

    /**
     *
     * 비밀번호로 로그인하십시오. 로그인 상태가 다음과 같은 경우에만 호출해야합니다.
     * [SignInState.SigningIn]. 성공하면 로그인 상태가 진행됩니다.
     * Signs in with a password. This should be called only when the sign-in state is
     * [SignInState.SigningIn]. If it succeeds, the sign-in state will proceed to
     * [SignInState.SignedIn].
     *
     * @param processing API 호출이 진행되는 동안이 값은 'true'로 설정됩니다.
     * @param processing The value is set to `true` while the API call is ongoing.
     */
    fun password(password: String, processing: MutableLiveData<Boolean>) {
        executor.execute {
            processing.postValue(true)
            val username = prefs.getString(PREF_USERNAME, null)!!
            try {
                val returnData = api.password(username, password)
                val token = returnData.get(0)
                val username2 = returnData.get(1)
                prefs.edit(commit = true) { putString(PREF_TOKEN, token) }
                prefs.edit(commit = true) { putString(PREF_USERNAME, username2) }
                invokeSignInStateListeners(SignInState.SignedIn(username, token))
            } catch (e: ApiException) {
                Log.e(TAG, "로그인 자격 증명이 잘못 되었습니다.", e)

                // start login over again
                prefs.edit(commit = true) {
                    remove(PREF_USERNAME)
                    remove(PREF_TOKEN)
                    remove(PREF_CREDENTIALS)
                }

                invokeSignInStateListeners(
                    SignInState.SignInError(e.message ?: "사용자 로그인에 실패했습니다." ))
            } finally {
                processing.postValue(false)
            }
        }
    }

    /**
     *이 사용자가 서버에 등록한 자격 증명 목록을 검색합니다. 이것은
     * 로그인 상태가 [SignInState.SignedIn] 인 경우에만 호출됩니다.
     * Retrieves the list of credential this user has registered on the server. This should be
     * called only when the sign-in state is [SignInState.SignedIn].
     */
    fun getCredentials(): LiveData<List<Credential>> {
        executor.execute {
            //            refreshCredentials()
        }
        return Transformations.map(prefs.liveStringSet(PREF_CREDENTIALS, emptySet())) { set ->
            parseCredentials(set)
        }
    }

    @WorkerThread
    private fun refreshCredentials() {
        val token = prefs.getString(PREF_TOKEN, null)!!
        prefs.edit(commit = true) {
            putStringSet(PREF_CREDENTIALS, api.getKeys(token).toStringSet())
        }
    }

    private fun List<Credential>.toStringSet(): Set<String> {
        return mapIndexed { index, credential ->
            "$index;${credential.id};${credential.publicKey}"
        }.toSet()
    }

    private fun parseCredentials(set: Set<String>): List<Credential> {
        return set.map { s ->
            val (index, id, publicKey) = s.split(";")
            index to Credential(id, publicKey)
        }.sortedBy { (index, _) -> index }
            .map { (_, credential) -> credential }
    }

    /**
     * 로그인 토큰을 지웁니다. 로그인 상태는 [SignInState.SigningIn]으로 진행됩니다.
     * Clears the sign-in token. The sign-in state will proceed to [SignInState.SigningIn].
     */
    fun clearToken() {
        executor.execute {
            val username = prefs.getString(PREF_USERNAME, null)!!
            prefs.edit(commit = true) {
                remove(PREF_TOKEN)
                remove(PREF_CREDENTIALS)
            }
            invokeSignInStateListeners(SignInState.SigningIn(username))
        }
    }

    /**
     * 모든 로그인 정보를 지 웁니다. 로그인 상태는 [SignInState.SignedOut].
     * Clears all the sign-in information. The sign-in state will proceed to
     * [SignInState.SignedOut].
     */
    fun signOut() {
        executor.execute {
            prefs.edit(commit = true) {
                remove(PREF_USERNAME)
                remove(PREF_TOKEN)
                remove(PREF_CREDENTIALS)
            }
            invokeSignInStateListeners(SignInState.SignedOut)
        }
    }

    /**
     * 서버에 새 자격 증명을 등록하기 시작합니다. 이 경우에만 호출해야합니다
     * 로그인 상태는 [SignInState.SignedIn]입니다.
     * Starts to register a new credential to the server. This should be called only when the
     * sign-in state is [SignInState.SignedIn].
     */
    fun registerRequest(processing: MutableLiveData<Boolean>): LiveData<Fido2PendingIntent> {
        val result = MutableLiveData<Fido2PendingIntent>()
        executor.execute {
            fido2ApiClient?.let { client ->
                processing.postValue(true)
                try {
                    val token = prefs.getString(PREF_TOKEN, null)!!
                    val username = prefs.getString(PREF_USERNAME, null)!!
                    val (options, challenge) = api.registerRequest(token, username)
                    lastKnownChallenge = challenge
                    val task: Task<Fido2PendingIntent> = client.getRegisterIntent(options)
                    result.postValue(Tasks.await(task))
                } catch (e: Exception) {
                    Log.e(TAG, "registerRequest를 호출할 수 없습니다.", e)
                } finally {
                    processing.postValue(false)
                }
            }
        }
        return result
    }

    /**
     * 서버에 새 자격 증명 등록을 마칩니다. 이 후에 만 ​​호출해야합니다
     * 공개 키 생성을 위한 [registerRequest] 및 로컬 FIDO2 API 호출.
     * Finishes registering a new credential to the server. This should only be called after
     * a call to [registerRequest] and a local FIDO2 API for public key generation.
     */
    fun registerResponse(data: Intent, processing: MutableLiveData<Boolean>) {
        executor.execute {
            processing.postValue(true)
            try {
                val token = prefs.getString(PREF_TOKEN, null)!!
                val challenge = lastKnownChallenge!!
                val response = AuthenticatorAttestationResponse.deserializeFromBytes(
                    data.getByteArrayExtra(Fido.FIDO2_KEY_RESPONSE_EXTRA)!!
                )
                val credentialId = response.keyHandle.toBase64()
                val credentials = api.registerResponse(token, challenge, response)
                prefs.edit {
                    putStringSet(PREF_CREDENTIALS, credentials.toStringSet())
                    putString(PREF_LOCAL_CREDENTIAL_ID, credentialId)
                }
            } catch (e: ApiException) {
                Log.e(TAG, "registerResponse를 호출할 수 없습니다.", e)
            } finally {
                processing.postValue(false)
            }
        }
    }

    /**
     * 서버에 등록 된 자격 증명을 제거합니다.
     * Removes a credential registered on the server.
     */
/*    fun removeKey(credentialId: String, processing: MutableLiveData<Boolean>) {
        executor.execute {
            processing.postValue(true)
            try {
                val token = prefs.getString(PREF_TOKEN, null)!!
                api.removeKey(token, credentialId)
                refreshCredentials()
            } catch (e: ApiException) {
                Log.e(TAG, "removeKey를 호출 할 수 없습니다", e)
            } finally {
                processing.postValue(false)
            }
        }
    }*/

    /**
     * * FIDO2 자격 증명으로 로그인을 시작합니다. 로그인 상태 인 경우에만 호출해야합니다.
     *는 [SignInState.SigningIn]입니다.
     * Starts to sign in with a FIDO2 credential. This should only be called when the sign-in state
     * is [SignInState.SigningIn].
     */
    fun signinRequest(processing: MutableLiveData<Boolean>): LiveData<Fido2PendingIntent> {
        val result = MutableLiveData<Fido2PendingIntent>()
        executor.execute {
            fido2ApiClient?.let { client ->
                processing.postValue(true)
                try {
                    val username = prefs.getString(PREF_USERNAME, null)!!
                    val credentialId = prefs.getString(PREF_LOCAL_CREDENTIAL_ID, null)
                    val (options, challenge) = api.signinRequest(username, credentialId)
                    lastKnownChallenge = challenge
                    val task = client.getSignIntent(options)
                    result.postValue(Tasks.await(task))
                } finally {
                    processing.postValue(false)
                }
            }
        }
        return result
    }

    /**
     * FIDO2 자격 증명으로 로그인을 마칩니다. 호출 한 후에 만 ​​호출해야합니다.
     * [signinRequest] 및 키 assertion을 위한 로컬 FIDO2 API
     * Finishes to signing in with a FIDO2 credential. This should only be called after a call to
     * [signinRequest] and a local FIDO2 API for key assertion.
     */
    fun signinResponse(data: Intent, processing: MutableLiveData<Boolean>) {
        executor.execute {
            processing.postValue(true)
            try {
                val username = prefs.getString(PREF_USERNAME, null)!!
                val challenge = lastKnownChallenge!!
                val response = AuthenticatorAssertionResponse.deserializeFromBytes(
                    data.getByteArrayExtra(Fido.FIDO2_KEY_RESPONSE_EXTRA)
                )
                val credentialId = response.keyHandle.toBase64()
                val (credentials, token) = api.signinResponse(username, challenge, response)
                prefs.edit(commit = true) {
                    putString(PREF_TOKEN, token)
                    putStringSet(PREF_CREDENTIALS, credentials.toStringSet())
                    putString(PREF_LOCAL_CREDENTIAL_ID, credentialId)
                }
                invokeSignInStateListeners(SignInState.SignedIn(username, token))
            } catch (e: ApiException) {
                Log.e(TAG, "registerResponse를 호출할 수 없습니다.", e)
            } finally {
                processing.postValue(false)
            }
        }
    }

}
