package top.yogiczy.mytv.core.data.network

/**
 * 服务端明确返回的 HTTP 状态错误（如 404/500）。
 * 与网络故障（连接失败、超时、DNS）不同：问题在服务端或地址本身，
 * 调用方不要把它包成“请检查网络连接”。
 */
class HttpStatusException(
    val code: Int,
    message: String,
) : Exception("$code: $message")
