package com.marinov.watchweather

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException

// Enum para identificar a fonte dos dados
enum class DataSource {
    CLIMATEMPO,
    WEATHER_COM
}

// Data class para armazenar os dados brutos extraídos
data class WeatherData(
    val city: String,
    val temperature: String,
    val sensation: String,
    val wind: String,
    val humidity: String,
    val pressure: String,
    val airQuality: String
)

// Data class para representar uma cidade
data class City(
    val url: String,
    val dataSource: DataSource
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SHARED_PREFS_NAME = "WeatherAppPrefs"
        private const val KEY_LAST_CITY_URL = "last_city_url"
        private const val KEY_LAST_DATA_SOURCE = "last_data_source"
    }

    private enum class ViewState {
        SELECTION,
        LOADING,
        CONTENT,
        ERROR
    }

    // UI de Previsão
    private lateinit var tvCity: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvSensation: TextView
    private lateinit var tvWind: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvPressure: TextView
    private lateinit var tvAirQuality: TextView
    private lateinit var weatherContent: ConstraintLayout

    // UI de Controle
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var selectionScreen: ScrollView
    private lateinit var countrySelectionLayout: LinearLayout
    private lateinit var citySelectionLayouts: List<LinearLayout>


    private lateinit var sharedPreferences: SharedPreferences

    // Estrutura de dados para cidades e fontes
    private val cities = mapOf(
        // Climatempo
        R.id.btn_sao_paulo to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/558/saopaulo-sp", DataSource.CLIMATEMPO),
        R.id.btn_ubatuba to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/570/ubatuba-sp", DataSource.CLIMATEMPO),
        R.id.btn_rio_de_janeiro to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/321/riodejaneiro-rj", DataSource.CLIMATEMPO),
        R.id.btn_florianopolis to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/377/florianopolis-sc", DataSource.CLIMATEMPO),
        R.id.btn_curitiba to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/271/curitiba-pr", DataSource.CLIMATEMPO),
        R.id.btn_praia_grande to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/523/praiagrande-sp", DataSource.CLIMATEMPO),
        R.id.btn_belo_horizonte to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/107/belohorizonte-mg", DataSource.CLIMATEMPO),
        R.id.btn_porto_alegre to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/363/portoalegre-rs", DataSource.CLIMATEMPO),
        R.id.btn_belem to City("https://www.climatempo.com.br/previsao-do-tempo/agora/cidade/232/belem-pa", DataSource.CLIMATEMPO),

        // Weather.com
        R.id.btn_taipei to City("https://weather.com/pt-BR/clima/hoje/l/ce745664a88b5afafeb9b67d548ae1f5cfbcc2631f3cb119d921b2c8ca7a20be", DataSource.WEATHER_COM),
        R.id.btn_hong_kong to City("https://weather.com/pt-BR/clima/hoje/l/5e9589bf5805da723141e8f56378fadcd6dfcb3a72844e124acfb4c61620cc15", DataSource.WEATHER_COM),
        R.id.btn_shenzhen to City("https://weather.com/pt-BR/clima/hoje/l/1670890a44c4bb7d7d66e47d0e79a8bfea1474ab51e9dd771062ab4a959e0c8a", DataSource.WEATHER_COM),
        R.id.btn_pequim to City("https://weather.com/pt-BR/clima/hoje/l/00c189c38a25908eae6f4246e0d4648890e75239d4331f4517f1fc38e920ff05", DataSource.WEATHER_COM),
        R.id.btn_nova_deli to City("https://weather.com/pt-BR/clima/hoje/l/7d67733d29d6a86f79bd6a172edd760e933c9f65987b7cc68362088d1e4afeec", DataSource.WEATHER_COM),
        R.id.btn_sao_petersburgo to City("https://weather.com/pt-BR/clima/hoje/l/866cb3be4344b5593f0928d35c0ee0cda69d89da7799fac8fda841adabd82a17", DataSource.WEATHER_COM),
        R.id.btn_kiev to City("https://weather.com/pt-BR/clima/hoje/l/88b31cb7a7d9b9c086599aebf8d87a2333cd69eae36776c3267eee41592a3e91", DataSource.WEATHER_COM),
        R.id.btn_doha to City("https://weather.com/pt-BR/clima/hoje/l/7dafe8e2c513aaed09df2c0038b86b2b64c308064bda1c3509231c0a513a1ebb", DataSource.WEATHER_COM),
        R.id.btn_berlim to City("https://weather.com/pt-BR/clima/hoje/l/2355888e52076fe39a2616d1423d554c76647d9c2f261e5b583918435bf6c42b", DataSource.WEATHER_COM),
        R.id.btn_frankfurt to City("https://weather.com/pt-BR/clima/hoje/l/55298a88f04ef9534dac59e109a1f147287cd50b1c09c59a9087e33a5d148eb2", DataSource.WEATHER_COM),
        R.id.btn_paris to City("https://weather.com/pt-BR/clima/hoje/l/a5c27da38afe789545e3446ede0bfd5042030764469a6cd4fff4e9468c74d2a7", DataSource.WEATHER_COM),
        R.id.btn_lisboa to City("https://weather.com/pt-BR/clima/hoje/l/071f551f4ce6727dc75e15dfacfd92c56a0f0fcdb0e41a60e128ae319be9463c", DataSource.WEATHER_COM),
        R.id.btn_porto to City("https://weather.com/pt-BR/clima/hoje/l/cd086140a970cbea1fa9ca908b2a87339076a7615de814fbf85488835ff8a8ef", DataSource.WEATHER_COM),
        R.id.btn_roma to City("https://weather.com/pt-BR/clima/hoje/l/104b5c3a7e17868e40f84026b44fd565a02ee18193bb030a5cbd3076e58c01bc", DataSource.WEATHER_COM),
        R.id.btn_bruxelas to City("https://weather.com/pt-BR/clima/hoje/l/d1ecabe854ff08676c7162ab00afc13a714c887bb2c9df889c1e0fc7309e3c1c", DataSource.WEATHER_COM),
        R.id.btn_amsterdam to City("https://weather.com/pt-BR/clima/hoje/l/253b712e01bcec0d92d5db5aa2c0b5699a1e14c50e72ca007ccc583178bd30a3", DataSource.WEATHER_COM),
        R.id.btn_seul to City("https://weather.com/pt-BR/clima/hoje/l/bcf9c3c268f09aa5353d6d374302bd3c826ea47577a25f549d7e8997f6e6da45", DataSource.WEATHER_COM),
        R.id.btn_pyongyang to City("https://weather.com/pt-BR/clima/hoje/l/da1b117b5877237fe1daee5687d6d55fb6fd53a18c3e81dda29009a36469418c", DataSource.WEATHER_COM),
        R.id.btn_madri to City("https://weather.com/pt-BR/clima/hoje/l/f620d7fe58f453124aa71caa578d94f09a298b74f2e9bd519413ad3d9ce6a771", DataSource.WEATHER_COM),
        R.id.btn_havana to City("https://weather.com/pt-BR/clima/hoje/l/eb2d56dee45d2ba7384ff99a47a70f3a5c64d9a331a8b059a7b331dc62579b33", DataSource.WEATHER_COM),
        R.id.btn_santiago to City("https://weather.com/pt-BR/clima/hoje/l/9d3b491220ea48e9a1cfe014e32934278032da27c04f7e964c6e65ec4de028e8", DataSource.WEATHER_COM),
        R.id.btn_cidade_do_mexico to City("https://weather.com/pt-BR/clima/hoje/l/279b9b5f79fa02ab0d2133430d64bbb06bb53ae0fdf3234ea59d68dba44812cb", DataSource.WEATHER_COM),
        R.id.btn_caracas to City("https://weather.com/pt-BR/clima/hoje/l/1eb2f9b8e20c3fb53805ed92c5f198993d2615356fd5b46a104e434e23b6f56e", DataSource.WEATHER_COM),
        R.id.btn_sucre to City("https://weather.com/pt-BR/clima/hoje/l/5d6fd655f00f9b39b6b89003aa7dc5230d9428dea70a5217cf24e432efb79f23", DataSource.WEATHER_COM),
        R.id.btn_assuncao to City("https://weather.com/pt-BR/clima/hoje/l/79579780a17cbe68d766d5075b6082e69a76eb017be29ebbad9698b718efdf96", DataSource.WEATHER_COM),
        R.id.btn_montevideu to City("https://weather.com/pt-BR/clima/hoje/l/0ec0b65ad1873426e3c3fb399b2c7f662b6fe0fd1e6e05b2e05c29bf9673551e", DataSource.WEATHER_COM),
        R.id.btn_buenos_aires to City("https://weather.com/pt-BR/clima/hoje/l/2447e41747ecf3ceead70ef4a5d89867e3a9cb3f1e92b0ddfce326652eb6c46e", DataSource.WEATHER_COM),
        R.id.btn_varsovia to City("https://weather.com/pt-BR/clima/hoje/l/c40c3d36ef5e02c7bfb64f4f9dde3c3783248c911f7cc83bf1345d4907336463", DataSource.WEATHER_COM),
        R.id.btn_toquio to City("https://weather.com/pt-BR/clima/hoje/l/a2d49e31992a3679724161c3a9029211ecefeda3e1da24489637a5685ded3fdf", DataSource.WEATHER_COM),
        R.id.btn_koto to City("https://weather.com/pt-BR/clima/hoje/l/3f7a1b4c1033eea2e1e647a686a9e488cf9dd32cc9f3402d3c9c06c847de3093", DataSource.WEATHER_COM),
        R.id.btn_abu_dhabi to City("https://weather.com/pt-BR/clima/hoje/l/3d5d25f87ba096ee5d2460f4b279363402a1f31068b3d0674059f68ad94821da", DataSource.WEATHER_COM),
        R.id.btn_teera to City("https://weather.com/pt-BR/clima/hoje/l/75814d99326fdea4d4f0d79772a0a7193bb0b4adf556bae671e29816c12a7ba1", DataSource.WEATHER_COM),
        R.id.btn_cankaya to City("https://weather.com/pt-BR/clima/hoje/l/7f6d21f7b08cf5eb15d1371590e895e74304f3972a925eacf3fc914bec2c7464", DataSource.WEATHER_COM),
        R.id.btn_jerusalem to City("https://weather.com/pt-BR/clima/hoje/l/eb9298942ea6bff5eefab6cba776eb07740f5a3a8f9385bff8ed65798f9f86eb", DataSource.WEATHER_COM)
    )

    // Mapeia o botão do país para o layout da lista de cidades
    private val countryToCityLayoutMap by lazy {
        mapOf(
            R.id.btn_country_brazil to findViewById(R.id.cities_brazil),
            R.id.btn_country_china to findViewById(R.id.cities_china),
            R.id.btn_country_india to findViewById(R.id.cities_india),
            R.id.btn_country_russia to findViewById(R.id.cities_russia),
            R.id.btn_country_ukraine to findViewById(R.id.cities_ukraine),
            R.id.btn_country_qatar to findViewById(R.id.cities_qatar),
            R.id.btn_country_germany to findViewById(R.id.cities_germany),
            R.id.btn_country_france to findViewById(R.id.cities_france),
            R.id.btn_country_portugal to findViewById(R.id.cities_portugal),
            R.id.btn_country_italy to findViewById(R.id.cities_italy),
            R.id.btn_country_belgium to findViewById(R.id.cities_belgium),
            R.id.btn_country_netherlands to findViewById(R.id.cities_netherlands),
            R.id.btn_country_south_korea to findViewById(R.id.cities_south_korea),
            R.id.btn_country_north_korea to findViewById(R.id.cities_north_korea),
            R.id.btn_country_spain to findViewById(R.id.cities_spain),
            R.id.btn_country_cuba to findViewById(R.id.cities_cuba),
            R.id.btn_country_chile to findViewById(R.id.cities_chile),
            R.id.btn_country_mexico to findViewById(R.id.cities_mexico),
            R.id.btn_country_venezuela to findViewById(R.id.cities_venezuela),
            R.id.btn_country_bolivia to findViewById(R.id.cities_bolivia),
            R.id.btn_country_paraguay to findViewById(R.id.cities_paraguay),
            R.id.btn_country_uruguay to findViewById(R.id.cities_uruguay),
            R.id.btn_country_argentina to findViewById(R.id.cities_argentina),
            R.id.btn_country_poland to findViewById(R.id.cities_poland),
            R.id.btn_country_japan to findViewById(R.id.cities_japan),
            R.id.btn_country_uae to findViewById(R.id.cities_uae),
            R.id.btn_country_iran to findViewById<LinearLayout>(R.id.cities_iran),
            R.id.btn_country_turkey to findViewById(R.id.cities_turkey),
            R.id.btn_country_israel to findViewById(R.id.cities_israel)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupSelectionListeners()

        val lastUrl = sharedPreferences.getString(KEY_LAST_CITY_URL, null)
        val lastDataSourceName = sharedPreferences.getString(KEY_LAST_DATA_SOURCE, null)
        val lastDataSource = lastDataSourceName?.let { DataSource.valueOf(it) }

        if (lastUrl != null && lastDataSource != null) {
            fetchAndDisplayWeatherData(lastUrl, lastDataSource)
        } else {
            updateViewState(ViewState.SELECTION)
        }
    }

    private fun initializeViews() {
        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        tvCity = findViewById(R.id.tv_city)
        tvTemperature = findViewById(R.id.tv_temperature)
        tvSensation = findViewById(R.id.tv_sensation)
        tvWind = findViewById(R.id.tv_wind)
        tvHumidity = findViewById(R.id.tv_humidity)
        tvPressure = findViewById(R.id.tv_pressure)
        tvAirQuality = findViewById(R.id.tv_air_quality)
        weatherContent = findViewById(R.id.weather_content)
        progressBar = findViewById(R.id.progress_bar)
        tvError = findViewById(R.id.tv_error)
        selectionScreen = findViewById(R.id.selection_screen)
        countrySelectionLayout = findViewById(R.id.country_selection)

        // Inicializa a lista de layouts de cidades
        citySelectionLayouts = countryToCityLayoutMap.values.toList()
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun setupSelectionListeners() {
        // Listeners para os botões de país
        countryToCityLayoutMap.forEach { (countryButtonId, cityLayout) ->
            findViewById<Button>(countryButtonId).setOnClickListener {
                countrySelectionLayout.visibility = View.GONE
                cityLayout.visibility = View.VISIBLE
            }
        }

        // Listener para todos os botões de cidade
        cities.forEach { (buttonId, city) ->
            findViewById<View>(buttonId)?.setOnClickListener {
                with(sharedPreferences.edit()) {
                    putString(KEY_LAST_CITY_URL, city.url)
                    putString(KEY_LAST_DATA_SOURCE, city.dataSource.name)
                    commit() // Usar commit para garantir gravação imediata
                }
                fetchAndDisplayWeatherData(city.url, city.dataSource)
            }
        }
    }

    private fun fetchAndDisplayWeatherData(url: String, dataSource: DataSource) {
        lifecycleScope.launch {
            updateViewState(ViewState.LOADING)
            val weatherData = when (dataSource) {
                DataSource.CLIMATEMPO -> scrapeClimatempoData(url)
                DataSource.WEATHER_COM -> scrapeWeatherComData(url)
            }
            if (weatherData != null) {
                updateUI(weatherData)
                updateViewState(ViewState.CONTENT)
            } else {
                updateViewState(ViewState.ERROR)
            }
        }
    }

    private suspend fun scrapeClimatempoData(url: String): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).get()
            val mainCard = doc.selectFirst("div.card[data-type='Card_FirstElement']")
            val airQualityCard = doc.selectFirst("div.card-health[data-id='Card_Indexes_Item_AirQuality']")

            val city = mainCard?.selectFirst("h1")?.text()?.replace("Tempo agora em", "")?.trim() ?: "N/A"
            val temperature = mainCard?.selectFirst("span.-font-55")?.text() ?: "N/A"
            val sensationRaw = mainCard?.select("div.no-gutters span")?.firstOrNull { it.text().contains("Sensação:") }?.text()?.replace("Sensação:", "")?.trim() ?: "N/A"
            val variablesList = mainCard?.select("ul.variables-list li.item")
            val windRaw = variablesList?.get(0)?.selectFirst("div._flex:last-child")?.text()?.trim() ?: "N/A"
            val humidityRaw = variablesList?.get(1)?.selectFirst("span.-gray-light")?.text() ?: "N/A"
            val pressureRaw = variablesList?.get(2)?.selectFirst("div._flex:last-child")?.text() ?: "N/A"
            val airQualityRaw = airQualityCard?.selectFirst("div.value-content p")?.text() ?: "N/A"

            WeatherData(city, temperature, sensationRaw, windRaw, humidityRaw, pressureRaw, airQualityRaw)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun scrapeWeatherComData(url: String): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).get()
            val currentConditions = doc.selectFirst("section[class*=CurrentConditions--card]")
            val todayDetails = doc.selectFirst("div[class*=TodayDetailsCard--detailsContainer]")

            val city = currentConditions?.selectFirst("h1.CurrentConditions--location--yub4l")?.text() ?: "N/A"
            val temperature = currentConditions?.selectFirst("span[data-testid=TemperatureValue]")?.text() ?: "N/A"
            val sensationRaw = todayDetails?.selectFirst("div[data-testid=FeelsLikeSection] span[data-testid=TemperatureValue]")?.text() ?: "N/A"

            // Helper para encontrar detalhes pelo texto do rótulo
            fun getDetailByLabel(label: String): String {
                val parent = todayDetails?.select("div.WeatherDetailsListItem--label--U-Wrx:contains($label)")?.firstOrNull()?.parent()
                return parent?.select("[data-testid=wxData]")?.text() ?: "N/A"
            }

            val windLabel = getString(R.string.wind_label_for_scraping)
            val humidityLabel = getString(R.string.humidity_label_for_scraping)
            val pressureLabel = getString(R.string.pressure_label_for_scraping)

            val windRaw = getDetailByLabel(windLabel)
            val humidityRaw = getDetailByLabel(humidityLabel)
            val pressureRaw = getDetailByLabel(pressureLabel)
            val airQualityRaw = "--" // Não disponível nesta fonte

            WeatherData(city, temperature, sensationRaw, windRaw, humidityRaw, pressureRaw, airQualityRaw)
        } catch (e: Exception) { // Captura exceções mais genéricas também
            e.printStackTrace()
            null
        }
    }

    private fun updateUI(data: WeatherData) {
        tvCity.text = data.city
        tvTemperature.text = data.temperature
        tvSensation.text = getString(R.string.sensation_format, data.sensation)
        tvWind.text = getString(R.string.wind_format, data.wind)
        tvHumidity.text = getString(R.string.humidity_format, data.humidity)
        tvPressure.text = getString(R.string.pressure_format, data.pressure)
        tvAirQuality.text = getString(R.string.air_quality_format, data.airQuality)
    }

    private fun updateViewState(state: ViewState) {
        selectionScreen.visibility = if (state == ViewState.SELECTION) View.VISIBLE else View.GONE
        progressBar.visibility = if (state == ViewState.LOADING) View.VISIBLE else View.GONE
        weatherContent.visibility = if (state == ViewState.CONTENT) View.VISIBLE else View.GONE
        tvError.visibility = if (state == ViewState.ERROR) View.VISIBLE else View.GONE
    }
}

