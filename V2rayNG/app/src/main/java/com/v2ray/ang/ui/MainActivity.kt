package com.v2ray.ang.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AuthManager
import com.v2ray.ang.handler.MigrateManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val requestVpnPermission =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    startV2Ray()
                }
            }
    private val requestSubSettingActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                // COMMENTED OUT: Tab initialization - UI hidden
//                initGroupTab()
            }
    private val tabGroupListener =
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val selectId = tab?.tag.toString()
                    if (selectId != mainViewModel.subscriptionId) {
                        mainViewModel.subscriptionIdChanged(selectId)
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            }
    private var mItemTouchHelper: ItemTouchHelper? = null
    val mainViewModel: MainViewModel by viewModels()

    // register activity result for requesting permission
    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    isGranted: Boolean ->
                if (isGranted) {
                    when (pendingAction) {
                        Action.IMPORT_QR_CODE_CONFIG ->
                                scanQRCodeForConfig.launch(
                                        Intent(this, ScannerActivity::class.java)
                                )
                        Action.READ_CONTENT_FROM_URI ->
                                chooseFileForCustomConfig.launch(
                                        Intent.createChooser(
                                                Intent(Intent.ACTION_GET_CONTENT).apply {
                                                    type = "*/*"
                                                    addCategory(Intent.CATEGORY_OPENABLE)
                                                },
                                                getString(R.string.title_file_chooser)
                                        )
                                )
                        Action.POST_NOTIFICATIONS -> {}
                        else -> {}
                    }
                } else {
                    toast(R.string.toast_permission_denied)
                }
                pendingAction = Action.NONE
            }

    private var pendingAction: Action = Action.NONE

    enum class Action {
        NONE,
        IMPORT_QR_CODE_CONFIG,
        READ_CONTENT_FROM_URI,
        POST_NOTIFICATIONS
    }

    private val chooseFileForCustomConfig =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val uri = it.data?.data
                if (it.resultCode == RESULT_OK && uri != null) {
                    readContentFromUri(uri)
                }
            }

    private val scanQRCodeForConfig =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is logged in, if not redirect to login
        if (!AuthManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(binding.root)
        title = getString(R.string.title_server)
        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                V2RayServiceManager.stopVService(this)
            } else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }

        // Update subscription info display
        updateSubscriptionInfo()

        binding.recyclerView.setHasFixedSize(true)
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)) {
            binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        } else {
            binding.recyclerView.layoutManager = GridLayoutManager(this, 1)
        }
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        val toggle =
                ActionBarDrawerToggle(
                        this,
                        binding.drawerLayout,
                        binding.toolbar,
                        R.string.navigation_drawer_open,
                        R.string.navigation_drawer_close
                )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        // COMMENTED OUT: Subscription tabs initialization - UI hidden
//        initGroupTab()
        setupViewModel()
        migrateLegacy()

        // Auto-fetch subscription data on startup if logged in
        autoFetchSubscription()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                pendingAction = Action.POST_NOTIFICATIONS
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        // COMMENTED OUT: Test state observer - no longer used
        // mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
                binding.fab.setImageResource(R.drawable.ic_stop_24dp)
                binding.fab.backgroundTintList =
                        ColorStateList.valueOf(
                                ContextCompat.getColor(this, R.color.color_fab_active)
                        )
                // Subscription info is always visible (if data exists)
            } else {
                binding.fab.setImageResource(R.drawable.ic_play_24dp)
                binding.fab.backgroundTintList =
                        ColorStateList.valueOf(
                                ContextCompat.getColor(this, R.color.color_fab_inactive)
                        )
            }
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = MigrateManager.migrateServerConfig2Profile()
            launch(Dispatchers.Main) {
                if (result) {
                    toast(getString(R.string.migration_success))
                    mainViewModel.reloadServerList()
                } else {
                    // toast(getString(R.string.migration_fail))
                }
            }
        }
    }

    private fun autoFetchSubscription() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Check if user is logged in
            if (AuthManager.isLoggedIn()) {
                val count = AngConfigManager.autoFetchSubscriptionWithAuth()
                if (count > 0) {
                    Log.i(AppConfig.TAG, "Auto-fetched $count configs from subscription")
                    launch(Dispatchers.Main) { mainViewModel.reloadServerList() }
                }
            }
        }
    }

    // COMMENTED OUT: Subscription tabs initialization - UI hidden
