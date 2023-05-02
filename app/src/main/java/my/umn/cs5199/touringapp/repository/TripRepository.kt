package my.umn.cs5199.touringapp.repository

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.umn.cs5199.touringapp.Constants
import my.umn.cs5199.touringapp.TripPlan
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TripRepository {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun saveToStorage(context: Context, tripPlan: TripPlan) : String {
        return withContext(Dispatchers.IO) {
            val adapter = moshi.adapter<TripPlan>()
            val string = adapter.toJson(tripPlan)
            val fileName = toFileName(tripPlan)
            val file = File(context.filesDir, fileName)
            file.writeText(string)
            Log.d("touringApp.saveToSorage", "saved to file: " + fileName)
            fileName
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun loadFromStorage(context: Context, fileName : String) : TripPlan {
        return withContext(Dispatchers.IO) {
            val adapter = moshi.adapter<TripPlan>()
            val tripPlan = adapter.fromJson(File(context.filesDir, fileName).readText())
            tripPlan!!
        }
    }

    companion object {
        private val regex = Regex("[^a-zA-Z0-9]")
        private val dateFormat = SimpleDateFormat("yyyy_MM_dd_HH_mm")

        fun toFileName(tripPlan: TripPlan): String {
            val fileName = tripPlan.name.replace(regex, "_") +
                    (if (tripPlan.timeStart != 0L) dateFormat.format(Date(tripPlan.timeStart)) else "") +
                    Constants.FILE_EXT
            return fileName
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun loadFromStorage(context: Context) : List<TripPlan> {
        return withContext(Dispatchers.IO) {
            val adapter = moshi.adapter<TripPlan>()
            val files: Array<String> = context.fileList()
            var tripPlans : List<TripPlan>? = null
            if (context.filesDir.exists()) {
                tripPlans = files.filter { it.endsWith(Constants.FILE_EXT) }
                    .mapNotNull { adapter.fromJson(File(context.filesDir, it).readText()) }.toList()
            } else {
                Log.w("touringApp.loadFromStorage", "Dir " + context.filesDir + " doesn't exist")
                tripPlans = listOf()
            }
            tripPlans
        }
    }

    suspend fun deleteFromStorage(context : Context, fileName : String) {
        return withContext(Dispatchers.IO) {
            context.deleteFile(fileName)
        }
    }
}