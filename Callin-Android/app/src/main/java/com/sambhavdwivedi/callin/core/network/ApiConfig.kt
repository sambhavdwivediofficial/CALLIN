package com.sambhavdwivedi.callin.core.network

object ApiConfig {
    // Physical device over USB: run `adb reverse tcp:8080 tcp:8080`
    // for each connected device, then localhost on the device reaches
    // your laptop's backend. Switch this to your Render URL once the
    // backend is deployed.
    
//    const val BASE_URL = "http://localhost:8080/"
     const val BASE_URL = "https://server.callin.sambhavdwivedi.in/"
}