//    private fun initGroupTab() {
//        binding.tabGroup.removeOnTabSelectedListener(tabGroupListener)
//        binding.tabGroup.removeAllTabs()
//        binding.tabGroup.isVisible = false
//
//        val (listId, listRemarks) = mainViewModel.getSubscriptions(this)
//        if (listId == null || listRemarks == null) {
//            return
//        }
//
//        for (it in listRemarks.indices) {
//            val tab = binding.tabGroup.newTab()
//            tab.text = listRemarks[it]
//            tab.tag = listId[it]
//            binding.tabGroup.addTab(tab)
//        }
//        val selectIndex =
//                listId.indexOf(mainViewModel.subscriptionId).takeIf { it >= 0 }
//                        ?: (listId.count() - 1)
//        binding.tabGroup.selectTab(binding.tabGroup.getTabAt(selectIndex))
//        binding.tabGroup.addOnTabSelectedListener(tabGroupListener)
//        binding.tabGroup.isVisible = true
//    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        V2RayServiceManager.startVService(this)
    }

    private fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
        updateSubscriptionInfo()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // DISABLED: Three-dot menu and search completely hidden
        // Users don't need access to these options
        return false
        
        // COMMENTED OUT: Menu inflation and search functionality
//        menuInflater.inflate(R.menu.menu_main, menu)
//
//        val searchItem = menu.findItem(R.id.search_view)
//        if (searchItem != null) {
//            val searchView = searchItem.actionView as SearchView
//            searchView.setOnQueryTextListener(
//                    object : SearchView.OnQueryTextListener {
//                        override fun onQueryTextSubmit(query: String?): Boolean = false
//
//                        override fun onQueryTextChange(newText: String?): Boolean {
//                            mainViewModel.filterConfig(newText.orEmpty())
//                            return false
//                        }
//                    }
//            )
//
//            searchView.setOnCloseListener {
//                mainViewModel.filterConfig("")
//                false
//            }
//        }
//        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            when (item.itemId) {
                // Import menu items removed - users can only add configs via channel login
                // R.id.import_qrcode, import_clipboard, import_local, import_manually_* removed
                // COMMENTED OUT: Export disabled
//                R.id.export_all -> {
//                    exportAll()
//                    true
//                }
                R.id.ping_all -> {
                    toast(
                            getString(
                                    R.string.connection_test_testing_count,
                                    mainViewModel.serversCache.count()
                            )
                    )
                    mainViewModel.testAllTcping()
                    true
                }
                R.id.real_ping_all -> {
                    toast(
                            getString(
                                    R.string.connection_test_testing_count,
                                    mainViewModel.serversCache.count()
                            )
                    )
                    mainViewModel.testAllRealPing()
                    true
                }
                R.id.intelligent_selection_all -> {
                    if (MmkvManager.decodeSettingsString(
                                    AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD,
                                    "1"
                            ) != "0"
                    ) {
                        toast(getString(R.string.pre_resolving_domain))
                    }
                    mainViewModel.createIntelligentSelectionAll()
                    true
                }
                R.id.service_restart -> {
                    restartV2Ray()
                    true
                }
                // COMMENTED OUT: Delete operations disabled
//                R.id.del_all_config -> {
//                    delAllConfig()
//                    true
//                }
//                R.id.del_duplicate_config -> {
//                    delDuplicateConfig()
//                    true
//                }
//                R.id.del_invalid_config -> {
//                    delInvalidConfig()
//                    true
//                }
                R.id.sort_by_test_results -> {
                    sortByTestResults()
                    true
                }
                R.id.sub_update -> {
                    importConfigViaSub()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }

    private fun importManually(createConfigType: Int) {
        startActivity(
                Intent().putExtra("createConfigType", createConfigType)
                        .putExtra("subscriptionId", mainViewModel.subscriptionId)
                        .setClass(this, ServerActivity::class.java)
        )
    }

    /** import config from qrcode */
    private fun importQRcode(): Boolean {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
        } else {
            pendingAction = Action.IMPORT_QR_CODE_CONFIG
            requestPermissionLauncher.launch(permission)
        }
        return true
    }

    /** import config from clipboard */
    private fun importClipboard(): Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?) {
        binding.pbWaiting.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) =
                        AngConfigManager.importBatchConfig(
                                server,
                                mainViewModel.subscriptionId,
                                true
                        )
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                        }
                        countSub > 0 -> {
                            // COMMENTED OUT: Tab initialization - UI hidden
//                            initGroupTab()
                        }
                        else -> toastError(R.string.toast_failure)
                    }
                    binding.pbWaiting.hide()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    binding.pbWaiting.hide()
                }
                Log.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    /** import config from local config file */
    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }

    /** import config from sub */
    private fun importConfigViaSub(): Boolean {
        binding.pbWaiting.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val count = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (count > 0) {
                    toast(getString(R.string.title_update_config_count, count))
                    mainViewModel.reloadServerList()
                } else {
                    toastError(R.string.toast_failure)
                }
                binding.pbWaiting.hide()
            }
        }
        return true
    }

    // COMMENTED OUT: Export functionality disabled
