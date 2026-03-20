package expo.modules.dynamicappicon

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import expo.modules.core.interfaces.ReactActivityLifecycleListener
//import android.widget.Toast
import android.os.Handler



object SharedObject {
    var packageName: String = ""
    var classesToKill = ArrayList<String>()
    var icon: String = ""
    var pm: PackageManager? = null
    var shouldChangeIcon: Boolean = false
    var isInBackground: Boolean = true
}
// For Support Contact: bashahowin@gmail.com

// Used Toast for easy Debugging purpose
class ExpoDynamicAppIconReactActivityLifecycleListener : ReactActivityLifecycleListener {

    private var currentActivity: Activity? = null
    private var isBackground = false
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundCheckRunnable = Runnable {
        if (isBackground) {
            onBackground()
        }
    }

     override fun onPause(activity: Activity) {
        currentActivity = activity
        isBackground = true
        handler.postDelayed(backgroundCheckRunnable, if (SharedObject.isInBackground) 5000 else 0)
    }

    override fun onResume(activity: Activity) {
        currentActivity = activity
        isBackground = false
        handler.removeCallbacks(backgroundCheckRunnable)
        //Toast.makeText(activity, "App is in Foreground", Toast.LENGTH_SHORT).show()
    }


    override fun onDestroy(activity: Activity) {
        handler.removeCallbacks(backgroundCheckRunnable)
        //Toast.makeText(activity, "OnDestroy Triggered,shouldChangeIcon: ${SharedObject.shouldChangeIcon}", Toast.LENGTH_LONG).show()
        if(SharedObject.shouldChangeIcon){
            //Toast.makeText(activity, "OnDestroy Triggered and icon will be changed", Toast.LENGTH_LONG).show()
             applyIconChange(activity)
        }
        if (currentActivity === activity) {
            currentActivity = null
        }
       
    }
    private fun onBackground() {
        currentActivity?.let { activity ->
            //Toast.makeText(activity, "App is in Background (onStop-like)", Toast.LENGTH_SHORT).show()
            
            if (SharedObject.shouldChangeIcon) {
                applyIconChange(activity)
            }
        }
    }

private fun applyIconChange(activity: Activity) {
    SharedObject.icon.takeIf { it.isNotEmpty() }?.let { icon ->
        val pm = SharedObject.pm ?: return
        val newComponent = ComponentName(SharedObject.packageName, icon)

        if (!doesComponentExist(newComponent)) {
            SharedObject.shouldChangeIcon = false
            return
        }

        try {
            val iconComponents = getManagedIconComponents(pm)

            iconComponents.forEach { componentName ->
                val state = pm.getComponentEnabledSetting(componentName)

                if (componentName.className != icon && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Log.i("IconChange", "Disabled component: ${componentName.className}")
                }
            }

            // Enable the new icon
            pm.setComponentEnabledSetting(
                newComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i("IconChange", "Enabled new icon: $icon")

        } catch (e: Exception) {
            Log.e("IconChange", "Error during icon change", e)
        } finally {
            SharedObject.shouldChangeIcon = false
        }

        // Ensure at least one component is enabled
        ensureAtLeastOneComponentEnabled(activity)
    }
}



private fun ensureAtLeastOneComponentEnabled(context: Context) {
    val pm = SharedObject.pm ?: return
    val iconComponents = getManagedIconComponents(pm)

    val hasEnabledComponent = iconComponents.any { componentName ->
        pm.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    if (!hasEnabledComponent) {
        val mainActivityName = "${SharedObject.packageName}.MainActivity"
        val mainComponent = ComponentName(SharedObject.packageName, mainActivityName)
        try {
            pm.setComponentEnabledSetting(
                mainComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i("IconChange", "No active component found. Re-enabling $mainActivityName")
        } catch (e: Exception) {
            Log.e("IconChange", "Error enabling fallback MainActivity", e)
        }
    }
}

    private fun getManagedIconComponents(pm: PackageManager): List<ComponentName> {
    val packageInfo = pm.getPackageInfo(
        SharedObject.packageName,
        PackageManager.GET_ACTIVITIES or PackageManager.GET_DISABLED_COMPONENTS
    )
    val mainActivityPrefix = "${SharedObject.packageName}.MainActivity"

    return packageInfo.activities
        ?.map { it.name }
        ?.filter { it == mainActivityPrefix || it.startsWith(mainActivityPrefix) }
        ?.distinct()
        ?.map { ComponentName(SharedObject.packageName, it) }
        ?: emptyList()
}

    /**
     * Check if a component exists in the manifest (including disabled ones).
     */
    private fun doesComponentExist(componentName: ComponentName): Boolean {
    return try {
        val packageInfo = SharedObject.pm?.getPackageInfo(
            SharedObject.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_DISABLED_COMPONENTS
        )

        val activityExists = packageInfo?.activities?.any { it.name == componentName.className } == true

        activityExists
    } catch (e: Exception) {

        false
    }
}


}
