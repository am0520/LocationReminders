package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import kotlinx.android.synthetic.main.fragment_save_reminder.*
import org.koin.android.ext.android.inject

class SaveReminderFragment : BaseFragment() {
    //Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var settingsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var reminder: ReminderDataItem? = null

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_EVENT
        PendingIntent.getBroadcast(
            requireContext(), 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)

        setDisplayHomeAsUpEnabled(true)

        binding.viewModel = _viewModel

        initLaunchers()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
            //            Navigate to another fragment to get the user location
            _viewModel.navigationCommand.value =
                NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        geofencingClient = LocationServices.getGeofencingClient(requireContext())

        binding.saveReminder.setOnClickListener {
            val title = _viewModel.reminderTitle.value
            val description = _viewModel.reminderDescription.value
            val location = _viewModel.reminderSelectedLocationStr.value
            val latitude = _viewModel.latitude.value
            val longitude = _viewModel.longitude.value

            reminder = ReminderDataItem(title, description, location, latitude, longitude)

            reminder?.let {
                if (_viewModel.validateEnteredData(it))
                    checkLocationPermission()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofenceForReminder() {
        val currentReminder = reminder ?: return

        val geofence = Geofence.Builder()
            .setRequestId(currentReminder.id)
            .setCircularRegion(
                currentReminder.latitude!!, currentReminder.longitude!!, GEOFENCE_RADIUS_IN_METERS
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
            addOnSuccessListener {
                _viewModel.validateAndSaveReminder(currentReminder)
            }
            addOnFailureListener { exception ->
                Toast.makeText(
                    requireContext(), R.string.geofences_not_added,
                    Toast.LENGTH_SHORT
                ).show()
                exception.message?.let {
                    Log.w(TAG, it)
                }
            }
        }

    }

    private fun initLaunchers() {
        settingsLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    // When user enables location setting, insert reminder and start geofence
                    reminder?.let {
                        addGeofenceForReminder()
                        _viewModel.validateAndSaveReminder(it)
                    }
                } else {
                    showLocationSettingsSnackbar()
                }
            }

        permissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
                // Check if all permissions are granted
                val granted: Boolean = resultMap.values.all { it }

                if (granted) {
                    checkLocationSettings()
//                    addGeofenceForReminder()
                } else {
                    // Check if we are in a state where the user has denied the permission and
                    // selected Don't ask again
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        showRationaleDialog()
                    }
                    showLocationSnackbar()
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }

    /*
    * Checks for foreground and background permissions and request them if not granted
    * */
    private fun checkLocationPermission() {
        // If foreground permissions are denied
        if (!isForegroundLocationGranted()) {
            // If we should show an explanation
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showRationaleDialog()
            } else {
                // No explanation needed, we can request the permission.
                requestForegroundLocationPermission()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !isBackgroundLocationGranted()
        ) {
            // If foreground location accessed, request background (on API 29+)
            requestBackgroundLocationPermission()
        } else {
            checkLocationSettings()
        }
    }

    private fun checkLocationSettings() {
        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(requireContext())
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // If location setting is already enabled, insert reminder and start geofence
            reminder?.let {
                addGeofenceForReminder()
                _viewModel.validateAndSaveReminder(it)
            }
        }

        // Location setting is not enabled on the device
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    settingsLauncher.launch(intentSenderRequest)
                } catch (_: IntentSender.SendIntentException) {
                }
            } else {
                showLocationSettingsSnackbar()
            }
        }
    }

    /*
    * Check for foreground and background permissions
    * */
    private fun isForegroundLocationGranted(): Boolean {
        // Check if foreground permissions (Fine and Coarse) are granted
        return PackageManager.PERMISSION_GRANTED ==
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                && PackageManager.PERMISSION_GRANTED ==
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
    }

    /*
    * Check if background permission is granted
    * */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun isBackgroundLocationGranted() =
        PackageManager.PERMISSION_GRANTED ==
                ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )

    /*
    * Request background location permission
    * */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestBackgroundLocationPermission() {
        permissionsLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    /*
    * Request foreground location permissions
    * */
    private fun requestForegroundLocationPermission() {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /*
    * Show an explanation to the user
    * After the user sees the explanation, try again to request the permission.
    * */
    private fun showRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.location_permission_explanation_title))
            .setMessage(getString(R.string.permission_denied_explanation))
            .setPositiveButton(R.string.ok) { _, _ ->
                //Prompt the user once explanation has been shown
                requestForegroundLocationPermission()
            }
            .create()
            .show()
    }

    /*
    * Shows a snackbar and an action to settings for user to enable location permission
    * */
    private fun showLocationSnackbar() {
        Snackbar.make(
            binding.root,
            R.string.permission_denied_explanation,
            Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.settings) {
            startActivity(Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }.show()
    }

    /*
    * Show a snackbar to inform user of the need to enable location setting
    * */
    private fun showLocationSettingsSnackbar() {
        Snackbar.make(
            binding.root,
            R.string.location_required_error,
            Snackbar.LENGTH_INDEFINITE
        ).setAction(android.R.string.ok) {
            checkLocationSettings()
        }.show()
    }

    companion object {
        internal const val ACTION_GEOFENCE_EVENT =
            "RemindersActivity.locationreminders.action.ACTION_GEOFENCE_EVENT"
    }
}

private const val TAG = "SaveReminderFragment"
private const val GEOFENCE_RADIUS_IN_METERS = 100f