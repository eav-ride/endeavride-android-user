package com.endeavride.endeavrideuser

import android.Manifest
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
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

    enum class OrderStatus(val value: Int)
    {
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

//    private lateinit var progressBar: ProgressBar
    private val adapter = PlacePredictionAdapter()
    private lateinit var viewModel: MapsViewModel
    private lateinit var loginViewModel: LoginViewModel

    private var permissionDenied = false
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null

    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var locationUpdateState = false

    private var _binding: FragmentMapsBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var dest: LatLng? = null
    private var customer: LatLng? = null
    private var needDirection = false
    private var rid: String? = null

    private var status: OrderStatus = OrderStatus.DEFAULT
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
            placeMarkerOnMap(it)
        }
        enableMyLocation()

        viewModel.refreshRide()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
//        progressBar = binding.progressBar
//        binding.toolbar.inflateMenu(R.menu.search_place_menu)
//        setHasOptionsMenu(true)
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
                if (driverLocation != null) {
                    driverLocation?.remove()
                }
                driverLocation = location?.let { placeMarkerOnMap(it) }

                if (isPostingDriveRecord) {
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
                setStatus(ride)
                ride ?: return@Observer
                val dest = Utils.decodeLocationString(ride.destination)
                val customer = Utils.decodeLocationString(ride.user_location)
                if (dest != null && customer != null && this.customer != customer) {
                    placeMarkerOnMap(dest)
                    this.dest = dest
                    this.customer = customer
                    requestDirection()
                }
            })

        val autocompleteFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME))

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                // TODO: Get info about the selected place.
                Log.i(TAG, "Place: ${place.name}, ${place.id}")
            }

            override fun onError(status: Status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: $status")
            }
        })

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                lastLocation = p0.lastLocation
//                placeMarkerOnMap(LatLng(lastLocation.latitude, lastLocation.longitude))

                if (needDirection) {
                    needDirection = false
                    requestDirection()
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

    private fun reloadData() {
        if (status == OrderStatus.UNASSIGNED || status == OrderStatus.ASSIGNING) {
            binding.requestDriverButton.text = "Waiting available drivers..."
            binding.requestDriverButton.isClickable = false
            binding.clearButton.text = "Cancel"
            binding.clearButton.isEnabled = true

            binding.clearButton.setOnClickListener {
                // show a popup to confirm if user want to cancel request, if confirmed, stop auto refresh, post cancel ride request
                // TODO: send cancel task request
            }

            viewModel.refreshRide(3000)
        } else if (status == OrderStatus.PICKING) {
            binding.requestDriverButton.text = "Ride received, waiting for pick up..."
            binding.requestDriverButton.isClickable = false
            binding.clearButton.text = "Cancel"
            binding.clearButton.isEnabled = false

            // start updating GPS to server
            if (!isPostingDriveRecord) {
                Log.d(TAG, "Start polling GPS Records")
                isPostingDriveRecord = true
                rid?.let { viewModel.pollDriveRecord(it) }
            }

            viewModel.refreshRide(5000)
        } else if (status == OrderStatus.ARRIVED_USER_LOCATION) {
            binding.requestDriverButton.text = "Driver arrived!"
            binding.requestDriverButton.isClickable = false
            binding.clearButton.text = "Cancel"
            binding.clearButton.isEnabled = false

            isPostingDriveRecord = false

            viewModel.refreshRide(5000)
        } else if (status == OrderStatus.STARTED) {
            binding.requestDriverButton.text = "Sit tight, heading to your destination!"
            binding.requestDriverButton.isClickable = false
            binding.clearButton.text = "Cancel"
            binding.clearButton.isEnabled = false

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

            viewModel.refreshRide(3000, showFinish = true)
        } else if (status == OrderStatus.FINISHED) {
            Toast.makeText(requireContext(), "You've arrived!!", Toast.LENGTH_SHORT).show()
            map.clear()
            defaultStatusActions()
        } else {
            defaultStatusActions()
        }
    }

    private fun defaultStatusActions() {
        binding.requestDriverButton.text = "Request Driver"
        binding.requestDriverButton.isClickable = true
        binding.clearButton.text = "Clear"
        binding.clearButton.isEnabled = true
        isPostingDriveRecord = false
        isAutoPollingEnabled = false

        binding.clearButton.setOnClickListener {
            map.clear()
            dest = null
        }

        binding.requestDriverButton.setOnClickListener {
            Log.d(TAG, "#K_send driver request with points $lastLocation and $dest")
            isAutoPollingEnabled = true
            dest?.let { it1 -> NetworkUtils.user?.userId?.let { it2 ->
                lastLocation?.let { it3 -> LatLng(it3.latitude, it3.longitude) }?.let { it4 ->
                    viewModel.createRide(
                        it4, it1,
                        it2
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
            viewModel.getDirection(
                target, it)
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
//            showMissingPermissionError()
            permissionDenied = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // MARK: search place by address
//    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
//        super.onCreateOptionsMenu(menu, inflater)
//        Log.d("Map", "#K_onCreateOptionsMenu")
//        val searchView = menu.findItem(R.id.search).actionView as SearchView
//        searchView.apply {
//            queryHint = getString(R.string.search_a_place)
//            isIconifiedByDefault = false
//            isFocusable = true
//            isIconified = false
//            requestFocusFromTouch()
//            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
//                override fun onQueryTextSubmit(query: String): Boolean {
//                    return false
//                }
//
//                override fun onQueryTextChange(newText: String): Boolean {
//                    viewModel.onSearchQueryChanged(newText)
//                    return true
//                }
//            })
//        }
//    }

//    private fun initRecyclerView() {
//        val linearLayoutManager = LinearLayoutManager(this)
//        findViewById<RecyclerView>(R.id.recycler_view).apply {
//            layoutManager = linearLayoutManager
////            adapter = this@PlacesSearchDemoActivity.adapter
//            addItemDecoration(
//                DividerItemDecoration(
//                    this@PlacesSearchDemoActivity,
//                    linearLayoutManager.orientation
//                )
//            )
//        }
//    }

    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    private fun enableMyLocation() {
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

            Log.d("TAG", "#K_permission granting")
            return
        }
        Log.d("TAG", "#K_permission granted")
        map.isMyLocationEnabled = true
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        fusedLocationClient.lastLocation.addOnSuccessListener(requireActivity()) { location ->
            // Got last known location. In some rare situations this can be null.
            if (location != null) {
                lastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
//                placeMarkerOnMap(currentLatLng)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
//                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))

                if (needDirection) {
                    needDirection = false
                    requestDirection()
                }
            }
        }
    }

    private fun placeMarkerOnMap(location: LatLng): Marker? {
        val markerOptions = MarkerOptions().position(location)

        val titleStr = getAddress(location)  // add these two lines
        markerOptions.title(titleStr)

        dest = location

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

//    private fun loadPlacePicker() {
//        val builder = PlacePicker.IntentBuilder()
//
//        try {
//            startActivityForResult(builder.build(requireActivity()), PLACE_PICKER_REQUEST)
//        } catch (e: GooglePlayServicesRepairableException) {
//            e.printStackTrace()
//        } catch (e: GooglePlayServicesNotAvailableException) {
//            e.printStackTrace()
//        }
//    }

//    override fun onMyLocationButtonClick(): Boolean {
//        Toast.makeText(requireContext(), "MyLocation button clicked", Toast.LENGTH_SHORT).show()
//        // Return false so that we don't consume the event and the default behavior still occurs
//        // (the camera animates to the user's current position).
//        return false
//    }
//
//    override fun onMyLocationClick(location: Location) {
//        Toast.makeText(requireContext(), "Current location:\n$location", Toast.LENGTH_LONG).show()
//    }

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