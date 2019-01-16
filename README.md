# ZhiDou NAS设备Android创建VPN用Service [![](https://jitpack.io/v/czhongm/NasClient.svg)](https://jitpack.io/#czhongm/NasClient)

## 功能
用于创建基于n2n的VPN连接至NAS设备

## 使用方法

1. build.gradle添加依赖
```groovy
repositories {
    maven { url "https://jitpack.io" }
}
dependencies {
    implementation 'com.github.czhongm:NasClient:<version>'
}
```
2. 一般使用方法示例

```java

    /**
     * 连接VPN
     * @param view view
     */
    public void onConnect(View view) {
        //因为在安卓中创建VPN是个危险权限，必须获得用户确认，所以先调用VpnService.prepare
        Intent vpnPrepareIntent = VpnService.prepare(this);
        if (vpnPrepareIntent != null) { //未获取权限的
            startActivityForResult(vpnPrepareIntent, REQUEST_CREATE_VPN);
        } else {//有权限的话，
            startVpn();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == REQUEST_CREATE_VPN && resultCode == RESULT_OK){ //获取到VPN权限
            startVpn();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 断开VPN
     * @param view view
     */
    public void onDisconnect(View view) {
        N2NService.stopNasVpn();
    }

    /**
     * 开始vpn
     */
    private void startVpn(){
        //开启N2n
        N2NService.startNasVpn(this,"10.11.0.1","800001000005","CE:1C:4C:EF:6F:42");
    }

```

3. 获取VPN运行与否

```java
    N2NService.isRunning()
```

