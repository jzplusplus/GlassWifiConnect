/* 
 * Created by Jay Zuerndorfer on 2013-09-20
 * Based off of lisah0's demo code
 */

package com.jzplusplus.glasswificonnect;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiEnterpriseConfig;
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

        autoFocusHandler = new Handler();
        
        //For some reason, right after launching from the "ok, glass" menu the camera is locked
        //Try 3 times to grab the camera, with a short delay in between.
        for(int i=0; i < 3; i++)
        {
	        mCamera = getCameraInstance();
	        if(mCamera != null) break;
	        
	        Log.d(TAG, "Couldn't lock camera, will try " + (2-i) + " more times...");
	        
	        try { Thread.sleep(500); } catch (InterruptedException e) { e.printStackTrace(); }
        }
        if(mCamera == null)
        {
        	Toast.makeText(this, "Camera cannot be locked", Toast.LENGTH_SHORT).show();
        	finish();
        }

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
            Log.d(TAG, "getCamera = " + c);
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
    	String username = null;
    	String password = null;
    	String EAP = null;
    	String phase2 = null;
    	String anon = null;
    	
    	text = text.substring("WIFI:".length(), text.length()-2);
    	
    	//This split method leads to some weird edge cases, but I want to support semicolons in fields
    	//without breaking backwards compatibility
    	String[] params = text.split(";(?=S:|T:|U:|P:|E:|PH:|A:|CC:|PK:|CA:)");
    	
    	for(String s: params)
    	{
    		String[] keyval = s.split(":");
    		if(keyval[0].equals("S"))
    		{
    			ssid = keyval[1];
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
    	}
    	
    	Toast t = Toast.makeText(getApplicationContext(), "Adding network '" + ssid + "'...", Toast.LENGTH_SHORT);
    	t.show();
    	
    	saveEapConfig(ssid, username, password, EAP, phase2, anon);
    	
    	finish();
    }
        
    // Mimic continuous auto-focusing
    AutoFocusCallback autoFocusCB = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                autoFocusHandler.postDelayed(doAutoFocus, 1000);
            }
        };
        
    boolean saveEapConfig(String ssid, String userName, String passString, String EAP, String phase2, String anon)
    {
	    /********************************Configuration Strings****************************************************/
	    final String ENTERPRISE_EAP = EAP;
	    final String ENTERPRISE_PHASE2 = phase2;
	    final String ENTERPRISE_ANON_IDENT = anon;
	    
	    /*Create a WifiConfig*/
	    WifiConfiguration selectedConfig = new WifiConfiguration();
	    
	    selectedConfig.SSID = "\""+ssid+"\"";
	    selectedConfig.status = WifiConfiguration.Status.ENABLED;
	    
	    selectedConfig.allowedKeyManagement.set(KeyMgmt.IEEE8021X);
	    selectedConfig.allowedKeyManagement.set(KeyMgmt.WPA_EAP);
	
	    // Enterprise Settings
	    selectedConfig.enterpriseConfig.setIdentity(userName);
	    selectedConfig.enterpriseConfig.setPassword(passString);
	    selectedConfig.enterpriseConfig.setAnonymousIdentity(ENTERPRISE_ANON_IDENT);
	    
	    int eap;
	    if(ENTERPRISE_EAP == null)
	    {
	    	eap = WifiEnterpriseConfig.Eap.NONE;
	    }
	    else if(ENTERPRISE_EAP.equalsIgnoreCase("PEAP"))
	    {
	    	eap = WifiEnterpriseConfig.Eap.PEAP;
	    }
	    else if(ENTERPRISE_EAP.equalsIgnoreCase("PWD"))
	    {
	    	eap = WifiEnterpriseConfig.Eap.PWD;
	    }
	    else if(ENTERPRISE_EAP.equalsIgnoreCase("TLS"))
	    {
	    	eap = WifiEnterpriseConfig.Eap.TLS;
	    }
	    else if(ENTERPRISE_EAP.equalsIgnoreCase("TTLS"))
	    {
	    	eap = WifiEnterpriseConfig.Eap.TTLS;
	    }
	    else
	    {
	    	eap = WifiEnterpriseConfig.Eap.NONE;
	    }
	    selectedConfig.enterpriseConfig.setEapMethod(eap);
	    
	    int phase2Method;
	    if(ENTERPRISE_PHASE2 == null)
	    {
	    	phase2Method = WifiEnterpriseConfig.Phase2.NONE;
	    }
	    else if(ENTERPRISE_PHASE2.equalsIgnoreCase("GTC"))
	    {
	    	phase2Method = WifiEnterpriseConfig.Phase2.GTC;
	    }
	    else if(ENTERPRISE_PHASE2.equalsIgnoreCase("MSCHAP") || ENTERPRISE_PHASE2.equalsIgnoreCase("MS-CHAP"))
	    {
	    	phase2Method = WifiEnterpriseConfig.Phase2.MSCHAP;
	    }
	    else if(ENTERPRISE_PHASE2.equalsIgnoreCase("MSCHAPv2") || ENTERPRISE_PHASE2.equalsIgnoreCase("MS-CHAPv2"))
	    {
	    	phase2Method = WifiEnterpriseConfig.Phase2.MSCHAPV2;
	    }
	    else if(ENTERPRISE_PHASE2.equalsIgnoreCase("PAP"))
	    {
	    	phase2Method = WifiEnterpriseConfig.Phase2.PAP;
	    }
	    else
	    {
	    	phase2Method = WifiEnterpriseConfig.Phase2.NONE;
	    }
	    selectedConfig.enterpriseConfig.setPhase2Method(phase2Method);
	    
	    WifiManager wifiManag = (WifiManager) getSystemService(Context.WIFI_SERVICE);
	    boolean res1 = wifiManag.setWifiEnabled(true);
	    int res = wifiManag.addNetwork(selectedConfig);
	    Log.d("WifiPreference", "add Network returned " + res );
	    boolean b = wifiManag.enableNetwork(res, false);
	    Log.d("WifiPreference", "enableNetwork returned " + b );
	    boolean c = wifiManag.saveConfiguration();
	    Log.d("WifiPreference", "Save configuration returned " + c );
	    boolean d = wifiManag.enableNetwork(res, true);   
	    Log.d("WifiPreference", "enableNetwork returned " + d );
	    
	    if(!res1 || res == -1 || !b || !c || !d)
	    {
	    	Toast t = Toast.makeText(getApplicationContext(), "WiFi network connection failed", Toast.LENGTH_LONG);
	    	t.show();
	    	return false;
	    }
	    
	    try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
	    
	    Toast t = Toast.makeText(getApplicationContext(), "Network added", Toast.LENGTH_SHORT);
	    t.show();
	    return true;
	}
    
}
