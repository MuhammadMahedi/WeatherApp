package com.example.weatherapp

import android.Manifest
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    lateinit var binding:ActivityMainBinding
    private lateinit var mFusedLocationClient:FusedLocationProviderClient
    private var mProgressDialog: Dialog?=null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient=LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences=getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        if(!isLocationEnabled()){
            Toast.makeText(this, "Please turn on the location provides",
                Toast.LENGTH_SHORT).show()

            val intent= Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)

        }else{
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ).withListener(object : MultiplePermissionsListener {

                    override fun onPermissionsChecked(report: MultiplePermissionsReport) { /* ... */
                        if(report.areAllPermissionsGranted()){
                            requestLocationData()
                        }

                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,"You have denied this permission",
                                Toast.LENGTH_LONG).show()

                        }

                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: List<PermissionRequest>,
                        token: PermissionToken
                    ) {
                        showRationalDialogForPermission()

                    }
                }).onSameThread().check()
        }


        binding.refreshIcon.setOnClickListener {
            requestLocationData()
        }
    }



    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this).setMessage("Seems like the permissions are denied and " +
                "you need to enable them in the setting options")
            .setPositiveButton("Go to settings"){
                    _,_->
                try {
                    val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    var uri= Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){ dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude= mLastLocation?.latitude as Double
            val longitude= mLastLocation?.longitude as Double

            Log.i("locations","Latitude : $latitude  Longitude : $longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double,longitude: Double){
        if(Constants.isNetWorkAvailable(this)){
            val retrofit:Retrofit= Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.App_ID
            )

            showProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        hideProgressDialog()
                        val weatherList:WeatherResponse? = response.body()
                        setUpUi(weatherList)
                        //savins the data as string
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor=mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()

                        Log.i("Response Result","$weatherResponseJsonString")
                    }else{
                        val rc=response.code()
                        when(rc){
                            400->{
                                Log.e("error 400","Bad Connection")
                            }
                            404->{
                                Log.e("Error 404","Response Not Found")
                            }else->{
                            Log.e("Error","Generic Error")
                            }

                        }
                    }

                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Error",t.message.toString())

                }

            })
        }else{
            Toast.makeText(this@MainActivity,
                "No Internet Connection",
                Toast.LENGTH_SHORT).show()
        }
    }

@SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private fun isLocationEnabled():Boolean{

        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    private fun showProgressDialog(){
        mProgressDialog=Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if(mProgressDialog!=null)
            mProgressDialog!!.dismiss()
    }

    private fun setUpUi(weatherList:WeatherResponse?){
        if(weatherList!=null){
            for(i in weatherList.weather.indices){
                Log.i("WeatherDetails", weatherList.weather.toString())
                with(binding){
                    this.tvWeather.text=weatherList.weather[i].main
                    this.tvWeatherDescription.text=weatherList.weather[i].description
                    this.tempMax.text=weatherList.main.temp_max.toString()+getUnit(application.resources.configuration.locales.toString())
                    this.tempMin.text=weatherList.main.temp_min.toString()+getUnit(application.resources.configuration.locales.toString())
                    this.tvSunrise.text=unixTime(weatherList.sys.sunrise)
                    this.tvSunset.text=unixTime(weatherList.sys.sunset)
                    this.temp.text=weatherList.main.temp.toString()+getUnit(application.resources.configuration.locales.toString())
                    this.humidity.text=weatherList.main.humidity.toString()
                    this.wind.text=weatherList.wind.speed.toString()
                    this.countryName.text=weatherList.sys.country
                    this.placeName.text=weatherList.name

                    when(weatherList.weather[i].icon){
                        "01d.png"-> this.ivWeather.setImageResource(R.drawable.sunny)
                        "02d.png"-> this.ivWeather.setImageResource(R.drawable.cloud)
                        "03d.png"-> this.ivWeather.setImageResource(R.drawable.cloud)
                        "04d.png"-> this.ivWeather.setImageResource(R.drawable.cloud)
                        "09d.png"-> this.ivWeather.setImageResource(R.drawable.cloud)
                        "10d.png"-> this.ivWeather.setImageResource(R.drawable.rain)
                        "11d.png"-> this.ivWeather.setImageResource(R.drawable.storm)
                        "13d.png"-> this.ivWeather.setImageResource(R.drawable.snowflake)
                        "01n.png"-> this.ivWeather.setImageResource(R.drawable.sunny)
                        "02n.png"-> this.ivWeather.setImageResource(R.drawable.cloud)
                        "03n.png"-> this.ivWeather.setImageResource(R.drawable.cloud)
                        "04n.png"-> this.ivWeather.setImageResource(R.drawable.cloud)
                        "09n.png"-> this.ivWeather.setImageResource(R.drawable.cloud)
                        "10n.png"-> this.ivWeather.setImageResource(R.drawable.rain)
                        "11n.png"-> this.ivWeather.setImageResource(R.drawable.storm)
                        "13n.png"-> this.ivWeather.setImageResource(R.drawable.snowflake)
                        else->this.ivWeather.setImageResource(R.drawable.sunny)
                    }
                }

            }


        }

    }
    private fun getUnit(value:String):String?{
        var v="°C"
        if(value=="US" || value=="LR" || value=="MM"){
            v="°F"
        }
        return v
    }
    private fun unixTime(timeX:Long):String?{
        val date= Date(timeX * 1000L) //millis to sec hence *1000L
        val sdf=SimpleDateFormat("HH:mm") // ("HH:mm:ss") this for 24 hours
        sdf.timeZone= TimeZone.getDefault()  // ("hh:mm:ss") this for 12 hours
        return sdf.format(date)
    }


}