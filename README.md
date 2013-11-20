inBillingSampleOnePage
======================

Appmartアプリ内課金システムのサンプルコードです。

簡単にAppmartのアプリ内決済システムをご利用いただけます。
実装に関するご質問ございましたら、お問い合わせください。

【管理】されているサービスでもご利用いただけます。

> 管理されているサービスに関しまして、【historyButton】を使うことにより、エンドユーザーは過去当サービスを購入したことあるかどうかをご確認いただけます。

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

#### Appmart課金システムとの連動

 * アプリとAppmartを連動させるために、先ずはバインドを行います
 
`bindService(i, mConnection, Context.BIND_AUTO_CREATE);`


 * ServiceConnectionが正常に接続すれば、接続フラグをyesに変え、Serviceをバインドします

```
service = AppmartInBillingInterface.Stub.asInterface((IBinder) boundService);
isConnected = true;
```

この時点ではAppmartの課金決済サービスと連動しており、AIDLインタフェースの各メッソードを呼ぶことができます。

 * 決済を行う際には必要なパラメータを暗号化し、【prepareForBillingService】メッソードに渡します。
 
```
String dataEncrypted = createEncryptedData(
	APPMART_SERVICE_ID,
	APPMART_DEVELOPER_ID,
	APPMART_LICENSE_KEY, APPMART_PUBLIC_KEY);

Bundle bundleForPaymentInterface = service.prepareForBillingService(APPMART_APP_ID, dataEncrypted);
```

> 【createEncryptedData】メッソードはクラスの一番下にありますので、ご参考ください。


【prepareForBillingService】メッソードからreturnされるBundleを確認します。【resultCode】コードは1でしたら、　BundleのPendingIntentオブジェクトをインスタンス化して、実行します。

 * 【PendingIntent】の実行
 
```
pIntent = bundleForPaymentInterface.getParcelable(PENDING);
pIntent.send(mContext, 0, new Intent());
```

PendingIntentを送信すると、Appmartアプリが起動し、決済画面が表示されます。エンドユーザーにデータ入力して、決済を行います。決済が完了になりましたら、Broadcastを送信し、Appmartアプリが終了し、アプリに戻ります。

 * Broadcast情報を取得

```
transactionId = arg1.getExtras().getString(SERVICE_ID);

// 継続決済の場合は次回決済ＩＤを取得
nextTransactionId = arg1.getExtras().getString(SERVICE_NEXT_ID);

//エンドユーザーにコンテンツを提供
```

 * 最後に決済を確認します
 
```
//決済を確認します
int res = service.confirmFinishedTransaction(
		transactionId, APPMART_SERVICE_ID,
		APPMART_DEVELOPER_ID);
```
