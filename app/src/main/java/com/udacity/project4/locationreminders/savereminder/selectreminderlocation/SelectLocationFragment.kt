package com.udacity.project4.locationreminders.savereminder.selectreminderlocation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import kotlinx.android.synthetic.main.it_reminder.*
import org.koin.android.ext.android.inject
import java.util.*

private const val TAG = "SelectLocationFragment"
private const val ZOOM_LEVEL = 15f

class SelectLocationFragment : BaseFragment(), OnMapReadyCallback {

    //Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var map: GoogleMap
    private lateinit var binding: FragmentSelectLocationBinding
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var settingsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var marker: Marker? = null

    private val fusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_select_location, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        initLaunchers()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Check for permissions before saving
        binding.saveButton.setOnClickListener { onLocationSelected() }

        // Get map fragment
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /*
    * Initialize permission launchers and handle request responses
    * */
    private fun initLaunchers() {
        // Check if device location has been enabled
        settingsLauncher = registerForActivityResult(StartIntentSenderForResult()) { result ->
            if (result.resultCode != RESULT_OK)
                showLocationSettingsSnackbar()
        }

        permissionsLauncher = registerForActivityResult(RequestMultiplePermissions()) { resultMap ->
            // Check if all permissions are granted
            val granted: Boolean = resultMap.values.all { it }

            if (granted) {
                // If granted check if location setting is enabled on device
                enableLocation()
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

    override fun onMapReady(p0: GoogleMap?) {
        if (p0 == null)
            return

        map = p0
        setMapStyle(map)
        checkLocationPermission()
        setMapLongClick(map)
        setPoiClick(map)
    }

    /*
    * Checks for foreground permissions and request them if not granted
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
        } else {
            // If all permissions are enabled
            enableLocation()
        }
    }

    /*
    * Check for foreground permissions
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

    @SuppressLint("MissingPermission")
    private fun enableLocation() {
        map.isMyLocationEnabled = true

        viewCurrentPosition()

        hintUser()

        // Check if device location is on when My Location button is tapped
        map.setOnMyLocationButtonClickListener {
            checkLocationSettings()
            false
        }
    }

    /*
    *  Zoom camera to current location
    * */
    @SuppressLint("MissingPermission")
    private fun viewCurrentPosition() {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                // Update map camera on user current location
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude), ZOOM_LEVEL
                    )
                )
            }
        }
    }

    private fun onLocationSelected() {
        marker?.let {
            _viewModel.latitude.value = it.position.latitude
            _viewModel.longitude.value = it.position.longitude
            _viewModel.reminderSelectedLocationStr.value = it.title
            _viewModel.navigationCommand.value = NavigationCommand.Back
        }
    }

    private fun checkLocationSettings() {
        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(requireContext())
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

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

    private fun setMapLongClick(map: GoogleMap) {
        map.setOnMapLongClickListener { latLng ->
            val snippet = getString(R.string.lat_long_snippet, latLng.latitude, latLng.longitude)

            map.clear()
            marker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.dropped_pin))
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )

            _viewModel.isLocationSelected.value = true
        }
    }

    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->
            map.clear()
            marker = map.addMarker(
                MarkerOptions()
                    .position(poi.latLng)
                    .title(poi.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
            )
            marker?.showInfoWindow()

            _viewModel.isLocationSelected.value = true
        }
    }

    private fun setMapStyle(map: GoogleMap) {
        // Customize the styling of the base map using a JSON object defined
        // in a raw resource file.
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.map_style
                )
            )
            if (!success)
                Log.e(TAG, "Style parsing failed.")
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Can't find style. Error: ", e)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    private fun hintUser() {
        // Hints user to pick a location
        Toast.makeText(
            requireContext(), R.string.select_location, Toast.LENGTH_SHORT
        ).show()
    }
}
