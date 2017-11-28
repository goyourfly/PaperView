package com.goyourfly.library.paper_view

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout


/**
 * Copyright 2017-present, GaoYufei
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Created by GaoYufei on 2017/11/24.
 *
 * PaperView 是一个自定义的View，它就像一张纸折叠和展开，很漂亮
 */

class PaperView : FrameLayout {
    companion object {
        val TAG = "PaperView"

        private val STATUS_SMALL = 1
        private val STATUS_LARGE = 2
        private val STATUS_S_TO_L = 3
        private val STATUS_L_TO_S = 4


        private fun String.logD() {
            Log.d(TAG, this)
        }

        private fun Bitmap.reverseY(): Bitmap {
            val m = Matrix()
            m.setScale(1F, -1F)
            return Bitmap.createBitmap(this, 0, 0, this.width, this.height, m, false)
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        initAttrs(attributeSet)
    }

    constructor(context: Context, attributeSet: AttributeSet, style: Int) : super(context, attributeSet, style) {
        initAttrs(attributeSet)
    }

    private var paint = Paint()

    private var status = STATUS_SMALL

    // 暂存拆分后的Bitmap
    private var paperList: MutableList<PaperInfo>? = null

    private val divideMatrix = Matrix()
    private val divideCamera = Camera()
    private val divideTempFloat = FloatArray(9)

    private val angleStart = 180F
    private val angleEnd = 0F

    private var duration = 2000L
    private var bgColor = Color.WHITE

    private val interpolator = AccelerateInterpolator()
    private var animatorSet: AnimatorSet? = null

    private var flipScale = 0.2F

    private var childRequireHeight = -1F

    private var animating = false

    private var largeChild: View? = null
    private var smallChild: View? = null

    // 标记本次动画是否需要重新获取View Bitmap
    private var contentChanged = false

    private var listener: OnFoldStateChangeListener? = null

    init {
        setWillNotDraw(false)
        paint.color = Color.BLACK
        paint.isAntiAlias = true
    }

    private fun initAttrs(attrs: AttributeSet) {
        val typeArray = context.obtainStyledAttributes(attrs, R.styleable.PaperView)
        val color = typeArray.getColor(R.styleable.PaperView_paper_bg_color, Color.WHITE)
        val duration = typeArray.getInt(R.styleable.PaperView_paper_duration, duration.toInt())
        typeArray.recycle()

        this.duration = duration.toLong()
        this.bgColor = color
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount != 2) {
            throw IndexOutOfBoundsException("PaperView should only have two children")
        }


        val myWidth = MeasureSpec.getSize(widthMeasureSpec)

        if (animating) {
            setMeasuredDimension(myWidth, (paddingTop + paddingBottom + childRequireHeight).toInt())
            return
        }

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.visibility = VISIBLE
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            if (smallChild == null) {
                smallChild = child
            } else if (smallChild!!.measuredHeight > child.measuredHeight) {
                largeChild = smallChild
                smallChild = child
            }
        }

        if (smallChild == null || largeChild == null)
            return

