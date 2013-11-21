Appmart　アプリ内課金　 決済サンプル
======================

Appmartアプリ内課金システムのサンプルコードです。このサンプールをfork・cloneしていただき、自由にご利用ください。 (apache 2.0ライセンス)

Pull requestも可能です。

このサンプルの対象サービスは:

| 処理                         | 決済タイプ                | 管理           |
| ------------- |:-------------:| ------: |
| 決済実行処理         | 都度                        | 管理対象    |
| 決済実行処理         | 継続                        | 管理対象    |
| 決済実行処理         | 都度                        | 管理対象外|
| 決済実行処理         | 継続                        | 管理対象外|




> 管理されているサービスに関しまして、ソースコードをご確認ください。


---


#### 引数の設定

下記引数を直してください。 (src/activities/MainActivity.java)

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


---


#### 本プロジェクトの大まかな流れ：

 *  AIDLファイルの生成:
 
Appmartの課金システムサービスとやりとりするために、AIDLファイルを作成する必要があります。
 
jp.app_mart.serviceパッケージを作り、AppmartInBillingInterface.aidlファイルを作ってください。
 
```
package jp.app_mart.service;

import android.os.Bundle;

interface AppmartInBillingInterface {

    //課金前の method
    Bundle prepareForBillingService(String appId, String encryptedData);

    //サービスの詳細情報を取得
    String getServiceDetails(String serviceId, String encryptedData);

    //サービス提供後の method
    int confirmFinishedTransaction(String trnsId, String serviceId, String developerId );

    //トランザクション情報を取得
    String getPaymentDetails(String trnsId, String serviceId, String developerId);

    //次回支払情報を取得
    String getNextPaymentDetails(String nextTrnsId, String developerId, String itemId);

    //継続課金の停止
    String stopContinuePayment(String nextTrnsId, String developerId, String itemId);
    
    //指定ユーザーの購入履歴（管理対象サービスのみ）
    int hasAlreadyBought (String developerId, String appId, String itemId);

}
```

> 必ず上記7つのメッソードを用意してください ！



 *  決済実行後のBroadcastを設定:
 
これからの修正はMainActivityクラス内に行います。
 
決済画面からアプリに戻る際に、Broadcastが発信されるため、ReceiverBroadcastをRegisterします。

```
setReceiver();

private void setReceiver() {
	IntentFilter filter = new IntentFilter(BROADCAST);
	receiver = new AppmartReceiver();
	registerReceiver(receiver, filter);
}
``` 


 * Appmartアプリに接続し、インストール状態を確認:
 
```
Intent i = new Intent();
i.setClassName(APP_PACKAGE, APP_PATH);
if (mContext.getPackageManager().queryIntentServices(i, 0).isEmpty()) {
	debugMess(getString(R.string.no_appmart_installed));
	return;
}
```
 
 
 * ServiceConnectionオブジェクトを用意:
 
RemoteServiceのため、ServiceConnectionインタフェースを実装しなければなりません。継承メッソードはonServiceConnected（接続時のcallback）とonServiceDisconnected（切断持のcallback）です。
 
```
ServiceConnection mConnection = new ServiceConnection() {
	//接続時実行
	public void onServiceConnected(ComponentName name,
			IBinder boundService) {
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

ボタンをクリックする際に、別threadでデータ処理を行うため、MainUIのHandlerを用意します。

```
handler = new Handler() {
   public void handleMessage(Message msg) {
	//処理はここに入ります
   }
}
```

 * ボタンの実装
 
実際にボタンを押下する時に、
 
```

Button paymentButton = (Button) findViewById(R.id.access_payment);
paymentButton.setOnClickListener(new OnClickListener() {
	//処理はここに入ります
}
```


---


#### Appmart課金システムとの具体的な連動

 * Appmartとの連動：
 
アプリとAppmartを連動させるために、先ずはバインドを行います。
 
`bindService(i, mConnection, Context.BIND_AUTO_CREATE);`



 * アプリのバインド:
 
ServiceConnectionが正常に接続すれば、接続フラグをyesに変え、Serviceをバインドします

```
service = AppmartInBillingInterface.Stub.asInterface((IBinder) boundService);
isConnected = true;
```

> この時点ではAppmartの課金決済サービスと連動しており、AIDLインタフェースの各メッソードを呼ぶことができます。



 * パラメータの暗号化:
 
決済を行う際には必要なパラメータを暗号化し、【prepareForBillingService】メッソードに渡します。
 
 
```
String dataEncrypted = createEncryptedData(
	APPMART_SERVICE_ID,
	APPMART_DEVELOPER_ID,
	APPMART_LICENSE_KEY, APPMART_PUBLIC_KEY);

Bundle bundleForPaymentInterface = service.prepareForBillingService(APPMART_APP_ID, dataEncrypted);
```

> 【createEncryptedData】メッソードはクラスの一番下にありますので、ご参考ください。


【prepareForBillingService】メッソードからreturnされるBundleを確認します。【resultCode】コードは1でしたら、　BundleのPendingIntentオブジェクトをインスタンス化し、実行します。



 * 【PendingIntent】の実行:
 
```
pIntent = bundleForPaymentInterface.getParcelable(PENDING);
pIntent.send(mContext, 0, new Intent());
```

PendingIntentを送信すると、Appmartアプリが起動し、決済画面が表示されます。エンドユーザーにデータ入力して、決済を行います。決済が完了になりましたら、Broadcastを送信し、Appmartアプリが終了し、アプリに戻ります。



 * Broadcast情報を取得:

```
transactionId = arg1.getExtras().getString(SERVICE_ID);

// 継続決済の場合は次回決済ＩＤを取得
nextTransactionId = arg1.getExtras().getString(SERVICE_NEXT_ID);

//エンドユーザーにコンテンツを提供
```



 * 決済確定
 
 この時点では決済が登録されましたが、まだ確定されていないので、最後に決済を確定します。
 
```
//決済を確認します
int res = service.confirmFinishedTransaction(
		transactionId, APPMART_SERVICE_ID,
		APPMART_DEVELOPER_ID);
```
