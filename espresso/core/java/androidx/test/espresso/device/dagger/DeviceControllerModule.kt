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

package androidx.test.espresso.device.dagger

import androidx.test.espresso.device.context.ActionContext
import androidx.test.espresso.device.context.InstrumentationTestActionContext
import androidx.test.espresso.device.controller.DeviceControllerOperationException
import androidx.test.espresso.device.controller.EmulatorController
import androidx.test.espresso.device.controller.PhysicalDeviceController
import androidx.test.espresso.device.controller.emulator.EmulatorConnection
import androidx.test.espresso.device.controller.emulator.LocalEmulatorConnection
import androidx.test.espresso.device.util.isTestDeviceAnEmulator
import androidx.test.internal.platform.ServiceLoaderWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.device.DeviceController
import dagger.Module
import dagger.Provides
import java.lang.reflect.Method
import javax.inject.Singleton

/** Dagger module for DeviceController. */
@Module
internal class DeviceControllerModule {

  companion object {
    private val EMULATOR_ADDRESS = "10.0.2.2"
    private val ARGS_GRPC_PORT = "grpc.port"
    private val ARGS_GRPC_TOKEN = "grpc.token"
    private val ARGS_GRPC_KEY = "grpc.key"
    private val ARGS_GRPC_CER = "grpc.cer"
    private val ARGS_GRPC_CA = "grpc.ca"
  }

  @Provides
  @Singleton
  fun provideActionContext(): ActionContext {
    return InstrumentationTestActionContext()
  }

  @Provides
  @Singleton
  fun provideDeviceController(): DeviceController {
    val platformDeviceController: androidx.test.platform.device.DeviceController? =
      ServiceLoaderWrapper.loadSingleServiceOrNull(
        androidx.test.platform.device.DeviceController::class.java
      )
    if (platformDeviceController == null) {
      if (isTestDeviceAnEmulator()) {
        val connection = provideEmulatorConnection()
        return EmulatorController(connection.emulatorController())
      } else {
        return PhysicalDeviceController()
      }
    } else {
      return EspressoDeviceControllerAdpater(platformDeviceController)
    }
  }

  @Provides
  @Singleton
  fun provideEmulatorConnection(): EmulatorConnection {
    val args = InstrumentationRegistry.getArguments()
    var grpcPort = args.getInt(ARGS_GRPC_PORT)
    if (grpcPort == 0) {
      // Running in g3
      grpcPort = getEmulatorGRPCPort()
    }

    return LocalEmulatorConnection(
      EMULATOR_ADDRESS,
      grpcPort,
      args.getString(ARGS_GRPC_TOKEN, ""),
      args.getString(ARGS_GRPC_CER, ""),
      args.getString(ARGS_GRPC_KEY, ""),
      args.getString(ARGS_GRPC_CA, "")
    )
  }

  private fun getEmulatorGRPCPort(): Int {
    val clazz = Class.forName("android.os.SystemProperties")
    val getter: Method = clazz.getMethod("get", String::class.java)
    var gRpcPort = getter.invoke(clazz, "mdevx.grpc_port") as String
    if (gRpcPort.isBlank()) {
      throw DeviceControllerOperationException(
        "Unable to connect to Emulator gRPC port. Please make sure the controller gRPC service is" +
          " enabled on the emulator."
      )
    }
    return gRpcPort.toInt()
  }

  private class EspressoDeviceControllerAdpater(
    val deviceController: androidx.test.platform.device.DeviceController
  ) : DeviceController {
    override fun setDeviceMode(deviceMode: Int) {
      deviceController.setDeviceMode(deviceMode)
    }

    override fun setScreenOrientation(screenOrientation: Int) {
      deviceController.setScreenOrientation(screenOrientation)
    }
  }
}
