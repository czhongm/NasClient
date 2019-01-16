package link.zhidou.libraryn2n.service;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

import link.zhidou.libraryn2n.BuildConfig;


/**
 * 基于N2N的VPN服务
 */
public class N2NService extends VpnService {

    private static final String TAG = "N2NService";
    public static N2NService INSTANCE;

    private ParcelFileDescriptor mParcelFileDescriptor = null;

    static {
        System.loadLibrary("uip");
        System.loadLibrary("n2n");
        System.loadLibrary("edge");
    }

    public class NasParam {
        public String nasIp;
        public String nasSerialNo;
        public String nasMac;
        public String ipAddr;
        public int vpnFd;
        public String mac;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
    }

    /**
     * 开启连接到Nas设备的VPN
     *
     * @param context Context
     * @param ip NAS设备IP
     * @param serialNo NAS设备序列号
     * @param mac NAS设备MAC地址
     */
    public static void startNasVpn(Context context, String ip, String serialNo, String mac){
        if(INSTANCE == null) {
            Intent intent = new Intent(context, N2NService.class);
            intent.putExtra("ip", ip);
            intent.putExtra("serialNo", serialNo);
            intent.putExtra("mac", mac);
            context.startService(intent);
        }
    }

    /**
     * 停止Nas设备VPN
     */
    public static void stopNasVpn(){
        if(INSTANCE != null){
            INSTANCE.stop();
        }
    }

    public static boolean isRunning(){
        if(INSTANCE == null) return false;
        return INSTANCE.getEdgeStatus();
    }

    /**
     * 检测IP地址是否有效
     * @param ip IP地址
     * @return 是否有效
     */
    private boolean isValidIp(String ip){
        if (ip == null || ip.length() < 7 || ip.length() > 15) {
            return false;
        }
        String[] split = ip.split("\\.");
        if (split.length != 4) {
            return false;
        }
        try {
            for (String aSplit : split) {
                int n = Integer.parseInt(aSplit);
                if (n < 0 || n > 255 || !String.valueOf(n).equals(aSplit)) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * 获取自己的ip地址
     * @param nasIp NAS设备的IP
     * @return ip地址
     */
    private String getMyIp(String nasIp){
        if(!isValidIp(nasIp)) return null;
        String[] split = nasIp.split("\\.");
        if(split.length != 4) return null;
        try {
            int n = Integer.parseInt(split[3]);
            Random random = new Random();
            do {
                int last = random.nextInt(255);
                if(last>0 && last<255 && last!=n) {
                    split[3] = String.valueOf(last);
                    return TextUtils.join(".",split);
                }
            }while(true);
        }catch (Exception ex){
            return null;
        }
    }

    /**
     * 获取随机MAC
     * @return MAC
     */
    private static String getRandomMac() {
        String mac = "", hex="0123456789abcdef";
        Random rand = new Random();
        for (int i = 0; i < 17; ++i)
        {
            if ((i + 1) % 3 == 0) {
                mac += ':';
                continue;
            }
            mac += hex.charAt(rand.nextInt(16));
        }
        return mac;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(getEdgeStatus()){
            return super.onStartCommand(intent,flags,startId);
        }

        NasParam param = new NasParam();
        //获取参数
        param.nasIp = intent.getStringExtra("ip");
        param.nasSerialNo = intent.getStringExtra("serialNo");
        param.nasMac = intent.getStringExtra("mac");
        param.ipAddr = getMyIp(param.nasIp);
        param.mac = getRandomMac();

        //设置VPN本机ip
        Builder b = new Builder();
        b.setMtu(1400); //MTU
        String ipAddress = param.ipAddr;
        if(ipAddress == null){
            Toast.makeText(INSTANCE, "Parameter is error.", Toast.LENGTH_SHORT).show();
            return super.onStartCommand(intent, flags, startId);
        }
        int netMask = getIpAddrPrefixLength("255.255.255.0");
        b.addAddress(ipAddress, netMask);
        String route = getRoute(ipAddress, netMask);
        b.addRoute(route, netMask);

        try {
            mParcelFileDescriptor = b.setSession("NasClient").establish();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Toast.makeText(INSTANCE, "Parameter is not accepted by the operating system.", Toast.LENGTH_SHORT).show();
            return super.onStartCommand(intent, flags, startId);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Toast.makeText(INSTANCE, "Parameter cannot be applied by the operating system.", Toast.LENGTH_SHORT).show();
            return super.onStartCommand(intent, flags, startId);
        }

        if (mParcelFileDescriptor == null) {
            Toast.makeText(INSTANCE, "~error~", Toast.LENGTH_SHORT).show();
            return super.onStartCommand(intent, flags, startId);
        }

        param.vpnFd = mParcelFileDescriptor.detachFd();

        try {
            startEdge(param);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void stop() {
        stopEdge();

        try {
            if (mParcelFileDescriptor != null) {
                mParcelFileDescriptor.close();
                mParcelFileDescriptor = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopSelf();
    }

    @Override
    public void onRevoke() {
        super.onRevoke();
        stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        INSTANCE = null;
    }

    private native boolean startEdge(NasParam cmd);

    private native void stopEdge();

    private native boolean getEdgeStatus();

    /**
     * 上报状态(由JNI调用)
     * @param isRunning 是否运行状态
     */
    public void reportEdgeStatus(boolean isRunning) {

    }

    public void reportEdgeExit(){
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "edge stopped ");
        }
    }


    private int getIpAddrPrefixLength(String netmask) {
        try {
            byte[] byteAddr = InetAddress.getByName(netmask).getAddress();
            int prefixLength = 0;
            for (int i = 0; i < byteAddr.length; i++) {
                for (int j = 0; j < 8; j++) {
                    if ((byteAddr[i] << j & 0xFF) != 0) {
                        prefixLength++;
                    } else {
                        return prefixLength;
                    }
                }
            }
            return prefixLength;
        } catch (Exception e) {
            return -1;
        }
    }

    private String getRoute(String ipAddr, int prefixLength) {
        byte[] arr = {(byte) 0x00, (byte) 0x80, (byte) 0xC0, (byte) 0xE0, (byte) 0xF0, (byte) 0xF8, (byte) 0xFC, (byte) 0xFE, (byte) 0xFF};

        if (prefixLength > 32 || prefixLength < 0) {
            return "";
        }
        try {
            byte[] byteAddr = InetAddress.getByName(ipAddr).getAddress();
            int idx = 0;
            while (prefixLength >= 8) {
                idx++;
                prefixLength -= 8;
            }
            if (idx < byteAddr.length) {
                byteAddr[idx++] &= arr[prefixLength];
            }
            for (; idx < byteAddr.length; idx++) {
                byteAddr[idx] = (byte) 0x00;
            }
            return InetAddress.getByAddress(byteAddr).getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }
}
