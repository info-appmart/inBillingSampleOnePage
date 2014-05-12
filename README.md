# Appmart　アプリ内課金

![last-version](http://img.shields.io/badge/last%20version-1.1-green.svg "last version:1.1") 

![license apache 2.0](http://img.shields.io/badge/license-apache%202.0-brightgreen.svg "licence apache 2.0")

Appmartアプリ内課金システムのサンプルコードです。このサンプルをfork・cloneしていただき、自由にご利用ください。 

このサンプルの対象サービスは:

+ アプリ内課金：都度決済 
+ アプリ内課金：継続決済 


---


## Ready-to-useサンプル

本リポジトリをcloneし、5つのパラメータを直してください


> サンプルをclone

```shell
cd /home/user/mydirectory
git clone https://github.com/info-appmart/inBillingSampleOnePage.git
```

> プロジェクトのパラメータを修正


```java
//　ファイル：src/activities/MainActivity.java

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

### 設定+サービス接続

+ AIDLファイルの生成
+ パーミッション追加
+ 決済実行後のBroadcastを設定
+ Appmartアプリに接続+インストール状態を確認
+ ServiceConnectionオブジェクト作成
+ ボタンと連動するhandlerを定義
+ ボタンの実装

### サービスとのやり取り

+ Appmartとの連動
+ アプリのバインド
+ パラメータの暗号化
+ PendingIntentの実行
+ Broadcast情報を取得
+ 決済確定
 
---


### 設定+サービス接続


####  AIDLファイルの生成

Appmartの課金システムサービスとやりとりするために、AIDLファイルを作成する必要があります。
 
| package名              | class名                        |
| ---------------------- |------------------------------- |
|  jp.app_mart.service   |　AppmartInBillingInterface.aidl |

 
```java
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
    String stopContinuePayment(String nextTrnsId, String developerId, String itemId, String appId);
    
    //指定ユーザーの購入履歴（管理対象サービスのみ）
    int hasAlreadyBought (String developerId, String appId, String itemId);

}
```

> 必ず上記7つのメソッドを用意してください 。メソッドの引数・戻り値は【リファレンス】を参照してください。



#### パーミッション追加

appmartを利用するには下記permissionsを追加してください。

> AndroidManifest.xml

```xml
<!-- 課金API用 -->
<uses-permission android:name="jp.app_mart.permissions.APPMART_BILLING" />
```


##### 決済実行後のBroadcastを設定
 

> これからの修正はMainActivityクラス内に行います。
 

決済画面からアプリに戻る際に、Broadcastが発信されるため、**ReceiverBroadcast**をRegisterします。

```java
setReceiver();

private void setReceiver() {
	IntentFilter filter = new IntentFilter(BROADCAST);
	receiver = new AppmartReceiver();
	registerReceiver(receiver, filter);
}
``` 


#### Appmartアプリに接続+インストール状態を確認
 
```java
Intent i = new Intent();
i.setClassName(APP_PACKAGE, APP_PATH);
if (mContext.getPackageManager().queryIntentServices(i, 0).isEmpty()) {
	debugMess(getString(R.string.no_appmart_installed));
	return;
}
```
 
 
#### ServiceConnectionオブジェクト作成
 
RemoteServiceのため、**ServiceConnection**インタフェースを実装しなければなりません。

継承メソッドは**onServiceConnected**(接続時のcallback)と**onServiceDisconnected**(切断持のcallback）です。
 
```java
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
 

#### ボタンと連動するhandlerを定義

ボタンをクリックする際に、別threadでデータ処理を行うため、MainUIのHandlerを用意します。

```java
handler = new Handler() {
   public void handleMessage(Message msg) {
	//処理はここに入ります
   }
}
```

#### ボタンの実装
 
> ボタンが押された時に処理
 
```java
Button paymentButton = (Button) findViewById(R.id.access_payment);
paymentButton.setOnClickListener(new OnClickListener() {
	//処理はここに入ります
}
```

---


### サービスとのやり取り

#### Appmartとの連動
 
アプリとAppmartを連動させるために、先ずはバインドを行います。
 
```java
bindService(i, mConnection, Context.BIND_AUTO_CREATE);
```


#### アプリのバインド
 
**ServiceConnection**が正常に接続すれば、接続フラグをyesに変え、Serviceをバインドします。


```java
service = AppmartInBillingInterface.Stub.asInterface((IBinder) boundService);
isConnected = true;
```

> この時点ではAppmartの課金決済サービスと連動しており、AIDLインタフェースの各メソッドを呼ぶことができます。


#### パラメータの暗号化
 
決済を行う際には必要なパラメータを暗号化し、**prepareForBillingService**メソッドに渡します。
 
 
```java
String dataEncrypted = createEncryptedData(
	APPMART_SERVICE_ID,
	APPMART_DEVELOPER_ID,
	APPMART_LICENSE_KEY, APPMART_PUBLIC_KEY);

Bundle bundleForPaymentInterface = service.prepareForBillingService(APPMART_APP_ID, dataEncrypted);
```

> **createEncryptedData**メソッドはクラスの一番下にありますので、ご参考ください。


**prepareForBillingService**メソッドからreturnされるBundleを確認します。

*resultCode*コードは1でしたら、　決済キー*resultKey*を保存し、Bundleの**PendingIntent**オブジェクトをインスタンス化し、実行します。


####  【PendingIntent】の実行
 
```java
resultKey= bundleForPaymentInterface.getString(RESULT_KEY);
pIntent = bundleForPaymentInterface.getParcelable(PENDING);
pIntent.send(mContext, 0, new Intent());
```

**PendingIntent**を送信すると、Appmartアプリが起動し、決済画面が表示されます。エンドユーザーにデータ入力して、決済を行います。

決済が完了になりましたら、Broadcastが自動的に送信され、Appmartアプリが終了し、アプリに戻ります。


#### Broadcast情報を取得
 
配信された情報を取得します。決済IDが一致するかを確認します。

```java
transactionId = arg1.getExtras().getString(SERVICE_ID);
String resultKeyCurrentStransaction= arg1.getExtras().getString(APPMART_RESULT_KEY);

// 継続決済の場合は次回決済IDを取得
nextTransactionId = arg1.getExtras().getString(SERVICE_NEXT_ID);

if (resultKeyCurrentStransaction!=null && resultKeyCurrentStransaction.equals(resultKey)){
  //エンドユーザーにコンテンツを提供
}
```


####  決済確定
 
この時点では決済が登録されましたが、まだ確定されていないので、最後に決済を確定します。
 
```java
//決済を確認します
int res = service.confirmFinishedTransaction(
		transactionId, APPMART_SERVICE_ID,
		APPMART_DEVELOPER_ID);
```

---

##  リファレンス


###  各メソッドの引数・戻り値


####　prepareForBillingService (決済前)

##### 引数

| No  | 項目名                     | 属性        |  説明       |
| --- |:-------------:| ------ | --------------------------- |
| 1   | appId         | 8 － 30 | 登録済みのアプリID                       |
| 2   | encryptedData | 指定なし  | サービスID, ディベロッパーID等を暗号化したデータ    |

> 暗号化パラメータの詳細はcreateEncryptedDataメソッドをご確認ください。

##### 戻り値


| No  | 項目名   | 説明 |
| --- |:----:| ------------- |
| 1   | Bundle   | Bundle.getInt(“resultCode”) : 1=OK 90=エラー         |

>  nullの場合、接続エラーが発生します。Bundle.getInt(“resultCode”) == 90 の場合は【Msg】もリターンされます（1= アプリIDエラー, 90=暗号化データエラー）


#### getServiceDetails(サービス情報取得)

#####　引数

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | appId         | 8 － 30 | 登録済みのアプリID                       |
| 2   | encryptedData | 指定なし  | サービスID, ディベロッパーID等を暗号化したデータ    |

> 暗号化パラメータの詳細はcreateEncryptedDataメソッドをご確認ください。


#####　戻り値

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



#### confirmFinishedTransaction (決済確定)

##### 引数

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | transactionId         | 13 | トランザクションID                       |
| 2   | itemId | 1 - 30  | 登録済みのサービスID    |
| 3   | developerId | 8 | ディベロッパーID    |

> 暗号化パラメータの詳細はcreateEncryptedDataメソッドをご確認ください。

##### 戻り値

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  90=例外発生  |
| 2   |  | Msg  |  エラーメッセージ　(63 = 決済IDエラー)                       |


#### getPaymentDetails(過去決済の詳細取得)

#####　引数

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | transactionId         | 13 | トランザクションID                       |
| 2   | itemId | 1 - 30  | 登録済みのサービスID    |
| 3   | developerId | 8 | ディベロッパーID    |

> 暗号化パラメータの詳細はcreateEncryptedDataメソッドをご確認ください。

#####　戻り値

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  90=例外発生  |
| 2   |  Application| amount  |  決済金額                      |
| 3   |  Application| setlDt  | 決済日時                     |
| 5   |  Application| setlCrcy  |  通貨                     |
| 2   |  Application| vald  |  有効確認                      |


#### getNextPaymentDetails(継続決済：次回支払日)

> 継続課金の場合のみ

#####　引数

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | nextTransactionId         | 13 | 次回トランザクションID                       |
| 2   | developerId | 8 | ディベロッパーID    |
| 3   | itemId | 1 - 30  | 登録済みのサービスID    |

> 暗号化パラメータの詳細はcreateEncryptedDataメソッドをご確認ください。

#####　戻り値

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  90=例外発生  |
| 2   | transactionId | -          |  トランザクションID  |
| 3   | nextTransLogId | -          |  次回トランザクションID  |



#### stopContinuePayment(継続決済停止)

> 継続課金の場合のみ

#####　引数

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | nextTransactionId         | 13 | 次回トランザクションID                       |
| 2   | developerId | 8 | ディベロッパーID    |
| 3   | itemId | 1 - 30  | 登録済みのサービスID    |
| 4   | appId | 1 - 30  | 登録済みのアプリID    |

> 暗号化パラメータの詳細はcreateEncryptedDataメソッドをご確認ください。

##### 戻り値

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | String | -          |  JSON形式のトランザクション情報 |


#### 　hasAlreadyBought(購入履歴確認)

#####　引数

| No  | 項目名                     | 属性        |  説明                                                                            |
| --- |:-------------:| ------ | ------------------------------------ |
| 1   | appId         | 1 - 30 | アプリID                       |
| 2   | developerId | 8 | ディベロッパーID    |
| 3   | itemId | 1 - 30  | 登録済みのサービスID    |


> 暗号化パラメータの詳細はcreateEncryptedDataメソッドをご確認ください。

##### 戻り値

| No  | 項目名               | サブ項目名        | 説明                                                                               |
| --- |:----------: | ---------- |--------------------------------------|
| 1   | Result_code | -          |  1== OK  90=例外発生  |
| 2   | buyFlg | -          |  購入フラッグ 1== 購入済み　0==未購入  |
| 3   | msg | -          |  エラーメッセージのID  |


> ユーザーはログインしていない状態で【hasAlreadyBought】メソッドを呼び出す場合は【null】がリターンされます。
> ログインさせるために、下記コードをご利用下さい：


```java
Intent intent = new Intent("jp.app_mart.app.LOGIN_ACTIVITY");
startActivityForResult(intent, "1111");
return null;

////
/* ログイン後　呼び出されるメソッド */
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
  if (requestCode == "1111" ) {
    if(resultCode == RESULT_OK){      
	  Utils.debug(mContext, "ログインしました！");		    	 
	}		    
  }
}

```


### エラーメッセージ


| エラーコード     |説明  |
|---------- | ---|
|1|App Idを選択してください。|
|2|対象のアプリが存在しません。|
|3|カテゴリーIDを選択してください。|
|4|AppmartIDを入力してください。|
|5|対象のユーザー情報が存在しません。|
|6|パスワードを入力してください。|
|7|ログインできません。ID、パスワードを確認してください。|
|8|ID、パスワードを確認してください。|
|9|クレジットカード番号は数字16桁以内で入力してください。|
|10|有効期限(月)は数字2桁で入力してください。|
|11|有効期限(年)は数字4桁で入力してください。|
|12|不正なクレジットカード番号です。|
|13|クレジットカード番号を入力してください。|
|14|有効期限(月)を入力してください。|
|15|有効期限(年)を入力してください。|
|16|現在のパスワードを入力してください。|
|17|新パスワードを入力してください。|
|18|新パスワード(確認)を入力してください。|
|19|現在のパスワードは半角英数、7?30文字で入力してください。|
|20|新パスワードは半角英数、7-30文字で入力してください。|
|21|新パスワード(確認)は半角英数、7-30文字で入力してください。|
|22|カード名義を入力してください。|
|23|カード名義は半角英字30文字以内で入力してください。|
|24|APPMART IDは半角英数字6～30文字以内で入力してください。|
|25|対象のAPPMART IDはすでに存在しています。|
|26|ニックネームを入力してください。|
|27|ニックネームは30文字以内で入力してください。|
|28|APPMART IDを入力してください。|
|29|パスワードを入力してください。|
|30|パスワードは英数字混合、7～30文字で入力してください。|
|31|パスワード(確認)を入力してください。|
|32|パスワード(確認)は英数字混合、7～30文字で入力してください。|
|33|パスワードとパスワード(確認)が一致しません。|
|34|性別を選択してください。|
|35|生年月日を正しく入力してください。|
|36|電話番号を正しく入力してください。|
|37|メールアドレスを入力してください。|
|38|メールアドレスを正しく入力してください。|
|39|入力されたメールアドレスは既に存在します。|
|40|現在のパスワードが正しくありません|
|41|決済方法を選択してください。|
|42|金額エラー|
|43|ディベロッパー情報が存在しません。|
|44|対象のアプリは無効です。|
|45|金額を入力してください。|
|46|カード番号が登録されていません。|
|47|カード番号エラー|
|48|カード番号が正しくありません。|
|49|カード有効期限(月）が登録されていません。|
|50|カード有効期限(月）エラー|
|51|カード有効期限(年）が登録されていません。|
|52|カード有効期限(年）エラー|
|53|決済通貨が存在しません。|
|54|既にダウンロード済みです。|
|55|暗号化データがありません。|
|56|秘密鍵データがありません。|
|57|認証エラー、暗号化データのフォーマットが合いません。|
|58|対象サービスデータがありません。|
|59|認証できない、ディベロッパー情報が一致しません。|
|60|認証できない、ライセンスキーが一致しません。|
|61|ユーザーID（カスタマーID）の入力がありません。|
|62|サービスIDの入力がありません。|
|63|トランザクションIDがありません。|
|64|ステータスの入力がありませんもしくは入力エラーです。|
|65|サービスデータがありません。|
|66|ディベロッパーIDの入力がありません。|
|67|対象のサービスは無効です。|
|68|対象のサービスは既に購入済です。|
|69|対象のサービスの継続課金データがありません。|
|70|ご利用中の継続課金データがありません。|
|90|原因不明のエラーが発生した場合。運営者にお問い合わせ下さい。|
