package guepardoapps.medical.heartratemonitor;

import java.util.concurrent.atomic.AtomicBoolean;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import guepardoapps.library.toolset.common.Logger;
import guepardoapps.library.toolset.controller.PermissionController;

import guepardoapps.medical.heartratemonitor.common.constants.Enables;
import guepardoapps.medical.heartratemonitor.common.enums.PixelType;
import guepardoapps.medical.heartratemonitor.processing.ImageProcessing;

@SuppressWarnings("deprecation")
public class HeartRateMonitorView extends Activity {

	private static final String TAG = HeartRateMonitorView.class.getSimpleName();
	private static Logger _logger;

	private static final int REQUEST_CAMERA_PERMISSION = 219403;

	private Context _context;
	private PermissionController _permissionController;

	private static final AtomicBoolean PROCESSING = new AtomicBoolean(false);

	private static final int AVERAGE_ARRAY_SIZE = 4;
	private static final int[] AVERAGE_ARRAY = new int[AVERAGE_ARRAY_SIZE];

	private static final int BEATS_ARRAY_SIZE = 3;
	private static final int[] BEATS_ARRAY = new int[BEATS_ARRAY_SIZE];

	private static SurfaceView _preview = null;
	private static SurfaceHolder _previewHolder = null;
	private static Camera _camera = null;
	private static View _image = null;
	private static TextView _textView = null;

	private static WakeLock _wakeLock = null;

	private static int _averageIndex = 0;

	private static PixelType _currentPixelType = PixelType.GREEN;

	public static PixelType getCurrentPixelType() {
		return _currentPixelType;
	}

	private static int _beatsIndex = 0;
	private static double _beats = 0;
	private static long _startTime = 0;

	private static PreviewCallback _previewCallback = new PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if (data == null) {
				_logger.Error("Data is null!");
				return;
			}

			Camera.Size size = camera.getParameters().getPreviewSize();
			if (size == null) {
				_logger.Error("Size is null!");
				return;
			}

			if (!PROCESSING.compareAndSet(false, true)) {
				_logger.Error("Have to return...");
				return;
			}

			int width = size.width;
			int height = size.height;

			int imageAverage = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), height, width);

			if (imageAverage == 0 || imageAverage == 255) {
				PROCESSING.set(false);
				return;
			}

			int averageArrayAverage = 0;
			int averageArrayCount = 0;

			for (int index = 0; index < AVERAGE_ARRAY.length; index++) {
				if (AVERAGE_ARRAY[index] > 0) {
					averageArrayAverage += AVERAGE_ARRAY[index];
					averageArrayCount++;
				}
			}

			int rollingAverage = (averageArrayCount > 0) ? (averageArrayAverage / averageArrayCount) : 0;
			PixelType newType = _currentPixelType;

			if (imageAverage < rollingAverage) {
				newType = PixelType.RED;
				if (newType != _currentPixelType) {
					_beats++;
				}
			} else if (imageAverage > rollingAverage) {
				newType = PixelType.GREEN;
			}

			if (_averageIndex == AVERAGE_ARRAY_SIZE) {
				_averageIndex = 0;
			}

			AVERAGE_ARRAY[_averageIndex] = imageAverage;
			_averageIndex++;

			if (newType != _currentPixelType) {
				_currentPixelType = newType;
				_image.postInvalidate();
			}

			long endTime = System.currentTimeMillis();
			double totalTimeInSecs = (endTime - _startTime) / 1000d;

			if (totalTimeInSecs >= 10) {
				double beatsPerSecond = (_beats / totalTimeInSecs);
				int beatsPerMinute = (int) (beatsPerSecond * 60d);
				if (beatsPerMinute < 30 || beatsPerMinute > 180) {
					_startTime = System.currentTimeMillis();
					_beats = 0;
					PROCESSING.set(false);
					return;
				}

				if (_beatsIndex == BEATS_ARRAY_SIZE) {
					_beatsIndex = 0;
				}

				BEATS_ARRAY[_beatsIndex] = beatsPerMinute;
				_beatsIndex++;

				int beatsArrayAverage = 0;
				int beatsArrayCount = 0;

				for (int index = 0; index < BEATS_ARRAY.length; index++) {
					if (BEATS_ARRAY[index] > 0) {
						beatsArrayAverage += BEATS_ARRAY[index];
						beatsArrayCount++;
					}
				}

				int beatsAverage = (beatsArrayAverage / beatsArrayCount);
				_textView.setText(String.valueOf(beatsAverage));

				_startTime = System.currentTimeMillis();
				_beats = 0;
			}

			PROCESSING.set(false);
		}
	};

	private static SurfaceHolder.Callback _surfaceCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			try {
				_camera.setPreviewDisplay(_previewHolder);
				_camera.setPreviewCallback(_previewCallback);
			} catch (Throwable throwable) {
				_logger.Error(throwable.toString());
			}
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			Camera.Parameters parameters = _camera.getParameters();
			parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);

			Camera.Size size = getSmallestPreviewSize(width, height, parameters);
			if (size != null) {
				parameters.setPreviewSize(size.width, size.height);
				_logger.Debug(String.format("Using widht %s and height %s", size.width, size.height));
			}

			_camera.setParameters(parameters);
			_camera.startPreview();
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
		}
	};

	private static Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
		Camera.Size result = null;

		for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
			if (size.width <= width && size.height <= height) {
				if (result == null) {
					result = size;
				} else {
					int resultArea = result.width * result.height;
					int newArea = size.width * size.height;

					if (newArea < resultArea) {
						result = size;
					}
				}
			}
		}

		return result;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.view_main);

		_logger = new Logger(TAG, Enables.DEBUGGING);
		_logger.Debug("onCreate");

		_context = this;
		_permissionController = new PermissionController(_context);
		_permissionController.CheckPermissions(REQUEST_CAMERA_PERMISSION, Manifest.permission.CAMERA);

		_preview = (SurfaceView) findViewById(R.id.preview);
		_previewHolder = _preview.getHolder();
		_previewHolder.addCallback(_surfaceCallback);
		_previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		_image = findViewById(R.id.image);
		_textView = (TextView) findViewById(R.id.text);

		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		_wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);
	}

	@Override
	public void onResume() {
		super.onResume();
		_logger.Debug("onResume");

		_wakeLock.acquire();
		_camera = Camera.open();
		_startTime = System.currentTimeMillis();
	}

	@Override
	public void onPause() {
		super.onPause();
		_logger.Debug("onPause");

		_wakeLock.release();

		_camera.setPreviewCallback(null);
		_camera.stopPreview();
		_camera.release();
		_camera = null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		_logger.Debug("onDestroy");
	}

	@Override
	public void onConfigurationChanged(Configuration configuration) {
		super.onConfigurationChanged(configuration);
		_logger.Debug("onConfigurationChanged");
	}
}
