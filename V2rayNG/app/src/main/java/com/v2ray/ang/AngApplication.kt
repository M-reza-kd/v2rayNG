package com.v2ray.ang

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AngApplication : MultiDexApplication(), Configuration.Provider {
    companion object {
        lateinit var application: AngApplication
    }

    private val applicationScope = CoroutineScope(SupervisorJob())

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setDefaultProcessName("${ANG_PACKAGE}:bg")
            .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        SettingsManager.setNightMode()

        applicationScope.launch(Dispatchers.IO) {
            SettingsManager.initRoutingRulesets(this@AngApplication)
            SettingsManager.initAssets(this@AngApplication, assets)
        }

        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 200)
            .apply()
    }
}
