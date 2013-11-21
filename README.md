Appmart　アプリ内課金
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


## 引数の設定

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


## 本プロジェクトの大まかな流れ：

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
    String getServiceDetails(String itemId, String encryptedData);

    //サービス提供後の method
    int confirmFinishedTransaction(String trnsId, String itemId, String developerId );

    //トランザクション情報を取得
    String getPaymentDetails(String trnsId, String itemId, String developerId);

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


## Appmart課金システムとの具体的な連動

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

---

##  リファレンス


###  各メッソードの引数・戻り値

> メッソード一覧は【AIDLファイルの生成】を参照してください。


#####　prepareForBillingService

■■　引数:

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | appId         | 8 － 30 | 登録済みのアプリID                       |
| 2   | encryptedData | 指定なし  | サービスID, ディベロッパーID等を暗号化したデータ    |

> 暗号化パラメータの詳細はcreateEncryptedDataメッソードをご確認ください。

■■　戻り値:

| No  | 項目名                     | 説明                                                                                                  |
| --- |:-------------:| --------------------------------------------- |
| 1   | Bundle        | Bundle.getInt(“resultCode”) : 1=OK 90=エラー         |

>  nullの場合、接続エラーが発生します。Bundle.getInt(“resultCode”) == 90 の場合は【Msg】もリターンされます（1= アプリIDエラー, 90=暗号化データエラー）







##### 　getServiceDetails

■■　引数:

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | appId         | 8 － 30 | 登録済みのアプリID                       |
| 2   | encryptedData | 指定なし  | サービスID, ディベロッパーID等を暗号化したデータ    |

> 暗号化パラメータの詳細はcreateEncryptedDataメッソードをご確認ください。


■■　戻り値:

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  55=暗号化データエラー   90=例外発生  |
| 2   | Application | developId  |  ディベロッパーID                         |
| 3   |             | itemId  |  サービスID                             |
| 4   |             | serviceName|  サービス名                                                                    |
| 5   |             | DiscountAmount|  Appmartでの販売価格 (セール期間考慮済み)  |
| 6   |             | exp|  サービス説明                                                                  |
| 7   |             | policy|  サービスのポリシー                                                                 |
| 8   |             | appName|  アプリ名                                                                 |
| 9   |             | setlCrcy|  通貨                                                                |
| 10   |             | SetlType|  販売タイプ   0=都度　1=月額課金                                                                |
| 11   |             | MonthCycle|  月サイクル                                                                |
| 12   |             | DayCycle|  日サイクル                                                                |
| 13   |             | CntCycle|  継続回数                                                                |
| 14   |             | discountStartDt|  割引開始日                                                                |
| 15   |             | discountEndDt|  割引終了日                                                                |
| 16   |             | discountRate|  割引率                                                                |
| 17   |             | logoImagePath|  ロゴファイル名                                                                |
| 18   |             | SetlType|  販売タイプ 0=都度　 1==月額課金                                                                |
| 19   |             | Price|  定価                                                            |

>  nullの場合、接続エラーが発生します。Bundle.getInt(“resultCode”) == 90 の場合は【Msg】もリターンされます（1= アプリIDエラー, 90=暗号化データエラー）





##### 　confirmFinishedTransaction

■■　引数:

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | transactionId         | 13 | トランザクションID                       |
| 2   | itemId | 1 - 30  | 登録済みのサービスID    |
| 3   | developerId | 8 | ディベロッパーID    |

> 暗号化パラメータの詳細はcreateEncryptedDataメッソードをご確認ください。

■■　戻り値:

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  90=例外発生  |
| 2   |  | Msg  |  エラーメッセージ　(63 = 決済IDエラー)                       |








#####　getPaymentDetails

■■　引数:

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | transactionId         | 13 | トランザクションID                       |
| 2   | itemId | 1 - 30  | 登録済みのサービスID    |
| 3   | developerId | 8 | ディベロッパーID    |

> 暗号化パラメータの詳細はcreateEncryptedDataメッソードをご確認ください。

■■　戻り値:

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  90=例外発生  |
| 2   |  Application| amount  |  決済金額                      |
| 3   |  Application| setlDt  | 決済日時                     |
| 5   |  Application| setlCrcy  |  通貨                     |
| 2   |  Application| vald  |  有効確認                      |






#####　getNextPaymentDetails

> 継続課金の場合のみ

■■　引数:

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | nextTransactionId         | 13 | 次回トランザクションID                       |
| 2   | developerId | 8 | ディベロッパーID    |
| 3   | itemId | 1 - 30  | 登録済みのサービスID    |

> 暗号化パラメータの詳細はcreateEncryptedDataメッソードをご確認ください。

■■　戻り値:

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  90=例外発生  |
| 2   | transactionId | -          |  トランザクションID  |
| 3   | nextTransLogId | -          |  次回トランザクションID  |








##### 　stopContinuePayment

> 継続課金の場合のみ

