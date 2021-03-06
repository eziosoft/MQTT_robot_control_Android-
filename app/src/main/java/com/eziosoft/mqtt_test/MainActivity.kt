/*
 *  This file is part of MQTT_robot_control_Android.
 *
 *     MQTT_robot_control_Android is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Foobar is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Foobar.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2020. Bartosz Szczygiel
 *
 */

package com.eziosoft.mqtt_test

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.eziosoft.mqtt_test.data.Mqtt
import com.eziosoft.mqtt_test.data.Mqtt.Companion.MQTTStreamTopic
import com.eziosoft.mqtt_test.data.Mqtt.Companion.MQTTcontrolTopic
import com.eziosoft.mqtt_test.data.Mqtt.Companion.MQTTtelemetryTopic
import com.eziosoft.mqtt_test.data.MqttRepository
import com.eziosoft.mqtt_test.data.RoombaParsedSensor
import com.eziosoft.mqtt_test.data.SensorParser
import com.eziosoft.mqtt_test.helpers.map
import com.eziosoft.mqtt_test.helpers.to16UByteArray
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttMessage
import javax.inject.Inject
import kotlin.random.Random

@ExperimentalUnsignedTypes
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val TESTING = false
    val mainViewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController

    @Inject
    lateinit var mqttRepository: MqttRepository
    lateinit var mqtt: Mqtt

    lateinit var sensorParser: SensorParser


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)


        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        navController = navHostFragment.findNavController()
        val appBarConfiguration = AppBarConfiguration((navController.graph))
        setupActionBarWithNavController(navController, appBarConfiguration)

        sensorParser = SensorParser(object : SensorParser.SensorListener {
            override fun onSensors(sensors: List<RoombaParsedSensor>, checksumOK: Boolean) {
                if (checksumOK) processParsedSensors(sensors)
                else
                    Log.e("aaa", "CHECKSUM ERROR")
            }


        })
        mqtt = mqttRepository.mqtt
        mqtt.setListener(mqttListener)


        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        mainViewModel.serverAddress.value =
            sharedPreferences?.getString("serverIP", "test.mosquitto.org:1883")

        mainViewModel.serverAddress.observe(this) { address ->
            sharedPreferences?.edit()?.putString("serverIP", address)?.apply()
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }


    @ExperimentalUnsignedTypes
    private val mqttListener = object : Mqtt.MqttListener {
        override fun onConnectComplete(reconnect: Boolean, serverURI: String?) {
            Log.d("aaa", "connectComplete")
            mainViewModel.tvString.value = "Connected"
            mqtt.subscribe(MQTTtelemetryTopic)
            mqtt.subscribe(MQTTStreamTopic)
            mainViewModel.connectionStatus.value = true
        }

        override fun onMessageArrived(topic: String?, message: MqttMessage?) {
            when (topic) {
                MQTTtelemetryTopic -> {
                    mainViewModel.tvString.value =
                        message.toString()
                    parseSmallRobotTelemetry(message.toString())
                }
                MQTTcontrolTopic -> {
                    Log.d("aaa", "messageArrived: $topic :" + message.toString())
                    val b = message.toString().toByteArray()
                    if (b[0] == '$'.toByte() && b[1] == 5.toByte()) {
                        val x: Float = -(b[2] - 100) / 100f
                        val y: Float = -(b[3] - 100) / 100f

                        mainViewModel.apply {
                            joyX.value = x
                            joyY.value = y
                        }
                    }
                }

                MQTTStreamTopic -> {
                    val bytes = message!!.payload!!.toUByteArray()
                    if (!bytes.isEmpty()) {
                        sensorParser.parse(bytes)
                    }
                }
            }
        }


        override fun onConnectionLost(cause: Throwable?) {
            Log.d("aaa", "connectionLost")
            mainViewModel.tvString.value = "Connection lost"
            mainViewModel.connectionStatus.value = false
        }

        override fun onDeliveryComplete(token: IMqttDeliveryToken?) {
        }

    }


    fun connectToMQTT() {
//        mainViewModel.serverAddress.value = "mqtt.flespi.io:1883" //T
        val userName = "27aQSfkPYPrH1WHfjsDejLIqJxTza4i21gIHlTn8wEDlqarztSBAr7O0swnsqvi"

        val url = "tcp://" + mainViewModel.serverAddress.value

        if (mqtt.isConnected()) mqtt.close()
        mainViewModel.tvString.value =
            getString(R.string.connectig_to, mainViewModel.serverAddress.value)

        mqtt.connect(
            this,
            url,
            "user${System.currentTimeMillis()}",
            userName
        )
    }

    var timer = 0L
    fun processParsedSensors(sensors: List<RoombaParsedSensor>) {
        if (timer < System.currentTimeMillis()) {
            timer = System.currentTimeMillis() + 250

            mainViewModel.sensorDataSet.clear()
            mainViewModel.sensorDataSet.addAll(sensors)

            mainViewModel.dataSetChanged.value = 0
        }
        timer++
    }

    fun test() {
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                delay(15)
                val v: UByteArray = (Random.nextInt(-2000, 2000)).to16UByteArray()

                val data1: ArrayList<UByte> =
                    arrayListOf(
                        19u,
                        35u,
                        46u,
                        10u,
                        Random.nextInt(255).toUByte(),
                        47u,
                        10u,
                        Random.nextInt(255).toUByte(),
                        48u,
                        10u,
                        Random.nextInt(255).toUByte(),
                        49u,
                        10u,
                        Random.nextInt(255).toUByte(),
                        50u,
                        10u,
                        Random.nextInt(255).toUByte(),
                        51u,
                        10u,
                        Random.nextInt(255).toUByte(),
                        26u,
                        100u,
                        0u,
                        25u,
                        80u,
                        Random.nextInt(255).toUByte(),
                        23u,
                        v[0],
                        v[1],
                        22u,
                        0u,
                        Random.nextInt(200).toUByte(),
                        29u,
                        2u,
                        Random.nextInt(200).toUByte(),
                        13u,
                        Random.nextInt(2).toUByte()
                    )
                val checksum = 256u - data1.sum()
                data1.add((checksum.toUByte()))
                sensorParser.parse(data1.toUByteArray())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (TESTING) {
            mainViewModel.tvString.value = "TEST"
            test()
        }
    }


    fun parseSmallRobotTelemetry(message: String) {
        if (message.take(2) == "TS") {
            val data = message.split(";")
            val time = data[1].toInt()
            val rssi = data[2].toInt()
            val vbat = data[3].toFloat()
            val current = data[4].toFloat()
            val used_mAh = data[5].toFloat()

            val batPercent: Int = map(vbat, 3.3f, 4.2f, 0.0f, 100.0f).toInt()
            mainViewModel.sensorDataSet.clear()
            mainViewModel.sensorDataSet.add(
                RoombaParsedSensor(
                    26,
                    0u,
                    0u,
                    100,
                    "Max battery percentage",
                    ""
                )
            )
            mainViewModel.sensorDataSet.add(
                RoombaParsedSensor(
                    25,
                    0u,
                    0u,
                    batPercent, "Battery Percentage", "%"
                )
            )
            mainViewModel.sensorDataSet.add(
                RoombaParsedSensor(22, 0u, 0u, (vbat * 1000).toInt())
            )
            mainViewModel.sensorDataSet.add(
                RoombaParsedSensor(23, 0u, 0u, current.toInt())
            )
            mainViewModel.sensorDataSet.add(
                RoombaParsedSensor(100, 0u, 0u, time)
            )
            mainViewModel.sensorDataSet.add(
                RoombaParsedSensor(101, 0u, 0u, rssi)
            )

            mainViewModel.sensorDataSet.add(
                RoombaParsedSensor(102, 0u, 0u, used_mAh.toInt())
            )
            mainViewModel.dataSetChanged.value = 0

        }
    }

}

