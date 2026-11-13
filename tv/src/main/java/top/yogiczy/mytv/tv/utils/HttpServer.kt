package top.yogiczy.mytv.tv.utils

import android.content.Context
import android.content.Intent
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.body.MultipartFormDataBody
import com.koushikdutta.async.http.body.StringBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.ApkInstaller
import top.yogiczy.mytv.core.util.utils.FsUtil
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.HttpServerService
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.tv.sync.CloudSync
import top.yogiczy.mytv.tv.sync.CloudSyncData
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException


object HttpServer : Loggable("HttpServer") {
    private const val SERVER_PORT = 10591
    private val uploadedApkFile by lazy {
        File(Globals.cacheDir, "uploaded_apk.apk").apply { deleteOnExit() }
    }

    // 服务地址每次现算：开机自启时服务先起来、Wi-Fi 还没拿到地址，或中途
    // 换网换 IP 时，lazy 缓存会把第一次算出的地址（可能是 127.0.0.1）定格
    // 整个进程；推送页每秒刷新一次，地址变了显示就跟着变
    val serverUrl get() = "http://${getLocalIpAddress()}:${SERVER_PORT}"

    fun start(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val server = AsyncHttpServer()

                server.addAction("OPTIONS", ".+") { _, response ->
                    wrapResponse(response)
                    response.send("ok")
                }

                server.listen(AsyncServer.getDefault(), SERVER_PORT)

                server.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.web_push)
                }
                server.get("/web_push_css.css") { _, response ->
                    handleRawResource(response, context, "text/css", R.raw.web_push_css)
                }
                server.get("/web_push_js.js") { _, response ->
                    handleRawResource(response, context, "text/javascript", R.raw.web_push_js)
                }

                server.get("/advance") { _, response ->
                    handleAssets(response, context, "text/html", "remote-configs/index.html")
                }

                server.get("/remote-configs/(.*)") { request, response ->
                    val contentType = when (request.path.split(".").last()) {
                        "css" -> "text/css"
                        "js" -> "text/javascript"
                        "html" -> "text/html"
                        "json" -> "application/json"
                        "svg" -> "image/svg+xml"
                        "png" -> "image/png"
                        else -> "text/plain"
                    }

                    handleAssets(response, context, contentType, request.path.removePrefix("/"))
                }

                server.get("/api/info") { _, response ->
                    handleGetInfo(response)
                }

                server.post("/api/iptv-source/push") { request, response ->
                    handleIptvSourcePush(request, response)
                }

                server.post("/api/epg-source/push") { request, response ->
                    handleEpgSourcePush(request, response)
                }

                server.get("/api/channel-alias") { _, response ->
                    handleGetChannelAlias(response)
                }

                server.post("/api/channel-alias") { request, response ->
                    handleUpdateChannelAlias(request, response)
                }

                server.get("/api/configs") { _, response ->
                    handleConfigsGet(response)
                }

                server.post("/api/configs") { request, response ->
                    handleConfigsPush(request, response)
                }

                server.post("/api/upload/apk") { request, response ->
                    handleUploadApk(request, response, context)
                }

                server.get("/api/cloud-sync/data") { _, response ->
                    handleCloudSyncDataGet(response)
                }

                server.post("/api/cloud-sync/data") { request, response ->
                    handleCloudSyncDataPost(request, response)
                }

                server.get("/api/about") { _, response ->
                    handleAboutGet(response)
                }

                server.get("/api/logs") { _, response ->
                    handleLogsGet(response)
                }

                server.get("/api/file/content") { request, response ->
                    handleFileContentGet(request, response)
                }

                server.post("/api/file/content") { request, response ->
                    handleFileContentPost(request, response)
                }

                server.post("/api/file/content-with-dir") { request, response ->
                    handleFileContentWithDirPost(request, response)
                }

                // server.post("/api/upload/allinone") { request, response ->
                //     handleUploadAllInOne(request, response)
                // }

                log.i("设置服务已启动: $serverUrl")
            } catch (ex: Exception) {
                log.e("设置服务启动失败: ${ex.message}", ex)
                launch(Dispatchers.Main) {
                    Snackbar.show("设置服务启动失败", type = SnackbarType.ERROR)
                }
            }
        }
    }

    private fun wrapResponse(response: AsyncHttpServerResponse) = response.apply {
        headers.set("Access-Control-Allow-Methods", "POST, GET, DELETE, PUT, OPTIONS")
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set("Access-Control-Allow-Headers", "Origin, Content-Type, X-Auth-Token")
    }

    private fun responseSuccess(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send("{\"code\": 0}")
        }
    }

    private fun responseError(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send("{\"code\": 1}")
        }
    }

    private fun handleRawResource(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        id: Int,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            // 读完就关：readBytes() 不关流，每次打开网页推送页都漏一个
            // 文件描述符；同仓 WebViewScreen 同款调用已是 .use 写法
            send(context.resources.openRawResource(id).use { it.readBytes().decodeToString() })
        }
    }

    private fun handleAssets(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        filename: String,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            // 读完就关：readText() 不关 reader，每次打开远程配置页都漏一个
            // 文件描述符；同仓 WebViewScreen 同款调用已是 .use 写法
            send(context.assets.open(filename).reader().use { it.readText() })
        }
    }

    private fun handleGetInfo(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Globals.json.encodeToString(
                    AppInfo(
                        appTitle = Constants.APP_TITLE,
                        appRepo = Constants.APP_REPO,
                        logHistory = Logger.history,
                    )
                )
            )
        }
    }

    private fun handleIptvSourcePush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val name = body.get("name").toString()
        val type = body.get("type").toString()
        val url = body.get("url").toString()
        val filePath = body.get("filePath").toString()
        val content = body.get("content").toString()

        var newIptvSource: IptvSource? = null

        when (type) {
            "url" -> {
                newIptvSource = IptvSource(name, url)
            }

            "file" -> {
                newIptvSource = IptvSource(name, filePath, true)
            }

            "content" -> {
                val file =
                    File(Globals.fileDir, "iptv_source_local_${System.currentTimeMillis()}.txt")
                FsUtil.writeTextAtomically(file, content)
                newIptvSource = IptvSource(name, file.path, true)
            }
        }

        newIptvSource?.let {
            Configs.iptvSourceList = IptvSourceList(Configs.iptvSourceList + it)
            Configs.iptvSourceCurrent = it
        }

        responseSuccess(response)
    }

    private fun handleEpgSourcePush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val name = body.get("name").toString()
        val url = body.get("url").toString()

        EpgSource(name, url).let {
            Configs.epgSourceList = EpgSourceList(Configs.epgSourceList + it)
            Configs.epgSourceCurrent = it
        }

        responseSuccess(response)
    }

    private fun handleGetChannelAlias(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            send(runCatching { ChannelAlias.aliasFile.readText() }.getOrElse { "" })
        }
    }

    private fun handleUpdateChannelAlias(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val alias = request.getBody<StringBody>().get()

        // 别名文件也走原子写：写一半被杀不留截断文件（云同步同款写法）
        FsUtil.writeTextAtomically(ChannelAlias.aliasFile, alias)
        runBlocking {
            IptvRepository.clearAllCache()
            EpgRepository.clearAllCache()
        }

        responseSuccess(response)
    }

    private fun handleConfigsGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(Globals.json.encodeToString(Configs.toPartial()))
        }
    }

    private fun handleConfigsPush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val configs = Globals.json.decodeFromString<Configs.Partial>(body.toString())
        Configs.fromPartial(configs)

        responseSuccess(response)
    }

    private fun handleUploadApk(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
        context: Context,
    ) {
        // 非 multipart 请求时 getBody 会返回 null 或抛类型转换异常，直接拒绝
        val body = runCatching { request.getBody<MultipartFormDataBody>() }.getOrNull()
        if (body == null) {
            log.e("APK 上传失败: 请求不是 multipart/form-data")
            responseError(response)
            return
        }

        val contentLength = (request.headers["Content-Length"]?.toLongOrNull() ?: 1)
            .coerceAtLeast(1)
        val os = runCatching { uploadedApkFile.outputStream() }.getOrElse {
            log.e("APK 上传失败: 无法创建上传文件: ${it.message}", it)
            responseError(response)
            return
        }
        var hasReceived = 0L
        var receivedFile = false
        var writeFailed = false

        body.setMultipartCallback { part ->
            if (part.isFile) {
                receivedFile = true
                body.setDataCallback { _, bb ->
                    if (writeFailed) return@setDataCallback
                    try {
                        val byteArray = bb.allByteArray
                        hasReceived += byteArray.size
                        Snackbar.show(
                            "正在接收文件: ${(hasReceived * 100f / contentLength).toInt()}%",
                            leadingLoading = true,
                            id = "uploading_apk",
                        )
                        os.write(byteArray)
                    } catch (ex: Exception) {
                        // 磁盘满等写入失败：记下失败并中断上传，不再继续写
                        writeFailed = true
                        log.e("APK 上传写入失败: ${ex.message}", ex)
                        runCatching { os.close() }
                        body.dataEmitter.close()
                    }
                }
            }
        }

        body.setEndCallback { ex ->
            // AndroidAsync 的 report 会把流异常（客户端中断、multipart 损坏等）
            // 传进 end 回调：原先直接忽略 ex 就去安装，截断/损坏的 APK 也会被安装
            runCatching { os.flush() }
            runCatching { os.close() }
            body.dataEmitter.close()

            val error = ex ?: if (writeFailed) Exception("文件写入失败") else null
            if (error != null || !receivedFile || hasReceived <= 0) {
                runCatching { uploadedApkFile.delete() }
                log.e("APK 上传失败: ${error?.message ?: "未收到文件"}", error)
                Snackbar.show("文件接收失败", type = SnackbarType.ERROR)
                // 失败响应只在上传真正结束后才发：原先 handler 一返回就先发成功，
                // 网页端即使随后上传失败也看到成功
                responseError(response)
                return@setEndCallback
            }

            Snackbar.show("正在准备安装，请稍后...", leadingLoading = true, duration = 10_000)
            responseSuccess(response)
            ApkInstaller.installApk(context, uploadedApkFile.path)
        }
    }

    // private fun handleUploadAllInOne(
    //     request: AsyncHttpServerRequest,
    //     response: AsyncHttpServerResponse,
    // ) {
    //     val body = request.getBody<MultipartFormDataBody>()

    //     val contentLength = request.headers["Content-Length"]?.toLong() ?: 1
    //     var hasReceived = 0L

    //     val allinoneFile = File(Globals.fileDir, "uploads/allinone").apply { parentFile?.mkdirs() }

    //     body.setMultipartCallback { part ->
    //         if (part.isFile) {
    //             with(allinoneFile.outputStream()) {

    //                 body.setDataCallback { _, bb ->
    //                     val byteArray = bb.allByteArray
    //                     hasReceived += byteArray.size
    //                     Snackbar.show(
    //                         "正在接收文件: ${(hasReceived * 100f / contentLength).toInt()}%",
    //                         leadingLoading = true,
    //                         id = "uploading_file",
    //                     )
    //                     write(byteArray)
    //                 }
    //             }
    //         }
    //     }

    //     body.setEndCallback {
    //         body.dataEmitter.close()
    //         Configs.feiyangAllInOneFilePath = allinoneFile.absolutePath
    //         Snackbar.show("文件接收完成", id = "uploading_file")
    //     }

    //     responseSuccess(response)
    // }

    private fun handleCloudSyncDataGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(Globals.json.encodeToString(runBlocking { CloudSync.getData() }))
        }
    }

    private fun handleCloudSyncDataPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val data = Globals.json.decodeFromString<CloudSyncData>(body.toString())
        runBlocking { data.apply() }

        responseSuccess(response)
    }

    private fun handleAboutGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Globals.json.encodeToString(
                    AppAbout(
                        applicationId = BuildConfig.APPLICATION_ID,
                        flavor = BuildConfig.FLAVOR,
                        buildType = BuildConfig.BUILD_TYPE,
                        versionCode = BuildConfig.VERSION_CODE,
                        versionName = BuildConfig.VERSION_NAME,
                        deviceName = Globals.deviceName,
                        deviceId = Globals.deviceId,
                    )
                )
            )
        }
    }

    private fun handleLogsGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(Globals.json.encodeToString(Logger.history))
        }
    }

    // 局域网文件接口只允许读写应用自己的 cache/file 目录：/api/file/content 原先接受
    // 任意绝对路径，同网段任何人都能读应用私有文件（配置、订阅源）或往任意位置写文件；
    // 与 content-with-dir 接口保持一致，只放行这两个目录内的文件（规范化后比较，.. 与
    // 符号链接越出目录会被拒绝）。网页端编辑本地订阅源走的正是这两个目录（content 类型
    // 的源文件建在 fileDir 下），不受影响；file 类型源若指向目录外，网页端编辑会被拒绝，
    // 但订阅本身照常能播。
    private fun isAllowedFile(file: File): Boolean {
        return FsUtil.isWithin(file, Globals.fileDir) || FsUtil.isWithin(file, Globals.cacheDir)
    }

    private fun handleFileContentGet(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        val path = request.query.getString("path")

        val file = File(path)
        if (!isAllowedFile(file)) {
            return response.code(400).send("Invalid path")
        }

        val targetFile = file.takeIf { it.isFile } ?: run {
            return response.code(404).send("File not found")
        }

        // 流式发送，不再整文件 readText() 进内存：请求大文件时会把应用内存撑爆
        wrapResponse(response).sendFile(targetFile)
    }

    private fun handleFileContentPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val path = body.get("path").toString()
        val content = body.get("content").toString()

        val file = File(path)
        if (!isAllowedFile(file)) {
            return response.code(400).send("Invalid path")
        }

        if (file.exists()) {
            FsUtil.writeTextAtomically(file, content)
        } else {
            file.parentFile?.mkdirs()
            FsUtil.writeTextAtomically(file, content)
        }

        responseSuccess(response)
    }

    private fun handleFileContentWithDirPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val dir = body.get("dir").toString()
        val filename = body.get("filename").toString()
        val content = body.get("content").toString()

        val baseDir = when (dir) {
            "cache" -> Globals.cacheDir
            "file" -> Globals.fileDir
            else -> return response.code(400).send("Invalid dir")
        }

        // 文件名含 .. 或绝对路径时可能越出 cache/file 目录，拒绝写入
        val file = FsUtil.resolveWithin(baseDir, filename)
            ?: return response.code(400).send("Invalid filename")

        if (file.exists()) {
            FsUtil.writeTextAtomically(file, content)
        } else {
            file.parentFile?.mkdirs()
            FsUtil.writeTextAtomically(file, content)
        }

        wrapResponse(response).send(file.path)
    }

    private fun getLocalIpAddress(): String {
        val defaultIp = "127.0.0.1"

        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        val ip = inetAddress.hostAddress ?: defaultIp
                        val isValid = when {
                            ip.startsWith("192.168.") -> true
                            ip.startsWith("10.") -> true
                            ip.startsWith("172.") -> {
                                val parts = ip.split(".")
                                val secondOctet = parts.getOrNull(1)?.toIntOrNull()
                                secondOctet in 16..31
                            }

                            else -> false
                        }

                        if (isValid)
                            return ip
                    }
                }
            }
            return defaultIp
        } catch (ex: SocketException) {
            log.e("IP Address: ${ex.message}", ex)
            return defaultIp
        }
    }

    fun startService(context: Context) {
        runCatching {
            context.startService(Intent(context, HttpServerService::class.java))
        }.onFailure { it.printStackTrace() }
    }

    fun stopService(context: Context) {
        runCatching {
            context.stopService(Intent(context, HttpServerService::class.java))
        }.onFailure { it.printStackTrace() }
    }
}

@Serializable
private data class AppInfo(
    val appTitle: String,
    val appRepo: String,
    val logHistory: List<Logger.HistoryItem>,
)

@Serializable
private data class AppAbout(
    val applicationId: String,
    val flavor: String,
    val buildType: String,
    val versionCode: Int,
    val versionName: String,
    val deviceName: String,
    val deviceId: String,
)