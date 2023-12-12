package com.github.tvbox.osc.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.DecelerateInterpolator
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig.Companion.get
import com.github.tvbox.osc.base.App.Companion.getInstance
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.*
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.player.controller.LiveController
import com.github.tvbox.osc.player.controller.LiveController.LiveControlListener
import com.github.tvbox.osc.ui.adapter.*
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog
import com.github.tvbox.osc.util.EpgUtil
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.live.TxtSubscribe
import com.google.gson.JsonArray
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.callback.StringCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.TvRecyclerView.OnItemListener
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONException
import org.json.JSONObject
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.util.PlayerUtils
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
class LivePlayActivity : BaseActivity() {
    // Main View
    private var mVideoView: VideoView? = null
    private var controller: LiveController? = null

    // Left Channel View
    private var tvLeftChannelListLayout: LinearLayout? = null
    private var mGroupGridView: TvRecyclerView? = null
    private var mDivLeft: LinearLayout? = null
    private var mChannelGridView: TvRecyclerView? = null
    private var mDivRight: LinearLayout? = null
    private var mGroupEPG: LinearLayout? = null
    private var mEpgDateGridView: TvRecyclerView? = null
    private var mEpgInfoGridView: TvRecyclerView? = null

    // Left Channel View - Variables
    private var liveChannelGroupAdapter: LiveChannelGroupAdapter? = null
    private var liveChannelItemAdapter: LiveChannelItemAdapter? = null
    private val liveChannelGroupList: MutableList<LiveChannelGroup> = ArrayList()
    private val liveSettingGroupList: MutableList<LiveSettingGroup> = ArrayList()
    private var currentLiveChannelIndex = -1
    private var currentLiveChannelItem: LiveChannelItem? = null

    // Right Channel View
    private var tvRightSettingLayout: LinearLayout? = null
    private var mSettingGroupView: TvRecyclerView? = null
    private var mSettingItemView: TvRecyclerView? = null

    // Right Channel View - Variables
    private var liveSettingGroupAdapter: LiveSettingGroupAdapter? = null
    private var liveSettingItemAdapter: LiveSettingItemAdapter? = null
    private val livePlayerManager = LivePlayerManager()
    private val channelGroupPasswordConfirmed = ArrayList<Int>()
    private var currentLiveChangeSourceTimes = 0

    // Bottom Channel View
    private lateinit var tvBottomLayout: LinearLayout
    private lateinit var tv_logo: ImageView
    private lateinit var tv_sys_time: TextView
    private lateinit var tv_size: TextView
    private lateinit var tv_source: TextView

    // Bottom Channel View - Line 1 / 2 / 3
    private var tv_channelname: TextView? = null
    private var tv_channelnum: TextView? = null
    private var tv_curr_name: TextView? = null
    private var tv_curr_time: TextView? = null
    private var tv_next_name: TextView? = null
    private var tv_next_time: TextView? = null

    // Bottom Channel View - Variables
    private var epgDateAdapter: LiveEpgDateAdapter? = null
    private var epgListAdapter: LiveEpgAdapter? = null

    // Misc Variables
    var epgStringAddress = ""
    var timeFormat = SimpleDateFormat("yyyy-MM-dd")
    private val mHandler = Handler()
    private var tvTime: TextView? = null
    private var tvNetSpeed: TextView? = null

    // Seek Bar
    var mIsDragging = false
    var llSeekBar: LinearLayout? = null
    var mCurrentTime: TextView? = null
    lateinit var mSeekBar: SeekBar
    var mTotalTime: TextView? = null
    var isVOD = false

    // center BACK button
    lateinit var mBack: LinearLayout
    private var isSHIYI = false
    override fun getLayoutResID(): Int {
        return R.layout.activity_live_play
    }

    override fun init() {

        // takagen99 : Hide only when video playing
        hideSystemUI(false)

        // Getting EPG Address
        epgStringAddress = Hawk.get(HawkConfig.EPG_URL, "")
        if (StringUtils.isBlank(epgStringAddress)) {
            epgStringAddress = "https://epg.112114.xyz/"
            //            Hawk.put(HawkConfig.EPG_URL, epgStringAddress);
        }
        // http://epg.aishangtv.top/live_proxy_epg_bc.php
        // http://diyp.112114.xyz/
        EventBus.getDefault().register(this)
        setLoadSir(findViewById(R.id.live_root))
        mVideoView = findViewById(R.id.mVideoView)
        tv_size = findViewById(R.id.tv_size) // Resolution
        tv_source = findViewById(R.id.tv_source) // Source/Total Source
        tv_sys_time = findViewById(R.id.tv_sys_time) // System Time

        // VOD SeekBar
        llSeekBar = findViewById(R.id.ll_seekbar)
        mCurrentTime = findViewById(R.id.curr_time)
        mSeekBar = findViewById(R.id.seekBar)
        mTotalTime = findViewById(R.id.total_time)

        // Center Back Button
        mBack = findViewById(R.id.tvBackButton)
        mBack.setVisibility(View.INVISIBLE)

        // Bottom Info
        tvBottomLayout = findViewById(R.id.tvBottomLayout)
        tvBottomLayout.setVisibility(View.INVISIBLE)
        tv_channelname = findViewById(R.id.tv_channel_name) //底部名称
        tv_channelnum = findViewById(R.id.tv_channel_number) //底部数字
        tv_logo = findViewById(R.id.tv_logo)
        tv_curr_time = findViewById(R.id.tv_current_program_time)
        tv_curr_name = findViewById(R.id.tv_current_program_name)
        tv_next_time = findViewById(R.id.tv_next_program_time)
        tv_next_name = findViewById(R.id.tv_next_program_name)

        // EPG Info
        mGroupEPG = findViewById(R.id.mGroupEPG)
        mDivRight = findViewById(R.id.mDivRight)
        mDivLeft = findViewById(R.id.mDivLeft)
        mEpgDateGridView = findViewById(R.id.mEpgDateGridView)
        mEpgInfoGridView = findViewById(R.id.mEpgInfoGridView)

        // Left Layout
        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannelListLayout)
        mGroupGridView = findViewById(R.id.mGroupGridView)
        mChannelGridView = findViewById(R.id.mChannelGridView)

