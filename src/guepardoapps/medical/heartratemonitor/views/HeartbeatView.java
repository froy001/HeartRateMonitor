package guepardoapps.medical.heartratemonitor.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import guepardoapps.library.toolset.common.Logger;

import guepardoapps.medical.heartratemonitor.HeartRateMonitorView;
import guepardoapps.medical.heartratemonitor.R;
import guepardoapps.medical.heartratemonitor.common.constants.Enables;
import guepardoapps.medical.heartratemonitor.common.enums.PixelType;

public class HeartbeatView extends View {

	private static final String TAG = HeartbeatView.class.getSimpleName();
	private Logger _logger;

	private static final Matrix _matrix = new Matrix();
	private static final Paint _paint = new Paint(Paint.ANTI_ALIAS_FLAG);

	private static Bitmap _greenBitmap = null;
	private static Bitmap _redBitmap = null;

	private static int _parentWidth = 0;
	private static int _parentHeight = 0;

	public HeartbeatView(Context context, AttributeSet attr) {
		super(context, attr);

		_logger = new Logger(TAG, Enables.DEBUGGING);
		_logger.Debug("Created...");

		_greenBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.green_icon);
		_redBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.red_icon);
	}

	public HeartbeatView(Context context) {
		super(context);

		_logger = new Logger(TAG, Enables.DEBUGGING);
		_logger.Debug("Created...");

		_greenBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.green_icon);
		_redBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.red_icon);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		_logger.Debug("onMeasure...");

		_parentWidth = MeasureSpec.getSize(widthMeasureSpec);
		_parentHeight = MeasureSpec.getSize(heightMeasureSpec);

		setMeasuredDimension(_parentWidth, _parentHeight);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		_logger.Debug("onDraw...");

		if (canvas == null) {
			_logger.Error("Canvas is null!");
			return;
		}

		Bitmap bitmap = null;
		if (HeartRateMonitorView.getCurrentPixelType() == PixelType.GREEN) {
			bitmap = _greenBitmap;
		} else {
			bitmap = _redBitmap;
		}

		int bitmapX = bitmap.getWidth() / 2;
		int bitmapY = bitmap.getHeight() / 2;

		int parentX = _parentWidth / 2;
		int parentY = _parentHeight / 2;

		int centerX = parentX - bitmapX;
		int centerY = parentY - bitmapY;

		_matrix.reset();
		_matrix.postTranslate(centerX, centerY);

		canvas.drawBitmap(bitmap, _matrix, _paint);
	}
}
