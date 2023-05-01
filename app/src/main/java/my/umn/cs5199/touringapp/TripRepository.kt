package my.umn.cs5199.touringapp

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TripRepository {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val regex = Regex("[^a-zA-Z0-9]")
    private val dateFormat = SimpleDateFormat("yyyy_MM_dd_HH_mm")

    object Const {
        const val FILE_EXT = ".plan"
    }

    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun saveToStorage(context: Context, tripPlan: TripPlan) : String {
        return withContext(Dispatchers.IO) {
            val adapter = moshi.adapter<TripPlan>()
            val string = adapter.toJson(tripPlan)
            val fileName = tripPlan.name.replace(regex, "_") +
                    (if (tripPlan.timeStart != 0L) dateFormat.format(Date(tripPlan.timeStart)) else "") +
                    Const.FILE_EXT
            val file = File(context.filesDir, fileName)
            file.writeText(string)
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

    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun loadFromStorage(context: Context) : List<TripPlan> {
        return withContext(Dispatchers.IO) {
            val adapter = moshi.adapter<TripPlan>()
            val files: Array<String> = context.fileList()
            val tripPlans = files.filter { it.endsWith(Const.FILE_EXT) }
                .mapNotNull { adapter.fromJson(File(context.filesDir, it).readText()) }.toList()
            tripPlans
        }
    }
}