■■　引数:

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | nextTransactionId         | 13 | 次回トランザクションID                       |
| 2   | developerId | 8 | ディベロッパーID    |
| 3   | itemId | 1 - 30  | 登録済みのサービスID    |

> 暗号化パラメータの詳細はcreateEncryptedDataメッソードをご確認ください。

■■　戻り値:

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | String | -          |  JSON形式のトランザクション情報 |






##### 　hasAlreadyBought


■■　引数:

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | appId         | 1 - 30 | アプリID                       |
| 2   | developerId | 8 | ディベロッパーID    |
| 3   | itemId | 1 - 30  | 登録済みのサービスID    |


> 暗号化パラメータの詳細はcreateEncryptedDataメッソードをご確認ください。

■■　戻り値:

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  90=例外発生  |
| 2   | transactionId | -          |  トランザクションID  |
| 3   | nextTransLogId | -          |  次回トランザクションID  |



### エラーメッセージ


| No  | エラーコード     |説明  |
| --- |---------- | ---|
|1|1|App Idを選択してください。|
|2|2|対象のアプリが存在しません。|
|3|3|カテゴリーIDを選択してください。|
|4|4|AppmartIDを入力してください。|
|5|5|対象のユーザー情報が存在しません。|
|6|6|パスワードを入力してください。|
|7|7|ログインできません。ID、パスワードを確認してください。|
|8|8|ID、パスワードを確認してください。|
|9|9|クレジットカード番号は数字16桁以内で入力してください。|
|10|10|有効期限(月)は数字2桁で入力してください。|
|11|11|有効期限(年)は数字4桁で入力してください。|
|12|12|不正なクレジットカード番号です。|
|13|13|クレジットカード番号を入力してください。|
|14|14|有効期限(月)を入力してください。|
|15|15|有効期限(年)を入力してください。|
|16|16|現在のパスワードを入力してください。|
|17|17|新パスワードを入力してください。|
|18|18|新パスワード(確認)を入力してください。|
|19|19|現在のパスワードは半角英数、7?30文字で入力してください。|
|20|20|新パスワードは半角英数、7-30文字で入力してください。|
|21|21|新パスワード(確認)は半角英数、7-30文字で入力してください。|
|22|22|カード名義を入力してください。|
|23|23|カード名義は半角英字30文字以内で入力してください。|
|24|24|APPMART IDは半角英数字6～30文字以内で入力してください。|
|25|25|対象のAPPMART IDはすでに存在しています。|
|26|26|ニックネームを入力してください。|
|27|27|ニックネームは30文字以内で入力してください。|
|28|28|APPMART IDを入力してください。|
|29|29|パスワードを入力してください。|
|30|30|パスワードは英数字混合、7～30文字で入力してください。|
|31|31|パスワード(確認)を入力してください。|
|32|32|パスワード(確認)は英数字混合、7～30文字で入力してください。|
|33|33|パスワードとパスワード(確認)が一致しません。|
|34|34|性別を選択してください。|
|35|35|生年月日を正しく入力してください。|
|36|36|電話番号を正しく入力してください。|
|37|37|メールアドレスを入力してください。|
|38|38|メールアドレスを正しく入力してください。|
|39|39|入力されたメールアドレスは既に存在します。|
|40|40|現在のパスワードが正しくありません|
|41|41|決済方法を選択してください。|
|42|42|金額エラー|
|43|43|ディベロッパー情報が存在しません。|
|44|44|対象のアプリは無効です。|
|45|45|金額を入力してください。|
|46|46|カード番号が登録されていません。|
|47|47|カード番号エラー|
|48|48|カード番号が正しくありません。|
|49|49|カード有効期限(月）が登録されていません。|
|50|50|カード有効期限(月）エラー|
|51|51|カード有効期限(年）が登録されていません。|
|52|52|カード有効期限(年）エラー|
|53|53|決済通貨が存在しません。|
|54|54|既にダウンロード済みです。|
|55|55|暗号化データがありません。|
|56|56|秘密鍵データがありません。|
|57|57|認証エラー、暗号化データのフォーマットが合いません。|
|58|58|対象サービスデータがありません。|
|59|59|認証できない、ディベロッパー情報が一致しません。|
|60|60|認証できない、ライセンスキーが一致しません。|
|61|61|ユーザーID（カスタマーID）の入力がありません。|
|62|62|サービスIDの入力がありません。|
|63|63|トランザクションIDがありません。|
|64|64|ステータスの入力がありませんもしくは入力エラーです。|
|65|65|サービスデータがありません。|
|66|66|ディベロッパーIDの入力がありません。|
|67|67|対象のサービスは無効です。|
|68|68|対象のサービスは既に購入済です。|
|69|69|対象のサービスの継続課金データがありません。|
|70|70|ご利用中の継続課金データがありません。|
|71|90|原因不明のエラーが発生した場合。運営者にお問い合わせ下さい。|