//    private fun exportAll() {
//        binding.pbWaiting.show()
//        lifecycleScope.launch(Dispatchers.IO) {
//            val ret = mainViewModel.exportAllServer()
//            launch(Dispatchers.Main) {
//                if (ret > 0) toast(getString(R.string.title_export_config_count, ret))
//                else toastError(R.string.toast_failure)
//                binding.pbWaiting.hide()
//            }
//        }
//    }

    // COMMENTED OUT: Delete all configs disabled
//    private fun delAllConfig() {
//        AlertDialog.Builder(this)
//                .setMessage(R.string.del_config_comfirm)
//                .setPositiveButton(android.R.string.ok) { _, _ ->
//                    binding.pbWaiting.show()
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        val ret = mainViewModel.removeAllServer()
//                        launch(Dispatchers.Main) {
//                            mainViewModel.reloadServerList()
//                            toast(getString(R.string.title_del_config_count, ret))
//                            binding.pbWaiting.hide()
//                        }
//                    }
//                }
//                .setNegativeButton(android.R.string.cancel) { _, _ ->
//                    // do noting
//                }
//                .show()
//    }

    // COMMENTED OUT: Delete duplicate configs disabled
//    private fun delDuplicateConfig() {
//        AlertDialog.Builder(this)
//                .setMessage(R.string.del_config_comfirm)
//                .setPositiveButton(android.R.string.ok) { _, _ ->
//                    binding.pbWaiting.show()
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        val ret = mainViewModel.removeDuplicateServer()
//                        launch(Dispatchers.Main) {
//                            mainViewModel.reloadServerList()
//                            toast(getString(R.string.title_del_duplicate_config_count, ret))
//                            binding.pbWaiting.hide()
//                        }
//                    }
//                }
//                .setNegativeButton(android.R.string.cancel) { _, _ ->
//                    // do noting
//                }
//                .show()
//    }

    // COMMENTED OUT: Delete invalid configs disabled
