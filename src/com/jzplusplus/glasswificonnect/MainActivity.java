/* 
 * Created by Jay Zuerndorfer on 2013-09-20
 * Based off of lisah0's demo code
 */

package com.jzplusplus.glasswificonnect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;

import com.jzplusplus.glasswificonnect.CameraPreview;

import android.os.Handler;

import android.util.Log;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.Toast;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Size;

import android.widget.TextView;

/* Import ZBar Class files */
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;
import net.sourceforge.zbar.Config;

public class MainActivity extends Activity
{
	final static String TAG = "GlassWifiConnect";
	
    private Camera mCamera;
    private CameraPreview mPreview;
    private Handler autoFocusHandler;

    Button scanButton;

    ImageScanner scanner;

    private boolean previewing = true;

    static {
        System.loadLibrary("iconv");
    } 

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        autoFocusHandler = new Handler();
        mCamera = getCameraInstance();

        /* Instance barcode scanner */
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        mPreview = new CameraPreview(this, mCamera, previewCb, autoFocusCB);
        FrameLayout preview = (FrameLayout)findViewById(R.id.cameraPreview);
        preview.addView(mPreview);
    }

    public void onPause() {
        super.onPause();
        releaseCamera();
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e){
        	Log.d(TAG, e.toString());
        }
        return c;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    private Runnable doAutoFocus = new Runnable() {
            public void run() {
                if (previewing)
                    mCamera.autoFocus(autoFocusCB);
            }
        };

    PreviewCallback previewCb = new PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Parameters parameters = camera.getParameters();
                Size size = parameters.getPreviewSize();

                Image barcode = new Image(size.width, size.height, "Y800");
                barcode.setData(data);

                int result = scanner.scanImage(barcode);
                
                if (result != 0) {
                    previewing = false;
                    mCamera.setPreviewCallback(null);
                    mCamera.stopPreview();
                    
                    SymbolSet syms = scanner.getResults();
                    for (Symbol sym : syms) {
                    	String text = sym.getData();
                        parseWifiInfo(text);
                        break;
                    }
                }
            }
        };

    void parseWifiInfo(String text)
    {
    	if(!text.startsWith("WIFI:") || !text.endsWith(";;"))
    	{
    		Toast t = Toast.makeText(getApplicationContext(), "Not a valid Wifi QR code", Toast.LENGTH_LONG);
    		t.show();
    		return;
    	}
    	
    	String ssid = null;
    	String type = null;
    	String username = null;
    	String password = null;
    	String EAP = null;
    	String phase2 = null;
    	String anon = null;
    	String clientCert = null;
    	String privKey = null;
    	String caCert = null;
    	
    	text = text.substring("WIFI:".length(), text.length()-2);
    	
    	String[] params = text.split(";");
    	
    	for(String s: params)
    	{
    		String[] keyval = s.split(":");
    		if(keyval[0].equals("S"))
    		{
    			ssid = keyval[1];
    		}
    		else if(keyval[0].equals("T"))
    		{
    			type = keyval[1];
    		}
    		else if(keyval[0].equals("U"))
    		{
    			username = keyval[1];
    		}
    		else if(keyval[0].equals("P"))
    		{
    			password = keyval[1];
    		}
    		else if(keyval[0].equals("E"))
    		{
    			EAP = keyval[1];
    		}
    		else if(keyval[0].equals("PH"))
    		{
    			phase2 = keyval[1];
    		}
    		else if(keyval[0].equals("A"))
    		{
    			anon = keyval[1];
    		}
    		else if(keyval[0].equals("CC"))
    		{
    			clientCert = keyval[1];
    		}
    		else if(keyval[0].equals("PK"))
    		{
    			privKey = keyval[1];
    		}
    		else if(keyval[0].equals("CA"))
    		{
    			caCert = keyval[1];
    		}
    	}
    	
    	Toast t = Toast.makeText(getApplicationContext(), "Connecting to " + ssid + "...", Toast.LENGTH_LONG);
    	t.show();
    	
    	saveEapConfig(ssid, username, password, EAP, phase2, anon, clientCert, privKey, caCert);
    	
    	finish();
    }
        
    // Mimic continuous auto-focusing
    AutoFocusCallback autoFocusCB = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                autoFocusHandler.postDelayed(doAutoFocus, 1000);
            }
        };
        
    boolean saveEapConfig(String ssid, String userName, String passString, String EAP, String phase2, String anon,
    					String clientCert, String privKey, String caCert)
    {
	    /********************************Configuration Strings****************************************************/
	    final String ENTERPRISE_EAP = EAP;
    	final String ENTERPRISE_CLIENT_CERT = clientCert;
    	final String ENTERPRISE_PRIV_KEY = privKey;
	    //CertificateName = Name given to the certificate while installing it
	
	    /*Optional Params- My wireless Doesn't use these*/
	    final String ENTERPRISE_PHASE2 = phase2;
	    final String ENTERPRISE_ANON_IDENT = anon;
	    final String ENTERPRISE_CA_CERT = caCert;
	    /********************************Configuration Strings****************************************************/
	
	    final String INT_PRIVATE_KEY = "private_key";
	    final String INT_PHASE2 = "phase2";
	    final String INT_PASSWORD = "password";
	    final String INT_IDENTITY = "identity";
	    final String INT_EAP = "eap";
	    final String INT_CLIENT_CERT = "client_cert";
	    final String INT_CA_CERT = "ca_cert";
	    final String INT_ANONYMOUS_IDENTITY = "anonymous_identity";
	    final String INT_ENTERPRISEFIELD_NAME = "android.net.wifi.WifiConfiguration$EnterpriseField";
	    
	    /*Create a WifiConfig*/
	    WifiConfiguration selectedConfig = new WifiConfiguration();
	
	    /*AP Name*/
	    selectedConfig.SSID = "\""+ssid+"\"";
	
	    /*Priority*/
	    selectedConfig.priority = 40;
	
	    /*Enable Hidden SSID*/
	    selectedConfig.hiddenSSID = true;
	
	    /*Key Mgmnt*/
	    selectedConfig.allowedKeyManagement.clear();
	    selectedConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
	    selectedConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
	    selectedConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
	    selectedConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
	
	    /*Group Ciphers*/
	    selectedConfig.allowedGroupCiphers.clear();
	    selectedConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
	    selectedConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
	    selectedConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
	    selectedConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
	
	    /*Pairwise ciphers*/
	    selectedConfig.allowedPairwiseCiphers.clear();
	    selectedConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
	    selectedConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
	
	    /*Protocols*/
	    selectedConfig.allowedProtocols.clear();
	    selectedConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
	    selectedConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
	
	    // Enterprise Settings
	    // Reflection magic here too, need access to non-public APIs
	    try {
	        // Let the magic start
	        Class[] wcClasses = WifiConfiguration.class.getClasses();
	        // null for overzealous java compiler
	        Class wcEnterpriseField = null;
	
	        for (Class wcClass : wcClasses)
	            if (wcClass.getName().equals(INT_ENTERPRISEFIELD_NAME)) 
	            {
	                wcEnterpriseField = wcClass;
	                break;
	            }
	        boolean noEnterpriseFieldType = false; 
	        if(wcEnterpriseField == null)
	            noEnterpriseFieldType = true; // Cupcake/Donut access enterprise settings directly
	
	        Field wcefAnonymousId = null, wcefCaCert = null, wcefClientCert = null, wcefEap = null, wcefIdentity = null, wcefPassword = null, wcefPhase2 = null, wcefPrivateKey = null;
	        Field[] wcefFields = WifiConfiguration.class.getFields();
	        // Dispatching Field vars
	        for (Field wcefField : wcefFields) 
	        {
	            if (wcefField.getName().equals(INT_ANONYMOUS_IDENTITY))
	                wcefAnonymousId = wcefField;
	            else if (wcefField.getName().equals(INT_CA_CERT))
	                wcefCaCert = wcefField;
	            else if (wcefField.getName().equals(INT_CLIENT_CERT))
	                wcefClientCert = wcefField;
	            else if (wcefField.getName().equals(INT_EAP))
	                wcefEap = wcefField;
	            else if (wcefField.getName().equals(INT_IDENTITY))
	                wcefIdentity = wcefField;
	            else if (wcefField.getName().equals(INT_PASSWORD))
	                wcefPassword = wcefField;
	            else if (wcefField.getName().equals(INT_PHASE2))
	                wcefPhase2 = wcefField;
	            else if (wcefField.getName().equals(INT_PRIVATE_KEY))
	                wcefPrivateKey = wcefField;
	        }
	
	
	        Method wcefSetValue = null;
	        if(!noEnterpriseFieldType){
	        for(Method m: wcEnterpriseField.getMethods())
	            //System.out.println(m.getName());
	            if(m.getName().trim().equals("setValue"))
	                wcefSetValue = m;
	        }
	
	
	        /*EAP Method*/
	        if(!noEnterpriseFieldType)
	        {
	                wcefSetValue.invoke(wcefEap.get(selectedConfig), ENTERPRISE_EAP);
	        }
	        else
	        {
	                wcefEap.set(selectedConfig, ENTERPRISE_EAP);
	        }
	        /*EAP Phase 2 Authentication*/
	        if(!noEnterpriseFieldType)
	        {
	                wcefSetValue.invoke(wcefPhase2.get(selectedConfig), ENTERPRISE_PHASE2);
	        }
	        else
	        {
	              wcefPhase2.set(selectedConfig, ENTERPRISE_PHASE2);
	        }
	        /*EAP Anonymous Identity*/
	        if(!noEnterpriseFieldType)
	        {
	                wcefSetValue.invoke(wcefAnonymousId.get(selectedConfig), ENTERPRISE_ANON_IDENT);
	        }
	        else
	        {
	              wcefAnonymousId.set(selectedConfig, ENTERPRISE_ANON_IDENT);
	        }
	        /*EAP CA Certificate*/
	        if(!noEnterpriseFieldType)
	        {
	                wcefSetValue.invoke(wcefCaCert.get(selectedConfig), ENTERPRISE_CA_CERT);
	        }
	        else
	        {
	              wcefCaCert.set(selectedConfig, ENTERPRISE_CA_CERT);
	        }               
	        /*EAP Private key*/
	        if(!noEnterpriseFieldType)
	        {
	                wcefSetValue.invoke(wcefPrivateKey.get(selectedConfig), ENTERPRISE_PRIV_KEY);
	        }
	        else
	        {
	              wcefPrivateKey.set(selectedConfig, ENTERPRISE_PRIV_KEY);
	        }               
	        /*EAP Identity*/
	        if(!noEnterpriseFieldType)
	        {
	                wcefSetValue.invoke(wcefIdentity.get(selectedConfig), userName);
	        }
	        else
	        {
	              wcefIdentity.set(selectedConfig, userName);
	        }               
	        /*EAP Password*/
	        if(!noEnterpriseFieldType)
	        {
	                wcefSetValue.invoke(wcefPassword.get(selectedConfig), passString);
	        }
	        else
	        {
	              wcefPassword.set(selectedConfig, passString);
	        }               
	        /*EAp Client certificate*/
	        if(!noEnterpriseFieldType)
	        {
	            wcefSetValue.invoke(wcefClientCert.get(selectedConfig), ENTERPRISE_CLIENT_CERT);
	        }
	        else
	        {
	              wcefClientCert.set(selectedConfig, ENTERPRISE_CLIENT_CERT);
	        }               
	        // Adhoc for CM6
	        // if non-CM6 fails gracefully thanks to nested try-catch
	
	        try{
	        Field wcAdhoc = WifiConfiguration.class.getField("adhocSSID");
	        Field wcAdhocFreq = WifiConfiguration.class.getField("frequency");
	        //wcAdhoc.setBoolean(selectedConfig, prefs.getBoolean(PREF_ADHOC,
	        //      false));
	        wcAdhoc.setBoolean(selectedConfig, false);
	        int freq = 2462;    // default to channel 11
	        //int freq = Integer.parseInt(prefs.getString(PREF_ADHOC_FREQUENCY,
	        //"2462"));     // default to channel 11
	        //System.err.println(freq);
	        wcAdhocFreq.setInt(selectedConfig, freq); 
	        } catch (Exception e)
	        {
	            e.printStackTrace();
	        }
	
	    } catch (Exception e)
	    {
	        e.printStackTrace();
	    }
	
	    WifiManager wifiManag = (WifiManager) getSystemService(Context.WIFI_SERVICE);
	    boolean res1 = wifiManag.setWifiEnabled(true);
	    int res = wifiManag.addNetwork(selectedConfig);
	    Log.d("WifiPreference", "add Network returned " + res );
	    boolean b = wifiManag.enableNetwork(selectedConfig.networkId, false);
	    Log.d("WifiPreference", "enableNetwork returned " + b );
	    boolean c = wifiManag.saveConfiguration();
	    Log.d("WifiPreference", "Save configuration returned " + c );
	    boolean d = wifiManag.enableNetwork(res, true);   
	    Log.d("WifiPreference", "enableNetwork returned " + d );
	    
	    if(!res1 || !c || !d)
	    {
	    	Toast t = Toast.makeText(getApplicationContext(), "WiFi network connection failed", Toast.LENGTH_LONG);
	    	t.show();
	    	return false;
	    }
	    
	    Toast t = Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_SHORT);
	    t.show();
	    return true;
	}
    
}
