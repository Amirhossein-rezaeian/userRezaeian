package tech.ula.utils

import android.content.Context
import android.content.ContentResolver
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.system.Os
import android.util.DisplayMetrics
import android.view.WindowManager
import tech.ula.R
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class Symlinker {
    fun createSymlink(targetPath: String, linkPath: String) {
        Os.symlink(targetPath, linkPath)
    }
}

class StorageUtility(private val statFs: StatFs) {
    fun getAvailableStorageInMB(): Long {
        val bytesInMB = 1048576
        val bytesAvailable = statFs.blockSizeLong * statFs.availableBlocksLong
        return bytesAvailable / bytesInMB
    }
}

class BuildWrapper {
    private fun getSupportedAbis(): Array<String> {
        return Build.SUPPORTED_ABIS
    }

    fun getArchType(): String {
        val supportedABIS = this.getSupportedAbis()
                .map {
                    translateABI(it)
                }
                .filter {
                    isSupported(it)
                }
        return if (supportedABIS.size == 1 && supportedABIS[0] == "") {
            val exception = IllegalStateException("No supported ABI!")
            SentryLogger().addExceptionBreadcrumb(exception)
            throw exception
        } else {
            supportedABIS[0]
        }
    }

    private fun isSupported(abi: String): Boolean {
        val supportedABIs = listOf("arm64", "arm", "x86_64", "x86")
        return supportedABIs.contains(abi)
    }

    private fun translateABI(abi: String): String {
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> ""
        }
    }
}

class ConnectionUtility {
    @Throws(Exception::class)
    fun getUrlInputStream(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        return conn.inputStream
    }
}

// TODO move to UlaFiles?
class LocalFileLocator(private val applicationFilesDir: String, private val resources: Resources) {
    fun findIconUri(appName: String): Uri {
        val icon =
                File("$applicationFilesDir/apps/$appName/$appName.png")
        if (icon.exists()) return Uri.fromFile(icon)
        return getDefaultIconUri()
    }

    private fun getDefaultIconUri(): Uri {
        val resId = R.mipmap.ic_launcher_foreground
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + resources.getResourcePackageName(resId) + '/' +
                resources.getResourceTypeName(resId) + '/' +
                resources.getResourceEntryName(resId))
    }

    fun findAppDescription(appName: String): String {
        val appDescriptionFile =
                File("$applicationFilesDir/apps/$appName/$appName.txt")
        if (!appDescriptionFile.exists()) {
            return resources.getString(R.string.error_app_description_not_found)
        }
        return appDescriptionFile.readText()
    }
}

class DeviceDimensions {
    private var height = 720
    private var width = 1480

    fun saveDeviceDimensions(windowManager: WindowManager, displayMetrics: DisplayMetrics, orientation: Int) {
        val navBarSize = getNavigationBarSize(windowManager)
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        height = displayMetrics.heightPixels
        width = displayMetrics.widthPixels
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> if (navBarSize.y > 0) height += navBarSize.y
            Configuration.ORIENTATION_LANDSCAPE -> if (navBarSize.x > 0) width += navBarSize.x
            else -> return
        }
    }

    fun getScreenResolution(): String {
        return when (height > width) {
            true -> "${height}x$width"
            false -> "${width}x$height"
        }
    }

    private fun getNavigationBarSize(windowManager: WindowManager): Point {
        val display = windowManager.defaultDisplay
        val appSize = Point()
        val screenSize = Point()
        display.getSize(appSize)
        display.getRealSize(screenSize)

        return Point(screenSize.x - appSize.x, screenSize.y - appSize.y)
    }
}