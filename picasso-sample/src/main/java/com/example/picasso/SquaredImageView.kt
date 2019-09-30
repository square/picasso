package com.example.picasso

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView

/** An image view which always remains square with respect to its width.  */
class SquaredImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ImageView(context, attrs) {
  override fun onMeasure(
    widthMeasureSpec: Int,
    heightMeasureSpec: Int
  ) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    setMeasuredDimension(measuredWidth, measuredWidth)
  }
}
