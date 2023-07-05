/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso3

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import com.squareup.picasso3.BaseDispatcher.NetworkBroadcastReceiver
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.MockitoAnnotations.initMocks
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BaseDispatcherTest {
  @Mock lateinit var context: Context

  @Before fun setUp() {
    initMocks(this)
  }

  @Test fun nullIntentOnReceiveDoesNothing() {
    val dispatcher = mock(BaseDispatcher::class.java)
    val receiver = NetworkBroadcastReceiver(dispatcher)

    receiver.onReceive(context, null)

    verifyNoInteractions(dispatcher)
  }

  @Test fun nullExtrasOnReceiveConnectivityAreOk() {
    val connectivityManager = mock(ConnectivityManager::class.java)
    val networkInfo = TestUtils.mockNetworkInfo()
    Mockito.`when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
    Mockito.`when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
    val dispatcher = mock(BaseDispatcher::class.java)
    val receiver = NetworkBroadcastReceiver(dispatcher)

    receiver.onReceive(context, Intent(ConnectivityManager.CONNECTIVITY_ACTION))

    verify(dispatcher).dispatchNetworkStateChange(networkInfo)
  }

  @Test fun nullExtrasOnReceiveAirplaneDoesNothing() {
    val dispatcher = mock(BaseDispatcher::class.java)
    val receiver = NetworkBroadcastReceiver(dispatcher)

    receiver.onReceive(context, Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED))

    verifyNoInteractions(dispatcher)
  }

  @Test fun correctExtrasOnReceiveAirplaneDispatches() {
    setAndVerifyAirplaneMode(false)
    setAndVerifyAirplaneMode(true)
  }

  private fun setAndVerifyAirplaneMode(airplaneOn: Boolean) {
    val dispatcher = mock(BaseDispatcher::class.java)
    val receiver = NetworkBroadcastReceiver(dispatcher)
    val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
    intent.putExtra(NetworkBroadcastReceiver.EXTRA_AIRPLANE_STATE, airplaneOn)
    receiver.onReceive(context, intent)
    verify(dispatcher).dispatchAirplaneModeChange(airplaneOn)
  }
}
