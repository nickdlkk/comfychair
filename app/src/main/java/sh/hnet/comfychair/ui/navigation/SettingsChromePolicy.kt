package sh.hnet.comfychair.ui.navigation

/** Standalone gallery is not a Settings tab and owns its own system/IME insets. */
object SettingsChromePolicy {
    fun showBottomBar(route: String?): Boolean = route != "server_gallery"
}
