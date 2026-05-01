package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.onepass.domain.model.WeChatActivity
import com.example.onepass.domain.model.WeChatData
import com.example.onepass.domain.model.WeChatId
import com.example.onepass.service.AccessibilityNodeHelper.safeRecycle
import com.example.onepass.service.AccessibilityNodeHelper.safeRecycleAll
import com.example.onepass.utils.PerformanceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 微信视频通话无障碍自动化服务
 *
 * 流程说明：
 * 步骤1: 确保在微信首页
 * 步骤2: 点击搜索按钮
 * 步骤3: 输入联系人昵称
 * 步骤4: 选择联系人进入聊天界面
 * 步骤5: 点击更多按钮(+)
 * 步骤6: 点击视频/语音通话
 * 步骤7: 点击确认通话
 */
class SelectToSpeakService : AccessibilityService() {
    companion object {
        private const val TAG = "WechatAccessibility"
        private const val MAX_RETRY_COUNT = 3
        private const val MAX_NAVIGATION_ATTEMPTS = 3
    }

    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    // 状态管理 - 使用AtomicBoolean确保线程安全
    private val isProcessing = AtomicBoolean(false)
    private var retryCount = 0
    private var navigationAttempts = 0
    private var lastWindowClassName = ""

    // 主动触发下一步的延迟任务
    private var nextStepRunnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentActivity = event?.className?.toString() ?: run {
            Log.d(TAG, "事件为空或className为null")
            return
        }

        // 跳过系统组件和无效事件
        if (shouldSkipActivity(currentActivity)) {
            Log.d(TAG, "跳过Activity: $currentActivity")
            return
        }

        lastWindowClassName = currentActivity
        Log.d(TAG, "Current Activity: $currentActivity, Step: ${WeChatData.index}")

        // 如果正在处理，跳过新事件（使用原子操作确保线程安全）
        if (isProcessing.get()) {
            Log.d(TAG, "正在处理中，跳过新事件: $currentActivity")
            return
        }


