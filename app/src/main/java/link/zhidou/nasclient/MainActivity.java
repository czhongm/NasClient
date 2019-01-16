package link.zhidou.nasclient;

import android.content.Intent;
import android.net.VpnService;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import link.zhidou.libraryn2n.service.N2NService;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CREATE_VPN = 1;
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.web_view);
        mWebView.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if(!TextUtils.isEmpty(title)){
                    setTitle(title);
                }
            }
        });
        mWebView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

    }

    public void onConnect(View view) {
        Intent vpnPrepareIntent = VpnService.prepare(this);
        if (vpnPrepareIntent != null) {
            startActivityForResult(vpnPrepareIntent, REQUEST_CREATE_VPN);
        } else {
            onActivityResult(REQUEST_CREATE_VPN, RESULT_OK, null);
        }
    }

    public void onDisconnect(View view) {
        N2NService.stopNasVpn();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == REQUEST_CREATE_VPN && resultCode == RESULT_OK){
            //开启N2n
            N2NService.startNasVpn(this,"10.11.0.1","800001000005","CE:1C:4C:EF:6F:42");
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void onViewWeb(View view) {
        mWebView.loadUrl("http://10.11.0.1");
    }
}
