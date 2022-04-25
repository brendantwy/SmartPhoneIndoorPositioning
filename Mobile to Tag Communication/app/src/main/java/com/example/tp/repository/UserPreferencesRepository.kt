package com.example.tp.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException


/**
 * This data class holds the preference settings that are saved in the DataStore. It is exposed
 * via the Flow interface.
 */
//TODO add at least one constructor parameter and remove this comment block
data class UserPreferences(
    val AnchorCalibrated: Boolean,
    val TagCalibrated: Boolean
)

private const val USER_PREFERENCES_NAME = "user_preferences"

/**
 * Class that handles saving and retrieving user preferences, utilizing Preferences DataStore. This
 * class may be utilized in either the ViewModel or an Activity, depending on what preferences are
 * being saved.
 */
// TODO Create the Preferences DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_NAME,
    produceMigrations = { context ->
        // Since we're migrating from SharedPreferences, add a migration based on the
        // SharedPreferences name
        listOf(SharedPreferencesMigration(context, USER_PREFERENCES_NAME))
    })

class UserPreferencesRepository(context: Context) {
    // TODO Create a private object for defining PreferencesKeys
    private object PreferencesKeys{
        val ANCHOR_CALIBRATED = booleanPreferencesKey("anchor_calibrated")
        val TAG_CALIBRATED = booleanPreferencesKey("tag_calibrated")
        val NUMBER_OF_ANCHORS = intPreferencesKey("number_of_anchors")
        val NUMBER_OF_TAGS = intPreferencesKey("number_of_tags")
    }

    // TODO Add code to get the user preferences flow
    val calibratedFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            val anchorCalibrated = preferences[PreferencesKeys.ANCHOR_CALIBRATED] ?: false
            val tagCalibrated = preferences[PreferencesKeys.TAG_CALIBRATED] ?: false
            UserPreferences(anchorCalibrated, tagCalibrated)
        }

    suspend fun updateCalibrationStatus(context: Context, anchorCalibrated : Boolean, tagCalibrated : Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANCHOR_CALIBRATED] = anchorCalibrated
            preferences[PreferencesKeys.TAG_CALIBRATED] = tagCalibrated
        }
        Log.d("UserPrefs","Anchor calibration:${anchorCalibrated}, Tag calibration:${tagCalibrated}")
    }

    suspend fun readAnchorCalibrationStatus(context: Context): Boolean? {
        val preferences = context.dataStore.data.first()
        Log.d("UserPrefs","Anchor Calibration is ${preferences[PreferencesKeys.ANCHOR_CALIBRATED]}")
        return preferences[PreferencesKeys.ANCHOR_CALIBRATED]
    }

    suspend fun readTagCalibrationStatus(context: Context): Boolean? {
        val preferences = context.dataStore.data.first()
        Log.d("UserPrefs","Tag Calibration is ${preferences[PreferencesKeys.TAG_CALIBRATED]}")
        return preferences[PreferencesKeys.TAG_CALIBRATED]
    }

    suspend fun updateNumberOfAnchors(context: Context, numberOfAnchors: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NUMBER_OF_ANCHORS] = numberOfAnchors
        }
        Log.d("UserPrefs","Logged number of anchors = $numberOfAnchors")
    }

    suspend fun updateNumberOfTags(context: Context, numberOfTags: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NUMBER_OF_TAGS] = numberOfTags
        }
        Log.d("UserPrefs","Logged number of tags = $numberOfTags")
    }

    suspend fun readNumberOfAnchors(context: Context): Int? {
        val preferences = context.dataStore.data.first()
        Log.d("UserPrefs","Number of anchors is ${preferences[PreferencesKeys.NUMBER_OF_ANCHORS]}")
        return preferences[PreferencesKeys.NUMBER_OF_ANCHORS]
    }

    suspend fun readNumberOfTags(context: Context): Int? {
        val preferences = context.dataStore.data.first()
        Log.d("UserPrefs","Number of tags is ${preferences[PreferencesKeys.NUMBER_OF_TAGS]}")
        return preferences[PreferencesKeys.NUMBER_OF_TAGS]
    }



    companion object {
        // Boilerplate-y code for singleton: the private reference to this self
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        /**
         * Boilerplate-y code for singleton: to ensure only a single copy is ever present
         * @param context to init the datastore
         */
        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let {
                    return it
                }

                val instance = UserPreferencesRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}