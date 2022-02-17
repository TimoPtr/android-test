/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.test.espresso.device.controller

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkRequest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.emulator.control.EmulatorControllerGrpc
import io.grpc.Channel
import io.grpc.InsecureChannelCredentials
import io.grpc.okhttp.OkHttpChannelBuilder
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.Socket
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val realContext: Context
  get() = InstrumentationRegistry.getContext()

@RunWith(JUnit4::class)
class EmulatorControllerTest {
  private val stub: EmulatorControllerGrpc.EmulatorControllerBlockingStub = createStub()
  private val mCM = realContext.getSystemService(ConnectivityManager::class.java)!!
  @Test
  fun canAccessAndReadGrpcHeader() {
    val callback =
      object : NetworkCallback() {
        override fun onAvailable(network: Network) {
          // A failure in this tests indicates that the gRPC endpoint is not reachable.
          val socket = Socket(InetAddress.getByName("10.0.2.2"), getGrpcPort())
          network.bindSocket(socket)
          val inputStream = socket.getInputStream()
          assertNotEquals(inputStream.read(), -1)
        }
        override fun onUnavailable() {
          throw DeviceControllerOperationException(
            "Network request is unavailable on Android emulator."
          )
        }
      }
    mCM.requestNetwork(
      NetworkRequest.Builder().addTransportType(TRANSPORT_CELLULAR).build(),
      callback
    )
  }

  @Test
  fun setDeviceModeToInvalidDeviceMode_throwsUnsupportedDeviceOperationException() {
    val deviceController = EmulatorController(stub)

    assertThrows(UnsupportedDeviceOperationException::class.java) {
      deviceController.setDeviceMode(-1)
    }
  }

  @Test
  fun setScreenOrientationWithInvalidPort_throwsDeviceControllerOperationException() {
    val deviceController = EmulatorController(createStubWithInvalidPort())

    assertThrows(DeviceControllerOperationException::class.java) {
      deviceController.setScreenOrientation(1)
    }
  }

  private fun getGrpcPort(): Int {
    val clazz = Class.forName("android.os.SystemProperties")
    val getter: Method = clazz.getMethod("get", String::class.java)
    var gRpcPort = getter.invoke(clazz, "mdevx.grpc_port") as String
    if (gRpcPort.isBlank()) {
      throw DeviceControllerOperationException(
        "gRPC port not found in SystemProperties, is mdevx.grpc_port set?"
      )
    }
    return gRpcPort.toInt()
  }

  private fun createStub(): EmulatorControllerGrpc.EmulatorControllerBlockingStub {
    val port = getGrpcPort()
    val channel: Channel =
      OkHttpChannelBuilder.forAddress("10.0.2.2", port, InsecureChannelCredentials.create()).build()
    val emulatorControllerStub: EmulatorControllerGrpc.EmulatorControllerBlockingStub =
      EmulatorControllerGrpc.newBlockingStub(channel)
    return emulatorControllerStub
  }

  private fun createStubWithInvalidPort(): EmulatorControllerGrpc.EmulatorControllerBlockingStub {
    val port = -1
    val channel: Channel =
      OkHttpChannelBuilder.forAddress("10.0.2.2", port, InsecureChannelCredentials.create()).build()
    val emulatorControllerStub: EmulatorControllerGrpc.EmulatorControllerBlockingStub =
      EmulatorControllerGrpc.newBlockingStub(channel)
    return emulatorControllerStub
  }
}
