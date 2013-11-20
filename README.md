inBillingSampleOnePage
======================

Appmartアプリ内課金システムのサンプルコードです。簡単にAppmartのアプリ内決済システムをご利用いただけます。
実装に関するご質問ございましたら、お問い合わせください。


#### カスタマイズしなければならないパラメータ

```
//デベロッパＩＤ
APPMART_DEVELOPER_ID = "your_developper_id";

//ライセンスキー
APPMART_LICENSE_KEY = "your_licence_key";

//公開鍵
APPMART_PUBLIC_KEY = "your_public_key";

//アプリＩＤ
APPMART_APP_ID = "your_application_id";

// サービスＩＤ
public static final String APPMART_SERVICE_ID = "your_service_id";
```

#### 本プロジェクトの大まかな流れ：


 *  決済実行後のBroadcastを設定

`setReceiver();` 

 * Appmartサービスに接続するためのIntentオブジェクトを用意
 
```
Intent i = new Intent();
i.setClassName(APP_PACKAGE, APP_PATH);
if (mContext.getPackageManager().queryIntentServices(i, 0).isEmpty()) {
	debugMess(getString(R.string.no_appmart_installed));
	return;
}
```
 
 * ServiceConnectionオブジェクトを用意
 
```
ServiceConnection mConnection = new ServiceConnection() {
	//接続時実行
	public void onServiceConnected(ComponentName name,
			IBinder boundService) {
		//Ｓｅｒｖｉｃｅクラスをインスタンス化
		service = AppmartInBillingInterface.Stub.asInterface((IBinder) boundService);
		isConnected = true;
		debugMess(getString(R.string.appmart_connection_success));
	}
	//切断時実行
	public void onServiceDisconnected(ComponentName name) {
		service = null;
	}
};
```
 
 * ボタンと連動するするhandlerを定義

```
//決済用
handler = new Handler() {
   public void handleMessage(Message msg) {
	。　。　。
   }
}

//管理されているサービスの購入状況
handlerCheck = new Handler() {
   public void handleMessage(Message msg) {
	。　。　。
   }
}
```

 * ボタンの実装
 
```
//決済用ボタン
Button paymentButton = (Button) findViewById(R.id.access_payment);
paymentButton.setOnClickListener(new OnClickListener() {
	。　。　。
}

//管理されているサービスの購入状況用
Button historyButton = (Button) findViewById(R.id.access_payment);
paymentButton.setOnClickListener(new OnClickListener() {
	。　。　。
}
```

