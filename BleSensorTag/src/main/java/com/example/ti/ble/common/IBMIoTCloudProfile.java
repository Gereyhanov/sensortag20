/**************************************************************************************************
 Filename:       IBMIoTCloudProfile.java

 Copyright (c) 2013 - 2015 Texas Instruments Incorporated

 All rights reserved not granted herein.
 Limited License.

 Texas Instruments Incorporated grants a world-wide, royalty-free,
 non-exclusive license under copyrights and patents it now or hereafter
 owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
 this software subject to the terms herein.  With respect to the foregoing patent
 license, such license is granted  solely to the extent that any such patent is necessary
 to Utilize the software alone.  The patent license shall not apply to any combinations which
 include this software, other than combinations with devices manufactured by or for TI ('TI Devices').
 No hardware patent is licensed hereunder.

 Redistributions must preserve existing copyright notices and reproduce this license (including the
 above copyright notice and the disclaimer and (if applicable) source code license limitations below)
 in the documentation and/or other materials provided with the distribution

 Redistribution and use in binary form, without modification, are permitted provided that the following
 conditions are met:

 * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
 software provided in binary form.
 * any redistribution and use are licensed by TI for use only with TI Devices.
 * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

 If software source code is provided to you, modification and redistribution of the source code are permitted
 provided that the following conditions are met:

 * any redistribution and use of the source code, including any resulting derivative works, are licensed by
 TI for use only with TI Devices.
 * any redistribution and use of any object code compiled from the source code and any resulting derivative
 works, are licensed by TI for use only with TI Devices.

 Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
 promote products derived from this software without specific prior written permission.

 DISCLAIMER.

 THIS SOFTWARE IS PROVIDED BY TI AND TI'S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL TI AND TI'S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.example.ti.ble.common;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;

import com.example.ti.ble.sensortag.R;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class IBMIoTCloudProfile extends GenericBluetoothProfile {
    final String startString = "{\n \"d\":{\n";
    final String stopString = "\n}\n}";
    MqttAndroidClient client;
    MemoryPersistence memPer;
    final String addrShort;
    static IBMIoTCloudProfile mThis;
    Map<String, String> valueMap = new HashMap<String, String>();
    Timer publishTimer;
    public boolean ready;
    private WakeLock wakeLock;
    BroadcastReceiver cloudConfigUpdateReceiver;
    cloudConfig config;
    // variables para usar el almacenamiento
    private SharedPreferences sharedpreferencebs ;
    private static final String MyPREFERENCES = "MyPrefs" ;
    private static final String numArchivo = "numArchivoKey";
    private static final String numLinea = "numLineaKey";
    private static final String KeyThingspeak =  "keyThingspeak";

    private static final int numberOfRows = 30000;
    private int countToSend = 1; // valor inicial
    private int datosPorEnvio = 1; // 15 datos por minuto, 30 en 2 min




    public IBMIoTCloudProfile(final Context con,BluetoothDevice device,BluetoothGattService service,BluetoothLeService controller) {
        super(con,device,service,controller);
        this.tRow =  new IBMIoTCloudTableRow(con);
        this.tRow.setOnClickListener(null);

        config = readCloudConfigFromPrefs();



        String addr = mBTDevice.getAddress();
        String[] addrSplit = addr.split(":");
        int[] addrBytes = new int[6];
        for (int ii = 0; ii < 6; ii++) {
            addrBytes[ii] = Integer.parseInt(addrSplit[ii], 16);
        }
        ready = false;
        this.addrShort = String.format("%02x%02x%02x%02x%02x%02x",addrBytes[0],addrBytes[1],addrBytes[2],addrBytes[3],addrBytes[4],addrBytes[5]);

        if (config != null) {
            Log.d("IBMIoTCloudProfile", "Stored cloud configuration" + "\r\n" + config.toString());
        }
        else {
            config = initPrefsWithIBMQuickStart();
            Log.d("IBMIoTCloudProfile", "Stored cloud configuration was corrupt, starting new based on IBM IoT Quickstart variables" + config.toString());
        }


        Log.d("IBMIoTCloudProfile", "Device ID : " + addrShort);
        this.tRow.sl1.setVisibility(View.INVISIBLE);
        this.tRow.sl2.setVisibility(View.INVISIBLE);
        this.tRow.sl3.setVisibility(View.INVISIBLE);
        this.tRow.title.setText("Cloud View");
        this.tRow.setIcon("sensortag2cloudservice", "", "");
        this.tRow.value.setText("Device ID : " + addr);



        IBMIoTCloudTableRow tmpRow = (IBMIoTCloudTableRow) this.tRow;
        // se deja seleccionado ya que se encuentra conectado
        // TODO: 23/03/2016
        connect();
        tmpRow.pushToCloud.setChecked(true);
        tmpRow.pushToCloud.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // // TODO: 23/03/2016 mark to know connection
                    Log.d("web", "se conecto el envio");
                    connect();
                }
                else {
                    //disconnect();
                }
            }
        });


        tmpRow.configureCloud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CloudProfileConfigurationDialogFragment dF = CloudProfileConfigurationDialogFragment.newInstance(addrShort);

                final Activity act = (Activity)context;
                dF.show(act.getFragmentManager(),"CloudConfig");


              }
        });


        /*
        String url = "https://quickstart.internetofthings.ibmcloud.com/#/device/" + addrShort + "/sensor/";

        Pattern pattern = Pattern.compile(url);
        Linkify.addLinks(((IBMIoTCloudTableRow) this.tRow).cloudURL, pattern, "https://");
        ((IBMIoTCloudTableRow) this.tRow).cloudURL.setText(Html.fromHtml("<a href='https://" + url + "'>" + url + "</a>"));
*/

        if (config.service == CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_CLOUD_SERVICE) {
            ((IBMIoTCloudTableRow) this.tRow).cloudURL.setText("Open in browser");
            ((IBMIoTCloudTableRow) this.tRow).cloudURL.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://thingspeak.com/channels/63112")));
                }
            });
        }
        else {
            ((IBMIoTCloudTableRow) this.tRow).cloudURL.setText("");
            ((IBMIoTCloudTableRow) this.tRow).cloudURL.setAlpha(0.1f);
        }
        mThis = this;
        cloudConfigUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(CloudProfileConfigurationDialogFragment.ACTION_CLOUD_CONFIG_WAS_UPDATED)) {
                    Log.d("IBMIoTCloudProfile","Cloud configuration was updated !");
                    Log.d("IBMIoTCloudProfile", "Old cloud configuration was :" + config.toString());
                    config = readCloudConfigFromPrefs();
                    Log.d("IBMIoTCloudProfile", "New cloud configuration :" + config.toString());
                    if (client != null) {
                        try {
                            if (client.isConnected()) {
                                disconnect();
                                connect();
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        this.context.registerReceiver(cloudConfigUpdateReceiver,makeCloudConfigUpdateFilter());
        // codigo de alamacenado, aqui obtengo las preferencia del xml
        sharedpreferencebs =context.getSharedPreferences(MyPREFERENCES, Activity.MODE_PRIVATE);
        // si no a sido creado el archivo de preferencias se crea uno con inicializacion en cero
        if(sharedpreferencebs.getInt(numArchivo, -1)==-1){
            SharedPreferences.Editor editor = sharedpreferencebs.edit();
            editor.putInt(numArchivo,0);
            editor.putInt(numLinea, 0);
            editor.commit();
        }
        Log.d("temp", "error "+sharedpreferencebs.getInt(numArchivo, -1));

    }
    public boolean disconnect() {
        try {
            ((IBMIoTCloudTableRow) tRow).setCloudConnectionStatusImage(context.getResources().getDrawable(R.drawable.cloud_disconnected));
            ready = false;
            if (publishTimer != null) {
                publishTimer.cancel();
            }
            if (client != null) {
                Log.d("IBMIoTCloudProfile", "Disconnecting from cloud : " + client.getServerURI() + "," + client.getClientId());
                if (client.isConnected()) client.disconnect();
                client.unregisterResources();
                client = null;
                memPer = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean connect() {

        /**
         * este try es solo para validar cnx la primer vez, no es neseario ya que genera un error
        try {
            memPer = new MemoryPersistence();
            String url = config.brokerAddress + ":" + config.brokerPort;
            Log.d("IBMIoTCloudProfile","Cloud Broker URL : " + url);
            client = new MqttAndroidClient(this.context,url,config.deviceId);
            MqttConnectOptions options = null;

            if (config.service > CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_CLOUD_SERVICE) {
                options = new MqttConnectOptions();
                options.setCleanSession(config.cleanSession);
                if (config.username.length() > 0)options.setUserName(config.username);
                if (config.password.length() > 0)options.setPassword(config.password.toCharArray());
                Log.d("IBMIoTCloudProfile","Adding Options : Clean Session : " + options.isCleanSession() + ", Username : " + config.username + ", " + "Password : " + "********");
            }

            client.connect(options, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken iMqttToken) {
                    Log.d("IBMIoTCloudProfile", "Connected to cloud : " + client.getServerURI() + "," + client.getClientId());

                    try {
                        client.publish(config.publishTopic,jsonEncode("myName",mBTDevice.getName().toString()).getBytes(),0,false);
                        ready = true;
                    }
                    catch (MqttException e) {
                        e.printStackTrace();
                    }

                    ((IBMIoTCloudTableRow) tRow).setCloudConnectionStatusImage(context.getResources().getDrawable(R.drawable.cloud_connected));

                }

                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                    Log.d("IBMIoTCloudProfile", "Connection to IBM cloud failed !");
                    Log.d("IBMIoTCloudProfile", "Error: " + throwable.getLocalizedMessage());
                    ((IBMIoTCloudTableRow) tRow).setCloudConnectionStatusImage(context.getResources().getDrawable(R.drawable.cloud_disconnected));
                    AlertDialog.Builder b = new AlertDialog.Builder(context);
                    b.setTitle("Connection to Cloud failed !");
                    b.setMessage(Html.fromHtml("<b>Connection to : </b><br>" + client.getServerURI() + "<br><br><b>Device id : </b><br>" + client.getClientId()
                            + "<br><br><b>Failed with error code :</b><br><font color='#FF0000'>" + throwable.getLocalizedMessage() + "</font>"));
                    b.setPositiveButton("OK", null);
                    b.create().show();
                    ((IBMIoTCloudTableRow) tRow).pushToCloud.setChecked(false);
                    disconnect();
                }
            });
        }
        catch (MqttException e) {
            e.printStackTrace();

        }
        **/
        ready = true;
        Log.d("web", "conectado a web");
        publishTimer = new Timer();
        MQTTTimerTask task = new MQTTTimerTask();
        // cada 2 min
        //publishTimer.schedule(task,1000,1000);
        publishTimer.schedule(task,0,15000); // 15000  120000
        return true;
    }


    public String jsonEncode(String variableName, String Value) {
        String tmpString = new String();
        tmpString += startString;
        tmpString += "\"" + variableName + "\"" + ":" + "\"" + Value + "\"";
        tmpString += stopString;
        return tmpString;
    }
    public String jsonEncode(String str) {
        String tmpString = new String();
        tmpString += startString;
        tmpString += str;
        tmpString += stopString;
        return tmpString;
    }
    public void publishString(String str) {
        MqttMessage message = new MqttMessage();
        try {

            client.publish(config.publishTopic,jsonEncode("Test","123").getBytes(),0,false);
            //Log.d("IBMIoTCloudProfile", "Published message :" + message.toString());
        }
        catch (MqttException e) {
            e.printStackTrace();
        }
    }
    public void addSensorValueToPendingMessage(String variableName, String Value) {
        this.valueMap.put(variableName,Value);
    }
    public void addSensorValueToPendingMessage(Map.Entry<String,String> e) {
        this.valueMap.put(e.getKey(), e.getValue());
    }
    @Override
    public void onPause() {
        super.onPause();
        this.context.unregisterReceiver(cloudConfigUpdateReceiver);
    }
    @Override
    public void onResume() {
        super.onResume();
        this.context.registerReceiver(cloudConfigUpdateReceiver,makeCloudConfigUpdateFilter());
    }
    @Override
    public void enableService () {

    }
    @Override
    public void disableService () {

    }
    @Override
    public void configureService() {

    }
    @Override
    public void deConfigureService() {

    }
    @Override
    public void didUpdateValueForCharacteristic(BluetoothGattCharacteristic c) {
    }
    @Override
    public void didReadValueForCharacteristic(BluetoothGattCharacteristic c) {
    }
    public static IBMIoTCloudProfile getInstance() {
        return mThis;
    }
    class MQTTTimerTask extends TimerTask {
        @Override
        public void run() {
            try {
                if (ready) {
                    final Activity activity = (Activity) context;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((IBMIoTCloudTableRow)tRow).setCloudConnectionStatusImage(activity.getResources().getDrawable(R.drawable.cloud_connected_tx));
                        }
                    });
                    String publishValues = "";
                    Map<String, String> dict = new HashMap<String, String>();
                    dict.putAll(valueMap);
                    for (Map.Entry<String, String> entry : dict.entrySet()) {
                        String var = entry.getKey();
                        String val = entry.getValue();

                        publishValues += "\"" + var + "\"" + ":" + "\"" + val + "\"" + ",\n";
                    }
                    if (publishValues.length() > 0) {
                        //String pub = publishValues.substring(0, publishValues.length() - 2);
                        // client.publish(config.publishTopic, jsonEncode(pub).getBytes(), 0, false);
                        //Log.d("IBMIoTCloudProfile", "Published :" + jsonEncode(pub));
                        // se usa el replace por que thingspeak recive . en lugar de , y # como separador
                        float f = getBatteryLevel();
                        updateReceivedData(
                                dict.get("ambient_temp").replace(",",".")+"#"+
                                dict.get("object_temp").replace(",",".")+"#"+
                                dict.get("humidity").replace(",",".")+"#"+
                                //dict.get("light").replace(",",".")+"#"+
                                f);
                        Log.d("bat", "battery :"+ f);
                        // aqui se llama al metodo de guardado en memoria
                        TestSD(","+dict.get("object_temp").replace(",","."));
                        try {
                            Thread.sleep(60);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //muestra la imagen de conectado
                            ((IBMIoTCloudTableRow)tRow).setCloudConnectionStatusImage(activity.getResources().getDrawable(R.drawable.cloud_connected));
                        }
                    });
                }
                else {
                    Log.d("IBMIoTCloudProfile", "MQTTTimerTask ran, but MQTT not ready");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateReceivedData(String karelRead1) {

        // si la trama contiene el mensaje de thingspeak envia la trama
        //StringTokenizer st = new StringTokenizer(message, "thingspeak");
        // se saca un substring que elimina el indice thingspeak y el /n del final de la linea
        String ms = karelRead1;


        // // TODO: 22/03/2016  api key automatic 3831SHX4AS2XD41T
        String url = "https://api.thingspeak.com/update?api_key=K24OF4CX99WPXXV7";
        //String url = "https://api.thingspeak.com/update?api_key=3831SHX4AS2XD41T";

        // String url = "https://api.thingspeak.com/update?api_key="+sharedpreferences.getString(KeyThingspeak, "");
        int i=0;


        for (String token : ms.split("#")) {
            i++;
            url = url+"&field"+i+"="+token;
        }

        final String finalUrl = url;
        Thread thread = new Thread() {
            @Override
            public void run() {
                HttpURLConnection urlConnection = null;
                StringBuilder total = new StringBuilder();

                try {
                    URL url1 = new URL(finalUrl);
                    urlConnection = (HttpURLConnection) url1.openConnection();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = r.readLine()) != null) {
                        total.append(line);
                    }
                    Log.d("web", total.toString()+" "+finalUrl);
                    if(total.toString().equals("0")){
                        //ThingspeakState= false;
                    }else{
                        //ThingspeakState= true;
                    }

                    //readStream(in);
                    urlConnection.disconnect();

                    //finally {
                    //  urlConnection.disconnect();
                    //}
                } catch (IOException e) {
                    //ThingspeakState= false;
                    e.printStackTrace();
                } catch (NullPointerException e){
                    //ThingspeakState= false;
                    e.printStackTrace();
                }
            }
        };

        thread.start();
        karelRead1 = new String();
    }

    private void TestSD(String thingspeakData) throws IOException {
        //public void TestSD (View view) throws IOException {
        
        Log.d("sd"," thingspeakData : "+thingspeakData+ " file "+ sharedpreferencebs.getInt(numArchivo, -1)+"bat:"+getBatteryLevel());



        thingspeakData = "\n" + thingspeakData;
        String nameDataFile = "F"+sharedpreferencebs.getInt(numArchivo, -1)+".csv";
        // android.content.SharedPreferences sharedpreferencebs;ruta para memoria interna
        File myFile = new File(Environment.getExternalStorageDirectory()+"/Haceb/",nameDataFile);
        // ruta para SD card
        //File myFile = new File("/storage/sdcard1/",nameDataFile);
        //Toast.makeText(getBaseContext(),myFile.getAbsolutePath(),
        //      Toast.LENGTH_SHORT).show();
        //myFile.mkdirs(); //create folders where write files
        if(!myFile.exists()){
            myFile.createNewFile();
        }

        if(myFile.canWrite()){
            //Now create the file in the above directory and write the contents into it myFile.createNewFile();
            try {
                //String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())
                String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());

                thingspeakData = currentDateandTime + thingspeakData+"\n" ;
                OutputStreamWriter escritor = new OutputStreamWriter(new FileOutputStream(myFile,true));
                escritor.write(thingspeakData);
                escritor.flush();
                escritor.close();
                //Toast.makeText(getBaseContext(), "Escritura OK",
                 //     Toast.LENGTH_SHORT).show();
                //auto incremeta las lineas escritas
                SharedPreferences.Editor editor = sharedpreferencebs.edit();

                if (sharedpreferencebs.getInt(numLinea, -1) >= numberOfRows){
                    editor.putInt(numLinea, 0);
                    editor.putInt(numArchivo,sharedpreferencebs.getInt(numArchivo, -1) + 1);
                }else{
                    editor.putInt(numLinea, sharedpreferencebs.getInt(numLinea, -1) + 1);

                }
                editor.commit();
                //SDCardState = true;
            } catch (FileNotFoundException e) {
                //SDCardState = false;
                e.printStackTrace();
            } catch (IOException e) {
                //SDCardState = false;
                e.printStackTrace();
            }
        }else{
            //Toast.makeText(getBaseContext(), "no se puede escribir",
            //        Toast.LENGTH_SHORT).show();
            //SDCardState = false;
        }
    }
    public float getBatteryLevel() {

        Intent batteryIntent =  context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        // Error checking that probably isn't needed but I added just in case.
        if(level == -1 || scale == -1) {
            return 50.0f;
        }

        return ((float)level / (float)scale) * 100.0f;
    }
    private static IntentFilter makeCloudConfigUpdateFilter() {
        final IntentFilter fi = new IntentFilter();
        fi.addAction(CloudProfileConfigurationDialogFragment.ACTION_CLOUD_CONFIG_WAS_UPDATED);
        return fi;
    }

    class cloudConfig extends Object {
        public Integer service;
        public String username;
        public String password;
        public String deviceId;
        public String brokerAddress;
        public int brokerPort;
        public String publishTopic;
        public boolean cleanSession;
        public boolean useSSL;
        cloudConfig () {

        }
        @Override
        public String toString() {
            String s = new String();
            s = "Cloud configuration :\r\n";
            s += "Service : " + service + "\r\n";
            s += "Username : " + username + "\r\n";
            s += "Password : " + password + "\r\n";
            s += "Device ID : " + deviceId + "\r\n";
            s += "Broker Address : " + brokerAddress + "\r\n";
            s += "Proker Port : " + brokerPort + "\r\n";
            s += "Publish Topic : " + publishTopic + "\r\n";
            s += "Clean Session : " + cleanSession + "\r\n";
            s += "Use SSL : " + useSSL + "\r\n";
            return s;
        }
    }
    public cloudConfig readCloudConfigFromPrefs() {
        cloudConfig c = new cloudConfig();
        try {
            c.service = Integer.parseInt(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_SERVICE, this.context), 10);
            c.username = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_USERNAME,this.context);
            c.password = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_PASSWORD,this.context);
            c.deviceId = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_DEVICE_ID, this.context);
            c.brokerAddress = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_BROKER_ADDR, this.context);
            c.brokerPort = Integer.parseInt(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_BROKER_PORT, this.context), 10);
            c.publishTopic = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_PUBLISH_TOPIC, this.context);
            c.cleanSession = Boolean.parseBoolean(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_CLEAN_SESSION,this.context));
            c.useSSL = Boolean.parseBoolean(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_USE_SSL,this.context));
            if (c.service == CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_CLOUD_SERVICE) {
                ((IBMIoTCloudTableRow) this.tRow).cloudURL.setText("Open in browser");
                ((IBMIoTCloudTableRow) this.tRow).cloudURL.setAlpha(1.0f);
                ((IBMIoTCloudTableRow) this.tRow).cloudURL.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://thingspeak.com/channels/63112")));
                    }
                });
            }
            else {
                ((IBMIoTCloudTableRow) this.tRow).cloudURL.setText("");
                ((IBMIoTCloudTableRow) this.tRow).cloudURL.setAlpha(0.1f);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return c;
    }
    public cloudConfig initPrefsWithIBMQuickStart() {
        cloudConfig c = new cloudConfig();
        c.service = CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_CLOUD_SERVICE;
        c.username = CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_USERNAME;
        c.password = CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_PASSWORD;
        c.deviceId = CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_DEVICEID_PREFIX + addrShort;
        c.brokerAddress = CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_BROKER_ADDR;
        try {
            c.brokerPort = Integer.parseInt(CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_BROKER_PORT);
        }
        catch (Exception e) {
            c.brokerPort = 1883;
        }
        c.publishTopic = CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_PUBLISH_TOPIC;
        c.cleanSession = CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_CLEAN_SESSION;
        c.useSSL = CloudProfileConfigurationDialogFragment.DEF_CLOUD_IBMQUICKSTART_USE_SSL;
        return c;
    }
    public void writeCloudConfigToPrefs(cloudConfig c) {
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_SERVICE,c.service.toString(),this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_USERNAME,c.username,this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_PASSWORD,c.password,this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_DEVICE_ID,c.deviceId,this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_BROKER_ADDR,c.brokerAddress,this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_BROKER_PORT,((Integer)c.brokerPort).toString(),this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_PUBLISH_TOPIC,c.publishTopic,this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_CLEAN_SESSION,((Boolean)c.cleanSession).toString(),this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_USE_SSL,((Boolean)c.useSSL).toString(),this.context);
    }
}