        when (WeChatData.index) {
            1 -> processStep1(currentActivity)
            2 -> processStep2(currentActivity)
            3 -> processStep3(currentActivity)
            4 -> processStep4(currentActivity)
            5 -> processStep5(currentActivity)
            6 -> processStep6(currentActivity)
            7 -> processStep7(currentActivity)
        }
    }

    /**
     * 判断是否需要跳过某些Activity事件
     */
    private fun shouldSkipActivity(activityName: String): Boolean {
        return activityName.contains("Toast") ||
                activityName.contains("SoftInputWindow") ||
                activityName == "com.example.onepass.MainActivity"
    }

    // ==================== 步骤处理 ====================

    /**
     * 步骤1: 确保在微信首页
     * 逻辑：如果在首页 -> 进入步骤2；如果在其他页面 -> 执行返回直到回到首页
     */
    private fun processStep1(currentActivity: String) {
        Log.d(TAG, ">>> 进入步骤1，当前Activity: $currentActivity <<<")
        serviceScope.launch {
            setProcessing(true)
            try {
                Log.d(TAG, "步骤1 - 当前Activity: $currentActivity")
                when {
                    // 已在首页
                    isWechatHomePage(currentActivity) -> {
                        Log.d(TAG, ">>> 已在微信首页，点击底部【微信】按钮返回聊天列表 <<<")
                        var rootNode: AccessibilityNodeInfo? = null
                        var wechatTab: List<AccessibilityNodeInfo>? = null
                        try {
                            rootNode = rootInActiveWindow
                            if (rootNode != null) {
                                // 方法1: 通过View ID查找
                                wechatTab = rootNode.findAccessibilityNodeInfosByViewId(WeChatId.BOTTOM_WECHAT.id)
                                if (wechatTab.isEmpty()) {
                                    // 方法2: 通过文本"微信"查找
                                    wechatTab = rootNode.findAccessibilityNodeInfosByText("微信")
                                }
                                if (wechatTab.isEmpty()) {
                                    // 方法3: 通过contentDescription查找
                                    wechatTab = rootNode.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/dn")
                                }

                                if (wechatTab.isNotEmpty()) {
                                    val tabNode = wechatTab.first()
                                    val clickResult = if (tabNode.isClickable) {
                                        tabNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    } else {
                                        tabNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                                    }
                                    Log.d(TAG, "点击底部【微信】按钮结果: $clickResult")
                                    waitStep(500)
                                } else {
                                    Log.d(TAG, "未找到底部【微信】按钮，直接进入步骤2")
                                }
                            }
                            Log.d(TAG, ">>> 进入步骤2 <<<")
                            resetRetryAndNavigation()
                            WeChatData.updateIndex(2)
                            setProcessing(false)
                            scheduleNextStep(500)
                            return@launch
                        } finally {
                            wechatTab?.safeRecycleAll()
                            rootNode?.safeRecycle()
                        }
                    }
                    // 在搜索界面，清空搜索框并进入步骤3
                    isSearchPage(currentActivity) -> {
                        Log.d(TAG, ">>> 在搜索界面，清空搜索框并进入步骤3 <<<")
                        var rootNode: AccessibilityNodeInfo? = null
                        try {
                            rootNode = rootInActiveWindow
                            if (rootNode != null) {
                                val inputNode = findInputField(rootNode)
                                if (inputNode != null && inputNode.isEditable) {
                                    val clearResult = clearInputField(inputNode)
                                    if (clearResult) {
                                        waitStep(200)
                                        resetRetryAndNavigation()
                                        WeChatData.updateIndex(3)
                                        setProcessing(false)
                                        scheduleNextStep(500)
                                        return@launch
                                    } else {
                                        Log.e(TAG, "清空输入框失败，重试")
                                        handleRetry("清空输入框失败", 1)
                                        setProcessing(false)
                                        return@launch
                                    }
                                } else {
                                    Log.d(TAG, "未找到输入框，直接进入步骤3")
                                    resetRetryAndNavigation()
                                    WeChatData.updateIndex(3)
                                    setProcessing(false)
                                    scheduleNextStep(500)
                                    return@launch
                                }
                            } else {
                                Log.d(TAG, "rootNode为空，直接进入步骤3")
                                resetRetryAndNavigation()
                                WeChatData.updateIndex(3)
                                setProcessing(false)
                                scheduleNextStep(500)
                                return@launch
                            }
                        } finally {
                            rootNode?.safeRecycle()
                        }
                    }
                    // 在聊天界面
                    isChatPage(currentActivity) -> {
                        Log.d(TAG, ">>> 在聊天界面，执行返回 <<<")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        waitStep(500)
                        incrementNavigationAttempts()
                        setProcessing(false)
                        scheduleNextStep(500)
                        return@launch
                    }
                    // 有弹窗
                    isDialogPage(currentActivity) -> {
                        Log.d(TAG, ">>> 检测到弹窗，关闭弹窗 <<<")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        waitStep(500)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        waitStep(500)
                        setProcessing(false)
                        scheduleNextStep(500)
                        return@launch
                    }
                    // 其他页面，尝试返回
                    else -> {
                        Log.d(TAG, ">>> 在其他页面，当前Activity: $currentActivity <<<")
                        if (navigationAttempts < MAX_NAVIGATION_ATTEMPTS) {
                            Log.d(TAG, ">>> 在其他页面，执行返回 (${navigationAttempts + 1}/$MAX_NAVIGATION_ATTEMPTS) <<<")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            waitStep(500)
                            incrementNavigationAttempts()
                            setProcessing(false)
                            scheduleNextStep(500)
                            return@launch
                        } else {
                            Log.d(TAG, ">>> 导航尝试次数达到上限，重置 <<<")
                            resetAndStop()
                            setProcessing(false)
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "步骤1处理失败", e)
                handleError(1)
                setProcessing(false)
            }
        }
    }

    /**
     * 步骤2: 点击搜索按钮
     */
    private fun processStep2(currentActivity: String) {
        Log.d(TAG, ">>> 进入步骤2，当前Activity: $currentActivity <<<")
        serviceScope.launch {
            PerformanceMonitor.startTimer("step2_clickSearch")
            setProcessing(true)
            var rootNode: AccessibilityNodeInfo? = null
            try {
                rootNode = rootInActiveWindow ?: run {
                    Log.d(TAG, "rootNode为空")
                    setProcessing(false)
                    PerformanceMonitor.endTimer("step2_clickSearch")
                    return@launch
                }

                // 查找搜索按钮 - 优先查找可点击的搜索图标
                val searchNode = findSearchButton(rootNode)

                if (searchNode != null) {
                    Log.d(TAG, "点击搜索按钮")
                    val clickResult = searchNode.click()
                    Log.d(TAG, "搜索按钮点击结果: $clickResult")
                    if (!clickResult) {
                        Log.e(TAG, "搜索按钮点击失败，重试")
                        handleRetry("搜索按钮点击失败", 1)
                        setProcessing(false)
                        PerformanceMonitor.endTimer("step2_clickSearch")
                        return@launch
                    }
                    waitStep(500)
                    resetRetryAndNavigation()
                    WeChatData.updateIndex(3)
                    setProcessing(false)
                    PerformanceMonitor.endTimer("step2_clickSearch")
                    scheduleNextStep(500)
                    return@launch
                } else {
                    Log.d(TAG, "未找到搜索按钮，可能已在搜索界面")
                    // 检查是否已经在搜索界面
                    if (isSearchPage(currentActivity)) {
                        Log.d(TAG, "已在搜索界面，进入步骤3")
                        resetRetryAndNavigation()
                        WeChatData.updateIndex(3)
                        setProcessing(false)
                        PerformanceMonitor.endTimer("step2_clickSearch")
                        scheduleNextStep(500)
                        return@launch
                    } else {
                        handleRetry("未找到搜索按钮", 1)
                        setProcessing(false)
                        PerformanceMonitor.endTimer("step2_clickSearch")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "步骤2处理失败", e)
                handleError(1)
                setProcessing(false)
                PerformanceMonitor.endTimer("step2_clickSearch")
            } finally {
                rootNode?.safeRecycle()
            }
        }
    }

    /**
     * 步骤3: 输入联系人昵称
     */
    private fun processStep3(currentActivity: String) {
        Log.d(TAG, ">>> 进入步骤3，当前Activity: $currentActivity <<<")
        serviceScope.launch {
            setProcessing(true)
            var rootNode: AccessibilityNodeInfo? = null
            try {
                rootNode = rootInActiveWindow ?: run {
                    Log.d(TAG, "rootNode为空")
                    setProcessing(false)
                    return@launch
                }

                // 查找输入框
                val inputNode = findInputField(rootNode)

                if (inputNode != null && inputNode.isEditable) {
                    Log.d(TAG, "输入联系人: ${WeChatData.value}")
                    val result = inputNode.input(WeChatData.value)
                    Log.d(TAG, "输入结果: $result")
                    if (!result) {
                        Log.e(TAG, "输入失败，重试")
                        handleRetry("输入失败", 2)
                        setProcessing(false)
                        return@launch
                    }
                    Log.d(TAG, "输入成功，等待搜索结果")
                    waitStep(500)
                    resetRetryAndNavigation()
                    WeChatData.updateIndex(4)
                    setProcessing(false)
                    scheduleNextStep(500)
                    return@launch
                } else {
                    handleRetry("未找到输入框", 2)
                    setProcessing(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "步骤3处理失败", e)
                handleError(2)
                setProcessing(false)
            } finally {
                rootNode?.safeRecycle()
            }
        }
    }

    /**
     * 步骤4: 选择第一个搜索结果进入聊天界面
     */
    private fun processStep4(currentActivity: String) {
        Log.d(TAG, ">>> 进入步骤4，当前Activity: $currentActivity <<<")
        serviceScope.launch {
            setProcessing(true)
            var rootNode: AccessibilityNodeInfo? = null
            try {
                rootNode = rootInActiveWindow ?: run {
                    Log.d(TAG, "rootNode为空")
                    setProcessing(false)
                    return@launch
                }

                // 查找搜索结果列表
                val contactNode = findSearchResult(rootNode)

                if (contactNode != null) {
                    Log.d(TAG, "点击联系人")
                    val clickResult = contactNode.click()
                    if (!clickResult) {
                        Log.e(TAG, "点击联系人失败，重试")
                        handleRetry("点击联系人失败", 3)
                        setProcessing(false)
                        return@launch
                    }
                    waitStep(500)
                    resetRetryAndNavigation()
                    WeChatData.updateIndex(5)
                    setProcessing(false)
                    scheduleNextStep(500)
                    return@launch
                } else {
                    // 搜索结果可能还未加载，不重试，等待下一个事件
                    Log.d(TAG, "搜索结果未出现，继续等待")
                    setProcessing(false)
                    scheduleNextStep(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "步骤4处理失败", e)
                handleError(3)
                setProcessing(false)
            } finally {
                rootNode?.safeRecycle()
            }
        }
    }

    /**
     * 步骤5: 点击更多按钮(+)
     */
    private fun processStep5(currentActivity: String) {
        Log.d(TAG, ">>> 进入步骤5，当前Activity: $currentActivity <<<")
        serviceScope.launch {
            setProcessing(true)
            var rootNode: AccessibilityNodeInfo? = null
            try {
                rootNode = rootInActiveWindow ?: run {
                    Log.d(TAG, "rootNode为空")
                    setProcessing(false)
                    return@launch
                }

                // 确保在聊天界面
                if (!isChatPage(currentActivity)) {
                    Log.d(TAG, "不在聊天界面，等待")
                    setProcessing(false)
                    scheduleNextStep(300)
                    return@launch
                }

                // 查找更多按钮
                val moreNode = findMoreButton(rootNode)

                if (moreNode != null) {
                    Log.d(TAG, "点击更多按钮")
                    val clickResult = moreNode.click()
                    if (!clickResult) {
                        Log.e(TAG, "点击更多按钮失败，重试")
                        handleRetry("点击更多按钮失败", 5)
                        setProcessing(false)
                        return@launch
                    }
                    waitStep(500)
                    resetRetryAndNavigation()
                    WeChatData.updateIndex(6)
                    setProcessing(false)
                    scheduleNextStep(500)
                    return@launch
                } else {
                    handleRetry("未找到更多按钮", 5)
                    setProcessing(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "步骤5处理失败", e)
                handleError(4)
                setProcessing(false)
            } finally {
                rootNode?.safeRecycle()
            }
        }
    }

    /**
     * 步骤6: 点击视频通话菜单（从更多面板中选择）
     */
    private fun processStep6(currentActivity: String) {
        Log.d(TAG, ">>> 进入步骤6，当前Activity: $currentActivity <<<")
        serviceScope.launch {
            setProcessing(true)
            var menuNodes: List<AccessibilityNodeInfo>? = null
            try {
                // 更多面板展开时，Activity 通常仍为微信聊天界面
                val root = rootInActiveWindow
                val callText = WeChatData.findText(false) // 获取“视频通话”文字
                Log.d(TAG, "步骤6 - 正在查找更多面板中的文字: $callText")

                menuNodes = root?.findAccessibilityNodeInfosByText(callText)

                if (!menuNodes.isNullOrEmpty()) {
                    val targetNode = menuNodes.first()
                    val rect = Rect()
                    targetNode.getBoundsInScreen(rect)

                    Log.d(TAG, "找到通话图标，位置: (${rect.centerX()}, ${rect.centerY()})，执行模拟点击")

                    // 使用你代码中的 performClick 模拟物理点击，解决 performAction 无效的问题
                    performClick(rect.centerX().toFloat(), rect.centerY().toFloat())

                    waitStep(800) // 等待底部菜单弹窗弹出
                    WeChatData.updateIndex(7)
                    setProcessing(false)
                    scheduleNextStep(200)
                } else {
                    Log.e(TAG, "更多面板中未找到文字: $callText")
                    handleRetry("未找到通话图标", 5)
                    setProcessing(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "步骤6处理失败", e)
                handleError(5)
                setProcessing(false)
            } finally {
                menuNodes?.safeRecycleAll()
            }
        }
    }

    /**
     * 步骤7: 点击视频/语音通话选项（底部确认弹窗）
     */
    private fun processStep7(currentActivity: String) {
        Log.d(TAG, ">>> 进入步骤7，当前Activity: $currentActivity <<<")
        serviceScope.launch {
            setProcessing(true)
            var options: List<AccessibilityNodeInfo>? = null
            try {
                // 判断是否在弹窗页面，或者 Activity 还没切换但弹窗已出的情况
                val root = rootInActiveWindow
                val confirmText = WeChatData.findText(true) // 获取弹窗里的“视频通话”
                Log.d(TAG, "步骤7 - 正在查找弹窗选项: $confirmText")

                options = root?.findAccessibilityNodeInfosByText(confirmText)

                if (!options.isNullOrEmpty()) {
                    val optionNode = options.first()

                    // 优先尝试标准点击
                    var clickResult = optionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    // 如果标准点击失败，尝试坐标点击
                    if (!clickResult) {
                        val rect = Rect()
                        optionNode.getBoundsInScreen(rect)
                        performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                        clickResult = true
                    }

                    Log.d(TAG, "点击通话选项结果: $clickResult")
                    waitStep(500)
                    Log.d(TAG, ">>> 通话流程完成 <<<")
                    WeChatData.updateIndex(0)
                    setProcessing(false)
                } else {
                    // 如果没找到，可能是弹窗还没加载完，等待下一次事件
                    Log.d(TAG, "未找到通话选项，继续等待...")
                    setProcessing(false)
                    scheduleNextStep(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "步骤7处理失败", e)
                handleError(6)
                setProcessing(false)
            } finally {
                options?.safeRecycleAll()
            }
        }
    }

    // ==================== 页面判断 ====================

    private fun isWechatHomePage(activityName: String): Boolean {
        return activityName == WeChatActivity.INDEX.id
    }

    private fun isChatPage(activityName: String): Boolean {
        return activityName == WeChatActivity.CHAT.id
    }

    private fun isSearchPage(activityName: String): Boolean {
        return activityName == WeChatActivity.SEARCH.id
    }

    private fun isDialogPage(activityName: String): Boolean {
        return activityName == WeChatActivity.DIALOG.id ||
                activityName == WeChatActivity.DIALOG_OLD.id
    }

    // ==================== 节点查找工具 ====================

    private fun findSearchButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 方法1: 通过ViewId查找（最快）
        var searchById: List<AccessibilityNodeInfo>? = null
        try {
            searchById = rootNode.findAccessibilityNodeInfosByViewId(WeChatId.SEARCH.id)
            if (searchById.isNotEmpty()) {
                return searchById.first()
            }
        } finally {
            searchById?.safeRecycleAll()
        }

        // 方法2: 通过文本查找
        var searchByText: List<AccessibilityNodeInfo>? = null
        try {
            searchByText = rootNode.findAccessibilityNodeInfosByText("搜索")
            if (searchByText.isNotEmpty()) {
                return searchByText.first()
            }
        } finally {
            searchByText?.safeRecycleAll()
        }

        // 方法3: 查找可点击的搜索图标
        return findClickableNodeByContent(rootNode, "搜索", "Search")
    }

    private fun findInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 方法1: 通过ViewId查找（最快）
        var inputById: List<AccessibilityNodeInfo>? = null
        try {
            inputById = rootNode.findAccessibilityNodeInfosByViewId(WeChatId.INPUT.id)
            if (inputById.isNotEmpty()) {
                return inputById.first()
            }
        } finally {
            inputById?.safeRecycleAll()
        }

        // 方法2: 查找可编辑节点
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        findEditableNodes(rootNode, editableNodes)
        if (editableNodes.isNotEmpty()) {
            return editableNodes.first()
        }

        return null
    }

    private fun findSearchResult(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 方法1: 通过ViewId查找
        var listById: List<AccessibilityNodeInfo>? = null
        try {
            listById = rootNode.findAccessibilityNodeInfosByViewId(WeChatId.LIST.id)
            if (listById.isNotEmpty()) {
                return listById.first()
            }
        } finally {
            listById?.safeRecycleAll()
        }

        // 方法2: 查找列表类型的节点
        return findFirstClickableListItem(rootNode)
    }

    private fun findMoreButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 方法1: 通过ViewId查找（最快）
        var moreById: List<AccessibilityNodeInfo>? = null
        try {
            moreById = rootNode.findAccessibilityNodeInfosByViewId(WeChatId.MORE.id)
            if (moreById.isNotEmpty()) {
                return moreById.first()
            }
        } finally {
            moreById?.safeRecycleAll()
        }

        // 方法2: 通过文本查找
        var moreByText: List<AccessibilityNodeInfo>? = null
        try {
            moreByText = rootNode.findAccessibilityNodeInfosByText("更多")
            if (moreByText.isNotEmpty()) {
                return moreByText.first()
            }
        } finally {
            moreByText?.safeRecycleAll()
        }

        // 方法3: 查找可点击的更多图标
        return findClickableNodeByContent(rootNode, "更多", "More")
    }

    private fun findCallButton(): AccessibilityNodeInfo? {
        val callText = WeChatData.findText(true)
        Log.d(TAG, "查找通话按钮: $callText")

        var options: List<AccessibilityNodeInfo>? = null
        try {
            options = rootInActiveWindow?.findAccessibilityNodeInfosByText(callText)
            if (options != null && options.isNotEmpty()) {
                val callNode = options.first()
                Log.d(TAG, "找到通话按钮: $callText, 可点击: ${callNode.isClickable}")
                return callNode
            }
        } finally {
            options?.safeRecycleAll()
        }

        return null
    }

    private fun findConfirmButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 查找确认、确定等按钮
        val confirmTexts = listOf("视频通话", "语音通话", "确定", "确认", "呼叫", "OK", "Confirm")

        for (text in confirmTexts) {
            var nodes: List<AccessibilityNodeInfo>? = null
            try {
                nodes = rootNode.findAccessibilityNodeInfosByText(text)
                if (nodes.isNotEmpty()) {
                    for (node in nodes) {
                        if (node.isClickable) {
                            return node
                        }
                    }
                }
            } finally {
                nodes?.safeRecycleAll()
            }
        }
        return null
    }

    private fun findNodeById(rootNode: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        var nodes: List<AccessibilityNodeInfo>? = null
        try {
            nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            return if (nodes.isNotEmpty()) nodes.first() else null
        } finally {
            nodes?.safeRecycleAll()
        }
    }

    private fun findNodeByText(rootNode: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        return if (nodes.isNotEmpty()) nodes.first() else null
    }

    private fun findNodeByDesc(rootNode: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        return findNodeByDescRecursive(rootNode, desc, 0, 20)
    }

    private fun findNodeByDescRecursive(node: AccessibilityNodeInfo?, desc: String, currentDepth: Int, maxDepth: Int): AccessibilityNodeInfo? {
        if (node == null || currentDepth > maxDepth) return null

        if (node.contentDescription?.toString()?.contains(desc) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findNodeByDescRecursive(node.getChild(i), desc, currentDepth + 1, maxDepth)
            if (result != null) return result
        }
        return null
    }

    private fun findEditableNodes(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        findEditableNodesRecursive(node, result, 0, 20)
    }

    private fun findEditableNodesRecursive(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>, currentDepth: Int, maxDepth: Int) {
        if (node == null || currentDepth > maxDepth) return

        if (node.isEditable) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            findEditableNodesRecursive(node.getChild(i), result, currentDepth + 1, maxDepth)
        }
    }

    private fun findFirstClickableListItem(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return findFirstClickableListItemRecursive(node, 0, 20)
    }

    private fun findFirstClickableListItemRecursive(node: AccessibilityNodeInfo?, currentDepth: Int, maxDepth: Int): AccessibilityNodeInfo? {
        if (node == null || currentDepth > maxDepth) return null

        if (node.isClickable && node.isEnabled) {
            val className = node.className?.toString() ?: ""
            if (!className.contains("Tab") && !className.contains("Bottom")) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val result = findFirstClickableListItemRecursive(node.getChild(i), currentDepth + 1, maxDepth)
            if (result != null) return result
        }
        return null
    }

    private fun findClickableNodeByContent(rootNode: AccessibilityNodeInfo, vararg texts: String): AccessibilityNodeInfo? {
        for (text in texts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) {
                    return node
                }
            }
        }
        return null
    }

    // ==================== 节点操作扩展 ====================

    private fun AccessibilityNodeInfo.click(): Boolean {
        return if (isClickable) {
            performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            parent?.click() == true
        }
    }

    private fun AccessibilityNodeInfo.input(text: String): Boolean {
        return if (isEditable) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            parent?.input(text) == true
        }
    }

    // ==================== 错误处理和重试 ====================

    private fun handleError(backToStep: Int) {
        retryCount++
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.e(TAG, "重试次数达到上限，重置流程")
            resetAndStop()
        } else {
            Log.d(TAG, "回到步骤$backToStep (重试 $retryCount/$MAX_RETRY_COUNT)")
            WeChatData.updateIndex(backToStep)
            scheduleNextStep(500)
        }
    }

    private fun handleRetry(message: String, backToStep: Int) {
        Log.d(TAG, "$message，准备重试")
        handleError(backToStep)
    }

    private fun incrementNavigationAttempts() {
        navigationAttempts++
    }

    private fun resetRetryAndNavigation() {
        retryCount = 0
        navigationAttempts = 0
    }

    private fun resetAndStop() {
        WeChatData.updateIndex(0)
        WeChatData.updateValue("")
        lastWindowClassName = ""
        resetRetryAndNavigation()
        cancelNextStep()
    }

    private fun clearInputField(inputNode: AccessibilityNodeInfo): Boolean {
        return try {
            val text = inputNode.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                Log.d(TAG, "清空输入框: $text")
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                val result = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d(TAG, "清空输入框结果: $result")
                result
            } else {
                Log.d(TAG, "输入框为空，无需清空")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "清空输入框失败", e)
            false
        }
    }

    // ==================== 生命周期 ====================

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
        resetAndStop()
        isProcessing.set(false)
        cancelNextStep()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "无障碍服务已断开")
        resetAndStop()
        isProcessing.set(false)
        cancelNextStep()
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    // ==================== isProcessing辅助函数 ====================

    private fun setProcessing(value: Boolean) {
        isProcessing.set(value)
    }

    // ==================== 主动触发下一步 ====================

    private fun getStepDelay(delayMs: Long): Long = maxOf(delayMs * 2, 1000L)

    private suspend fun waitStep(delayMs: Long) {
        delay(getStepDelay(delayMs))
    }

    private fun scheduleNextStep(delayMs: Long = 300) {
        cancelNextStep()
        nextStepRunnable = Runnable {
            if (!isProcessing.get() && WeChatData.index > 0 && WeChatData.index <= 7) {
                Log.d(TAG, ">>> 主动触发步骤 ${WeChatData.index} <<<")
                val currentActivity = lastWindowClassName
                when (WeChatData.index) {
                    1 -> processStep1(currentActivity)
                    2 -> processStep2(currentActivity)
                    3 -> processStep3(currentActivity)
                    4 -> processStep4(currentActivity)
                    5 -> processStep5(currentActivity)
                    6 -> processStep6(currentActivity)
                    7 -> processStep7(currentActivity)
                }
            }
        }
        mainHandler.postDelayed(nextStepRunnable!!, getStepDelay(delayMs))
    }

    private fun cancelNextStep() {
        nextStepRunnable?.let {
            mainHandler.removeCallbacks(it)
            nextStepRunnable = null
        }
    }

    private fun performClick(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, 100, false)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        dispatchGesture(gesture, null, null)
    }
}