        val smallChild = this.smallChild!!
        val largeChild = this.largeChild!!
        // 根据不同的状态计算需要的尺寸
        when (status) {
            STATUS_SMALL -> {
                smallChild.visibility = VISIBLE
                largeChild.visibility = GONE

                setMeasuredDimension(myWidth, smallChild.measuredHeight + paddingTop + paddingBottom)
            }
            STATUS_S_TO_L -> {
                setMeasuredDimension(myWidth, smallChild.measuredHeight + paddingTop + paddingBottom)
                animateLargeImpl()

                smallChild.visibility = GONE
                largeChild.visibility = GONE
            }
            STATUS_LARGE -> {
                smallChild.visibility = GONE
                largeChild.visibility = View.VISIBLE

                setMeasuredDimension(myWidth, largeChild.measuredHeight + paddingTop + paddingBottom)
            }

            STATUS_L_TO_S -> {
                setMeasuredDimension(myWidth, largeChild.measuredHeight + paddingTop + paddingBottom)
                animateSmallImpl()

                smallChild.visibility = GONE
                largeChild.visibility = GONE
            }
        }
    }

    /**
     * 展开
     * @param animator 是否执行动画
     * @param changed 内容是否发生变化
     */
    fun unfold(animator: Boolean = true, changed: Boolean = false) {
        status = if (animator) STATUS_S_TO_L else STATUS_LARGE
        contentChanged = changed
        requestLayout()
    }

    /**
     * 收起
     * @param animator 是否执行动画
     * @param changed 内容是否发生变化
     */
    fun fold(animator: Boolean = true, changed: Boolean = false) {
        status = if (animator) STATUS_L_TO_S else STATUS_SMALL
        contentChanged = changed
        requestLayout()
    }

    /**
     * 是否已经展开
     */
    fun isUnfold() = status == STATUS_LARGE

    /**
     * 是否已经折叠
     */
    fun isFold() = status == STATUS_SMALL

    /**
     * 设置展开和折叠监听器
     */
    fun setStateChangedListener(listener: OnFoldStateChangeListener) {
        this.listener = listener
    }

    /**
     * 设置动画总时长
     */
    fun setDuration(duration: Long) {
        this.duration = duration
    }

    /**
     * 设置翻转时背景颜色
     */
    fun setBgColor(color: Int) {
        this.bgColor = color
    }

    override fun onDraw(canvas: Canvas) {
        if (status == STATUS_SMALL || status == STATUS_LARGE) {
            return
        }
        if (paperList == null)
            return
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        childRequireHeight = 0F
        paperList?.forEach {
            val itemHeight = flipBitmap(canvas, it)
            if (itemHeight > 0) {
                childRequireHeight += itemHeight
            }
        }
        canvas.restore()
        requestLayout()
    }

    private fun flipBitmap(canvas: Canvas, store: PaperInfo?): Float {
        if (store == null || !store.visible)
            return 0F

        val angle = store.angle
        val x = store.x
        val y = store.y

        val centerX = store.fg.width / 2.0F
        val centerY = store.fg.height / 2.0F
        divideMatrix.reset()
        divideCamera.save()

        divideCamera.rotate(angle, 0.0F, 0.0F)
        divideCamera.getMatrix(divideMatrix)
        divideCamera.restore()


        // 修正旋转时的透视 MPERSP_0
        divideMatrix.getValues(divideTempFloat)
        divideTempFloat[6] = divideTempFloat[6] * flipScale
        divideTempFloat[7] = divideTempFloat[7] * flipScale
        divideMatrix.setValues(divideTempFloat)

        // 将锚点调整到 (-centerX,0) 的位置
        divideMatrix.preTranslate(-centerX, 0.0F)
        // 旋转完之后再回到原来的位置
        divideMatrix.postTranslate(centerX, 0.0F)

        // 移动到指定位置
        divideMatrix.postTranslate(x, y)


        val bitmap = getProperBitmap(store)
        // 在旋转的时候调整亮度
        val amount = (Math.sin((Math.toRadians(angle.toDouble())))).toFloat() * (-255F / 4)
        // 调整亮度
        adjustBrightness(amount)
        canvas.drawBitmap(bitmap, divideMatrix, paint)
        return (bitmap.height * Math.cos(Math.toRadians(angle.toDouble()))).toFloat()
    }

    private fun adjustBrightness(amount: Float) {
        val colorMatrix = ColorMatrix(floatArrayOf(
                1F, 0F, 0F, 0F, amount,
                0F, 1F, 0F, 0F, amount,
                0F, 0F, 1F, 0F, amount,
                0F, 0F, 0F, 1F, 0F
        ))
        val colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = colorMatrixFilter
    }


    private fun getProperBitmap(store: PaperInfo): Bitmap {
        val angle = store.angle
        if (isForeground(angle)) {
            // 根据角度计算要显示前面，但是由于前面有遮挡物
            // 这个遮挡物就是下一个折叠的背面
            if (store.next != null
                    && store.next!!.angle == angleStart) {
                if (store.next!!.bg.height < store.bg.height) {
                    return store.bg
                } else {
                    return store.next!!.bg
                }
            } else {
                return store.fg
            }
        } else {
            // 背部同理，可能有前一个折叠的背面遮挡
            if (store.prev != null
                    && store.prev!!.bg.height > store.bg.height) {
                return store.prev!!.bg
            } else {
                return store.bg
            }
        }
    }

    private fun preAnimate() {
        if (animating
                && animatorSet != null
                && animatorSet!!.isRunning) {
            animatorSet?.end()
            animatorSet = null
        }
        animating = false
        if (paperList == null
                || paperList!!.isEmpty()
                || contentChanged) {
            contentChanged = false
            paperList?.clear()
            paperList = getDividedBitmap(getSmallBitmap().reverseY(), getLargeBitmap())
        }
    }

    private fun animate(store: PaperInfo,
                        from: Float,
                        to: Float,
                        visibleOnEnd: Boolean,
                        duration: Long): Animator {
        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = duration
        animator.addUpdateListener {
            value ->
            store.angle = value.animatedValue as Float
            invalidate()
        }
        animator.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationStart(animation: Animator?) {
                store.visible = true
            }

            override fun onAnimationEnd(animation: Animator?) {
                store.visible = visibleOnEnd
            }
        })
        return animator
    }

    private fun startAnimator(set: AnimatorSet) {
        set.interpolator = interpolator
        set.start()
        animatorSet = set
        animating = true
    }

    /**
     * 展开动画
     */
    private fun animateLargeImpl() {
        preAnimate()
        largeReset()
        val set = AnimatorSet()
        val list = ArrayList<Animator>()
        val eachDuration = duration / paperList!!.size
        paperList?.forEachIndexed {
            index, it ->
            // 第一个不做动画
            if (index != 0)
                list.add(animate(it, angleStart, angleEnd, true, eachDuration))
        }
        set.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator?) {
                status = STATUS_LARGE
                animating = false
                requestLayout()
                listener?.onUnfold()
            }
        })
        set.playSequentially(list)
        startAnimator(set)
    }

    /**
     * 收起动画
     */
    private fun animateSmallImpl() {
        preAnimate()
        smallReset()
        val set = AnimatorSet()
        val list = ArrayList<Animator>()
        val eachDuration = duration / paperList!!.size
        // 缩小动画是倒着执行的
        for (index in (paperList!!.size - 1) downTo 0) {
            val store = paperList!![index]
            // 第一个不做动画
            if (index != 0)
                list.add(animate(store, angleEnd, angleStart, false, eachDuration))
        }
        set.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator?) {
                status = STATUS_SMALL
                animating = false
                requestLayout()
                listener?.onFold()
            }
        })
        set.playSequentially(list)
        startAnimator(set)
    }


    private fun largeReset() {
        paperList?.forEach {
            nextStore ->
            nextStore.angle = angleStart
            nextStore.visible = false
        }
        paperList?.first()?.visible = true
        paperList?.first()?.angle = angleEnd
    }

    private fun smallReset() {
        paperList?.forEach {
            nextStore ->
            nextStore.angle = angleEnd
            nextStore.visible = true
        }
        paperList?.first()?.angle = angleEnd
    }


    private fun getSmallBitmap(): Bitmap {
        return getBitmap(smallChild!!)
    }

    private fun getLargeBitmap(): Bitmap {
        return getBitmap(largeChild!!)
    }

    private fun getBitmap(child: View): Bitmap {
        val bitmap = Bitmap.createBitmap(child.measuredWidth, child.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        child.draw(canvas)
        return bitmap
    }

    private fun getDividedBitmap(smallBitmap: Bitmap, largeBitmap: Bitmap): MutableList<PaperInfo> {
        val desireWidth = largeBitmap.width
        val desireHeight = largeBitmap.height

        val list = ArrayList<PaperInfo>()

        val x = 0
        val divideItemWidth = smallBitmap.width
        val divideItemHeight = smallBitmap.height
        var nextDividerItemHeight = divideItemHeight.toFloat()
        var divideYOffset = 0F
        val count = desireHeight / divideItemHeight + if (desireHeight % divideItemHeight == 0) 0 else 1
        var prevStore: PaperInfo? = null
        for (i in 0..count - 1) {
            if (divideYOffset + nextDividerItemHeight > desireHeight) {
                nextDividerItemHeight = desireHeight - divideYOffset
            }
            val fg = Bitmap.createBitmap(largeBitmap, x, divideYOffset.toInt(), divideItemWidth, nextDividerItemHeight.toInt())
            val bg = if (i == 1) smallBitmap else generateBackgroundBitmap(fg.width, fg.height)
            val store = PaperInfo(false, x.toFloat(), divideYOffset, 180F, fg, bg, prevStore, null)
            list.add(store)
            prevStore?.next = store
            prevStore = store
            divideYOffset += divideItemHeight
        }
        return list
    }

    private fun isForeground(angle: Float) = ((angle - 1) % 180) <= 90

    private fun generateBackgroundBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)
        return bitmap
    }


    private data class PaperInfo(var visible: Boolean,
                                 val x: Float,
                                 var y: Float,
                                 var angle: Float,
                                 val fg: Bitmap,
                                 val bg: Bitmap,
                                 var prev: PaperInfo?,
                                 var next: PaperInfo?)


    private open class SimpleAnimatorListener : Animator.AnimatorListener {
        override fun onAnimationRepeat(animation: Animator?) {

        }

        override fun onAnimationEnd(animation: Animator?) {

        }

        override fun onAnimationCancel(animation: Animator?) {

        }

        override fun onAnimationStart(animation: Animator?) {

        }
    }

    interface OnFoldStateChangeListener {

        fun onFold()

        fun onUnfold()

    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        paperList?.clear()
        animatorSet?.end()

        paperList = null
        animatorSet = null
        listener = null

        smallChild = null
        largeChild = null
    }
}