//    private fun delInvalidConfig() {
//        AlertDialog.Builder(this)
//                .setMessage(R.string.del_invalid_config_comfirm)
//                .setPositiveButton(android.R.string.ok) { _, _ ->
//                    binding.pbWaiting.show()
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        val ret = mainViewModel.removeInvalidServer()
//                        launch(Dispatchers.Main) {
//                            mainViewModel.reloadServerList()
//                            toast(getString(R.string.title_del_config_count, ret))
//                            binding.pbWaiting.hide()
//                        }
//                    }
//                }
//                .setNegativeButton(android.R.string.cancel) { _, _ ->
//                    // do noting
//                }
//                .show()
//    }

    private fun sortByTestResults() {
        binding.pbWaiting.show()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                binding.pbWaiting.hide()
            }
        }
    }

    /** show file chooser */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            pendingAction = Action.READ_CONTENT_FROM_URI
            chooseFileForCustomConfig.launch(
                    Intent.createChooser(intent, getString(R.string.title_file_chooser))
            )
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    /** read content from uri */
    private fun readContentFromUri(uri: Uri) {
        val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                contentResolver.openInputStream(uri).use { input ->
                    importBatchConfig(input?.bufferedReader()?.readText())
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to read content from URI", e)
            }
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    // COMMENTED OUT: Test state function - no longer used
//    private fun setTestState(content: String?) {
//        binding.tvTestState.text = content
//    }

    //    val mConnection = object : ServiceConnection {
    //        override fun onServiceDisconnected(name: ComponentName?) {
    //        }
    //
    //        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    //            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
    //        }
    //    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            // COMMENTED OUT: Hidden navigation items
//            R.id.per_app_proxy_settings ->
//                    startActivity(Intent(this, PerAppProxyActivity::class.java))
//            R.id.routing_setting ->
//                    requestSubSettingActivity.launch(
//                            Intent(this, RoutingSettingActivity::class.java)
//                    )
//            R.id.user_asset_setting -> startActivity(Intent(this, UserAssetActivity::class.java))
            R.id.settings ->
                    startActivity(
                            Intent(this, SettingsActivity::class.java)
                                    .putExtra("isRunning", mainViewModel.isRunning.value == true)
                    )
//            R.id.promotion ->
//                    Utils.openUri(
//                            this,
//                            "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}"
//                    )
//            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
//            R.id.check_for_update -> startActivity(Intent(this, CheckUpdateActivity::class.java))
//            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
            R.id.logout -> {
                // Confirm logout
                AlertDialog.Builder(this)
                        .setTitle(R.string.menu_logout)
                        .setMessage("Are you sure you want to logout?")
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            // Stop VPN if running
                            if (mainViewModel.isRunning.value == true) {
                                V2RayServiceManager.stopVService(this)
                            }
                            // Clear credentials
                            AuthManager.logout()
                            // Redirect to login
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
            }
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * Updates the subscription info display with traffic and expiry data
     * Parses data from config remarks in Persian format
     */
    private fun updateSubscriptionInfo() {
        try {
            // Search through all configs for subscription info
            var remainingDays: Int? = null
            var remainingGB: Double? = null
            var totalGB: Double? = null

            for (config in mainViewModel.serversCache) {
                val remarks = config.profile.remarks ?: ""
                
                // Parse Persian format: "روز های باقی مانده: XXXX"
                val daysPattern = """روز های باقی مانده:\s*(\d+)""".toRegex()
                val daysMatch = daysPattern.find(remarks)
                if (daysMatch != null) {
                    remainingDays = daysMatch.groupValues[1].toIntOrNull()
                }

                // Parse Persian format: "حجم باقی مانده: XXX.XXX گیگابایت"
                val volumePattern = """حجم باقی مانده:\s*([\d.]+)\s*گیگابایت""".toRegex()
                val volumeMatch = volumePattern.find(remarks)
                if (volumeMatch != null) {
                    remainingGB = volumeMatch.groupValues[1].toDoubleOrNull()
                }

                // Also try to find total volume if mentioned
                val totalPattern = """کل حجم:\s*([\d.]+)\s*گیگابایت""".toRegex()
                val totalMatch = totalPattern.find(remarks)
                if (totalMatch != null) {
                    totalGB = totalMatch.groupValues[1].toDoubleOrNull()
                }

                // If we found data, break
                if (remainingDays != null || remainingGB != null) {
                    break
                }
            }

            // If no data found, hide the info panel
            if (remainingDays == null && remainingGB == null) {
                binding.layoutSubscriptionInfo.visibility = android.view.View.GONE
                return
            }

            binding.layoutSubscriptionInfo.visibility = android.view.View.VISIBLE

            // Display traffic info
            if (remainingGB != null) {
                if (totalGB != null && totalGB > 0) {
                    val usedGB = totalGB - remainingGB
                    val percentage = ((usedGB / totalGB) * 100).toInt().coerceIn(0, 100)
                    
                    binding.tvSubscriptionTraffic.text = String.format(
                        "Volume: %.2f GB / %.2f GB (%.1f%% used)",
                        usedGB, totalGB, (usedGB / totalGB) * 100
                    )
                    binding.pbSubscriptionUsage.progress = percentage
                } else {
                    // Only remaining volume is known
                    binding.tvSubscriptionTraffic.text = String.format(
                        "Remaining: %.2f GB",
                        remainingGB
                    )
                    binding.pbSubscriptionUsage.progress = 0
                }
            } else {
                binding.tvSubscriptionTraffic.text = "Volume: No data"
                binding.pbSubscriptionUsage.progress = 0
            }

            // Display expiry info
            if (remainingDays != null) {
                binding.tvSubscriptionExpire.text = "Expires in: $remainingDays days"
            } else {
                binding.tvSubscriptionExpire.text = "Expiry: Unknown"
            }

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to update subscription info", e)
            binding.layoutSubscriptionInfo.visibility = android.view.View.GONE
        }
    }
}
