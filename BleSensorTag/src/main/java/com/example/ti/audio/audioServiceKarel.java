package com.example.ti.audio;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class audioServiceKarel extends Service {

    // constructor
    public audioServiceKarel() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