        // Right Layout
        tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout)
        mSettingGroupView = findViewById(R.id.mSettingGroupView)
        mSettingItemView = findViewById(R.id.mSettingItemView)

        // Not in Used
        tvTime = findViewById(R.id.tvTime)
        tvNetSpeed = findViewById(R.id.tvNetSpeed)

        // Initialization
        initEpgDateView()
        initEpgListView()
        initVideoView()
        initChannelGroupView()
        initLiveChannelView()
        initSettingGroupView()
        initSettingItemView()
        initLiveChannelList()
        initLiveSettingGroupList()

        // takagen99 : Add SeekBar for VOD
        mSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                mHandler.removeCallbacks(mHideChannelInfoRun)
                mHandler.postDelayed(mHideChannelInfoRun, 6000)
                val duration = mVideoView!!.getDuration()
                val newPosition = duration * progress / seekBar.max
                if (mCurrentTime != null) mCurrentTime!!.text = PlayerUtils.stringForTimeVod(newPosition.toInt())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                mIsDragging = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mIsDragging = false
                val duration = mVideoView!!.getDuration()
                val newPosition = duration * seekBar.progress / seekBar.max
                mVideoView!!.seekTo(newPosition.toInt().toLong())
            }
        })
        mSeekBar.setOnKeyListener(View.OnKeyListener { arg0, keycode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keycode == KeyEvent.KEYCODE_DPAD_LEFT || keycode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    mIsDragging = true
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                mIsDragging = false
                val duration = mVideoView!!.getDuration()
                val newPosition = duration * mSeekBar.getProgress() / mSeekBar.getMax()
                mVideoView!!.seekTo(newPosition.toInt().toLong())
            }
            false
        })
        // Button: BACK click to go back to previous page -------------------
        mBack.setOnClickListener(View.OnClickListener { finish() })
    }

    var PiPON = Hawk.get(HawkConfig.PIC_IN_PIC, false)

    // takagen99 : Enter PIP if supported
    public override fun onUserLeaveHint() {
        if (supportsPiPMode() && PiPON) {
            // Hide controls when entering PIP
            mHandler.post(mHideChannelListRun)
            mHandler.post(mHideChannelInfoRun)
            mHandler.post(mHideSettingLayoutRun)
            enterPictureInPictureMode()
        }
    }

    override fun onBackPressed() {
        if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.post(mHideChannelListRun)
        } else if (tvRightSettingLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun)
            mHandler.post(mHideSettingLayoutRun)
        } else if (tvBottomLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun)
            mHandler.post(mHideChannelInfoRun)
        } else {
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
            mHandler.removeCallbacks(mUpdateNetSpeedRun)
            mHandler.removeCallbacks(mUpdateTimeRun)
            mHandler.removeCallbacks(tv_sys_timeRunnable)
            exit()
        }
    }

    private var mExitTime: Long = 0
    private fun exit() {
        if (System.currentTimeMillis() - mExitTime < 2000) {
            super.onBackPressed()
        } else {
            mExitTime = System.currentTimeMillis()
            Toast.makeText(mContext, getString(R.string.hm_exit_live), Toast.LENGTH_SHORT).show()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                showSettingGroup()
            } else if (!isListOrSettingLayoutVisible) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> if (Hawk.get(
                            HawkConfig.LIVE_CHANNEL_REVERSE,
                            false
                        )
                    ) playNext() else playPrevious()

                    KeyEvent.KEYCODE_DPAD_DOWN -> if (Hawk.get(
                            HawkConfig.LIVE_CHANNEL_REVERSE,
                            false
                        )
                    ) playPrevious() else playNext()

                    KeyEvent.KEYCODE_DPAD_LEFT ->                         // takagen99 : To cater for newer Android w no Menu button
                        // playPreSource();
                        if (!isVOD) {
                            showSettingGroup()
                        } else {
                            showChannelInfo()
                        }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (!isVOD) {
                        playNextSource()
                    } else {
                        showChannelInfo()
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> showChannelList()
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
        }
        return super.dispatchKeyEvent(event)
    }

    // takagen99 : Use onStopCalled to track close activity
    private var onStopCalled = false
    override fun onResume() {
        super.onResume()
        if (mVideoView != null) {
            mVideoView!!.resume()
        }
    }

    override fun onStop() {
        super.onStop()
        onStopCalled = true
    }

    override fun onPause() {
        super.onPause()
        if (mVideoView != null) {
            if (supportsPiPMode()) {
                if (isInPictureInPictureMode) {
                    // Continue playback
                    mVideoView!!.resume()
                } else {
                    // Pause playback
                    mVideoView!!.pause()
                }
            } else {
                mVideoView!!.pause()
            }
        }
    }

    // takagen99 : PIP fix to close video when close window
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (supportsPiPMode()) {
            if (!isInPictureInPictureMode()) {
                // Closed playback
                if (onStopCalled) {
                    mVideoView!!.release()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mVideoView != null) {
            mVideoView!!.release()
            mVideoView = null
        }
    }

    private fun showChannelList() {
        mBack!!.visibility = View.INVISIBLE
        if (tvBottomLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun)
            mHandler.post(mHideChannelInfoRun)
        } else if (tvRightSettingLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun)
            mHandler.post(mHideSettingLayoutRun)
        } else if ((tvLeftChannelListLayout!!.visibility == View.INVISIBLE) and (tvRightSettingLayout!!.visibility == View.INVISIBLE)) {
            //重新载入上一次状态
            liveChannelItemAdapter!!.setNewData(getLiveChannels(currentChannelGroupIndex))
            if (currentLiveChannelIndex > -1) mChannelGridView!!.scrollToPosition(currentLiveChannelIndex)
            mChannelGridView!!.setSelection(currentLiveChannelIndex)
            mGroupGridView!!.scrollToPosition(currentChannelGroupIndex)
            mGroupGridView!!.setSelection(currentChannelGroupIndex)
            mHandler.postDelayed(mFocusCurrentChannelAndShowChannelList, 200)
            mHandler.post(tv_sys_timeRunnable)
        } else {
            mBack!!.visibility = View.INVISIBLE
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.post(mHideChannelListRun)
            mHandler.removeCallbacks(tv_sys_timeRunnable)
        }
    }

    //频道列表
    fun divLoadEpgR(view: View?) {
        mGroupGridView!!.visibility = View.GONE
        mEpgInfoGridView!!.visibility = View.VISIBLE
        mGroupEPG!!.visibility = View.VISIBLE
        mDivLeft!!.visibility = View.VISIBLE
        mDivRight!!.visibility = View.GONE
        tvLeftChannelListLayout!!.visibility = View.INVISIBLE
        showChannelList()
    }

    fun divLoadEpgL(view: View?) {
        mGroupGridView!!.visibility = View.VISIBLE
        mEpgInfoGridView!!.visibility = View.GONE
        mGroupEPG!!.visibility = View.GONE
        mDivLeft!!.visibility = View.GONE
        mDivRight!!.visibility = View.VISIBLE
        tvLeftChannelListLayout!!.visibility = View.INVISIBLE
        showChannelList()
    }

    private val mFocusCurrentChannelAndShowChannelList: Runnable = object : Runnable {
        override fun run() {
            if (mGroupGridView!!.isScrolling || mChannelGridView!!.isScrolling || mGroupGridView!!.isComputingLayout || mChannelGridView!!.isComputingLayout) {
                mHandler.postDelayed(this, 100)
            } else {
                liveChannelGroupAdapter!!.setSelectedGroupIndex(currentChannelGroupIndex)
                liveChannelItemAdapter!!.setSelectedChannelIndex(currentLiveChannelIndex)
                val holder = mChannelGridView!!.findViewHolderForAdapterPosition(currentLiveChannelIndex)
                holder?.itemView?.requestFocus()
                tvLeftChannelListLayout!!.visibility = View.VISIBLE
                tvLeftChannelListLayout!!.alpha = 0.0f
                tvLeftChannelListLayout!!.translationX = (-tvLeftChannelListLayout!!.width / 2).toFloat()
                tvLeftChannelListLayout!!.animate()
                    .translationX(0f)
                    .alpha(1.0f)
                    .setDuration(250)
                    .setInterpolator(DecelerateInterpolator())
                    .setListener(null)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
                mHandler.postDelayed(mUpdateLayout, 255) // Workaround Fix : SurfaceView
            }
        }
    }
    private val mUpdateLayout = Runnable {
        tvLeftChannelListLayout!!.requestLayout()
        tvRightSettingLayout!!.requestLayout()
    }
    private val mHideChannelListRun = Runnable {
        val params = tvLeftChannelListLayout!!.layoutParams as MarginLayoutParams
        if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) {
            tvLeftChannelListLayout!!.animate()
                .translationX((-tvLeftChannelListLayout!!.width / 2).toFloat())
                .alpha(0.0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        tvLeftChannelListLayout!!.visibility = View.INVISIBLE
                        tvLeftChannelListLayout!!.clearAnimation()
                    }
                })
        }
    }

    private fun showChannelInfo() {
        // takagen99: Check if Touch Screen, show back button
        if (supportsTouch()) {
            mBack!!.visibility = View.VISIBLE
        }
        if (tvBottomLayout!!.visibility == View.GONE || tvBottomLayout!!.visibility == View.INVISIBLE) {
            tvBottomLayout!!.visibility = View.VISIBLE
            tvBottomLayout!!.translationY = (tvBottomLayout!!.height / 2).toFloat()
            tvBottomLayout!!.alpha = 0.0f
            tvBottomLayout!!.animate()
                .alpha(1.0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .translationY(0f)
                .setListener(null)
        }
        mHandler.removeCallbacks(mHideChannelInfoRun)
        mHandler.postDelayed(mHideChannelInfoRun, 6000)
        mHandler.postDelayed(mUpdateLayout, 255) // Workaround Fix : SurfaceView
    }

    private val mHideChannelInfoRun = Runnable {
        mBack!!.visibility = View.INVISIBLE
        if (tvBottomLayout!!.visibility == View.VISIBLE) {
            tvBottomLayout!!.animate()
                .alpha(0.0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .translationY((tvBottomLayout!!.height / 2).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        tvBottomLayout!!.visibility = View.INVISIBLE
                        tvBottomLayout!!.clearAnimation()
                    }
                })
        }
    }

    private fun toggleChannelInfo() {
        if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.post(mHideChannelListRun)
        } else if (tvRightSettingLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun)
            mHandler.post(mHideSettingLayoutRun)
        } else if (tvBottomLayout!!.visibility == View.INVISIBLE) {
            showChannelInfo()
        } else {
            mBack!!.visibility = View.INVISIBLE
            mHandler.removeCallbacks(mHideChannelInfoRun)
            mHandler.post(mHideChannelInfoRun)
            mHandler.post(mUpdateLayout) // Workaround Fix : SurfaceView
        }
    }

    //显示侧边EPG
    private fun showEpg(date: Date, arrayList: ArrayList<Epginfo>) {
        if (arrayList.size > 0) {
            epgdata = arrayList
            epgListAdapter!!.CanBack(currentLiveChannelItem!!.getinclude_back())
            epgListAdapter!!.setNewData(epgdata)
            var i = -1
            var size = epgdata.size - 1
            while (size >= 0) {
                if (Date().compareTo(epgdata[size]!!.startdateTime) >= 0) {
                    break
                }
                size--
            }
            i = size
            if (i >= 0 && Date().compareTo(epgdata[i]!!.enddateTime) <= 0) {
                mEpgInfoGridView!!.selectedPosition = i
                mEpgInfoGridView!!.setSelection(i)
                epgListAdapter!!.setSelectedEpgIndex(i)
                val finalI = i
                mEpgInfoGridView!!.post { mEpgInfoGridView!!.smoothScrollToPosition(finalI) }
            }
        } else {
            val epgbcinfo = Epginfo(date, "暂无节目信息", date, "00:00", "23:59", 0)
            arrayList.add(epgbcinfo)
            epgdata = arrayList
            epgListAdapter!!.setNewData(epgdata)

            //  mEpgInfoGridView.setAdapter(epgListAdapter);
        }
    }

    private val tv_sys_timeRunnable: Runnable = object : Runnable {
        override fun run() {
            val date = Date()
            val timeFormat = SimpleDateFormat("hh:mm aa", Locale.ENGLISH)
            tv_sys_time!!.text = timeFormat.format(date)
            mHandler.postDelayed(this, 1000)

            // takagen99 : Update SeekBar
            if ((mVideoView != null) and !mIsDragging) {
                val currentPosition = mVideoView!!.getCurrentPosition().toInt()
                mCurrentTime!!.text = PlayerUtils.stringForTimeVod(currentPosition)
                mSeekBar!!.progress = currentPosition
            }
        }
    }

    //显示底部EPG
    private fun showBottomEpg() {
        if (isSHIYI) return
        if (channel_Name!!.channelName != null) {
            showChannelInfo()
            val savedEpgKey = channel_Name!!.channelName + "_" + epgDateAdapter!!.getItem(
                epgDateAdapter!!.selectedIndex
            )!!.datePresented
            if (hsEpg.containsKey(savedEpgKey)) {
                val epgInfo = EpgUtil.getEpgInfo(channel_Name!!.channelName)
                getTvLogo(channel_Name!!.channelName, epgInfo?.get(0))
                val arrayList = hsEpg[savedEpgKey]
                if (arrayList != null && arrayList.size > 0) {
                    val date = Date()
                    var size = arrayList.size - 1
                    while (size >= 0) {
                        if (date.after(arrayList[size].startdateTime) and date.before(arrayList[size].enddateTime)) {
//                            if (new Date().compareTo(((Epginfo) arrayList.get(size)).startdateTime) >= 0) {
                            tv_curr_time!!.text =
                                arrayList[size].start + " - " + arrayList[size].end
                            (arrayList[size] as Epginfo).title.also { tv_curr_name!!.text = it }
                            if (size != arrayList.size - 1) {
                                tv_next_time!!.text =
                                    (arrayList[size + 1] as Epginfo).start + " - " + (arrayList[size + 1] as Epginfo).end
                                tv_next_name!!.text = (arrayList[size + 1] as Epginfo).title
                            } else {
                                tv_next_time!!.text = "00:00 - 23:59"
                                tv_next_name!!.text = "No Information"
                            }
                            break
                        } else {
                            size--
                        }
                    }
                }
                epgListAdapter!!.CanBack(currentLiveChannelItem!!.getinclude_back())
                epgListAdapter!!.setNewData(arrayList)
            } else {
                val selectedIndex = epgDateAdapter!!.selectedIndex
                if (selectedIndex < 0) getEpg(Date()) else getEpg(epgDateAdapter!!.data[selectedIndex].dateParamVal)
            }
        }
    }

    // 获取EPG并存储 // 百川epg
    private var epgdata: List<Epginfo?> = ArrayList()

    // Get Channel Logo
    private fun getTvLogo(channelName: String, logoUrl: String?) {
        // takagen99 : Use Glide instead
        val options = RequestOptions()
        options.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.drawable.img_logo_placeholder)
        Glide.with(tv_logo!!)
            .load(logoUrl)
            .apply(options)
            .into(tv_logo!!)
    }

    fun getEpg(date: Date) {
        val channelName = channel_Name!!.channelName
        val timeFormat = SimpleDateFormat("yyyy-MM-dd")
        timeFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
        val epgInfo = EpgUtil.getEpgInfo(channelName)
        var epgTagName = channelName
        getTvLogo(channelName, epgInfo?.get(0))
        if (epgInfo != null && !epgInfo[1].isEmpty()) {
            epgTagName = epgInfo[1]
        }
        epgListAdapter!!.CanBack(currentLiveChannelItem!!.getinclude_back())
        val epgUrl: String
        epgUrl = if (epgStringAddress.contains("{name}") && epgStringAddress.contains("{date}")) {
            epgStringAddress.replace("{name}", URLEncoder.encode(epgTagName)).replace("{date}", timeFormat.format(date))
        } else {
            epgStringAddress + "?ch=" + URLEncoder.encode(epgTagName) + "&date=" + timeFormat.format(date)
        }
        OkGo.get<String>(epgUrl).execute(object : StringCallback() {
            override fun onSuccess(response: Response<String>) {
                val paramString = response.body()
                val arrayList = ArrayList<Epginfo>()
                try {
                    if (paramString.contains("epg_data")) {
                        val jSONArray = JSONObject(paramString).optJSONArray("epg_data")
                        if (jSONArray != null) for (b in 0 until jSONArray.length()) {
                            val jSONObject = jSONArray.getJSONObject(b)
                            val epgbcinfo = Epginfo(
                                date,
                                jSONObject.optString("title"),
                                date,
                                jSONObject.optString("start"),
                                jSONObject.optString("end"),
                                b
                            )
                            arrayList.add(epgbcinfo)
                        }
                    }
                } catch (jSONException: JSONException) {
                    jSONException.printStackTrace()
                }
                showEpg(date, arrayList)
                val savedEpgKey = channelName + "_" + epgDateAdapter!!.getItem(epgDateAdapter!!.selectedIndex)!!
                    .datePresented
                if (!hsEpg.contains(savedEpgKey)) hsEpg[savedEpgKey] = arrayList
                showBottomEpg()
            }

            fun onFailure(i: Int, str: String?) {
                showEpg(date, ArrayList<Epginfo>())
                showBottomEpg()
            }
        })
    }

    //节目播放
    private fun playChannel(channelGroupIndex: Int, liveChannelIndex: Int, changeSource: Boolean): Boolean {
        if (channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource || changeSource && currentLiveChannelItem!!.getSourceNum() == 1) {
            showChannelInfo()
            return true
        }
        if (mVideoView == null) return true
        mVideoView!!.release()
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex
            currentLiveChannelIndex = liveChannelIndex
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex)[currentLiveChannelIndex]
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem!!.channelName)
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem!!.channelName)
        }
        channel_Name = currentLiveChannelItem
        currentLiveChannelItem!!.setinclude_back(currentLiveChannelItem!!.url.indexOf("PLTV/8888") != -1)

        // takagen99 : Moved update of Channel Info here before getting EPG (no dependency on EPG)
        mHandler.post(tv_sys_timeRunnable)

        // Channel Name & No. + Source No.
        tv_channelname!!.text = channel_Name!!.channelName
        tv_channelnum!!.text = "" + channel_Name!!.channelNum
        if (channel_Name == null || channel_Name!!.getSourceNum() <= 0) {
            tv_source!!.text = "1/1"
        } else {
            tv_source!!.text =
                "线路 " + (channel_Name!!.getSourceIndex() + 1) + "/" + channel_Name!!.getSourceNum()
        }
        getEpg(Date())
        mVideoView!!.setUrl(currentLiveChannelItem!!.url)
        showChannelInfo()
        mVideoView!!.start()
        return true
    }

    private fun playNext() {
        if (!isCurrentLiveChannelValid) return
        val groupChannelIndex = getNextChannel(1)
        playChannel(groupChannelIndex[0]!!, groupChannelIndex[1]!!, false)
    }

    private fun playPrevious() {
        if (!isCurrentLiveChannelValid) return
        val groupChannelIndex = getNextChannel(-1)
        playChannel(groupChannelIndex[0]!!, groupChannelIndex[1]!!, false)
    }

    fun playPreSource() {
        if (!isCurrentLiveChannelValid) return
        currentLiveChannelItem!!.preSource()
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true)
    }

    fun playNextSource() {
        if (mVideoView == null) {
            return
        }
        if (!isCurrentLiveChannelValid) return
        currentLiveChannelItem!!.nextSource()
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true)
    }

    //显示设置列表
    private fun showSettingGroup() {
        mBack!!.visibility = View.INVISIBLE
        if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.post(mHideChannelListRun)
        } else if (tvBottomLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun)
            mHandler.post(mHideChannelInfoRun)
        } else if (tvRightSettingLayout!!.visibility == View.INVISIBLE) {
            if (!isCurrentLiveChannelValid) return
            //重新载入默认状态
            loadCurrentSourceList()
            liveSettingGroupAdapter!!.setNewData(liveSettingGroupList)
            selectSettingGroup(0, false)
            mSettingGroupView!!.scrollToPosition(0)
            mSettingItemView!!.scrollToPosition(currentLiveChannelItem!!.getSourceIndex())
            mHandler.postDelayed(mFocusAndShowSettingGroup, 200)
        } else {
            mBack!!.visibility = View.INVISIBLE
            mHandler.removeCallbacks(mHideSettingLayoutRun)
            mHandler.post(mHideSettingLayoutRun)
        }
    }

    private val mFocusAndShowSettingGroup: Runnable = object : Runnable {
        override fun run() {
            if (mSettingGroupView!!.isScrolling || mSettingItemView!!.isScrolling || mSettingGroupView!!.isComputingLayout || mSettingItemView!!.isComputingLayout) {
                mHandler.postDelayed(this, 100)
            } else {
                val holder = mSettingGroupView!!.findViewHolderForAdapterPosition(0)
                holder?.itemView?.requestFocus()
                tvRightSettingLayout!!.visibility = View.VISIBLE
                tvRightSettingLayout!!.alpha = 0.0f
                tvRightSettingLayout!!.translationX = (tvRightSettingLayout!!.width / 2).toFloat()
                tvRightSettingLayout!!.animate()
                    .translationX(0f)
                    .alpha(1.0f)
                    .setDuration(250)
                    .setInterpolator(DecelerateInterpolator())
                    .setListener(null)
                mHandler.removeCallbacks(mHideSettingLayoutRun)
                mHandler.postDelayed(mHideSettingLayoutRun, 6000)
                mHandler.postDelayed(mUpdateLayout, 255) // Workaround Fix : SurfaceView
            }
        }
    }
    private val mHideSettingLayoutRun = Runnable {
        if (tvRightSettingLayout!!.visibility == View.VISIBLE) {
            tvRightSettingLayout!!.animate()
                .translationX((tvRightSettingLayout!!.width / 2).toFloat())
                .alpha(0.0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        tvRightSettingLayout!!.visibility = View.INVISIBLE
                        tvRightSettingLayout!!.clearAnimation()
                        liveSettingGroupAdapter!!.setSelectedGroupIndex(-1)
                    }
                })
        }
    }

    private fun initVideoView() {
        controller = LiveController(this)
        controller!!.setListener(object : LiveControlListener {
            override fun singleTap(e: MotionEvent): Boolean {
                val fiveScreen = PlayerUtils.getScreenWidth(mContext, true) / 5
                if (e.x > 0 && e.x < fiveScreen * 2) {
                    // left side <<<<<
                    showChannelList()
                } else if (e.x > fiveScreen * 2 && e.x < fiveScreen * 3) {
                    // middle screen
                    toggleChannelInfo()
                } else if (e.x > fiveScreen * 3) {
                    // right side >>>>>
                    showSettingGroup()
                }
                return true
            }

            override fun longPress() {
                showSettingGroup()
            }

            override fun playStateChanged(playState: Int) {
                when (playState) {
                    VideoView.STATE_IDLE, VideoView.STATE_PAUSED -> {}
                    VideoView.STATE_PREPARED -> {
                        // takagen99 : Retrieve Video Resolution & Retrieve Video Duration
                        if (mVideoView!!.videoSize.size >= 2) {
                            tv_size!!.text = mVideoView!!.videoSize[0].toString() + " x " + mVideoView!!.videoSize[1]
                        }
                        // Show SeekBar if it's a VOD (with duration)
                        val duration = mVideoView!!.getDuration().toInt()
                        if (duration > 0) {
                            isVOD = true
                            llSeekBar!!.visibility = View.VISIBLE
                            mSeekBar!!.progress = 10
                            mSeekBar!!.max = duration
                            mSeekBar!!.progress = 0
                            mTotalTime!!.text = PlayerUtils.stringForTimeVod(duration)
                        } else {
                            isVOD = false
                            llSeekBar!!.visibility = View.GONE
                        }
                    }

                    VideoView.STATE_BUFFERED, VideoView.STATE_PLAYING -> {
                        currentLiveChangeSourceTimes = 0
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
                    }

                    VideoView.STATE_ERROR, VideoView.STATE_PLAYBACK_COMPLETED -> {
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
                        mHandler.post(mConnectTimeoutChangeSourceRun)
                    }

                    VideoView.STATE_PREPARING, VideoView.STATE_BUFFERING -> {
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
                        mHandler.postDelayed(
                            mConnectTimeoutChangeSourceRun,
                            (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5000L
                        )
                    }
                }
            }

            override fun changeSource(direction: Int) {
                if (direction > 0) playNextSource() else playPreSource()
            }
        })
        controller!!.setCanChangePosition(false)
        controller!!.setEnableInNormal(true)
        controller!!.setGestureEnabled(true)
        controller!!.setDoubleTapTogglePlayEnabled(false)
        mVideoView!!.setVideoController(controller)
        mVideoView!!.setProgressManager(null)
    }

    private val mConnectTimeoutChangeSourceRun = Runnable {
        currentLiveChangeSourceTimes++
        if (currentLiveChannelItem!!.getSourceNum() == currentLiveChangeSourceTimes) {
            currentLiveChangeSourceTimes = 0
            val groupChannelIndex = getNextChannel(if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) -1 else 1)
            playChannel(groupChannelIndex[0]!!, groupChannelIndex[1]!!, false)
        } else {
            playNextSource()
        }
    }

    private fun initEpgListView() {
        mEpgInfoGridView!!.setHasFixedSize(true)
        mEpgInfoGridView!!.setLayoutManager(V7LinearLayoutManager(mContext, 1, false))
        epgListAdapter = LiveEpgAdapter()
        mEpgInfoGridView!!.setAdapter(epgListAdapter)
        mEpgInfoGridView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
            }
        })
        //电视
        mEpgInfoGridView!!.setOnItemListener(object : OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                epgListAdapter!!.setFocusedEpgIndex(-1)
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
                epgListAdapter!!.setFocusedEpgIndex(position)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {
                val date =
                    if (epgDateAdapter!!.selectedIndex < 0) Date() else epgDateAdapter!!.data[epgDateAdapter!!.selectedIndex].dateParamVal
                val dateFormat = SimpleDateFormat("yyyyMMdd")
                dateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
                val selectedData = epgListAdapter!!.getItem(position)
                val targetDate = dateFormat.format(date)
                val shiyiStartdate = targetDate + selectedData!!.originStart.replace(":", "") + "30"
                val shiyiEnddate = targetDate + selectedData.originEnd.replace(":", "") + "30"
                val now = Date()
                if (now.compareTo(selectedData.startdateTime) < 0) {
                    return
                }
                epgListAdapter!!.setSelectedEpgIndex(position)
                if (now.compareTo(selectedData.startdateTime) >= 0 && now.compareTo(selectedData.enddateTime) <= 0) {
                    mVideoView!!.release()
                    isSHIYI = false
                    mVideoView!!.setUrl(currentLiveChannelItem!!.url)
                    mVideoView!!.start()
                    epgListAdapter!!.setShiyiSelection(-1, false, timeFormat.format(date))
                }
                if (now.compareTo(selectedData.startdateTime) < 0) {
                } else {
                    mVideoView!!.release()
                    shiyi_time = "$shiyiStartdate-$shiyiEnddate"
                    isSHIYI = true
                    mVideoView!!.setUrl(currentLiveChannelItem!!.url + "?playseek=" + shiyi_time)
                    mVideoView!!.start()
                    epgListAdapter!!.setShiyiSelection(position, true, timeFormat.format(date))
                    epgListAdapter!!.notifyDataSetChanged()
                    mEpgInfoGridView!!.selectedPosition = position
                }
            }
        })

        //手机/模拟器
        epgListAdapter!!.onItemClickListener = BaseQuickAdapter.OnItemClickListener { adapter, view, position ->
            val date =
                if (epgDateAdapter!!.selectedIndex < 0) Date() else epgDateAdapter!!.data[epgDateAdapter!!.selectedIndex].dateParamVal
            val dateFormat = SimpleDateFormat("yyyyMMdd")
            dateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
            val selectedData = epgListAdapter!!.getItem(position)
            val targetDate = dateFormat.format(date)
            val shiyiStartdate = targetDate + selectedData!!.originStart.replace(":", "") + "30"
            val shiyiEnddate = targetDate + selectedData.originEnd.replace(":", "") + "30"
            val now = Date()
            if (now.compareTo(selectedData.startdateTime) < 0) {
                return@OnItemClickListener
            }
            epgListAdapter!!.setSelectedEpgIndex(position)
            if (now.compareTo(selectedData.startdateTime) >= 0 && now.compareTo(selectedData.enddateTime) <= 0) {
                mVideoView!!.release()
                isSHIYI = false
                mVideoView!!.setUrl(currentLiveChannelItem!!.url)
                mVideoView!!.start()
                epgListAdapter!!.setShiyiSelection(-1, false, timeFormat.format(date))
            }
            if (now.compareTo(selectedData.startdateTime) < 0) {
            } else {
                mVideoView!!.release()
                shiyi_time = "$shiyiStartdate-$shiyiEnddate"
                isSHIYI = true
                mVideoView!!.setUrl(currentLiveChannelItem!!.url + "?playseek=" + shiyi_time)
                mVideoView!!.start()
                epgListAdapter!!.setShiyiSelection(position, true, timeFormat.format(date))
                epgListAdapter!!.notifyDataSetChanged()
                mEpgInfoGridView!!.selectedPosition = position
            }
        }
    }

    private fun initEpgDateView() {
        mEpgDateGridView!!.setHasFixedSize(true)
        mEpgDateGridView!!.setLayoutManager(V7LinearLayoutManager(mContext, 1, false))
        epgDateAdapter = LiveEpgDateAdapter()
        val calendar = Calendar.getInstance()
        calendar.time = Date()
        //        SimpleDateFormat datePresentFormat = new SimpleDateFormat("dd-MMM", Locale.ENGLISH);
        val datePresentFormat = SimpleDateFormat("EEEE", Locale.SIMPLIFIED_CHINESE)
        calendar.add(Calendar.DAY_OF_MONTH, -6)
        for (i in 0..8) {
            val dateIns = calendar.time
            val epgDate = LiveEpgDate()
            epgDate.index = i

            // takagen99: Yesterday / Today / Tomorrow
            if (i == 5) {
                epgDate.datePresented = "昨天"
            } else if (i == 6) {
                epgDate.datePresented = "今天"
            } else if (i == 7) {
                epgDate.datePresented = "明天"
            } else if (i == 8) {
                epgDate.datePresented = "后天"
            } else {
                epgDate.datePresented = datePresentFormat.format(dateIns)
            }
            epgDate.dateParamVal = dateIns
            epgDateAdapter!!.addData(epgDate)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        mEpgDateGridView!!.setAdapter(epgDateAdapter)
        mEpgDateGridView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
            }
        })

        //电视
        mEpgDateGridView!!.setOnItemListener(object : OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                epgDateAdapter!!.setFocusedIndex(-1)
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
                epgDateAdapter!!.setFocusedIndex(position)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
                epgDateAdapter!!.setSelectedIndex(position)
                getEpg(epgDateAdapter!!.data[position].dateParamVal)
            }
        })

        //手机/模拟器
        epgDateAdapter!!.onItemClickListener = BaseQuickAdapter.OnItemClickListener { adapter, view, position ->
            FastClickCheckUtil.check(view)
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.postDelayed(mHideChannelListRun, 6000)
            epgDateAdapter!!.setSelectedIndex(position)
            getEpg(epgDateAdapter!!.data[position].dateParamVal)
        }
        epgDateAdapter!!.setSelectedIndex(1)
    }

    private fun initChannelGroupView() {
        mGroupGridView!!.setHasFixedSize(true)
        mGroupGridView!!.setLayoutManager(V7LinearLayoutManager(mContext, 1, false))
        liveChannelGroupAdapter = LiveChannelGroupAdapter()
        mGroupGridView!!.setAdapter(liveChannelGroupAdapter)
        mGroupGridView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
            }
        })

        //电视
        mGroupGridView!!.setOnItemListener(object : OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {}
            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                selectChannelGroup(position, true, -1)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {
                if (isNeedInputPassword(position)) {
                    showPasswordDialog(position, -1)
                }
            }
        })

        //手机/模拟器
        liveChannelGroupAdapter!!.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { adapter, view, position ->
                FastClickCheckUtil.check(view)
                selectChannelGroup(position, false, -1)
            }
    }

    private fun selectChannelGroup(groupIndex: Int, focus: Boolean, liveChannelIndex: Int) {
        if (focus) {
            liveChannelGroupAdapter!!.setFocusedGroupIndex(groupIndex)
            liveChannelItemAdapter!!.setFocusedChannelIndex(-1)
        }
        if (groupIndex > -1 && groupIndex != liveChannelGroupAdapter!!.selectedGroupIndex || isNeedInputPassword(
                groupIndex
            )
        ) {
            liveChannelGroupAdapter!!.setSelectedGroupIndex(groupIndex)
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex)
                return
            }
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex)
        }
        if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.postDelayed(mHideChannelListRun, 6000)
        }
    }

    private fun initLiveChannelView() {
        mChannelGridView!!.setHasFixedSize(true)
        mChannelGridView!!.setLayoutManager(V7LinearLayoutManager(mContext, 1, false))
        liveChannelItemAdapter = LiveChannelItemAdapter()
        mChannelGridView!!.setAdapter(liveChannelItemAdapter)
        mChannelGridView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
            }
        })

        //电视
        mChannelGridView!!.setOnItemListener(object : OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {}
            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                if (position < 0) return
                liveChannelGroupAdapter!!.setFocusedGroupIndex(-1)
                liveChannelItemAdapter!!.setFocusedChannelIndex(position)
                mHandler.removeCallbacks(mHideChannelListRun)
                mHandler.postDelayed(mHideChannelListRun, 6000)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {
                clickLiveChannel(position)
            }
        })

        //手机/模拟器
        liveChannelItemAdapter!!.onItemClickListener = BaseQuickAdapter.OnItemClickListener { adapter, view, position ->
            FastClickCheckUtil.check(view)
            clickLiveChannel(position)
        }
    }

    private fun clickLiveChannel(position: Int) {
        liveChannelItemAdapter!!.setSelectedChannelIndex(position)

        // Set default as Today
        epgDateAdapter!!.setSelectedIndex(6)
        if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun)
            mHandler.post(mHideChannelListRun)
            //            mHandler.postDelayed(mHideChannelListRun, 500);
        }
        playChannel(liveChannelGroupAdapter!!.selectedGroupIndex, position, false)
    }

    private fun initSettingGroupView() {
        mSettingGroupView!!.setHasFixedSize(true)
        mSettingGroupView!!.setLayoutManager(V7LinearLayoutManager(mContext, 1, false))
        liveSettingGroupAdapter = LiveSettingGroupAdapter()
        mSettingGroupView!!.setAdapter(liveSettingGroupAdapter)
        mSettingGroupView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideSettingLayoutRun)
                mHandler.postDelayed(mHideSettingLayoutRun, 5000)
            }
        })

        //电视
        mSettingGroupView!!.setOnItemListener(object : OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {}
            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                selectSettingGroup(position, true)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {}
        })

        //手机/模拟器
        liveSettingGroupAdapter!!.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { adapter, view, position ->
                FastClickCheckUtil.check(view)
                selectSettingGroup(position, false)
            }
    }

    private fun selectSettingGroup(position: Int, focus: Boolean) {
        if (!isCurrentLiveChannelValid) return
        if (focus) {
            liveSettingGroupAdapter!!.setFocusedGroupIndex(position)
            liveSettingItemAdapter!!.setFocusedItemIndex(-1)
        }
        if (position == liveSettingGroupAdapter!!.selectedGroupIndex || position < -1) return
        liveSettingGroupAdapter!!.setSelectedGroupIndex(position)
        liveSettingItemAdapter!!.setNewData(liveSettingGroupList[position].liveSettingItems)
        when (position) {
            0 -> liveSettingItemAdapter!!.selectItem(currentLiveChannelItem!!.getSourceIndex(), true, false)
            1 -> liveSettingItemAdapter!!.selectItem(livePlayerManager.getLivePlayerScale(), true, true)
            2 -> liveSettingItemAdapter!!.selectItem(livePlayerManager.getLivePlayerType(), true, true)
        }
        var scrollToPosition = liveSettingItemAdapter!!.getSelectedItemIndex()
        if (scrollToPosition < 0) scrollToPosition = 0
        mSettingItemView!!.scrollToPosition(scrollToPosition)
        mHandler.removeCallbacks(mHideSettingLayoutRun)
        mHandler.postDelayed(mHideSettingLayoutRun, 5000)
    }

    private fun initSettingItemView() {
        mSettingItemView!!.setHasFixedSize(true)
        mSettingItemView!!.setLayoutManager(V7LinearLayoutManager(mContext, 1, false))
        liveSettingItemAdapter = LiveSettingItemAdapter()
        mSettingItemView!!.setAdapter(liveSettingItemAdapter)
        mSettingItemView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                mHandler.removeCallbacks(mHideSettingLayoutRun)
                mHandler.postDelayed(mHideSettingLayoutRun, 5000)
            }
        })

        //电视
        mSettingItemView!!.setOnItemListener(object : OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {}
            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                if (position < 0) return
                liveSettingGroupAdapter!!.setFocusedGroupIndex(-1)
                liveSettingItemAdapter!!.setFocusedItemIndex(position)
                mHandler.removeCallbacks(mHideSettingLayoutRun)
                mHandler.postDelayed(mHideSettingLayoutRun, 5000)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {
                clickSettingItem(position)
            }
        })

        //手机/模拟器
        liveSettingItemAdapter!!.onItemClickListener = BaseQuickAdapter.OnItemClickListener { adapter, view, position ->
            FastClickCheckUtil.check(view)
            clickSettingItem(position)
        }
    }

    private fun clickSettingItem(position: Int) {
        val settingGroupIndex = liveSettingGroupAdapter!!.selectedGroupIndex
        if (settingGroupIndex < 4) {
            if (position == liveSettingItemAdapter!!.getSelectedItemIndex()) return
            liveSettingItemAdapter!!.selectItem(position, true, true)
        }
        when (settingGroupIndex) {
            0 -> {
                currentLiveChannelItem!!.setSourceIndex(position)
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true)
            }

            1 -> livePlayerManager.changeLivePlayerScale(mVideoView!!, position, currentLiveChannelItem!!.channelName)
            2 -> {
                mVideoView!!.release()
                livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem!!.channelName)
                mVideoView!!.setUrl(currentLiveChannelItem!!.url)
                mVideoView!!.start()
            }

            3 -> Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position)
            4 -> {
                var select = false
                when (position) {
                    0 -> {
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select)
                        showTime()
                    }

                    1 -> {
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select)
                        showNetSpeed()
                    }

                    2 -> {
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select)
                    }

                    3 -> {
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select)
                    }

                    4 -> {
                        // takagen99 : Added Skip Password Option
                        select = !Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false)
                        Hawk.put(HawkConfig.LIVE_SKIP_PASSWORD, select)
                    }
                }
                liveSettingItemAdapter!!.selectItem(position, select, false)
            }

            5 -> when (position) {
                0 -> {
                    // takagen99 : Added Live History list selection - 直播列表
                    val liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, ArrayList<String>())
                    if (liveHistory.isEmpty()) return
                    val current = Hawk.get(HawkConfig.LIVE_URL, "")
                    var idx = 0
                    if (liveHistory.contains(current)) idx = liveHistory.indexOf(current)
                    val dialog = ApiHistoryDialog(this@LivePlayActivity)
                    dialog.setTip(getString(R.string.dia_history_live))
                    dialog.setAdapter(object : ApiHistoryDialogAdapter.SelectDialogInterface {
                        override fun click(liveURL: String) {
                            var liveURL = liveURL
                            Hawk.put(HawkConfig.LIVE_URL, liveURL)
                            liveChannelGroupList.clear()
                            try {
                                liveURL = Base64.encodeToString(
                                    liveURL.toByteArray(charset("UTF-8")),
                                    Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP
                                )
                                liveURL = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=$liveURL"
                                loadProxyLives(liveURL)
                            } catch (th: Throwable) {
                                th.printStackTrace()
                            }
                            dialog.dismiss()
                        }

                        override fun del(value: String, data: ArrayList<String>) {
                            Hawk.put(HawkConfig.LIVE_HISTORY, data)
                        }
                    }, liveHistory, idx)
                    dialog.show()
                }
            }

            6 -> when (position) {
                0 -> finish()
            }
        }
        mHandler.removeCallbacks(mHideSettingLayoutRun)
        mHandler.postDelayed(mHideSettingLayoutRun, 5000)
    }

    private fun initLiveChannelList() {
        val list = get().channelGroupList
        if (list.isEmpty()) {
            Toast.makeText(getInstance(), "频道列表为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (list.size == 1 && list[0].groupName!!.startsWith("http://127.0.0.1")) {
            showLoading()
            loadProxyLives(list[0].groupName)
        } else {
            liveChannelGroupList.clear()
            liveChannelGroupList.addAll(list)
            showSuccess()
            initLiveState()
        }
    }

    //加载列表
    fun loadProxyLives(url: String?) {
        var url = url
        url = try {
            val parsedUrl = Uri.parse(url)
            String(
                Base64.decode(
                    parsedUrl.getQueryParameter("ext"),
                    Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP
                ), charset("UTF-8")
            )
        } catch (th: Throwable) {
            Toast.makeText(getInstance(), "频道列表为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        showLoading()
        OkGo.get<String>(url).execute(object : AbsCallback<String?>() {
            @Throws(Throwable::class)
            override fun convertResponse(response: okhttp3.Response): String {
                return response.body()!!.string()
            }

            override fun onSuccess(response: Response<String?>?) {
                val livesArray: JsonArray
                val linkedHashMap = LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>>()
                TxtSubscribe.parse(linkedHashMap, response?.body())
                livesArray = TxtSubscribe.live2JsonArray(linkedHashMap)
                get().loadLives(livesArray)
                val list = get().channelGroupList
                if (list.isEmpty()) {
                    Toast.makeText(getInstance(), "频道列表为空", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                liveChannelGroupList.clear()
                liveChannelGroupList.addAll(list)
                mHandler.post {
                    showSuccess()
                    initLiveState()
                }
            }
        })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_LIVEPLAY_UPDATE) {
            val bundle = event.obj as Bundle
            val channelGroupIndex = bundle.getInt("groupIndex", 0)
            val liveChannelIndex = bundle.getInt("channelIndex", 0)
            if (channelGroupIndex != liveChannelGroupAdapter!!.selectedGroupIndex) selectChannelGroup(
                channelGroupIndex,
                true,
                liveChannelIndex
            ) else {
                clickLiveChannel(liveChannelIndex)
                mGroupGridView!!.scrollToPosition(channelGroupIndex)
                mChannelGridView!!.scrollToPosition(liveChannelIndex)
                playChannel(channelGroupIndex, liveChannelIndex, false)
            }
        }
    }

    private fun initLiveState() {
        val lastChannelName = Hawk.get(HawkConfig.LIVE_CHANNEL, "")
        var lastChannelGroupIndex = -1
        var lastLiveChannelIndex = -1
        val intent = intent
        if (intent != null && intent.extras != null) {
            val bundle = intent.extras
            lastChannelGroupIndex = bundle!!.getInt("groupIndex", 0)
            lastLiveChannelIndex = bundle.getInt("channelIndex", 0)
        } else {
            for (liveChannelGroup in liveChannelGroupList) {
                for (liveChannelItem in liveChannelGroup.liveChannels) {
                    if (liveChannelItem.channelName == lastChannelName) {
                        lastChannelGroupIndex = liveChannelGroup.groupIndex
                        lastLiveChannelIndex = liveChannelItem.channelIndex
                        break
                    }
                }
                if (lastChannelGroupIndex != -1) break
            }
            if (lastChannelGroupIndex == -1) {
                lastChannelGroupIndex = firstNoPasswordChannelGroup
                if (lastChannelGroupIndex == -1) lastChannelGroupIndex = 0
                lastLiveChannelIndex = 0
            }
        }
        livePlayerManager.init(mVideoView)
        showTime()
        showNetSpeed()
        tvLeftChannelListLayout!!.visibility = View.INVISIBLE
        tvRightSettingLayout!!.visibility = View.INVISIBLE
        liveChannelGroupAdapter!!.setNewData(liveChannelGroupList)
        selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex)
    }

    private val isListOrSettingLayoutVisible: Boolean
        private get() = tvLeftChannelListLayout!!.visibility == View.VISIBLE || tvRightSettingLayout!!.visibility == View.VISIBLE

    private fun initLiveSettingGroupList() {
        val groupNames =
            ArrayList(mutableListOf("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "直播地址", "退出直播"))
        val itemsArrayList = ArrayList<ArrayList<String>>()
        val sourceItems = ArrayList<String>()
        val scaleItems = ArrayList(mutableListOf("默认", "16:9", "4:3", "填充", "原始", "裁剪"))
        val playerDecoderItems = ArrayList(mutableListOf("系统", "ijk硬解", "ijk软解", "exo"))
        val timeoutItems = ArrayList(mutableListOf("5s", "10s", "15s", "20s", "25s", "30s"))
        val personalSettingItems = ArrayList(mutableListOf("显示时间", "显示网速", "换台反转", "跨选分类", "关闭密码"))
        val liveAdd = ArrayList(mutableListOf("列表历史"))
        val exitConfirm = ArrayList(mutableListOf("确定"))
        itemsArrayList.add(sourceItems)
        itemsArrayList.add(scaleItems)
        itemsArrayList.add(playerDecoderItems)
        itemsArrayList.add(timeoutItems)
        itemsArrayList.add(personalSettingItems)
        itemsArrayList.add(liveAdd)
        itemsArrayList.add(exitConfirm)
        liveSettingGroupList.clear()
        for (i in groupNames.indices) {
            val liveSettingGroup = LiveSettingGroup()
            val liveSettingItemList = ArrayList<LiveSettingItem>()
            liveSettingGroup.groupIndex = i
            liveSettingGroup.groupName = groupNames[i]
            for (j in itemsArrayList[i].indices) {
                val liveSettingItem = LiveSettingItem()
                liveSettingItem.itemIndex = j
                liveSettingItem.itemName = itemsArrayList[i][j]
                liveSettingItemList.add(liveSettingItem)
            }
            liveSettingGroup.liveSettingItems = liveSettingItemList
            liveSettingGroupList.add(liveSettingGroup)
        }
        liveSettingGroupList[3].liveSettingItems[Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)].isItemSelected =
            true
        liveSettingGroupList[4].liveSettingItems[0].isItemSelected = Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)
        liveSettingGroupList[4].liveSettingItems[1].isItemSelected =
            Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)
        liveSettingGroupList[4].liveSettingItems[2].isItemSelected =
            Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)
        liveSettingGroupList[4].liveSettingItems[3].isItemSelected =
            Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)
        liveSettingGroupList[4].liveSettingItems[4].isItemSelected = Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false)
    }

    private fun loadCurrentSourceList() {
        val currentSourceNames = currentLiveChannelItem!!.channelSourceNames
        val liveSettingItemList = ArrayList<LiveSettingItem>()
        for (j in currentSourceNames.indices) {
            val liveSettingItem = LiveSettingItem()
            liveSettingItem.itemIndex = j
            liveSettingItem.itemName = currentSourceNames[j]
            liveSettingItemList.add(liveSettingItem)
        }
        liveSettingGroupList[0].liveSettingItems = liveSettingItemList
    }

    fun showTime() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)) {
            mHandler.post(mUpdateTimeRun)
            tvTime!!.visibility = View.VISIBLE
        } else {
            mHandler.removeCallbacks(mUpdateTimeRun)
            tvTime!!.visibility = View.GONE
        }
    }

    private val mUpdateTimeRun: Runnable = object : Runnable {
        override fun run() {
            val day = Date()
            val df = SimpleDateFormat("HH:mm:ss")
            tvTime!!.text = df.format(day)
            mHandler.postDelayed(this, 1000)
        }
    }

    private fun showNetSpeed() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)) {
            mHandler.post(mUpdateNetSpeedRun)
            tvNetSpeed!!.visibility = View.VISIBLE
        } else {
            mHandler.removeCallbacks(mUpdateNetSpeedRun)
            tvNetSpeed!!.visibility = View.GONE
        }
    }

    private val mUpdateNetSpeedRun: Runnable = object : Runnable {
        override fun run() {
            if (mVideoView == null) return
            tvNetSpeed!!.text = String.format("%.2fMB/s", mVideoView!!.tcpSpeed.toFloat() / 1024.0 / 1024.0)
            mHandler.postDelayed(this, 1000)
        }
    }

    private fun showPasswordDialog(groupIndex: Int, liveChannelIndex: Int) {
        if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) mHandler.removeCallbacks(mHideChannelListRun)
        val dialog = LivePasswordDialog(this)
        dialog.setOnListener(object : LivePasswordDialog.OnListener {
            override fun onChange(password: String) {
                if (password == liveChannelGroupList[groupIndex].groupPassword) {
                    channelGroupPasswordConfirmed.add(groupIndex)
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex)
                } else {
                    Toast.makeText(getInstance(), "密码错误", Toast.LENGTH_SHORT).show()
                }
                if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) mHandler.postDelayed(
                    mHideChannelListRun,
                    6000
                )
            }

            override fun onCancel() {
                if (tvLeftChannelListLayout!!.visibility == View.VISIBLE) {
                    val groupIndex = liveChannelGroupAdapter!!.selectedGroupIndex
                    liveChannelItemAdapter!!.setNewData(getLiveChannels(groupIndex))
                }
            }
        })
        dialog.show()
    }

    private fun loadChannelGroupDataAndPlay(groupIndex: Int, liveChannelIndex: Int) {
        liveChannelItemAdapter!!.setNewData(getLiveChannels(groupIndex))
        if (groupIndex == currentChannelGroupIndex) {
            if (currentLiveChannelIndex > -1) mChannelGridView!!.scrollToPosition(currentLiveChannelIndex)
            liveChannelItemAdapter!!.setSelectedChannelIndex(currentLiveChannelIndex)
        } else {
            mChannelGridView!!.scrollToPosition(0)
            liveChannelItemAdapter!!.setSelectedChannelIndex(-1)
        }
        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex)
            mGroupGridView!!.scrollToPosition(groupIndex)
            mChannelGridView!!.scrollToPosition(liveChannelIndex)
            playChannel(groupIndex, liveChannelIndex, false)
        }
    }

    private fun isNeedInputPassword(groupIndex: Int): Boolean {
        return (liveChannelGroupList[groupIndex].groupPassword.isNotEmpty()
                && !isPasswordConfirmed(groupIndex))
    }

    private fun isPasswordConfirmed(groupIndex: Int): Boolean {
        return if (Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false)) {
            true
        } else {
            for (confirmedNum in channelGroupPasswordConfirmed) {
                if (confirmedNum == groupIndex) return true
            }
            false
        }
    }

    private fun getLiveChannels(groupIndex: Int): ArrayList<LiveChannelItem> {
        return if (!isNeedInputPassword(groupIndex)) {
            liveChannelGroupList[groupIndex].liveChannels
        } else {
            ArrayList()
        }
    }

    private fun getNextChannel(direction: Int): Array<Int?> {
        var channelGroupIndex = currentChannelGroupIndex
        var liveChannelIndex = currentLiveChannelIndex

        //跨选分组模式下跳过加密频道分组（遥控器上下键换台/超时换源）
        if (direction > 0) {
            liveChannelIndex++
            if (liveChannelIndex >= getLiveChannels(channelGroupIndex).size) {
                liveChannelIndex = 0
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex++
                        if (channelGroupIndex >= liveChannelGroupList.size) channelGroupIndex = 0
                    } while (!liveChannelGroupList[channelGroupIndex].groupPassword.isEmpty() || channelGroupIndex == currentChannelGroupIndex)
                }
            }
        } else {
            liveChannelIndex--
            if (liveChannelIndex < 0) {
                if (Hawk.get<Boolean>(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex--
                        if (channelGroupIndex < 0) channelGroupIndex = liveChannelGroupList.size - 1
                    } while (liveChannelGroupList[channelGroupIndex].groupPassword.isNotEmpty() || channelGroupIndex == currentChannelGroupIndex)
                }
                liveChannelIndex = getLiveChannels(channelGroupIndex).size - 1
            }
        }
        val groupChannelIndex = arrayOfNulls<Int>(2)
        groupChannelIndex[0] = channelGroupIndex
        groupChannelIndex[1] = liveChannelIndex
        return groupChannelIndex
    }

    private val firstNoPasswordChannelGroup: Int
        private get() {
            for (liveChannelGroup in liveChannelGroupList) {
                if (liveChannelGroup.groupPassword.isEmpty()) return liveChannelGroup.groupIndex
            }
            return -1
        }
    private val isCurrentLiveChannelValid: Boolean
        private get() {
            if (currentLiveChannelItem == null) {
                Toast.makeText(getInstance(), "请先选择频道", Toast.LENGTH_SHORT).show()
                return false
            }
            return true
        }

    companion object {
        @JvmField
        var currentChannelGroupIndex = 0
        private var channel_Name: LiveChannelItem? = null
        private val hsEpg = Hashtable<String, ArrayList<Epginfo>>()
        private var shiyi_time: String? = null //时移时间
    }
}