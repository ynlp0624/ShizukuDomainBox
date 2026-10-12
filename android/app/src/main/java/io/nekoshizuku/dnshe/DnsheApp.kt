package io.nekoshizuku.dnshe

import android.app.Application

class DnsheApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainerHolder.init(this)
    }
}

/** 把 AppContainer 的初始化包一层，避免 Application 里 import 太乱 */
internal object AppContainerHolder {
    fun init(app: Application) {
        io.nekoshizuku.dnshe.data.AppContainer.init(app)
    }
}
