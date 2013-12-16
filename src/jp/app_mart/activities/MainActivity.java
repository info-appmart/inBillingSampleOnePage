package jp.app_mart.activities;

import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

import jp.app_mart.service.AppmartInBillingInterface;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @copyright Appmart(c) の内部課金システムサンプルコードです。 
 */
public class MainActivity extends Activity {
	
	// デベロッパＩＤ
	public static final String APPMART_DEVELOPER_ID = "your_developer_id";
	// ライセンスキー
	public static final String APPMART_LICENSE_KEY = "your_license_key";
	// 公開鍵
	public static final String APPMART_PUBLIC_KEY = "your_public_key";
	// アプリＩＤ
	public static final String APPMART_APP_ID = "your_application_id";
	// サービスＩＤ
	public static final String APPMART_SERVICE_ID = "your_service_id";
	
	// aidlファイルから生成されたサービスクラス
	private AppmartInBillingInterface service;
	// 接続状態
	private boolean isConnected = false;
	// appmart package
	public static final String APP_PACKAGE = "jp.app_mart";
	// サービスパス
	public static final String APP_PATH = "jp.app_mart.service.AppmartInBillingService";
	
	// ＤＥＢＵＧ
	private boolean isDebug = true;	
	// アプリコンテキスト
	private Context mContext;
	// thread用のhandler
	private Handler handler = new Handler();
	// pendingIntent
	PendingIntent pIntent;
	// 決済ID
	private String transactionId;
	//決済キー
	private String resultKey;
	//次回決済ＩＤ
	private String nextTransactionId;
	// BroadcastReceiver(決済後）
	private AppmartReceiver receiver;
	private ServiceConnection mConnection;
	
	public static final String RESULT_CODE = "resultCode";
	public static final String RESULT_KEY = "resultKey";
	public static final String PENDING = "appmart_pending_intent";
	public static final String BROADCAST = "appmart_broadcast_return_service_payment";
	public static final String SERVICE_ID = "appmart_service_trns_id";
	public static final String APPMART_RESULT_KEY = "appmart_result_key";	
	public static final String SERVICE_NEXT_ID = "appmart_service_next_trns_id";
	
	TextView success;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mContext = getApplicationContext();

		success = (TextView) findViewById(R.id.success_tv);
		
		// 決済後のbroadcastをキャッチ
		setReceiver();

		// appmartサービスに接続するためのIntentオブジェクトを生成
		Intent i = new Intent();
		i.setClassName(APP_PACKAGE, APP_PATH);
		if (mContext.getPackageManager().queryIntentServices(i, 0).isEmpty()) {
			debugMess(getString(R.string.no_appmart_installed));
			return;
		}

