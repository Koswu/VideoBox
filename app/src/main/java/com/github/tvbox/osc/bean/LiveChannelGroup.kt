package com.github.tvbox.osc.bean

class LiveChannelGroup {
    /**
     * groupIndex : 分组索引号
     * groupName : 分组名称
     * password : 分组密码
     */
    @JvmField
    var groupIndex = 0
    @JvmField
    var groupName: String = ""
    var groupPassword: String = ""
    var liveChannels  = ArrayList<LiveChannelItem>()
}
