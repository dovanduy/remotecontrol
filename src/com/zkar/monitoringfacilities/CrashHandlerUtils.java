package com.zkar.monitoringfacilities;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

//本地异常记录
public class CrashHandlerUtils implements UncaughtExceptionHandler {
	private static final String TAG = "CrashHandler";
	private Thread.UncaughtExceptionHandler mDefaultHandler;// 系统默认的UncaughtException处理类
	private static CrashHandlerUtils INSTANCE = new CrashHandlerUtils();// CrashHandler实例
	private Context mContext;// 程序的Context对象
	private Map<String, String> info = new HashMap<String, String>();// 用来存储设备信息和异常信息
	private SimpleDateFormat format = new SimpleDateFormat(
			"yyyy-MM-dd-HH-mm-ss");// 用于格式化日期,作为日志文件名的一部分

	public static final int MSG_EMAIL = 0;
	public static final int MSG_FILE = 1;
	private int type;
//	private MyApplication myapplication;

	/** 保证只有一个CrashHandler实例 */
	private CrashHandlerUtils() {

	}

	/** 获取CrashHandler实例 ,单例模式 */
	public static CrashHandlerUtils getInstance() {

		return INSTANCE;
	}

	/**
	 * 设置该CrashHandler为程序的默认处理器
	 * 
	 * @param context
	 */
	public void init(Context context, int type) {
		mContext = context;
		this.type = type;
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();// 获取系统默认的UncaughtException处理器
		Thread.setDefaultUncaughtExceptionHandler(this);// 设置该CrashHandler为程序的默认处理器

	}

	/**
	 * 重写@：当UncaughtException发生时会转入该重写的方法来处理
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable ex) {

		if (!handleException(ex) && mDefaultHandler != null) {
			// 如果自定义的没有处理则让系统默认的异常处理器来处理
			mDefaultHandler.uncaughtException(thread, ex);
		} else {
			try {
				Thread.sleep(3000);// 如果处理了，让程序继续运行3秒再退出，保证文件保存并上传到服务器
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		// Intent intent = new Intent(myapplication.getApplicationContext(),
		// LockScreenActivity.class);
		// PendingIntent restartIntent = PendingIntent.getActivity(
		// myapplication.getApplicationContext(), 0, intent,
		// Intent.FLAG_ACTIVITY_NEW_TASK);
		// // //退出程序
		// AlarmManager mgr =
		// (AlarmManager)myapplication.getSystemService(Context.ALARM_SERVICE);
		// mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000,
		// restartIntent); // 1秒钟后重启应用
		// myapplication.finishActivity();
		
		//重启机器
//		restartSystem();
		// 退出程序,
		android.os.Process.killProcess(android.os.Process.myPid());
		System.exit(1);

	}

	/**
	 * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
	 * 
	 * @param ex
	 *            异常信息
	 * @return true 如果处理了该异常信息;否则返回false.
	 */
	public boolean handleException(Throwable ex) {
		if (ex == null)
			return false;
		new Thread() {
			public void run() {
				Looper.prepare();
				Toast.makeText(mContext, "很抱歉,程序出现异常,即将退出", 1).show();
				Looper.loop();
			}
		}.start();
		// 收集设备参数信息
		collectDeviceInfo(mContext, info);
		// 保存日志文件
		saveCrashInfo2File(ex);
		return true;
	}

	/**
	 * 收集设备参数信息
	 * 
	 * @param context
	 */
	public static void collectDeviceInfo(Context context,
			Map<String, String> info) {
		try {
			PackageManager pm = context.getPackageManager();// 获得包管理器
			PackageInfo pi = pm.getPackageInfo(context.getPackageName(),
					PackageManager.GET_ACTIVITIES);// 得到该应用的信息，即主Activity
			if (pi != null) {
				String versionName = pi.versionName == null ? "null"
						: pi.versionName;
				String versionCode = pi.versionCode + "";
				info.put("versionName", versionName);
				info.put("versionCode", versionCode);
			}
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		Field[] fields = Build.class.getDeclaredFields();// 反射机制
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				info.put(field.getName(), field.get("").toString());

			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 把錯誤信息保存本地。
	 * @param ex
	 * @return
	 */
	private String saveCrashInfo2File(Throwable ex) {
		StringBuffer sb = new StringBuffer();
		for (Map.Entry<String, String> entry : info.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			sb.append(key + "=" + value + "\r\n");
		}
		Writer writer = new StringWriter();
		PrintWriter pw = new PrintWriter(writer);
		ex.printStackTrace(pw);
		Throwable cause = ex.getCause();
		// 循环着把所有的异常信息写入writer中
		while (cause != null) {
			cause.printStackTrace(pw);
			cause = cause.getCause();
		}
		pw.close();// 记得关闭
		String result = writer.toString();
		sb.append(result);
		// 保存文件
		long timetamp = System.currentTimeMillis();
		String time = format.format(new Date());
		String fileName = "crash-" + time + "-" + timetamp + ".log";
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			try {
				//获取外部存储的路径返回绝对路径的,其实就是你的SD卡的文件路径
				File dir = new File(Environment.getExternalStorageDirectory()
						.getAbsolutePath() + File.separator + "ErrorInfo");
				Log.i("CrashHandler", dir.toString());
				if (!dir.exists())
					dir.mkdir();
				FileOutputStream fos = new FileOutputStream(new File(dir,
						fileName));
				fos.write(sb.toString().getBytes());
				fos.close();
				return fileName;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	// 重启机器
	private void restartSystem() {
		try {
			Process localProcess = Runtime.getRuntime().exec("su");
			DataOutputStream localDataOutputStream = new DataOutputStream(
					localProcess.getOutputStream());
			localDataOutputStream.writeBytes("chmod 755 " + "reboot" + "\n");
			localDataOutputStream.writeBytes("reboot\n");
			localDataOutputStream.flush();
			localDataOutputStream.close();
			localProcess.waitFor();
			// String str = new
			// DataInputStream(localProcess.getErrorStream()).readLine();
			localProcess.destroy();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