		// Service Connectionインスタンス化
		mConnection = new ServiceConnection() {
			
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

		// bindServiceを利用し、サービスに接続
		try {
			bindService(i, mConnection, Context.BIND_AUTO_CREATE);
		} catch (Exception e) {
			e.printStackTrace();
			debugMess(getString(R.string.appmart_connection_not_possible));
		}

		// Handler初期化
		handler = new Handler() {
			@SuppressLint("HandlerLeak")
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case 1: // pendingIntent取得
					accessPaymentPage();
					break;
				case 2:// パラメータNG
					debugMess(getString(R.string.wrong_parameters));
					break;
				case 3:// 例外発生
					debugMess(getString(R.string.exception_occured));
					break;
				case 10:// 決済最終確認完了					
					TextView success = (TextView) findViewById(R.id.success_tv);
					success.setVisibility(View.VISIBLE);					
					break;
				case -10:// 決済最終確認エラー
					debugMess(getString(R.string.settlement_not_confirmed));
					break;
				}
			}
		};
		

		// 決済画面を呼ぶボタン
		Button paymentButton = (Button) findViewById(R.id.access_payment);
		paymentButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				//接続状態の確認
				if (isConnected) {

					debugMess(getString(R.string.start_information_handle));

					(new Thread(new Runnable() {
						public void run() {
							try {

								// 必要なデータを暗号化
								String dataEncrypted = createEncryptedData(
										APPMART_SERVICE_ID,
										APPMART_DEVELOPER_ID,
										APPMART_LICENSE_KEY, APPMART_PUBLIC_KEY);

								// サービスのprepareForBillingServiceメソッドを呼びます
								Bundle bundleForPaymentInterface = service.prepareForBillingService(
												APPMART_APP_ID, dataEncrypted);

								if (bundleForPaymentInterface != null) {
									
									int statusId = bundleForPaymentInterface.getInt(RESULT_CODE);
									if (statusId != 1) {
										handler.sendEmptyMessage(2);
										return;
									} else {

										// PendingIntentを取得
										pIntent = bundleForPaymentInterface.getParcelable(PENDING);
										
										// 決済キーを取得
										resultKey= bundleForPaymentInterface.getString(RESULT_KEY);
										
										// mainUIに設定
										handler.sendEmptyMessage(1);
									}

								}

							} catch (Exception e) {
								handler.sendEmptyMessage(3);
								e.printStackTrace();
							}

						}
					})).start();
				}
			}
		});
		
	}
	
	/*　BroadcastReceiverの設定 */
	private void setReceiver() {
		// Broadcast設定
		IntentFilter filter = new IntentFilter(BROADCAST);
		receiver = new AppmartReceiver();
		registerReceiver(receiver, filter);
	}

	/* onDestroy */
	@Override
	protected void onDestroy() {

		super.onDestroy();

		// appmartサービスからアンバインド
		unbindService(mConnection);
		service = null;

		// broadcast停止
		unregisterReceiver(receiver);

	}

	/* 課金画面へリダイレクト */
	private void accessPaymentPage() {
		try {
			pIntent.send(mContext, 0, new Intent());
		} catch (CanceledException e) {
			e.printStackTrace();
		}
	}

	/* debug用 */
	private void debugMess(String mess) {
		if (isDebug) {
			Log.d("DEBUG", mess);
			Toast.makeText(getApplicationContext(), mess, Toast.LENGTH_SHORT)
					.show();
		}
	}

	/*決済完了後のbroadcastをcatchするReceiverクラス */
	private class AppmartReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context arg0, Intent arg1) {

			try {

				debugMess(getString(R.string.settlement_confirmed));

				// 決済ＩＤを取得
				transactionId = arg1.getExtras().getString(SERVICE_ID);
				
				//決済キー
				String resultKeyCurrentStransaction= arg1.getExtras().getString(APPMART_RESULT_KEY);
				
				//Appmart1.2以下は決済キーが発行されない
				if (resultKeyCurrentStransaction==null || resultKeyCurrentStransaction.equals(resultKey)){
								
					// 継続決済の場合は次回決済ＩＤを取得
					nextTransactionId = arg1.getExtras().getString(SERVICE_NEXT_ID);
	
					// コンテンツを提供し、ＤＢを更新
					Thread.sleep(1000);
	
					// 決済を確認
					(new Thread(new Runnable() {
						public void run() {
	
							try {
	
								int res = service.confirmFinishedTransaction(
										transactionId, APPMART_SERVICE_ID,
										APPMART_DEVELOPER_ID);
	
								if (res == 1) {
									handler.sendEmptyMessage(10);
								} else {
									handler.sendEmptyMessage(-10);
								}
	
							} catch (Exception e) {
								handler.sendEmptyMessage(3);
								e.printStackTrace();
							}
	
						}
					})).start();
				
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/* 引数暗号化 */
	public String createEncryptedData(String serviceId, String developId,
			String strLicenseKey, String strPublicKey) {

		final String SEP_SYMBOL = "&";
		StringBuilder infoDataSB = new StringBuilder();
		infoDataSB.append(serviceId).append(SEP_SYMBOL);

		// デベロッパID引数を追加
		infoDataSB.append(developId).append(SEP_SYMBOL);

		// ライセンスキー引数を追加
		infoDataSB.append(strLicenseKey);

		String strEncryInfoData = "";

		try {
			KeyFactory keyFac = KeyFactory.getInstance("RSA");
			KeySpec keySpec = new X509EncodedKeySpec(Base64.decode(
					strPublicKey.getBytes(), Base64.DEFAULT));
			Key publicKey = keyFac.generatePublic(keySpec);

			if (publicKey != null) {
				Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
				cipher.init(Cipher.ENCRYPT_MODE, publicKey);

				byte[] EncryInfoData = cipher.doFinal(infoDataSB.toString()
						.getBytes());
				strEncryInfoData = new String(Base64.encode(EncryInfoData,
						Base64.DEFAULT));
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			strEncryInfoData = "";
			debugMess(getString(R.string.data_encryption_failed));
		}

		return strEncryInfoData.replaceAll("(\\r|\\n)", "");

	}
}
