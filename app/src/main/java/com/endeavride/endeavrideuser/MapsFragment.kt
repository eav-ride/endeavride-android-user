package com.endeavride.endeavrideuser

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.endeavride.endeavrideuser.PermissionUtils.isPermissionGranted
import com.endeavride.endeavrideuser.PermissionUtils.requestPermission
import com.endeavride.endeavrideuser.data.model.Ride
import com.endeavride.endeavrideuser.databinding.FragmentMapsBinding
import com.endeavride.endeavrideuser.ui.login.LoginViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import java.io.IOException


//@AndroidEntryPoint
class MapsFragment : Fragment(), GoogleMap.OnMarkerClickListener, OnRequestPermissionsResultCallback {

    companion object {
        /**
         * Request code for location permission request.
         *
         * @see .onRequestPermissionsResult
         */
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2
        private const val PLACE_PICKER_REQUEST = 3

        private const val TAG = "MapsFragment"
    }

    enum class OrderStatus(val value: Int) {
        DEFAULT(-1),
        UNASSIGNED(0),
        ASSIGNING(1),
        PICKING(2),
        ARRIVED_USER_LOCATION(3),
        STARTED(4),
        FINISHED(5),
        CANCELED(6);

        companion object {
            private val VALUES = values()
            fun from(value: Int) = VALUES.firstOrNull { it.value == value }
        }
    }

    enum class OrderType(val value: Int) {
        RIDE_SERVICE(0),
        HOME_SERVICE(1);

        companion object {
            private val VALUES = OrderType.values()
            fun from(value: Int) = VALUES.firstOrNull { it.value == value }
        }
    }

//    private lateinit var progressBar: ProgressBar
    private val adapter = PlacePredictionAdapter()
    private lateinit var viewModel: MapsViewModel
    private lateinit var loginViewModel: LoginViewModel

    private var permissionDenied = false
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private var isInitialLoadingLocation = true
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var locationUpdateState = false

    private var _binding: FragmentMapsBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var homeServiceButton: Button
    private lateinit var rideServiceButton: Button
    private lateinit var clearButton: Button
    private lateinit var actionButton: Button
    private lateinit var autocompleteFragment: FragmentContainerView
    private lateinit var buttonContainer: View

    private var dest: LatLng? = null
    private var customer: LatLng? = null
    private var needDirection = false
    private var rid: String? = null

    private var status: OrderStatus = OrderStatus.DEFAULT
    private var type: OrderType = OrderType.RIDE_SERVICE
    private var isAutoPollingEnabled = false
    private var isPostingDriveRecord = false
    private var driverLocation: Marker? = null

