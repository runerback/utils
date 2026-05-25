package com.runerback.tagem

import android.app.Application
import com.runerback.tagem.data.TagDatabase

class TagEmApplication : Application() {
    val database by lazy { TagDatabase.getInstance(this) }
}