    private val callback = OnMapReadyCallback { googleMap ->
        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        map = googleMap
        map.setOnMapLongClickListener {
            map.clear()
            placeDestinationOnMap(it)

        }
        enableMyLocation()

        viewModel.getCurrentRide()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        homeServiceButton = binding.homeServiceButton
        rideServiceButton = binding.requestDriverButton
        clearButton = binding.clearButton
        actionButton = binding.actionButton
        autocompleteFragment = binding.autocompleteFragment
        buttonContainer = binding.view
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, MapsViewModelFactory()).get(MapsViewModel::class.java)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)

        reloadData()

        viewModel.latestDriverLocation.observe(viewLifecycleOwner,
            Observer { driver ->
                val location = Utils.decodeLocationString(driver.driver_location)

                if (isPostingDriveRecord) {
                    if (driverLocation != null) {
                        driverLocation?.remove()
                    }
                    driverLocation = location?.let { placeMarkerOnMap(it) }
                    rid?.let { viewModel.pollDriveRecord(it) }
                }
            })

        viewModel.mapDirectionResult.observe(viewLifecycleOwner,
            Observer { path ->
                for (i in 0 until path.size) {
                    map.addPolyline(PolylineOptions().addAll(path[i]).color(Color.RED))
                }
            })

        viewModel.currentRide.observe(viewLifecycleOwner,
            Observer { ride ->
                println("#K_current ride $ride")
                this.rid = ride?.rid
                if (ride == null) {
                    dest = null
                    customer = null
                    setStatus(ride)
                    return@Observer
                }
                type = OrderType.from(ride.type) ?: OrderType.RIDE_SERVICE
                dest = Utils.decodeLocationString(ride.destination)
                customer = if (ride.user_location == null) {
                    null
                } else {
                    Utils.decodeLocationString(ride.user_location)
                }
                setStatus(ride)
            })

        Places.initialize(requireContext(), getString(R.string.google_maps_key))
        val autocompleteFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                Log.i(TAG, "Place: ${place.name}, ${place.id}, ${place.latLng}")
                place.latLng?.let {
                    map.clear()
                    placeDestinationOnMap(it)
                }
            }

            override fun onError(status: Status) {
                Log.i(TAG, "An error occurred: $status")
                Toast.makeText(requireContext(), "An error occurred! Please try again later!", Toast.LENGTH_SHORT).show()
            }
        })

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                lastLocation = p0.lastLocation
                val currentLatLng = LatLng(p0.lastLocation.latitude, p0.lastLocation.longitude)
                if (isInitialLoadingLocation) {
                    isInitialLoadingLocation = false
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                }
                if (status == OrderStatus.STARTED) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                }
            }
        }
        createLocationRequest()
    }

    private fun setStatus(ride: Ride?) {
        val rideStatus = ride?.status ?: -1
        status = OrderStatus.from(rideStatus) ?: OrderStatus.DEFAULT

        reloadData()
    }

    private fun showActionButton() {
        val params = buttonContainer.layoutParams
        params.height = getPixelFromDp(84)
        buttonContainer.layoutParams = params
        actionButton.isVisible = true
        homeServiceButton.isVisible = false
        rideServiceButton.isVisible = false
        autocompleteFragment.isVisible = false
    }

    private fun reloadData() {
        if (status == OrderStatus.UNASSIGNED || status == OrderStatus.ASSIGNING) {
            showActionButton()
            actionButton.text = "Waiting available drivers..."
            clearButton.text = "Cancel"
            clearButton.isEnabled = true

            clearButton.setOnClickListener {
                // show a popup to confirm if user want to cancel request, if confirmed, stop auto refresh, post cancel ride request
                val alert = activity?.let {
                    // Use the Builder class for convenient dialog construction
                    val builder = AlertDialog.Builder(it)
                    builder.setMessage("Are you sure to cancel the ride?")
                        .setPositiveButton("No",
                            DialogInterface.OnClickListener { dialog, id ->
                                // FIRE ZE MISSILES!
                            })
                        .setNegativeButton("Yes, cancel it",
                            DialogInterface.OnClickListener { dialog, id ->
                                // User cancelled the dialog
                                // TODO: send cancel task request
                                rid?.let { it1 -> viewModel.cancelRide(it1) }
                                map.clear()
                            })
                    // Create the AlertDialog object and return it
                    builder.create()
                } ?: throw IllegalStateException("Activity cannot be null")
                alert.show()
            }
            requestDirectionIfNeeded()

            rid?.let { viewModel.refreshRide(it, 3000) }
        } else if (status == OrderStatus.PICKING) {
            showActionButton()
            actionButton.text = "Ride received, waiting for pick up..."
            actionButton.isClickable = false
            clearButton.isEnabled = false

            // start updating GPS to server
            if (!isPostingDriveRecord) {
                Log.d(TAG, "Start polling GPS Records")
                isPostingDriveRecord = true
                rid?.let { viewModel.pollDriveRecord(it) }
            }
            requestDirectionIfNeeded()

            rid?.let { viewModel.refreshRide(it, 10000) }
        } else if (status == OrderStatus.ARRIVED_USER_LOCATION) {
            showActionButton()
            actionButton.text = "Driver arrived!"
            actionButton.isClickable = false
            clearButton.isEnabled = false

            isPostingDriveRecord = false
            requestDirectionIfNeeded()

            rid?.let { viewModel.refreshRide(it, 10000) }
        } else if (status == OrderStatus.STARTED) {
            showActionButton()
            actionButton.text = if (type == OrderType.RIDE_SERVICE) {
                "Sit tight, heading to your destination!"
            } else {
                "Driver is on the way!"
            }
            actionButton.isClickable = false
            clearButton.isEnabled = false

            // start updating GPS to server
            if (!isPostingDriveRecord) {
                Log.d(TAG, "Start polling GPS Records and start updating user location as well")
                isPostingDriveRecord = true
                try {
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

                    rid?.let { viewModel.pollDriveRecord(it) }
                } catch (unlikely: SecurityException) {
                    Log.e(TAG, "Lost location permissions. Couldn't remove updates. $unlikely")
                }
            }
            requestDirectionIfNeeded()

            rid?.let { viewModel.refreshRide(it, 3000) }
        } else if (status == OrderStatus.FINISHED) {
            val text = if (type == OrderType.RIDE_SERVICE) {
                "You've arrived!!"
            } else {
                "Driver has arrived!!"
            }
            dest = null
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
            map.clear()
            setStatus(null)
        } else {
            defaultStatusActions()
        }
    }

    private fun defaultStatusActions() {
        // UI updates
        val params = buttonContainer.layoutParams
        params.height = getPixelFromDp(140)
        buttonContainer.layoutParams = params
        rideServiceButton.text = "Ride Service"
        rideServiceButton.isVisible = true
        homeServiceButton.text = "Home Service"
        homeServiceButton.isVisible = true
        clearButton.text = "Clear"
        clearButton.isEnabled = true
        autocompleteFragment.isVisible = true
        actionButton.isVisible = false

        // logic updates
        rid = null
        isPostingDriveRecord = false
        isAutoPollingEnabled = false
        needDirection = true

        if (dest == null) {
            rideServiceButton.isEnabled = false
            homeServiceButton.isEnabled = true
        } else {
            rideServiceButton.isEnabled = true
            homeServiceButton.isEnabled = false
        }

        clearButton.setOnClickListener {
            map.clear()
            dest = null
            reloadData()
        }

        homeServiceButton.setOnClickListener {
            Log.d(TAG, "Sending home service button")
            isAutoPollingEnabled = true
            NetworkUtils.user?.userId?.let { it2 ->
                lastLocation?.let { it3 -> LatLng(it3.latitude, it3.longitude) }?.let { it4 ->
                    viewModel.createRide(OrderType.HOME_SERVICE.value, null, it4, it2
                    )
                }
            }
        }

        rideServiceButton.setOnClickListener {
            Log.d(TAG, "#K_send driver request with points $lastLocation and $dest")
            isAutoPollingEnabled = true
            dest?.let { it1 -> NetworkUtils.user?.userId?.let { it2 ->
                lastLocation?.let { it3 -> LatLng(it3.latitude, it3.longitude) }?.let { it4 ->
                    viewModel.createRide(OrderType.RIDE_SERVICE.value, it4, it1, it2
                    )
                }
            } }
        }
    }

    private fun requestDirection() {
        if (lastLocation == null) {
            needDirection = true
            return
        }
        val target = LatLng(customer?.latitude ?: lastLocation!!.latitude, customer?.longitude ?: lastLocation!!.longitude)
        dest?.let {
            needDirection = false
            placeDestinationOnMap(it)
            viewModel.getDirection(
                target, it)
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError()
            permissionDenied = false
        } else {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showMissingPermissionError() {
        Toast.makeText(requireContext(), "Permission was not granted! Please grant the permission to use the app!", Toast.LENGTH_LONG).show()
    }

    private fun getPixelFromDp(dps: Int): Int {
        val scale: Float? = context?.resources?.displayMetrics?.density
        return (dps * scale!! + 0.5f).toInt()
    }

    private fun requestDirectionIfNeeded() {
        if (dest != null && customer != null && needDirection) {
            requestDirection()
        }
    }

    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    private fun enableMyLocation(animated: Boolean = false) {
        if (!::map.isInitialized) return
        if (context == null) {
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing. Show rationale and request permission
            requestPermission(requireActivity(), LOCATION_PERMISSION_REQUEST_CODE,
                Manifest.permission.ACCESS_FINE_LOCATION, true
            )

            Log.d(TAG, "location permission granting")
            return
        }
        Log.d(TAG, "location permission granted!")
        map.isMyLocationEnabled = true
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        fusedLocationClient.lastLocation.addOnSuccessListener(requireActivity()) { location ->
            // Got last known location. In some rare situations this can be null.
            if (location != null) {
                lastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
                if (animated) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                } else {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                }

                requestDirectionIfNeeded()
            }
        }
    }

    private fun placeDestinationOnMap(location: LatLng): Marker? {
        dest = location
        reloadData()
        return placeMarkerOnMap(location)
    }

    private fun placeMarkerOnMap(location: LatLng): Marker? {
        val markerOptions = MarkerOptions().position(location)

        val titleStr = getAddress(location)  // add these two lines
        markerOptions.title(titleStr)

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 12f))

        return map.addMarker(markerOptions)
    }

    override fun onMarkerClick(p0: Marker): Boolean {
        TODO("Not yet implemented")
    }

    private fun getAddress(latLng: LatLng): String {
        // 1
        if (context == null) {
            return ""
        }
        val geocoder = Geocoder(requireContext())
        val addresses: List<Address>?
        val address: Address?
        var addressText = ""

        try {
            // 2
            addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            // 3
            if (null != addresses && !addresses.isEmpty()) {
                address = addresses[0]
                for (i in 0 until address.maxAddressLineIndex) {
                    addressText += if (i == 0) address.getAddressLine(i) else "\n" + address.getAddressLine(i)
                }
            }
        } catch (e: IOException) {
            Log.e("MapsActivity", e.localizedMessage)
        }

        return addressText
    }

    private fun startLocationUpdates() {
        if (context == null) {
            return
        }
        if (ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null /* Looper */)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.interval = 10000
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            locationUpdateState = true
            startLocationUpdates()
        }
        task.addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(requireActivity(),
                        REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    // [START maps_check_location_permission_result]
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return
        }
        if (isPermissionGranted(permissions, grantResults, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation()
        } else {
            // Permission was denied. Display an error message
            // [START_EXCLUDE]
            // Display the missing permission error dialog when the fragments resume.
            permissionDenied = true
            // [END_EXCLUDE]
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
//    private fun showMissingPermissionError() {
//        newInstance(true).show(supportFragmentManager, "dialog")
//    }
}